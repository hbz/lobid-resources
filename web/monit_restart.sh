#!/bin/sh
set -euo pipefail # See http://redsymbol.net/articles/unofficial-bash-strict-mode/

USAGE="<GIT REPO NAME> {start|stop} <PORT> [<JAVA OPTS>]"

if [ $# -lt 3 ]; then
  echo "$USAGE
    THIS SCRIPT SHOULD ONLY BE USED BY -MONIT-!

    If you want to restart an instance, use ./restart.sh

    First 3 parameters are mandatory.
    Don't forget that the process is monitored by 'monit'.
    It will restart automatically if you stop the API.
    If you want to stop it permanently, do 'sudo /etc/ini.d/monit stop' first.
    "
 exit 65
fi

REPO=$1
ACTION=$2
PORT=$3
JAVA_OPTS=""
if [ $# -gt 3  ]; then
  JAVA_OPTS=$(echo "$4" |sed 's#,#\ #g')
fi
DO_ETL_UPDATE=""
if [ $# -eq 5 ]; then
  DO_ETL_UPDATE="$5"
fi

HOME="/home/sol"

# it is important to set the proper locale
. $HOME/.locale
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64/
JAVA_OPTS="$JAVA_OPTS --add-exports=java.base/sun.net.www.protocol.file=ALL-UNNAMED --add-opens=java.base/sun.net.www.protocol.file=ALL-UNNAMED"

cd $HOME/git/$REPO
ETL_TOKEN=$(cat scripts/.secrets/ETL_TOKEN)

case $ACTION in
  start)
       cd ..
       git fetch && git reset --hard ssh/master && git submodule update --init --recursive --remote || ( echo "ERROR when using git. Aborting restart ..."; exit 1)
       if [ ! -f lookup-tables/data/opacLinks/isil2opac_issn.tsv ]; then
          echo "ERROR: The file lookup-tables/data/opacLinks/isil2opac_issn.tsv is missing. Aborting the restart ..."
          exit # see #2306
       fi
       mvn clean install -DskipTests=true; cd -
       if [ -f target/universal/stage/RUNNING_PID ]; then
          kill $(cat target/universal/stage/RUNNING_PID)
          rm target/universal/stage/RUNNING_PID
       fi
       export JAVA_OPTS="$JAVA_OPTS -XX:+ExitOnOutOfMemoryError -DpreferIPv4Stack"
       sbt -Djava.security.manager=allow clean
       sbt -Djava.security.manager=allow stage
       ( ./target/universal/stage/bin/lobid-resources-web -Djava.security.manager=allow -Dhttp.port=$PORT -no-version-check > monit_start.log & ) && echo "Done starting!" >> monit_start.log
       if [ -n "$DO_ETL_UPDATE" -a $(tail -n100 logs/etl.log  |grep -c "Finishing indexing of ES index 'resources-alma-fix") -eq 0 ]; then
          echo "Automatical updates-ETL triggered and last entries were not ok, thus starting ETL. Sleep 100s before starting ETL ..." >> monit_start.log
          sleep 100
          curl http://localhost:$PORT/resources/webhook/update-alma?token=$ETL_TOKEN
        fi
        echo "Done starting!" >> monit_start.log
  ;;
 stop)
  kill $(cat target/universal/stage/RUNNING_PID)
  sleep 14
  kill -9 $(cat target/universal/stage/RUNNING_PID)
  rm target/universal/stage/RUNNING_PID
  ;;
 *)
  echo "usage: $USAGE"
  ;;
esac
exit 0

