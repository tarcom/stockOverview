#!/bin/bash
#
# Daglig opdatering af stockOverview-screeneren (køres af cron, fx kl. 04:00).
# 1) Inkrementel ingest: henter nye dagskurser for alle gyldige symboler (kun nye dage
#    siden sidst; springer 'not_found' over). Tunge backfills laves separat med
#    INGEST_BACKFILL_ALL=1 — IKKE her.
# 2) precompute: genberegner den brede screener-tabel + dedup + facet-cache, så
#    web-portalen afspejler de nye kurser.
#
# Cron-linje (crontab -e):
#   0 4 * * * /home/allan/stockOverview/daily_update.sh
#
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
cd /home/allan/stockOverview || exit 1

LOG=/home/allan/stockOverview/logs/daily_update.log
mkdir -p /home/allan/stockOverview/logs
# behold kun de seneste ~5000 linjer så loggen ikke vokser uendeligt
[ -f "$LOG" ] && tail -n 5000 "$LOG" > "$LOG.tmp" && mv "$LOG.tmp" "$LOG"

echo "" >> "$LOG"
echo "======== START $(date '+%Y-%m-%d %H:%M:%S') ========" >> "$LOG"

# 1) Inkrementel kurs-ingest (INGEST_FORCE=1 = opdatér alle, spring kun not_found over)
echo "[1/2] Ingest (inkrementel)..." >> "$LOG"
INGEST_FORCE=1 mvn -q -Dexec.cleanupDaemonThreads=false compile exec:java >> "$LOG" 2>&1
echo "[1/2] Ingest exit=$?" >> "$LOG"

# 2) Genberegn screener-tabel + dedup + facet-cache
echo "[2/3] Precompute..." >> "$LOG"
php /home/allan/stockOverview/screener/bin/precompute.php >> "$LOG" 2>&1
echo "[2/3] Precompute exit=$?" >> "$LOG"

# 3) Delta-sync til simply.com (den LIVE screener bor nu på screener.ans-consulting.dk):
#    genbygget screener-tabel + facetter + status, og KUN de nye dagskurser (seneste
#    10 dage, REPLACE = idempotent). userdata ekskluderes (live-brugernes gemte screens).
#    Direkte DB-til-DB via simplys eksterne MySQL-adgang. Den fulde 24 GB prices-backfill
#    er en engangs-ting, IKKE en del af cron.
echo "[3/3] Push til simply..." >> "$LOG"
bash /home/allan/stockOverview/screener/bin/push_to_simply.sh >> "$LOG" 2>&1
echo "[3/3] Push exit=$?" >> "$LOG"

echo "======== DONE  $(date '+%Y-%m-%d %H:%M:%S') ========" >> "$LOG"
