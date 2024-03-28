# author: dr0i
#
# grep all logs concerning the triggering of the Webhook in ETL.log , filtered
# to today's entries, and send email if ERRORS appeared or response code != 204
# see https://github.com/hbz/lobid-resources/issues/1512

MAIL_TO=$(cat .secrets/MAIL_TO_WEBHOOK_SUBSCRIBER)
MAIL_FROM=$(cat .secrets/MAIL_FROM)

ERROR_PATTERN="Got response code\|HttpPoster\|notifyWebhook"

NEWEST_LOG_FN="../logs/etl.log"
ERRORS=$(grep  -B1 "$ERROR_PATTERN" $NEWEST_LOG_FN)
DATE=$(date +"%F")
ERRORS=$(echo "$ERRORS" | grep $DATE| grep "ERROR\|response code"|grep -v 204)

if [ -n "$ERRORS" ]; then
        mail -s "ERRORS when triggering Webhook in Alma Fix ETL" "${MAIL_TO}" -a "From: ${MAIL_FROM}" << EOF
$ERRORS
EOF
        echo $ERRORS
fi

