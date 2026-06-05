'use strict';
// StockScreener — filter-motor (vanilla JS). Henter facetter, bygger sliders med
// histogram bagved + multivalg, og kører live tælling/funnel/resultater mod api.php.

const $  = (s, r = document) => r.querySelector(s);
const $$ = (s, r = document) => Array.from(r.querySelectorAll(s));
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
  try {
    FACETS = await (await fetch('api.php?action=facets')).json();
  } catch (e) { $('#resultWrap').innerHTML = '<div class="err">Kunne ikke hente facetter: ' + e + '</div>'; return; }
  buildRanges();
  buildMultis();
  applyStateFromURL();
  refresh();
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
  $('#resetBtn').addEventListener('click', resetAll);
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
    <th class="num">P/E</th><th class="num">Udbytte</th></tr></thead><tbody>`;
  rows.forEach((r, i) => {
    h += `<tr>
      <td class="muted">${i+1}</td>
      <td class="sym">${escHtml(r.symbol)}</td>
      <td class="nm" title="${escAttr(r.name||'')}">${escHtml(trunc(r.name, 28))}</td>
      <td>${escHtml(r.sector||'–')}</td>
      <td>${escHtml(r.country||'–')}</td>
      <td class="num">${fmtVal(+r.mkt_cap_usd, 'usd')}</td>
      <td class="num hl">${fmtCol(sort, r._sortval)}</td>
      <td class="num ${cls(r.ret_1y)}">${fmtVal(+r.ret_1y, 'pct')}</td>
      <td class="num">${fmtVal(+r.quality_3y, 'num')}</td>
      <td class="num">${fmtVal(+r.trailing_pe, 'num')}</td>
      <td class="num">${fmtVal(+r.dividend_yield, 'pct')}</td>
    </tr>`;
  });
  $('#resultWrap').innerHTML = h + '</tbody></table>';
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
function resetAll() {
  $$('.filt:not(.multi)').forEach(el => { $('.r-min', el).value = 0; $('.r-max', el).value = 1000; el.classList.remove('active'); drawHist(el);
    const d = DOMAINS[el.dataset.key]; if (d) $('.filt-vals', el).textContent = fmtVal(d.min, d.fmt) + ' – ' + fmtVal(d.max, d.fmt); });
  $$('.filt.multi input:checked').forEach(i => i.checked = false);
  $$('.filt.multi').forEach(el => el.classList.remove('active'));
  $$('.grp-active').forEach(s => s.textContent = '');
  $('#hideJunk').checked = true;
  refresh();
}

// ---------- util ----------
function trunc(s, n) { s = s || ''; return s.length > n ? s.slice(0, n-1) + '…' : s; }
function escHtml(s) { return String(s ?? '').replace(/[&<>]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;'}[c])); }
function escAttr(s) { return String(s ?? '').replace(/"/g, '&quot;').replace(/[&<>]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;'}[c])); }
