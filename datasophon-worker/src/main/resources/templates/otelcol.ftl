<#if (rawYaml!"")?has_content>${rawYaml}<#else>
extensions:
  file_storage/queue:
    directory: ${queueStorageDir}
    create_directory: true
    timeout: 10s

receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318
  prometheus/self:
    config:
      scrape_configs:
        - job_name: otelcol-self
          scrape_interval: 30s
          static_configs:
            - targets: ['127.0.0.1:${otelSelfMetricsPort}']
              labels:
                host: ${ip}
<#if (localScrapeJobsYaml!"")?has_content>
  prometheus/local:
    config:
      scrape_configs:
${localScrapeJobsYaml}
</#if>
  host_metrics:
    collection_interval: 15s
    scrapers:
      cpu: {}
      load: {}
      memory:
        metrics:
          system.linux.memory.available:
            enabled: true
      disk: {}
      filesystem: {}
      network: {}

processors:
  memory_limiter:
    check_interval: 5s
    limit_mib: ${memLimitMiB}
  # Prometheus Summary 类型指标从未被观测到时 quantile 恒为 NaN（count=0），下游 exporter 序列化
  # NaN 到 JSON 会整批失败并触发 retry 风暴，拖累同一 pipeline 里其它正常指标的导出（沙箱实测确认，
  # 见 docs/monitoring/zookeeper-otel-verification.md）。丢弃这类空 Summary 数据点不影响其余指标；
  # 注意：Summary 若历史上被观测过(count>0)之后又因滑动窗口衰减出 NaN 的情况，本处理器无法覆盖
  # (OTTL 当前版本不支持读写 quantile_values 这一 slice-of-struct 字段)。
  filter/drop_empty_summary:
    metrics:
      datapoint:
        - 'metric.type == METRIC_DATA_TYPE_SUMMARY and count == 0'
  # ZooKeeper 的 election_time/fsynctime/snapshottime/jvm_pause_time_ms 即使 count>0 也会因滑动
  # 时间窗衰减回 NaN(上面 filter/drop_empty_summary 覆盖不到),且当前 OTTL 版本无法读写
  # quantile_values 字段清空 NaN。直接整体丢弃这 4 个指标,不导入 Doris，避免拖累同一 pipeline
  # 里其它指标的导出(详见 docs/monitoring/zookeeper-otel-verification.md)。代价:ZooKeeper 看板
  # Z16/Z19/Z20/Z23 面板将永远无数据。
  filter/drop_zk_decaying_summary:
    metrics:
      metric:
        - 'name == "election_time" or name == "fsynctime" or name == "snapshottime" or name == "jvm_pause_time_ms"'
  batch:
    send_batch_size: ${batchSize}
    timeout: 5s
  resource/host_metrics:
    attributes:
      - key: service.name
        value: node
        action: upsert
      - key: service.instance.id
        value: ${nodeHostname}
        action: upsert

exporters:
<#if (exporterMode!"s3") == "doris">
  doris:
    endpoint: ${dorisEndpoint}
    database: ${dorisDatabase}
    username: ${dorisUser}
    password: <#noparse>${env:OTEL_DORIS_PASSWORD}</#noparse>
    create_schema: false
    sending_queue:
      enabled: true
      storage: file_storage/queue
    retry_on_failure:
      enabled: true
      initial_interval: 5s
      max_interval: 30s
      max_elapsed_time: 300s
<#else>
  awss3:
    s3uploader:
      region: ${s3Region}
      s3_bucket: ${s3Bucket}
      s3_prefix: ${s3Prefix}
      endpoint: ${s3Endpoint}
      s3_force_path_style: true
    marshaler: otlp_json
    sending_queue:
      enabled: true
      storage: file_storage/queue
    retry_on_failure:
      enabled: true
      initial_interval: 5s
      max_interval: 30s
      max_elapsed_time: 300s
</#if>

service:
  extensions: [file_storage/queue]
  telemetry:
    metrics:
      readers:
        - pull:
            exporter:
              prometheus:
                host: '0.0.0.0'
                port: ${otelSelfMetricsPort}
  pipelines:
    metrics:
      receivers: [otlp, prometheus/self<#if (localScrapeJobsYaml!"")?has_content>, prometheus/local</#if>]
      processors: [memory_limiter, filter/drop_empty_summary, filter/drop_zk_decaying_summary, batch]
      exporters: [<#if (exporterMode!"s3") == "doris">doris<#else>awss3</#if>]
    metrics/host:
      receivers: [host_metrics]
      processors: [memory_limiter, resource/host_metrics, batch]
      exporters: [<#if (exporterMode!"s3") == "doris">doris<#else>awss3</#if>]
    logs:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [<#if (exporterMode!"s3") == "doris">doris<#else>awss3</#if>]
    traces:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [<#if (exporterMode!"s3") == "doris">doris<#else>awss3</#if>]
</#if>
