#!/bin/bash
set -euo pipefail # See http://redsymbol.net/articles/unofficial-bash-strict-mode/
IFS=$'\n\t'

# Run install-dependencies.sh in the project root if dependencies are not set up.

TEST_FILE="/data/other/datenportal/export/alma/prod/update.xml.bgzf"
WORKING_DIR="$(pwd)"
PROJECT_ROOT=$WORKING_DIR/../../..
cd $PROJECT_ROOT
mkdir -p log

# index alma test resources:
# don't forget to build and install metafatcure-core and metafacture-fix if there are any changes!
mvn install -DskipTests=true
mvn exec:java -Dexec.mainClass="org.lobid.resources.run.AlmaMarcXmlFix2lobidJsonEs" -Dexec.args="${TEST_FILE} resources-almatest NOALIAS indexcluster.lobid.org weywot exact src/main/resources/alma/alma.fix" -DjsonLdContext="http://test.lobid.org/resources/context.jsonld" -Dexec.cleanupDaemonThreads=false
