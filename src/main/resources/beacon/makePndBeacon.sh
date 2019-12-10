#!/bin/bash
set -euo pipefail # See http://redsymbol.net/articles/unofficial-bash-strict-mode/

cd data
TIMESTAMP=$(date +%Y-%m-%d)
REVISIT=$(date +%Y-%m-%d --date="$TIMESTAMP  +1 month")

sed -i "s/#REVISIT.*/#REVISIT: $REVISIT/g" beacon_head.txt
sed -i "s/#TIMESTAMP.*/#TIMESTAMP: $TIMESTAMP/g" beacon_head.txt

cp beacon_head.txt hbzlod-pndbeacon.txt

curl -L "http://lobid.org/resources/search?q=%28describedBy.dateModified%3A%3E20170726+OR+describedBy.dateCreated%3A%3E20170726%29+AND+contribution.agent.id%3A*&format=bulk"  > resources-from-20170726.jsonl 

cat *.jsonl | jq -r '. | select(.contribution != null) | .contribution[].agent.id'  | sort | uniq -c | sort -n -r | sed 's#\(.*\)https://d-nb.info/gnd/\(.*\)#\2|\1#g' | tr -d ' '|grep -v null  >> hbzlod-pndbeacon.txt

scp hbzlod-pndbeacon.txt lobid@emphytos:/usr/local/lobid/src/lobid.org/download/beacons/
