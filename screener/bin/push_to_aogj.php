<?php
/**
 * Pusher: streamer en lokal tabel op til aogj.com's ingest.php i batches (chunked JSON POST).
 * Bruges til engangs-backfill OG daglig delta-sync. Måler hastighed.
 *
 * Kør:  php bin/push_to_aogj.php <table> [batch] [limit]
 *   <table>  fx stockOverview_prices
 *   [batch]  rækker pr. POST (default 25000)
 *   [limit]  begræns antal rækker (default 0 = alle)
 *
 * Henter kun URL + token fra deploy/push-config.php (ingen aogj-DB-creds her).
 */
require __DIR__ . '/../web/lib/db.php';
@set_time_limit(0);
ini_set('memory_limit', '1024M');

$cfg = require __DIR__ . '/../deploy/push-config.php';
$URL = $cfg['url']; $TOKEN = $cfg['token'];

$table = $argv[1] ?? 'stockOverview_prices';
$BATCH = (int)($argv[2] ?? 25000);
$LIMIT = (int)($argv[3] ?? 0);

$COLS = [
    'stockOverview_prices' => ['symbol','price_date','open','high','low','close','adj_close','volume'],
];
if (!isset($COLS[$table])) { fwrite(STDERR, "Ukendt tabel: $table\n"); exit(1); }
$cols = $COLS[$table];

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
[$c, $j] = post($URL, ['token' => $TOKEN, 'action' => 'ddl', 'table' => $table]);
echo "ddl:  HTTP $c " . json_encode($j) . "\n";

// 2) stream lokale rækker -> batches
$sql = "SELECT " . implode(',', $cols) . " FROM $table" . ($LIMIT ? " LIMIT $LIMIT" : "");
$st = db()->query($sql);
$batch = []; $sent = 0; $t0 = microtime(true); $serverSecs = 0;

$flush = function () use (&$batch, &$sent, &$serverSecs, $URL, $TOKEN, $table, $cols, $t0) {
    if (!$batch) return;
    [$code, $j, $raw] = post($URL, ['token' => $TOKEN, 'action' => 'insert', 'table' => $table, 'columns' => $cols, 'rows' => $batch]);
    if ($code !== 200 || empty($j['ok'])) { fwrite(STDERR, "Batch fejlede: HTTP $code " . substr((string)$raw, 0, 300) . "\n"); exit(1); }
    $sent += $j['inserted']; $serverSecs += $j['secs'] ?? 0;
    $rate = $sent / max(0.001, microtime(true) - $t0);
    printf("  sendt %s rækker  (%.0f rk/s netto · server %.1fs)\n", number_format($sent), $rate, $serverSecs);
    $batch = [];
};

while ($row = $st->fetch(PDO::FETCH_NUM)) {
    $batch[] = $row;
    if (count($batch) >= $BATCH) $flush();
}
$flush();

$secs = microtime(true) - $t0;
printf("FÆRDIG: %s rækker på %.1fs = %.0f rækker/s. (server-insert-tid %.1fs)\n",
    number_format($sent), $secs, $sent / max(0.001, $secs), $serverSecs);
// estimat for fuld tabel
$approx = (int) db()->query("SELECT table_rows FROM information_schema.tables
    WHERE table_schema=DATABASE() AND table_name=" . db()->quote($table))->fetchColumn();
if ($approx > $sent && $sent > 0) {
    $eta = $approx / ($sent / $secs) / 3600;
    printf("Ekstrapoleret: hele tabellen (~%s rækker) ≈ %.1f timer ved dette tempo.\n", number_format($approx), $eta);
}
