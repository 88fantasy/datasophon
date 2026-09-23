<#-- 占位符未解析（平台未配置 Rustfs）时直接失败，避免把字面量 ${...} 写进配置 -->
<#if s3Endpoint?contains(r"${")><#stop "s3Endpoint is unresolved: ${s3Endpoint}"></#if>
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
seatunnel:
  engine:
    classloader-cache-mode: true
    history-job-expire-minutes: ${historyJobExpireMinutes}
    backup-count: 1
    queue-type: blockingqueue
    print-execution-info-interval: 60
    print-job-metrics-info-interval: 60
    slot-service:
      dynamic-slot: true
    checkpoint:
      interval: ${checkpointInterval}
      timeout: ${checkpointTimeout}
      storage:
        type: hdfs
        max-retained: 3
        plugin-config:
          namespace: /seatunnel/checkpoint_snapshot
          storage.type: s3
          s3.bucket: s3a://${stateBucket}
          fs.s3a.endpoint: ${s3Endpoint}
          fs.s3a.access.key: ${s3AccessKey}
          fs.s3a.secret.key: ${s3SecretKey}
          fs.s3a.aws.credentials.provider: org.apache.hadoop.fs.s3a.SimpleAWSCredentialsProvider
          fs.s3a.path.style.access: true
          fs.s3a.connection.ssl.enabled: false
    telemetry:
      metric:
        enabled: true
      logs:
        scheduled-deletion-enable: true
    http:
      enable-http: true
      port: <#if workerHttpPort??>${workerHttpPort}<#else>${masterHttpPort}</#if>
      enable-dynamic-port: false
    <#if lineageEnabled?string == "true">
    <#-- DDL 不硬依赖 GRAVITINO：未安装时默认值里的占位符解析不了，按未配置处理 -->
    <#if (lineageUrl!"")?trim?has_content && !lineageUrl?contains(r"${")>
    openlineage:
      enabled: ${lineageEnabled?string}
      url: ${lineageUrl}
      transport: http
      <#-- 空值会渲染成 YAML null，SeaTunnel 启动即 Fatal（InvalidConfigurationException）；未配置 token 时整行省略 -->
      <#if (lineageToken!"")?has_content>
      auth_token: ${lineageToken}
      </#if>
      namespace: ${clusterName}
      job_name_per_output: true
    <#else>
    <#stop "lineageUrl is required when lineageEnabled is true (install GRAVITINO or set lineageUrl, or disable lineage)">
    </#if>
    </#if>
