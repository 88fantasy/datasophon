#!/bin/sh

usage="Usage: start.sh (start|stop|restart|status) "

# if no args specified, show usage
if [ $# -le 0 ]; then
  echo $usage
  exit 1
fi

BIN_PATH=$(cd `dirname $0`; pwd)
cd `dirname $0`
cd ..
current_path=`pwd`
startStop=$1
shift

echo "Begin $startStop ......"


export LOG_DIR=$current_path/logs

if [ ! -d "$LOG_DIR" ]; then
  mkdir $LOG_DIR
fi


function start() {

  echo "start juicefs"

<#list juicefsMounts as item>
  $current_path/juicefs mount "${juicefsMeta}" ${item.path} -d --log=$LOG_DIR/${item.log} --metrics=0.0.0.0:9567
</#list>

}

function stop() {
  echo "stop juicefs"
<#list juicefsMounts as item>
  $current_path/juicefs umount ${item.path} -f
</#list>

}

function status() {
<#list juicefsMounts as item>
  if $current_path/juicefs info ${item.path} 2>&1 | grep -q "ERROR"; then
</#list>
      echo "juicefs  is not ready"
      exit 1
  else
      echo "juicefs is OK"
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

echo "End $startStop juicefs."




