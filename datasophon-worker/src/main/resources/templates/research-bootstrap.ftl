jasypt:
  encryptor:
    #加密因子
    password: ${researchJasyptEncryptorPassword}
    #加密算法
    algorithm: PBEWithMD5AndDES
    iv-generator-classname: org.jasypt.iv.NoIvGenerator


spring:
  profiles:
    active: ${researchProfilesActive}
  cloud:
    nacos:
      config:
        username: ${researchNacosUsername}
        password: ${researchNacosPassword}
        file-extension: yaml
        server-addr: ${researchNacosHosts}
        namespace: ${researchNacosNamespace}
        group: ${researchNacosGroup}
        extension-configs:
          - data-id: common.yaml
            refresh: true
            group: ${researchNacosGroup}
      discovery:
        username: ${researchNacosUsername}
        password: ${researchNacosPassword}
        server-addr: ${researchNacosHosts}
        namespace: ${researchNacosNamespace}

  datasource:
    dynamic:
      druid:
        #去掉wall
        filters: stat

# 动态数据源数据库初始化
research:
  migration:
    enable: ${researchMigrationEnabled}
    locations:
      - file:config/db/migrations # 配置 SQL-based 的 SQL 脚本在该目录下
      - classpath:db/migrations # 配置 SQL-based 的 SQL 脚本在该目录下
    # 对应动态数据源的数据库类型映射
    mapping:
    <#list itemList as item>
      ${item.name}: ${item.value}
    </#list>