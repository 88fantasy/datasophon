if ! grep -q <<<"$YARN_RESOURCEMANAGER_OPTS" jmx_prometheus_javaagent; then
YARN_RESOURCEMANAGER_OPTS="$YARN_RESOURCEMANAGER_OPTS -javaagent:${hadoopHome}/jmx/jmx_prometheus_javaagent-0.16.1.jar=${rmJmxPort}:${hadoopHome}/jmx/prometheus_config.yml"
fi

if ! grep -q <<<"$YARN_NODEMANAGER_OPTS" jmx_prometheus_javaagent; then
YARN_NODEMANAGER_OPTS="$YARN_NODEMANAGER_OPTS -javaagent:${hadoopHome}/jmx/jmx_prometheus_javaagent-0.16.1.jar=${nmJmxPort}:${hadoopHome}/jmx/prometheus_config.yml"
fi

if ! grep -q <<<"$YARN_HISTORYSERVER_OPTS" jmx_prometheus_javaagent; then
YARN_HISTORYSERVER_OPTS="$YARN_HISTORYSERVER_OPTS -javaagent:${hadoopHome}/jmx/jmx_prometheus_javaagent-0.16.1.jar=${historyServerJmxPort}:${hadoopHome}/jmx/prometheus_config.yml"
fi
