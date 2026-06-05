<?php
require __DIR__ . '/lib/header.php';
require __DIR__ . '/lib/filters.php';   // samme kilde som filter-tooltipsene (flt_groups → 'info')
?>
<!doctype html>
<html lang="da">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Om &amp; metode — Nørgaard's Aktie Screener</title>
<link rel="stylesheet" href="assets/style.css?v=<?= filemtime(__DIR__ . '/assets/style.css') ?>">
</head>
<body>
<?php render_header('about'); ?>

<main class="about">
  <h1>Om &amp; metode</h1>
  <p class="lead">Nørgaard's Aktie Screener gennemsøger ~150.000 aktier globalt og hjælper dig med at
  finde dem der matcher dine kriterier — med fokus på at finde <strong>stabile, vedvarende vækst-aktier</strong>.</p>

  <h2>Sådan bruger du den</h2>
  <p>På <a href="screener.php">Screener</a>-siden skruer du på filtrene (sliders med histogram bagved, så du
  kan se hvor data ligger). Udfaldsrummet snævres ind live for hvert filter — meningen er at filtrere ned til
  en håndfuld kandidater. De viste aktier sammenlignes på én base-100-graf, og du kan klikke en aktie for
  teknisk analyse (SMA, RSI, MACD, volumen). Strategi-presets sætter en hel opskrift med ét klik.</p>

  <h2>Kvalitets-scoren — stabil + høj vækst</h2>
  <p>Screenerens kernemål. En aktie der vokser med konstant rate er en <em>lige linje i logaritmisk skala</em>.
  Vi laver derfor en lineær regression på <code>ln(kurs)</code> over hvert tidsvindue og kombinerer to tal:</p>
  <ul>
    <li><strong>Hældningen</strong> → den årlige eksponentielle vækstrate (høj = stærk vækst).</li>
    <li><strong>R²</strong> (0–1) → hvor jævnt aktien følger sin egen vækstkurve (høj = rolig, forudsigelig).</li>
  </ul>
  <p>Kvalitets-score = <strong>vækstrate × R²</strong>. Den belønner aktier der stiger både kraftigt
  <em>og</em> jævnt — og straffer kaotiske kursforløb. En aktie der fordobles i et hak får lav score; en der
  stiger som efter en lineal får høj.</p>

  <h2>Alle filtre forklaret</h2>
  <p>Hvert filter på <a href="screener.php">Screener</a>-siden har en lille <span class="info-inline">i</span>
  med samme forklaring du ser her — teksterne kommer fra ét fælles sted, så de aldrig er i utakt.</p>
  <?php foreach (flt_groups() as $g): ?>
    <h3><?= htmlspecialchars($g['title']) ?></h3>
    <table class="filter-help">
      <?php foreach ($g['filters'] as $f): ?>
        <tr><th><?= htmlspecialchars($f['label']) ?></th><td><?= htmlspecialchars($f['info']) ?></td></tr>
      <?php endforeach; ?>
    </table>
  <?php endforeach; ?>

  <h2>Data &amp; opdatering</h2>
  <p>Screeneren bygger på <strong>markedsdata fra offentlige finansielle datakilder</strong>: op til 10 års
  daglig kurshistorik, udbytter og splits, samt fundamentale nøgletal pr. selskab. Alle nøgletal og
  performance-mål er <strong>forudberegnet</strong>, og markedsværdier er omregnet til USD så aktier kan
  sammenlignes på tværs af børser. Data opdateres dagligt.</p>

  <div class="note">
    <strong>Ansvarsfraskrivelse.</strong> Dette værktøj er udelukkende til informations- og uddannelsesformål
    og udgør <strong>ikke finansiel rådgivning</strong>, anbefaling eller opfordring til at købe eller sælge
    værdipapirer. Data kan indeholde fejl og er ikke garanteret korrekte eller aktuelle. Historisk afkast er
    ingen garanti for fremtidigt afkast. Træf altid dine egne beslutninger og søg professionel rådgivning ved
    behov. Brug sker på eget ansvar.
  </div>

  <p>Kan du lide værktøjet? <a href="https://www.buymeacoffee.com/noergaard" target="_blank" rel="noopener">☕ Buy me a coffee</a> — så holdes det gratis og kørende.</p>
</main>

<?php render_footer(); ?>
</body>
</html>
