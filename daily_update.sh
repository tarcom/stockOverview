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

# 3) Delta-sync til aogj.com (one.com): den genbyggede screener-tabel + facetter +
#    status, og KUN de nye dagskurser (price_date inden for de seneste 7 dage — dækker
#    ingestens 4-dages overlap; UPSERT er idempotent). Den fulde prices-backfill er en
#    engangs-push, IKKE en del af cron. Springes over hvis push-config mangler.
PUSH=/home/allan/stockOverview/screener/bin/push_to_aogj.php
if [ -f /home/allan/stockOverview/screener/deploy/push-config.php ]; then
    echo "[3/3] Push til aogj..." >> "$LOG"
    for t in screener securities indexes fx ingest_log runs cache userdata; do
        php "$PUSH" stockOverview_$t >> "$LOG" 2>&1
    done
    PUSH_SINCE=$(date -d '7 days ago' '+%Y-%m-%d') php "$PUSH" stockOverview_prices 25000 >> "$LOG" 2>&1
    echo "[3/3] Push exit=$?" >> "$LOG"
else
    echo "[3/3] Push sprunget over (ingen deploy/push-config.php)" >> "$LOG"
fi

echo "======== DONE  $(date '+%Y-%m-%d %H:%M:%S') ========" >> "$LOG"
