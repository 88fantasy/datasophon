deployment:
  role: data_plane
  role_data_plane:
    config_provider: yaml

apisix:
  node_listen: ${apisixPort}
  enable_admin: false

plugin_attr:
  prometheus:
    export_addr:
      ip: ${apisixPrometheusAddr?json_string}
      port: ${apisixPrometheusPort}
    enable_export_server: true
