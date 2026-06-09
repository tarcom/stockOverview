<?php
/**
 * Pusher: streamer en lokal tabel op til aogj.com's ingest.php i batches (chunked JSON POST).
 * Bruges til engangs-backfill OG daglig delta-sync. Måler hastighed.
 *
 * Kør:  php bin/push_to_aogj.php <table> [batch] [limit]
 *   <table>  fx stockOverview_screener  (alle whitelistede stockOverview_*-tabeller)
 *   [batch]  rækker pr. POST (default 25000)
 *   [limit]  begræns antal rækker (default 0 = alle) — til test/måling
 *
 * Env-knapper (mest til den store prices-tabel):
 *   PUSH_SINCE=YYYY-MM-DD     kun rækker med price_date >= dato (option B: nyere historik)
 *   PUSH_FROM_SYMBOL=ABC      genoptag: kun symbol > 'ABC' (keyset på PK, index-effektivt)
 *   PUSH_NO_DDL=1             spring ddl over (tabellen findes allerede på aogj)
 *
 * Skema sendes automatisk (SHOW CREATE TABLE) → ingest.php opretter tabellen (whitelistet navn).
 * Kolonner udledes af den lokale tabel. Henter kun URL + token fra deploy/push-config.php.
 */
require __DIR__ . '/../web/lib/db.php';
@set_time_limit(0);
ini_set('memory_limit', '1024M');

$cfg = require __DIR__ . '/../deploy/push-config.php';
$URL = $cfg['url']; $TOKEN = $cfg['token'];

$table = $argv[1] ?? 'stockOverview_screener';
$BATCH = (int)($argv[2] ?? 25000);
$LIMIT = (int)($argv[3] ?? 0);
$SINCE = getenv('PUSH_SINCE') ?: '';
$FROM  = getenv('PUSH_FROM_SYMBOL') ?: '';

if (!preg_match('/^stockOverview_[a-z_]+$/', $table)) { fwrite(STDERR, "Ugyldigt tabelnavn: $table\n"); exit(1); }

// Kolonner fra den lokale tabel (i definitions-rækkefølge).
$cols = db()->query("SELECT column_name FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = " . db()->quote($table) . "
    ORDER BY ordinal_position")->fetchAll(PDO::FETCH_COLUMN);
if (!$cols) { fwrite(STDERR, "Ukendt/tom tabel lokalt: $table\n"); exit(1); }

function post($url, $payload) {
    $ch = curl_init($url);
    curl_setopt_array($ch, [
        CURLOPT_POST => true, CURLOPT_POSTFIELDS => json_encode($payload),
        CURLOPT_HTTPHEADER => ['Content-Type: application/json'],
        CURLOPT_RETURNTRANSFER => true, CURLOPT_TIMEOUT => 300,
    ]);
    $body = curl_exec($ch); $code = curl_getinfo($ch, CURLINFO_HTTP_CODE);
    $err = curl_error($ch); curl_close($ch);
    return [$code, json_decode($body, true), $err ?: $body];
}

// 1) ping + ddl
[$c, $j] = post($URL, ['token' => $TOKEN, 'action' => 'ping']);
echo "ping: HTTP $c " . json_encode($j) . "\n";
if ($c !== 200) { fwrite(STDERR, "Ping fejlede — tjek URL/token/deploy.\n"); exit(1); }
if (!getenv('PUSH_NO_DDL')) {
    $createSql = db()->query("SHOW CREATE TABLE `$table`")->fetch(PDO::FETCH_NUM)[1];
    [$c, $j, $raw] = post($URL, ['token' => $TOKEN, 'action' => 'ddl', 'table' => $table, 'create_sql' => $createSql]);
    echo "ddl:  HTTP $c " . json_encode($j) . "\n";
    if ($c !== 200) { fwrite(STDERR, "DDL fejlede: " . substr((string)$raw, 0, 400) . "\n"); exit(1); }
}

// 2) stream lokale rækker -> batches (UBUFFERET — prices har 260M rækker)
$where = [];
if ($SINCE && in_array('price_date', $cols, true)) $where[] = "price_date >= " . db()->quote($SINCE);
if ($FROM)                                         $where[] = "symbol > " . db()->quote($FROM);
$order = in_array('symbol', $cols, true) ? " ORDER BY " . implode(',', array_filter([
    'symbol', in_array('price_date', $cols, true) ? 'price_date' : null])) : '';
$sql = "SELECT " . implode(',', array_map(fn($c) => "`$c`", $cols)) . " FROM `$table`"
     . ($where ? " WHERE " . implode(' AND ', $where) : '') . $order
     . ($LIMIT ? " LIMIT $LIMIT" : "");

db()->setAttribute(PDO::MYSQL_ATTR_USE_BUFFERED_QUERY, false);
$st = db()->query($sql);

$batch = []; $sent = 0; $t0 = microtime(true); $serverSecs = 0; $lastSym = '';
$symIdx = array_search('symbol', $cols, true);

$flush = function () use (&$batch, &$sent, &$serverSecs, &$lastSym, $URL, $TOKEN, $table, $cols, $t0, $symIdx) {
    if (!$batch) return;
    [$code, $j, $raw] = post($URL, ['token' => $TOKEN, 'action' => 'insert', 'table' => $table, 'columns' => $cols, 'rows' => $batch]);
    if ($code !== 200 || empty($j['ok'])) { fwrite(STDERR, "Batch fejlede (sidste symbol $lastSym): HTTP $code " . substr((string)$raw, 0, 300) . "\n"); exit(1); }
    $sent += $j['inserted']; $serverSecs += $j['secs'] ?? 0;
    $rate = $sent / max(0.001, microtime(true) - $t0);
    $tail = $symIdx !== false ? " · ved $lastSym" : '';
    printf("  sendt %s rækker  (%.0f rk/s netto · server %.1fs%s)\n", number_format($sent), $rate, $serverSecs, $tail);
    $batch = [];
};

while ($row = $st->fetch(PDO::FETCH_NUM)) {
    $batch[] = $row;
    if ($symIdx !== false) $lastSym = $row[$symIdx];
    if (count($batch) >= $BATCH) $flush();
}
$flush();

$secs = microtime(true) - $t0;
printf("FÆRDIG (%s): %s rækker på %.1fs = %.0f rækker/s. (server-insert-tid %.1fs)\n",
    $table, number_format($sent), $secs, $sent / max(0.001, $secs), $serverSecs);
