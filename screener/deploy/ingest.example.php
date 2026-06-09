<?php
/**
 * Migrerings-/sync-endpoint for stockScreener på aogj.com (one.com).
 * Modtager token-beskyttede JSON-batches fra HTPC og UPSERTer til aogj_com-DB'en.
 * GITIGNORED — indeholder DB-creds. Deployes kun via FTP, committes ALDRIG.
 *
 * POST JSON: { token, action, table, create_sql, columns, rows }
 *   action=ping     -> sundhedstjek (+ php/mysql-version)
 *   action=ddl      -> opret tabel (klient sender CREATE TABLE; tabelnavn whitelistes)
 *   action=truncate -> tøm tabel (til ren gen-push)
 *   action=count    -> COUNT(*)   (action=approx -> information_schema-estimat)
 *   action=insert   -> bulk UPSERT af rows (default)
 */
header('Content-Type: application/json; charset=utf-8');

$TOKEN = 'DIT_HEMMELIGE_TOKEN';
$DB = ['host' => 'aogj.com.mysql', 'name' => 'aogj_com', 'user' => 'aogj_com', 'pass' => 'DIT_DB_PASSWORD'];

// Whitelist: tilladte tabel-navne. Skemaet sendes af pusheren (SHOW CREATE TABLE) og
// valideres mod dette navn; insert-kolonner valideres dynamisk mod den faktiske tabel.
$ALLOWED = [
    'stockOverview_prices', 'stockOverview_screener', 'stockOverview_securities',
    'stockOverview_indexes', 'stockOverview_fx', 'stockOverview_cache',
    'stockOverview_userdata', 'stockOverview_ingest_log', 'stockOverview_runs',
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

if ($action === 'ping') {
    $mysql = '';
    try { $mysql = (string)$pdo->query('SELECT VERSION()')->fetchColumn(); } catch (Throwable $e) {}
    echo json_encode(['ok' => true, 'php' => PHP_VERSION, 'mysql' => $mysql]); exit;
}

$table = $in['table'] ?? '';
if (!in_array($table, $ALLOWED, true)) { http_response_code(400); echo json_encode(['error' => 'table not allowed']); exit; }

/** Faktiske kolonner i en tabel (til validering af insert-kolonner). */
function table_columns(PDO $pdo, string $table): array {
    $st = $pdo->prepare("SELECT column_name FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = ?");
    $st->execute([$table]);
    return $st->fetchAll(PDO::FETCH_COLUMN);
}

try {
    if ($action === 'ddl') {
        $sql = (string)($in['create_sql'] ?? '');
        // Skal være CREATE TABLE for præcis den whitelistede tabel.
        if (!preg_match('/^\s*CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?' . preg_quote($table, '/') . '`?\s*\(/i', $sql)) {
            http_response_code(400); echo json_encode(['error' => 'ddl mismatch']); exit;
        }
        // Gør idempotent + MySQL-portabel (MariaDB-SHOW CREATE bruger current_timestamp()).
        $sql = preg_replace('/^\s*CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?/i', 'CREATE TABLE IF NOT EXISTS ', $sql, 1);
        $sql = str_ireplace('current_timestamp()', 'CURRENT_TIMESTAMP', $sql);
        $pdo->exec($sql);
        echo json_encode(['ok' => true, 'ddl' => $table]); exit;
    }
    if ($action === 'truncate') { $pdo->exec("TRUNCATE TABLE `$table`"); echo json_encode(['ok' => true, 'truncated' => $table]); exit; }
    if ($action === 'count')    { echo json_encode(['count' => (int)$pdo->query("SELECT COUNT(*) FROM `$table`")->fetchColumn()]); exit; }
    if ($action === 'approx') {
        $st = $pdo->prepare("SELECT table_rows FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?");
        $st->execute([$table]); echo json_encode(['approx' => (int)$st->fetchColumn()]); exit;
    }

    // insert (bulk UPSERT)
    $cols = $in['columns'] ?? []; $rows = $in['rows'] ?? [];
    if (!$cols || !is_array($rows) || !$rows) { echo json_encode(['ok' => true, 'inserted' => 0]); exit; }
    $valid = table_columns($pdo, $table);
    foreach ($cols as $c) if (!in_array($c, $valid, true)) {
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
