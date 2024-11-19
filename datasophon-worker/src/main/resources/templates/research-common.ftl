sso:
  accessTokenKey: ${researchSsoAccessTokenKey}
  domain: ${researchSsoDomain}
  rolesystem: ${researchSsoRolesystem}
  jwt:
    tokenExpiration: ${researchSsoJwtTokenExpiration}
    tokenSecret: ${researchSsoJwtTokenSecret}

server:
  tomcat:
    accept-count: 1024

swagger.enabled: true
monitor.schduler.run: true

db:
  system:
    url: ${researchDbSystemUrl}
    username: ${researchDbSystemUsername}
    password: ${researchDbSystemPassword}
    driver-class-name: ${researchDbSystemDriverClassName}
  doris:
    url: ${researchDbDorisUrl}
    username: ${researchDbDorisUsername}
    password: ${researchDbDorisPassword}
    driver-class-name: ${researchDbDorisDriverClassName}

spring:
  servlet:
    multipart:
      max-file-size: 200MB
      max-request-size: 200MB
  mvc:
    async:
      request-timeout: 300000
  session:
    store-type: none
  jackson:
    date-format: yyyy-MM-dd HH:mm:ss
    time-zone: GMT+8
    # generator:
    #   write_numbers_as_strings: true
  redis:
    dc-mode: ${researchRedisDcMode}
    host: ${researchRedisHost}
    port: ${researchRedisPort}
    database: ${researchRedisDatabase}
    password: ${researchRedisPassword}
    timeout: 300000
    redisson:
      config: |
        singleServerConfig:
          address: ${researchRedisAddress}
          password: ${researchRedisPassword}
          database: 0
        codec: !<org.redisson.codec.JsonJacksonCodec> {}
        transportMode: "NIO"
  datasource:
    initialization-mode: ALWAYS
    hikari:
      connection-init-sql: SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci
      minimum-idle: 25
      maximum-pool-size: 50
      max-lifetime: 28700000
      idle-timeout: 60000
mybatis-plus:
  mapper-locations:
    - classpath*:/mapper/*Mapper.xml
    - classpath*:/mapper/*/*Mapper.xml
    - classpath*:/com/gitee/sunchenbin/mybatis/actable/mapping/**/*.xml
  type-aliases-package:
    - com.chinaunicom.medical.capacity.*.model
  configuration:
    mapUnderscoreToCamelCase: true
    ##    log-impl: org.apache.ibatis.logging.nologging.NoLoggingImpl
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl

  global-config:
    logic-delete-field: deleted
    logic-not-delete-value: 0
    logic-delete-value: 1
    id-type: auto
  type-handlers-package: com.chinaunicom.medical
  type-enums-package: com.chinaunicom.medical

redis:
  key:
    prefix:
      authCode: "portal:authCode:"
      orderId: "portal:orderId:"
    expire:
      authCode: 90

#ribbon conf
ribbon:
  readTimeout: 600000
feign:
  httpclient:
    enabled: true
  client:
    config:
      default:
        ConnectTimeOut: 5000
        ReadTimeOut: 10000

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
  name: ${spring.application.name}

encrypt.secretKey: 1bv1231gxg1vrujg
cipher.switchButton: 0

com.chinaunicom.medical.designpattern.strategy.projectName: chinaunicom

com.chinaunicom.medical.comp.core.cryptographSwitch: 0

minio:
  accessKey: ${researchMinioAccessKey}
  secretKey: ${researchMinioSecretKey}
  url: ${researchMinioHost}
  outsideUrl: ${researchMinioOutsideHost}
  access: ${researchMinioAccess}
  secret: ${researchMinioSecret}
  endpoint: ${researchMinioEndpoint}
  bucket: ${researchMinioBucket}

retrofit:
  log:
    enable: true
    logging-interceptor: com.github.lianjiatech.retrofit.spring.boot.interceptor.DefaultLoggingInterceptor
    global-log-level: error
    global-log-strategy: body
  retry:
    enable-global-retry: false
  global-connect-timeout-ms: 60000
  global-call-timeout-ms: 60000

down:
  filePath: /data/apps/public-capacity/chinaunicom-medical-mgmt-system/upload/

logging:
  level:
    root: info
log:
  level: info

request-log:
  enable: true