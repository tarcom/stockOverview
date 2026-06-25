#!/bin/bash
#
# Daglig delta-sync af screener-data fra HTPC's lokale DB til simply.com (DB-til-DB).
# Simply tillader ekstern MySQL-adgang (mysql68.unoeuro.com), så vi bruger
# mysqldump | mysql direkte — hurtigere/enklere end HTTP-push'en til one.com.
#
#   - Små tabeller: full replace (drop+create+insert), de er små.
#   - Priser: KUN de seneste 10 dage (REPLACE INTO = idempotent; dækker ingestens
#     overlap). Den fulde 24 GB-backfill er en engangs-ting, IKKE en del af cron.
#   - userdata EKSKLUDERES bevidst: den holder live-brugernes gemte screens/favoritter
#     på simply og må ikke overskrives af HTPC's lokale (tomme) kopi.
#
# Kilde-creds læses fra screener/config/config.php; simply-creds fra
# screener/deploy/simply.cnf (gitignored).
#
set -o pipefail
DIR=/home/allan/stockOverview/screener
DSTCNF="$DIR/deploy/simply.cnf"
DSTDB=ans_consulting_dk_db_screener

[ -f "$DSTCNF" ] || { echo "push_to_simply: mangler $DSTCNF — springer over"; exit 0; }

# Lokal kilde fra PHP-config
read LH LU LP LN < <(php -r '$c=require "'"$DIR"'/config/config.php";$d=$c["db"];echo $d["host"]." ".$d["user"]." ".$d["pass"]." ".$d["name"];')
SRCCNF=$(mktemp); umask 077
printf '[client]\nhost=%s\nuser=%s\npassword=%s\n' "$LH" "$LU" "$LP" > "$SRCCNF"

FULL="stockOverview_screener stockOverview_securities stockOverview_indexes stockOverview_fx stockOverview_ingest_log stockOverview_runs stockOverview_cache"

mysqldump --defaults-extra-file="$SRCCNF" --no-tablespaces --single-transaction --skip-lock-tables "$LN" $FULL \
  | mysql --defaults-extra-file="$DSTCNF" "$DSTDB"
RC1=$?

PSINCE=$(date -d '10 days ago' '+%Y-%m-%d')
mysqldump --defaults-extra-file="$SRCCNF" --no-tablespaces --single-transaction --skip-lock-tables \
  --no-create-info --skip-add-drop-table --replace \
  --where="price_date >= '$PSINCE'" "$LN" stockOverview_prices \
  | mysql --defaults-extra-file="$DSTCNF" "$DSTDB"
RC2=$?

rm -f "$SRCCNF"
echo "push_to_simply: smaa_tabeller_rc=$RC1 priser(>=$PSINCE)_rc=$RC2"
[ $RC1 -eq 0 ] && [ $RC2 -eq 0 ]
