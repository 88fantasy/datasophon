server:
  disable: true

positions:
  filename: ./positions.yaml

clients:
  - url: ${lokiUrl}

scrape_configs:
 - job_name: datasophon-manager-info
   static_configs:
   - targets:
      - localhost
     labels:
       job: datasophon-manager-info
       host: ${ip}
       __path__: /data/datasophon/logs/datasophon-api.log
   pipeline_stages:
   - multiline:
       firstline: '^\[[A-Z]*\] \d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}'
       max_lines: 256
       max_wait_time: 5s
   - regex:
       expression: '^\[(?P<level>[A-Z]*)\] (?P<time>\d{4}\-\d{2}\-\d{2} \d{2}:\d{2}:\d{2}) (?P<logger>[^\s:]+)'
   - labels:
       logger:
       level:
   - timestamp:
       source: time
       format: '2006-01-02 15:04:05'
       location: "Asia/Shanghai"
   - drop:
       older_than: 120h
       drop_counter_reason: "line_too_old"

 - job_name: datasophon-manager-error
   static_configs:
   - targets:
      - localhost
     labels:
       job: datasophon-manager-error
       host: ${ip}
       __path__: /data/datasophon/logs/datasophon-api-error.log
   pipeline_stages:
   - multiline:
       firstline: '^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}'
       max_lines: 256
       max_wait_time: 5s
   - regex:
       expression: '^(?P<time>\d{4}\-\d{2}\-\d{2} \d{2}:\d{2}:\d{2}) \[(?P<thread>[^\s:]+)\] (?P<level>[A-Z]*) (?P<logger>[^\s:]+)'
   - labels:
       logger:
       level:
       thread:
   - timestamp:
       source: time
       format: '2006-01-02 15:04:05'
       location: "Asia/Shanghai"
   - drop:
       older_than: 120h
       drop_counter_reason: "line_too_old"

 - job_name: hadoop-hdfs-namenode
   static_configs:
   - targets:
      - localhost
     labels:
       job: hadoop-hdfs-namenode
       host: ${ip}
       __path__: /data/install_datasophon/hadoop/logs/hadoop-hdfs-namenode-*.log
   pipeline_stages:
   - multiline:
       firstline: '^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}'
       max_lines: 256
       max_wait_time: 5s
   - regex:
       expression: '^(?P<time>\d{4}\-\d{2}\-\d{2} \d{2}:\d{2}:\d{2},\d{3}) (?P<level>[A-Z]*) (?P<logger>[^\s:]+)'
   - labels:
       logger:
       level:
   - timestamp:
       source: time
       format: '2006-01-02 15:04:05'
       location: "Asia/Shanghai"
   - drop:
       older_than: 120h
       drop_counter_reason: "line_too_old" 

 - job_name: zookeeper-server
   static_configs:
   - targets:
      - localhost
     labels:
       job: zookeeper-server
       host: ${ip}
       __path__: /data/install_datasophon/zookeeper/logs/zookeeper-*-server-*.out
   pipeline_stages:
   - multiline:
       firstline: '^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}'
       max_lines: 256
       max_wait_time: 5s
   - regex:
       expression: '^(?P<time>\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2},\d{3}) \[[^\]]+\] - (?P<level>[A-Z]+)  \[(?P<thread>[^\]:]+):(?P<logger>[^\]]+)\] - (?P<message>[^\s:]+)'
   - labels:
       logger:
       level:
       thread:
       message:
   - timestamp:
       source: time
       format: '2006-01-02 15:04:05'
       location: "Asia/Shanghai"
   - drop:
       older_than: 120h
       drop_counter_reason: "line_too_old"

 - job_name: hadoop-hdfs-datanode
   static_configs:
   - targets:
      - localhost
     labels:
       job: hadoop-hdfs-datanode
       host: ${ip}
       __path__: /data/install_datasophon/hadoop/logs/hadoop-hdfs-datanode-*.log
   pipeline_stages:
   - multiline:
       firstline: '^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}'
       max_lines: 256
       max_wait_time: 5s
   - regex:
       expression: '^(?P<time>\d{4}\-\d{2}\-\d{2} \d{2}:\d{2}:\d{2},\d{3}) (?P<level>[A-Z]*) (?P<logger>[^\s:]+)'
   - labels:
       logger:
       level:
   - timestamp:
       source: time
       format: '2006-01-02 15:04:05'
       location: "Asia/Shanghai"
   - drop:
       older_than: 120h
       drop_counter_reason: "line_too_old"

 - job_name: hadoop-hdfs-httpfs
   static_configs:
   - targets:
      - localhost
     labels:
       job: hadoop-hdfs-httpfs
       host: ${ip}
       __path__: /data/install_datasophon/hadoop/logs/hadoop-hdfs-httpfs-*.log
   pipeline_stages:
   - multiline:
       firstline: '^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}'
       max_lines: 256
       max_wait_time: 5s
   - regex:
       expression: '^(?P<time>\d{4}\-\d{2}\-\d{2} \d{2}:\d{2}:\d{2},\d{3}) (?P<level>[A-Z]*) (?P<logger>[^\s:]+)'
   - labels:
       logger:
       level:
   - timestamp:
       source: time
       format: '2006-01-02 15:04:05'
       location: "Asia/Shanghai"
   - drop:
       older_than: 120h
       drop_counter_reason: "line_too_old"

 - job_name: hadoop-hdfs-journalnode
   static_configs:
   - targets:
      - localhost
     labels:
       job: hadoop-hdfs-journalnode
       host: ${ip}
       __path__: /data/install_datasophon/hadoop/logs/hadoop-hdfs-journalnode-*.log
   pipeline_stages:
   - multiline:
       firstline: '^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}'
       max_lines: 256
       max_wait_time: 5s
   - regex:
       expression: '^(?P<time>\d{4}\-\d{2}\-\d{2} \d{2}:\d{2}:\d{2},\d{3}) (?P<level>[A-Z]*) (?P<logger>[^\s:]+)'
   - labels:
       logger:
       level:
   - timestamp:
       source: time
       format: '2006-01-02 15:04:05'
       location: "Asia/Shanghai"
   - drop:
       older_than: 120h
       drop_counter_reason: "line_too_old"

 - job_name: hadoop-hdfs-zkfc
   static_configs:
   - targets:
      - localhost
     labels:
       job: hadoop-hdfs-zkfc
       host: ${ip}
       __path__: /data/install_datasophon/hadoop/logs/hadoop-hdfs-zkfc-*.log
   pipeline_stages:
   - multiline:
       firstline: '^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}'
       max_lines: 256
       max_wait_time: 5s
   - regex:
       expression: '^(?P<time>\d{4}\-\d{2}\-\d{2} \d{2}:\d{2}:\d{2},\d{3}) (?P<level>[A-Z]*) (?P<logger>[^\s:]+)'
   - labels:
       logger:
       level:
   - timestamp:
       source: time
       format: '2006-01-02 15:04:05'
       location: "Asia/Shanghai"
   - drop:
       older_than: 120h
       drop_counter_reason: "line_too_old"

 - job_name: hadoop-mapred-historyserver
   static_configs:
   - targets:
      - localhost
     labels:
       job: hadoop-mapred-historyserver
       host: ${ip}
       __path__: /data/install_datasophon/hadoop/logs/hadoop-mapred-historyserver-*.log
   pipeline_stages:
   - multiline:
       firstline: '^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}'
       max_lines: 256
       max_wait_time: 5s
   - regex:
       expression: '^(?P<time>\d{4}\-\d{2}\-\d{2} \d{2}:\d{2}:\d{2},\d{3}) (?P<level>[A-Z]*) (?P<logger>[^\s:]+)'
   - labels:
       logger:
       level:
   - timestamp:
       source: time
       format: '2006-01-02 15:04:05'
       location: "Asia/Shanghai"
   - drop:
       older_than: 120h
       drop_counter_reason: "line_too_old"

 - job_name: hadoop-yarn-nodemanager
   static_configs:
   - targets:
      - localhost
     labels:
       job: hadoop-yarn-nodemanager
       host: ${ip}
       __path__: /data/install_datasophon/hadoop/logs/hadoop-yarn-nodemanager-*.log
   pipeline_stages:
   - multiline:
       firstline: '^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}'
       max_lines: 256
       max_wait_time: 5s
   - regex:
       expression: '^(?P<time>\d{4}\-\d{2}\-\d{2} \d{2}:\d{2}:\d{2},\d{3}) (?P<level>[A-Z]*) (?P<logger>[^\s:]+)'
   - labels:
       logger:
       level:
   - timestamp:
       source: time
       format: '2006-01-02 15:04:05'
       location: "Asia/Shanghai"
   - drop:
       older_than: 120h
       drop_counter_reason: "line_too_old"

 - job_name: hadoop-yarn-resourcemanager
   static_configs:
   - targets:
      - localhost
     labels:
       job: hadoop-yarn-resourcemanager
       host: ${ip}
       __path__: /data/install_datasophon/hadoop/logs/hadoop-yarn-resourcemanager-*.log
   pipeline_stages:
   - multiline:
       firstline: '^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}'
       max_lines: 256
       max_wait_time: 5s
   - regex:
       expression: '^(?P<time>\d{4}\-\d{2}\-\d{2} \d{2}:\d{2}:\d{2},\d{3}) (?P<level>[A-Z]*) (?P<logger>[^\s:]+)'
   - labels:
       logger:
       level:
   - timestamp:
       source: time
       format: '2006-01-02 15:04:05'
       location: "Asia/Shanghai"
   - drop:
       older_than: 120h
       drop_counter_reason: "line_too_old"

 - job_name: doris-be-info
   static_configs:
   - targets:
      - localhost
     labels:
       job: doris-be-info
       host: ${ip}
       __path__: /data/install_datasophon/doris/be/log/be.INFO
   pipeline_stages:
   - multiline:
       firstline: '^[A-Z]\d{8} \d{2}:\d{2}:\d{2}\.\d{6}'
       max_lines: 256
       max_wait_time: 5s
   - regex:
       expression: '^(?P<level>[A-Z])(?P<time>\d{8} \d{2}:\d{2}:\d{2}\.\d{6})'
   - labels:
       level:
   - timestamp:
       source: time
       format: '2006-01-02 15:04:05'
       location: "Asia/Shanghai"
   - drop:
       older_than: 120h
       drop_counter_reason: "line_too_old"
           
 - job_name: doris-be-warn
   static_configs:
   - targets:
      - localhost
     labels:
       job: doris-be-warn
       host: ${ip}
       __path__: /data/install_datasophon/doris/be/log/be.WARNING
   pipeline_stages:
   - multiline:
       firstline: '^[A-Z]\d{8} \d{2}:\d{2}:\d{2}\.\d{6}'
       max_lines: 256
       max_wait_time: 5s
   - regex:
       expression: '^(?P<level>[A-Z])(?P<time>\d{8} \d{2}:\d{2}:\d{2}\.\d{6})'
   - labels:
       level:
   - timestamp:
       source: time
       format: '2006-01-02 15:04:05'
       location: "Asia/Shanghai"
   - drop:
       older_than: 120h
       drop_counter_reason: "line_too_old"
                   
 - job_name: kyuubi-server
   static_configs:
   - targets:
      - localhost
     labels:
       job: kyuubi-server
       host: ${ip}
       __path__: /data/install_datasophon/kyuubi/logs/kyuubi-*-org.apache.kyuubi.server.KyuubiServer-*.out
   pipeline_stages:
   - multiline:
       firstline: '^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}'
       max_lines: 256
       max_wait_time: 5s
   - regex:
       expression: '^(?P<time>\d{4}\-\d{2}\-\d{2} \d{2}:\d{2}:\d{2},\d{3}) (?P<level>[A-Z]*) (?P<logger>[^\s:]+)'
   - labels:
       logger:
       level:
   - timestamp:
       source: time
       format: '2006-01-02 15:04:05'
       location: "Asia/Shanghai"
   - drop:
       older_than: 120h
       drop_counter_reason: "line_too_old"
           
 - job_name: ds-api-server
   static_configs:
   - targets:
      - localhost
     labels:
       job: ds-api-server
       host: ${ip}
       __path__: /data/install_datasophon/uscheduler/api-server/logs/dolphinscheduler-api.log
   pipeline_stages:
   - multiline:
       firstline: '^\[[A-Z]*\] \d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}'
       max_lines: 256
       max_wait_time: 5s
   - regex:
       expression: '^\[(?P<level>[A-Z]*)\] (?P<time>\d{4}\-\d{2}\-\d{2} \d{2}:\d{2}:\d{2}\.\d{3} \+\d{4}) (?P<logger>[^\s:]+)'
   - labels:
       logger:
       level:
   - timestamp:
       source: time
       format: '2006-01-02 15:04:05'
       location: "Asia/Shanghai"
   - drop:
       older_than: 120h
       drop_counter_reason: "line_too_old"
           
 - job_name: ds-master-server
   static_configs:
   - targets:
      - localhost
     labels:
       job: ds-master-server
       host: ${ip}
       __path__: /data/install_datasophon/uscheduler/master-server/logs/dolphinscheduler-master.log
   pipeline_stages:
   - multiline:
       firstline: '^\[[A-Z]*\] \d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}'
       max_lines: 256
       max_wait_time: 5s
   - regex:
       expression: '^\[(?P<level>[A-Z]*)\] (?P<time>\d{4}\-\d{2}\-\d{2} \d{2}:\d{2}:\d{2}\.\d{3} \+\d{4}) (?P<logger>[^\s:]+)'
   - labels:
       logger:
       level:
   - timestamp:
       source: time
       format: '2006-01-02 15:04:05'
       location: "Asia/Shanghai"
   - drop:
       older_than: 120h
       drop_counter_reason: "line_too_old"
                   
 - job_name: ds-worker-server
   static_configs:
   - targets:
      - localhost
     labels:
       job: ds-worker-server
       host: ${ip}
       __path__: /data/install_datasophon/uscheduler/worker-server/logs/dolphinscheduler-worker.log
   pipeline_stages:
   - multiline:
       firstline: '^\[[A-Z]*\] \d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}'
       max_lines: 256
       max_wait_time: 5s
   - regex:
       expression: '^\[(?P<level>[A-Z]*)\] (?P<time>\d{4}\-\d{2}\-\d{2} \d{2}:\d{2}:\d{2}\.\d{3} \+\d{4}) (?P<logger>[^\s:]+)'
   - labels:
       logger:
       level:
   - timestamp:
       source: time
       format: '2006-01-02 15:04:05'
       location: "Asia/Shanghai"
   - drop:
       older_than: 120h
       drop_counter_reason: "line_too_old"
           
                   
 - job_name: ustream
   static_configs:
   - targets:
      - localhost
     labels:
       job: ustream
       host: ${ip}
       __path__: /data/install_datasophon/ustream/logs/ustream.log
   pipeline_stages:
   - multiline:
       firstline: '^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}'
       max_lines: 256
       max_wait_time: 5s
   - regex:
       expression: '^(?P<time>\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3}) (?P<level>[A-Z]+)\s+TID: (?P<thread_info>[^\s]+ [^\s]+) (?P<logger>[^\s:]+)'
   - labels:
       logger:
       level:
       thread_info:
   - timestamp:
       source: time
       format: '2006-01-02 15:04:05'
       location: "Asia/Shanghai"
   - drop:
       older_than: 120h
       drop_counter_reason: "line_too_old"

 - job_name: elasticsearch-deprecation
   static_configs:
   - targets:
      - localhost
     labels:
       job: elasticsearch-deprecation
       host: ${ip}
       __path__: /data/install_datasophon/elasticsearch/logs/ddp_es_deprecation.log
   pipeline_stages:
   - multiline:
       firstline: '^\[\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2},\d{3}\]'
       max_lines: 256
       max_wait_time: 5s
   - regex:
       expression: '^\[(?P<time>\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2},\d{3})\]\[(?P<level>[A-Z]+)\](?P<logger>[^\s:]+)'
   - labels:
       logger:
       level:
   - timestamp:
       source: time
       format: '2006-01-02 15:04:05'
       location: "Asia/Shanghai"
   - drop:
       older_than: 120h
       drop_counter_reason: "line_too_old"
                                   
 - job_name: elasticsearch-gc
   static_configs:
   - targets:
      - localhost
     labels:
       job: elasticsearch-gc
       host: ${ip}
       __path__: /data/install_datasophon/elasticsearch/logs/gc.log
   pipeline_stages:
   - multiline:
       firstline: '^\[\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2},\d{3}\+\d{4}\]'
       max_lines: 256
       max_wait_time: 5s
   - regex:
       expression: '^\[(?P<time>\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2},\d{3})\](?P<logger>[^\s:]+)'
   - labels:
       logger:
   - timestamp:
       source: time
       format: '2006-01-02 15:04:05'
       location: "Asia/Shanghai"
   - drop:
       older_than: 120h
       drop_counter_reason: "line_too_old"
                                           
 - job_name: elasticsearch-es
   static_configs:
   - targets:
      - localhost
     labels:
       job: elasticsearch-es
       host: ${ip}
       __path__: /data/install_datasophon/elasticsearch/logs/ddp_es.log
   pipeline_stages:
   - multiline:
       firstline: '^\[\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2},\d{3}\]'
       max_lines: 256
       max_wait_time: 5s
   - regex:
       expression: '^\[(?P<time>\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2},\d{3})\]\[(?P<level>[A-Z]+)\](?P<logger>[^\s:]+)'
   - labels:
       logger:
       level:
   - timestamp:
       source: time
       format: '2006-01-02 15:04:05'
       location: "Asia/Shanghai"
   - drop:
       older_than: 120h
       drop_counter_reason: "line_too_old"
                                                   
 - job_name: elasticsearch-indexing-slowlog
   static_configs:
   - targets:
      - localhost
     labels:
       job: elasticsearch-indexing-slowlog
       host: ${ip}
       __path__: /data/install_datasophon/elasticsearch/logs/ddp_es_index_indexing_slowlog.log
   pipeline_stages:
   - multiline:
       firstline: '^\[\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2},\d{3}\]'
       max_lines: 256
       max_wait_time: 5s
   - regex:
       expression: '^\[(?P<time>\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2},\d{3})\]\[(?P<level>[A-Z]+)\](?P<logger>[^\s:]+)'
   - labels:
       logger:
       level:
   - timestamp:
       source: time
       format: '2006-01-02 15:04:05'
       location: "Asia/Shanghai"
   - drop:
       older_than: 120h
       drop_counter_reason: "line_too_old"
                                                   
 - job_name: elasticsearch-search-slowlog
   static_configs:
   - targets:
      - localhost
     labels:
       job: elasticsearch-search-slowlog
       host: ${ip}
       __path__: /data/install_datasophon/elasticsearch/logs/ddp_es_index_search_slowlog.log
   pipeline_stages:
   - multiline:
       firstline: '^\[\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2},\d{3}\]'
       max_lines: 256
       max_wait_time: 5s
   - regex:
       expression: '^\[(?P<time>\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2},\d{3})\]\[(?P<level>[A-Z]+)\](?P<logger>[^\s:]+)'
   - labels:
       logger:
       level:
   - timestamp:
       source: time
       format: '2006-01-02 15:04:05'
       location: "Asia/Shanghai"
   - drop:
       older_than: 120h
       drop_counter_reason: "line_too_old"

 - job_name: doris-fe-info
   static_configs:
   - targets:
      - localhost
     labels:
       job: doris-fe-info
       host: ${ip}
       __path__: /data/install_datasophon/doris/fe/log/fe.INFO
   pipeline_stages:
   - multiline:
       firstline: '^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2},\d{3}'
       max_lines: 256
       max_wait_time: 5s
   - regex:
       expression: '^(?P<time>\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2},\d{3}) (?P<level>[A-Z]+) \([^\s:]+\) \[(?P<logger>[^\s:]+)'
   - labels:
       logger:
       level:
   - timestamp:
       source: time
       format: '2006-01-02 15:04:05'
       location: "Asia/Shanghai"
   - drop:
       older_than: 120h
       drop_counter_reason: "line_too_old"

 - job_name: doris-fe-warn
   static_configs:
   - targets:
      - localhost
     labels:
       job: doris-fe-warn
       host: ${ip}
       __path__: /data/install_datasophon/doris/fe/log/fe.WARNING
   pipeline_stages:
   - multiline:
       firstline: '^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2},\d{3}'
       max_lines: 256
       max_wait_time: 5s
   - regex:
       expression: '^(?P<time>\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2},\d{3}) (?P<level>[A-Z]+) \([^\s:]+\) \[(?P<logger>[^\s:]+)'
   - labels:
       logger:
       level:
   - timestamp:
       source: time
       format: '2006-01-02 15:04:05'
       location: "Asia/Shanghai"
   - drop:
       older_than: 120h
       drop_counter_reason: "line_too_old"