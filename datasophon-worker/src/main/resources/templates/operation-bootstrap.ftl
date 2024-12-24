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


# 动态数据源数据库初始化
operation:
  migration:
    enable: ${operationMigrationEnabled}
    locations:
      - file:config/db/migrations # 配置 SQL-based 的 SQL 脚本在该目录下
      - classpath:db/migrations # 配置 SQL-based 的 SQL 脚本在该目录下
    # 对应动态数据源的数据库类型映射
    mapping:
    <#list itemList as item>
      ${item.name}: ${item.value}
    </#list>