#!/bin/bash
# see https://github.com/hbz/lobid-resources/issues/1075

set -euo pipefail # See http://redsymbol.net/articles/unofficial-bash-strict-mode/
RESOURCES_DIRECTORY=/data/DE-605/resources

cd data

# copy and adjust beacon header
cp beacon_head.txt hbzlod-pndbeacon.txt
TIMESTAMP_NEXT_MONTH=$(date --date="31 day" "+%Y-%m-%d")
sed -i "s#REVISIT.*#REVISIT: $TIMESTAMP_NEXT_MONTH#g" hbzlod-pndbeacon.txt
TIMESTAMP=$(date "+%Y-%m-%d")
sed -i "s#TIMESTAMP.*#TIMESTAMP: $TIMESTAMP#g" hbzlod-pndbeacon.txt

#get "new" data ...
curl -L "http://lobid.org/resources/search?q=%28describedBy.resultOf.object.dateModified%3A%3E2023-05-29+OR+describedBy.resultOf.object.dateCreated%3A%3E2023-05-29%29+AND+contribution.agent.id%3A*&format=bulk"  > $RESOURCES_DIRECTORY/resources-starting-from-20230529.jsonl

# ... and filter the new data along the old dump (from up to 2023-05-29)
cat $RESOURCES_DIRECTORY/*.jsonl | jq -r '. | select(.contribution != null) | .contribution[].agent.id'  | grep -v '_:b' | sort | uniq -c | sort -n -r | sed 's#\(.*\)https://d-nb.info/gnd/\(.*\)#\2|\1#g' | tr -d ' '|grep -v null  >> hbzlod-pndbeacon.txt



scp hbzlod-pndbeacon.txt lobid@emphytos:/usr/local/lobid/src/lobid.org/download/beacons/
