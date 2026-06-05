<?php
require __DIR__ . '/lib/filters.php';
$sortOpts = [
  'quality_1y'=>'Kvalitets-score 1Y','quality_3y'=>'Kvalitets-score 3Y',
  'cagr_3y'=>'CAGR 3Y','cagr_1y'=>'CAGR 1Y',
  'ret_1y'=>'Afkast 1Y','ret_3y'=>'Afkast 3Y','ret_5y'=>'Afkast 5Y','ret_1m'=>'Afkast 1M',
  'rs_1y'=>'Relativ styrke 1Y','sharpe_1y'=>'Sharpe 1Y','trend_r2_3y'=>'Trend-stabilitet 3Y',
  'mkt_cap_usd'=>'Markedsværdi','dividend_yield'=>'Udbytte %','return_on_equity'=>'ROE',
];
?>
<!doctype html>
<html lang="da">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>StockScreener — Screener</title>
<link rel="stylesheet" href="assets/style.css">
<link rel="stylesheet" href="assets/screener.css">
<script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js"></script>
</head>
<body>
<header class="topbar">
  <div class="brand">📈 StockScreener</div>
  <nav><a href="index.php">Universet</a><a href="screener.php" class="active">Screener</a><a href="index.php#plan">Plan &amp; status</a></nav>
</header>

<main class="screener">
  <aside class="filters" id="filters">
    <div class="filters-head">
      <h2>Filtre</h2>
      <button id="resetBtn" class="btn-ghost">Nulstil</button>
    </div>
    <label class="dq-toggle"><input type="checkbox" id="hideJunk" checked>
      Skjul mistænkte datafejl
      <span class="info" title="Skjuler aktier med &gt;100% kursudsving på én dag — næsten altid korrupt Yahoo-data. Slå fra for at se alt.">i</span></label>

    <?php foreach (flt_groups() as $g): ?>
      <section class="fgroup<?= $g['open'] ? ' open' : '' ?>" data-group="<?= $g['id'] ?>">
        <h3 class="fgroup-h"><span class="caret">▸</span> <?= htmlspecialchars($g['title']) ?>
          <span class="grp-active"></span></h3>
        <div class="fgroup-body">
          <?php foreach ($g['filters'] as $f): ?>
            <?php if ($f['type'] === 'range'): ?>
              <div class="filt" data-key="<?= $f['key'] ?>" data-fmt="<?= $f['fmt'] ?>" data-scale="<?= $f['scale'] ?>">
                <div class="filt-label"><?= htmlspecialchars($f['label']) ?>
                  <span class="info" title="<?= htmlspecialchars($f['info']) ?>">i</span>
                  <span class="filt-vals"></span></div>
                <div class="slider">
                  <canvas class="hist" height="38"></canvas>
                  <div class="track"></div>
                  <input type="range" class="r-min" min="0" max="1000" value="0">
                  <input type="range" class="r-max" min="0" max="1000" value="1000">
                </div>
              </div>
            <?php else: ?>
              <div class="filt multi" data-key="<?= $f['key'] ?>">
                <div class="filt-label"><?= htmlspecialchars($f['label']) ?>
                  <span class="info" title="<?= htmlspecialchars($f['info']) ?>">i</span></div>
                <div class="multi-opts"></div>
              </div>
            <?php endif; ?>
          <?php endforeach; ?>
        </div>
      </section>
    <?php endforeach; ?>
  </aside>

  <section class="results">
    <div class="results-head">
      <div class="count-box"><span id="count">…</span> <span class="muted">/ <span id="total">…</span> aktier matcher</span></div>
      <div class="controls">
        <label>Sortér <select id="sort">
          <?php foreach ($sortOpts as $k => $lbl): ?><option value="<?= $k ?>"><?= $lbl ?></option><?php endforeach; ?>
        </select></label>
        <button id="dirBtn" class="btn-ghost" title="Skift retning">▼ Højest</button>
        <label>Vis <select id="limit"><option>10</option><option selected>20</option><option>50</option><option>100</option></select></label>
        <button id="shareBtn" class="btn-ghost" title="Kopiér delbart link">🔗 Del</button>
      </div>
    </div>
    <div id="funnel" class="funnel"></div>

    <div class="chartbox" id="chartbox">
      <div class="chart-controls">
        <strong>Kursudvikling (base-100)</strong>
        <label>Periode <select id="chartWindow">
          <?php foreach (['1m','3m','6m','1y','2y','3y','5y','10y'] as $w): ?>
            <option value="<?= $w ?>"<?= $w==='3y'?' selected':'' ?>><?= strtoupper($w) ?></option>
          <?php endforeach; ?>
        </select></label>
        <label>Benchmark <select id="chartBench">
          <?php foreach (cfg()['benchmarks'] as $k => $lbl): ?>
            <option value="<?= htmlspecialchars($k) ?>"<?= $k===cfg()['rs_benchmark']?' selected':'' ?>><?= htmlspecialchars($lbl) ?></option>
          <?php endforeach; ?>
          <option value="">(ingen)</option>
        </select></label>
        <span class="muted">alle indekseret til 100 ved start · klik i forklaringen for at skjule linjer</span>
      </div>
      <div class="chart-canvas-wrap"><canvas id="overlayChart"></canvas></div>
    </div>

    <div id="resultWrap" class="result-wrap"><div class="loading">Indlæser…</div></div>
    <p class="results-note">Bemærk: et aktivt filter udelukker automatisk aktier der mangler den datatype. Klik en række for teknisk analyse.</p>
  </section>
</main>

<div id="stockModal" class="modal" hidden>
  <div class="modal-card">
    <div class="modal-head">
      <div class="m-title"><span class="m-sym"></span> <span class="m-name muted"></span></div>
      <button id="modalClose" class="btn-ghost" title="Luk">✕</button>
    </div>
    <div class="modal-sub muted"></div>
    <div class="ta-controls">
      <label>Periode <select id="taWindow">
        <?php foreach (['6m','1y','2y','3y','5y','10y'] as $w): ?>
          <option value="<?= $w ?>"<?= $w==='2y'?' selected':'' ?>><?= strtoupper($w) ?></option>
        <?php endforeach; ?>
      </select></label>
      <span class="ta-smas">SMA
        <label><input type="checkbox" data-sma="20"> 20</label>
        <label><input type="checkbox" data-sma="50" checked> 50</label>
        <label><input type="checkbox" data-sma="100"> 100</label>
        <label><input type="checkbox" data-sma="200" checked> 200</label>
        <span class="info" title="Simpelt glidende gennemsnit (Simple Moving Average) over N dage. Udjævner kursen og viser trenden — når kursen er over SMA200 er den langsigtede trend op.">i</span>
      </span>
      <label><input type="checkbox" id="taBench" checked> S&P500-overlay</label>
      <span>Nederste panel <select id="taSub">
        <option value="rsi">RSI (14)</option>
        <option value="macd">MACD</option>
        <option value="vol">Volumen</option>
      </select>
      <span class="info" title="RSI: momentum 0-100 (>70 overkøbt, <30 oversolgt). MACD: forskel mellem 12- og 26-dages EMA + signallinje — krydsninger antyder trendskift. Volumen: handelsmængde pr. dag.">i</span></span>
    </div>
    <div class="ta-main"><canvas id="taPrice"></canvas></div>
    <div class="ta-pane"><canvas id="taSubChart"></canvas></div>
  </div>
</div>

<script>window.SORT_OPTS = <?= json_encode($sortOpts) ?>;</script>
<script src="assets/screener.js"></script>
</body>
</html>
