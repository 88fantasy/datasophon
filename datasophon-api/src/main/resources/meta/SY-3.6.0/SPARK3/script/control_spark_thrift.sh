#!/bin/sh

usage="Usage: start.sh (start|stop|restart|status) "

# if no args specified, show usage
if [ $# -le 0 ]; then
  echo $usage
  exit 1
fi

current_path=$(cd `dirname $0`;pwd)
startStop=$1
shift

echo "Begin $startStop ......"

export PID_DIR=/tmp
export LOG_DIR=$current_path/logs
export STOP_TIMEOUT=3

export JAVA_HOME=/usr/local/jdk1.8.0_333

log=$LOG_DIR/spark-hive-org.apache.spark.sql.hive.thriftserver.HiveThriftServer2.log
spark_thrift_port=10016

exec_command_start="$current_path/sbin/start-thriftserver.sh --proxy-user hive --master yarn --conf spark.dynamicAllocation.enabled=true --conf spark.shuffle.service.enabled=true --conf spark.driver.memory=4g --conf spark.executor.memory=2g --conf spark.dynamicAllocation.minExecutors=3 --conf spark.dynamicAllocation.maxExecutors=100 --conf spark.dynamicAllocation.shuffleTracking.enabled=true --conf spark.cleaner.periodicGC.interval=5min --hiveconf hive.server2.thrift.port=${spark_thrift_port}"
exec_command_stop="$current_path/sbin/stop-thriftserver.sh"
exec_command_status="netstat -tlnp | grep ${spark_thrift_port} |wc -l"

function start() {
  echo starting thriftserver
  echo "nohup $exec_command_start > $log 2>&1 &"
  nohup $exec_command_start > $log 2>&1 &
}

function stop() {
    echo stopping thriftserver
    echo "nohup $exec_command_stop > $log 2>&1 &"
    nohup $exec_command_stop > $log 2>&1 &

}

function status() {
  ret=`netstat -tlnp | grep ${spark_thrift_port} |wc -l`
  if [ "${ret}" = "1" ]; then
    echo "saprk thriftserver is running"
  else
    echo "saprk thriftserver is not exists"
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
    sleep 10s
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

echo "End $startStop spark thrift."