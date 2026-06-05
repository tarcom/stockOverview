<?php
/**
 * Precompute: bygger den brede, forudberegnede screener-tabel (stockOverview_screener),
 * én række pr. aktie, så web-portalen kan fuldscanne ~150k rækker på millisekunder uden
 * nogensinde at røre rå dagshistorik.
 *
 * Pr. tidsvindue (1M..10Y) beregnes:
 *   ret      – samlet afkast
 *   cagr     – annualiseret afkast
 *   quality  – ANNUALISERET EKSPONENTIEL HÆLDNING × R²  (stabil+høj vækst; Clenow-stil)
 *   trend_r2 – R² af log-lineær fit  (hvor jævnt aktien følger sin egen vækstkurve)
 *   lin_r2   – R² af lineær (ikke-log) fit  (medtaget efter ønske)
 *   vol      – annualiseret volatilitet
 *   maxdd    – max drawdown (negativ)
 *   sharpe   – cagr / vol
 *   beta     – mod S&P500 (markedsfølsomhed)
 *   mkt_r2   – korrelation² mod S&P500  (hvor meget bevægelse er markeds-forklaret = "i takt")
 *   idio_vol – idiosynkratisk volatilitet (den del der IKKE forklares af markedet = "tilfældig")
 *   rs       – merafkast vs S&P500 (relativ styrke)
 *
 * Kør:  php bin/precompute.php [limit]   (limit udeladt = alle)
 * Idempotent (UPSERT). Kan køres mens ingesten stadig fylder data — den læser bare det der er.
 */
require __DIR__ . '/../web/lib/db.php';

@set_time_limit(0);
ini_set('memory_limit', '1024M');
$pdo = db();

$LIMIT = isset($argv[1]) ? (int)$argv[1] : 0;

// Tidsvinduer (kalenderdage tilbage). Under 1M giver ikke mening.
$WINDOWS = ['1m'=>30,'3m'=>91,'6m'=>182,'1y'=>365,'2y'=>730,'3y'=>1095,'5y'=>1826,'10y'=>3652];
// Metrics pr. vindue.
$METRICS = ['ret','cagr','quality','trend_r2','lin_r2','vol','maxdd','sharpe','beta','mkt_r2','idio_vol','rs'];
// Fundamentals der kopieres ind (så portalen filtrerer på dem uden join).
$FUND = ['trailing_pe','forward_pe','peg_ratio','price_to_book','price_to_sales','ev_to_ebitda',
    'ev_to_revenue','profit_margins','gross_margins','operating_margins','return_on_equity',
    'return_on_assets','revenue_growth','earnings_growth','debt_to_equity','current_ratio',
    'quick_ratio','dividend_yield','payout_ratio','trailing_eps','total_revenue','free_cashflow',
    'recommendation_mean','target_mean_price'];

ensureSchema($pdo, $WINDOWS, $METRICS, $FUND);

// --- Referencedata i hukommelse ---
$fx = [];
foreach ($pdo->query("SELECT currency, usd_rate FROM " . t('fx')) as $r) $fx[$r['currency']] = (float)$r['usd_rate'];

$spx = []; // date => close
$benchSym = cfg()['rs_benchmark'];
$st = $pdo->prepare("SELECT price_date, close FROM " . t('indexes') . " WHERE symbol=? ORDER BY price_date");
$st->execute([$benchSym]);
foreach ($st as $r) $spx[$r['price_date']] = (float)$r['close'];
if (!$spx) { fwrite(STDERR, "ADVARSEL: ingen $benchSym-data — kør bin/fetch_benchmarks.php først.\n"); }

// --- Symboler at behandle ---
$sql = "SELECT symbol,name,exchange,currency,quote_type,sector,industry,country,employees
        FROM " . t('securities') . " ORDER BY symbol" . ($LIMIT > 0 ? " LIMIT $LIMIT" : "");
$symbols = $pdo->query($sql)->fetchAll();
$total = count($symbols);
echo "Precompute: $total aktier" . ($LIMIT ? " (limit)" : "") . ", " . count($spx) . " SPX-punkter.\n";

$priceStmt = $pdo->prepare("SELECT price_date, COALESCE(adj_close, close) p FROM " . t('prices')
    . " WHERE symbol=? AND COALESCE(adj_close, close) IS NOT NULL ORDER BY price_date");
$fundCols = implode(',', $FUND);
$fundStmt = $pdo->prepare("SELECT $fundCols, market_cap, snapshot_date FROM " . t('fundamentals')
    . " WHERE symbol=? ORDER BY snapshot_date DESC LIMIT 1");

$done = 0; $withMetrics = 0; $t0 = microtime(true);
foreach ($symbols as $sec) {
    $sym = $sec['symbol'];
    $priceStmt->execute([$sym]);
    $series = $priceStmt->fetchAll(); // [['price_date'=>...,'p'=>...], ...] ascending
    $fundStmt->execute([$sym]);
    $fund = $fundStmt->fetch() ?: [];

    $row = buildRow($sym, $sec, $series, $fund, $spx, $fx, $WINDOWS, $METRICS, $FUND);
    if ($row['_hasMetrics']) $withMetrics++;
    unset($row['_hasMetrics']);
    upsert($pdo, t('screener'), $row, ['symbol']);

    if (++$done % 500 === 0) {
        $rate = $done / max(0.001, microtime(true) - $t0);
        printf("[%d/%d] %.0f/s  (m. metrics: %d)\n", $done, $total, $rate, $withMetrics);
    }
}
$secs = microtime(true) - $t0;
printf("Færdig: %d aktier på %.1fs (%.0f/s). Med metrics: %d.\n", $done, $secs, $done/max(0.001,$secs), $withMetrics);
logRun($pdo, 'precompute', $done, $withMetrics);

// =====================================================================

function buildRow($sym, $sec, $series, $fund, $spx, $fx, $WINDOWS, $METRICS, $FUND): array {
    $row = ['symbol'=>$sym, 'name'=>$sec['name'], 'exchange'=>$sec['exchange'],
        'currency'=>$sec['currency'], 'quote_type'=>$sec['quote_type'], 'sector'=>$sec['sector'],
        'industry'=>$sec['industry'], 'country'=>$sec['country'],
        'employees'=>$sec['employees'] !== null ? (int)$sec['employees'] : null,
        '_hasMetrics'=>false];

    $n = count($series);
    if ($n > 0) {
        $row['first_date'] = $series[0]['price_date'];
        $row['last_date']  = $series[$n-1]['price_date'];
        $row['last_close'] = (float)$series[$n-1]['p'];
        $days = (strtotime($series[$n-1]['price_date']) - strtotime($series[0]['price_date'])) / 86400;
        $row['history_days']  = (int)round($days);
        $row['history_years'] = round($days / 365.25, 2);
        // Datakvalitets-signal: største absolutte 1-dags bevægelse over HELE historikken.
        // Rigtige aktier bevæger sig sjældent >100% på én dag; ekstreme værdier afslører
        // korrupt Yahoo-data (fx fladlinede spikes) som screeneren kan filtrere fra.
        $maxMove = null;
        for ($i = 1; $i < $n; $i++) {
            $p0 = (float)$series[$i-1]['p']; $p1 = (float)$series[$i]['p'];
            if ($p0 > 0) { $mv = abs($p1/$p0 - 1); if ($maxMove === null || $mv > $maxMove) $maxMove = $mv; }
        }
        $row['max_day_move'] = r($maxMove);
    }

    // USD-markedsværdi
    $cap = isset($fund['market_cap']) ? (float)$fund['market_cap'] : null;
    $rate = $fx[$sec['currency']] ?? null;
    $row['mkt_cap_usd'] = ($cap !== null && $rate !== null) ? $cap * $rate : ($cap !== null && $sec['currency']==='USD' ? $cap : null);

    foreach ($FUND as $f) $row[$f] = isset($fund[$f]) && $fund[$f] !== null ? (float)$fund[$f] : null;
    // Ryd meningsløse P/E-værdier: ≤0 = negativ indtjening, >1000 = nær-nul indtjening → N/A.
    foreach (['trailing_pe','forward_pe'] as $pe)
        if ($row[$pe] !== null && ($row[$pe] <= 0 || $row[$pe] > 1000)) $row[$pe] = null;

    // Per-vindue metrics
    foreach ($METRICS as $m) foreach (array_keys($WINDOWS) as $w) $row["{$m}_{$w}"] = null;
    if ($n >= 6) {
        $lastDate = strtotime($series[$n-1]['price_date']);
        foreach ($WINDOWS as $w => $cdays) {
            $cut = $lastDate - $cdays * 86400;
            $sub = [];
            foreach ($series as $pt) if (strtotime($pt['price_date']) >= $cut) $sub[] = $pt;
            if (count($sub) < 5) continue;
            $met = windowMetrics($sub, $spx);
            if ($met === null) continue;
            foreach ($met as $k => $v) $row["{$k}_{$w}"] = $v;
            $row['_hasMetrics'] = true;
        }
    }
    return $row;
}

/** Beregner alle metrics for ét vindue. $sub = ['price_date','p'] ascending. */
function windowMetrics(array $sub, array $spx): ?array {
    $n = count($sub);
    $first = (float)$sub[0]['p']; $last = (float)$sub[$n-1]['p'];
    if ($first <= 0 || $last <= 0) return null;
    $spanYears = (strtotime($sub[$n-1]['price_date']) - strtotime($sub[0]['price_date'])) / (86400*365.25);
    if ($spanYears <= 0) return null;

    $ret = $last/$first - 1;
    $cagr = pow($last/$first, 1/$spanYears) - 1;

    // Log- og lineær regression (x = år fra start)
    $xs=[]; $ylog=[]; $ylin=[];
    $t0 = strtotime($sub[0]['price_date']);
    foreach ($sub as $pt) {
        $xs[]   = (strtotime($pt['price_date']) - $t0) / (86400*365.25);
        $ylog[] = log((float)$pt['p']);
        $ylin[] = (float)$pt['p'];
    }
    [$slopeLog,,$r2log] = linreg($xs, $ylog);
    [,,$r2lin]          = linreg($xs, $ylin);
    $trendCagr = exp($slopeLog) - 1;           // annualiseret eksponentiel hældning
    $quality   = $trendCagr * $r2log;          // stabil + høj vækst

    // Daglige afkast (simple) + max drawdown
    $rets=[]; $peak=$first; $maxdd=0;
    for ($i=1; $i<$n; $i++) {
        $p0=(float)$sub[$i-1]['p']; $p1=(float)$sub[$i]['p'];
        if ($p0>0) $rets[] = $p1/$p0 - 1;
        if ($p1>$peak) $peak=$p1;
        if ($peak>0) { $dd=$p1/$peak-1; if ($dd<$maxdd) $maxdd=$dd; }
    }
    $vol = stddev($rets) * sqrt(252);
    $sharpe = $vol > 0 ? $cagr / $vol : null;

    // Markeds-regression vs SPX på fælles datoer
    $beta=null; $mktR2=null; $idioVol=null; $rs=null;
    $rsArr=[]; $rmArr=[];
    for ($i=1; $i<$n; $i++) {
        $d0=$sub[$i-1]['price_date']; $d1=$sub[$i]['price_date'];
        if (!isset($spx[$d0]) || !isset($spx[$d1]) || $spx[$d0]<=0) continue;
        $p0=(float)$sub[$i-1]['p']; $p1=(float)$sub[$i]['p'];
        if ($p0<=0) continue;
        $rsArr[] = $p1/$p0 - 1;
        $rmArr[] = $spx[$d1]/$spx[$d0] - 1;
    }
    if (count($rsArr) >= 10) {
        [$beta,$alpha,$mktR2] = linreg($rmArr, $rsArr); // y=stock, x=market
        // idiosynkratisk vol = stddev af residualer
        $resid=[];
        foreach ($rsArr as $i=>$y) $resid[] = $y - ($alpha + $beta*$rmArr[$i]);
        $idioVol = stddev($resid) * sqrt(252);
        // relativ styrke: aktiens afkast minus SPX' afkast over vinduet (på fælles datoer)
        $datesWithSpx = array_values(array_filter($sub, fn($pt)=>isset($spx[$pt['price_date']])));
        if (count($datesWithSpx) >= 2) {
            $sa=(float)$datesWithSpx[0]['p']; $sb=(float)$datesWithSpx[count($datesWithSpx)-1]['p'];
            $ma=$spx[$datesWithSpx[0]['price_date']]; $mb=$spx[$datesWithSpx[count($datesWithSpx)-1]['price_date']];
            if ($sa>0 && $ma>0) $rs = ($sb/$sa-1) - ($mb/$ma-1);
        }
    }

    return [
        'ret'=>r($ret), 'cagr'=>r($cagr), 'quality'=>r($quality), 'trend_r2'=>r($r2log),
        'lin_r2'=>r($r2lin), 'vol'=>r($vol), 'maxdd'=>r($maxdd), 'sharpe'=>r($sharpe),
        'beta'=>r($beta), 'mkt_r2'=>r($mktR2), 'idio_vol'=>r($idioVol), 'rs'=>r($rs),
    ];
}

/** Mindste-kvadraters regression. Returnerer [slope, intercept, r2]. */
function linreg(array $x, array $y): array {
    $n = count($x);
    if ($n < 2) return [null, null, 0.0];
    $sx=array_sum($x); $sy=array_sum($y); $sxx=0; $sxy=0; $syy=0;
    for ($i=0; $i<$n; $i++) { $sxx+=$x[$i]*$x[$i]; $sxy+=$x[$i]*$y[$i]; $syy+=$y[$i]*$y[$i]; }
    $denom = $n*$sxx - $sx*$sx;
    if ($denom == 0.0) return [0.0, $sy/$n, 0.0];
    $slope = ($n*$sxy - $sx*$sy) / $denom;
    $intercept = ($sy - $slope*$sx) / $n;
    $ssTot = $syy - $sy*$sy/$n;
    $ssRes = 0;
    for ($i=0; $i<$n; $i++) { $e=$y[$i]-($intercept+$slope*$x[$i]); $ssRes+=$e*$e; }
    $r2 = $ssTot > 0 ? max(0.0, 1 - $ssRes/$ssTot) : 0.0;
    return [$slope, $intercept, $r2];
}

function stddev(array $a): float {
    $n=count($a); if ($n<2) return 0.0;
    $m=array_sum($a)/$n; $s=0; foreach ($a as $v) $s+=($v-$m)*($v-$m);
    return sqrt($s/($n-1));
}

/** Afrund + sikr endeligt tal (NaN/Inf -> null). */
function r($v) {
    if ($v === null || !is_finite($v)) return null;
    return round($v, 6);
}

// --------------------------------------------------------------- schema/DB

function ensureSchema(PDO $pdo, array $W, array $M, array $F): void {
    $cols = [
        "symbol VARCHAR(40) NOT NULL", "name VARCHAR(255) NULL", "exchange VARCHAR(64) NULL",
        "currency VARCHAR(8) NULL", "quote_type VARCHAR(32) NULL", "sector VARCHAR(128) NULL",
        "industry VARCHAR(160) NULL", "country VARCHAR(96) NULL", "employees INT NULL",
        "mkt_cap_usd DOUBLE NULL", "history_days INT NULL", "history_years DOUBLE NULL",
        "first_date DATE NULL", "last_date DATE NULL", "last_close DOUBLE NULL",
        "max_day_move DOUBLE NULL",
        "computed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP",
    ];
    foreach ($F as $f) $cols[] = "$f DOUBLE NULL";
    foreach ($M as $m) foreach (array_keys($W) as $w) $cols[] = "{$m}_{$w} DOUBLE NULL";
    $keys = ["PRIMARY KEY (symbol)", "KEY idx_cap (mkt_cap_usd)", "KEY idx_sector (sector)",
        "KEY idx_country (country)", "KEY idx_type (quote_type)",
        "KEY idx_q1y (quality_1y)", "KEY idx_ret1y (ret_1y)", "KEY idx_hist (history_years)"];
    $sql = "CREATE TABLE IF NOT EXISTS " . t('screener') . " (\n  "
         . implode(",\n  ", array_merge($cols, $keys)) . "\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    $pdo->exec($sql);

    $pdo->exec("CREATE TABLE IF NOT EXISTS " . t('runs') . " (
      id INT AUTO_INCREMENT PRIMARY KEY, run_type VARCHAR(32) NOT NULL,
      started_at TIMESTAMP NULL, finished_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
      symbols INT NULL, with_metrics INT NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
}

function upsert(PDO $pdo, string $table, array $row, array $keyCols): void {
    static $cache = [];
    $cols = array_keys($row);
    $sig = $table . '|' . implode(',', $cols);
    if (!isset($cache[$sig])) {
        $ph = implode(',', array_fill(0, count($cols), '?'));
        $upd = [];
        foreach ($cols as $c) if (!in_array($c, $keyCols, true)) $upd[] = "$c=VALUES($c)";
        $cache[$sig] = $pdo->prepare("INSERT INTO $table (" . implode(',', $cols) . ") VALUES ($ph)"
            . (count($upd) ? " ON DUPLICATE KEY UPDATE " . implode(',', $upd) : ""));
    }
    $cache[$sig]->execute(array_values($row));
}

function logRun(PDO $pdo, string $type, int $symbols, int $withMetrics): void {
    $pdo->prepare("INSERT INTO " . t('runs') . " (run_type, symbols, with_metrics) VALUES (?,?,?)")
        ->execute([$type, $symbols, $withMetrics]);
}
