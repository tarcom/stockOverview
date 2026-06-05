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
