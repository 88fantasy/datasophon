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
            - targets: ['127.0.0.1:8888']
              labels:
                host: ${ip}

processors:
  memory_limiter:
    check_interval: 5s
    limit_mib: ${memLimitMiB}
  batch:
    send_batch_size: ${batchSize}
    timeout: 5s

exporters:
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

service:
  extensions: [file_storage/queue]
  telemetry:
    metrics:
      readers:
        - pull:
            exporter:
              prometheus:
                host: '0.0.0.0'
                port: 8888
  pipelines:
    metrics:
      receivers: [otlp, prometheus/self]
      processors: [memory_limiter, batch]
      exporters: [awss3]
    logs:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [awss3]
    traces:
      receivers: [otlp]
      processors: [memory_limiter, batch]
      exporters: [awss3]
