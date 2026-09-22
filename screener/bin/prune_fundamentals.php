<?php
/**
 * Oprydning i fundamentals: behold nøgletallene for ALLE snapshot-datoer, men fjern
 * det rå API-svar (raw_json) fra snapshots ældre end RAW_JSON_DAYS dage.
 *
 * raw_json er hele quoteSummary-svaret, ca. 15 KB pr. række, mod ca. 0,5 KB for de
 * udtrukne kolonner. Med ~62.000 værdipapirer om dagen voksede tabellen derfor ca.
 * 1,2 GB i døgnet. Intet læser raw_json (precompute.php bruger kun de udtrukne
 * kolonner fra seneste snapshot), så de seneste dage beholdes kun til fejlsøgning.
 *
 * Kører i bidder på primærnøglens symbol-præfiks, så der aldrig er én lang
 * transaktion. Kaldes fra daily_update.sh.
 *
 * Kør:  php bin/prune_fundamentals.php
 */
require __DIR__ . '/../web/lib/db.php';

const RAW_JSON_DAYS = 7;
const CHUNK = 500;

$pdo = db();
$table = t('fundamentals');
$cutoff = date('Y-m-d', strtotime('-' . RAW_JSON_DAYS . ' days'));

$symbols = $pdo->query("SELECT DISTINCT symbol FROM $table ORDER BY symbol")->fetchAll(PDO::FETCH_COLUMN);
$update = $pdo->prepare("UPDATE $table SET raw_json = NULL
    WHERE symbol BETWEEN ? AND ? AND snapshot_date < ? AND raw_json IS NOT NULL");

$rows = 0;
$t0 = microtime(true);
foreach (array_chunk($symbols, CHUNK) as $chunk) {
    $update->execute([$chunk[0], end($chunk), $cutoff]);
    $rows += $update->rowCount();
}

printf("prune_fundamentals: raw_json fjernet fra %d rækker før %s (%d symboler, %.0f s)\n",
    $rows, $cutoff, count($symbols), microtime(true) - $t0);
