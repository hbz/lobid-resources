# author: dr0i
#
# grep all FIX errors (besides the multiple hbzIds (see the extra script "getMultipleHbzid.sh") and send email. If errors exceed 500kb then show only the last 50 entries.

MAIL_TO=$(cat .secrets/MAIL_TO)
MAIL_FROM=$(cat .secrets/MAIL_FROM)
if [ -z $1 ]; then
	NEWEST_LOG_FN=$(ls ../etl-log*.gz| tail -n1)
	else
	NEWEST_LOG_FN="../logs/etl.log"
fi
echo $NEWEST_LOG_FN
ERRORS=$(zgrep  -v 'replace_all("hbzId",' $NEWEST_LOG_FN | grep -B1 'Error while executing Fix expression')
SHORTEND_ERRORS=$(echo "$ERRORS" | sed 's#.*001=\(.*\), .*#\1#g'| sed 's#, 0.*##g')
if [ -n "$ERRORS" ]; then
        chrlen=${#ERRORS}
        ERRORS_LINES_COUNT=$(echo "${ERRORS}"|wc -l)
        if [ ${chrlen} -gt 500000 ]; then
                ERRORS_TAILED="Es sind zu viele ERRORS - es werden nur die letzten 30 Zeilen angezeigt von insgesamt $ERRORS_LINES_COUNT:"
                SHORTEND_ERRORS="$ERRORS_TAILED $(echo "$SHORTEND_ERRORS"|tail -n50)"
        fi
	echo "$SHORTEND_ERRORS"
        mail -s "FIX errors in Alma Fix ETL" "${MAIL_TO}" -a "From: ${MAIL_FROM}" << EOF
Getriggert von ausgeführt in $(pwd)/getLogsEtlFixErrors.sh :

Achte auf das Datum der ERROR-Zeilen - evtl. sind das alte Fehler!

$SHORTEND_ERRORS
EOF

fi

