#!/bin/bash
# see https://github.com/hbz/lobid-resources/issues/1075

set -euo pipefail # See http://redsymbol.net/articles/unofficial-bash-strict-mode/
RESOURCES_DIRECTORY=/data/DE-605/resources

cd data

cp beacon_head.txt hbzlod-pndbeacon.txt

curl -L "http://lobid.org/resources/search?q=%28describedBy.dateModified%3A%3E20200615+OR+describedBy.dateCreated%3A%3E20200615%29+AND+contribution.agent.id%3A*&format=bulk"  > $RESOURCES_DIRECTORY/resources-from-20200615.jsonl

cat $RESOURCES_DIRECTORY/*.jsonl | jq -r '. | select(.contribution != null) | .contribution[].agent.id'  | grep -v '_:b' | sort | uniq -c | sort -n -r | sed 's#\(.*\)https://d-nb.info/gnd/\(.*\)#\2|\1#g' | tr -d ' '|grep -v null  >> hbzlod-pndbeacon.txt

scp hbzlod-pndbeacon.txt lobid@emphytos:/usr/local/lobid/src/lobid.org/download/beacons/
