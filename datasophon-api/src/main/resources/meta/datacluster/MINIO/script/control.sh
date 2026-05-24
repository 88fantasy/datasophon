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

export PID_DIR=$current_path/pid
export LOG_DIR=$current_path/logs
export STOP_TIMEOUT=10

# 加载环境变量
. $current_path/conf/minio_env.sh

if [ ! -d "$LOG_DIR" ]; then
  mkdir $LOG_DIR
fi

log=$LOG_DIR/minio.out
pid=$PID_DIR/minio.pid

exec_command="$current_path/minio server $MINIO_OPTS $MINIO_VOLUMES"

function start() {
  [ -w "$PID_DIR" ] ||  mkdir -p "$PID_DIR"

  if [ -f $pid ]; then
    if kill -0 `cat $pid` > /dev/null 2>&1; then
      echo minio running as process `cat $pid`.  Stop it first.
      exit 1
    fi
  fi

  echo starting minio, logging to $log

  echo "nohup $exec_command > $log 2>&1 &"
  nohup $exec_command > $log 2>&1 &
  echo $! > $pid

}

function stop() {

  if [ -f $pid ]; then
    TARGET_PID=`cat $pid`
    if kill -0 $TARGET_PID > /dev/null 2>&1; then
      echo stopping minio
      kill $TARGET_PID
      sleep $STOP_TIMEOUT
      if kill -0 $TARGET_PID > /dev/null 2>&1; then
        echo "minio did not stop gracefully after $STOP_TIMEOUT seconds: killing with kill -9"
        kill -9 $TARGET_PID
      fi
    else
      echo no minio to stop
    fi
    rm -f $pid
  else
    echo no minio to stop
  fi

}

function status() {
  if [ -f $pid ]; then
    TARGET_PID=`cat $pid`
    echo "pid is $TARGET_PID"
  else
    echo "minio  pid file is not exists"
  fi
  kill -0 $TARGET_PID
  if [ $? -eq 0 ]
  then
    echo "minio is  running "
  else
    echo "minio  is not running"
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

echo "End $startStop minio."
