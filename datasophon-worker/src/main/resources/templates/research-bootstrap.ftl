jasypt:
  encryptor:
    #加密因子
    password: ${researchJasyptEncryptorPassword:Wj!~I#+(JYMH-+9zWaM%8RVKrJW8n2Ch}
    #加密算法
    algorithm: PBEWithMD5AndDES
    iv-generator-classname: org.jasypt.iv.NoIvGenerator


spring:
  profiles:
    active: ${researchProfilesActive:prod}
  cloud:
    nacos:
      config:
        username: ${researchNacosUsername:ENC(VSh41yrXgIrPfuHyiEDQIg==)}
        password: ${researchNacosPassword:ENC(FD5U4rI9VcZ8sFFPz5fTqeAQSbzPdAPE3iM8sTWR9vfEJUv05XvTVA==)}
        file-extension: yaml
        server-addr: ${researchNacosHosts:nacos-node-1:9066}
        namespace: ${researchNacosNamespace:prod}
        group: ${researchNacosGroup:retrieval}
        extension-configs:
          - data-id: common.yaml
            refresh: true
            group: ${researchNacosGroup:retrieval}
      discovery:
        username: ${researchNacosUsername:ENC(VSh41yrXgIrPfuHyiEDQIg==)}
        password: ${researchNacosPassword:ENC(FD5U4rI9VcZ8sFFPz5fTqeAQSbzPdAPE3iM8sTWR9vfEJUv05XvTVA==)}
        server-addr: ${researchNacosHosts:nacos-node-1:9066}
        namespace: ${researchNacosNamespace:prod}
        group: ${researchNacosGroup:retrieval}

  # flyway 配置内容，对应 FlywayAutoConfiguration.FlywayConfiguration 配置项
  flyway:
    enabled: ${researchFlywayEnabled:true} # 开启 Flyway 功能
    cleanDisabled: true # 禁用 Flyway 所有的 drop 相关的逻辑，避免出现跑路的情况。
    locations: # 迁移脚本目录
      - filesystem:config/db/migration # 配置 SQL-based 的 SQL 脚本在该目录下
      - classpath:db/migration # 配置 SQL-based 的 SQL 脚本在该目录下
    check-location: false # 是否校验迁移脚本目录下。如果配置为 true ，代表需要校验。此时，如果目录下没有迁移脚本，会抛出 IllegalStateException 异常
    baseline-on-migrate: true
    url: ${researchFlywayUrl:jdbc:mysql://192.168.2.239:33066/chinaunicom_medical_app_zssy?useSSL=false&useUnicode=true&characterEncoding=UTF-8} # 数据库地址
    user: ${researchFlywayUser:chinaunicom} # 数据库账号
    password: ${researchFlywayPassword:Un1c0m@Dqc79bf8473#22} # 数据库密码

