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
      processors: [memory_limiter, batch]
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
