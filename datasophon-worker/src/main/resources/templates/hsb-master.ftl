jasypt:
  encryptor:
    # 此配置需要根据部署环境修改，配置jasypt加密密钥
    password: ${bigdataJasyptEncryptorPassword}
    # 此配置需要根据部署环境修改，配置jasypt算法
    algorithm: PBEWithMD5AndDES
    iv-generator-classname: org.jasypt.iv.NoIvGenerator

## tomcat config
server:
  port: ${bsbServerPort}
  tomcat:
    accept-count: 1024
spring:
  profiles:
    active: ${bigdataAppProfile}
  application:
    name: chinaunicom-medical-mgmt-${hsbAppName}-server
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
        name: ${hsbAppName}
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
