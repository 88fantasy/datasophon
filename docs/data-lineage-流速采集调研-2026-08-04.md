# Spark 任务流速/吞吐采集调研（2026-08-04）

> 本文是一次调研对话的沉淀，不是实施方案——下个 session 接手时先读完本文，
> 再决定要不要立项、立项走哪条技术路线。所有结论都标了验证方式，区分
> "反编译/grep 实测坐实" 和 "凭已知知识推断，未逐条验证"。

## 0. 起因

在 [[docs/session-handoff-lineage-token-auth-2026-08-03.md]] 完成 OpenLineage 404 修复后，
用户追问：现有血缘链路能不能看到 Spark 任务的流速/吞吐（每秒行数、每秒流量）。调研过程中
发现这不只是"加个字段"的小事，而是牵出了三层完全独立的问题：现有链路到底丢没丢这个数据、
OpenLineage 这套机制本身能不能给这个数据、OTel 能不能补上。

## 1. 现状结论：整条血缘链路完全不采集流速/吞吐

**结论本身已 100% 验证**（跨两个仓库、四个层级逐一 grep/读代码确认，不是猜测）：

|                      层级                      |                       检查方式                        |                                                       结论                                                        |
|----------------------------------------------|---------------------------------------------------|-----------------------------------------------------------------------------------------------------------------|
| 原始 OpenLineage 事件                            | 上次调试 404 时抓到的真实事件 `gravitino_lineage.log`         | **有数据**：`outputFacets.outputStatistics = {rowCount, size, fileCount}`                                           |
| Gravitino 摄入（`JdbcLineageStorage.java`）      | 读源码，只有 3 处 `textAt(root, ..., "/xxx/facets/...")` | 只解析 `processing_engine.name`/`jobType.jobType`/`ownership.owners[0].name`，**`outputStatistics` 从未被读取，落库这一步就丢了** |
| 查询模型（`LineageQuery.java`）                    | 逐个 record 字段核对                                    | `NodeMeta`/`LogicalEdge`/`GraphJob`/`JobDetail` 均无 rate/rowCount/size 字段                                        |
| Datasophon 代理（`GravitinoLineageClient.java`） | 读源码                                               | 纯 JSON 透传（`JsonNode`），Gravitino 给什么转发什么，不做字段裁剪也不做补充                                                             |
| 前端                                           | grep `datasophon-ui-v2/src` 全部血缘相关文件              | 零匹配，没有任何速率类 UI 位                                                                                                |

**根因不是实现疏漏，是一次迁移中的功能遗漏**：2026-07-30 定过 D4 决策（见
[[docs/data-lineage-平台级血缘架构-2026-07-29.md]]）——"Flink 走 OTel 真实速率，Spark/DS
走 OpenLineage 自带的 `OutputStatisticsOutputDatasetFacet` 做批次画像"。但这个决策是在血缘
存储还在 Datasophon 自己 MySQL 里时定的；PR #37 把权威存储整体迁到 Gravitino 时，摄入逻辑
重写了一遍（"删本地 ingest/lease/MySQL/Guava/rebuild/metrics"），`OutputStatisticsOutputDatasetFacet`
这部分没有被搬过去。

## 2. OpenLineage 本身的上报模型：事件驱动首尾式，不支持"过程中持续上报"

**已验证**（反编译沙箱在用的 `openlineage-spark_2.12-1.29.0.jar`）：

- `OpenLineageSparkListener` 只重写了 `onApplicationStart/End`、`sparkSQLExecStart/End`、
  `onJobStart/End`、`onTaskEnd` 这几个 Spark 回调。
- 没有 `RUNNING` eventType 的处理逻辑，没有 `Timer`/`ScheduledExecutorService` 字段，
  jar 里**没有** `StreamingQueryListener` 类（这个版本连 Structured Streaming 微批次上报都不支持）。
- `JobMetricsHolder` 只是把 `onTaskEnd` 的行数/字节数**累加进内存**，直到对应 Spark job
  结束才打包进那个 job 的 COMPLETE 事件——中间过程完全不可见。

**实际含义**：粒度取决于"这条 SQL 内部被 Spark 拆成了几个 job"，不取决于时间。一条简单的
`INSERT ... SELECT` 如果只对应一个 Spark job，从开始到结束只有 START/COMPLETE 两个事件，
中间跑多久（哪怕 3 小时）都没有任何中间上报。

**结论**：靠调整 OpenLineage 配置解决不了"长时间任务过程中看流速"这个诉求，这是这套集成
的设计边界，不是配置问题。

## 3. 备选方案：接入 OTel

### 3.1 拉取式（Prometheus scrape）—— 项目已有的标准范式，但有 Spark 特有的坑

项目里 Doris/JuiceFS/ZooKeeper 监控看板都是这个模式（`deploy/compose/conf/otelcol-juicefs.yaml`
里 `prometheus/doris` receiver 就是活的范例）：OTel Collector 定时 scrape 一个 `/metrics` 端点，
落 Doris。Spark 从 3.0 起自带 `spark.ui.prometheus.enabled=true`，挂在 Spark UI 端口
（默认 4040）暴露 `/metrics/prometheus`。

**Spark 特有的坑（其他已迁移服务都没有这个问题）**：Doris FE/BE、ZooKeeper、JuiceFS 都是
**常驻守护进程**，端口固定，scrape target 静态配置一次就行。Spark driver/executor 是
**按应用生命周期存在的短命进程**，端口会因为并发作业抢占而漂移（4040 被占跳 4041、4042...）。
对于用户说的"长时间任务"场景，进程活得够久，只要提交时固定 `spark.ui.port` 或走独占节点，
静态 target 可行；但如果同一节点上有多个并发 Spark 作业，就要么上 Prometheus service
discovery，要么换推送式。

### 3.2 推送式（Graphite/Statsd sink）—— 字段清单已验证

Spark 自带 `GraphiteSink`/`StatsdSink`，本质是把 Dropwizard `MetricRegistry` 里已经注册的
指标按周期（默认 10s，`spark.metrics.conf` 里 `*.sink.graphite.period` 可调）原样推给
Graphite/StatsD 协议端点——**不会新增字段，不做速率换算**。

命名规则：`<namespace>.<组件实例>.<Source>.<字段>`，driver 和每个 executor 各自独立上报。

**已验证的真实字段**（反编译沙箱在用的 `spark-core_2.12-3.5.8.jar` 里 `ExecutorSource`/
`DAGSchedulerSource`/`BlockManagerSource` 三个类的字节码字符串常量，不是文档转述）：

|      分类      |                                                     字段                                                     |                  含义                   |
|--------------|------------------------------------------------------------------------------------------------------------|---------------------------------------|
| I/O 读        | `bytesRead` / `recordsRead`                                                                                | 从数据源读了多少字节/行（累计）                      |
| I/O 写        | `bytesWritten` / `recordsWritten`                                                                          | 写出多少字节/行（累计）                          |
| Shuffle 读    | `shuffleTotalBytesRead` / `shuffleRemoteBytesRead` / `shuffleLocalBytesRead` / `shuffleRecordsRead`        | shuffle 拉取量，分远程/本地                    |
| Shuffle 写    | `shuffleBytesWritten` / `shuffleRecordsWritten`                                                            | shuffle 落盘量                           |
| Shuffle 耗时   | `shuffleFetchWaitTime` / `shuffleWriteTime`                                                                | 拉取/写入耗时                               |
| 文件系统层        | `filesystem.<scheme>.read_bytes` / `write_bytes` / `read_ops` / `write_ops` / `largeRead_ops`              | 按 `hdfs`/`file`/`s3a` 等具体 scheme 分别统计 |
| 溢写           | `diskBytesSpilled` / `memoryBytesSpilled`                                                                  | 内存不够溢写磁盘的量                            |
| 执行耗时         | `cpuTime` / `runTime` / `jvmGCTime` / `deserializeTime` / `deserializeCpuTime` / `resultSerializationTime` | task 各阶段耗时拆分                          |
| 线程池          | `threadpool.activeTasks` / `completeTasks` / `startedTasks` / `currentPool_size` / `maxPool_size`          | 并发度                                   |
| DAGScheduler | `stage.runningStages`/`waitingStages`/`failedStages`、`job.allJobs`/`activeJobs`                            | 整体进度                                  |
| BlockManager | `memory.maxMem_MB`/`remainingMem_MB`/`memUsed_MB`、`disk.diskSpaceUsed_MB`                                  | 内存/磁盘水位                               |

**关键限制：全部是累计计数器，不是速率**。要拿到"每秒行数/每秒流量"必须在 Doris 落库后
按时间窗口做 `(本次值 - 上次值) / 时间差`——这正是 JuiceFS 监控看板已经做过的
"counter 字段级 rate builder" 模式，可以直接复用同一套后端代码。

**未验证、需要下个 session 确认的点**：OTel Collector Contrib 确认有 `statsdreceiver`
能直接接 StatsD 协议；但 Graphite 协议这一侧是否有对应的现成 receiver，**这次没有查证，
不能假设有**——选型前必须先确认。

## 4. 一个容易被忽略、可能推翻整个方案方向的问题：粒度不匹配

**这一点是本次调研最重要的风险点，务必在立项前想清楚**：

`ExecutorSource` 这一整套指标是 **executor 级别的聚合**——一个 executor 在某个时间点
"总共"读了多少字节，不区分这些字节来自哪张表、属于血缘图上哪条边。如果同一个 Spark
应用同时读写多张表（很常见），OTel/Prometheus 这条路径**给不出"这条边的流速"**，只能给出
"这个 Spark 应用/这个 executor 的总吞吐趋势"。

而 D4 决策里原本选中的 `OutputStatisticsOutputDatasetFacet`（OpenLineage 事件自带）恰恰是
**按 dataset 归属**的（`outputs[].outputFacets.outputStatistics`，每个输出表各自一份），
这是它被选中的原因——只是这次调研发现它目前完全没被摄入（见 §1）。

**所以真实的技术选择不是"OpenLineage vs OTel 二选一"，而是两者服务于不同的问题**：

- 想在**血缘图的边上**标"这条边跑了多少行/多快"→ 只能靠补齐 §1 的摄入缺口
  （`JdbcLineageStorage` 解析 `outputStatistics` 并入库），OTel 在这个粒度上帮不上忙。
- 想看**整个 Spark 应用/任务级别**的健康度和吞吐趋势（不区分具体表）→ OTel 抓
  `ExecutorSource` 这条路径是合适的，可以复用项目现成的 Doris 监控看板范式，做成独立的
  "Spark 任务监控" Tab，和血缘图是两个互补但独立的产物。

## 5. 下个 session 的决策点

1. **先决定要解决哪个问题**：血缘边上的流速（需要补 Gravitino 摄入代码），还是 Spark 任务级
   吞吐监控看板（走 OTel，接现成的 Doris 监控范式）？两者工作量、涉及的仓库（前者主要在
   gravitino fork，后者主要在 datasophon）都不一样，不建议混着做。
2. 如果选 OTel 路线：Prometheus 拉取 vs Graphite/StatsD 推送，取决于 Spark 部署形态是否
   长期独占端口/节点；先确认 OTel Collector 对 Graphite 协议的接收能力。
3. 如果选补摄入缺口路线：改 `JdbcLineageStorage.java` 解析 `outputFacets.outputStatistics`，
   `LineageQuery` 的 `GraphJob`/`JobDetail` record 加字段，前端边详情 Drawer 加展示位——
   工作量集中在 gravitino fork，datasophon 侧只需要透传新字段给前端认识。
4. 两条路线都不冲突，如果人力允许可以并行规划，但不要在没有分清"边级"和"任务级"这两个
   目标之前就直接开始写代码。

