<?php
/**
 * Dashboard-data til "Universet". Læser den forudberegnede screener-tabel +
 * ingest-/run-status. Alt er billige aggregeringer på ~150k rækker.
 */
require_once __DIR__ . '/db.php';

function screener_exists(): bool {
    try { db()->query("SELECT 1 FROM " . t('screener') . " LIMIT 1"); return true; }
    catch (Throwable $e) { return false; }
}

/** Overordnede tal. */
function stat_headline(): array {
    $pdo = db();
    $s = $pdo->query("SELECT
        COUNT(*) total,
        SUM(quality_1y IS NOT NULL) with_metrics,
        SUM(mkt_cap_usd IS NOT NULL) with_cap,
        SUM(trailing_pe IS NOT NULL) with_pe,
        ROUND(SUM(mkt_cap_usd)/1e12,2) total_cap_t,
        MIN(first_date) earliest, MAX(last_date) latest,
        SUM(max_day_move > 1.0) suspect_data
        FROM " . t('screener'))->fetch();
    // Rå univers (fra ingesten). prices har 100M+ rækker → COUNT(*) ville fuldscanne
    // (30s+). Brug InnoDB's øjeblikkelige rækkeestimat fra information_schema i stedet.
    $sec = (int)$pdo->query("SELECT COUNT(*) FROM " . t('securities'))->fetchColumn();
    $px  = approx_rows(t('prices'));
    return array_merge($s, ['securities' => $sec, 'price_rows' => $px]);
}

/** Øjeblikkeligt (ca.) rækkeantal fra InnoDB-statistik — undgår dyrt COUNT(*) på kæmpe-tabeller. */
function approx_rows(string $table): int {
    $st = db()->prepare("SELECT table_rows FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = ?");
    $st->execute([$table]);
    return (int)$st->fetchColumn();
}

/** Status på seneste kørsler. */
function stat_runs(): array {
    $pdo = db();
    $out = ['ingest' => [], 'precompute' => null];
    foreach ($pdo->query("SELECT status, COUNT(*) n, MAX(updated_at) last FROM " . t('ingest_log') . " GROUP BY status") as $r)
        $out['ingest'][$r['status']] = ['n' => (int)$r['n'], 'last' => $r['last']];
    try {
        $out['precompute'] = $pdo->query("SELECT run_type, symbols, with_metrics, finished_at
            FROM " . t('runs') . " ORDER BY id DESC LIMIT 1")->fetch() ?: null;
    } catch (Throwable $e) {}
    return $out;
}

/** Gruppe-fordeling (count + samlet USD-cap) for et felt. */
function stat_breakdown(string $field, int $limit = 15): array {
    $allowed = ['sector', 'industry', 'country', 'quote_type', 'currency', 'exchange'];
    if (!in_array($field, $allowed, true)) return [];
    $sql = "SELECT COALESCE(NULLIF($field,''),'(ukendt)') label, COUNT(*) n, ROUND(SUM(mkt_cap_usd)/1e9,1) cap_b
            FROM " . t('screener') . " GROUP BY label ORDER BY n DESC LIMIT $limit";
    return db()->query($sql)->fetchAll();
}

/** Histogram over historik-længde (datakvalitet). */
function stat_history_buckets(): array {
    $sql = "SELECT bucket, COUNT(*) n FROM (
        SELECT CASE
            WHEN history_years >= 9.5 THEN '10 år'
            WHEN history_years >= 5 THEN '5-10 år'
            WHEN history_years >= 3 THEN '3-5 år'
            WHEN history_years >= 1 THEN '1-3 år'
            WHEN history_years IS NULL THEN '(ingen)'
            ELSE '<1 år' END bucket,
            CASE
            WHEN history_years >= 9.5 THEN 5 WHEN history_years >= 5 THEN 4
            WHEN history_years >= 3 THEN 3 WHEN history_years >= 1 THEN 2
            WHEN history_years IS NULL THEN 0 ELSE 1 END ord
        FROM " . t('screener') . ") x GROUP BY bucket, ord ORDER BY ord DESC";
    return db()->query($sql)->fetchAll();
}

function fmt_int($n): string { return number_format((int)$n, 0, ',', '.'); }
function fmt_ago(?string $ts): string {
    if (!$ts) return '–';
    $diff = time() - strtotime($ts);
    if ($diff < 90) return 'lige nu';
    if ($diff < 3600) return floor($diff/60) . ' min siden';
    if ($diff < 86400) return floor($diff/3600) . ' t siden';
    return floor($diff/86400) . ' dage siden';
}
