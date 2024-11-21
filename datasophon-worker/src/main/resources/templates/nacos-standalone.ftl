spring.sql.init.platform=mysql
db.num=1
db.url.0=${nacosDbUrl}
db.user.0=${nacosDbUser}
db.password.0=${nacosDbPwd}

### Connection pool configuration: hikariCP
db.pool.config.connectionTimeout=30000
db.pool.config.validationTimeout=10000
db.pool.config.maximumPoolSize=20
db.pool.config.minimumIdle=2

nacos.core.auth.system.type=nacos
nacos.core.auth.enabled=${nacosAuthEnabled}
nacos.core.auth.server.identity.key=${nacosUsername}
nacos.core.auth.server.identity.value=${nacosPassword}


management.endpoints.web.exposure.include=prometheus
nacos.core.auth.plugin.nacos.token.secret.key=${nacosTokenSecretKey}

nacos.console.ui.enabled=${nacosWebUI}