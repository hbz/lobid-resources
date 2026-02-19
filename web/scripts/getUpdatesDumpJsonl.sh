#!/bin/bash
# Get lobid-reosurces bulk download updates.
# See https://github.com/hbz/lobid-resources/issues/2252
# Should be scheduled daily besides Saturday and Sunday.
#
# 1. start date is:
# 1a) default is yesterday
# 1b) if exist use last succesfull update
# 1c) if parameter exists use this
#
# 2. remove old data (10 days aka files are enough)
# 3. get from "start date" to "now" and store as file
# 4 test if >1kB . If it's not, remove the file and bail out
# 5. inform subscribers

set -euo pipefail # See http://redsymbol.net/articles/unofficial-bash-strict-mode/

MAIL_TO_WEBHOOK_SUBSCRIBER=$(cat .secrets/MAIL_TO_WEBHOOK_SUBSCRIBER)
MAIL_FROM=$(cat .secrets/MAIL_FROM)

# default is yesterday
DATE_FROM=$(date  --date="yesterday" +"%Y-%m-%d")
DATE_TO=$(date  +"%Y-%m-%d")

UPDATES_FNAME_SUFFIX="_lobid-resources-updates.jsonl.gz"

cd /data/DE-605/resources/

function rmOldData {
        if [ $(ls *${UPDATES_FNAME_SUFFIX}| wc -l) -gt 10 ]; then
                echo "more than 10 updates - deleting oldest and test again ..."
                rm $(ls *${UPDATES_FNAME_SUFFIX} |head -n1)
                rmOldData
        fi
}

rmOldData

LAST_UPDATE=$(ls *_to_*${UPDATES_FNAME_SUFFIX} | sed "s#.*_to_\(.*\)${UPDATES_FNAME_SUFFIX}#\1#g"|tail -n1)

# if exits: take last update date
if [ -n "$LAST_UPDATE" ] ; then
       DATE_FROM=$LAST_UPDATE
fi
# precedence is the parameter (if given)
if [ -n "${1-}" ] ; then
       DATE_FROM=$1
fi

echo $DATE_FROM

UPDATES_FNAME=${DATE_FROM}_to_${DATE_TO}${UPDATES_FNAME_SUFFIX}

echo "Start: get updates from $DATE_FROM"
# get updates
curl -L "http://localhost:7507/resources/search?q=describedBy.resultOf.object.dateModified:>${DATE_FROM}+OR+describedBy.resultOf.object.dateCreated:>${DATE_FROM}&format=bulk" |gzip > ${UPDATES_FNAME}

# if file is too small, remove it and bail out
if [[ $(find ${UPDATES_FNAME} -type f -size +1k 2>/dev/null) ]]; then
  echo "size seems good"
  else
    echo "size seems to be too small ..."
    rm ${UPDATES_FNAME}
    exit 1
fi

mail -s "Updates of lobid-resources published" "${MAIL_TO_WEBHOOK_SUBSCRIBER}" -a "From: ${MAIL_FROM}" << EOF
Siehe https://lobid.org/download/dumps/lobid-resources/${UPDATES_FNAME}.
EOF
