#!/bin/sh

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
JAVA_OPTS="$4"
DO_ETL_UPDATE="$5"

HOME="/home/sol"

# it is important to set the proper locale
. $HOME/.locale
export JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64/
JAVA_OPTS=$(echo "$JAVA_OPTS" |sed 's#,#\ #g')

cd $HOME/git/$REPO
ETL_TOKEN=$(cat scripts/.secrets/ETL_TOKEN)

case $ACTION in
  start)
       cd ..; mvn clean install -DskipTests=true; cd -
       if [ -f target/universal/stage/RUNNING_PID ]; then
          kill $(cat target/universal/stage/RUNNING_PID)
          rm target/universal/stage/RUNNING_PID
       fi
       JAVA_OPTS="$JAVA_OPTS -XX:+ExitOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError -DpreferIPv4Stack"
       sbt clean
       sbt --java-home $JAVA_HOME stage
       ./target/universal/stage/bin/lobid-resources-web -Dhttp.port=$PORT -no-version-check > monit_start.log &
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

