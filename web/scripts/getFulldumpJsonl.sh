#!/bin/bash
# see https://github.com/hbz/lobid-resources/issues/2252
# should be scheduled after weekly ETL

set -euo pipefail # See http://redsymbol.net/articles/unofficial-bash-strict-mode/

MAIL_TO_WEBHOOK_SUBSCRIBER=$(cat .secrets/MAIL_TO_WEBHOOK_SUBSCRIBER)
DATE=$(date +%Y-%m-%d)
FULLDUMP_FNAME=${DATE}_lobid-resources.jsonl.gz
cd /data/DE-605/resources/

echo "Start: get fulldump at $(date)"
# get fulldump 
curl -L "http://localhost:7507/resources/search?q=*&format=bulk" |gzip  > ${FULLDUMP_FNAME}
if [[ $(find ${FULLDUMP_FNAME} -type f -size +19G 2>/dev/null) ]]; then
  echo "size seems good"
  else
    echo "size seems to be too small ..."
    exit 1
fi

# update symlink to the latest dump
rm latestLobidResources.jsonl.gz
ln -s ${FULLDUMP_FNAME} latestLobidResources.jsonl.gz

mail -s "Fulldump lobid-resources published" "${MAIL_TO_WEBHOOK_SUBSCRIBER}" -a "From: ${MAIL_FROM}" << EOF
Siehe ${FULLDUMP_FNAME} aka
https://lobid.org/download/dumps/lobid-resources/latestLobidResources.jsonl.gz
EOF
