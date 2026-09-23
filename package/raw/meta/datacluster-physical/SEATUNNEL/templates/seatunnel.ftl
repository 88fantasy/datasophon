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
    <#if (lineageUrl!"")?trim?has_content>
    openlineage:
      enabled: ${lineageEnabled?string}
      url: ${lineageUrl}
      transport: http
      auth_token: ${lineageToken}
      namespace: ${clusterName}
      job_name_per_output: true
    <#else>
    <#stop "lineageUrl is required when lineageEnabled is true">
    </#if>
    </#if>
