jasypt:
  encryptor:
    # 此配置需要根据部署环境修改，配置jasypt加密密钥
    password: ${jasyptEncryptorPassword}
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
app:
  version: ${appVersion}
  # nacos所属命名空间配置，此配置可按需要更改
  profile: ${appProfile}
  name: bigdata
  # nacos配置
  nacos:
    namespace: ${appProfile}
    # nacos所属分组配置，此配置可按需要更改
    group: ${appNacosGroup}
    # 此配置需要根据部署环境修改，配置nacos的host地址
    host: ${appNacosHost}
    # 此配置需要根据部署环境修改，配置nacos的端口
    port: ${appNacosPort}
    # 此配置需要根据部署环境修改，配置nacos的账号
    username: ${appNacosUsername}
    # 此配置需要根据部署环境修改，配置nacos的密码
    password: ${appNacosPassword}

spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    type: com.alibaba.druid.pool.DruidDataSource
    url: ${mysqlBigdataUrl}
    username: ${mysqlBigdataUsername}
    password: ${mysqlBigdataPassword}
  profiles:
    active: ${appProfile}
  application:
    name: chinaunicom-medical-mgmt-${app.name}-server
  main:
    allow-bean-definition-overriding: true
    allow-circular-references: true
  cloud:
    nacos:
      discovery:
        server-addr: ${app.nacos.host}:${app.nacos.port}
        namespace: ${app.nacos.namespace}
        heart-beat-timeout: 10000
        username: ${app.nacos.username}
        password: ${app.nacos.password}
      config:
        server-addr: ${app.nacos.host}:${app.nacos.port}
        namespace: ${app.nacos.namespace}
        group: ${app.nacos.group}
        name: ${app.name}
        file-extension: yml
        refresh-enabled: true
        extension-configs:
          - data-id: common-vars.yml
            group: ${app.nacos.group}
            refresh: true
          - data-id: common.yml
            group: ${app.nacos.group}
            refresh: true
        timeout: 10000
        username: ${app.nacos.username}
        password: ${app.nacos.password}
