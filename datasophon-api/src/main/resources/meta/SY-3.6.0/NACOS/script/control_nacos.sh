#!/bin/bash
#
#  Licensed to the Apache Software Foundation (ASF) under one or more
#  contributor license agreements.  See the NOTICE file distributed with
#  this work for additional information regarding copyright ownership.
#  The ASF licenses this file to You under the Apache License, Version 2.0
#  (the "License"); you may not use this file except in compliance with
#  the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#

usage="Usage: control_nacos.sh (start|stop|restart|status) <command>"

# if no args specified, show usage
if [ $# -le 1 ]; then
  echo $usage
  exit 1
fi
startStop=$1
shift
command=$1
NACOS_DIR=$(cd `dirname $0`;pwd)
export LOG_DIR=$NACOS_DIR/logs
export PID_DIR=$NACOS_DIR/pid

export HOSTNAME=`hostname`

pid=$PID_DIR/$command.pid
log=$LOG_DIR/$startStop-$command.out

if [ ! -d "$LOG_DIR" ]; then
  mkdir $LOG_DIR
fi

start(){
	[ -w "$PID_DIR" ] ||  mkdir -p "$PID_DIR"
  if [ -f $pid ]; then
    if kill -0 `cat $pid` > /dev/null 2>&1; then
      echo $command running as process `cat $pid`.  Stop it first.
      exit 1
    fi
  fi
  echo starting nacos $command, logging to $log
  exec_command="$NACOS_DIR/bin/startup.sh -m $command"
  nohup $exec_command > $log 2>&1 &
  echo $! > $pid
}
stop(){
	if [ -f $pid ]; then
    exec_shutdown_command="$NACOS_DIR/bin/shutdown.sh"
    $exec_shutdown_command
    rm -f $pid
  else
    echo no nacos $command to stop
  fi
}
restart(){
	stop
	sleep 10
	start
}
status(){
  if [ -f $pid ]; then
    ARGET_PID=`cat $pid`
    kill -0 $ARGET_PID
    if [ $? -eq 0 ]
    then
      echo "Nacos $command is  running "
    else
      echo "Nacos $command  is not running"
      exit 1
    fi
  else
    echo "Nacos $command  pid file is not exists"
    exit 1
	fi
}



case $startStop in
  (start)
    start;;
  (stop)
    stop;;
  (status)
	  status;;
  (restart)
	  restart;;
  (*)
    echo $usage
    exit 1
    ;;
esac


echo "End $startStop $command."