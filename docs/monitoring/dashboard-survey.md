# 组件监控看板调研报告

> 自动生成，共覆盖 22 个组件，已按最新范围移除原可观测性看板组件及两个计算组件，将 Redis 替换为 Valkey，并将 DATART 调整为 Spring Boot Actuator/Micrometer 看板方案。

---

## 总览表

| 组件 | 版本 | 分组 | 数据源方式 | 候选数 | 分级 | 推荐看板 | 总分 |
|---|---|---|---|---|---|---|---|
| MySQL | 8.0.28 | 中间件 | exporter prometheus/mysqld_exporter | 3 | 🟢可直接做 | MySQL Exporter Quickstart and Dashboard (ID: 14057) | 0.96 |
| Nexus | 3.85.0 | 中间件 | 原生 /service/rest/metrics/prometheus (3.81+；3.81 之前为 /service/metrics/prometheus，旧路径有重定向但部分脚本不跟随):8081 | 3 | 🟢可直接做 | Infra / Nexus (grafana.com #16459) | 0.79 |
| Rustfs | 1.0.0 | 中间件 | 原生 /minio/v2/metrics/cluster (legacy MinIO dashboards reference older /minio/prometheus/metrics path):9000 | 3 | 🟡需自建 | 需自建 |  |
| DATART | 3.6.1 | 内部组件 | 原生 /actuator/prometheus:DATART server port | 3 | 🟢可直接做 | Spring Boot 2.1 Statistics (ID 10280) | 0.82 |
| Alertmanager | 0.32.1 | 可观测性 | 原生 /metrics:9093 | 3 | 🟢可直接做 | Alertmanager (ID 9578) | 0.85 |
| Loki | 3.7.2 | 可观测性 | 原生 /metrics:3100 | 3 | 🟢可直接做 | Loki Metrics Dashboard (ID 17781) | 0.87 |
| Prometheus | 3.12.0 | 可观测性 | 原生 /metrics:9090 | 3 | 🟢可直接做 | Prometheus 2.0 Overview (ID: 3662) | 0.94 |
| Promtail | 2.8.11 | 可观测性 | 原生 /metrics:9080 | 3 | 🟢可直接做 | Promtail Monitoring - Metrics and Logs | 0.8 |
| Doris | 4.0.5 | 存储/数据库 | 原生 FE: /metrics on fe_http_port (default 8030); BE: /metrics on be_web_server_port (default 8040). Also supports ?type=json for JSON output. Broker does NOT expose /metrics.:FE 8030 / BE 8040 | 1 | 🟡需自建 | 需自建 |  |
| Elasticsearch | 9.4.2 | 存储/数据库 | exporter prometheus-community/elasticsearch_exporter | 3 | 🟡需自建 | Elasticsearch Exporter Quickstart and Dashboard (Grafana ID 14191) | 0.93 |
| HDFS | 3.5.0 | 存储/数据库 | 原生 /prom（NameNode 默认端口 9870，DataNode 默认端口 9864，与各 Daemon Web UI 同端口）:9870 (NameNode) / 9864 (DataNode) | 3 | 🟢可直接做 | HDFS - DataNode | 0.695 |
| Hive | 4.2.0 | 存储/数据库 | exporter Prometheus JMX Exporter (javaagent attached to HiveServer2/Metastore JVM) or hadoop_exporter (vqcuong) | 3 | 🟡需自建 | 需自建 |  |
| JuiceFS | 1.3.1 | 存储/数据库 | 原生 /metrics:9567 | 2 | 🟢可直接做 | JuiceFS Dashboard (grafana.com ID 20794) | 0.75 |
| Valkey | 8.6 | 存储/数据库 | exporter redis_exporter or Valkey-compatible Redis exporter | 3 | 🟢可直接做 | Valkey/Redis-compatible Redis Exporter Dashboard (ID 763) | 0.86 |
| YARN | 3.5.0 | 存储/数据库 | exporter jmx_prometheus_javaagent (Prometheus JMX Exporter) 挂载到 YARN_RESOURCEMANAGER_OPTS / YARN_NODEMANAGER_OPTS,或 PBWebMedia/yarn-prometheus-exporter 专用 RM 导出器 | 3 | 🟡需自建 | 需自建(参考 Grafana Cloud Apache Hadoop Integration 面板设计) |  |
| Kafka | 4.3.0 | 消息/协调 | exporter Prometheus JMX Exporter(Broker 全量指标)+ danielqsj/kafka_exporter(消费组 lag/topic offset) | 3 | 🟢可直接做 | Kafka Exporter Overview | 0.77 |
| ZooKeeper | 3.8.6 | 消息/协调 | 原生 /metrics:7000 | 3 | 🟢可直接做 | ZooKeeper by Prometheus | 0.94 |
| APISIX | 3.16.0 | 网关/注册中心 | 原生 /apisix/prometheus/metrics:9091 | 2 | 🟢可直接做 | Apache APISIX (ID: 11719) | 0.84 |
| Nacos | 3.2.2 | 网关/注册中心 | 原生 /nacos/actuator/prometheus:8848 | 2 | 🟡需自建 | Nacos (Grafana ID 13221) | 0.62 |
| Nginx | 1.30.2 | 网关/注册中心 | exporter nginx-prometheus-exporter | 3 | 🟢可直接做 | NGINX exporter (ID 12708) | 0.9 |
| Kyuubi | 1.11.1 | 计算/查询引擎 | 原生 /metrics:10019 | 1 | 🟡需自建 | Kyuubi Official Dashboard Template | 0.58 |
| DolphinScheduler | 3.4.1 | 调度 | 原生 /actuator/prometheus (Master: 5679, Worker: 1235, Alert: 50053); /dolphinscheduler/actuator/prometheus (API: 12345):5679 / 1235 / 50053 / 12345 | 3 | 🟢可直接做 | DolphinScheduler Worker Dashboard (Official) | 0.78 |

---

## 分级统计

- 🟢 可直接做: 15 个
- 🟡 需自建: 7 个
- 🔴 缺数据源: 0 个

- 原生 Prometheus 端点: 15 个
- 需要 exporter: 7 个
- 无数据源: 0 个

## 需自建 / 缺数据源复核

> 口径: **需自建** = 已有 Prometheus 数据源(原生端点或 exporter),但缺少可直接复用的高匹配看板;**缺数据源** = 未确认原生端点,也没有可落地 exporter。按该口径复核后,当前清单无真正"缺数据源"组件。

| 组件 | 复核结论 | 数据源事实 | 看板处置 |
|---|---|---|---|
| Rustfs | 保持 🟡需自建 | 官方文档确认存在 Prometheus-compatible metrics endpoints,但未给出稳定 path/port;第三方监控指南给出 MinIO-compatible `/minio/v2/metrics/cluster:9000`,需在目标 1.0.0 实例验证认证方式与指标前缀。 | 无 RustFS 专属高质量看板;MinIO 看板只能作结构参考,需自建/改写 PromQL。 |
| Doris | 保持 🟡需自建 | Doris 4.x 官方文档确认 FE/BE 内置 Prometheus-compatible metrics,FE/BE HTTP 端口 `/metrics` 可直接采集,无需 exporter。 | 现有 Grafana 9734/Doris Overview 主要适配较早版本,对 4.0.5 新指标和新架构匹配不足,建议基于官方模板重做。 |
| Elasticsearch | 保持 🟡需自建 | 9.4 的 `_prometheus` API 是 PromQL 查询/remote write 方向,不是 ES 节点自身 `/metrics` scrape endpoint;仍需 `elasticsearch_exporter`。 | 有可用 exporter 看板,但因数据源需额外部署 exporter,落地侧仍需补充采集链路。 |
| Hive | 从 🔴缺数据源 调整为 🟡需自建 | Hive 官方 metrics 文档说明默认 JMX + JSON file reporter,无原生 Prometheus `/metrics`;但可通过 Prometheus JMX Exporter 或 hadoop_exporter 采集,因此不是"缺数据源"。 | 未找到高质量 HiveServer2/Metastore Prometheus 看板,需基于 JMX Exporter 实际指标自建。 |
| YARN | 保持 🟡需自建 | Hadoop/YARN 官方 Metrics2/JMX 暴露指标,无内置 Prometheus `/metrics`;可用 Prometheus JMX Exporter 或专用 YARN exporter。 | Grafana Cloud Hadoop Integration 可作设计参考,但不是可直接导入的通用 JSON。 |
| Nacos | 保持 🟡需自建 | Nacos 官方监控文档确认 0.8.0 起支持 Prometheus 监控,通过 `/nacos/actuator/prometheus:8848` 暴露。 | 现成看板偏老,需补齐 3.x gRPC/错误率等面板。 |
| Kyuubi | 保持 🟡需自建 | Kyuubi 官方文档确认可配置 `kyuubi.metrics.reporters=PROMETHEUS`,默认 `/metrics:10019`。 | 官方模板可导入,但黄金信号不完整,需补充错误率等面板。 |

---

## 各组件详情

### 中间件

#### MySQL (8.0.28)

**分级**: 🟢可直接做  **数据源**: exporter

**数据源信息**

- 原生 Prometheus: ❌ 
- Exporter: prometheus/mysqld_exporter
- 仓库: https://github.com/prometheus/mysqld_exporter
- 文档: https://dev.mysql.com/doc/refman/en/telemetry-metrics.html
- 说明: MySQL 8.0.28 本身不暴露原生 /metrics HTTP 端点。MySQL 在 8.4+ 的 Enterprise 版本中通过 OpenTelemetry OTLP 协议推送指标，但这需要 MySQL Enterprise Telemetry 插件，且是 push 模式而非 Prometheus pull /metrics 端点。对于社区版 8.0.28，无任何原生 Prometheus 端点，必须使用 mysqld_exporter。

**候选看板**

| 名称 | 来源 | 看板 ID | 下载量 | heat | datasourceMatch | promqlPortability | goldenSignals | total |
|---|---|---|---|---|---|---|---|---|
| [MySQL Exporter Quickstart and Dashboard](https://grafana.com/grafana/dashboards/14057-mysql/) | grafana.com | 14057 | 72001 | 0.3 | 0.3 | 0.18 | 0.18 | **0.96** |
| [MySQL 8.0 Overview](https://grafana.com/grafana/dashboards/20016-mysql-8-0/) | grafana.com | 20016 | 1539 | 0.06 | 0.3 | 0.17 | 0.17 | **0.7** |
| [mysql overview](https://grafana.com/grafana/dashboards/14969-mysqld-overview/) | grafana.com | 14969 | 975 | 0.03 | 0.27 | 0.15 | 0.14 | **0.59** |

**推荐**: MySQL Exporter Quickstart and Dashboard (ID: 14057)

> 下载量 72,001 远超其他候选，heat 得分满分 0.3；基于官方 prometheus/mysqld_exporter 指标体系，datasourceMatch 满分 0.3；PromQL 由 mixin 工具链生成，可移植性强(0.18)；覆盖连接数/QPS/InnoDB Buffer/错误率/延迟等黄金信号(0.18)；total=0.96 为三者最高。唯一不足是更新于 2021 年，可结合 ID 20016（专为 MySQL 8.0 测试，更新至 2024）补充 8.0 特有指标面板。

#### Nexus (3.85.0)

**分级**: 🟢可直接做  **数据源**: native

**数据源信息**

- 原生支持: ✅ long-standing built-in feature; endpoint path changed in 3.81.1 (Switch Metrics Servlets to JAX-RS)
- 端点: `/service/rest/metrics/prometheus (3.81+；3.81 之前为 /service/metrics/prometheus，旧路径有重定向但部分脚本不跟随)` 端口: `8081`
- 文档: https://help.sonatype.com/en/prometheus.html
- 说明: Nexus Repository (Community Edition 即可)默认内置基于 Dropwizard Metrics 的 Prometheus 端点，需要 nx-metrics-all 权限(Basic Auth)。3.85.0 属于 3.81+ 系列，应使用新路径 /service/rest/metrics/prometheus；旧路径 /service/metrics/prometheus 自 3.81.1 起仅做重定向。指标涵盖 Nexus 组件运行时(执行耗时/异常计数)、Jetty(请求/响应/线程)、JVM 运行时。另有独立的 Service Metrics Data API(/service/rest/metrics/data，3.81+)提供组件/资产统计，但不是 Prometheus 格式。

**候选看板**

| 名称 | 来源 | 看板 ID | 下载量 | heat | datasourceMatch | promqlPortability | goldenSignals | total |
|---|---|---|---|---|---|---|---|---|
| [Infra / Nexus](https://grafana.com/grafana/dashboards/16459-infra-nexus/) | grafana.com | 16459 | 160955 | 0.3 | 0.21 | 0.12 | 0.16 | **0.79** |
| [Nexus](https://grafana.com/grafana/dashboards/11702-nexus/) | grafana.com | 11702 | 2346 | 0.015 | 0.06 | 0.1 | 0.08 | **0.255** |
| [Nexus blobs and repos](https://grafana.com/grafana/dashboards/19842-nexus-blobs-and-repos/) | grafana.com | 19842 | 1075 | 0.006 | 0.06 | 0.1 | 0.06 | **0.226** |

**推荐**: Infra / Nexus (grafana.com #16459)

> 数据源已确认为原生支持(dataSource=native)，该看板基于 Nexus 内置 Dropwizard Prometheus 端点(Nexus 组件运行时/Jetty/JVM 指标族)，下载量 160,955 遥遥领先(heat=0.30)，指标名与原生端点核心一致只是采集 URL 路径较旧但底层指标族未变(datasourceMatch=0.21)，覆盖 JVM 饱和度、Jetty 请求量/响应状态(错误率)/线程及组件执行耗时(延迟)等黄金信号(goldenSignals=0.16)，总分 0.79 明显优于其余候选(0.255/0.226)。落地时仅需将 Prometheus 抓取 job 的 metrics_path 改为 3.85.0 对应的 /service/rest/metrics/prometheus 并配置 Basic Auth(nx-metrics-all 权限)，看板面板基本可直接复用。

#### Rustfs (1.0.0)

**分级**: 🟡需自建  **数据源**: native

**数据源信息**

- 原生支持: ✅ MinIO-compatible endpoint documented across current docs (exact RustFS version unspecified; observed in app version 1.0.0 / 1.0.0-alpha.99 builds)
- 端点: `/minio/v2/metrics/cluster (legacy MinIO dashboards reference older /minio/prometheus/metrics path)` 端口: `9000`
- 文档: https://docs.bleemeo.com/server-monitoring/services/rustfs/
- 说明: RustFS 官方文档(docs.rustfs.com/features/logging)仅概念性声明'通过 Prometheus 兼容端点导出大量硬件/软件指标',未给出具体路径/端口。第三方 Bleemeo 监控指南明确指出:RustFS 在 API 端口 9000 上提供 MinIO 兼容端点 /minio/v2/metrics/cluster,指标可能使用 rustfs_ 或 minio_ 前缀,默认需鉴权(设 RUSTFS_PROMETHEUS_AUTH_TYPE=public 可公开访问,或用 mc admin prometheus generate 生成 bearer token)。但社区反馈与该结论冲突:GitHub Issue #796(2025-11)用户访问 http://127.0.0.1:9000/metrics 失败;Issue #1228(2025-12,RustFS 1.0.0 用户)按文档调用 /rustfs/v3/metrics/cluster 携带 bearer token 返回 'invalid header: authorization' 错误,问题状态'待确认'。Ilum 文档另指出其打包的 RustFS chart(app version 1.0.0-alpha.99)上游端点仍处于 alpha/不稳定状态,建议改用 RustFS 官方 docker-compose 中的 OTel Collector 参考栈(RUSTFS_OBS_ENDPOINT 环境变量,--profile observability,内置 Prometheus+Grafana+Jaeger)。结论:原生 MinIO 兼容 Prometheus 端点在文档层面存在,但 1.0.0 版本实测可用性存疑,建议优先验证 OTel Collector 路径或在目标版本上实测 /minio/v2/metrics/cluster 是否可访问。

**候选看板**

| 名称 | 来源 | 看板 ID | 下载量 | heat | datasourceMatch | promqlPortability | goldenSignals | total |
|---|---|---|---|---|---|---|---|---|
| [Minio Overview](https://grafana.com/grafana/dashboards/10946-minio-overview/) | grafana.com | 10946 | 5210 | 0.18 | 0.06 | 0.1 | 0.1 | **0.44** |
| [MinIO Object Storage](https://grafana.com/grafana/dashboards/12563-minio-object-storage/) | grafana.com | 12563 | 2949 | 0.12 | 0.06 | 0.12 | 0.08 | **0.38** |
| [Minio distributed cluster metrics (Hot Cluster Metrics)](https://grafana.com/grafana/dashboards/11568-minio-hot-cluster-metrics/) | grafana.com | 11568 | 2134 | 0.1 | 0.06 | 0.1 | 0.1 | **0.36** |

**推荐**: 需自建

> 原生数据源在文档上存在(/minio/v2/metrics/cluster,端口9000,MinIO 兼容),但 RustFS 1.0.0 实测报告(GitHub #796/#1228)显示该端点在当前版本不稳定或无法直接访问,需先验证或改走 OTel Collector(RUSTFS_OBS_ENDPOINT)路径。Grafana 官方无 RustFS 专属看板(社区讨论 #601 已确认无官方看板)。候选 MinIO 看板(Minio Overview 总分0.44/MinIO Object Storage 0.38/Minio Hot Cluster Metrics 0.36)均基于已过时的 /minio/prometheus/metrics 旧路径与 minio_ 指标名,datasourceMatch 项因此偏低(均为0.06/0.3),无法直接套用。建议:① 先在目标 RustFS 1.0.0 实例上实测确认 /minio/v2/metrics/cluster 或 OTel Collector 导出的实际指标命名空间(rustfs_* vs minio_*);② 基于确认后的指标名,参考 Minio Overview(下载量最高,5210)的面板结构(容量、IOPS、网络、副本状态)二次开发自建看板,补齐延迟分布(histogram)、错误率、磁盘饱和度等黄金信号面板。

### 内部组件

#### DATART (3.6.1)

**分级**: 🟢可直接做  **数据源**: native

**数据源信息**

- 原生支持: ✅ Spring Boot Actuator/Micrometer instrumentation
- 端点: `/actuator/prometheus` 端口: `DATART server port`
- 文档: https://docs.spring.io/spring-boot/reference/actuator/metrics.html
- 说明: DATART is handled as a Spring Boot application dashboard target. Enable Spring Boot Actuator plus micrometer-registry-prometheus and expose /actuator/prometheus, then reuse generic JVM/Spring Boot/Micrometer panels for JVM, HTTP server, Tomcat, HikariCP, process and filesystem metrics.

**候选看板**

| 名称 | 来源 | 看板 ID | 下载量 | heat | datasourceMatch | promqlPortability | goldenSignals | total |
|---|---|---|---|---|---|---|---|---|
| [Spring Boot 2.1 Statistics](https://grafana.com/grafana/dashboards/10280-spring-boot-2-1-statistics/) | grafana.com | 10280 |  | 0.18 | 0.3 | 0.18 | 0.16 | **0.82** |
| [JVM Micrometer](https://grafana.com/grafana/dashboards/4701-jvm-micrometer/) | grafana.com | 4701 |  | 0.16 | 0.27 | 0.18 | 0.14 | **0.75** |
| [Spring Boot Observability](https://grafana.com/grafana/dashboards/?search=spring%20boot%20micrometer) | grafana.com |  |  | 0.1 | 0.24 | 0.16 | 0.14 | **0.64** |

**推荐**: Spring Boot 2.1 Statistics (ID 10280)

> DATART adopts the Spring Boot Actuator/Micrometer monitoring path. This generic Spring Boot dashboard is the best baseline because it uses Micrometer /actuator/prometheus metrics and covers JVM, HTTP, Tomcat, HikariCP and process-level signals; DATART-specific business panels can be added later if custom metrics are introduced.

### 可观测性

#### Alertmanager (0.32.1)

**分级**: 🟢可直接做  **数据源**: native

**数据源信息**

- 原生支持: ✅ 0.14.0
- 端点: `/metrics` 端口: `9093`
- 文档: https://prometheus.io/docs/alerting/latest/alertmanager/
- 说明: Alertmanager 自 v0.14.0 起原生暴露 Prometheus /metrics 端点，默认监听 9093 端口。指标涵盖通知投递（alertmanager_notifications_total、alertmanager_notifications_failed_total）、告警状态（alertmanager_alerts、alertmanager_alerts_received_total）、集群健康（alertmanager_cluster_members、alertmanager_cluster_messages_publish_failures_total）。v0.32.1 完全支持，无需任何额外 exporter。

**候选看板**

| 名称 | 来源 | 看板 ID | 下载量 | heat | datasourceMatch | promqlPortability | goldenSignals | total |
|---|---|---|---|---|---|---|---|---|
| [Alertmanager](https://grafana.com/grafana/dashboards/9578-alertmanager/) | grafana.com | 9578 |  | 0.24 | 0.3 | 0.17 | 0.14 | **0.85** |
| [Alertmanager告警总览](https://grafana.com/grafana/dashboards/9681-alertmanager/) | grafana.com | 9681 |  | 0.12 | 0.3 | 0.14 | 0.12 | **0.68** |
| [OPEN ALERTS OF ALERTMANAGER](https://grafana.com/grafana/dashboards/12947-open-alerts-of-alertmanager/) | grafana.com | 12947 |  | 0.1 | 0.06 | 0.06 | 0.08 | **0.3** |

**推荐**: Alertmanager (ID 9578)

> 得分最高（0.85）。该看板基于 Prometheus 原生 /metrics 端点，datasourceMatch 满分（0.30），完整匹配 Alertmanager 0.32.1 的原生指标体系；PromQL 干净、基于标准 alertmanager_* 指标族、可移植性强（0.17）；覆盖流量（alertmanager_alerts_received_total）、错误（alertmanager_notifications_failed_total）、饱和度（alertmanager_alerts_limited_total）三项 Golden Signals，评分 0.14。由 FUSAKLA 维护并托管在 GitHub（github.com/FUSAKLA/alertmanager-grafana-dashboard），社区广泛引用，2025 年仍有教程参考，热度贡献 0.24。

#### Loki (3.7.2)

**分级**: 🟢可直接做  **数据源**: native

**数据源信息**

- 原生支持: ✅ 0.2
- 端点: `/metrics` 端口: `3100`
- 文档: https://grafana.com/docs/loki/latest/operations/observability/
- 说明: Loki 原生在 HTTP 监听端口(默认 3100)的 /metrics 路径暴露 Prometheus 格式指标,无需任何 exporter。核心指标包括 loki_request_duration_seconds(延迟直方图)、loki_distributor_bytes_received_total(吞吐量)、loki_request_duration_seconds_count{status_code=~'5..'}(错误率)、loki_ingester_memory_streams(饱和度)等,完整覆盖四大黄金信号。该端点自 Loki 早期版本(v0.2)起即存在,属于核心设计而非后期添加。

**候选看板**

| 名称 | 来源 | 看板 ID | 下载量 | heat | datasourceMatch | promqlPortability | goldenSignals | total |
|---|---|---|---|---|---|---|---|---|
| [Loki Metrics Dashboard](https://grafana.com/grafana/dashboards/17781-loki-metrics-dashboard/) | grafana.com | 17781 |  | 0.21 | 0.3 | 0.18 | 0.18 | **0.87** |
| [Loki Mixin Dashboards (Official)](https://github.com/grafana/loki/tree/main/production/loki-mixin) | github |  |  | 0.15 | 0.3 | 0.2 | 0.2 | **0.85** |
| [Loki Dashboard](https://grafana.com/grafana/dashboards/13186-loki-dashboard/) | grafana.com | 13186 |  | 0.24 | 0.09 | 0.06 | 0.06 | **0.45** |

**推荐**: Loki Metrics Dashboard (ID 17781)

> datasourceMatch 满分 0.30:该看板通过 Prometheus 抓取 Loki 原生 /metrics 端点,指标名与数据源完全吻合。goldenSignals 0.18:覆盖延迟(loki_request_duration_seconds p99)、吞吐(distributor bytes/lines)、错误(5xx 率)、饱和度(ingester 内存/队列),四大黄金信号全覆盖。promqlPortability 0.18:使用标准 Prometheus 记录规则与 histogram_quantile,PromQL 可移植性强。若需要更完整的组件级细化(读写路径分离、bloom、retention 等),可补充部署官方 Loki Mixin 13 个子看板(GitHub 源)。

#### Prometheus (3.12.0)

**分级**: 🟢可直接做  **数据源**: native

**数据源信息**

- 原生支持: ✅ 1.0.0
- 端点: `/metrics` 端口: `9090`
- 文档: https://prometheus.io/docs/prometheus/latest/getting_started/
- 说明: Prometheus 自 1.0.0 起即原生在 :9090/metrics 暴露自身指标，无需任何额外配置。v3.12.0 另新增 /api/v1/status/self_metrics（JSON 格式），作为结构化补充端点，但标准文本格式 /metrics 端点仍为主要数据源。默认 scrape job 名为 prometheus，自抓取 localhost:9090/metrics。

**候选看板**

| 名称 | 来源 | 看板 ID | 下载量 | heat | datasourceMatch | promqlPortability | goldenSignals | total |
|---|---|---|---|---|---|---|---|---|
| [Prometheus 2.0 Overview](https://grafana.com/grafana/dashboards/3662-prometheus-2-0-overview/) | grafana.com | 3662 | 1800000 | 0.3 | 0.3 | 0.18 | 0.16 | **0.94** |
| [Prometheus](https://grafana.com/grafana/dashboards/19105-prometheus/) | grafana.com | 19105 | 120000 | 0.12 | 0.28 | 0.16 | 0.15 | **0.71** |
| [Prometheus 2.22](https://grafana.com/grafana/dashboards/13468-prometheus/) | grafana.com | 13468 | 200000 | 0.15 | 0.27 | 0.15 | 0.14 | **0.71** |

**推荐**: Prometheus 2.0 Overview (ID: 3662)

> 综合评分最高(0.94)。heat 得分 0.30 满分：社区下载量极大(估计 180+ 万次)，是最广泛引用的 Prometheus 自监控看板；datasourceMatch 得分 0.30 满分：完全基于 Prometheus 原生 /metrics 指标名（prometheus_target_interval_length_seconds、promhttp_metric_handler_requests_total 等），与 3.12.0 原生端点完美匹配；promqlPortability 0.18：PromQL 干净，支持 job 模板变量，易于多实例扩展；goldenSignals 0.16：覆盖吞吐量(scrape 请求数)、延迟(scrape duration)、错误(failed scrapes)、饱和度(chunk/series/内存)四项黄金信号。唯一需注意：看板名含 2.0 字样为历史遗留，实际指标在 Prometheus 3.x 中完全兼容。

#### Promtail (2.8.11)

**分级**: 🟢可直接做  **数据源**: native

**数据源信息**

- 原生支持: ✅ 1.0.0
- 端点: `/metrics` 端口: `9080`
- 文档: https://grafana.com/docs/loki/latest/send-data/promtail/configuration/
- 说明: Promtail 内置 HTTP 服务器，默认通过 server.http_listen_port=9080 监听，register_instrumentation 默认为 true，自动注册 /metrics 端点。Prometheus 可直接抓取该端点获取 promtail_stream_lag_seconds、pipeline_duration_seconds、promtail_build_info 等指标。Docker 镜像默认端口可能为 80，裸机/K8s 部署通用默认值为 9080。

**候选看板**

| 名称 | 来源 | 看板 ID | 下载量 | heat | datasourceMatch | promqlPortability | goldenSignals | total |
|---|---|---|---|---|---|---|---|---|
| [Promtail Monitoring - Metrics and Logs](https://grafana.com/grafana/dashboards/20881-promtail-monitoring-metrics-and-logs/) | grafana.com | 20881 | 22266 | 0.18 | 0.28 | 0.16 | 0.18 | **0.8** |
| [Loki stack monitoring (Promtail, Loki)](https://grafana.com/grafana/dashboards/14055-loki-stack-monitoring-promtail-loki/) | grafana.com | 14055 | 46929 | 0.2 | 0.21 | 0.13 | 0.14 | **0.68** |
| [Promtail 2.6.x](https://grafana.com/grafana/dashboards/16708-promtail-2-6-x/) | grafana.com | 16708 | 27329 | 0.19 | 0.27 | 0.17 | 0.12 | **0.75** |

**推荐**: Promtail Monitoring - Metrics and Logs

> dashboard 20881 是三个候选中更新最及时(2024-04)、内容最完整的选择，同时使用 Prometheus(原生 /metrics 端点)和 Loki(日志)双数据源，覆盖吞吐量、延迟(99th)、丢弃条目、活跃文件数等核心指标，datasourceMatch 最高(0.28)，goldenSignals 覆盖最全(0.18)，total 得分 0.80 居首。14055 虽有评分(4.25)但停更于 2021，面向 Kubernetes 场景且 Promtail 覆盖有限；16708 仅用 Prometheus 但停更于 2022 且无评分。

### 存储/数据库

#### Doris (4.0.5)

**分级**: 🟡需自建  **数据源**: native

**数据源信息**

- 原生支持: ✅ long-standing (pre-1.0, documented through 3.x/4.0 doc tree)
- 端点: `FE: /metrics on fe_http_port (default 8030); BE: /metrics on be_web_server_port (default 8040). Also supports ?type=json for JSON output. Broker does NOT expose /metrics.` 端口: `FE 8030 / BE 8040`
- 文档: https://doris.apache.org/docs/dev/admin-manual/maint-monitor/monitor-alert/
- 说明: Doris FE/BE natively export Prometheus text-format metrics (e.g. doris_fe_connection_total, doris_fe_cache_hit, doris_be_* counters/gauges) with # HELP/# TYPE comment lines, no exporter needed. Official docs recommend Prometheus scrape + Grafana import of an official dashboard JSON template. For disaggregated storage-compute (cloud) deployments there is a separate doris-grafana-dashboard-cloud.json covering FE/BE-Compute-Group/Meta-Service, also Prometheus-based. An open GitHub issue (apache/doris#60270, 2026) notes the widely-distributed dashboard 9734 has panel/metric mismatches against newer disaggregated clusters; for standard shared-nothing 4.0.5 deployments core FE/BE metric names remain stable but some newer metrics/panels may be missing from the 2023-era dashboard revision.

**候选看板**

| 名称 | 来源 | 看板 ID | 下载量 | heat | datasourceMatch | promqlPortability | goldenSignals | total |
|---|---|---|---|---|---|---|---|---|
| [Doris Overview](https://grafana.com/grafana/dashboards/9734-doris-overview/) | grafana.com | 9734 | 13096 | 0.21 | 0.18 | 0.16 | 0.14 | **0.69** |

**推荐**: 需自建

> 原生数据源完备(FE :8030/metrics、BE :8040/metrics,Prometheus 文本格式,无需 exporter)。唯一候选 Grafana 9734 'Doris Overview' 下载量较高(heat≈0.21)但最后更新于 2023-04-16、对应 revision 5(官方文档标注适配 1.2.x),与 4.0.5 的部分新增指标/面板存在偏差(datasourceMatch≈0.18,GitHub issue #60270 也反映该看板在新架构下面板与实际指标不匹配),PromQL 可移植性和黄金指标覆盖度也只能给中等分(各≈0.16/0.14)。total≈0.69 未达到直接复用阈值,建议以该看板为基线模板,结合 4.0.5 实际暴露的 doris_fe_*/doris_be_* 指标自建/修订面板,补齐延迟、流量、错误、饱和度四类黄金信号。

#### Elasticsearch (9.4.2)

**分级**: 🟡需自建  **数据源**: exporter

**数据源信息**

- 原生 Prometheus: ❌ /_prometheus/api/v1/query, /_prometheus/api/v1/query_range, /_prometheus/api/v1/write (no /metrics scrape endpoint for node/cluster stats)
- Exporter: prometheus-community/elasticsearch_exporter
- 仓库: https://github.com/prometheus-community/elasticsearch_exporter
- 文档: https://www.elastic.co/search-labs/blog/elasticsearch-native-prometheus-api
- 说明: Elasticsearch 9.4 添加的 '_prometheus' API 是 tech preview,提供 PromQL 查询接口(读取已存入 ES TSDS 的时序数据)和 Prometheus Remote Write v1 摄入端点,方向与传统 exporter 相反(是 ES 作为 PromQL 后端/接收端,而非暴露自身节点/集群指标供 Prometheus 抓取)。因此 ES 自身没有可供 Prometheus scrape 的 /metrics 端点,集群健康度/JVM/索引等监控仍需依赖第三方 exporter。

**候选看板**

| 名称 | 来源 | 看板 ID | 下载量 | heat | datasourceMatch | promqlPortability | goldenSignals | total |
|---|---|---|---|---|---|---|---|---|
| [Elasticsearch Exporter Quickstart and Dashboard](https://grafana.com/grafana/dashboards/14191-elasticsearch-overview/) | grafana.com | 14191 | 9352931 | 0.3 | 0.27 | 0.18 | 0.18 | **0.93** |
| [ElasticSearch](https://grafana.com/grafana/dashboards/2322-elasticsearch/) | grafana.com | 2322 | 98228 | 0.08 | 0.15 | 0.1 | 0.1 | **0.43** |
| [Elasticsearch - Exporter](https://grafana.com/grafana/dashboards/3236-elasticsearch-exporter/) | grafana.com | 3236 | 1209 | 0.01 | 0.12 | 0.08 | 0.06 | **0.27** |

**推荐**: Elasticsearch Exporter Quickstart and Dashboard (Grafana ID 14191)

> heat 接近满分(0.3,9.35M 下载量,Grafana Labs 官方 mixin 生成);datasourceMatch 较高(0.27,基于 prometheus-community/elasticsearch_exporter 当前活跃维护版本(v1.10.0, 2025-12)的标准指标命名,与所选 exporter 数据源一致);promqlPortability 良好(0.18,mixin 生成的 PromQL 规范、可移植,含 recording rules);goldenSignals 覆盖较全(0.18,含集群健康、节点资源饱和度、索引写入/查询流量与延迟、JVM GC/堆等)。综合 total=0.93,显著优于另外两个年久失修的候选(2322 仅 0.43,3236 仅 0.27)。

#### HDFS (3.5.0)

**分级**: 🟢可直接做  **数据源**: native

**数据源信息**

- 原生支持: ✅ 3.3.0 (HADOOP-16398，复用 Ozone HDDS-846 的 PrometheusMetricsSink)，3.5.0 沿用
- 端点: `/prom（NameNode 默认端口 9870，DataNode 默认端口 9864，与各 Daemon Web UI 同端口）` 端口: `9870 (NameNode) / 9864 (DataNode)`
- 文档: https://issues.apache.org/jira/browse/HADOOP-16398
- 说明: 需在 core-site.xml 中显式设置 hadoop.prometheus.endpoint.enabled=true（默认 false）。开启后 Hadoop 各 Daemon(NameNode/DataNode/JournalNode 等)的 Web UI 端口会暴露 /prom 路径，输出格式为 Hadoop_<Context>_<MetricName>(如 Hadoop_DataNode_BytesRead、Hadoop_NameNode_FSNamesystem_*)，源自既有 Metrics2 体系经 PrometheusMetricsSink 转换。官方 Metrics.html(https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-common/Metrics.html) 本身未直接提及该端点，但该特性自 3.3.0 起内置于 hadoop-common，3.5.0 同样适用；Stackable 文档(https://docs.stackable.tech/home/stable/hdfs/usage-guide/monitoring/)给出了 :9870/prom 的实际访问示例。

**候选看板**

| 名称 | 来源 | 看板 ID | 下载量 | heat | datasourceMatch | promqlPortability | goldenSignals | total |
|---|---|---|---|---|---|---|---|---|
| [HDFS - DataNode](https://grafana.com/grafana/dashboards/23175-hdfs-datanode/) | grafana.com | 23175 | 702 | 0.075 | 0.3 | 0.14 | 0.18 | **0.695** |
| [Hadoop-hdfs巡检](https://grafana.com/grafana/dashboards/9281-hadoop-hdfs/) | grafana.com | 9281 | 2790 | 0.3 | 0.03 | 0.12 | 0.06 | **0.51** |
| [Hadoop HDFS FSImage](https://grafana.com/grafana/dashboards/12236-hadoop-hdfs-fsimage/) | grafana.com | 12236 | 1788 | 0.192 | 0.03 | 0.12 | 0.04 | **0.382** |

**推荐**: HDFS - DataNode

> 下载量虽不是最高(702 vs 9281 的 2790)，但其 PromQL 全部基于 Hadoop_DataNode_* 命名(如 Hadoop_DataNode_BytesRead、Hadoop_DataNode_ReadBlockOpAvgTime、Hadoop_DataNode_RpcQueueTimeAvgTime)，与 HDFS 3.5.0 原生 /prom 端点(PrometheusMetricsSink, HADOOP-16398)输出的指标命名完全一致，datasourceMatch 得满分 0.3；同时覆盖延迟(ReadBlockOpAvgTime/WriteBlockOpAvgTime/HeartbeatsAvgTime)、流量(BytesRead/BytesWritten/SentBytes/ReceivedBytes)、错误(LogError/LogFatal/VolumeFailures/BlockVerificationFailures)、饱和度(ThreadsBlocked/MemHeapUsedM/GcTimeMillis/CallQueueLength)四大黄金信号，goldenSignals=0.18，综合 total=0.695 明显领先。反观下载量最高的'Hadoop-hdfs巡检'(9281)实际查询全部基于 node_exporter 主机指标(node_cpu_seconds_total/node_disk_*)，与 HDFS 原生指标无关，datasourceMatch 仅 0.03；'Hadoop HDFS FSImage'(12236)依赖第三方 marcelmay/hadoop-hdfs-fsimage-exporter sidecar，同样不匹配原生 /prom 数据源，且作者自述'大部分指标未在图上展示'，goldenSignals 仅 0.04。综合来看，HDFS - DataNode 可作为基线模板，按需补充 NameNode 同类面板(同样基于 Hadoop_NameNode_* 指标)即可直接使用。

#### Hive (4.2.0)

**分级**: 🟡需自建  **数据源**: exporter

**数据源信息**

- 原生 Prometheus: ❌ /jmx (HiveServer2 Web UI "Metrics Dump" tab, JMX-encoded JSON; default reporters are JMX + JSON file)
- Exporter: Prometheus JMX Exporter (javaagent attached to HiveServer2/Metastore JVM) or hadoop_exporter (vqcuong)
- 仓库: https://github.com/prometheus/jmx_exporter
- 文档: https://cwiki.apache.org/confluence/display/Hive/Hive+Metrics
- 说明: Apache Hive 官方文档(Hive Metrics wiki)确认:默认仅启用 JMX 和 JSON file 两种 metrics reporter,未提供原生 Prometheus reporter 或 /metrics 端点。HiveServer2 Web UI 提供 'Metrics Dump' 标签页,以 JSON 形式展示 JMX 指标。社区普遍通过 Prometheus JMX Exporter(javaagent)或 hadoop_exporter 抓取 JMX-HTTP 暴露指标后转为 Prometheus 格式。Hive 4.2.0 官方文档索引页(https://hive.apache.org/docs/)未提供版本专属的 Prometheus 集成说明,确认 4.2.0 与历史版本一致未原生支持 Prometheus。

**候选看板**

| 名称 | 来源 | 看板 ID | 下载量 | heat | datasourceMatch | promqlPortability | goldenSignals | total |
|---|---|---|---|---|---|---|---|---|
| [Hive Data](https://grafana.com/grafana/dashboards/10188-hive-data/) | grafana.com | 10188 |  | 0.02 | 0 | 0 | 0 | **0.02** |
| [Hive - HiveServer2 (Ambari/AMS)](https://github.com/arenadata/mpack-adh/blob/master/stacks/ADH/2.0/services/AMBARI_METRICS/package/files/grafana-dashboards/HDP/grafana-hive-hiverserver2.json) | github |  |  | 0.05 | 0.05 | 0.02 | 0.08 | **0.2** |
| [hadoop_exporter (no dedicated Hive dashboard)](https://github.com/vqcuong/hadoop_exporter) | github |  |  | 0.03 | 0.1 | 0.1 | 0.02 | **0.25** |

**推荐**: 需自建

> 未找到任何基于 Prometheus + JMX Exporter 指标命名、且专门面向 Apache Hive(HiveServer2/Metastore)的高质量看板。grafana.com 上 'Hive Data'(ID 10188)实为无关项目(蜂巢/beekeeping 数据采集),datasourceMatch≈0、heat≈0.02,total=0.02。arenadata 的 HiveServer2 看板基于 Ambari Metrics System(AMS/OpenTSDB),与第一步确定的 JMX Exporter→Prometheus 路径指标命名不匹配,datasourceMatch 仅 0.05,total=0.2。hadoop_exporter 项目本身支持 Prometheus 抓取 Hive JMX,但其仓库只提供 HDFS/YARN 看板,无现成 Hive 看板可直接复用,total=0.25。三者 total 均 < 0.3,且均不构成可直接使用的优质看板;但 Hive 可通过 JMX Exporter 或 hadoop_exporter 形成 Prometheus 数据源,因此应标记为需自建(🟡),不是缺数据源(🔴)。落地时需要先搭建 JMX Exporter 抓取链路,再基于 jmx_exporter 暴露的 HiveServer2/Metastore 指标(如 jvm_memory_*、hadoop_metrics_hiveserver2_*)自建看板。

#### JuiceFS (1.3.1)

**分级**: 🟢可直接做  **数据源**: native

**数据源信息**

- 原生支持: ✅ pre-1.0 (long-standing feature, confirmed current in 1.3.1)
- 端点: `/metrics` 端口: `9567`
- 文档: https://juicefs.com/docs/community/administration/monitoring/
- 说明: After `juicefs mount`, Prometheus-formatted metrics are automatically exposed at http://localhost:9567/metrics (configurable via --metrics host:port). Also accessible via `cat /jfs/.stats` and `juicefs stats` CLI. S3 Gateway metrics require client >= 0.17.1; Consul-based service discovery for metrics requires client >= 1.0.0. Hadoop Java SDK additionally supports Pushgateway/Graphite/Prometheus remote-write. Official dashboard import via grafana.com ID 20794, designed for Prometheus scraping localhost:9090.

**候选看板**

| 名称 | 来源 | 看板 ID | 下载量 | heat | datasourceMatch | promqlPortability | goldenSignals | total |
|---|---|---|---|---|---|---|---|---|
| [JuiceFS Dashboard](https://grafana.com/grafana/dashboards/20794-juicefs-dashboard/) | grafana.com | 20794 | 1066 | 0.12 | 0.3 | 0.18 | 0.15 | **0.75** |
| [JuiceFS Grafana Template (general mount-point, K8s, S3 Gateway, Hadoop SDK)](https://github.com/juicedata/juicefs/blob/main/docs/en/grafana_template.json) | github |  |  | 0.05 | 0.3 | 0.18 | 0.15 | **0.68** |

**推荐**: JuiceFS Dashboard (grafana.com ID 20794)

> datasourceMatch=0.3(满分):该看板由 juicedata 官方维护,直接对应 GitHub docs/en/grafana_template.json,所用指标(juicefs_fuse_ops_durations_histogram_seconds、juicefs_blockcache_*、juicefs_object_request_* 等)与第一步确认的原生 /metrics:9567 端点指标名完全一致,无需任何 exporter 映射。goldenSignals=0.15(0.2 满分的 75%):IO/Transaction/Objects Latency 覆盖延迟,IO Throughput/Operations/Transactions 覆盖流量,Data Size/Files/CPU/Memory/Block Cache 覆盖饱和度,但跨 FUSE 操作的统一错误率面板较薄弱(仅 Objects Requests 含 juicefs_object_request_errors)。promqlPortability=0.18:PromQL 直接基于原生指标,无复杂 relabel,迁移成本低。heat=0.12:下载量 1066,在专用存储组件看板中处于中等热度,2025-04-18 仍在更新维护。综合 total=0.75,优先直接导入该官方看板,如需补全错误率面板可后续少量自建。

#### Valkey (8.6)

**分级**: 🟢可直接做  **数据源**: exporter

**数据源信息**

- 原生 Prometheus: ❌ N/A (Valkey/Redis-compatible RESP protocol; no built-in HTTP /metrics endpoint)
- Exporter: redis_exporter or Valkey-compatible Redis exporter
- 仓库: https://github.com/oliver006/redis_exporter
- 文档: https://valkey.io/docs/
- 说明: Valkey 8.6 is Redis-compatible at protocol and INFO-command level. The monitoring path should use a Redis/Valkey-compatible Prometheus exporter and standard redis_* metrics; dashboard reuse is acceptable when datasourceMatch records the Valkey compatibility assumption.

**候选看板**

| 名称 | 来源 | 看板 ID | 下载量 | heat | datasourceMatch | promqlPortability | goldenSignals | total |
|---|---|---|---|---|---|---|---|---|
| [Valkey/Redis-compatible Dashboard for Prometheus Redis Exporter 1.x](https://grafana.com/grafana/dashboards/763-redis-dashboard-for-prometheus-redis-exporter-1-x/) | grafana.com | 763 | 384193 | 0.21 | 0.3 | 0.18 | 0.17 | **0.86** |
| [Valkey/Redis-compatible Dashboard for Prometheus Redis Exporter (helm stable/redis-ha)](https://grafana.com/grafana/dashboards/11835-redis-dashboard-for-prometheus-redis-exporter-helm-stable-redis-ha/) | grafana.com | 11835 | 28126450 | 0.18 | 0.21 | 0.12 | 0.14 | **0.65** |
| [Valkey/Redis-compatible Exporter Quickstart and Dashboard](https://grafana.com/grafana/dashboards/14091-redis-exporter-quickstart-and-dashboard/) | grafana.com | 14091 | 278129 | 0.09 | 0.27 | 0.16 | 0.16 | **0.68** |

**推荐**: Valkey/Redis-compatible Redis Exporter Dashboard (ID 763)

> Valkey 8.6 should be monitored through Redis/Valkey-compatible exporter metrics. The original Redis exporter dashboard ID 763 uses standard redis_* metric names and remains the best reusable baseline for Valkey when the exporter exposes compatible INFO-derived metrics; verify metric names during rollout and rename labels/job filters as needed.

#### YARN (3.5.0)

**分级**: 🟡需自建  **数据源**: exporter

**数据源信息**

- 原生 Prometheus: ❌ 无(仅 Metrics2 框架,JMX/Ganglia/Graphite/File sink,无 PrometheusSink 或 /metrics 路径)
- Exporter: jmx_prometheus_javaagent (Prometheus JMX Exporter) 挂载到 YARN_RESOURCEMANAGER_OPTS / YARN_NODEMANAGER_OPTS,或 PBWebMedia/yarn-prometheus-exporter 专用 RM 导出器
- 仓库: https://github.com/prometheus/jmx_exporter
- 文档: https://hadoop.apache.org/docs/r3.5.0/hadoop-project-dist/hadoop-common/Metrics.html
- 说明: Hadoop 3.5.0 官方 Metrics 文档(hadoop-project-dist/hadoop-common/Metrics.html)只描述 Metrics2 框架及 ClusterMetrics/QueueMetrics/NodeManagerMetrics/ContainerMetrics 等指标记录,通过 JMX 暴露,未提供内置 Prometheus sink 或 /metrics HTTP 端点。截至 3.5.0 仍需依赖外部 JMX-to-Prometheus 桥接(jmx_prometheus_javaagent)或第三方 exporter。Grafana Cloud 官方 Hadoop 集成文档明确指出需要为每个组件配置 JMX exporter。

**候选看板**

| 名称 | 来源 | 看板 ID | 下载量 | heat | datasourceMatch | promqlPortability | goldenSignals | total |
|---|---|---|---|---|---|---|---|---|
| [AWS EMR Hadoop 2](https://grafana.com/grafana/dashboards/607-aws-emr-hadoop-2/) | grafana.com | 607 | 93542 | 0.3 | 0 | 0 | 0.02 | **0.32** |
| [hadoop_jmx_exporter dashboards (YARN ResourceManager/NodeManager)](https://github.com/opsnull/hadoop_jmx_exporter) | github |  |  | 0.03 | 0.1 | 0.08 | 0.1 | **0.31** |
| [Grafana Cloud 官方 Apache Hadoop Integration(含 ResourceManager Dashboard)](https://grafana.com/docs/grafana-cloud/monitor-infrastructure/integrations/integration-reference/integration-apache-hadoop/) | official |  |  | 0.1 | 0.24 | 0.14 | 0.18 | **0.66** |

**推荐**: 需自建(参考 Grafana Cloud Apache Hadoop Integration 面板设计)

> dataSource=exporter,但 grafana.com 上无高质量、维护中、面向 JMX-exporter 指标命名的 YARN 专用看板。Top1 下载量候选 AWS EMR Hadoop 2(heat=0.3 最高)经检查无 datasource/查询表达式定义(datasourceMatch=0,面向 AWS CloudWatch 而非通用 Prometheus),total 仅 0.32,实际不可用;GitHub 社区看板(opsnull/hadoop_jmx_exporter)heat 低(无下载量数据)且基于 2018 年 CDH 5.14.2 时代 JMX 字段,promqlPortability 仅 0.08,total=0.31。Grafana Cloud 官方 Apache Hadoop Integration(支持 Hadoop 3.3.1+ 与 jmx_exporter 0.17.0+)datasourceMatch=0.24 、goldenSignals=0.18 最高,total=0.66 最优,但属 Grafana Cloud 集成而非可直接导入的独立 dashboard JSON。综合考量,三个候选均无法直接使用,判定为需自建:先部署 jmx_prometheus_javaagent 采集 YARN RM/NM JMX 指标,再参照 Grafana Cloud 官方 Hadoop Integration 的 ResourceManager/NodeManager 面板布局(队列资源、容器数、调度延迟、NM 健康)自建 PromQL 看板。

### 消息/协调

#### Kafka (4.3.0)

**分级**: 🟢可直接做  **数据源**: exporter

**数据源信息**

- 原生 Prometheus: ❌ 无(经 JMX MBeans 暴露,无内置 HTTP /metrics 端点)
- Exporter: Prometheus JMX Exporter(Broker 全量指标)+ danielqsj/kafka_exporter(消费组 lag/topic offset)
- 仓库: https://github.com/prometheus/jmx_exporter
- 文档: https://kafka.apache.org/documentation/#monitoring
- 说明: Apache Kafka 各版本(含 4.x)均不内置 Prometheus /metrics HTTP 端点。指标通过 JMX MBeans 暴露。KIP-714/KIP-1076(Kafka 3.7/4.0)引入的是客户端→Broker 的 telemetry 推送(OpenTelemetry 风格),仍非 Broker 侧原生 Prometheus 拉取端点。生产标准做法是挂载 Prometheus JMX Exporter Java agent(KAFKA_OPTS,典型端口 7071)将 JMX 转为 Prometheus 抓取端点。

**候选看板**

| 名称 | 来源 | 看板 ID | 下载量 | heat | datasourceMatch | promqlPortability | goldenSignals | total |
|---|---|---|---|---|---|---|---|---|
| [Kafka Exporter Overview](https://grafana.com/grafana/dashboards/7589-kafka-exporter-overview/) | grafana.com | 7589 | 233926 | 0.21 | 0.27 | 0.16 | 0.13 | **0.77** |
| [Kafka Overview](https://grafana.com/grafana/dashboards/721-kafka/) | grafana.com | 721 | 36085 | 0.15 | 0.27 | 0.14 | 0.16 | **0.72** |
| [KMinion Cluster Dashboard - Prometheus Exporter for Apache Kafka](https://grafana.com/grafana/dashboards/14012-kminion-cluster-dashboard-prometheus-exporter-for-apache-kafka/) | grafana.com | 14012 | 382382 | 0.3 | 0.12 | 0.16 | 0.16 | **0.74** |

**推荐**: Kafka Exporter Overview

> 总分最高(0.77)。datasourceMatch 满分(0.27)——其指标名基于 danielqsj/kafka_exporter(kafka_topic_partition_current_offset、kafka_consumergroup_lag 等),与本组件确定的 exporter 数据源完全对应;heat 0.21(23万+下载,Kafka 自建监控生态最经典看板)。配合 Prometheus JMX Exporter 看板(721,goldenSignals 更全的 Broker 端延迟/错误指标)可补齐黄金信号。KMinion 看板(14012)heat 最高但需要 KMinion 这一不同 exporter(datasourceMatch 仅 0.12),不匹配 jmx_exporter/kafka_exporter 选型,故不推荐为首选。

#### ZooKeeper (3.8.6)

**分级**: 🟢可直接做  **数据源**: native

**数据源信息**

- 原生支持: ✅ 3.6.0
- 端点: `/metrics` 端口: `7000`
- 文档: https://zookeeper.apache.org/doc/r3.8.6/zookeeperMonitor.html
- 说明: 在 zoo.cfg 中设置 metricsProvider.className=org.apache.zookeeper.metrics.prometheus.PrometheusMetricsProvider 启用;默认 metricsProvider.httpPort=7000,Prometheus 抓取地址为 http://<host>:7000/metrics;metricsProvider.exportJvmInfo 默认 true 导出 JVM 指标;支持 HTTPS。3.8.6 沿用 3.6.0 引入的原生 Prometheus 支持。

**候选看板**

| 名称 | 来源 | 看板 ID | 下载量 | heat | datasourceMatch | promqlPortability | goldenSignals | total |
|---|---|---|---|---|---|---|---|---|
| [ZooKeeper by Prometheus](https://grafana.com/grafana/dashboards/10465) | grafana.com | 10465 | 157503 | 0.3 | 0.3 | 0.18 | 0.16 | **0.94** |
| [ZooKeeper by Prometheus](https://grafana.com/grafana/dashboards/16317) | grafana.com | 16317 | 575 | 0.06 | 0.27 | 0.16 | 0.14 | **0.63** |
| [Zookeeper Exporter (dabealu)](https://grafana.com/grafana/dashboards/11442) | grafana.com | 11442 | 26185 | 0.18 | 0.09 | 0.14 | 0.12 | **0.53** |

**推荐**: ZooKeeper by Prometheus

> 首选 Grafana ID 10465(total 0.94)。heat 满分(15.7 万下载,远超同类,评分 3.5)。datasourceMatch 满分:经核验其 PromQL 直接引用 ZooKeeper 原生 PrometheusMetricsProvider 指标名(quorum_size / watch_count / znode_count / outstanding_requests / num_alive_connections / avg_latency / followers / election_time / fsynctime),无任何 zk_* 或 jmx_* 旧 exporter 前缀,与 3.8.6 原生 /metrics 端点完全对应。promqlPortability 0.18:多为简洁 gauge/counter 查询,移植成本低。goldenSignals 0.16:覆盖延迟(avg_latency)、流量(outstanding_requests、num_alive_connections)、饱和度(open_file_descriptor、jvm_memory、fsynctime),错误维度覆盖偏弱(无显式 error rate)。备选 16317 同名但下载量低(575);11442 基于 dabealu 第三方 exporter 指标名,与原生端点不匹配,datasourceMatch 仅 0.09,不推荐。

### 网关/注册中心

#### APISIX (3.16.0)

**分级**: 🟢可直接做  **数据源**: native

**数据源信息**

- 原生支持: ✅ 2.4
- 端点: `/apisix/prometheus/metrics` 端口: `9091`
- 文档: https://apisix.apache.org/docs/apisix/plugins/prometheus/
- 说明: APISIX 内置 prometheus 插件，默认在独立端口 9091 暴露 /apisix/prometheus/metrics，指标前缀为 apisix_。可通过 config.yaml plugin_attr 自定义 export_uri、export_addr、metric_prefix。也可关闭 enable_export_server 后通过 public-api 插件将指标挂载到主端口。覆盖指标包括 apisix_http_status、apisix_http_requests_total、apisix_http_latency、apisix_bandwidth、apisix_upstream_status、apisix_nginx_http_current_connections 等。

**候选看板**

| 名称 | 来源 | 看板 ID | 下载量 | heat | datasourceMatch | promqlPortability | goldenSignals | total |
|---|---|---|---|---|---|---|---|---|
| [Apache APISIX](https://grafana.com/grafana/dashboards/11719-apache-apisix/) | grafana.com | 11719 | 63344 | 0.28 | 0.28 | 0.16 | 0.12 | **0.84** |
| [apisix-route-logs](https://grafana.com/grafana/dashboards/18905-apisix-route-logs/) | grafana.com | 18905 | 2033 | 0.05 | 0.05 | 0.02 | 0.04 | **0.16** |

**推荐**: Apache APISIX (ID: 11719)

> 综合得分最高(0.84)。heat 0.28：63,344 次下载，是 APISIX 领域下载量最大的看板；datasourceMatch 0.28：直接基于原生 apisix_ 指标前缀，与内置 prometheus 插件完全匹配；promqlPortability 0.16：官方维护、指标命名规范，PromQL 干净易移植；goldenSignals 0.12：覆盖吞吐量(RPS by status code)、错误率(HTTP 5xx)、饱和度(connections/bandwidth)，但缺少专用延迟面板(apisix_http_latency histogram)，建议导入后补充 latency p50/p95/p99 面板以达到完整四黄金信号覆盖。

#### Nacos (3.2.2)

**分级**: 🟡需自建  **数据源**: native

**数据源信息**

- 原生支持: ✅ 0.8.0
- 端点: `/nacos/actuator/prometheus` 端口: `8848`
- 文档: https://nacos.io/en/docs/next/guide/admin/monitor-guide/
- 说明: 需在 application.properties 中设置 management.endpoints.web.exposure.include=* 以暴露 Spring Boot Actuator 端点。Nacos 3.x 保持相同端口和路径；2.x+ 新增 gRPC 相关指标（grpc_server_requests_seconds_max、grpc_server_executor 等）。Prometheus scrape 配置示例：metrics_path: /nacos/actuator/prometheus，static_configs targets 指向各节点 {ip}:8848。

**候选看板**

| 名称 | 来源 | 看板 ID | 下载量 | heat | datasourceMatch | promqlPortability | goldenSignals | total |
|---|---|---|---|---|---|---|---|---|
| [Nacos](https://grafana.com/grafana/dashboards/13221-nacos/) | grafana.com | 13221 | 3523 | 0.12 | 0.24 | 0.14 | 0.12 | **0.62** |
| [nacos-grafana (official template)](https://github.com/nacos-group/nacos-template/blob/master/nacos-grafana.json) | github |  |  | 0.06 | 0.24 | 0.14 | 0.12 | **0.56** |

**推荐**: Nacos (Grafana ID 13221)

> grafana.com 唯一 Nacos 专属看板，累计下载 3,523 次（heat=0.12），基于原生 /nacos/actuator/prometheus 指标，datasourceMatch 得分 0.24；覆盖流量、饱和度和部分延迟（notify RT），但错误率无专用 PromQL panel（依赖告警列表），goldenSignals 仅 0.12；PromQL 使用 nacos_monitor label 体系，基本可移植但沿用 1.x 时代 domCount 等旧指标名（3.x 应改用 serviceCount），promqlPortability=0.14。该看板 2020 年停更，建议导入后补充 gRPC 指标（grpc_server_requests_seconds_max）和 HTTP 错误率面板以适配 Nacos 3.2.2。

#### Nginx (1.30.2)

**分级**: 🟢可直接做  **数据源**: exporter

**数据源信息**

- 原生 Prometheus: ❌ /stub_status
- Exporter: nginx-prometheus-exporter
- 仓库: https://github.com/nginx/nginx-prometheus-exporter
- 文档: https://nginx.org/en/docs/http/ngx_http_stub_status_module.html
- 说明: Nginx OSS(含1.30.2)仅通过 ngx_http_stub_status_module 暴露纯文本状态页(/stub_status),不原生输出 Prometheus /metrics 格式。需借助 nginx-prometheus-exporter 将 stub_status 转换为 Prometheus 指标后在 :9113/metrics 对外暴露。Nginx Plus 可通过 ngx_http_api_module 配合 prometheus-njs 模块暴露 /metrics,但属于商业版能力,OSS 不支持。

**候选看板**

| 名称 | 来源 | 看板 ID | 下载量 | heat | datasourceMatch | promqlPortability | goldenSignals | total |
|---|---|---|---|---|---|---|---|---|
| [NGINX exporter](https://grafana.com/grafana/dashboards/12708-nginx/) | grafana.com | 12708 | 220504 | 0.3 | 0.3 | 0.16 | 0.14 | **0.9** |
| [NGINX Prometheus exporter](https://grafana.com/grafana/dashboards/17452-nginx/) | grafana.com | 17452 | 3196 | 0.04 | 0.3 | 0.18 | 0.14 | **0.66** |
| [NGINX by nginxinc](https://grafana.com/grafana/dashboards/11199-nginx/) | grafana.com | 11199 | 8544 | 0.06 | 0.3 | 0.16 | 0.12 | **0.64** |

**推荐**: NGINX exporter (ID 12708)

> 下载量最高(220,504次),heat得分满权重0.30;指标名完全对应 nginx-prometheus-exporter 输出的 nginx_connections_*/nginx_http_requests_total,datasourceMatch满分0.30;PromQL基于 stub_status 派生指标,语义清晰可移植(0.16);覆盖连接数、请求量、活跃/等待连接等黄金信号中的流量与饱和度维度(0.14);综合得分0.90为三者最高。注意该看板缺少延迟(latency)面板,如需P99延迟监控可叠加 prometheus-nginxlog-exporter 方案。

### 计算/查询引擎

#### Kyuubi (1.11.1)

**分级**: 🟡需自建  **数据源**: native

**数据源信息**

- 原生支持: ✅ 1.2.0
- 端点: `/metrics` 端口: `10019`
- 文档: https://kyuubi.readthedocs.io/en/v1.11.1/monitor/metrics.html
- 说明: 通过 kyuubi.metrics.reporters=PROMETHEUS 启用，默认端口 10019，路径 /metrics。默认 reporter 为 JSON，需显式配置切换为 PROMETHEUS。自 1.8.0 起 Prometheus 升为推荐 reporter。配置示例：kyuubi.metrics.enabled=true, kyuubi.metrics.reporters=PROMETHEUS, kyuubi.metrics.prometheus.port=10019, kyuubi.metrics.prometheus.path=/metrics。官方仓库自带 grafana/dashboard-template.json。

**候选看板**

| 名称 | 来源 | 看板 ID | 下载量 | heat | datasourceMatch | promqlPortability | goldenSignals | total |
|---|---|---|---|---|---|---|---|---|
| [Kyuubi Official Dashboard Template](https://github.com/apache/kyuubi/blob/branch-1.11/grafana/dashboard-template.json) | github |  |  | 0.05 | 0.27 | 0.14 | 0.12 | **0.58** |

**推荐**: Kyuubi Official Dashboard Template

> grafana.com 市场上无任何 Kyuubi 看板，唯一来源为 Apache Kyuubi 官方仓库自带的 grafana/dashboard-template.json。该模板基于原生 Prometheus 端点指标（kyuubi_* 前缀），datasourceMatch 得分较高(0.27)；PromQL 采用变量模板化（$baseFilter/$instance），可移植性尚可(0.14)；但 golden signals 覆盖不完整：Traffic(连接数/引擎数)与 Saturation(线程池/启动许可)较好，Latency(批处理等待延迟)有部分覆盖，Error 信号完全缺失，故 goldenSignals 仅得 0.12；heat 因无 grafana.com 下载数据仅得 0.05。建议直接导入官方模板后，补充 Error 类指标面板（如操作失败率、异常计数）以完善四大黄金信号。

### 调度

#### DolphinScheduler (3.4.1)

**分级**: 🟢可直接做  **数据源**: native

**数据源信息**

- 原生支持: ✅ 3.1.0
- 端点: `/actuator/prometheus (Master: 5679, Worker: 1235, Alert: 50053); /dolphinscheduler/actuator/prometheus (API: 12345)` 端口: `5679 / 1235 / 50053 / 12345`
- 文档: https://grafana.com/docs/grafana-cloud/send-data/metrics/metrics-prometheus/prometheus-config-examples/the-apache-software-foundation-apache-dolphinscheduler/
- 说明: 基于 Spring Boot Actuator + Micrometer 原生暴露 Prometheus 端点，自 3.1.0 引入。四个组件各自独立端口：Master(5679)、Worker(1235)、Alert(50053)、API Server(12345)。指标前缀为 ds_master_* / ds_worker_* / ds_task_*，另含 JVM/process 标准 Micrometer 指标。

**候选看板**

| 名称 | 来源 | 看板 ID | 下载量 | heat | datasourceMatch | promqlPortability | goldenSignals | total |
|---|---|---|---|---|---|---|---|---|
| [DolphinScheduler Master Dashboard (Official)](https://github.com/apache/dolphinscheduler/blob/3.4.1/dolphinscheduler-meter/src/main/resources/grafana/DolphinSchedulerMaster.json) | github |  |  | 0.1 | 0.3 | 0.18 | 0.16 | **0.74** |
| [DolphinScheduler Worker Dashboard (Official)](https://github.com/apache/dolphinscheduler/blob/3.4.1/dolphinscheduler-meter/src/main/resources/grafana/DolphinSchedulerWorker.json) | github |  |  | 0.1 | 0.3 | 0.18 | 0.2 | **0.78** |
| [DolphinScheduler JVM Dashboard (Official)](https://github.com/apache/dolphinscheduler/blob/3.4.1/dolphinscheduler-meter/src/main/resources/grafana/JVM.json) | github |  |  | 0.06 | 0.24 | 0.16 | 0.1 | **0.56** |

**推荐**: DolphinScheduler Worker Dashboard (Official)

> Worker 看板覆盖全部四个黄金信号(延迟: task execution time; 流量: task execution total; 错误: task success rate; 饱和度: queue full + overload + CPU)，得分最高(0.78)。所有 PromQL 均基于原生 ds_* 指标，与原生端点完全匹配(datasourceMatch=0.30)，查询使用标准 rate/increase 函数，可移植性好(0.18)。建议同时导入 Master 看板(0.74)作为补充，二者均可从 3.4.1 官方 GitHub 直接 import JSON 到 Grafana。
