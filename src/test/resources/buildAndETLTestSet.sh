#!/bin/bash
set -euo pipefail # See http://redsymbol.net/articles/unofficial-bash-strict-mode/
IFS=$'\n\t'

# Get all resources listed in the file "testIds.txt" in their raw format 
# (that is: AlephMabXml). Add them to the resources serving as junit test. 
# Then ETL the data using the MabXml2lobidJsonEs class. See at the bottom of 
# this script for the hardcoded server and cluster name etc.

# Run install-dependencies.sh in the project root if dependencies are not set up.

WORKING_DIR="$(pwd)"
DATA_DIR=xml # relative to $WORKING_DIR
PROJECT_ROOT=$WORKING_DIR/../../..
TEST_FILE="test.tar.bz2"

# build test set:
cd $PROJECT_ROOT
mvn clean assembly:assembly -DdescriptorId=jar-with-dependencies -DskipTests
mvn exec:java -Dexec.mainClass="org.lobid.resources.run.DownloadTestSet"
cd $WORKING_DIR/$DATA_DIR
tar xfj $WORKING_DIR/hbz01XmlClobs.tar.bz2
cd $WORKING_DIR
rm $TEST_FILE || true
tar cfj $TEST_FILE $DATA_DIR

# index test resources:
cd $PROJECT_ROOT
mkdir -p log
mvn exec:java -Dexec.mainClass="org.lobid.resources.run.MabXml2lobidJsonEs" -Dexec.args="$WORKING_DIR/$TEST_FILE resources-smalltest NOALIAS weywot4.hbz-nrw.de weywot create" -DjsonLdContext="http://test.lobid.org/resources/context.jsonld"
