# author: dr0i
#
# grep all multiple hbzIds and send mail

MAIL_TO=$(cat .secrets/MAIL_TO)
MAIL_FROM=$(cat .secrets/MAIL_FROM)
SEND_TO_VERBUND=$(cat .secrets/SEND_TO_VERBUND)

cd ../
NEWEST_LOG_FN=$(ls etl-log*.gz| tail -n1)
echo $NEWEST_LOG_FN
HBZIDS=$( zgrep -B2 'java.lang.IllegalStateException: Expected String, got Array' $NEWEST_LOG_FN |grep -B1 'replace_all("hbzId",'| grep hbzId= | sed "s#\(.*\)almaMmsId=\(.*\), id=.*hbzId\(.*\)#\2\3#g" |tr = , |sed "s#\[\(.*\)\].*#\1#g"|sort -u)
echo "$HBZIDS" >> $NEWEST_LOG_FN

if [ -n "$HBZIDS" ]; then
       echo "non null: $HBZIDS"
       mail -s "Multiple hbzId entries in Alma" "${MAIL_TO}" -a "From: ${MAIL_FROM}" << EOF
       Triggered by crontab@weywot1
       To be send to ${SEND_TO} with subject "Doppelte hbz-Id":
$HBZIDS
EOF
fi

