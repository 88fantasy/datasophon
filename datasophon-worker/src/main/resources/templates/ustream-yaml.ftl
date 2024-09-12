server:
  port: 9112
jasypt:
  encryptor:
    password: ${encryptorPassword}
    algorithm: PBEWithMD5AndDES
    iv-generator-classname: org.jasypt.iv.NoIvGenerator
log:
  level: info
  max-file-size: 100MB
  max-history: 15
  path: .
logging:
  config: classpath:logback-logstash.xml
  level:
    com:
      chinaunicom: debug
mybatis-plus:
  configuration:
    default-enum-type-handler: org.apache.ibatis.type.EnumOrdinalTypeHandler
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    banner: true
    db-config:
      id-type: ASSIGN_ID
      logic-delete-value: 1
      logic-not-delete-value: 0
  mapper-locations: classpath*:/com/chinaunicom/medical/business/ustream/mapper/xml/*.xml
  type-aliases-package: com.chinaunicom.medical.business.ustream.entity
pagehelper:
  helper-dialect: mysql
  pageSizeZero: true
  params: count=countSql
  reasonable: true
  support-methods-arguments: true
s3:
  access-key: ${s3AccessKey}
  basePath: ${s3BasePath}
  bucket: ${s3Bucket}
  endpoint: ${s3Endpoint}
  secret-key: ${s3Secretkey}
spring:
  application:
    name: ustream
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    password: ${dbPassword}
    url: ${dbUrl}
    username: ${dbUser}
  main:
    allow-bean-definition-overriding: true
  redis:
    host: ${redisHost}
    password: ${redisPassword}
    port: ${redisPort}
    username: ${redisUsername}
  servlet:
    multipart:
      max-file-size: 500MB
      max-request-size: 500MB
swagger:
  enabled: true
system:
  username: ${loginUsername}
  password: ${loginPassword}
  flink-history-rest-address: ${flinkHistoryRestAddress}
  flink-home: ${flinkHomePath}
  grafana-flink-url: http://xx.xx.xx.xx:3000/d/cWNVbGm7z/flink
  sql-gateway-jar: ${ustreamExecutorPath}
  sql-gateway-jar-mainclass: com.chinaunicom.medical.business.ustream.gateway.Ulauncher
  ustream-home: ${ustreamHomePath}
  yarn-rm-http-address: ${yarnRmHttpAddress}
