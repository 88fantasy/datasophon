bind 0.0.0.0
daemonize yes
protected-mode no
port ${valkeyMasterPort}
tcp-backlog 511
logfile /data/install_datasophon/valkey/logs/master.log
pidfile /data/install_datasophon/valkey/pid/master.pid
databases ${valkeyDatabases}
dir ${valkeyDataDir}
requirepass ${valkeyPass}
maxmemory ${valkeyMaxMemory}
maxmemory-policy ${valkeyMaxMemoryPolicy}
dbfilename dump-master.rdb
appendonly yes
appendfilename "appendonly-master.aof"

<#list itemList as item>
${item.name} ${item.value}
</#list>