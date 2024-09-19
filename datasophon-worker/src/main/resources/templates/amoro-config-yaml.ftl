ams:
  admin-username: ${amoroUsername}
  admin-password: ${amoroPassword}
  server-bind-host: "0.0.0.0"
  server-expose-host: "127.0.0.1"

  thrift-server:
    max-message-size: 104857600 # 100MB
    selector-thread-count: 2
    selector-queue-size: 4
    table-service:
      bind-port: 1260
      worker-thread-count: 20
    optimizing-service:
      bind-port: 1261

  http-server:
    bind-port: ${amoroHttpPort}

  refresh-external-catalogs:
    interval: 180000 # 3min
    thread-count: 10
    queue-size: 1000000

  refresh-tables:
    thread-count: 10
    interval: 60000 # 1min
    
  self-optimizing:
    commit-thread-count: 10

  optimizer:
    heart-beat-timeout: 60000 # 1min
    task-ack-timeout: 30000 # 30s

  blocker:
    timeout: 60000 # 1min

  # optional features
  expire-snapshots:
    enabled: true
    thread-count: 10

  clean-orphan-files:
    enabled: true
    thread-count: 10

  sync-hive-tables:
    enabled: true
    thread-count: 10

  data-expiration:
    enabled: false
    thread-count: 10
    interval: 1d

#   MySQL database configuration.
  database:
    type: mysql
    jdbc-driver-class: com.mysql.cj.jdbc.Driver
    url: ${amoroDbUrl}
    username: ${amoroDbUsername}
    password: ${amoroDbPassword}


#  terminal:
#    backend: local
#    local.spark.sql.iceberg.handle-timestamp-without-timezone: false

#  Kyuubi terminal backend configuration.
  terminal:
    backend: ${amoroTerminalBackend}
    local.spark.sql.iceberg.handle-timestamp-without-timezone: false
    kyuubi.jdbc.url: ${amoroTerminalJdbcUrl}
    kyuubi.jdbc.user: ${amoroTerminalJdbcUser}
    kyuubi.jdbc.password: ${amoroTerminalJdbcPassword}
    <#list itemList as item>
      ${item.name}: ${item.value}
    </#list>


#  High availability configuration.
  ha:
    enabled: ${amoroHaEnabled}
    cluster-name: default
    zookeeper-address: ${amoroHaZookeeperAddress}

containers:
  - name: localContainer
    container-impl: com.netease.arctic.server.manager.LocalOptimizerContainer
    properties:
      export.JAVA_HOME: ${javaHome}   # JDK environment

#containers:
  - name: flinkContainer
    container-impl: com.netease.arctic.server.manager.FlinkOptimizerContainer
    properties:
      flink-home: /opt/datasophon/flink
      target: yarn-per-job
#      export.JVM_ARGS: -Djava.security.krb5.conf=/opt/krb5.conf
      export.HADOOP_CONF_DIR: /opt/datasophon/hadoop/etc/hadoop/
      export.HADOOP_USER_NAME: ${amoroHadoopUsername}
      export.FLINK_CONF_DIR: /opt/datasophon/flink/conf/
