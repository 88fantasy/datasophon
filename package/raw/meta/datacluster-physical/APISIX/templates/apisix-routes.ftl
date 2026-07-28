upstreams:
  - id: 1
    type: roundrobin
    nodes:
      '${apisixUpstreamHost?replace("'", "''")}:${apisixUpstreamPort?is_number?then(apisixUpstreamPort?c, apisixUpstreamPort)}': 1

routes:
  - id: 1
    uri: '${apisixRouteUri?replace("'", "''")}'
    upstream_id: 1

global_rules:
  - id: 1
    plugins:
      prometheus:
        prefer_name: true
  - id: 2
    plugins:
      opentelemetry:
        sampler:
          name: always_on

# opentelemetry 插件运行时读取的是 plugin_metadata，不是 config.yaml 的 plugin_attr——
# 缺失时插件会静默跳过（access.log 报 "plugin_metadata is required"），不生成任何 span。
plugin_metadata:
  - id: opentelemetry
    resource:
      service.name: apisix
    collector:
      address: 127.0.0.1:4318
      request_timeout: 3
    batch_span_processor:
      drop_on_queue_full: false
      max_queue_size: 1024
      batch_timeout: 2
#END
