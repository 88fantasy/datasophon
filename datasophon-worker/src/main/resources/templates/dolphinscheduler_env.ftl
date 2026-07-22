#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# JAVA_HOME, will use it to start DolphinScheduler server
export JAVA_HOME=$JAVA_HOME8

# Database related configuration, set database type, username and password
export DATABASE=${r"${DATABASE"}:-mysql}
export SPRING_PROFILES_ACTIVE=${r"${DATABASE}"}
export SPRING_DATASOURCE_URL="${databaseUrl}"
export SPRING_DATASOURCE_USERNAME=${username}
export SPRING_DATASOURCE_PASSWORD=${password}

# DolphinScheduler server related configuration
export SPRING_CACHE_TYPE=${r"${SPRING_CACHE_TYPE:-none}"}
export SPRING_JACKSON_TIME_ZONE=${r"${SPRING_JACKSON_TIME_ZONE:-UTC}"}
export MASTER_FETCH_COMMAND_NUM=${r"${MASTER_FETCH_COMMAND_NUM:-10}"}

# Registry center configuration：使用 MySQL(与 DS 元数据库同一实例)作为注册中心，
# 不再依赖独立 ZooKeeper 集群（dolphinscheduler-registry-jdbc，DS 3.2+ 内置支持）。
export REGISTRY_TYPE=${r"${REGISTRY_TYPE"}:-jdbc}
export REGISTRY_HIKARI_CONFIG_JDBC_URL=${r"${REGISTRY_HIKARI_CONFIG_JDBC_URL"}:-${databaseUrl}}
export REGISTRY_HIKARI_CONFIG_USERNAME=${r"${REGISTRY_HIKARI_CONFIG_USERNAME"}:-${username}}
export REGISTRY_HIKARI_CONFIG_PASSWORD=${r"${REGISTRY_HIKARI_CONFIG_PASSWORD"}:-${password}}
export REGISTRY_HIKARI_CONFIG_DRIVER_CLASS_NAME=${r"${REGISTRY_HIKARI_CONFIG_DRIVER_CLASS_NAME"}:-com.mysql.cj.jdbc.Driver}

# Tasks related configurations, need to change the configuration if you use the related tasks.
export HADOOP_HOME=${r"${HADOOP_HOME"}:-/data/install_datasophon/hadoop}
export HADOOP_CONF_DIR=${r"${HADOOP_CONF_DIR"}:-/data/install_datasophon/hadoop/etc/hadoop}
export SPARK_HOME1=${r"${SPARK_HOME1"}:-/opt/soft/spark1}
export SPARK_HOME2=${r"${SPARK_HOME2"}:-/opt/soft/spark2}
export PYTHON_HOME=${r"${PYTHON_HOME"}:-/opt/soft/python}
export HIVE_HOME=${r"${HIVE_HOME"}:-/data/install_datasophon/hive}
export FLINK_HOME=${r"${FLINK_HOME"}:-/data/install_datasophon/flink}
export DATAX_HOME=${r"${DATAX_HOME"}:-/opt/soft/datax}
export SEATUNNEL_HOME=${r"${SEATUNNEL_HOME"}:-/opt/soft/seatunnel}
export CHUNJUN_HOME=${r"${CHUNJUN_HOME"}:-/opt/soft/chunjun}

export PATH=$HADOOP_HOME/bin:$SPARK_HOME1/bin:$SPARK_HOME2/bin:$PYTHON_HOME/bin:$JAVA_HOME/bin:$HIVE_HOME/bin:$FLINK_HOME/bin:$DATAX_HOME/bin:$SEATUNNEL_HOME/bin:$CHUNJUN_HOME/bin:$PATH

# 按角色覆盖 server.port（Spring Boot 环境变量优先级高于内置 application.yaml，
# 与上面 SPRING_DATASOURCE_* 是同一套注入机制）、OTel service.name。四个角色共用
# 同一份渲染结果（configWriter 只生成一份 bin/env/dolphinscheduler_env.sh，由
# dolphinscheduler-daemon.sh 复制覆盖各角色 conf/ 下同名文件），无法在渲染期区分
# 角色，只能靠运行时判断。$command 不可靠——它是 dolphinscheduler-daemon.sh 的本地
# shell 变量，daemon.sh 用 nohup bash 派生新进程执行各角色 bin/start.sh，新进程不
# 继承旧进程的本地变量，source 本文件时 $command 恒为空。改用各角色 start.sh 在
# source 前各自设置、互不重名的 *_HOME 变量（API_HOME/MASTER_HOME/WORKER_HOME/
# ALERT_HOME）判断，这是运行时真正可靠的信号。
if [ -n "${r"$API_HOME"}" ]; then
  export DS_ROLE=api-server
  export SERVER_PORT=${apiServerPort}
elif [ -n "${r"$MASTER_HOME"}" ]; then
  export DS_ROLE=master-server
  export SERVER_PORT=${masterServerPort}
elif [ -n "${r"$WORKER_HOME"}" ]; then
  export DS_ROLE=worker-server
  export SERVER_PORT=${workerServerPort}
elif [ -n "${r"$ALERT_HOME"}" ]; then
  export DS_ROLE=alert-server
  export SERVER_PORT=${alertServerPort}
fi

# OTel Java Agent 自动埋点（中间件链路追踪接入，Phase F）：只开 traces，metrics 已由
# Prometheus 抓取管道覆盖（避免重复计数）。agent jar 复用 datasophon-worker 自带的
# otel/opentelemetry-javaagent.jar（同目录 service_ddl.json 的 link hook 在安装期软链
# 到 DS 安装根目录），依赖 dolphinscheduler-daemon.sh 在 source 本文件时已导出
# $DOLPHINSCHEDULER_HOME；service.name 复用上面 $DS_ROLE。
export OTEL_JAVAAGENT_ENABLED="${r"${OTEL_JAVAAGENT_ENABLED:-true}"}"
if [ "${r"$OTEL_JAVAAGENT_ENABLED"}" = "true" ]; then
  export JAVA_TOOL_OPTIONS="-javaagent:$DOLPHINSCHEDULER_HOME/otel/opentelemetry-javaagent.jar -Dotel.service.name=dolphinscheduler-${r"$DS_ROLE"} -Dotel.exporter.otlp.endpoint=http://localhost:4317 -Dotel.exporter.otlp.protocol=grpc -Dotel.traces.exporter=otlp -Dotel.metrics.exporter=none -Dotel.logs.exporter=none"
fi
