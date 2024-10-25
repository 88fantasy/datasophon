minio:
  accessKey: ${bigdataMinioAccessKey}
  secretKey: ${bigdataMinioSecretKey}
  url: http://${bigdataMinioHost}:6943
  viewurl: ${bigdataOutsideAccessUrl}/mfts
  outsideUrl: ${bigdataOutsideAccessUrl}/mfts
  savePath: public/quality

## redis密码和库配置
redis:
  password: ${bigdataRedisPassword}
  database: ${bigdataRedisDatabase}
  key:
    prefix:
      authCode: "portal:authCode:"
      orderId: "portal:orderId:"
    expire:
      authCode: 90
es:
  nodes: ${bigdataEsNodes}:27920
  username: ${bigdataEsUserName}
  password: ${bigdataEsPassword}

ds:
  downloadPath: ${bigdataOutsideAccessUrl}/bigdata/bigdata

workbench:
  sso:
    redirectUrl: ${bigdataOutsideAccessUrl}/bigdata/workbench-view#/home/Overview
  msg:
    redirectUrl: ${bigdataOutsideAccessUrl}/bigdata/data-governance-view#/dispatch-center/workflow-management?activeName=Examples
  vcs:
    # 版本号
    version: ${bigdataAppVersion}
  system:
    sso:
      getTokenUrl: http://${bigdataWorkbenchSystemUrl}:19000/gw/system/auth/v1/getToken
      appkey: ${bigdataWorkbenchSystemAppkey}
      # 默认DEFAULT，平台管理跳转SYSTEM
      type: ${bigdataWorkbenchSystemType}
      # type: SYSTEM
      appId: 9999
      checkToken: http://${bigdataWorkbenchSystemUrl}:19000/gw/system/api/checkToken
      queryRootMenu: http://${bigdataWorkbenchSystemUrl}:19000/gw/system/user/v2/resource/queryRootMenu

# api-six的xApiKey
xApi:
  xApiKey: ${bigdataXApiKey}

##kafka密码
kafka:
  password: ${bigdataKafkaPassword}

########################原common.yaml##########################
## spring config
spring:
  main:
    allow-bean-definition-overriding: true
    allow-circular-references: true
  mvc:
    async:
      request-timeout: 300000
  session:
    store-type: none
  jackson:
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: GMT+8

  ## spring redis config
  redis:
    dc-mode: single
    sentinel:
      master: mymaster
      # 配置redis集群
      nodes:
        - sentinel-node-1:7601
        - sentinel-node-2:7602
        - sentinel-node-3:7603
    # 可以屏蔽其他配置只配置单一一个redis
    # host: redis-node-1
    # port: 7501
    database: ${bigdataRedisDatabase}
    password: ${bigdataRedisPassword}
    redisson:
      config: |
        singleServerConfig:
          address: "redis://redis-node-1:7501"
          password: "${bigdataRedisPassword}"
          database: 0
        codec: !<org.redisson.codec.JsonJacksonCodec> {}
        transportMode: "NIO"

  ##spring datasource config
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: ${bigdataMysqlBigdataUrl}
    username: ${bigdataMysqlBigdataUsername}
    password: ${bigdataMysqlBigdataPassword}
    hikari:
      connection-init-sql: set names utf8mb4
    initialization-mode: ALWAYS
    hikari.connection-init-sql: SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci
    hikari.minimum-idle: 25
    hikari.maximum-pool-size: 50
    hikari.max-lifetime: 28700000
    idle-timeout: 60000

  ##spring file-size config
  servlet:
    multipart:
      max-file-size: 500MB
      max-request-size: 500MB

## mybatis-plus config
mybatis-plus:
  mapper-locations:
    - classpath*:/mapper/*Mapper.xml
    - classpath*:/mapper/*/*Mapper.xml
    - classpath*:/com/gitee/sunchenbin/mybatis/actable/mapping/**/*.xml
  type-aliases-package:
    - com.chinaunicom.medical.capacity.*.model
  configuration:
    mapUnderscoreToCamelCase: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
    # log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    logic-delete-field: deleted
    logic-not-delete-value: 0
    logic-delete-value: 1
    id-type: auto
  type-handlers-package: com.chinaunicom.medical
  type-enums-package: com.chinaunicom.medical

## sso config
sso:
  accessTokenKey: accessTokenKey
  domain: domaindev
  rolesystem: rolesystem
  jwt:
    tokenExpiration: 18000000
    tokenSecret: NonceJwtSecre

## tomcat config
server:
  tomcat:
    accept-count: 1024

## swagger config
swagger.enabled: true
##swagger3
springdoc.api-docs.enabled: true

## monitor config
monitor.schduler.run: true

#ribbon conf
ribbon:
  readTimeout: 600000
feign:
  httpclient:
    enabled: true
  client:
    config:
      default:
        connectTimeOut: 30000
        readTimeOut: 180000

#springboot admin
management:
  metrics:
    export:
      prometheus:
        enabled: false
      jmx:
        enabled: false
      simple:
        enabled: false
    tags:
      application: chinaunicom-medical-mgmt-${bigdataAppName}-server
  endpoints:
    web:
      base-path: /actuator
      exposure:
        exclude: '*'
  endpoint:
    health:
      show-details: always
    shutdown:
      enabled: false

jwt:
  tokenHeader: Authorization
  secret: mall-portal-secret
  expiration: 604800
  tokenHead: bearer

info:
  author: unicom-medical
  name: chinaunicom-medical-mgmt-${bigdataAppName}-server


## forest-第三方工具
forest:
  # 转换器配置，支持 json, xml, text, binary 四种数据类型的配置
  converters:
    # JSON转换器
    json:
      # JSON转换器设置为Jackson转换器
      type: com.dtflys.forest.converter.json.ForestJacksonConverter
  backend: httpclient
  ## 日志总开关，打开/关闭Forest请求/响应日志（默认为 true）
  log-enabled: true
  ## 打开/关闭Forest请求日志（默认为 true）
  log-request: true
  ## 打开/关闭Forest响应状态日志（默认为 true）
  log-response-status: true
  ## 打开/关闭Forest响应内容日志（默认为 false）
  log-response-content: true
  timeout: 30000

## retrofit http客户端
retrofit:
  log:
    enable: true
    logging-interceptor: com.github.lianjiatech.retrofit.spring.boot.interceptor.DefaultLoggingInterceptor
    global-log-level: error
    global-log-strategy: body
  retry:
    enable-global-retry: false
  # 全局连接超时时间
  global-connect-timeout-ms: 60000
  # 全局完整调用超时时间
  global-call-timeout-ms: 60000

## 自动建表actable
actable:
  database:
    type: mysql
  index:
    prefix: idx_
  model:
    pack: com.chinaunicom.medical.capacity.dc.model.v2;com.chinaunicom.medical.capacity.datasource.entity.v2;com.chinaunicom.medical.capacity.workbench.dao.entity
  table:
    auto: update
  unique:
    prefix: uni_

log:
  level: info
  max-file-size: 100MB
  max-history: 15
  path: .
logging:
  config: classpath:logback-medical.xml
  # level:
  #   com.chinaunicom.medical.capacity.dc.mapper: DEBUG

## 关闭密评
cipher.switchButton: 1


########################原bigdata.yaml##########################
## 模块-数据质控
qc:
  executeResult: ${bigdataServerAddress}/qc/v1/qcRuleExecuteResult/internal/receive
  imgServer.url: http://localhost:9800

## 模块-元数据管理
metadata:
  blood:
    # 配置跳转到bigdata的/metadata/api/v1/blood/receive接口地址
    receive: ${bigdataServerAddress}/metadata/api/v1/blood/receive
  collector:
    # 配置跳转到bigdata的/metadata/api/v1/collector/callback接口地址
    callback: ${bigdataServerAddress}/metadata/api/v1/collector/callback
  permissions:
    # 配置跳转到bigdata的/metadata/api/v1/permissions/executePermissionsJudge接口地址
    executePermissionsJudge: ${bigdataServerAddress}/metadata/api/v1/permissions/executePermissionsJudge
  syncTask:
    # 配置跳转到bigdata的/dc/v1/syncTask/callback接口地址
    callback: ${bigdataServerAddress}/dc/v1/syncTask/callback
  updateIndexMetadataFlag: true
## 模块-ustream
ustream:
  user: hive
  yarn-rm-address: ${bigdataUstreamYarnRmAddress}
  agent-yarn-rm-address: ${bigdataUstreamAgentYarnRmAddress}
  flink:
    home: /data/flink/flink-lastest
    history-rest-address: ${bigdataUstreamFlinkHistoryRestAddress}
  gateway:
    home: /data/flink/ustream
    jarname: ustream-gateway-1.0-SNAPSHOT.jar
    mainclass: com.chinaunicom.medical.business.ustream.gateway.Ulauncher
## unicom-etcd/es
com:
  chinaunicom:
    medical:
      comp:
        etcd:
          enabled: true
          # 配置etcd服务地址
          location: ${bigdataEtcdUrl}
        es:
          enabled: true
          nodes: ${bigdataEsNodes}:27920
          userName: ${bigdataEsUserName}
          password: ${bigdataEsPassword}
########### 分割线 ###############

## unicom-apisix
api-six:
  # 配置apisix访问地址
  base-url: ${bigdataApiSixBaseUrl}
  xApiKey: ${bigdataXApiKey}
  allowOrigins: ${bigdataApiSixAllowOrigins}
  # 配置bigdata访问/hsb/v1/logger/info接口地址
  httpLoggerEndpoint: ${bigdataApiSixHttpLoggerEndpoint}
  limitCountRedisHost: redis-1
  limitCountRedisPort: 7501
  limitCountRedisPassword: ${bigdataRedisPassword}
  limitCountRedisDatabase: 0
  kafkaLoggerBrokerList:
    - kafka213-node-1:9092
  kafkaLoggerKafkaTopic: apisix_logger
  kafkaLoggerKey: aaa
  kafkaLoggerDisable: false

## unicom-hsb
hsb-node:
  address:
    # 此配置需要根据部署环境修改，配置hsb访问地址
    - ${bigdataHsbNode}

prometheus:
  # 配置prometheus访问地址
  url: ${bigdataPrometheusUrl}

grafana:
  url: ${bigdataGrafanaUrl}
  header: Auth
  value: viewer

aiMapping:
  aiTermMappingUrl: ${bigdataAiMappingAiTermMappingUrl}
  aiFieldMappingUrl: ${bigdataAiMappingAiFieldMappingUrl}

## 其他
hsb:
  apisix-url: ${bigdataOutsideAccessUrl}/data-sharing-server

easy-es:
  enable: true
  address: ${bigdataEsNodes}:27920
  username: ${bigdataEsUserName}
  password: ${bigdataEsPassword}
  global-config:
    process-index-mode: smoothly
    db-config:
      refresh-policy: immediate