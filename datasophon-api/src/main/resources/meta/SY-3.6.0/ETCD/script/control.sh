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

log=$LOG_DIR/etcd.out
pid=$PID_DIR/etcd.pid

exec_command="$current_path/etcd --config-file=$current_path/conf.yml"

function start() {
  [ -w "$PID_DIR" ] ||  mkdir -p "$PID_DIR"

  if [ -f $pid ]; then
    if kill -0 `cat $pid` > /dev/null 2>&1; then
      echo etcd running as process `cat $pid`.  Stop it first.
      exit 1
    fi
  fi

  echo starting etcd, logging to $log

  echo "nohup $exec_command > $log 2>&1 &"
  nohup $exec_command > $log 2>&1 &
  echo $! > $pid

}

function stop() {

  if [ -f $pid ]; then
    TARGET_PID=`cat $pid`
    if kill -0 $TARGET_PID > /dev/null 2>&1; then
      echo stopping etcd
      kill $TARGET_PID
      sleep $STOP_TIMEOUT
      if kill -0 $TARGET_PID > /dev/null 2>&1; then
        echo "etcd did not stop gracefully after $STOP_TIMEOUT seconds: killing with kill -9"
        kill -9 $TARGET_PID
      fi
    else
      echo no etcd to stop
    fi
    rm -f $pid
  else
    echo no etcd to stop
  fi

}

function status() {
  if [ -f $pid ]; then
    ARGET_PID=`cat $pid`
    echo "pid is $ARGET_PID"
  else
    echo "etcd  pid file is not exists"
  fi
  code=$($current_path/etcdctl endpoint health | grep -c "healthy: successfully")
  if [ $code -eq 1 ]; then
      echo "etcd is OK"
  else
      echo "etcd  is not ready"
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

echo "End $startStop etcd."
