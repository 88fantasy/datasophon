<#noparse>
log: 
  level: info
  max-file-size: 100MB
  max-history: 15
  path: .
  loki:
    url: ${log.loki.baseUrl}/loki/api/v1/push
    base-url: ${loki.url}
logging:
  config: classpath:logback-${spring.application.name}.xml
</#noparse>  