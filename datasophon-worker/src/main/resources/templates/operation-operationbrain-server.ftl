server:
  port: ${operationBrainServerPort}
operationbrain:
  scheduled:
    reportTask:
      enable: true

spring:
  datasource:
    dynamic:
      primary: workflow #设置默认的数据源或者数据源组,默认值即为master
      strict: false #严格匹配数据源,默认false. true未匹配到指定数据源时抛异常,false使用默认数据源
      datasource:
        workflow:
          username: ${operationDbWorkflowUsername}
          password: ${operationDbWorkflowPassword}
          driver-class-name: ${operationDbWorkflowDriverClassName}
          url: ${operationDbWorkflowUrl}
        doris:
          username: ${operationDbDorisUsername}
          password: ${operationDbDorisPassword}
          driver-class-name: ${operationDbDorisDriverClassName}
          url: ${operationDbDorisUrl}
      druid:
        connect-timeout: 6000000
        query-timeout: 6000000
        max-evictable-idle-time-millis: 6000000
        time-between-eviction-runs-millis: 6000000
        keep-alive-between-time-millis: 7000000
        initial-size: 50
        max-active: 2000
        min-idle: 50
        max-wait: 120000
  h2:
    console:
      enabled: true
  redis:
    dc-mode: ${operationRedisDcMode}
    host: ${operationRedisHost}
    port: ${operationRedisPort}
    # 关闭哨兵（v1.3.5封包）
    # sentinel: 
    #   master: mymaster
    #   nodes:
    #    - sentinel-node-1:7601
    #    - sentinel-node-2:7602
    #    - sentinel-node-3:7603
    database: ${operationRedisDatabase}
    password: ${operationRedisPassword}
    # 停用redisson（v1.3.5封包）
    redisson:
      config: |
        singleServerConfig:
          address: ${operationRedisAddress}
          password: ${operationRedisPassword}
          database: ${operationRedisDatabase}
        codec: !<org.redisson.codec.JsonJacksonCodec> {}
        transportMode: "NIO"

minio:
  minioPublicBucket: ${operationMinioPublicBucket}
  viewurl: ${operationMinioViewurl}
  outsideUrl: ${operationMinioOutsideHost}
  accessKey: ${operationMinioAccessKey}
  secretKey: ${operationMinioSecretKey}
  url: ${operationMinioHost}