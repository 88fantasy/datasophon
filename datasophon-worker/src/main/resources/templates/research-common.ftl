sso:
  accessTokenKey: ${researchSsoAccessTokenKey:accessTokenKey}
  domain: ${researchSsoDomain:domaindev}
  rolesystem: ${researchSsoRolesystem:rolesystem}
  jwt:
    tokenExpiration: ${researchSsoJwtTokenExpiration:21600000}
    tokenSecret: ${researchSsoJwtTokenSecret:NonceJwtSecre}

server:
  tomcat:
    accept-count: 1024

swagger.enabled: true
monitor.schduler.run: true

db:
  system:
    url: ${researchDbSystemUrl:jdbc:mysql://192.168.2.239:33066/chinaunicom_medical_app_zssy?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai}
    username: ${researchDbSystemUsername:chinaunicom}
    password: ${researchDbSystemPassword:Un1c0m@Dqc79bf8473#22}
    driver-class-name: ${researchDbSystemDriverClassName:com.mysql.cj.jdbc.Driver}
  doris:
    url: ${researchDbDorisUrl:jdbc:mysql://192.168.2.48:9030/chinaunicom_medical_zssy_web?useUnicode=true&characterEncoding=UTF-8&useSSL=false&serverTimezone=Asia/Shanghai}
    username: ${researchDbDorisUsername:root}
    password: ${researchDbDorisPassword:3ght%ed75BGk}
    driver-class-name: ${researchDbDorisDriverClassName:com.mysql.cj.jdbc.Driver}

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
    dc-mode: ${researchRedisDcMode:single}
    host: ${researchRedisHost:192.168.2.239}
    port: ${researchRedisPort:7501}
    database: ${researchRedisDatabase:0}
    password: ${researchRedisPassword:fX0GzFyWOK4LR^9K}
    timeout: 300000
    redisson:
      config: |
        singleServerConfig:
          address: ${researchRedisAddress:redis://redis-node-1:7501}
          password: ${researchRedisPassword:fX0GzFyWOK4LR^9K}
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
    tags:
      application: ${spring.application.name}
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
  accessKey: ${researchMinioAccessKey:cuminio}
  secretKey: ${researchMinioSecretKey:u4Gkp19TRcKKlTCLNA1pyA==}
  url: ${researchMinioHost:http://192.168.2.239:6943/}
  outsideUrl: ${researchMinioOutsideHost:http://192.168.2.239:6943/}
  access: ${researchMinioAccess:cuminio}
  secret: ${researchMinioSecret:u4Gkp19TRcKKlTCLNA1pyA==}
  endpoint: ${researchMinioEndpoint:http://minio-node-1:6943/}
  bucket: ${researchMinioBucket:public}

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