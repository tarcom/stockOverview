<?php
/**
 * JSON-API til screeneren.
 *   ?action=facets   → min/max + histogrammer pr. range-filter + multivalg-muligheder (cachevenligt)
 *   ?action=query    → live tælling + funnel + top-N resultater for de givne filtre
 * Alle filter-parametre kommer som query-string (samme nøgler som i URL'en → delbar).
 */
require __DIR__ . '/lib/filters.php';

header('Content-Type: application/json; charset=utf-8');

try {
    $action = $_GET['action'] ?? 'query';
    if ($action === 'facets') {
        echo json_encode(flt_facets());
        exit;
    }
    if ($action === 'chart') {
        $syms  = isset($_GET['symbols']) && $_GET['symbols'] !== '' ? explode('~', $_GET['symbols']) : [];
        $win   = $_GET['window'] ?? '3y';
        $bench = $_GET['bench'] ?? cfg()['rs_benchmark'];
        echo json_encode(flt_chart($syms, $win, $bench));
        exit;
    }
    if ($action === 'stock') {
        echo json_encode(flt_stock($_GET['symbol'] ?? '', $_GET['window'] ?? '3y'));
        exit;
    }
    if ($action === 'csv') {
        $rows = flt_results($_GET, $_GET['sort'] ?? 'quality_1y', $_GET['dir'] ?? 'desc', 2000);
        header('Content-Type: text/csv; charset=utf-8');
        header('Content-Disposition: attachment; filename="screener.csv"');
        $out = fopen('php://output', 'w');
        fwrite($out, "\xEF\xBB\xBF"); // UTF-8 BOM (Excel)
        if ($rows) {
            // Dansk Excel: ';' som kolonne-separator + ',' som decimaltegn (modsat standard).
            fputcsv($out, array_keys($rows[0]), ';', '"', '\\');
            foreach ($rows as $r) {
                $vals = array_map(fn($v) => is_numeric($v) ? str_replace('.', ',', (string)$v) : $v, $r);
                fputcsv($out, $vals, ';', '"', '\\');
            }
        }
        fclose($out); exit;
    }
    // query
    $sort  = $_GET['sort'] ?? 'quality_1y';
    $dir   = $_GET['dir'] ?? 'desc';
    $limit = max(1, min(200, (int)($_GET['limit'] ?? 20)));
    $count = flt_count($_GET);
    echo json_encode([
        'count'   => $count,
        'total'   => flt_total(),
        'funnel'  => flt_funnel($_GET),
        'results' => $count > 0 ? flt_results($_GET, $sort, $dir, $limit) : [],
        'sort'    => $sort, 'dir' => $dir, 'limit' => $limit,
    ]);
} catch (Throwable $e) {
    http_response_code(500);
    echo json_encode(['error' => $e->getMessage()]);
}
