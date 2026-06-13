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

usage="Usage: start.sh (start|stop|restart) <command> "

# if no args specified, show usage
if [ $# -le 0 ]; then
  echo $usage
  exit 1
fi
startStop=$1
shift
SH_DIR=`dirname $0`

curdir=/usr/local/apisix
pid=/usr/local/apisix/logs/nginx.pid

status(){
  if [ -f $pid ]; then
    ARGET_PID=`cat $pid`
    echo "pid is $ARGET_PID"
    kill -0 $ARGET_PID
    if [ $? -eq 0 ]
    then
      # 发送GET请求到指定的URL
      port=$(grep -e 'node_listen:' $curdir/conf/config.yaml | awk '{print $2}')
      status=$(curl -I -m 10 -o /dev/null -s -w %{http_code} http://127.0.0.1:$port/)
      # 检查返回值是否为200||404
      if [ $status == "200" -o $status == "404" ]; then
          echo "http request success, return value is $status"
          echo "Apisix is OK"
      else
          echo "http request failed, return value is：$status"
          echo "Apisix  is not ready"
          exit 1
      fi
      echo "Apisix is  running "
    else
      echo "Apisix  is not running"
      exit 1
    fi
  else
    echo "Apisix pid file is not exists"
    exit 1
	fi
}
case $startStop in
  (status)
	  status
	;;
  (*)
    echo $usage
    exit 1
    ;;
esac


echo "End $startStop ."
