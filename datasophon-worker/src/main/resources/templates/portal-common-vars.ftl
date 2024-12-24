mysql:
  url: 192.168.2.122:3306
  database: portal
  username: ENC(3Tr+zjAlEm6RvyLvU+uIgw==)
  password: ENC(VVrKz/bMHxZ1dMO7Ik6fMus0IAlbYvhI)

redis:
  host: ${portalRedisHost}
  port: ${portalRedisPort}
  database: ${portalRedisDb}
<if portalRedisPassword??>
  password: ${portalRedisPassword}
</if>

loki:
  url: ${portalLokiUrl}


oss:
  endpoint: ${minioEndpoint}
  access-key:  ${minioAccessKey}
  secret-key: ${minioSecretKey}
  prefix: <#noparse >/capacity/${spring.application.name}</#noparse >
  public-bucket: ${minioBucket}