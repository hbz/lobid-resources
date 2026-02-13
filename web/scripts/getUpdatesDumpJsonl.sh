#!/bin/bash
# see https://github.com/hbz/lobid-resources/issues/2252
# should be scheduled daily besides Saturday and Sunday.
# TODO:
# 1. get the last succesfull update and use that date
# 2. remove old data (10 days aka files are enough)

set -euo pipefail # See http://redsymbol.net/articles/unofficial-bash-strict-mode/

MAIL_TO_WEBHOOK_SUBSCRIBER=$(cat .secrets/MAIL_TO_WEBHOOK_SUBSCRIBER)

DATE=$(date +%Y-%m-%d)

UPDATES_FNAME=${DATE}_lobid-resources-updates.jsonl.gz
cd /data/DE-605/resources/

echo "Start: get updates from $(date)"
# get updates
curl -L "http://localhost:7507/resources/search?q=%28describedBy.resultOf.object.dateModified%3A%3E>${DATE}+OR+describedBy.resultOf.object.dateCreated%3A%3E>${DATE}%29&format=bulk" |gzip > ${UPDATES_FNAME}

mail -s "Udates of lobid-resources published" "${MAIL_TO_WEBHOOK_SUBSCRIBER}" -a "From: ${MAIL_FROM}" << EOF
Siehe https://lobid.org/download/dumps/lobid-resources/${UPDATES_FNAME}.
EOF
