'use strict';
// StockScreener — filter-motor (vanilla JS). Henter facetter, bygger sliders med
// histogram bagved + multivalg, og kører live tælling/funnel/resultater mod api.php.

const $  = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));
const EXT_QUOTE_URL = 'https://finance.yahoo.com/quote/'; // ekstern detaljeside (label er neutral)
const DOMAINS = {};            // key -> {min,max,scale,fmt,edges,counts}
let FACETS = null;
let debounceTimer = null;

// ---------- formattering ----------
function fmtVal(v, fmt) {
  if (v === null || v === undefined || !isFinite(v)) return '–';
  if (fmt === 'usd') {
    const a = Math.abs(v);
    if (a >= 1e12) return '$' + (v/1e12).toFixed(2) + 'T';
    if (a >= 1e9)  return '$' + (v/1e9).toFixed(2) + 'B';
    if (a >= 1e6)  return '$' + (v/1e6).toFixed(1) + 'M';
    if (a >= 1e3)  return '$' + (v/1e3).toFixed(0) + 'k';
    return '$' + v.toFixed(0);
  }
  if (fmt === 'pct') return (v*100).toFixed(1) + '%';
  // num
  const a = Math.abs(v);
  if (a >= 1e9) return (v/1e9).toFixed(1) + 'B';
  if (a >= 1e6) return (v/1e6).toFixed(1) + 'M';
  if (a !== 0 && a < 0.01) return v.toExponential(1);
  return (Math.round(v*100)/100).toString();
}
const COLFMT = { mkt_cap_usd:'usd', ret_1y:'pct', ret_3y:'pct', quality_3y:'num',
  dividend_yield:'pct', return_on_equity:'pct', trailing_pe:'num', _sortval:null };

// ---------- skala: position(0..1000) <-> værdi ----------
function posToVal(pos, d) {
  const t = pos / 1000;
  if (d.scale === 'log') {
    const lo = Math.log10(d.min > 0 ? d.min : 1e-9), hi = Math.log10(d.max > 0 ? d.max : 1);
    return Math.pow(10, lo + (hi - lo) * t);
  }
  return d.min + (d.max - d.min) * t;
}
function valToPos(val, d) {
  let t;
  if (d.scale === 'log') {
    const lo = Math.log10(d.min > 0 ? d.min : 1e-9), hi = Math.log10(d.max > 0 ? d.max : 1);
    t = (Math.log10(val > 0 ? val : d.min) - lo) / (hi - lo);
  } else {
    t = (val - d.min) / (d.max - d.min);
  }
  return Math.max(0, Math.min(1000, Math.round(t * 1000)));
}

// ---------- init ----------
window.addEventListener('DOMContentLoaded', async () => {
  wireGroups();
  wireControls();
  initTA();
  try {
    FACETS = await (await fetch('api.php?action=facets')).json();
  } catch (e) { $('#resultWrap').innerHTML = '<div class="err">Kunne ikke hente facetter: ' + e + '</div>'; return; }
  buildRanges();
  buildMultis();
  initFavorites();
  applyStateFromURL();
  const params = new URLSearchParams(location.search); // læs FØR refresh() (som rydder URL'en)
  const ta = params.get('ta'), preset = params.get('preset');
  if (preset && (window.PRESETS || {})[preset]) applyPreset(preset); // deep-link til preset
  else refresh();
  if (ta) openStock(ta); // deep-link til teknisk analyse
});

function wireGroups() {
  $$('.fgroup-h').forEach(h => h.addEventListener('click', () => h.parentElement.classList.toggle('open')));
}
function wireControls() {
  $('#sort').addEventListener('change', () => { refresh(); });
  $('#limit').addEventListener('change', () => { refresh(); });
  $('#dirBtn').addEventListener('click', () => {
    const b = $('#dirBtn'); const desc = b.dataset.dir !== 'asc';
    b.dataset.dir = desc ? 'asc' : 'desc';
    b.textContent = desc ? '▲ Lavest' : '▼ Højest';
    refresh();
  });
  $('#hideJunk').addEventListener('change', refresh);
  $('#chartWindow').addEventListener('change', loadChart);
  $('#chartBench').addEventListener('change', loadChart);
  $('#resetBtn').addEventListener('click', resetAll);
  $$('.preset').forEach(b => b.addEventListener('click', () => applyPreset(b.dataset.preset)));
  $('#csvBtn').addEventListener('click', () => { window.location = 'api.php?action=csv&' + collectParams().toString(); });
  $('#saveBtn').addEventListener('click', saveScreen);
  $('#chips').addEventListener('click', onChipClick);
  renderSaved();
  $('#shareBtn').addEventListener('click', () => {
    navigator.clipboard.writeText(location.href).then(() => {
      const b = $('#shareBtn'); const t = b.textContent; b.textContent = '✓ Kopieret'; setTimeout(() => b.textContent = t, 1200);
    });
  });
}

// ---------- byg sliders ----------
function buildRanges() {
  $$('.filt:not(.multi)').forEach(el => {
    const key = el.dataset.key, r = FACETS.ranges[key];
    if (!r) { el.style.display = 'none'; return; }
    const d = { min: r.min, max: r.max, scale: r.scale, fmt: r.fmt, edges: r.edges, counts: r.counts };
    DOMAINS[key] = d;
    const rmin = $('.r-min', el), rmax = $('.r-max', el);
    const upd = () => {
      if (+rmin.value > +rmax.value) { // forhindre kryds
        if (document.activeElement === rmin) rmin.value = rmax.value; else rmax.value = rmin.value;
      }
      drawHist(el);
      const lo = posToVal(+rmin.value, d), hi = posToVal(+rmax.value, d);
      $('.filt-vals', el).textContent = fmtVal(lo, d.fmt) + ' – ' + fmtVal(hi, d.fmt);
      el.classList.toggle('active', +rmin.value > 0 || +rmax.value < 1000);
      markGroupActive(el);
      scheduleRefresh();
    };
    rmin.addEventListener('input', upd);
    rmax.addEventListener('input', upd);
    drawHist(el);
    $('.filt-vals', el).textContent = fmtVal(d.min, d.fmt) + ' – ' + fmtVal(d.max, d.fmt);
  });
}

function drawHist(el) {
  const key = el.dataset.key, d = DOMAINS[key];
  const cv = $('.hist', el);
  const w = cv.clientWidth || 240, h = cv.height;
  if (cv.width !== w) cv.width = w;
  const ctx = cv.getContext('2d'); ctx.clearRect(0, 0, w, h);
  const counts = d.counts, B = counts.length, maxc = Math.max(1, ...counts);
  const rmin = +$('.r-min', el).value, rmax = +$('.r-max', el).value;
  const bw = w / B;
  for (let i = 0; i < B; i++) {
    const bh = Math.round((counts[i] / maxc) * (h - 2));
    // er denne bucket inden for det valgte spænd?
    const bucketPos0 = (i / B) * 1000, bucketPos1 = ((i + 1) / B) * 1000;
    const inSel = bucketPos1 > rmin && bucketPos0 < rmax;
    ctx.fillStyle = inSel ? '#5b8def' : '#2a3340';
    ctx.fillRect(i * bw, h - bh, Math.max(1, bw - 1), bh);
  }
}

// ---------- byg multivalg ----------
function buildMultis() {
  $$('.filt.multi').forEach(el => {
    const key = el.dataset.key, opts = (FACETS.options[key] || []);
    const box = $('.multi-opts', el);
    box.innerHTML = opts.map(o =>
      `<label class="opt"><input type="checkbox" value="${escAttr(o.v)}"> ${escHtml(o.v)} <span class="muted">${o.n}</span></label>`
    ).join('');
    box.addEventListener('change', () => { el.classList.toggle('active', $$('input:checked', box).length > 0); markGroupActive(el); scheduleRefresh(); });
  });
}

function markGroupActive(el) {
  const grp = el.closest('.fgroup');
  const n = $$('.filt.active', grp).length;
  $('.grp-active', grp).textContent = n ? n : '';
}

// ---------- params + URL ----------
function collectParams() {
  const p = new URLSearchParams();
  $$('.filt:not(.multi)').forEach(el => {
    const key = el.dataset.key, d = DOMAINS[key]; if (!d) return;
    const rmin = +$('.r-min', el).value, rmax = +$('.r-max', el).value;
    if (rmin > 0)    p.set(key + '_min', round(posToVal(rmin, d)));
    if (rmax < 1000) p.set(key + '_max', round(posToVal(rmax, d)));
  });
  $$('.filt.multi').forEach(el => {
    const vals = $$('input:checked', el).map(i => i.value);
    if (vals.length) p.set(el.dataset.key, vals.join('~'));
  });
  if ($('#hideJunk').checked) {
    const cur = p.get('max_day_move_max');
    if (cur === null || +cur > 1) p.set('max_day_move_max', '1');
  } else {
    p.delete('hj'); // ingen markør = default on; vi sætter hj=0 når slået fra
    p.set('hj', '0');
  }
  p.set('sort', $('#sort').value);
  p.set('dir', $('#dirBtn').dataset.dir === 'asc' ? 'asc' : 'desc');
  p.set('limit', $('#limit').value);
  return p;
}
function round(v) { return Math.abs(v) >= 100 ? Math.round(v) : (Math.round(v*1e4)/1e4); }

function scheduleRefresh() { clearTimeout(debounceTimer); debounceTimer = setTimeout(refresh, 220); }

async function refresh() {
  const p = collectParams();
  history.replaceState(null, '', location.pathname + '?' + p.toString());
  $('#resultWrap').classList.add('busy');
  let d;
  try { d = await (await fetch('api.php?action=query&' + p.toString())).json(); }
  catch (e) { $('#resultWrap').innerHTML = '<div class="err">Fejl: ' + e + '</div>'; return; }
  if (d.error) { $('#resultWrap').innerHTML = '<div class="err">' + d.error + '</div>'; return; }
  $('#count').textContent = d.count.toLocaleString('da-DK');
  $('#total').textContent = d.total.toLocaleString('da-DK');
  renderFunnel(d.funnel);
  renderResults(d.results, d.sort);
  renderChips();
  $('#resultWrap').classList.remove('busy');
}

function renderFunnel(funnel) {
  if (!funnel || funnel.length <= 1) { $('#funnel').innerHTML = ''; return; }
  const top = funnel[0].n || 1;
  $('#funnel').innerHTML = '<div class="funnel-h">Udfaldsrum snævres ind:</div>' + funnel.map((s, i) => {
    const pct = Math.max(2, Math.round((s.n / top) * 100));
    return `<div class="fstep"><span class="flabel">${escHtml(s.label)}</span>
      <span class="fbar"><span style="width:${pct}%"></span></span>
      <span class="fn">${s.n.toLocaleString('da-DK')}</span></div>`;
  }).join('');
}

function renderResults(rows, sort) {
  if (!rows || !rows.length) { $('#resultWrap').innerHTML = '<div class="empty-r">Ingen aktier matcher. Løsn et filter.</div>'; return; }
  const sortLbl = (window.SORT_OPTS && window.SORT_OPTS[sort]) || sort;
  let h = `<table class="rtable"><thead><tr>
    <th>#</th><th>Symbol</th><th>Navn</th><th>Sektor</th><th>Land</th><th class="num">Mkt cap</th>
    <th class="num hl">${escHtml(sortLbl)}</th><th class="num">Afkast 1Y</th><th class="num">Kvalitet 3Y</th>
    <th class="num">P/E</th><th class="num">Udbytte</th><th></th></tr></thead><tbody>`;
  rows.forEach((r, i) => {
    const yurl = EXT_QUOTE_URL + encodeURIComponent(r.symbol);
    h += `<tr data-sym="${escAttr(r.symbol)}" class="clickrow" title="Klik for teknisk analyse">
      <td class="muted">${i+1}</td>
      <td class="sym"><span class="ta-dot">📈</span> ${escHtml(r.symbol)}</td>
      <td class="nm" title="${escAttr(r.name||'')}">${escHtml(trunc(r.name, 28))}</td>
      <td>${escHtml(r.sector||'–')}</td>
      <td>${escHtml(r.country||'–')}</td>
      <td class="num">${fmtVal(+r.mkt_cap_usd, 'usd')}</td>
      <td class="num hl">${fmtCol(sort, r._sortval)}</td>
      <td class="num ${cls(r.ret_1y)}">${fmtVal(+r.ret_1y, 'pct')}</td>
      <td class="num">${fmtVal(+r.quality_3y, 'num')}</td>
      <td class="num">${fmtVal(+r.trailing_pe, 'num')}</td>
      <td class="num">${fmtVal(+r.dividend_yield, 'pct')}</td>
      <td class="ylink"><a href="${yurl}" target="_blank" rel="noopener" title="Åbn aktiens eksterne detaljeside" onclick="event.stopPropagation()">↗</a></td>
    </tr>`;
  });
  $('#resultWrap').innerHTML = h + '</tbody></table>';
  window.CURRENT_SYMBOLS = rows.map(r => r.symbol);
  loadChart();
}

// ---------- overlay base-100 graf ----------
const CHART_PALETTE = ['#5b8def','#2ec4b6','#ff9f1c','#e71d73','#9b5de5','#00bbf9','#80ed99',
  '#f15bb5','#ffca3a','#ff70a6','#8ac926','#ff595e','#6a4c93','#1982c4','#52a675','#c77dff'];
let overlayChart = null;

async function loadChart() {
  const syms = (window.CURRENT_SYMBOLS || []).slice(0, 16);
  const cv = $('#overlayChart');
  if (!syms.length) { if (overlayChart) { overlayChart.destroy(); overlayChart = null; } return; }
  const win = $('#chartWindow').value, bench = $('#chartBench').value;
  let d;
  try {
    const q = new URLSearchParams({ action: 'chart', symbols: syms.join('~'), window: win, bench });
    d = await (await fetch('api.php?' + q.toString())).json();
  } catch (e) { return; }

  const datasets = (d.series || []).map((s, i) => ({
    label: s.symbol,
    data: s.points.map(p => ({ x: Date.parse(p[0]), y: p[1] })),
    borderColor: CHART_PALETTE[i % CHART_PALETTE.length],
    backgroundColor: CHART_PALETTE[i % CHART_PALETTE.length],
    borderWidth: 1.5, pointRadius: 0, tension: 0.1,
  }));
  if (d.bench && d.bench.points.length) {
    datasets.push({
      label: d.bench.label + ' (benchmark)',
      data: d.bench.points.map(p => ({ x: Date.parse(p[0]), y: p[1] })),
      borderColor: '#e6edf3', borderWidth: 2.5, borderDash: [6, 4], pointRadius: 0, tension: 0.1,
    });
  }
  if (overlayChart) overlayChart.destroy();
  overlayChart = new Chart(cv, {
    type: 'line',
    data: { datasets },
    options: {
      responsive: true, maintainAspectRatio: false, animation: false,
      interaction: { mode: 'nearest', intersect: false },
      scales: {
        x: { type: 'linear', ticks: { color: '#9aa4b2', maxTicksLimit: 8,
              callback: v => new Date(v).toLocaleDateString('da-DK', { year: '2-digit', month: 'short' }) },
             grid: { color: '#1c2330' } },
        y: { ticks: { color: '#9aa4b2' }, grid: { color: '#1c2330' },
             title: { display: true, text: 'Base 100', color: '#9aa4b2' } },
      },
      plugins: {
        legend: { position: 'bottom', labels: { color: '#cbd3df', boxWidth: 12, font: { size: 11 } } },
        tooltip: { callbacks: {
          title: items => items.length ? new Date(items[0].parsed.x).toLocaleDateString('da-DK') : '',
          label: c => `${c.dataset.label}: ${c.parsed.y.toFixed(1)}`,
        } },
      },
    },
  });
}
function fmtCol(col, v) {
  v = +v;
  let fmt = COLFMT[col];
  if (fmt === undefined) {
    fmt = /^(ret_|cagr_|rs_|maxdd_|vol_|dividend_yield$|return_on_|.*_margins$|.*_growth$)/.test(col) ? 'pct' : 'num';
    if (col === 'mkt_cap_usd') fmt = 'usd';
  }
  return `<span class="${cls(v)}">${fmtVal(v, fmt)}</span>`;
}
function cls(v) { v = +v; return v > 0 ? 'pos' : (v < 0 ? 'neg' : ''); }

// ---------- URL-state ----------
function applyStateFromURL() {
  const p = new URLSearchParams(location.search);
  if (p.get('hj') === '0') $('#hideJunk').checked = false;
  if (p.get('sort')) $('#sort').value = p.get('sort');
  if (p.get('limit')) $('#limit').value = p.get('limit');
  if (p.get('dir') === 'asc') { $('#dirBtn').dataset.dir = 'asc'; $('#dirBtn').textContent = '▲ Lavest'; }
  $$('.filt:not(.multi)').forEach(el => {
    const key = el.dataset.key, d = DOMAINS[key]; if (!d) return;
    const mn = p.get(key + '_min'), mx = p.get(key + '_max');
    if (mn !== null) $('.r-min', el).value = valToPos(+mn, d);
    if (mx !== null) $('.r-max', el).value = valToPos(+mx, d);
    if (mn !== null || mx !== null) { el.classList.add('active'); }
    drawHist(el);
    const lo = posToVal(+$('.r-min', el).value, d), hi = posToVal(+$('.r-max', el).value, d);
    $('.filt-vals', el).textContent = fmtVal(lo, d.fmt) + ' – ' + fmtVal(hi, d.fmt);
    markGroupActive(el);
  });
  $$('.filt.multi').forEach(el => {
    const raw = p.get(el.dataset.key); if (!raw) return;
    const set = new Set(raw.split('~'));
    $$('input', el).forEach(i => { if (set.has(i.value)) i.checked = true; });
    if (set.size) { el.classList.add('active'); markGroupActive(el); }
  });
}
function clearFilters() {
  $$('.filt:not(.multi)').forEach(el => { $('.r-min', el).value = 0; $('.r-max', el).value = 1000; el.classList.remove('active'); drawHist(el);
    const d = DOMAINS[el.dataset.key]; if (d) $('.filt-vals', el).textContent = fmtVal(d.min, d.fmt) + ' – ' + fmtVal(d.max, d.fmt); });
  $$('.filt.multi input:checked').forEach(i => i.checked = false);
  $$('.filt.multi').forEach(el => el.classList.remove('active'));
  $$('.grp-active').forEach(s => s.textContent = '');
  $('#hideJunk').checked = true;
}
function resetAll() { clearFilters(); $$('.preset').forEach(b => b.classList.remove('on')); refresh(); }

// ---------- strategi-presets ----------
function applyPreset(name) {
  const ps = (window.PRESETS || {})[name];
  if (!ps) return;
  clearFilters();
  $$('.preset').forEach(b => b.classList.toggle('on', b.dataset.preset === name));
  for (const [key, rng] of Object.entries(ps.filters || {})) {
    const el = document.querySelector('.filt[data-key="' + key + '"]');
    if (!el) continue;
    el.closest('.fgroup').classList.add('open');
    if (rng.in) {                                   // multivalg (fx lande)
      const want = new Set(rng.in);
      $$('input', el).forEach(i => i.checked = want.has(i.value));
      el.classList.toggle('active', $$('input:checked', el).length > 0);
    } else {                                        // range-slider
      const d = DOMAINS[key]; if (!d) continue;
      if (rng.min != null) $('.r-min', el).value = valToPos(rng.min, d);
      if (rng.max != null) $('.r-max', el).value = valToPos(rng.max, d);
      el.classList.add('active'); drawHist(el);
      const lo = posToVal(+$('.r-min', el).value, d), hi = posToVal(+$('.r-max', el).value, d);
      $('.filt-vals', el).textContent = fmtVal(lo, d.fmt) + ' – ' + fmtVal(hi, d.fmt);
    }
    markGroupActive(el);
  }
  if (ps.sort) $('#sort').value = ps.sort;
  if (ps.dir) { $('#dirBtn').dataset.dir = ps.dir; $('#dirBtn').textContent = ps.dir === 'asc' ? '▲ Lavest' : '▼ Højest'; }
  $('#hideJunk').checked = true;
  refresh();
}

// ---------- aktive-filter-chips ----------
function renderChips() {
  const items = [];
  $$('.filt:not(.multi).active').forEach(el => {
    const d = DOMAINS[el.dataset.key]; if (!d) return;
    const lo = posToVal(+$('.r-min', el).value, d), hi = posToVal(+$('.r-max', el).value, d);
    items.push({ key: el.dataset.key, type: 'range', text: el.dataset.label + ': ' + fmtVal(lo, d.fmt) + '–' + fmtVal(hi, d.fmt) });
  });
  $$('.filt.multi.active').forEach(el => {
    const sel = $$('input:checked', el).map(i => i.value);
    items.push({ key: el.dataset.key, type: 'multi', text: el.dataset.label + ': ' + (sel.length > 2 ? sel.length + ' valgt' : sel.join(', ')) });
  });
  const wrap = $('#chips');
  wrap.innerHTML = items.length
    ? items.map(c => `<span class="chip" data-key="${c.key}" data-type="${c.type}">${escHtml(c.text)} <span class="chip-x">×</span></span>`).join('')
      + '<button class="chip-clear">Ryd alle</button>'
    : '';
}
function onChipClick(e) {
  if (e.target.classList.contains('chip-clear')) { resetAll(); return; }
  const chip = e.target.closest('.chip'); if (!chip) return;
  clearOneFilter(chip.dataset.key, chip.dataset.type);
}
function clearOneFilter(key, type) {
  const el = document.querySelector('.filt[data-key="' + key + '"]'); if (!el) return;
  if (type === 'multi') { $$('input:checked', el).forEach(i => i.checked = false); }
  else {
    $('.r-min', el).value = 0; $('.r-max', el).value = 1000; drawHist(el);
    const d = DOMAINS[key]; if (d) $('.filt-vals', el).textContent = fmtVal(d.min, d.fmt) + ' – ' + fmtVal(d.max, d.fmt);
  }
  el.classList.remove('active'); markGroupActive(el);
  $$('.preset').forEach(b => b.classList.remove('on'));
  refresh();
}

// ---------- gemte screens (localStorage) ----------
const SAVED_KEY = 'screener_saved';
function getSaved() { try { return JSON.parse(localStorage.getItem(SAVED_KEY)) || []; } catch (e) { return []; } }
function setSaved(a) { try { localStorage.setItem(SAVED_KEY, JSON.stringify(a)); } catch (e) {} }
function saveScreen() {
  const name = (prompt('Navn på denne screen:') || '').trim(); if (!name) return;
  const saved = getSaved().filter(s => s.name !== name);
  saved.push({ name, q: collectParams().toString() });
  setSaved(saved); renderSaved();
}
function renderSaved() {
  const saved = getSaved(), wrap = $('#savedScreens');
  if (!wrap) return;
  wrap.innerHTML = saved.length
    ? '<span class="saved-lbl">Mine screens:</span>' + saved.map((s, i) =>
        `<span class="saved-item"><a href="?${s.q}">${escHtml(s.name)}</a><span class="saved-x" data-i="${i}" title="Slet">×</span></span>`).join('')
    : '';
  $$('.saved-x', wrap).forEach(x => x.addEventListener('click', () => {
    const a = getSaved(); a.splice(+x.dataset.i, 1); setSaved(a); renderSaved();
  }));
}

// ---------- favorit-filtre (vises øverst, gemt i localStorage) ----------
const FAV_KEY = 'screener_favs';
function getFavs() { try { return JSON.parse(localStorage.getItem(FAV_KEY)) || []; } catch (e) { return []; } }
function setFavs(a) { try { localStorage.setItem(FAV_KEY, JSON.stringify(a)); } catch (e) {} }

function initFavorites() {
  $$('.fav-star').forEach(star => star.addEventListener('click', e => {
    e.stopPropagation();
    toggleFav(star.closest('.filt').dataset.key);
  }));
  getFavs().forEach(key => moveFilt(key, true));
  updateFavGroup();
}
function toggleFav(key) {
  let favs = getFavs();
  const on = !favs.includes(key);
  favs = on ? [...favs, key] : favs.filter(k => k !== key);
  setFavs(favs);
  moveFilt(key, on);
  updateFavGroup();
}
function moveFilt(key, toFav) {
  const el = document.querySelector('.filt[data-key="' + key + '"]');
  if (!el) return;
  el.classList.toggle('is-fav', toFav);
  const star = $('.fav-star', el); if (star) star.textContent = toFav ? '★' : '☆';
  const dest = toFav
    ? $('#favGroup .fgroup-body')
    : document.querySelector('.fgroup[data-group="' + el.dataset.origin + '"] .fgroup-body');
  if (dest) dest.appendChild(el);
  if (!el.classList.contains('multi')) drawHist(el); // canvas-redraw efter flytning
}
function updateFavGroup() { $('#favGroup').hidden = $$('#favGroup .filt').length === 0; }

// ---------- util ----------
function trunc(s, n) { s = s || ''; return s.length > n ? s.slice(0, n-1) + '…' : s; }
function escHtml(s) { return String(s ?? '').replace(/[&<>]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;'}[c])); }
function escAttr(s) { return String(s ?? '').replace(/"/g, '&quot;').replace(/[&<>]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;'}[c])); }

// ============ Teknisk analyse (fokus-view ved klik på række) ============
let taPriceChart = null, taSubChart = null, TA_DATA = null;

function initTA() {
  $('#resultWrap').addEventListener('click', e => {
    const tr = e.target.closest('.clickrow'); if (tr) openStock(tr.dataset.sym);
  });
  $('#modalClose').addEventListener('click', closeStock);
  $('#stockModal').addEventListener('click', e => { if (e.target.id === 'stockModal') closeStock(); });
  $('#taWindow').addEventListener('change', () => { if (TA_DATA) openStock(TA_DATA.symbol); });
  $('#taBench').addEventListener('change', renderTA);
  $('#taSub').addEventListener('change', renderTA);
  $$('.ta-smas input').forEach(c => c.addEventListener('change', renderTA));
  document.addEventListener('keydown', e => { if (e.key === 'Escape') closeStock(); });
}

async function openStock(sym) {
  const m = $('#stockModal'); m.hidden = false;
  $('.m-sym', m).textContent = sym; $('.m-name', m).textContent = '…';
  $('.m-yahoo', m).href = EXT_QUOTE_URL + encodeURIComponent(sym);
  try {
    const q = new URLSearchParams({ action: 'stock', symbol: sym, window: $('#taWindow').value });
    TA_DATA = await (await fetch('api.php?' + q.toString())).json();
  } catch (e) { $('.m-name', m).textContent = 'kunne ikke hente data'; return; }
  $('.m-name', m).textContent = TA_DATA.name || '';
  $('.modal-sub', m).textContent = [TA_DATA.sector, TA_DATA.country, TA_DATA.currency].filter(Boolean).join(' · ');
  renderTA();
}
function closeStock() {
  $('#stockModal').hidden = true;
  if (taPriceChart) { taPriceChart.destroy(); taPriceChart = null; }
  if (taSubChart) { taSubChart.destroy(); taSubChart = null; }
  TA_DATA = null;
}

function renderTA() {
  if (!TA_DATA || !TA_DATA.points || !TA_DATA.points.length) return;
  const pts = TA_DATA.points;
  const ts = pts.map(p => Date.parse(p[0]));
  const close = pts.map(p => p[1]);
  const vol = pts.map(p => p[2]);
  const b0 = close[0] || 1;
  const b100 = close.map(c => c / b0 * 100);
  const xy = arr => arr.map((v, i) => ({ x: ts[i], y: (v == null ? null : v) }));

  const ds = [{ label: TA_DATA.symbol, data: xy(b100), borderColor: '#5b8def', borderWidth: 1.6, pointRadius: 0, tension: .1 }];
  const SMA_COL = { 20: '#ffca3a', 50: '#2ec4b6', 100: '#ff9f1c', 200: '#f15bb5' };
  $$('.ta-smas input:checked').forEach(c => {
    const p = +c.dataset.sma;
    ds.push({ label: 'SMA' + p, data: xy(sma(b100, p)), borderColor: SMA_COL[p], borderWidth: 1.2, pointRadius: 0, tension: .1 });
  });
  if ($('#taBench').checked && TA_DATA.bench && TA_DATA.bench.points.length) {
    const bp = TA_DATA.bench.points, bb0 = bp[0][1] || 1;
    ds.push({ label: TA_DATA.bench.label, data: bp.map(p => ({ x: Date.parse(p[0]), y: p[1] / bb0 * 100 })),
      borderColor: '#e6edf3', borderWidth: 2, borderDash: [6, 4], pointRadius: 0, tension: .1 });
  }
  if (taPriceChart) taPriceChart.destroy();
  taPriceChart = new Chart($('#taPrice'), { type: 'line', data: { datasets: ds }, options: baseOpts('Base 100') });

  const sub = $('#taSub').value; let sds, opts;
  if (sub === 'rsi') {
    sds = [
      { label: 'RSI(14)', data: xy(rsi(close, 14)), borderColor: '#9b5de5', borderWidth: 1.4, pointRadius: 0, tension: .1 },
      { label: '70', data: ts.map(t => ({ x: t, y: 70 })), borderColor: 'rgba(248,81,73,.4)', borderWidth: 1, borderDash: [4,4], pointRadius: 0 },
      { label: '30', data: ts.map(t => ({ x: t, y: 30 })), borderColor: 'rgba(63,185,80,.4)', borderWidth: 1, borderDash: [4,4], pointRadius: 0 },
    ];
    opts = baseOpts('RSI'); opts.scales.y.min = 0; opts.scales.y.max = 100;
  } else if (sub === 'macd') {
    const m = macd(b100);
    sds = [
      { type: 'bar', label: 'Histogram', data: xy(m.hist), backgroundColor: m.hist.map(v => v >= 0 ? 'rgba(63,185,80,.5)' : 'rgba(248,81,73,.5)') },
      { label: 'MACD', data: xy(m.macd), borderColor: '#5b8def', borderWidth: 1.3, pointRadius: 0, tension: .1 },
      { label: 'Signal', data: xy(m.signal), borderColor: '#ff9f1c', borderWidth: 1.3, pointRadius: 0, tension: .1 },
    ];
    opts = baseOpts('MACD');
  } else {
    sds = [{ type: 'bar', label: 'Volumen', data: xy(vol), backgroundColor: 'rgba(91,141,239,.5)' }];
    opts = baseOpts('Volumen');
  }
  if (taSubChart) taSubChart.destroy();
  taSubChart = new Chart($('#taSubChart'), { type: 'line', data: { datasets: sds }, options: opts });
}

function baseOpts(yTitle) {
  return {
    responsive: true, maintainAspectRatio: false, animation: false,
    interaction: { mode: 'index', intersect: false },
    scales: {
      x: { type: 'linear', ticks: { color: '#9aa4b2', maxTicksLimit: 8,
            callback: v => new Date(v).toLocaleDateString('da-DK', { year: '2-digit', month: 'short' }) }, grid: { color: '#1c2330' } },
      y: { ticks: { color: '#9aa4b2' }, grid: { color: '#1c2330' }, title: { display: true, text: yTitle, color: '#9aa4b2' } },
    },
    plugins: { legend: { position: 'bottom', labels: { color: '#cbd3df', boxWidth: 12, font: { size: 10 } } } },
  };
}

// ---- indikator-matematik ----
function sma(a, p) { const o = Array(a.length).fill(null); let s = 0; for (let i = 0; i < a.length; i++) { s += a[i]; if (i >= p) s -= a[i-p]; if (i >= p-1) o[i] = s/p; } return o; }
function ema(a, p) { const o = Array(a.length).fill(null); const k = 2/(p+1); let prev = null;
  for (let i = 0; i < a.length; i++) { if (a[i] == null) continue;
    if (prev == null) { let s = 0, c = 0; for (let j = Math.max(0,i-p+1); j <= i; j++) if (a[j] != null) { s += a[j]; c++; }
      if (c >= p) { prev = s/c; o[i] = prev; } }
    else { prev = a[i]*k + prev*(1-k); o[i] = prev; } }
  return o; }
function rsi(a, p) { const o = Array(a.length).fill(null); let g = 0, l = 0;
  for (let i = 1; i < a.length; i++) { const ch = a[i]-a[i-1], gg = Math.max(0,ch), ll = Math.max(0,-ch);
    if (i <= p) { g += gg; l += ll; if (i === p) { g/=p; l/=p; o[i] = 100 - 100/(1+(l===0?1e9:g/l)); } }
    else { g = (g*(p-1)+gg)/p; l = (l*(p-1)+ll)/p; o[i] = 100 - 100/(1+(l===0?1e9:g/l)); } }
  return o; }
function macd(a) { const e12 = ema(a,12), e26 = ema(a,26);
  const line = a.map((_, i) => (e12[i]!=null && e26[i]!=null) ? e12[i]-e26[i] : null);
  const sig = ema(line, 9);
  const hist = line.map((v, i) => (v!=null && sig[i]!=null) ? v - sig[i] : null);
  return { macd: line, signal: sig, hist }; }
