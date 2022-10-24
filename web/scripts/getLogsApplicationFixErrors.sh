# author: dr0i
#
# grep all FIX errors (besides the multiple hbzIds (see the extra script "getMultipleHbzid.sh") and send email

MAIL_TO=$(cat .secrets/MAIL_TO)
MAIL_FROM=$(cat .secrets/MAIL_FROM)

cd ../
NEWEST_LOG_FN=$(ls application-log*.gz| tail -n1)
echo $NEWEST_LOG_FN
ERRORS=$(zgrep  -v 'replace_all("hbzId",' $NEWEST_LOG_FN | grep -B1 'Error while executing Fix expression')
if [ -n "$ERRORS" ]; then
	chrlen=${#ERRORS}
	ERRORS_LINES_COUNT=$(echo "${ERRORS}"|wc -l)
	if [ ${chrlen} -gt 500000 ]; then
		ERRORS_TAILED="Es sind zu viele ERRORS - es werden nur die letzten 30 Zeilen angezeigt von insgesamt $ERRORS_LINES_COUNT:"
		ERRORS_TAILED="$ERRORS_TAILED $(echo "$ERRORS"|tail -n30)"
	fi
       mail -s "FIX errors in Alma Fix ETL" "${MAIL_TO}" -a "From: ${MAIL_FROM}" << EOF
Getriggert von ausgeführt in $(pwd)/scripts :

Achte auf das Datum der ERROR-Zeilen - evtl. sind das alte Fehler!

$ERRORS_TAILED
EOF
fi

