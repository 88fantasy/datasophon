#!/bin/sh
usage="Usage: control.sh (start|stop|restart|status)"
if [ $# -le 0 ]; then echo $usage; exit 1; fi

current_path=$(cd `dirname $0`;pwd)
startStop=$1; shift
echo "Begin $startStop ......"

export PID_DIR=$current_path/pid
export LOG_DIR=$current_path/logs
export STOP_TIMEOUT=10
[ -d "$LOG_DIR" ] || mkdir -p "$LOG_DIR"

log=$LOG_DIR/otelcol.out
pid=$PID_DIR/otelcol.pid
bin=$current_path/otelcol-contrib
conf=$current_path/config/otelcol.yaml

start() {
  [ -w "$PID_DIR" ] || mkdir -p "$PID_DIR"
  if [ -f $pid ] && kill -0 `cat $pid` >/dev/null 2>&1; then
    echo "otelcol running as `cat $pid`. Stop it first."; exit 1
  fi
  env_file=$current_path/config/otelcol.env
  if [ -f "$env_file" ]; then
    while IFS= read -r line || [ -n "$line" ]; do
      case "$line" in
        ''|'#'*)
          ;;
        *=*)
          key=${line%%=*}
          value=${line#*=}
          case "$key" in
            ''|[0-9]*|*[!A-Za-z0-9_]*)
              echo "invalid environment key in $env_file: $key"; exit 1
              ;;
            *)
              export "$key=$value"
              ;;
          esac
          ;;
        *)
          echo "invalid environment entry in $env_file"; exit 1
          ;;
      esac
    done < "$env_file"
  fi
  echo "starting otelcol, logging to $log"
  nohup $bin --config=$conf > $log 2>&1 &
  echo $! > $pid
}

stop() {
  if [ -f $pid ]; then
    TARGET_PID=`cat $pid`
    if kill -0 $TARGET_PID >/dev/null 2>&1; then
      echo "stopping otelcol"; kill $TARGET_PID; sleep $STOP_TIMEOUT
      kill -0 $TARGET_PID >/dev/null 2>&1 && { echo "force kill"; kill -9 $TARGET_PID; }
    else echo "no otelcol to stop"; fi
    rm -f $pid
  else echo "no otelcol to stop"; fi
}

status() {
  if [ -f $pid ] && kill -0 `cat $pid` >/dev/null 2>&1; then
    echo "otelcol is running"
  else echo "otelcol is not running"; exit 1; fi
}

case $startStop in
  (start) start ;;
  (stop) stop ;;
  (restart) stop; sleep 2s; start ;;
  (status) status ;;
  (*) echo $usage; exit 1 ;;
esac
echo "End $startStop otelcol."
