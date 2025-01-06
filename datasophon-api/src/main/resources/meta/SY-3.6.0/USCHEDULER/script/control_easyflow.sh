#!/bin/sh

usage="Usage: start.sh (start|stop|restart|status) "

# if no args specified, show usage
if [ $# -le 0 ]; then
  echo $usage
  exit 1
fi

current_path=$(cd `dirname $0`;pwd)
EXECUTOR_HOME=$current_path/..
EXECUTOR_HOME=`cd "$EXECUTOR_HOME"; pwd`
startStop=$1
shift

echo "Begin $startStop ......"

export SPARK_HOME3=${SPARK_HOME3:-/opt/datasophon/spark3}
export JAVA_HOME=/usr/local/jdk1.8.0_333
export STOP_TIMEOUT=10

log=$current_path/easyflow-web.log
pid=$current_path/easyflow-web.pid

exec_command_start="${SPARK_HOME3}/bin/spark-submit  --master yarn --deploy-mode client --name EasyFlow-WebApp --conf spark.dynamicAllocation.enabled=true --conf spark.shuffle.service.enabled=true --conf spark.driver.memory=2g --conf spark.dynamicAllocation.minExecutors=2 --conf spark.dynamicAllocation.maxExecutors=10 --conf spark.dynamicAllocation.shuffleTracking.enabled=true --conf spark.cleaner.periodicGC.interval=5min --class com.chinaunicom.easyflow.WebApp $EXECUTOR_HOME/easyflow-executor.jar"

function start() {
  if [ -f $pid ]; then
    if kill -0 `cat $pid` > /dev/null 2>&1; then
      echo easyflow running as process `cat $pid`.  Stop it first.
      exit 1
    fi
  fi

  echo starting easyflow, logging to $log

  echo "nohup $exec_command_start > $log 2>&1 &"
  nohup $exec_command_start > $log 2>&1 &
  echo $! > $pid

}

function stop() {

  if [ -f $pid ]; then
    TARGET_PID=`cat $pid`
    if kill -0 $TARGET_PID > /dev/null 2>&1; then
      echo stopping easyflow
      kill $TARGET_PID
      sleep $STOP_TIMEOUT
      if kill -0 $TARGET_PID > /dev/null 2>&1; then
        echo "easyflow did not stop gracefully after $STOP_TIMEOUT seconds: killing with kill -9"
        kill -9 $TARGET_PID
      fi
    else
      echo no easyflow to stop
    fi
    rm -f $pid
  else
    echo no easyflow to stop
  fi

}

function status() {
  if [ -f $pid ]; then
    TARGET_PID=`cat $pid`
    echo "pid is $TARGET_PID"
  else
    echo "easyflow  pid file is not exists"
  fi
  kill -0 $TARGET_PID
  if [ $? -eq 0 ]
  then
    echo "easyflow is  running "
  else
    echo "easyflow  is not running"
    exit 1
  fi
}

case $startStop in
  (start)
    start
    ;;

  (stop)
    stop
    ;;
  (restart)
    stop
    sleep 2s
    start
    ;;
  (status)
    status
    ;;
  (*)
    echo $usage
    exit 1
    ;;

esac

echo "End $startStop easyflow."
