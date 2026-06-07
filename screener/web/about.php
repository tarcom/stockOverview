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

  <h2>Sådan bruger du graferne</h2>
  <p>De viste aktier sammenlignes på én <strong>base-100-graf</strong>: alle starter i 100 ved periodens
  begyndelse, så kurverne viser den procentvise udvikling og kan sammenlignes direkte uanset kurs.</p>
  <ul>
    <li><strong>Musen over</strong> grafen viser et sigtekors der snapper til nærmeste kurve — med dato på
      x-aksen og %-værdi på y-aksen — plus aktiens navn. Hold musen over en tabel-række for at fremhæve den
      tilsvarende kurve.</li>
    <li><strong>Scroll</strong> = zoom på tids-aksen (y-aksen tilpasser sig automatisk det viste vindue).</li>
    <li><strong>Træk</strong> = panorér (når du har zoomet ind). <strong>Shift+træk</strong> = zoom ind på et
      udvalgt område. <strong>⟲</strong> nulstiller.</li>
    <li><strong>Navigator-grafen</strong> nederst viser hele perioden; træk det markerede felt (kanter, midten
      eller tegn et nyt) for at vælge hvilket udsnit den store graf viser.</li>
    <li><strong>Graf-kolonnen</strong> i tabellen: fjern fluebenet for at skjule en aktie fra grafen (den bliver
      i tabellen). Valget huskes.</li>
  </ul>
  <p>Klik en aktie (eller dens række) for <strong>teknisk analyse</strong> (SMA, RSI, MACD, volumen).</p>

  <h2>Alle filtre forklaret</h2>
  <p>Hvert filter på <a href="screener.php">Screener</a>-siden har en lille <span class="info-inline">i</span>
  med samme forklaring du ser her — teksterne kommer fra ét fælles sted, så de aldrig er i utakt.</p>

  <div class="note">
    <strong>Sådan virker sliderne.</strong> Bag hver slider ligger et <strong>histogram</strong> der viser
    hvor aktierne fordeler sig — så du kan se hvor det giver mening at skubbe håndtagene hen.
    Sliderens interval dækker det <strong>brugbare område</strong>: ekstreme eller fysisk umulige yderpunkter
    (fx en P/E på flere millioner, eller en margin på 20.000% fra korrupte data) klippes væk, så slideren ikke
    bliver ubrugelig. <strong>Sliderens ender er “åbne”:</strong> når et håndtag står yderst, sætter det
    <em>intet</em> loft eller gulv på den side — så alle aktier derover/derunder er <strong>stadig med</strong>.
    Det vises som fx <code>100+</code> (alt over 100 inkluderet) eller <code>≤-100%</code> (alt derunder med).
    En aktie som Palantir med høj P/E forsvinder altså først hvis du <em>aktivt</em> trækker P/E-loftet ned
    under dens værdi. Aktier med <strong>mistænkte datafejl</strong> (urealistiske kursspring) skjules som
    standard, men kan slås til med knappen øverst i filter-panelet.
  </div>

  <?php foreach (flt_groups() as $g): ?>
    <h3><?= htmlspecialchars($g['title']) ?></h3>
    <table class="filter-help">
      <?php foreach ($g['filters'] as $f): ?>
        <tr><th><?= htmlspecialchars($f['label']) ?></th><td><?= htmlspecialchars($f['info']) ?></td></tr>
      <?php endforeach; ?>
    </table>
  <?php endforeach; ?>

  <h2>Krydsnoteringer (samme selskab på flere børser)</h2>
  <p>Samme selskab handles ofte på flere børser — fx Western Digital som <code>WDC</code> (Nasdaq),
  <code>WDC.F</code> (Frankfurt), <code>WDC.MX</code> (Mexico) og <code>WDC.VI</code> (Wien). Det er reelt
  samme aktie, men med små forskelle i tal (anden valuta, likviditet og handelsdage). For ikke at fylde
  listen med dubletter <strong>vælger vi automatisk ét primært listing pr. selskab</strong> og viser kun
  det som standard.</p>
  <p>Det primære listing vælges ud fra et <strong>børs-hierarki</strong> — amerikanske børser først, derefter
  Norden og Vesteuropa, så Canada/Australien, så Asien, og til sidst regionale handelspladser og OTC — med
  længst kurshistorik og største markedsværdi som tie-break. Vil du se alle noteringer, kan du slå
  <strong>“Vis alle børsnoteringer”</strong> til øverst i filter-panelet; sekundære noteringer markeres da
  med deres børs (fx <code>↪ FRA</code>).</p>

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
