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

#export PID_DIR=$current_path/pid
export LOG_DIR=$current_path/logs
#export STOP_TIMEOUT=10

if [ ! -d "$LOG_DIR" ]; then
  mkdir $LOG_DIR
fi


function start() {

  echo "starting juicefs"

<#list juicefsMounts as item>
$current_path/juicefs mount ${juicefsMeta} ${item.path} -d --log=$LOG_DIR/${item.log}
</#list>

  echo "nohup $exec_command > $log 2>&1 &"
  nohup $exec_command > $log 2>&1 &
  echo $! > $pid

}

function stop() {

<#list juicefsMounts as item>
$current_path/juicefs umount ${item.path} -f
</#list>

}

function status() {
<#list juicefsMounts as item>
$current_path/juicefs info ${item.path}
</#list>
#  if [ $code -eq 1 ]; then
#      echo "juicefs is OK"
#  else
#      echo "juicefs  is not ready"
#      exit 1
#  fi
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

echo "End $startStop juicefs."




