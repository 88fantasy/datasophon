jasypt:
  encryptor:
    # 此配置需要根据部署环境修改，配置jasypt加密密钥
    password: ${bigdataJasyptEncryptorPassword}
    # 此配置需要根据部署环境修改，配置jasypt算法
    algorithm: PBEWithMD5AndDES
    iv-generator-classname: org.jasypt.iv.NoIvGenerator
server:
  servlet:
    context-path: /bigdata
bigdata:
  server:
    # 此配置需要根据部署环境修改，配置任务调度地址，有gateway则配置gateway跳转到bigdata的地址，没有就配bigdata访问地址
    address: ${bigdataServerAddress}

spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    type: com.alibaba.druid.pool.DruidDataSource
    url: ${bigdataMysqlBigdataUrl}
    username: ${bigdataMysqlBigdataUsername}
    password: ${bigdataMysqlBigdataPassword}
  profiles:
    active: ${bigdataAppProfile}
  application:
    name: chinaunicom-medical-mgmt-${bigdataAppName}-server
  main:
    allow-bean-definition-overriding: true
    allow-circular-references: true
  cloud:
    nacos:
      discovery:
        server-addr: ${bigdataAppNacosHost}:${bigdataAppNacosPort}
        namespace: ${bigdataAppProfile}
        heart-beat-timeout: 10000
        username: ${bigdataAppNacosUsername}
        password: ${bigdataAppNacosPassword}
      config:
        server-addr: ${bigdataAppNacosHost}:${bigdataAppNacosPort}
        namespace: ${bigdataAppProfile}
        group: ${bigdataAppNacosGroup}
        name: ${bigdataAppName}
        file-extension: yml
        refresh-enabled: true
        extension-configs:
          - data-id: common-vars.yml
            group: ${bigdataAppNacosGroup}
            refresh: true
          - data-id: common.yml
            group: ${bigdataAppNacosGroup}
            refresh: true
        timeout: 10000
        username: ${bigdataAppNacosUsername}
        password: ${bigdataAppNacosPassword}
