# GRAVITINO 指标接入验证（OTel Collector → Doris）

> 用途：记录 GRAVITINO（Apache Gravitino 元数据服务）监控看板所依据的真实指标清单、与官方文档的差异、
> 面板契约设计，以及已知问题。
> **2026-08-12 已用沙箱环境（ddh-01 Doris + ddh-02 Gravitino 端点）实测回填指标清单与已知问题**，
> 面板逐项现场核对、SQL 交叉验证、延迟面板专项复测**尚未进行**，见文末「现场证据」章节。

## 1. 概述

GRAVITINO 是 Apache Gravitino 元数据服务，Datasophon 内置服务之一（`meta/datacluster/GRAVITINO/service_ddl.json`）。
本次工作范围是给该服务补一个 OTel+Doris 监控看板（服务实例详情页「监控」Tab，20 个面板），**不涉及采集链路改动**——
Gravitino 的 Prometheus 指标抓取早已打通，本次只是新增查询/面板消费这批已有数据。与本任务并行、但不属于本文档范围的
两个独立子任务：后端把 `operation` 属性加入 OTel 指标查询的属性过滤白名单；前端新建 20 个监控面板组件。

## 2. 采集链路现状（已实测）

- `OtelScrapeConfigBuilder.java` 的 `PATH_OVERRIDES` 已含 `GravitinoServer → /prometheus/metrics`，抓取任务与
  其余内置服务走同一套 `prometheus/<service>` job 命名规范（`job_name = role.getServiceRoleName()`），本次无需改动。
- Doris 表 `otel.otel_metrics_gauge` 里 `service_name='GravitinoServer'` 已有 **650 万行**数据，说明采集→落库
  链路已稳定运行了较长时间，非本次新接入。
- 唯一实例：`ddh-02:8090`（沙箱环境单实例，未验证多实例场景）。

## 3. 指标清单（2026-08-12 实测 `/prometheus/metrics` 端点）

端点实测共 **2200 行、175 个 `# TYPE` 声明**：69 个 counter、76 个 gauge、30 个 summary。指标前缀只有两种：
`gravitino_*`（108 个）与 `jvm_*`（67 个）。按 Doris 落表类型 + 属性维度分为 8 类：

| 类别 | 落表 | 属性维度 | 代表指标 |
| --- | --- | --- | --- |
| gauge，无属性（Jetty 线程/连接池） | `otel_metrics_gauge` | 无 | `gravitino_server_http_server_busy_thread_num`、`gravitino_server_http_server_queued_request_num`、`gravitino_relational_store_datasource_active_connections` |
| gauge，JVM（Dropwizard 命名） | `otel_metrics_gauge` | 无（部分带 pool/gc 类子指标名后缀，非 label） | `jvm_heap_used`、`jvm_heap_usage`、`jvm_G1_Young_Generation_count`、`jvm_G1_Old_Generation_time`、`jvm_non_heap_used`、`jvm_pools_Metaspace_used`、`jvm_direct_used` |
| sum，带 `operation` 属性 | `otel_metrics_sum` | `operation` | `gravitino_server_2xx_responses_total`、`gravitino_server_4xx_responses_total`、`gravitino_server_5xx_responses_total` |
| sum，健康探针 | `otel_metrics_sum` | 无 | `gravitino_server_health_live_2xx_responses_total`、`gravitino_server_health_ready_2xx_responses_total` |
| sum，relational store 操作计数 | `otel_metrics_sum` | 无 | 27 个 `gravitino_relational_store_<op>_{success,failure}_total`（如 `listMetalakes`、`getMetalakeByIdentifier`、`deleteTableMetasByLegacyTimeline`、`deleteFilesetVersionsByRetentionCount`） |
| summary，带 `operation` 属性 | `otel_metrics_summary` | `operation` | `gravitino_server_http_request_duration_seconds` |
| summary，relational store 延迟 | `otel_metrics_summary` | 无 | 27 个 `gravitino_relational_store_<op>_total`（Dropwizard Timer 导出） |

> `otel_metrics_gauge`/`otel_metrics_sum`/`otel_metrics_summary` 表结构参考
> `datasophon-api/src/main/resources/observability/doris/V1__otel_tables.sql`：`service_name`/`metric_name` 为顶层列，
> `attributes` 为 VARIANT 类型的维度标签，summary 表额外有 `quantile_values array<struct<quantile,value>>`。

## 4. 与官方文档的差异

对照 [Apache Gravitino 官方指标文档（1.3.0）](https://gravitino.apache.org/docs/1.3.0/metrics)：

1. **`gravitino_catalog_*` 系列指标缺失**：官方文档描述的 Fileset 缓存命中率、JDBC catalog 连接池等指标
   只对 Fileset/JDBC catalog 生效，ddh-02 沙箱环境本次未建这类 catalog，实机端点上**完全不存在**这些指标名。
   实际暴露的核心业务指标是 `gravitino_relational_store_*`（MySQL entity store 的增删查操作计数与延迟）。
2. **JVM 指标命名为 Dropwizard 风格**：非标准 Prometheus JVM exporter 命名（如 `jvm_threads_*`），而是
   `jvm_heap_used`、`jvm_G1_Young_Generation_count` 这类 Dropwizard MetricSet 命名。**没有** `jvm_threads_*`
   系列、**没有**任何 CPU 使用率指标——Gravitino 只注册了 Dropwizard 的 BufferPoolMetricSet /
   GarbageCollectorMetricSet / MemoryUsageGaugeSet 三个 MetricSet，未注册线程或 CPU 相关的 MetricSet。

## 5. 面板契约（20 个面板，5 个分组）

| ID | 标题 | 数据来源（表/指标） | 说明 |
| --- | --- | --- | --- |
| G01 | 节点数 | 角色注册表（非 Prometheus） | GravitinoServer 角色 RUNNING 实例数 |
| G02 | 当前 HTTP QPS | 派生自 G07 最新时间桶 | 不单独发起查询 |
| G03 | Jetty 线程占用率 | gauge `gravitino_server_http_server_busy_thread_num` / `_max_thread_num` | 占比 % |
| G04 | 排队请求数 | gauge `gravitino_server_http_server_queued_request_num` | |
| G05 | JDBC 活跃连接 | gauge `gravitino_relational_store_datasource_active_connections` | |
| G06 | JVM Heap 使用率 | gauge `jvm_heap_usage`（已是 0~1 比值） | % |
| G07 | HTTP 响应速率（按状态类） | sum `gravitino_server_{1..5}xx_responses_total`，rate 1m | 5 条曲线 |
| G08 | Top 操作请求速率 | sum `gravitino_server_2xx_responses_total`，按 `operation` 属性分组，rate 1m | 依赖后端 operation 白名单改动 |
| G09 | 错误请求速率（按操作） | sum `gravitino_server_{4,5}xx_responses_total`，按 `operation` 分组，rate 1m | 依赖后端 operation 白名单改动 |
| G10 | HTTP 请求延迟 p50/p99 | summary `gravitino_server_http_request_duration_seconds`，quantile 0.5/0.99 | **已知恒为 0，见下方「已知问题」** |
| G11 | Jetty 线程数 | gauge busy/idle/total/max 四条 | |
| G12 | 健康探针响应速率 | sum `gravitino_server_health_{live,ready}_2xx_responses_total`，rate 1m | |
| G13 | JDBC 连接池 | gauge active/idle/max 三条 | |
| G14 | 元数据读取速率 | sum `..._listMetalakes_success_total` / `..._getMetalakeByIdentifier_success_total`，rate 1m | |
| G15 | 实体存储失败速率 | 同上两个指标的 `_failure_total` 后缀 | |
| G16 | 后台清理任务速率 | sum `..._deleteTableMetasByLegacyTimeline_success_total` / `..._deleteFilesetVersionsByRetentionCount_success_total`，rate 1m | |
| G17 | JVM 堆内存 | gauge `jvm_heap_used`/`_committed`/`_max` | |
| G18 | GC 频率 | gauge `jvm_G1_{Young,Old}_Generation_count`，rate 1m | |
| G19 | GC 耗时 | gauge `jvm_G1_{Young,Old}_Generation_time`，rate 1m | |
| G20 | 非堆与直接内存 | gauge `jvm_non_heap_used`/`jvm_pools_Metaspace_used`/`jvm_direct_used` | |

**明确不做的面板及原因**：

- **CPU 使用率、JVM 线程数**——Gravitino 未注册对应 Dropwizard MetricSet，无数据源。
- **Fileset catalog 缓存命中率、JDBC catalog 连接池**（`gravitino_catalog_*` 系列）——官方文档有描述，但本次沙箱
  环境未建相应 catalog，实机端点上不存在这些指标。

## 6. 已知问题：Dropwizard Timer 分位数恒为 0

所有 Dropwizard Timer（如 `gravitino_server_http_request_duration_seconds`、27 个
`gravitino_relational_store_<op>_total`）导出为 Prometheus `summary` 类型，但实测**全部 6 个分位数**
（0.5/0.75/0.95/0.98/0.99/0.999）**恒为 `0.0`**，且导出器**完全不产生 `_sum` 行**（对应 Doris
`otel_metrics_summary` 表的 `sum` 列恒为 0）。这意味着延迟类面板（G10）只能依赖 `count`（请求次数），
无法得出真实延迟数值。

这是 Gravitino / Dropwizard-to-Prometheus 导出器本身的问题，**不是 Datasophon 采集链路的 bug**。
用户已决定：监控面板照常做（用 count 拆分），分位数字段先写上，若沙箱验证时仍为 0，问题记录在案，
留给后续 Gravitino 工程侧排查修复，本次不处理。

## 7. 现场证据

> **待集成阶段回填**。以下小节是占位标题，不代表已发生的验证结果——本文档编写时仅完成了指标清单的实测
> 与面板契约设计，尚未做逐面板现场核对。负责集成的人在真实沙箱验证后回填本章节。

### 7.1 截图

（待回填）

### 7.2 SQL 交叉验证结果

（待回填）

### 7.3 operation 分组验证结果

（待回填，依赖后端 `operation` 属性白名单改动落地）

### 7.4 延迟面板复测结果

（待回填，核实 G10 及 relational store 延迟指标的分位数/`_sum` 是否仍恒为 0）
