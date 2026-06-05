<?php
require __DIR__ . '/lib/stats.php';
require __DIR__ . '/lib/markdown.php';

$planHtml = is_file(__DIR__ . '/../PLAN.md')
    ? render_markdown(file_get_contents(__DIR__ . '/../PLAN.md')) : '';

$ready = screener_exists();
if ($ready) {
    $h        = stat_headline();
    $runs     = stat_runs();
    $sectors  = stat_breakdown('sector');
    $countries= stat_breakdown('country');
    $industries = stat_breakdown('industry', 12);
    $types    = stat_breakdown('quote_type', 8);
    $hist     = stat_history_buckets();
}
?>
<!doctype html>
<html lang="da">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>StockScreener — Universet</title>
<link rel="stylesheet" href="assets/style.css">
<script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js"></script>
</head>
<body>
<header class="topbar">
  <div class="brand">📈 StockScreener</div>
  <nav><a href="#universe" class="active">Universet</a><a href="#plan">Plan &amp; status</a></nav>
</header>

<?php if (!$ready): ?>
  <main class="wrap">
    <div class="card empty">
      <h2>Ingen forudberegnet data endnu</h2>
      <p>Kør <code>php bin/precompute.php</code> for at bygge <code>stockOverview_screener</code>, så vises universet her.</p>
    </div>
  </main>
<?php else: ?>
<main class="wrap" id="universe">

  <section class="kpis">
    <div class="kpi"><div class="kpi-num"><?= fmt_int($h['total']) ?></div><div class="kpi-lbl">aktier i screeneren</div></div>
    <div class="kpi"><div class="kpi-num"><?= fmt_int($h['with_metrics']) ?></div><div class="kpi-lbl">med beregnede metrics</div></div>
    <div class="kpi"><div class="kpi-num">$<?= number_format((float)$h['total_cap_t'],1,',','.') ?> bio</div><div class="kpi-lbl">samlet markedsværdi (USD)</div></div>
    <div class="kpi"><div class="kpi-num"><?= fmt_int($h['price_rows']) ?></div><div class="kpi-lbl">kurspunkter i DB</div></div>
    <div class="kpi"><div class="kpi-num"><?= $h['earliest'] ?> → <?= $h['latest'] ?></div><div class="kpi-lbl">dato-spænd</div></div>
    <div class="kpi <?= $h['suspect_data']>0?'warn':'' ?>"><div class="kpi-num"><?= fmt_int($h['suspect_data']) ?></div><div class="kpi-lbl">mistænkt datakvalitet (&gt;100%/dag)</div></div>
  </section>

  <section class="card runstatus">
    <h3>Kørsels-status</h3>
    <div class="runs-grid">
      <div>
        <div class="run-h">Data-indsamling (ingest)</div>
        <?php $ing=$runs['ingest']; $ok=$ing['ok']['n']??0; $nf=$ing['not_found']['n']??0; $er=$ing['error']['n']??0; $last=$ing['ok']['last']??null; ?>
        <div class="run-line"><span class="ok">✔ <?= fmt_int($ok) ?> ok</span> · <span class="muted"><?= fmt_int($nf) ?> ikke fundet</span> · <span class="err"><?= fmt_int($er) ?> fejl</span></div>
        <div class="run-sub">Sidst aktiv: <?= fmt_ago($last) ?></div>
      </div>
      <div>
        <div class="run-h">Precompute</div>
        <?php if ($runs['precompute']): $p=$runs['precompute']; ?>
          <div class="run-line"><span class="ok">✔ <?= fmt_int($p['symbols']) ?> aktier</span> · <?= fmt_int($p['with_metrics']) ?> med metrics</div>
          <div class="run-sub">Sidste kørsel: <?= fmt_ago($p['finished_at']) ?></div>
        <?php else: ?><div class="run-line muted">Endnu ikke kørt</div><?php endif; ?>
      </div>
    </div>
  </section>

  <section class="charts">
    <div class="card"><h3>Sektorer</h3><canvas id="cSector"></canvas></div>
    <div class="card"><h3>Lande (top 15)</h3><canvas id="cCountry"></canvas></div>
    <div class="card"><h3>Industrier (top 12)</h3><canvas id="cIndustry"></canvas></div>
    <div class="card half"><h3>Type</h3><canvas id="cType"></canvas></div>
    <div class="card half"><h3>Datakvalitet — historik-længde</h3><canvas id="cHist"></canvas></div>
  </section>

</main>

<script>
const PALETTE = ['#5b8def','#2ec4b6','#ff9f1c','#e71d73','#9b5de5','#00bbf9','#80ed99','#f15bb5','#fee440','#ff70a6','#8ac926','#ff595e','#6a4c93','#1982c4','#52a675'];
function bar(id, rows, label) {
  new Chart(document.getElementById(id), {
    type: 'bar',
    data: { labels: rows.map(r=>r.label), datasets:[{ label, data: rows.map(r=>+r.n), backgroundColor:'#5b8def' }] },
    options: { indexAxis:'y', plugins:{legend:{display:false}}, scales:{x:{ticks:{color:'#9aa4b2'}},y:{ticks:{color:'#cbd3df'}}} }
  });
}
function donut(id, rows) {
  new Chart(document.getElementById(id), {
    type: 'doughnut',
    data: { labels: rows.map(r=>r.label), datasets:[{ data: rows.map(r=>+r.n), backgroundColor: PALETTE }] },
    options: { plugins:{legend:{position:'right',labels:{color:'#cbd3df',boxWidth:12}}} }
  });
}
bar('cSector', <?= json_encode($sectors) ?>, 'Selskaber');
bar('cCountry', <?= json_encode($countries) ?>, 'Selskaber');
bar('cIndustry', <?= json_encode($industries) ?>, 'Selskaber');
donut('cType', <?= json_encode($types) ?>);
bar('cHist', <?= json_encode($hist) ?>, 'Aktier');
</script>
<?php endif; ?>

<section class="wrap plan" id="plan">
  <div class="card">
    <?= $planHtml ?>
  </div>
</section>

<footer class="foot">StockScreener · kører på HTPC (PHP + MySQL) · data fra Yahoo Finance via stockOverview-ingesten</footer>
</body>
</html>
