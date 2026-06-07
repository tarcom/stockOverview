<?php
require __DIR__ . '/lib/filters.php';
require __DIR__ . '/lib/header.php';
$sortOpts = [
  'quality_1y'=>'Kvalitets-score 1Y','quality_3y'=>'Kvalitets-score 3Y',
  'cagr_3y'=>'CAGR 3Y','cagr_1y'=>'CAGR 1Y',
  'ret_1y'=>'Afkast 1Y','ret_3y'=>'Afkast 3Y','ret_5y'=>'Afkast 5Y','ret_1m'=>'Afkast 1M',
  'rs_1y'=>'Relativ styrke 1Y','sharpe_1y'=>'Sharpe 1Y','trend_r2_3y'=>'Trend-stabilitet 3Y',
  'mkt_cap_usd'=>'Markedsværdi','dividend_yield'=>'Udbytte %','return_on_equity'=>'ROE',
];
// Strategi-presets: ét klik sætter en hel filter-opskrift + sortering.
// (pct-værdier er brøker: 0.15 = 15%. maxdd er negativ. debt_to_equity er Yahoos %-tal.)
$presets = [
  'compounder' => ['label'=>'📈 Stabil compounder', 'sort'=>'quality_3y', 'dir'=>'desc',
    'title'=>'Jævn, vedvarende vækst: høj trend-stabilitet (R²>0.85), positiv 3-års CAGR, begrænset drawdown, mid/large-cap.',
    'filters'=>['trend_r2_3y'=>['min'=>0.85],'cagr_3y'=>['min'=>0.10],'maxdd_3y'=>['min'=>-0.45],'mkt_cap_usd'=>['min'=>1e9],'history_years'=>['min'=>5]]],
  'momentum' => ['label'=>'🚀 Momentum', 'sort'=>'quality_1y', 'dir'=>'desc',
    'title'=>'Stærk nylig stigning: 6M >20% og 1Y >30%, sorteret efter kvalitets-score.',
    'filters'=>['ret_6m'=>['min'=>0.20],'ret_1y'=>['min'=>0.30],'mkt_cap_usd'=>['min'=>3e8]]],
  'value' => ['label'=>'💰 Value', 'sort'=>'trailing_pe', 'dir'=>'asc',
    'title'=>'Billig prissætning: P/E under 15, P/B under 2, positiv ROE, mid/large-cap.',
    'filters'=>['trailing_pe'=>['min'=>0,'max'=>15],'price_to_book'=>['max'=>2],'return_on_equity'=>['min'=>0.08],'mkt_cap_usd'=>['min'=>1e9]]],
  'quality' => ['label'=>'⭐ Kvalitet', 'sort'=>'return_on_equity', 'dir'=>'desc',
    'title'=>'Buffett-stil: høj forrentning (ROE>15%), lav gæld, gode driftsmarginer.',
    'filters'=>['return_on_equity'=>['min'=>0.15],'debt_to_equity'=>['max'=>80],'operating_margins'=>['min'=>0.15],'mkt_cap_usd'=>['min'=>1e9]]],
  'dividend' => ['label'=>'🏦 Udbytte', 'sort'=>'dividend_yield', 'dir'=>'desc',
    'title'=>'Solide udbyttebetalere: yield over 3%, mid/large-cap.',
    'filters'=>['dividend_yield'=>['min'=>0.03],'mkt_cap_usd'=>['min'=>1e9]]],
  'nordnet' => ['label'=>'🇩🇰 Nordnet-handelbar', 'sort'=>'quality_1y', 'dir'=>'desc',
    'title'=>'Kun markeder du typisk kan handle via Nordnet: Norden, USA/Canada og Vesteuropa — ingen direkte asiatiske børser.',
    'filters'=>['mkt_cap_usd'=>['min'=>1e8], 'country'=>['in'=>['Denmark','Sweden','Norway','Finland','Iceland',
      'United States','Canada','Germany','France','Netherlands','Belgium','Italy','Spain','Portugal','Austria',
      'Switzerland','United Kingdom','Ireland','Estonia','Latvia','Lithuania','Poland']]]],
];
?>
<!doctype html>
<html lang="da">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Nørgaard's Aktie Screener</title>
<link rel="stylesheet" href="assets/style.css?v=<?= filemtime(__DIR__ . '/assets/style.css') ?>">
<link rel="stylesheet" href="assets/screener.css?v=<?= filemtime(__DIR__ . '/assets/screener.css') ?>">
<script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/chartjs-plugin-zoom@2.0.1/dist/chartjs-plugin-zoom.min.js"></script>
</head>
<body>
<?php render_header('screener'); ?>

<main class="screener">
  <aside class="filters" id="filters">
    <div class="filters-head">
      <h2>Filtre</h2>
      <button id="resetBtn" class="btn-ghost">Nulstil</button>
    </div>
    <label class="dq-toggle"><input type="checkbox" id="hideJunk" checked>
      Skjul mistænkte datafejl
      <span class="info" title="Skjuler aktier med &gt;100% kursudsving på én dag — næsten altid korrupte kursdata. Slå fra for at se alt.">i</span></label>

    <section class="fgroup open favgroup" id="favGroup" data-group="fav" hidden>
      <h3 class="fgroup-h"><span class="caret">▸</span> ⭐ Favoritter <span class="grp-active"></span></h3>
      <div class="fgroup-body"></div>
    </section>

    <?php foreach (flt_groups() as $g): ?>
      <section class="fgroup<?= $g['open'] ? ' open' : '' ?>" data-group="<?= $g['id'] ?>">
        <h3 class="fgroup-h"><span class="caret">▸</span> <?= htmlspecialchars($g['title']) ?>
          <span class="grp-active"></span></h3>
        <div class="fgroup-body">
          <?php foreach ($g['filters'] as $f): ?>
            <?php if ($f['type'] === 'range'): ?>
              <div class="filt" data-key="<?= $f['key'] ?>" data-label="<?= htmlspecialchars($f['label']) ?>" data-origin="<?= $g['id'] ?>" data-fmt="<?= $f['fmt'] ?>" data-scale="<?= $f['scale'] ?>">
                <div class="filt-label"><span class="fav-star" title="Vis øverst som favorit">☆</span>
                  <?= htmlspecialchars($f['label']) ?>
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
              <div class="filt multi" data-key="<?= $f['key'] ?>" data-label="<?= htmlspecialchars($f['label']) ?>" data-origin="<?= $g['id'] ?>">
                <div class="filt-label"><span class="fav-star" title="Vis øverst som favorit">☆</span>
                  <?= htmlspecialchars($f['label']) ?>
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
    <div class="presets">
      <span class="presets-lbl">Strategier:</span>
      <?php foreach ($presets as $k => $ps): ?>
        <button class="preset" data-preset="<?= $k ?>" title="<?= htmlspecialchars($ps['title']) ?>"><?= htmlspecialchars($ps['label']) ?></button>
      <?php endforeach; ?>
      <span class="info" title="Et preset nulstiller filtrene og sætter en hel opskrift. Du kan finjustere bagefter.">i</span>
    </div>
    <div class="results-head">
      <div class="count-box"><span id="count">…</span> <span class="muted">/ <span id="total">…</span> aktier matcher</span></div>
      <div class="controls">
        <label>Sortér <select id="sort">
          <?php foreach ($sortOpts as $k => $lbl): ?><option value="<?= $k ?>"><?= $lbl ?></option><?php endforeach; ?>
        </select></label>
        <button id="dirBtn" class="btn-ghost" title="Skift retning">▼ Højest</button>
        <label>Vis <select id="limit"><option>10</option><option selected>20</option><option>50</option><option>100</option></select></label>
        <button id="shareBtn" class="btn-ghost" title="Kopiér delbart link">🔗 Del</button>
        <button id="csvBtn" class="btn-ghost" title="Download resultater som CSV">⬇ CSV</button>
        <button id="saveBtn" class="btn-ghost" title="Gem disse filtre som en navngiven screen">💾 Gem</button>
      </div>
    </div>
    <div id="savedScreens" class="saved"></div>
    <div id="chips" class="chips"></div>
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
        <label title="Logaritmisk y-akse — gør at en enkelt aktie med ekstrem stigning ikke klemmer de andre flade"><input type="checkbox" id="chartLogY"> Log y-akse</label>
        <label title="Vis hver aktie relativt til benchmark (flad linje = følger markedet)"><input type="checkbox" id="chartRelative"> Relativ til benchmark</label>
        <label title="Tilføj en linje = gennemsnittet af de viste aktier (ligevægts-portefølje)"><input type="checkbox" id="chartEqual"> Ligevægts-linje</label>
        <button id="chartReset" class="btn-ghost" title="Nulstil zoom, panorering og base-dato">⟲ Nulstil</button>
      </div>
      <div class="chart-canvas-wrap"><canvas id="overlayChart"></canvas></div>
      <div class="chart-help muted">Indekseret til 100 ved start (eller <strong>klik en dato</strong> for at re-indeksere derfra) · <strong>scroll</strong> = zoom, <strong>træk</strong> = panorér · klik i forklaringen for teknisk analyse · hold musen over for navn + highlight i tabellen</div>
    </div>

    <div id="resultWrap" class="result-wrap"><div class="loading">Indlæser…</div></div>
    <p class="results-note">Bemærk: et aktivt filter udelukker automatisk aktier der mangler den datatype. Klik en række for teknisk analyse.</p>
  </section>
</main>

<div id="stockModal" class="modal" hidden>
  <div class="modal-card">
    <div class="modal-head">
      <div class="m-title"><span class="m-sym"></span> <span class="m-name muted"></span></div>
      <div class="m-actions">
        <a class="m-yahoo btn-ghost" target="_blank" rel="noopener" title="Åbn aktiens eksterne detaljeside">Detaljer ↗</a>
        <button id="modalClose" class="btn-ghost" title="Luk (Esc)">✕</button>
      </div>
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

    <div class="ta-details">
      <div class="ta-block">
        <h4>Nøgletal</h4>
        <div class="fund-grid" id="taFundGrid"></div>
      </div>
      <div class="ta-block">
        <h4>I sektoren <span id="taSectorName" class="muted"></span></h4>
        <div id="taPercentiles" class="pcts"></div>
      </div>
      <div class="ta-block ta-desc">
        <h4>Om selskabet</h4>
        <p id="taSummary"></p>
      </div>
    </div>
  </div>
</div>

<?php render_footer(db()->query("SELECT DATE_FORMAT(MAX(computed_at),'%Y-%m-%d') FROM " . t('screener'))->fetchColumn()); ?>

<script>window.SORT_OPTS = <?= json_encode($sortOpts) ?>; window.PRESETS = <?= json_encode($presets) ?>;</script>
<script src="assets/screener.js?v=<?= filemtime(__DIR__ . '/assets/screener.js') ?>"></script>
</body>
</html>
