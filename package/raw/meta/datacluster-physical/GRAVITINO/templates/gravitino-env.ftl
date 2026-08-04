#!/usr/bin/env bash
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# datasophon 生成：整体覆盖官方 conf/gravitino-env.sh，取代其中被注释掉的 export 项。
# Gravitino 1.3.1-SNAPSHOT 强制要求 Java 17+（bin/common.sh 的 check_java_version 校验，
# 低于 17 直接 exit 1），节点默认 JAVA_HOME 通常仍指向 JDK8，必须在此显式覆盖。

export JAVA_HOME=${javaHome}
export GRAVITINO_MEM="${gravitinoMem}"

# bin/common.sh 靠这个变量判断"当前是不是一个正确解压的发行包"，未设置直接 exit 1
# （本地源码构建的 conf/gravitino-env.sh 版本由 Gradle 写入，
# 整体覆盖模板时必须显式保留，否则 start/status/stop 全部启动失败）。
export GRAVITINO_VERSION=1.3.1-SNAPSHOT

# OTel Java Agent：指标走 Prometheus scrape（见 OtelScrapeConfigBuilder.PATH_OVERRIDES
# 里的 GravitinoServer -> /prometheus/metrics），此处只上报 trace，metrics/logs 关闭，
# 避免与 scrape 链路重复采集。
# 注意：javaagent 路径不能用 $(pwd) —— Datasophon 的巡检（每 30s 状态检查）调用
# gravitino.sh 时不会先 cd 进安装目录（只有 start/stop 这类正式任务命令才会），此时
# pwd 落在上级目录，导致 javaagent jar 定位失败、java -version 探测崩溃，进而让
# check_java_version 判空出错，status 恒返回失败（真实复现过的 bug）。
# GRAVITINO_HOME 是 bin/common.sh 用 cd+pwd 稳定算出的绝对路径，不受调用方 cwd 影响。
export JAVA_TOOL_OPTIONS="-javaagent:${r"${GRAVITINO_HOME}"}/otel/opentelemetry-javaagent.jar -Dotel.service.name=gravitino -Dotel.exporter.otlp.endpoint=http://localhost:4317 -Dotel.exporter.otlp.protocol=grpc -Dotel.traces.exporter=otlp -Dotel.metrics.exporter=none -Dotel.logs.exporter=none"
