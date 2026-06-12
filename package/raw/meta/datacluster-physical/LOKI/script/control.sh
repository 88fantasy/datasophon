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

log=$LOG_DIR/loki.out
pid=$PID_DIR/loki.pid

function start() {
  [ -w "$PID_DIR" ] ||  mkdir -p "$PID_DIR"

  if [ -f $pid ]; then
    if kill -0 `cat $pid` > /dev/null 2>&1; then
      echo minio running as process `cat $pid`.  Stop it first.
      exit 1
    fi
  fi

  echo starting loki, logging to $log

  nohup $current_path/loki-linux-amd64 > $log 2>&1 &
  echo $! > $pid

}

function stop() {

  if [ -f $pid ]; then
    TARGET_PID=`cat $pid`
    if kill -0 $TARGET_PID > /dev/null 2>&1; then
      echo stopping loki
      kill $TARGET_PID
      sleep $STOP_TIMEOUT
      if kill -0 $TARGET_PID > /dev/null 2>&1; then
        echo "loki did not stop gracefully after $STOP_TIMEOUT seconds: killing with kill -9"
        kill -9 $TARGET_PID
      fi
    else
      echo no loki to stop
    fi
    rm -f $pid
  else
    echo no loki to stop
  fi

}

function status() {
  if [ -f $pid ]; then
    TARGET_PID=`cat $pid`
    echo "pid is $TARGET_PID"
  else
    echo "loki  pid file is not exists"
  fi
  kill -0 $TARGET_PID
  if [ $? -eq 0 ]
  then
    # 发送GET请求到指定的URL
    port=$(grep -e 'http_listen_port:' $current_path/config/config.yaml | awk '{print $2}')
    status=$(curl -I -m 10 -o /dev/null -s -w %{http_code} http://127.0.0.1:$port/metrics)
    # 检查返回值是否为200||404
    if [ $status == "200" ]; then
        echo "http request success, return value is $status"
        echo "loki is OK"
    else
        echo "http request failed, return value is：$status"
        echo "loki  is not ready"
        exit 1
    fi
    echo "loki is  running "
  else
    echo "loki  is not running"
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

echo "End $startStop loki."
