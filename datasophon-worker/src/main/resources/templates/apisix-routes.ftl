upstreams:
  - id: 1
    type: roundrobin
    nodes:
      '${apisixUpstreamHost?replace("'", "''")}:${apisixUpstreamPort}': 1

routes:
  - id: 1
    uri: '${apisixRouteUri?replace("'", "''")}'
    upstream_id: 1

global_rules:
  - id: 1
    plugins:
      prometheus:
        prefer_name: true
#END
