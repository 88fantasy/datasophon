<#noparse>
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    password: ${mysql.password}
    url: jdbc:mysql://${mysql.url}/${mysql.database}?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=GMT%2b8&autoReconnect=true&failOverReadOnly=false&allowMultiQueries=true&rewriteBatchedStatements=true
    username: ${mysql.username}
    hikari:
      connection-init-sql: set names utf8mb4
</#noparse>