#!/bin/bash
# see https://github.com/hbz/lobid-resources/issues/2252
# should be scheduled after weekly ETL
# If bulk download not > 20 GB the filename ois prefixed with "broken".
# Removes old data (4 weeks aka files are enough).
# Creates a symlink "latestLobidResources.jsonl.gz" to the latest bulk file.:

# Deliberately not setting this because we dont' want to just bail out, e.g. when
# curl fails, but do some mending afterwards:
#set -euo pipefail # See http://redsymbol.net/articles/unofficial-bash-strict-mode/

MAIL_TO_WEBHOOK_SUBSCRIBER=$(cat .secrets/MAIL_TO_WEBHOOK_SUBSCRIBER)
DATE=$(date +%Y-%m-%d)

FULLDUMP_FNAME_SUFFIX="_lobid-resources.jsonl.gz"
FULLDUMP_FNAME=${DATE}${FULLDUMP_FNAME_SUFFIX}

FULLDUMP_DIR="/data/DE-605/resources"

function rmOldData {
        if [ $(ls ${FULLDUMP_DIR}/*${FULLDUMP_FNAME_SUFFIX}| wc -l) -gt 4 ]; then
                echo "more than 4 fulldumps - deleting oldest and test again ..."
                rm -f $(ls ${FULLDUMP_DIR}/*${FULLDUMP_FNAME_SUFFIX} |head -n1)
                rmOldData
        fi
}

rmOldData

echo "Start: get fulldump at $(date)"
# get fulldump
curl -L "http://localhost:7507/resources/search?q=*&format=bulk" |gzip  > ${FULLDUMP_FNAME}
if [[ $(find ${FULLDUMP_FNAME} -type f -size +19G 2>/dev/null) ]]; then
  echo "size seems good"
  else
    echo "size seems to be too small ..."
    mv ${FULLDUMP_FNAME} ${FULLDUMP_DIR}/broken_${FULLDUMP_FNAME}
    exit 1
fi

mv ${FULLDUMP_FNAME} ${FULLDUMP_DIR}/
cd  ${FULLDUMP_DIR}
# update symlink to the latest dump
rm latestLobidResources.jsonl.gz
ln -s ${FULLDUMP_FNAME} latestLobidResources.jsonl.gz

mail -s "[sysad] [lobid-resources] Fulldump lobid-resources published" "${MAIL_TO_WEBHOOK_SUBSCRIBER}" -a "From: ${MAIL_FROM}" << EOF
Siehe https://lobid.org/download/dumps/lobid-resources/${FULLDUMP_FNAME} aka
https://lobid.org/download/dumps/lobid-resources/latestLobidResources.jsonl.gz
EOF
