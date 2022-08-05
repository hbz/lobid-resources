#!/bin/bash
set -euo pipefail # See http://redsymbol.net/articles/unofficial-bash-strict-mode/
IFS=$'\n\t'

# Get all resources listed in the file "testIds.txt" in their raw format 
# (that is: AlephMabXml). Add them to the resources serving as junit test. 
# Then ETL the data using the MabXml2lobidJsonEs class. See at the bottom of 
# this script for the hardcoded server and cluster name etc.

# Run install-dependencies.sh in the project root if dependencies are not set up.

TEST_FILE="/data/other/datenportal/export/alma/prod/update.xml.bgzf"
WORKING_DIR="$(pwd)"
PROJECT_ROOT=$WORKING_DIR/../../..
cd $PROJECT_ROOT
mkdir -p log

# index alma test resources:
mvn exec:java -Dexec.mainClass="org.lobid.resources.run.AlmaMarcXmlFix2lobidJsonEs" -Dexec.args="${TEST_FILE} resources-almatest NOALIAS weywot4.hbz-nrw.de weywot create" -DjsonLdContext="http://test.lobid.org/resources/context.jsonld" -Dexec.cleanupDaemonThreads=false
