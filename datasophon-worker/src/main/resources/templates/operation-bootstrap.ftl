jasypt:
  encryptor:
    #加密因子
    password: ${operationJasyptEncryptorPassword}
    #加密算法
    algorithm: PBEWithMD5AndDES
    iv-generator-classname: org.jasypt.iv.NoIvGenerator


spring:
  profiles:
    active: ${operationProfilesActive}
  cloud:
    nacos:
      config:
        username: ${operationNacosUsername}
        password: ${operationNacosPassword}
        file-extension: yaml
        server-addr: ${operationNacosHosts}
        namespace: ${operationNacosNamespace}
        group: ${operationNacosGroup}
        extension-configs:
          - data-id: common.yaml
            refresh: true
            group: ${operationNacosGroup}
      discovery:
        username: ${operationNacosUsername}
        password: ${operationNacosPassword}
        server-addr: ${operationNacosHosts}
        namespace: ${operationNacosNamespace}

  # flyway 配置内容，对应 FlywayAutoConfiguration.FlywayConfiguration 配置项
  flyway:
    enabled: ${operationFlywayEnabled} # 开启 Flyway 功能
    cleanDisabled: true # 禁用 Flyway 所有的 drop 相关的逻辑，避免出现跑路的情况。
    locations: # 迁移脚本目录
      - filesystem:config/db/migration # 配置 SQL-based 的 SQL 脚本在该目录下
      - classpath:db/migration # 配置 SQL-based 的 SQL 脚本在该目录下
    check-location: false # 是否校验迁移脚本目录下。如果配置为 true ，代表需要校验。此时，如果目录下没有迁移脚本，会抛出 IllegalStateException 异常
    baseline-on-migrate: true
    url: ${operationFlywayUrl} # 数据库地址
    user: ${operationFlywayUser} # 数据库账号
    password: ${operationFlywayPassword} # 数据库密码

