#!/bin/bash

BRANCH=$1
FILE=$2
INDEX_NAME=$3
INDEX_ALIAS_SUFFIX=$4
ES_NODE=$5
ES_CLUSTER_NAME=$6
UPDATE_NEWEST_INDEX=$7
UPDATE_LIST=$8

echo "You specified:
$1 $2 $3 $4 $5 $6 $7 $8"

if [ $# -lt 7 ]
	then
	echo "Usage: <BRANCH> <FILE> <INDEX_NAME> <INDEX_ALIAS_SUFFIX> <ES_NODE> <ES_CLUSTER_NAME> <UPDATE_NEWEST_INDEX>

example parameter:

master
files/open_data/closed/hbzvk/index.hbz-nrw.de/alephxml/clobs/baseline/DE-605-aleph-2015050310.tar.gz
lobid-resources
-staging
quaoar2.hbz-nrw.de
quaoar
update

<INDEX_ALIAS_SUFFIX> may be:
	'NOALIAS': will not set a new index alias

<UPDATE_NEWEST_INDEX> may be:
	'update' : most recent index will be updated
	'exact' : a timestamp suffix in the index name isn't mandatory (it would be created if not already set in the index name)
	All other values will result in creating an index if the giving one does not exist.  If it should be existent already, it will be updated.

You may give as 8th parameter a file containing a list of filenames. These will be transformed and indexed the same way as described above.
"
	exit 65
fi

function indexFile() {
	mvn exec:java -DkeyToGetMainId=almaMmsId -Dexec.mainClass="org.lobid.resources.run.AlmaMarcXml2lobidJsonEs" -Dexec.args="$1 $INDEX_NAME $INDEX_ALIAS_SUFFIX $ES_NODE $ES_CLUSTER_NAME $UPDATE_NEWEST_INDEX" -Dexec.cleanupDaemonThreads=false
}

function index() {
	DATE=$(date "+%Y%m%d-%H%M")
	indexFile $FILE
	# optionally a file with a list of file names
	if [ -n "$UPDATE_LIST" ]; then
		echo "Taking your file $UPDATE_LIST with the list into account ..."
		for i in $(cat $UPDATE_LIST); do
			indexFile $i
		done
	fi
}

# do this only once:
mvn clean assembly:assembly -DdescriptorId=jar-with-dependencies -DskipTests=true -DskipIntegrationTests
index

exit 0
