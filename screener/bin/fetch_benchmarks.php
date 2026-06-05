<?php
/**
 * Henter benchmark-indeks (S&P500 m.fl., 10 års historik til graf-overlay + relativ styrke)
 * og FX-kurser (currency -> USD-multiplikator) til markedsværdi-normalisering.
 *
 * Kør:  php bin/fetch_benchmarks.php
 * Lille datasæt (~15 symboler) — kan køres dagligt sammen med top-up.
 */
require __DIR__ . '/../web/lib/db.php';
require __DIR__ . '/../web/lib/yahoo.php';

$pdo = db();
$y = new Yahoo();

// --- Skema ---
$pdo->exec("CREATE TABLE IF NOT EXISTS " . t('indexes') . " (
  symbol VARCHAR(24) NOT NULL, price_date DATE NOT NULL, close DOUBLE NOT NULL,
  PRIMARY KEY (symbol, price_date)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
$pdo->exec("CREATE TABLE IF NOT EXISTS " . t('fx') . " (
  currency VARCHAR(8) NOT NULL, usd_rate DOUBLE NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (currency)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

// --- Benchmark-indeks (fuld historik) ---
$insIdx = $pdo->prepare("INSERT INTO " . t('indexes') . " (symbol, price_date, close)
  VALUES (?,?,?) ON DUPLICATE KEY UPDATE close=VALUES(close)");
foreach (array_keys(cfg()['benchmarks']) as $sym) {
    try {
        $rows = $y->dailyCloses($sym, '10y');
        $pdo->beginTransaction();
        foreach ($rows as $r) $insIdx->execute([$sym, $r['date'], $r['close']]);
        $pdo->commit();
        echo "indeks $sym: " . count($rows) . " punkter\n";
    } catch (Throwable $e) {
        if ($pdo->inTransaction()) $pdo->rollBack();
        echo "FEJL indeks $sym: " . $e->getMessage() . "\n";
    }
}

// --- FX: currency -> USD ---
// Under-enheder: pence/cents/agorot kvoteres som 1/100 af hovedvalutaen.
$subunit = ['GBp' => ['GBP', 0.01], 'ZAc' => ['ZAR', 0.01], 'ILA' => ['ILS', 0.01]];

$currencies = $pdo->query("SELECT DISTINCT currency FROM " . t('securities') . "
  WHERE currency IS NOT NULL AND currency <> ''")->fetchAll(PDO::FETCH_COLUMN);

$insFx = $pdo->prepare("INSERT INTO " . t('fx') . " (currency, usd_rate)
  VALUES (?,?) ON DUPLICATE KEY UPDATE usd_rate=VALUES(usd_rate)");
$insFx->execute(['USD', 1.0]);

foreach ($currencies as $cur) {
    if ($cur === 'USD') continue;
    [$base, $factor] = $subunit[$cur] ?? [$cur, 1.0];
    $rate = fxRate($y, $base);
    if ($rate === null) { echo "FX $cur: ingen kurs (springes over)\n"; continue; }
    $usdRate = $rate * $factor;
    $insFx->execute([$cur, $usdRate]);
    echo "FX $cur -> USD: $usdRate\n";
}
echo "Færdig.\n";

/** USD pr. 1 enhed af $base. Prøver baseUSD=X, ellers invers USDbase=X. */
function fxRate(Yahoo $y, string $base): ?float {
    if ($base === 'USD') return 1.0;
    foreach ([["{$base}USD=X", false], ["USD{$base}=X", true]] as [$sym, $inv]) {
        try {
            $rows = $y->dailyCloses($sym, '5d');
            if ($rows) {
                $c = end($rows)['close'];
                if ($c > 0) return $inv ? 1.0 / $c : $c;
            }
        } catch (Throwable $e) { /* prøv næste form */ }
    }
    return null;
}
