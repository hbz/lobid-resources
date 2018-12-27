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
TEST_FILE="loc-works_ca100k.nt.gz"

# index test resources:
cd $PROJECT_ROOT
mvn exec:java -Dexec.mainClass="org.lobid.resources.run.LocBibframe2JsonEs" -Dexec.args="$WORKING_DIR/$TEST_FILE loc-smalltest NOALIAS 193.30.112.84 lobid-lab create"
