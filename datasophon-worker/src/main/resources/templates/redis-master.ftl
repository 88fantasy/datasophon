bind 0.0.0.0
daemonize yes
protected-mode no
port ${redisMasterPort}
logfile /opt/datasophon/redis/logs/master.log
pidfile /opt/datasophon/redis//pid/master.pid
dir ${dataDir}
requirepass ${requirePass}
dbfilename dump-master.rdb
appendonly yes
appendfilename "appendonly-master.aof"

<#list itemList as item>
${item.name} ${item.value}
</#list>