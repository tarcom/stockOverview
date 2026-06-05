<?php
/**
 * Migrerings-/sync-endpoint for stockScreener på aogj.com (one.com).
 * Modtager token-beskyttede JSON-batches fra HTPC og UPSERTer til aogj_com-DB'en.
 * GITIGNORED — indeholder DB-creds. Deployes kun via FTP, committes ALDRIG.
 *
 * POST JSON: { token, action, table, columns, rows }
 *   action=ping   -> sundhedstjek
 *   action=ddl    -> opret tabel (whitelistet schema)
 *   action=count  -> COUNT(*)  (action=approx -> information_schema-estimat)
 *   action=insert -> bulk UPSERT af rows (default)
 */
header('Content-Type: application/json; charset=utf-8');

$TOKEN = 'DIT_HEMMELIGE_TOKEN';
$DB = ['host' => 'aogj.com.mysql', 'name' => 'aogj_com', 'user' => 'aogj_com', 'pass' => 'DIT_DB_PASSWORD'];

// Whitelist: tabel => tilladte kolonner
$ALLOWED = [
  'stockOverview_prices' => ['symbol','price_date','open','high','low','close','adj_close','volume'],
];
$DDL = [
  'stockOverview_prices' => "CREATE TABLE IF NOT EXISTS stockOverview_prices (
     symbol VARCHAR(40) NOT NULL, price_date DATE NOT NULL,
     open DOUBLE NULL, high DOUBLE NULL, low DOUBLE NULL, close DOUBLE NULL,
     adj_close DOUBLE NULL, volume BIGINT NULL,
     PRIMARY KEY (symbol, price_date)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4",
];

$in = json_decode(file_get_contents('php://input'), true);
if (!is_array($in) || !hash_equals($TOKEN, (string)($in['token'] ?? ''))) {
    http_response_code(403); echo json_encode(['error' => 'auth']); exit;
}
$action = $in['action'] ?? 'insert';

try {
    $pdo = new PDO("mysql:host={$DB['host']};dbname={$DB['name']};charset=utf8mb4",
        $DB['user'], $DB['pass'], [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]);
} catch (Throwable $e) { http_response_code(500); echo json_encode(['error' => 'db: ' . $e->getMessage()]); exit; }

if ($action === 'ping') { echo json_encode(['ok' => true, 'php' => PHP_VERSION]); exit; }

$table = $in['table'] ?? '';
if (!isset($ALLOWED[$table])) { http_response_code(400); echo json_encode(['error' => 'table not allowed']); exit; }

try {
    if ($action === 'ddl')   { $pdo->exec($DDL[$table]); echo json_encode(['ok' => true, 'ddl' => $table]); exit; }
    if ($action === 'count') { echo json_encode(['count' => (int)$pdo->query("SELECT COUNT(*) FROM `$table`")->fetchColumn()]); exit; }
    if ($action === 'approx') {
        $st = $pdo->prepare("SELECT table_rows FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?");
        $st->execute([$table]); echo json_encode(['approx' => (int)$st->fetchColumn()]); exit;
    }

    // insert (bulk UPSERT)
    $cols = $in['columns'] ?? []; $rows = $in['rows'] ?? [];
    if (!$cols || !is_array($rows) || !$rows) { echo json_encode(['ok' => true, 'inserted' => 0]); exit; }
    foreach ($cols as $c) if (!in_array($c, $ALLOWED[$table], true)) {
        http_response_code(400); echo json_encode(['error' => "bad column: $c"]); exit;
    }
    $colList = implode(',', array_map(fn($c) => "`$c`", $cols));
    $rowPh   = '(' . implode(',', array_fill(0, count($cols), '?')) . ')';
    $upd     = implode(',', array_map(fn($c) => "`$c`=VALUES(`$c`)", $cols));
    $t0 = microtime(true); $inserted = 0; $CHUNK = 1000;
    $pdo->beginTransaction();
    for ($i = 0; $i < count($rows); $i += $CHUNK) {
        $slice = array_slice($rows, $i, $CHUNK);
        $sql = "INSERT INTO `$table` ($colList) VALUES " . implode(',', array_fill(0, count($slice), $rowPh))
             . " ON DUPLICATE KEY UPDATE $upd";
        $st = $pdo->prepare($sql);
        $bind = []; foreach ($slice as $r) foreach ($r as $v) $bind[] = $v;
        $st->execute($bind);
        $inserted += count($slice);
    }
    $pdo->commit();
    echo json_encode(['ok' => true, 'inserted' => $inserted, 'secs' => round(microtime(true) - $t0, 2)]);
} catch (Throwable $e) {
    if ($pdo->inTransaction()) $pdo->rollBack();
    http_response_code(500); echo json_encode(['error' => $e->getMessage()]);
}
