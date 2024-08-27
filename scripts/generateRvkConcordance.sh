#/bin/bash
# Date: 2024-08
# Description: gets the monthly generated aggregated data from culturegraph
# Is called from crontab every second Wednesday of the month.
# Takes 5.5h, single process on quaoar.
# Generated tsv: ~ 257 MB
# See https://github.com/hbz/lobid-resources/issues/1058.

URL_ROOT="https://data.dnb.de/culturegraph/"
TARGET_FNAME="/data/other/cg/aggregate.marcxml.gz"

FNAME=$(curl $URL_ROOT | grep '<a href="aggregate_' | sed 's#.*\<a href="aggregate_\(.*\)".*#aggregate_\1#g')
echo Got filename: "$FNAME"
wget "$URL_ROOT$FNAME" -O $TARGET_FNAME

FNAME_SIZE=$(ls -s $TARGET_FNAME |cut -d ' ' -f1)
if [ $FNAME_SIZE -gt 8654321 ]; then # 9593288 blocks was aggregate_20240507.marcxml.gz
 cd ..
 export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64/
 mvn exec:java -Dexec.mainClass="org.lobid.resources.run.CulturegraphXmlFilterHbzRvkToTsv" -Dexec.args=$TARGET_FNAME
fi

