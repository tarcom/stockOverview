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

// Label "lo – hi" for en range-slider. Viser "100+" / "≤-100%" når hard-grænsen
// afskærer ægte data — yderpunktet sætter INTET filter, så de aktier er stadig MED.
function rangeLabel(el) {
  const d = DOMAINS[el.dataset.key]; if (!d) return '';
  const pmin = +$('.r-min', el).value, pmax = +$('.r-max', el).value;
  const lo = posToVal(pmin, d), hi = posToVal(pmax, d);
  const loStr = (pmin === 0 && d.dmin < d.min - 1e-9) ? '≤' + fmtVal(d.min, d.fmt) : fmtVal(lo, d.fmt);
  const hiStr = (pmax === 1000 && d.dmax > d.max + 1e-9) ? fmtVal(d.max, d.fmt) + '+' : fmtVal(hi, d.fmt);
  return loStr + ' – ' + hiStr;
}

// ---------- init ----------
// ---------- loading-overlay: omtrentlig progress, færdig når første graf er tegnet ----------
let FIRST_LOAD = true, loadCreep = null;
function loadProg(pct) { const b = $('#loadBar'); if (b) b.style.width = Math.min(99, pct) + '%'; }
function loadStartCreep(to) { // glider langsomt mod et loft, så den føles levende mellem stadier
  clearInterval(loadCreep); let cur = parseFloat($('#loadBar')?.style.width) || 5;
  loadCreep = setInterval(() => { cur += (to - cur) * 0.08; loadProg(cur); if (cur > to - 0.5) clearInterval(loadCreep); }, 120);
}
function loadDone() {
  if (!FIRST_LOAD) return; FIRST_LOAD = false;
  clearInterval(loadCreep);
  const o = $('#loadOverlay'), b = $('#loadBar');
  if (b) b.style.width = '100%';
  if (o) { setTimeout(() => { o.classList.add('done'); setTimeout(() => o.remove(), 500); }, 180); }
}

window.addEventListener('DOMContentLoaded', async () => {
  wireGroups();
  wireControls();
  initTA();
  loadProg(12); loadStartCreep(38);
  try {
    FACETS = await (await fetch('api.php?action=facets')).json();
  } catch (e) { loadDone(); $('#resultWrap').innerHTML = '<div class="err">Kunne ikke hente facetter: ' + e + '</div>'; return; }
  loadProg(45); loadStartCreep(78);
  buildRanges();
  buildMultis();
  initFavorites();
  applyStateFromURL();
  const params = new URLSearchParams(location.search); // læs FØR refresh() (som rydder URL'en)
  if (params.get('rel') === '1') $('#chartRelative').checked = true;   // til verificering/deling
  if (params.get('eq') === '1') $('#chartEqual').checked = true;
  if (params.get('logy') === '1') $('#chartLogY').checked = true;
  const ta = params.get('ta'), preset = params.get('preset');
  if (preset && (window.PRESETS || {})[preset]) applyPreset(preset); // deep-link til preset
  else refresh();
  if (ta) openStock(ta); // deep-link til teknisk analyse
  // sikkerhedsnet: skjul overlay efter 12 s selv hvis noget hænger
  setTimeout(loadDone, 12000);
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
  $('#chartWindow').addEventListener('change', loadChart);  // ny periode → refetch
  $('#chartBench').addEventListener('change', loadChart);
  $('#chartLogY').addEventListener('change', drawOverlay);  // transformationer → kun gentegn
  $('#chartRelative').addEventListener('change', drawOverlay);
  $('#chartEqual').addEventListener('change', drawOverlay);
  $('#chartReset').addEventListener('click', drawOverlay);   // gentegn = fuld visning (nulstiller zoom + y)
  // Shift ved museknap-ned = box-zoom på vej → undgå y-autoskalering bagefter.
  $('#overlayChart').addEventListener('mousedown', e => { if (e.shiftKey) chartBoxZoom = true; });
  wireMiniBrush();   // navigator-graf: træk feltet for at vælge periode
  // tabel-række → fremhæv linje i grafen
  $('#resultWrap').addEventListener('mouseover', e => { const tr = e.target.closest('.clickrow'); if (tr) emphasizeLine(tr.dataset.sym, true); });
  $('#resultWrap').addEventListener('mouseout', e => { const tr = e.target.closest('.clickrow'); if (tr) emphasizeLine(tr.dataset.sym, false); });
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
    const d = { min: r.min, max: r.max, dmin: r.dmin, dmax: r.dmax, scale: r.scale, fmt: r.fmt, edges: r.edges, counts: r.counts };
    DOMAINS[key] = d;
    const rmin = $('.r-min', el), rmax = $('.r-max', el);
    const upd = () => {
      if (+rmin.value > +rmax.value) { // forhindre kryds
        if (document.activeElement === rmin) rmin.value = rmax.value; else rmax.value = rmin.value;
      }
      drawHist(el);
      $('.filt-vals', el).textContent = rangeLabel(el);
      el.classList.toggle('active', +rmin.value > 0 || +rmax.value < 1000);
      markGroupActive(el);
      scheduleRefresh();
    };
    rmin.addEventListener('input', upd);
    rmax.addEventListener('input', upd);
    drawHist(el);
    $('.filt-vals', el).textContent = rangeLabel(el);
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
  if (!rows || !rows.length) { $('#resultWrap').innerHTML = '<div class="empty-r">Ingen aktier matcher. Løsn et filter.</div>'; loadDone(); return; }
  const sortLbl = (window.SORT_OPTS && window.SORT_OPTS[sort]) || sort;
  const I = t => `<span class="info" title="${escAttr(t)}">i</span>`;
  let h = `<table class="rtable"><thead><tr>
    <th>#</th><th>Symbol</th><th>Navn</th><th>Sektor</th><th>Land</th>
    <th class="num">Mkt cap ${I('Markedsværdi i USD (omregnet, så aktier kan sammenlignes på tværs af børser).')}</th>
    <th class="num hl">${escHtml(sortLbl)} ${I('Den metric du sorterer efter — vælg i "Sortér" ovenfor.')}</th>
    <th class="num">Afkast 1Y ${I('Samlet kursafkast de seneste 12 måneder.')}</th>
    <th class="num">Kvalitet 3Y ${I('Kvalitets-score over 3 år: annualiseret eksponentiel vækst × R² (belønner stabil OG høj vækst).')}</th>
    <th class="num">P/E ${I('Kurs ÷ indtjening seneste 12 mdr. Negativ eller meningsløs indtjening vises som N/A.')}</th>
    <th class="num">Udbytte ${I('Årligt udbytte ÷ kurs.')}</th>
    <th>Trend ${I('Mini-kursgraf over den valgte graf-periode (skift under "Periode" på grafen ovenfor — pt. det samme spænd som overlay-grafen).')}</th>
    <th></th></tr></thead><tbody>`;
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
      <td class="spark"><canvas class="sparkcanvas" data-sym="${escAttr(r.symbol)}" width="80" height="22"></canvas></td>
      <td class="ylink"><a href="${yurl}" target="_blank" rel="noopener" title="Åbn aktiens eksterne detaljeside" onclick="event.stopPropagation()">↗</a></td>
    </tr>`;
  });
  $('#resultWrap').innerHTML = h + '</tbody></table>';
  window.CURRENT_SYMBOLS = rows.map(r => r.symbol);
  window.SYMBOL_NAMES = {}; rows.forEach(r => window.SYMBOL_NAMES[r.symbol] = r.name || r.symbol);
  loadChart();
}

// ---------- overlay base-100 graf ----------
const CHART_PALETTE = ['#5b8def','#2ec4b6','#ff9f1c','#e71d73','#9b5de5','#00bbf9','#80ed99',
  '#f15bb5','#ffca3a','#ff70a6','#8ac926','#ff595e','#6a4c93','#1982c4','#52a675','#c77dff'];
let overlayChart = null;
let CHART_RAW = null;       // sidst hentede serier (til transformationer uden ny fetch)

// Aksens dato-format: vis årstal ved første tick og når året skifter, ellers kun måned.
function axisDateFmt(v, i, ticks) {
  const d = new Date(v);
  const m = d.toLocaleDateString('da-DK', { month: 'short' });
  const showYear = i === 0 || !ticks[i-1] || new Date(ticks[i-1].value).getFullYear() !== d.getFullYear();
  return showYear ? m + ' ' + d.getFullYear() : m;
}

async function loadChart() {
  const syms = (window.CURRENT_SYMBOLS || []).slice(0, 16);
  if (!syms.length) { CHART_RAW = null; if (overlayChart) { overlayChart.destroy(); overlayChart = null; } clearSparklines(); loadDone(); return; }
  const win = $('#chartWindow').value, bench = $('#chartBench').value;
  try {
    const q = new URLSearchParams({ action: 'chart', symbols: syms.join('~'), window: win, bench });
    CHART_RAW = await (await fetch('api.php?' + q.toString())).json();
  } catch (e) { loadDone(); return; }
  drawOverlay();
  drawSparklines();
  loadDone();   // første graf tegnet → skjul loading-overlay
}

// Bygger/gentegner overlay-grafen ud fra CHART_RAW + transformationer (relativ, ligevægt, rebase).
function drawOverlay() {
  if (!CHART_RAW) return;
  const relative = $('#chartRelative').checked;
  // Forward-fill benchmark (aktier og benchmark har forskellige handelsdage; eksakt
  // dato-match gav huller). benchFF(dato) = seneste benchmark-værdi på/før datoen.
  const benchFF = (CHART_RAW.bench && CHART_RAW.bench.points.length) ? makeFFill(CHART_RAW.bench.points) : null;

  const datasets = (CHART_RAW.series || []).map((s, i) => {
    let pts = s.points.map(p => [Date.parse(p[0]), p[1]]);
    if (relative && benchFF) pts = s.points.map(p => { const b = benchFF(p[0]); return [Date.parse(p[0]), b ? p[1] / b * 100 : null]; });
    return { label: s.symbol, symbol: s.symbol, data: pts.map(([t, y]) => ({ x: t, y })),
      borderColor: CHART_PALETTE[i % CHART_PALETTE.length], borderWidth: 1.5, pointRadius: 0, tension: 0.1 };
  });
  if (CHART_RAW.bench && CHART_RAW.bench.points.length && !relative) {
    datasets.push({ label: CHART_RAW.bench.label + ' (benchmark)',
      data: CHART_RAW.bench.points.map(p => ({ x: Date.parse(p[0]), y: p[1] })),
      borderColor: '#e6edf3', borderWidth: 2.5, borderDash: [6, 4], pointRadius: 0, tension: 0.1 });
  }
  if ($('#chartEqual').checked) {
    const avg = equalWeightLine(datasets.filter(ds => ds.symbol));
    if (avg.length) datasets.push({ label: 'Ligevægt (gns.)', symbol: null, data: avg,
      borderColor: '#ffca3a', borderWidth: 3, pointRadius: 0, tension: 0.1, borderDash: [2, 2] });
  }

  // Bind x-aksen eksakt til data-spændet, så Chart.js ikke polstrer ud til en rund
  // tick-grænse (= tomt område til højre). Sidste punkt = yderst til højre.
  let xmin = Infinity, xmax = -Infinity;
  datasets.forEach(ds => ds.data.forEach(pt => { if (pt.y != null) { if (pt.x < xmin) xmin = pt.x; if (pt.x > xmax) xmax = pt.x; } }));

  if (overlayChart) overlayChart.destroy();
  overlayChart = new Chart($('#overlayChart'), {
    type: 'line', data: { datasets },
    options: {
      responsive: true, maintainAspectRatio: false, animation: false,
      interaction: { mode: 'nearest', intersect: false },
      onHover: (e, els, chart) => {
        $$('.rtable tr.hl-row').forEach(r => r.classList.remove('hl-row'));
        if (els.length) {
          const sym = chart.data.datasets[els[0].datasetIndex].symbol;
          if (sym) { const tr = document.querySelector('.rtable tr[data-sym="' + sym + '"]'); if (tr) tr.classList.add('hl-row'); }
        }
      },
      scales: {
        x: { type: 'linear', min: isFinite(xmin) ? xmin : undefined, max: isFinite(xmax) ? xmax : undefined,
             ticks: { color: '#9aa4b2', maxTicksLimit: 8, callback: axisDateFmt }, grid: { color: '#1c2330' } },
        y: { type: $('#chartLogY').checked ? 'logarithmic' : 'linear',
             ticks: { color: '#9aa4b2' }, grid: { color: '#1c2330' },
             title: { display: true, text: relative ? 'Relativt til benchmark (100=match)' : 'Base 100', color: '#9aa4b2' } },
      },
      plugins: {
        legend: { position: 'bottom', labels: { color: '#cbd3df', boxWidth: 12, font: { size: 11 } },
          onClick: (e, item) => {                   // klik forklaring → teknisk analyse
            const ds = overlayChart.data.datasets[item.datasetIndex];
            if (ds && ds.symbol) openStock(ds.symbol);
            else { const m = overlayChart.getDatasetMeta(item.datasetIndex); m.hidden = !m.hidden; overlayChart.update(); }
          } },
        tooltip: { callbacks: {
          title: items => items.length ? new Date(items[0].parsed.x).toLocaleDateString('da-DK') : '',
          label: c => { const s = c.dataset.symbol; const nm = s ? (window.SYMBOL_NAMES[s] || s) : c.dataset.label;
            return `${nm}: ${c.parsed.y.toFixed(1)}`; },
        } },
        zoom: {
          // scroll/pinch zoomer x; y auto-skaleres bagefter til det viste vindue.
          // Shift+træk = box-zoom på et område (x+y) — y bevares som tegnet.
          zoom: {
            wheel: { enabled: true },
            pinch: { enabled: true },
            drag: { enabled: true, modifierKey: 'shift',
                    backgroundColor: 'rgba(91,141,239,0.2)', borderColor: '#5b8def', borderWidth: 1 },
            mode: 'xy',
            onZoomComplete: ({ chart }) => { if (chartBoxZoom) { chartBoxZoom = false; } else { autoScaleY(chart); } syncMiniFromBig(); },
          },
          pan: { enabled: true, mode: 'x', onPanComplete: ({ chart }) => { autoScaleY(chart); syncMiniFromBig(); } }, // træk = panorér x
        },
      },
    },
  });
  // Navigator-graf: fuldt data-spænd, brush = det viste vindue (nulstilles til fuldt ved gentegn).
  DATA_XMIN = isFinite(xmin) ? xmin : 0; DATA_XMAX = isFinite(xmax) ? xmax : 1;
  MINI_SEL = { min: DATA_XMIN, max: DATA_XMAX };
  drawMini(datasets);
}

// ---------- Navigator-graf (lille oversigt under hovedgrafen, Google-Finance-stil) ----------
let miniChart = null, MINI_SEL = null, DATA_XMIN = 0, DATA_XMAX = 1, miniDrag = null;

// Plugin: dæmp området udenfor det valgte vindue + tegn markerings-felt med håndtag.
const miniBrush = {
  id: 'miniBrush',
  afterDraw(chart) {
    if (!MINI_SEL) return;
    const { ctx, chartArea: ca, scales: { x } } = chart;
    const x1 = Math.max(ca.left, Math.min(ca.right, x.getPixelForValue(MINI_SEL.min)));
    const x2 = Math.max(ca.left, Math.min(ca.right, x.getPixelForValue(MINI_SEL.max)));
    ctx.save();
    ctx.fillStyle = 'rgba(14,17,23,0.62)';
    ctx.fillRect(ca.left, ca.top, x1 - ca.left, ca.height);
    ctx.fillRect(x2, ca.top, ca.right - x2, ca.height);
    ctx.fillStyle = 'rgba(91,141,239,0.10)';
    ctx.fillRect(x1, ca.top, x2 - x1, ca.height);
    ctx.strokeStyle = '#5b8def'; ctx.lineWidth = 1;
    ctx.strokeRect(x1 + 0.5, ca.top + 0.5, x2 - x1 - 1, ca.height - 1);
    ctx.fillStyle = '#5b8def';                                   // håndtag i hver side
    ctx.fillRect(x1 - 1, ca.top, 3, ca.height); ctx.fillRect(x2 - 1, ca.top, 3, ca.height);
    ctx.restore();
  },
};

function drawMini(datasets) {
  const thin = datasets.filter(ds => ds.symbol || ds.label).map(ds => ({
    data: ds.data, borderColor: ds.borderColor, borderWidth: 0.8, pointRadius: 0,
    tension: 0.1, borderDash: ds.borderDash, fill: false,
  }));
  if (miniChart) miniChart.destroy();
  miniChart = new Chart($('#overlayMini'), {
    type: 'line', data: { datasets: thin },
    options: {
      responsive: true, maintainAspectRatio: false, animation: false,
      events: [], interaction: { mode: null }, // ingen hover/tooltip — vi styrer selv via pointer-events
      scales: {
        x: { type: 'linear', min: DATA_XMIN, max: DATA_XMAX, display: false },
        y: { display: false },
      },
      plugins: { legend: { display: false }, tooltip: { enabled: false } },
    },
    plugins: [miniBrush],
  });
}

// Sæt det store charts x-vindue ud fra brush'en (uden at røre mini'ens egen tilstand).
function applyBrushToBig() {
  if (!overlayChart || !MINI_SEL) return;
  overlayChart.options.scales.x.min = MINI_SEL.min;
  overlayChart.options.scales.x.max = MINI_SEL.max;
  autoScaleY(overlayChart);   // kalder selv update()
}
// Opdatér brush'en ud fra det store charts aktuelle zoom (kaldes efter zoom/pan).
function syncMiniFromBig() {
  if (!overlayChart || !miniChart) return;
  const xs = overlayChart.scales.x;
  MINI_SEL = { min: Math.max(DATA_XMIN, xs.min), max: Math.min(DATA_XMAX, xs.max) };
  miniChart.update('none');
}

// Pointer-styring på navigatoren: træk kanter (resize), træk midten (flyt), klik udenfor (nyt felt).
function wireMiniBrush() {
  const cv = $('#overlayMini');
  const valAt = px => miniChart.scales.x.getValueForPixel(px);
  const HANDLE = 7;
  cv.addEventListener('pointerdown', e => {
    if (!miniChart || !MINI_SEL) return;
    const x = miniChart.scales.x, px = e.offsetX;
    const x1 = x.getPixelForValue(MINI_SEL.min), x2 = x.getPixelForValue(MINI_SEL.max);
    if (Math.abs(px - x1) <= HANDLE) miniDrag = { mode: 'l' };
    else if (Math.abs(px - x2) <= HANDLE) miniDrag = { mode: 'r' };
    else if (px > x1 && px < x2) miniDrag = { mode: 'move', startPx: px, snap: { ...MINI_SEL } };
    else { const v = valAt(px); MINI_SEL = { min: v, max: v }; miniDrag = { mode: 'r' }; } // start nyt felt
    cv.setPointerCapture(e.pointerId);
    e.preventDefault();
  });
  cv.addEventListener('pointermove', e => {
    if (!miniDrag || !miniChart) return;
    const x = miniChart.scales.x, span = DATA_XMAX - DATA_XMIN, minW = span * 0.01;
    const clamp = v => Math.max(DATA_XMIN, Math.min(DATA_XMAX, v));
    if (miniDrag.mode === 'l') MINI_SEL.min = clamp(Math.min(valAt(e.offsetX), MINI_SEL.max - minW));
    else if (miniDrag.mode === 'r') MINI_SEL.max = clamp(Math.max(valAt(e.offsetX), MINI_SEL.min + minW));
    else if (miniDrag.mode === 'move') {
      const d = valAt(e.offsetX) - valAt(miniDrag.startPx);
      let lo = miniDrag.snap.min + d, hi = miniDrag.snap.max + d;
      const w = hi - lo;
      if (lo < DATA_XMIN) { lo = DATA_XMIN; hi = lo + w; }
      if (hi > DATA_XMAX) { hi = DATA_XMAX; lo = hi - w; }
      MINI_SEL = { min: lo, max: hi };
    }
    miniChart.update('none');
    applyBrushToBig();
  });
  const end = e => { if (miniDrag) { miniDrag = null; try { cv.releasePointerCapture(e.pointerId); } catch (_) {} } };
  cv.addEventListener('pointerup', end);
  cv.addEventListener('pointercancel', end);
}

// Sættes når brugeren starter en Shift+træk (box-zoom), så y ikke auto-skaleres bagefter.
let chartBoxZoom = false;
// Skalér y-aksen til min/max for de synlige datapunkter i det aktuelle x-vindue (+ 5% luft).
function autoScaleY(chart) {
  const xs = chart.scales.x, xmin = xs.min, xmax = xs.max;
  let lo = Infinity, hi = -Infinity;
  chart.data.datasets.forEach((ds, i) => {
    if (chart.getDatasetMeta(i).hidden) return;
    ds.data.forEach(pt => { if (pt.y != null && pt.x >= xmin && pt.x <= xmax) { if (pt.y < lo) lo = pt.y; if (pt.y > hi) hi = pt.y; } });
  });
  if (!isFinite(lo) || !isFinite(hi)) return;
  const pad = (hi - lo) * 0.05 || Math.abs(hi) * 0.05 || 1;
  const isLog = chart.options.scales.y.type === 'logarithmic';
  chart.options.scales.y.min = isLog ? Math.max(lo * 0.95, lo > 0 ? lo * 0.5 : 0.01) : lo - pad;
  chart.options.scales.y.max = hi + pad;
  chart.update('none');
}
// Forward-fill-opslag: returnerer en funktion dato-streng → seneste værdi på/før datoen.
function makeFFill(points) {
  const ds = points.map(p => p[0]), ys = points.map(p => p[1]);
  return d => {
    let lo = 0, hi = ds.length - 1, res = null;
    while (lo <= hi) { const m = (lo + hi) >> 1; if (ds[m] <= d) { res = ys[m]; lo = m + 1; } else hi = m - 1; }
    return res;
  };
}
// Ligevægts-linje: gennemsnit på et FÆLLES dato-gitter (forward-fill pr. aktie), så
// linjen ikke hopper pga. at forskellige aktier handler på forskellige dage.
function equalWeightLine(datasets) {
  const grid = [...new Set(datasets.flatMap(ds => ds.data.filter(p => p.y != null).map(p => p.x)))].sort((a, b) => a - b);
  if (!grid.length) return [];
  const ffs = datasets.map(ds => {
    const pts = ds.data.filter(p => p.y != null).sort((a, b) => a.x - b.x);
    let i = 0;
    return grid.map(x => { while (i + 1 < pts.length && pts[i + 1].x <= x) i++; return (pts.length && pts[0].x <= x) ? pts[i].y : null; });
  });
  return grid.map((x, gi) => { let s = 0, n = 0; ffs.forEach(f => { if (f[gi] != null) { s += f[gi]; n++; } }); return n ? { x, y: s / n } : null; })
    .filter(Boolean);
}

// ---------- sparklines i tabellen (genbruger graf-data) ----------
function drawSparklines() {
  const map = {}; (CHART_RAW && CHART_RAW.series || []).forEach(s => map[s.symbol] = s.points);
  $$('.sparkcanvas').forEach(cv => drawSpark(cv, map[cv.dataset.sym]));
}
function clearSparklines() { $$('.sparkcanvas').forEach(cv => { const c = cv.getContext('2d'); c.clearRect(0, 0, cv.width, cv.height); }); }
function drawSpark(cv, pts) {
  const ctx = cv.getContext('2d'), w = cv.width, h = cv.height; ctx.clearRect(0, 0, w, h);
  if (!pts || pts.length < 2) return;
  const ys = pts.map(p => p[1]); let mn = Math.min(...ys), mx = Math.max(...ys); if (mx === mn) mx = mn + 1;
  ctx.strokeStyle = ys[ys.length - 1] >= ys[0] ? '#3fb950' : '#f85149'; ctx.lineWidth = 1; ctx.beginPath();
  pts.forEach((p, i) => {
    const x = (i / (pts.length - 1)) * (w - 2) + 1;
    const y = h - 1 - ((p[1] - mn) / (mx - mn)) * (h - 2);
    i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y);
  });
  ctx.stroke();
}
// Tabel-række → fremhæv aktiens linje i grafen.
function emphasizeLine(sym, on) {
  if (!overlayChart) return;
  const ds = overlayChart.data.datasets.find(d => d.symbol === sym);
  if (ds) { ds.borderWidth = on ? 4 : 1.5; overlayChart.update('none'); }
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
    $('.filt-vals', el).textContent = rangeLabel(el);
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
    const d = DOMAINS[el.dataset.key]; if (d) $('.filt-vals', el).textContent = rangeLabel(el); });
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
      $('.filt-vals', el).textContent = rangeLabel(el);
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
    items.push({ key: el.dataset.key, type: 'range', text: el.dataset.label + ': ' + rangeLabel(el) });
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
    const d = DOMAINS[key]; if (d) $('.filt-vals', el).textContent = rangeLabel(el);
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
  $('.modal-sub', m).textContent = [TA_DATA.sector, TA_DATA.country, TA_DATA.exchange, TA_DATA.currency].filter(Boolean).join(' · ');
  renderTA();
  renderDetails();
}

// fundamentals + sektor-percentiler + beskrivelse (statisk pr. aktie)
function renderDetails() {
  const d = TA_DATA, m = d.meta || {};
  const FUND = [
    ['Markedsværdi', 'mkt_cap_usd', 'usd'], ['P/E', 'trailing_pe', 'num'], ['P/B', 'price_to_book', 'num'],
    ['EV/EBITDA', 'ev_to_ebitda', 'num'], ['ROE', 'return_on_equity', 'pct'], ['Nettomargin', 'profit_margins', 'pct'],
    ['Gæld/EK', 'debt_to_equity', 'num'], ['Udbytte', 'dividend_yield', 'pct'], ['Kvalitet 1Y', 'quality_1y', 'num'],
    ['CAGR 3Y', 'cagr_3y', 'pct'], ['Afkast 1Y', 'ret_1y', 'pct'], ['Sharpe 1Y', 'sharpe_1y', 'num'],
    ['Beta', 'beta_1y', 'num'], ['Markeds-korr²', 'mkt_r2_1y', 'num'], ['Max drawdown 1Y', 'maxdd_1y', 'pct'],
  ];
  const SIGNED = new Set(['return_on_equity','profit_margins','cagr_3y','ret_1y','maxdd_1y']);
  $('#taFundGrid').innerHTML = FUND.map(([lbl, key, fmt]) =>
    m[key] == null ? '' : `<div class="fund-cell"><span class="fl">${lbl}</span><span class="fv ${SIGNED.has(key) ? cls(+m[key]) : ''}">${fmtVal(+m[key], fmt)}</span></div>`
  ).join('');

  $('#taSectorName').textContent = d.sector ? '(' + d.sector + ', ' + (d.percentiles._n || 0) + ' selskaber)' : '';
  const PCT = [
    ['P/E', 'trailing_pe', false], ['P/B', 'price_to_book', false], ['EV/EBITDA', 'ev_to_ebitda', false],
    ['ROE', 'return_on_equity', true], ['Nettomargin', 'profit_margins', true], ['Kvalitet 1Y', 'quality_1y', true],
    ['Afkast 1Y', 'ret_1y', true], ['Udbytte', 'dividend_yield', true], ['Omsætningsvækst', 'revenue_growth', true],
  ];
  const p = d.percentiles || {};
  const html = PCT.map(([lbl, key, higher]) => {
    if (p[key] == null) return '';
    const pct = +p[key];
    const good = higher ? pct * 100 : (1 - pct) * 100;
    const n = Math.min(99, Math.round(good));
    const txt = higher ? `højere end ${n}%` : `lavere end ${n}%`;
    return `<div class="pct-row"><span class="pl">${lbl}</span>
      <span class="pbar"><span style="width:${good.toFixed(0)}%"></span></span>
      <span class="pv">${txt}</span></div>`;
  }).join('');
  $('#taPercentiles').innerHTML = html || '<div class="muted">Ingen sektor-data.</div>';

  // "Om selskabet" skjules helt hvis der ingen beskrivelse er (fonde/indeks har aldrig én)
  const desc = (d.summary || '').trim();
  const descBlock = $('#taSummary').closest('.ta-block');
  if (desc) { $('#taSummary').textContent = desc; descBlock.style.display = ''; }
  else { descBlock.style.display = 'none'; }
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
  const xmin = ts[0], xmax = ts[ts.length - 1];   // bind x-akse til data (intet tomt felt til højre)
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
  taPriceChart = new Chart($('#taPrice'), { type: 'line', data: { datasets: ds }, options: baseOpts('Base 100', xmin, xmax) });

  const sub = $('#taSub').value; let sds, opts;
  if (sub === 'rsi') {
    sds = [
      { label: 'RSI(14)', data: xy(rsi(close, 14)), borderColor: '#9b5de5', borderWidth: 1.4, pointRadius: 0, tension: .1 },
      { label: '70', data: ts.map(t => ({ x: t, y: 70 })), borderColor: 'rgba(248,81,73,.4)', borderWidth: 1, borderDash: [4,4], pointRadius: 0 },
      { label: '30', data: ts.map(t => ({ x: t, y: 30 })), borderColor: 'rgba(63,185,80,.4)', borderWidth: 1, borderDash: [4,4], pointRadius: 0 },
    ];
    opts = baseOpts('RSI', xmin, xmax); opts.scales.y.min = 0; opts.scales.y.max = 100;
  } else if (sub === 'macd') {
    const m = macd(b100);
    sds = [
      { type: 'bar', label: 'Histogram', data: xy(m.hist), backgroundColor: m.hist.map(v => v >= 0 ? 'rgba(63,185,80,.5)' : 'rgba(248,81,73,.5)') },
      { label: 'MACD', data: xy(m.macd), borderColor: '#5b8def', borderWidth: 1.3, pointRadius: 0, tension: .1 },
      { label: 'Signal', data: xy(m.signal), borderColor: '#ff9f1c', borderWidth: 1.3, pointRadius: 0, tension: .1 },
    ];
    opts = baseOpts('MACD', xmin, xmax);
  } else {
    sds = [{ type: 'bar', label: 'Volumen', data: xy(vol), backgroundColor: 'rgba(91,141,239,.5)' }];
    opts = baseOpts('Volumen', xmin, xmax);
  }
  if (taSubChart) taSubChart.destroy();
  taSubChart = new Chart($('#taSubChart'), { type: 'line', data: { datasets: sds }, options: opts });
}

function baseOpts(yTitle, xmin, xmax) {
  return {
    responsive: true, maintainAspectRatio: false, animation: false,
    interaction: { mode: 'index', intersect: false },
    scales: {
      x: { type: 'linear', min: xmin, max: xmax, ticks: { color: '#9aa4b2', maxTicksLimit: 8,
            callback: axisDateFmt }, grid: { color: '#1c2330' } },
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
