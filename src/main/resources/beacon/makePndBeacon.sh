#!/bin/bash
# see https://github.com/hbz/lobid-resources/issues/1075
# when scheduled: change into this (./) directory first!

DATE=$(date +%Y-%m-%d)
FULLDUMP_FNAME=/data/DE-605/resources/latestLobidResources.jsonl.gz
UPDATES_FNAME=/data/DE-605/resources/resources-starting-from-${DATE}.jsonl.gz

cd data

# copy and adjust beacon header
cp beacon_head.txt hbzlod-pndbeacon.bf
TIMESTAMP_NEXT_MONTH=$(date --date="31 day" "+%Y-%m-%d")
sed -i "s#REVISIT.*#REVISIT: $TIMESTAMP_NEXT_MONTH#g" hbzlod-pndbeacon.bf
TIMESTAMP=$(date "+%Y-%m-%d")
sed -i "s#TIMESTAMP.*#TIMESTAMP: $TIMESTAMP#g" hbzlod-pndbeacon.bf

echo "Start: get updates at $(date)"
#get data updates
curl -L "http://localhost:7507/resources/search?q=%28describedBy.resultOf.object.dateModified%3A%3E${DATE}+OR+describedBy.resultOf.object.dateCreated%3A%3E${DATE}%29+AND+contribution.agent.id%3A*&format=bulk" |gzip  > ${UPDATES_FNAME}

echo "Start: merge updates at $(date)"
# merge and count occurences => beacon
##cat hbzlod-pndbeacon_fulldump.txt hbzlod-pndbeacon_updates.txt | sort | uniq -c | sort -n -r | tr -d ' '| grep -v null >> hbzlod-pndbeacon.txt
zcat ${FULLDUMP_FNAME} ${UPDATES_FNAME} | jq -r '. | select(.contribution != null) | .contribution[].agent.id' | sort | uniq -c | sort -n -r | sed 's#\(.*\)https://d-nb.info/gnd/\(.*\)#\2|\1#g' | tr -d ' '| grep -v null >> hbzlod-pndbeacon.bf

cp hbzlod-pndbeacon.bf /files/lobid-files/beacons/hbzlod-pndbeacon.bf

echo "End: providing beacon at $(date)"
