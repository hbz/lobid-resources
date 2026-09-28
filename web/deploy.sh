#!/bin/bash

# This script automates the deployment of the application.

# Ensure the script exits immediately if any command fails, and that all variables are defined before use.
# The 'pipefail' option ensures that if any command in a pipeline fails, the entire pipeline is considered to have failed.
set -euo pipefail

##
## Configuration
##

REPO=$1 # "lobid-resources-rpb-test"
JAVA_OPTS=$2 # -Xmx2048m,-Xms1024m

HOME="/home/sol"
BASE_DIR="${HOME}/git/${REPO}"
WEB_DIR="$BASE_DIR/web"

LOG_FILE="${WEB_DIR}/logs/deploy.log"

SERVICE_NAME="${REPO}.service"

# it is important to set the proper locale
. $HOME/.locale
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64/
JAVA_OPTS=$(echo "$JAVA_OPTS" |sed 's#,#\ #g')

##
## Logging
##

# Log everything to console and logfile
# same as executing `./deploy.sh lobid-resources-rpb-test -Xmx2048m,-Xms1024m 2>&1 | tee -a ../logs/deploy.log`
exec > >(tee -a "$LOG_FILE")
exec 2>&1

echo
echo "=================================================="
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Starting deployment"
echo "=================================================="

##
## Cleanup / Error handling
##

cleanup() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] Cleanup finished"
}

error_handler() {
    # Capture the exit code of the failed command
    local exit_code=$?
    echo
    echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] DEPLOYMENT FAILED"
    echo "Exit code: ${exit_code}"
    echo "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"
    exit "$exit_code"
}

# Cleanup on script exit
trap cleanup EXIT
# Call error handler on any error
trap error_handler ERR

##
## Deployment steps
##

echo "==> Changing to repository directory"
cd "${BASE_DIR}"

echo "==> Fetching latest changes"
git fetch

echo "==> Resetting working tree"
git reset --hard origin/master

echo "==> Updating submodules"
git submodule update --init --recursive --remote

if [ ! -f lookup-tables/data/opacLinks/isil2opac_issn.tsv ]; then
    echo "ERROR: Required lookup table missing:"
    echo "lookup-tables/data/opacLinks/isil2opac_issn.tsv"
    exit 1 # see #2306
fi

echo "==> Building Maven modules"
mvn clean install -DskipTests=true

echo "==> Building Play stage"
cd "${WEB_DIR}"
export JAVA_OPTS="$JAVA_OPTS -XX:+ExitOnOutOfMemoryError -DpreferIPv4Stack"
sbt clean
sbt --java-home $JAVA_HOME stage

echo "==> Restarting service"
sudo systemctl restart "$SERVICE_NAME"

echo "==> Waiting for application startup"
sleep 10

echo "==> Checking service state"
systemctl is-active --quiet "$SERVICE_NAME"

echo
echo "=================================================="
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Deployment successful"
echo "=================================================="
