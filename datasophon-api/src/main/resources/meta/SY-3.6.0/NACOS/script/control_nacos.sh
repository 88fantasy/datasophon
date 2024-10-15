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

usage="Usage: control_nacos.sh (start|stop|restart) <command>"

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
log=$LOG_DIR/$command.out

if [ ! -d "$LOG_DIR" ]; then
  mkdir $LOG_DIR
fi

start(){
  exec_command="$NACOS_DIR/bin/startup.sh -m $command"
  echo starting nacos $command, logging to $log
  nohup $exec_command > $log 2>&1 &
  PID=`ps -ef | grep '[n]acos.nacos' | awk '{print $2}'`
  echo "nacos is starting..... pid is ${PID} "
}
stop(){
  PID=`ps -ef | grep '[n]acos.nacos' | awk '{print $2}'`
  echo stop nacos $command $PID
	exec_shutdown_command="$NACOS_DIR/bin/shutdown.sh"
  $exec_shutdown_command
}
restart(){
	stop
	sleep 10
	start
}
status(){
  echo "start check $command status"
  pid=`ps -ef | grep '[n]acos.nacos' | awk '{print $2}'`
  echo "pid is : $pid"
  kill -0 $pid
  if [ $? -eq 0 ]
  	then
  		echo "nacos $command is  running "
  	else
  		echo "nacos $command  is not running"
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