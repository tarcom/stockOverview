<?php
require __DIR__ . '/lib/stats.php';
require __DIR__ . '/lib/header.php';

$ready = screener_exists();
if ($ready) {
    $h        = stat_headline();
    $runs     = stat_runs();
    $sectors  = stat_breakdown('sector');
    $countries= stat_breakdown('country');
    $industries = stat_breakdown('industry', 12);
    $hist     = stat_history_buckets();
}
?>
<!doctype html>
<html lang="da">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Stock Screener Lab — screen verdens aktier på data</title>
<link rel="stylesheet" href="assets/style.css?v=<?= filemtime(__DIR__ . '/assets/style.css') ?>">
<script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js"></script>
<style>
  .hero-cta { text-align:center; padding:2.6rem 1rem 2.2rem; margin-bottom:1.4rem;
    background:radial-gradient(120% 140% at 50% 0%, rgba(91,141,239,.16), transparent 70%);
    border-bottom:1px solid rgba(255,255,255,.06); }
  .hero-cta h1 { font-size:clamp(2rem,4.5vw,3rem); font-weight:800; letter-spacing:-.5px; margin:0 0 .5rem;
    background:linear-gradient(90deg,#2ec4b6,#5b8def); -webkit-background-clip:text; background-clip:text; -webkit-text-fill-color:transparent; }
  .hero-cta h1 b { font-weight:800; }
  .hero-cta .hero-lead { max-width:640px; margin:0 auto 1.6rem; color:#aab3c0; line-height:1.6; font-size:1.04rem; }
  .cta-big { display:inline-flex; align-items:center; gap:.5rem; padding:.95rem 2rem; border-radius:50px;
    font-size:1.08rem; font-weight:700; color:#fff; background:linear-gradient(90deg,#2ec4b6,#5b8def);
    box-shadow:0 8px 30px rgba(91,141,239,.4); transition:transform .2s, box-shadow .2s; text-decoration:none; }
  .cta-big:hover { transform:translateY(-2px); box-shadow:0 12px 38px rgba(91,141,239,.55); }
  .hero-sub { margin-top:1rem; font-size:.82rem; color:#7c8696; letter-spacing:.3px; }
</style>
</head>
<body>
<?php render_header('universe'); ?>

<?php if (!$ready): ?>
  <main class="wrap">
    <div class="card empty">
      <h2>Ingen forudberegnet data endnu</h2>
      <p>Kør <code>php bin/precompute.php</code> for at bygge <code>stockOverview_screener</code>, så vises universet her.</p>
    </div>
  </main>
<?php else: ?>
<main class="wrap" id="universe">

  <section class="hero-cta">
    <h1>Stock Screener <b>Lab</b></h1>
    <p class="hero-lead">Filtrér, sammenlign og analysér ~76.000 aktier fra hele verden — på data, ikke på mavefornemmelse. Kvalitets-score, base-100-grafer og teknisk analyse på millisekunder.</p>
    <a class="cta-big" href="screener.php">🔎 Åbn screeneren →</a>
    <div class="hero-sub">Gratis · ingen login · SMA / RSI / MACD · delbare screens</div>
  </section>

  <section class="kpis">
    <div class="kpi"><div class="kpi-num"><?= fmt_int($h['total']) ?></div><div class="kpi-lbl">aktier i screeneren</div></div>
    <div class="kpi"><div class="kpi-num"><?= fmt_int($h['with_metrics']) ?></div><div class="kpi-lbl">med beregnede metrics</div></div>
    <div class="kpi"><div class="kpi-num"><?= fmt_int($h['large_caps']) ?></div><div class="kpi-lbl">store selskaber (&gt; $1 mia.)</div></div>
    <div class="kpi"><div class="kpi-num">≈<?= fmt_int($h['price_rows']) ?></div><div class="kpi-lbl">kurspunkter i DB (estimat)</div></div>
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
    <div class="card"><h3>Datakvalitet — historik-længde</h3><canvas id="cHist"></canvas></div>
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
bar('cHist', <?= json_encode($hist) ?>, 'Aktier');
</script>
<?php endif; ?>

<?php render_footer($ready ? ($h['latest'] ?? null) : null); ?>
</body>
</html>
