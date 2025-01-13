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
export LOG_DIR=$current_path/log
export STOP_TIMEOUT=3

log=$LOG_DIR/history-start.log
pid=$PID_DIR/flink-hive-historyserver.pid

exec_command_start="$current_path/bin/historyserver.sh start"
exec_command_stop="$current_path/bin/historyserver.sh stop"

function start() {

  [ -w "$PID_DIR" ] ||  mkdir -p "$PID_DIR"

  if [ -f $pid ]; then
    if kill -0 `tail -n 1 $pid` > /dev/null 2>&1; then
      echo history running as process `cat $pid`.  Stop it first.
      exit 1
    fi
  fi

  echo starting history

  echo "nohup $exec_command_start > $log 2>&1 &"
  nohup $exec_command_start > $log 2>&1 &
  echo $! > $pid

}

function stop() {

  if [ -f $pid ]; then
    TARGET_PID=`tail -n 1 $pid`
    if kill -0 $TARGET_PID > /dev/null 2>&1; then
      echo stopping
      echo "nohup $exec_command_stop > $log 2>&1 &"
      nohup $exec_command_stop > $log 2>&1 &
      sleep $STOP_TIMEOUT
      if kill -0 $TARGET_PID > /dev/null 2>&1; then
        echo "history did not stop gracefully after $STOP_TIMEOUT seconds: killing with kill -9"
        kill -9 $TARGET_PID
      fi
    else
      echo no history to stop
    fi
    rm -f $pid
  else
    echo no history to stop
  fi

}

function status() {
  if [ -f $pid ]; then
      TARGET_PID=`tail -n 1 $pid`
      kill -0 $TARGET_PID
      if [ $? -eq 0 ]
      then
        echo "history is  running"
      else
        echo "history  is not running"
        exit 1
      fi
    else
      echo "history pid file[$pid] is not exists"
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

echo "End $startStop history."
