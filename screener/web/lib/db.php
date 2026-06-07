<?php
/**
 * Fælles DB-/config-hjælpere. Bruges af både CLI-scripts (bin/) og web-portalen (web/).
 */

function cfg(): array {
    static $cfg = null;
    if ($cfg === null) {
        $path = __DIR__ . '/../../config/config.php';
        if (!is_file($path)) {
            fwrite(STDERR, "Mangler config/config.php (kopiér config/config.example.php)\n");
            exit(1);
        }
        $cfg = require $path;
    }
    return $cfg;
}

/** Tabel-navn med prefix, fx t('screener') => 'stockOverview_screener'. */
function t(string $name): string {
    return cfg()['prefix'] . $name;
}

function db(): PDO {
    static $pdo = null;
    if ($pdo === null) {
        $c = cfg()['db'];
        $dsn = "mysql:host={$c['host']};port={$c['port']};dbname={$c['name']};charset={$c['charset']}";
        $pdo = new PDO($dsn, $c['user'], $c['pass'], [
            PDO::ATTR_ERRMODE            => PDO::ERRMODE_EXCEPTION,
            PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
            PDO::ATTR_EMULATE_PREPARES   => false,
        ]);
    }
    return $pdo;
}

/** Nøgle/værdi-cache (JSON) i DB — fx forudberegnede facetter. Selv-skabende tabel. */
function cache_table(): void {
    static $done = false;
    if ($done) return;
    db()->exec("CREATE TABLE IF NOT EXISTS " . t('cache') . " (
        ckey VARCHAR(64) PRIMARY KEY, cval LONGTEXT, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    $done = true;
}
/** Hent cachet JSON-værdi (eller null hvis ikke sat). */
function cache_get(string $key) {
    cache_table();
    $st = db()->prepare("SELECT cval FROM " . t('cache') . " WHERE ckey = ?");
    $st->execute([$key]);
    $v = $st->fetchColumn();
    return $v === false ? null : json_decode($v, true);
}
/** Gem JSON-værdi i cache. */
function cache_set(string $key, $val): void {
    cache_table();
    $st = db()->prepare("REPLACE INTO " . t('cache') . " (ckey, cval) VALUES (?, ?)");
    $st->execute([$key, json_encode($val)]);
}
