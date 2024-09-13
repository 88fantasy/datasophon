bind 0.0.0.0
daemonize yes
protected-mode no
port ${redisMasterPort}
tcp-backlog 511
logfile /opt/datasophon/redis/logs/master.log
pidfile /opt/datasophon/redis//pid/master.pid
databases ${redisDatabases}
dir ${redisDataDir}
requirepass ${redisPass}
maxmemory ${redisMaxMemory}
maxmemory-policy ${redisMaxMemoryPolicy}
dbfilename dump-master.rdb
appendonly yes
appendfilename "appendonly-master.aof"

<#list itemList as item>
${item.name} ${item.value}
</#list>