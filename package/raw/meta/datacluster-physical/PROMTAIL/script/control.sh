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


if [ ! -d "$LOG_DIR" ]; then
  mkdir $LOG_DIR
fi

log=$LOG_DIR/promtail.out
pid=$PID_DIR/promtail.pid

function start() {
  [ -w "$PID_DIR" ] ||  mkdir -p "$PID_DIR"

  if [ -f $pid ]; then
    if kill -0 `cat $pid` > /dev/null 2>&1; then
      echo promtail running as process `cat $pid`.  Stop it first.
      exit 1
    fi
  fi

  echo starting promtail, logging to $log

  nohup $current_path/promtail-linux-amd64 -config.file=$current_path/config/config.yaml -inspect > $log 2>&1 &
  echo $! > $pid

}

function stop() {

  if [ -f $pid ]; then
    TARGET_PID=`cat $pid`
    if kill -0 $TARGET_PID > /dev/null 2>&1; then
      echo stopping promtail
      kill $TARGET_PID
      sleep $STOP_TIMEOUT
      if kill -0 $TARGET_PID > /dev/null 2>&1; then
        echo "promtail did not stop gracefully after $STOP_TIMEOUT seconds: killing with kill -9"
        kill -9 $TARGET_PID
      fi
    else
      echo no promtail to stop
    fi
    rm -f $pid
  else
    echo no promtail to stop
  fi

}

function status() {
  if [ -f $pid ]; then
    ARGET_PID=`cat $pid`
    kill -0 $ARGET_PID
    if [ $? -eq 0 ]
    then
      echo "promtail is  running "
    else
      echo "promtail  is not running"
      exit 1
    fi
  else
    echo "promtail  pid file is not exists"
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

echo "End $startStop promtail."
