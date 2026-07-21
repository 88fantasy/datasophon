deployment:
  role: data_plane
  role_data_plane:
    config_provider: yaml

apisix:
  node_listen: ${apisixPort}
  enable_admin: false

# 中间件链路追踪接入（Phase F）：APISIX 官方默认插件清单（对齐 apisix/cli/config.lua 的 plugins 数组，
# server-info 在源码里已被注释禁用，故不列入）+ opentelemetry。config.yaml 顶层声明 plugins 会整体覆盖
# 默认清单而非追加，因此必须显式列出全部默认项，否则会静默丢失未列出的插件。
plugins:
  - real-ip
  - ai
  - client-control
  - proxy-buffering
  - proxy-control
  - request-id
  - zipkin
  - ext-plugin-pre-req
  - fault-injection
  - mocking
  - serverless-pre-function
  - cors
  - ip-restriction
  - ua-restriction
  - referer-restriction
  - csrf
  - uri-blocker
  - request-validation
  - chaitin-waf
  - multi-auth
  - openid-connect
  - saml-auth
  - cas-auth
  - authz-casbin
  - authz-casdoor
  - wolf-rbac
  - ldap-auth
  - hmac-auth
  - basic-auth
  - jwt-auth
  - jwe-decrypt
  - key-auth
  - dingtalk-auth
  - feishu-auth
  - acl
  - consumer-restriction
  - attach-consumer-label
  - forward-auth
  - opa
  - authz-keycloak
  - data-mask
  - proxy-cache
  - body-transformer
  - ai-prompt-template
  - ai-prompt-decorator
  - ai-prompt-guard
  - ai-rag
  - ai-rate-limiting
  - ai-proxy-multi
  - ai-proxy
  - ai-aws-content-moderation
  - ai-aliyun-content-moderation
  - proxy-mirror
  - graphql-proxy-cache
  - proxy-rewrite
  - workflow
  - api-breaker
  - graphql-limit-count
  - limit-conn
  - limit-count
  - limit-req
  - gzip
  - traffic-label
  - traffic-split
  - redirect
  - response-rewrite
  - oas-validator
  - mcp-bridge
  - degraphql
  - kafka-proxy
  - grpc-transcode
  - grpc-web
  - http-dubbo
  - public-api
  - prometheus
  - datadog
  - lago
  - loki-logger
  - elasticsearch-logger
  - echo
  - loggly
  - http-logger
  - splunk-hec-logging
  - skywalking-logger
  - google-cloud-logging
  - sls-logger
  - tcp-logger
  - kafka-logger
  - rocketmq-logger
  - syslog
  - udp-logger
  - file-logger
  - clickhouse-logger
  - tencent-cloud-cls
  - inspect
  - example-plugin
  - aws-lambda
  - azure-functions
  - openwhisk
  - openfunction
  - serverless-post-function
  - ext-plugin-post-req
  - ext-plugin-post-resp
  - ai-request-rewrite
  - opentelemetry

plugin_attr:
  prometheus:
    export_addr:
      ip: ${apisixPrometheusAddr?json_string}
      port: ${apisixPrometheusPort}
    enable_export_server: true
