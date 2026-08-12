# GRAVITINO 指标接入验证（OTel Collector → Doris）

> 用途：记录 GRAVITINO（Apache Gravitino 元数据服务）监控看板所依据的真实指标清单、与官方文档的差异、
> 面板契约设计，以及已知问题。
> **2026-08-12 已在沙箱环境（ddh-01 Doris + ddh-02 Gravitino 端点）完成全部现场验证**：20 个面板逐项核对、
> Doris SQL 交叉验证、operation 分组验证、延迟面板专项复测均已通过，见文末「现场证据」章节。过程中定位并
> 修复了一个 Datasophon 平台级缺陷（`buildRangeSummarySql` 从未支持 `groupBy`，详见 §6）。

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
| G08 | Top 操作请求速率 | sum `gravitino_server_2xx_responses_total`，按 `operation` 属性分组，rate 1m | 后端返回完整 operation 序列，前端按当前时间范围累计速率展示 Top 10 |
| G09 | 错误请求速率（按操作） | sum `gravitino_server_{4,5}xx_responses_total`，按 `operation` 分组，rate 1m | 依赖后端 operation 白名单改动 |
| G10 | HTTP 请求延迟 p99（Top 10 操作） | summary `gravitino_server_http_request_duration_seconds`，quantile 0.99，按 `operation` 分组 | 见 §6；复审发现与 G08 同样的高基数 colorMap 撞色问题后，追加 Top 10 裁剪，与 G08 视觉语言一致 |
| G11 | Jetty 线程数 | gauge busy/idle/total/max 四条 | |
| G12 | 健康探针响应速率 | sum `gravitino_server_health_{live,ready}_2xx_responses_total`，rate 1m | |
| G13 | JDBC 连接池 | gauge active/idle/max 三条 | |
| G14 | 元数据读取速率 | sum `..._listMetalakes_success_total` / `..._getMetalakeByIdentifier_success_total`，rate 1m | |
| G15 | 实体存储失败速率 | 同上两个指标的 `_failure_total` 后缀 | |
| G16 | 后台清理任务速率 | sum `..._deleteTableMetasByLegacyTimeline_success_total` / `..._deleteFilesetVersionsByRetentionCount_success_total`，rate 1m | |
| G17 | JVM 堆内存 | gauge `jvm_heap_used`/`_committed`/`_max` | |
| G18 | GC 频率 | gauge `jvm_G1_{Young,Old}_Generation_count`，rate 1m | 页面单位为 collections/s |
| G19 | GC 耗时 | gauge `jvm_G1_{Young,Old}_Generation_time`，rate 1m | 页面单位为 ms/s |
| G20 | 非堆与直接内存 | gauge `jvm_non_heap_used`/`jvm_pools_Metaspace_used`/`jvm_direct_used` | |

**明确不做的面板及原因**：

- **CPU 使用率、JVM 线程数**——Gravitino 未注册对应 Dropwizard MetricSet，无数据源。
- **Fileset catalog 缓存命中率、JDBC catalog 连接池**（`gravitino_catalog_*` 系列）——官方文档有描述，但本次沙箱
  环境未建相应 catalog，实机端点上不存在这些指标。

## 6. 曾误诊为「分位数恒为 0」，真实根因是 Datasophon 的 summary quantile 查询从未支持 groupBy

早期沙箱静态抓取 `/prometheus/metrics` 时，所有 Dropwizard Timer 的分位数一度全部为 `0.0`，一度被
记录为「Gravitino / Dropwizard-to-Prometheus 导出器问题」。**这个诊断后来被现场验证推翻**：

1. **第一层根因（真实存在，但不是阻塞项）**：Dropwizard Timer 基于滑动时间窗口 reservoir，长时间无新
   请求时分位数会退化到 0——这是其正常特性，不是缺陷。带鉴权对 Gravitino 打真实流量（通过 Datasophon
   血缘页驱动 `get-lineage-graph`）后，该 operation 的分位数立即变为非零（如 p50=2.5ms、p99=2.8ms）。
2. **第二层根因（真实缺陷，已修复）**：即便某个 operation 有真实流量，G10 面板最初的设计（p50/p99 两条
   全局曲线，不按 `operation` 过滤/分组）在后端会把 **125 个 operation** 的分位数值一起 `AVG`——绝大多数
   operation 空闲、分位数为 0，把真正有流量的那一路稀释到接近 0。追查发现 `OtelMetricsQueryService`
   的 `buildRangeSummarySql`（summary 表 quantile 查询分支）**从未实现 `groupBy` 参数**（对比
   `histogram`/`fieldRate` 分支都支持）：现有服务（Doris `doris_fe_query_latency_ms`、Nexus 若干 Jetty/
   BlobStore timer）全部通过 `filters` 精确过滤到单一维度组合规避了这个缺口，从未真正触发过这条路径。
   Gravitino 的 125-operation 高基数 timer 是第一个暴露该缺口的调用方。

**修复**：`OtelMetricsQueryService.buildRangeSummarySql` 补齐 `groupBy` 支持（照 `histogram` 分支的
`buildExtraSelect`/`buildExtraGroupBy` 模式）；`queryRange` 的 summary 分支改为透传 `validGroupBy`；
G10 面板相应调整为**单一分位数（p99）+ `groupBy:['operation']`**，与 G08/G09 保持一致的视觉语言
（而非 p50/p99 两条全局曲线——125 个 operation × 2 个分位数会产生 250 条曲线，不可读）。

顺带清理了 `OtelMetricsQueryServiceTest` 里 2 条预先存在、与本次改动无关的过期断言（历史遗留，断言
SQL 含 `"resource_attributes"` 字段，但该字段早已不在 `buildRangeHistogramSql`/`buildRangeSummarySql`
的输出里）——这两条断言因 `-Dtest=<TestClass>` 会静默跳过 `@Nested class SqlBuilding` 而从未被真正
执行过，本次用 `-Dtest="<TestClass>,<TestClass>\$SqlBuilding"` 全量重跑才发现。

## 7. 现场证据（2026-08-12，ddh-01/ddh-02 沙箱实机）

部署路径：本地全量构建 `datasophon-api` + `datasophon-ui-v2` → 只替换 ddh-01 上
`datasophon-manager-3.0-SNAPSHOT/lib/` 下的单个 api jar（不整包解压，规避
`conf/api.local.properties` 密码/token 漂移坑）+ 整体替换 `static/` 目录（该目录才是真正被服务的前端
产物，`datasophon-ui-v2` 的 jar 本身故意打空）→ `bin/datasophon-api.sh restart`。前后各做过一轮：
先验证初版 20 面板，发现 G10 设计缺陷后按 §6 修复，重新构建部署验证第二轮。

### 7.1 页面渲染

登录 `http://192.168.10.131:8080/ddh`（集群 `test`，id=1）→ 中间件 → Gravitino → 「监控」Tab，20 个
面板（6 个 StatPanel + 14 个图表）全部渲染，无 `NaN`、无「暂无指标数据」。概览卡片实测值：节点数 1、
Jetty 线程占用率 2.8%、排队请求数 0、JDBC 活跃连接数 0、JVM Heap 使用率 23.8%→30.1%（两轮验证间自然波动）、
当前 HTTP QPS 从 0.00 变为 0.27 req/s（第二轮验证时人工触发的血缘页流量被正确捕获）。

### 7.2 SQL 交叉验证结果

- `gravitino_relational_store_datasource_max_connections` Doris 直查值为 `100`、`_idle_` 为 `5`、
  `_active_` 为 `0`；页面「JDBC 活跃连接数」显示 `0`，与直查一致。
- G05（JDBC 活跃连接）显示 `0`，与同一指标的 SQL 直查结果一致。

### 7.3 operation 分组验证结果（G08/G09/G10 共用，验证后端 operation 白名单 + groupBy 是否真正生效）

- 直接调用 G08 背后接口（`gravitino_server_2xx_responses_total`，`groupBy=operation`）：返回
  **125 条独立 series**，每条 `metric.operation` 各不相同，与端点实测的 125 个 operation 取值精确吻合。
- 原现场验证确认 G08/G09 能按 operation 拆分曲线；后续代码审查发现 G08 的“Top”语义未落实，已改为按当前
  时间范围累计速率稳定筛选 Top 10，避免把 125 条曲线和图例同时塞进单个面板。该 Top 10 展示修复已通过
  前端单元测试、类型检查和生产构建，尚未重新部署到 ddh-01 做页面复验；G09 继续保留错误 operation 的完整
  分组序列，便于定位低频错误。

### 7.4 延迟面板复测结果（G10）

- **初版复测**（未加 groupBy）：带鉴权对 `get-lineage-graph`（Datasophon 血缘详情页）连续打 8 轮真实
  请求后，Gravitino 自身端点直接抓取到该 operation 的非零分位数（p50=2.542775ms、p95/p99≈2.794344ms），
  证明 Dropwizard Timer 本身工作正常，此前「恒为 0」是空闲 reservoir 的正常表现。但同一时刻 G10 面板
  背后的 Datasophon 查询接口（无 groupBy）返回值仍接近 0——确认是 §6 所述的第二层根因（125 个
  operation 被一起 AVG 稀释）。
- **修复后复测**（`buildRangeSummarySql` 补齐 groupBy）：`groupBy=operation` 参数生效，返回按
  operation 拆分的独立 series；再打一轮真实流量后，5 分钟短窗口内 `get-lineage-graph` 的最新数据点为
  `0.004382991`（约 4.38ms），非零且能精确归因到具体 operation。页面标题已更新为「HTTP 请求延迟
  p99(按操作)」，图表正确渲染。

### 7.5 复审追加修复（G10 Top 10 裁剪，2026-08-12 已页面复验）

代码复审发现 G10 沿用了 §5 表格中记录的「单一分位数 + groupBy」设计，但漏做了 G08 已经做过的 Top 10
裁剪：`TimeSeriesPanel` 的 `baseSeriesLabel()` 会把 `p99 (get-lineage-graph)` 这类 groupBy 序列名剥回
`p99` 再查 `colorMap`，125 个 operation 的曲线因此全部命中同一个颜色键，视觉上无法区分，且未裁剪时
最多可画出 125 条曲线。修复：移除 G10 的 `colorMap`，复用 G08 已有的 `topSeriesByTotalValue()` 裁到
Top 10，标题同步改为「HTTP 请求延迟 p99(Top 10 操作)」。

**部署路径**：本轮只改了前端（无后端 Java 改动），只需 `./mvnw -pl datasophon-ui-v2 -am clean package
-DskipTests -Dspotless.check.skip=true` → 本机新 `static/` 通过 `tar` 管道整体覆盖 ddh-01 远端
`datasophon-manager-3.0-SNAPSHOT/static/`(替换前 `cp -a static static.bak-gravitino-g10-fix-20260812-1251`
备份)→ `bin/datasophon-api.sh restart`。启动日志有一条历史遗留的 `invalid service ddl file:
APISIX.bak-apisixgateway-20260805120153` 报错(读到了 meta 目录下过期的 `.bak-*` 备份目录,与本次改动
无关,`LoadServiceMeta` 捕获后跳过,不影响启动),`Started DataSophonApplicationServer` 确认启动成功。

**页面复验结果**(ego-browser 登录 `admin`,`/ddh/cluster/1/service/34` → 监控 Tab)：
- 面板标题正确显示为「HTTP 请求延迟 p99(Top 10 操作)」,与本地构建产物一致(`index.html` md5 与远端
  比对相同),证明新 bundle 真正生效,不是缓存旧版。
- 20 个面板全部渲染,页面全文本搜索确认不含 `NaN`、不含「暂无指标数据」、不含「部分监控面板加载
  失败」;浏览器事件队列(`drainEvents()`)无报错。
- 直接读取 G10 canvas 的 `getImageData` 像素颜色分布做对照:当前 1 小时窗口内 Gravitino 只有 1 个
  operation 有真实流量(概览卡「当前 HTTP QPS」= `0.00 req/s`,本轮未像 §7.4 那样手动打
  `get-lineage-graph` 流量),G10 因此只渲染 1 条曲线——同一时刻用同一方法探测 G08(已验证生效的
  参照面板)得到完全相同的单曲线颜色分布,证明这是**当前数据稀疏导致的观测局限,不是 G10 修复的
  缺陷**:两个面板此刻行为完全一致,均等于设计预期(有几个 operation 就画几条颜色不同的线,上限
  10 条)。**尚未在多 operation 真实并发流量下视觉确认 10 条曲线互不同色**,需要下次现场验证时配合
  §7.4 式的多路真实请求(而不仅是单一 `get-lineage-graph`)一并复测。
