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

# APISIX 由离线 RPM 安装包安装，随包自带 systemd unit
# （/usr/lib/systemd/system/apisix.service），生命周期管理直接委托给 systemctl，
# 不再自行探活 pid 文件——systemctl 的退出码本身就是准确的事实依据。

usage="Usage: control.sh (start|stop|restart|status)"

if [ $# -le 0 ]; then
  echo "$usage"
  exit 1
fi

case "$1" in
  (start)
    systemctl start apisix
    ;;
  (stop)
    systemctl stop apisix
    ;;
  (restart)
    systemctl restart apisix
    ;;
  (status)
    systemctl is-active --quiet apisix
    ;;
  (*)
    echo "$usage"
    exit 1
    ;;
esac
