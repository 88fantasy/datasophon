auth_enabled: false

server:
  http_listen_port: ${lokiPort}

storage_config:
  aws:
    s3: ${lokiS3Addr}
    s3forcepathstyle: true
  tsdb_shipper:
    active_index_directory: /loki/index
    cache_location: /loki/index_cache
    resync_interval: 5s

common:
  path_prefix: /data/loki
  instance_addr: ${ip}
  replication_factor: 1
  ring:
    kvstore:
      store: inmemory

schema_config:
  configs:
    - from: 2020-05-15
      store: tsdb
      object_store: s3
      schema: v13
      index:
        prefix: index_
        period: 24h
      chunks:
        prefix: chunks_
        period: 24h

frontend:
  compress_responses: true

query_range:
  results_cache:
    cache:
      redis:
        endpoint: ${lokiRedisAddr}
        expiration: 10s
        password: ${lokiRedisPwd}
        db: ${lokiRedisDb}
  cache_results: true

chunk_store_config:
  chunk_cache_config:
    redis:
      endpoint: ${lokiRedisAddr}
      expiration: 10s
      password: ${lokiRedisPwd}
      db: ${lokiRedisDb}
  write_dedupe_cache_config:
    redis:
      endpoint: ${lokiRedisAddr}
      expiration: 1h
      password: ${lokiRedisPwd}
      db: ${lokiRedisDb}

limits_config:
  ingestion_rate_strategy: local
  ingestion_rate_mb: 32
  ingestion_burst_size_mb: 64
  max_global_streams_per_user: 0
