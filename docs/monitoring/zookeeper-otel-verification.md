# ZooKeeper 指标接入验证（OTel Collector → Doris）

> 用途：核对 ZooKeeper 监控看板所需指标的落表、维度与存在性。
> **2026-07-04 已用 `deploy/compose/docker-compose.observability.yml`（新增 `obs-zookeeper` 服务 + otelcol
> `prometheus/zookeeper` 抓取任务）搭建真实沙箱跑通，本文档记录的是真实观测结果，不是待回填占位。**

## 沙箱环境

- `zookeeper:3.8` 单节点（`ZOO_STANDALONE_ENABLED=true`），`ZOO_CFG_EXTRA` 注入
  `metricsProvider.className=org.apache.zookeeper.metrics.prometheus.PrometheusMetricsProvider` +
  `metricsProvider.httpPort=7000`，与生产 `ZOOKEEPER/service_ddl.json` 的
  `metricsProvider.httpPort` 默认值一致。
- otelcol `prometheus/zookeeper` 抓取任务 `job_name: ZkServer`，与生产
  `OtelScrapeConfigBuilder`（`job_name = role.getServiceRoleName()` = `"ZkServer"`）一致。
- 用 `zkCli.sh create/set/delete` 持续写入 znode，制造真实 fsync/session/packet 流量。

## Step 1 — ZkServer 是否写入 Doris（已确认）

```sql
SELECT COUNT(*) FROM otel.otel_metrics_gauge WHERE service_name = 'ZkServer';
SELECT COUNT(*) FROM otel.otel_metrics_sum WHERE service_name = 'ZkServer';
SELECT COUNT(*) FROM otel.otel_metrics_summary WHERE service_name = 'ZkServer';
```

**真实结果**：gauge 表 7000+ 行、sum 表 4000+ 行均正常写入；summary 表在未过滤前**长期为 0 行**——
见下方「⚠️ 采集侧发现的真实缺陷」。加白名单 relabel 隔离掉恒为 `NaN` 的无关指标后，summary 表恢复写入
（`fsynctime`/`snapshottime`/`jvm_gc_collection_seconds` 三个指标共 4 行）。

## Step 2 — 指标落表与类型核对（已确认，逐条实测 `/metrics` 端点 `# TYPE` 行 + Doris 落表）

**关键结论：本任务清单最初的"已拍板决策"里有两处推断被真实数据证伪，已按下表修正代码：**

|                                                 指标                                                  |  最初假设的落表   |                                **真实落表**                                 |                                    结论                                    |
|-----------------------------------------------------------------------------------------------------|------------|-------------------------------------------------------------------------|--------------------------------------------------------------------------|
| `jvm_gc_collection_seconds_count`                                                                   | `summary`✅ | `summary`（metric_name 为不带 `_count` 后缀的 **`jvm_gc_collection_seconds`**） | 落表正确，**metric_name 原假设错**（多写了 `_count` 后缀），已修正                           |
| `election_time_sum`                                                                                 | `sum`❌     | `summary`（metric_name 为 **`election_time`**，非 `_sum` 后缀）                | **假设错**，真实 `# TYPE election_time summary`，已修正为 `table=summary,field=sum` |
| `fsynctime_sum`                                                                                     | `sum`❌     | `summary`（**`fsynctime`**）                                              | 同上，已修正                                                                   |
| `snapshottime_sum`                                                                                  | `sum`❌     | `summary`（**`snapshottime`**）                                           | 同上，已修正                                                                   |
| `jvm_pause_time_ms_sum`                                                                             | `sum`❌     | `summary`（**`jvm_pause_time_ms`**）                                      | 同上，已修正                                                                   |
| `packets_received`/`packets_sent`                                                                   | `sum`❌     | **`gauge`**（`# TYPE packets_sent gauge`）                                | **假设错**（以为是 counter），已修正为默认 gauge，去掉 `table:'sum'`                       |
| `connection_rejected`/`connection_drop_count`/`unrecoverable_error_count`/`digest_mismatches_count` | `sum`✅     | `sum`（真实 `# TYPE ... counter`）                                          | 假设正确                                                                     |
| `commit_count`/`snap_count`/`proposal_count`                                                        | `sum`✅     | `sum`（真实 `# TYPE ... counter`）                                          | 假设正确                                                                     |
| 其余全部 gauge（`quorum_size`/`jvm_threads_*`/`max_latency` 等）                                           | `gauge`    | `gauge`                                                                 | 假设正确                                                                     |

**已按此表修正** `datasophon-ui-v2/.../ZooKeeperMonitor/panelQueries.ts`：Z13 去掉 `table:'sum'`；
Z16/Z19/Z20/Z23 的 `metric` 去掉 `_sum` 后缀、`table` 改 `summary`、加 `field:'sum'`；Z22 的 `metric` 去掉
`_count` 后缀。教训：**"看起来像 Counter 语义" ≠ "真实 Prometheus TYPE 是 counter"**，必须实测
`/metrics` 端点的 `# TYPE` 行，不能凭指标名字面意思推断。

## Step 3 — 属性维度（已确认）

```sql
SELECT service_name, metric_name, count, sum, CAST(attributes AS CHAR) AS attrs
FROM otel.otel_metrics_summary WHERE service_name='ZkServer';
```

真实结果：

```
ZkServer  snapshottime                1     5      {}
ZkServer  jvm_gc_collection_seconds   0     0      {"gc":"G1 Old Generation"}
ZkServer  jvm_gc_collection_seconds   2     0.021  {"gc":"G1 Young Generation"}
ZkServer  fsynctime                   1101  431    {}
```

|                               检查项                                |                                      真实结论                                       |
|------------------------------------------------------------------|---------------------------------------------------------------------------------|
| `jvm_memory_pool_bytes_used` 的 `attributes` 含 `pool`             | ✅确认（`pool="G1 Eden Space"` 等）                                                   |
| `jvm_gc_collection_seconds` 的 `attributes` 含 `gc`                | ✅确认（如上）                                                                         |
| `instance` 位于 `resource_attributes['service']['instance']['id']` | ✅确认（值为 `obs-zookeeper`）                                                         |
| `job`/`service_name` 对应 `ZkServer`                               | ✅确认（`resource_attributes['service']['name']` 与顶层 `service_name` 列均为 `ZkServer`） |

## Step 4 — SQL 层端到端验证（已确认，绕过 REST 层直接对 Doris 跑生成的 SQL）

手工拼出 `OtelMetricsQueryService.buildRangeFieldRateSql("sum", ..., "otel_metrics_summary")` 生成的
SQL 模板（`metric='fsynctime'`），代入真实 start/end/step/rateWindow 直接对 Doris 执行：

```
instance        job       bucket       value
obs-zookeeper   ZkServer  1783153620   1.466678892963017
```

以及 `groupBy=['gc']` 版本（`metric='jvm_gc_collection_seconds', field='count'`）：

```
instance       job       gc                   bucket       value
obs-zookeeper  ZkServer  G1 Old Generation     1783153620   0.0000
obs-zookeeper  ZkServer  G1 Young Generation   1783153620   0.0000
```

结论：**`buildRangeFieldRateSql` 对 summary 表的 series_key 分区 + reset 守卫 + groupBy 逻辑正确**，
`pool`/`gc` 白名单生效，`gc` 维度正确拆分成独立 series。GC rate 全为 0 属预期（沙箱运行期内只发生过
1 次 Young GC，验证窗口内无新增收集事件）。

## ⚠️ 采集侧发现的真实缺陷（超出本任务范围，需团队决策，未修复）

**`otel_metrics_summary` 表的 Stream Load 写入会被同批次里任意一个 `NaN` 值的 datapoint 整体拖垮。**

ZooKeeper 内部有约 45 个 processor 队列类 Summary 指标（`prep_processor_queue_size`、
`proposal_latency`、`election_time`、`jvm_pause_time_ms` 等）在从未被观测到时会输出
`{quantile="0.5",} NaN`（Prometheus Summary 无观测值时的标准行为）。otelcol 的 dorisexporter 把这类
NaN datapoint 序列化进 Stream Load 的 JSON payload 时报
`"error": "json: unsupported value: NaN"`，导致**整个 `otel_metrics_summary` 表的这次 Stream Load
请求失败**（`gauge`/`sum` 表因走独立 Stream Load 请求不受影响，已实测确认）。

实测现象：未过滤白名单前，`otel_metrics_summary` 表对 `ZkServer` 长期 0 行，即使
`jvm_gc_collection_seconds`（本身从不产生 NaN，因为它的 Summary 未配置任何 quantile 分位数）也被
连带拖累无法落库。加 `metric_relabel_configs` 白名单只保留会产生真实数值的指标后，summary 表才恢复写入。

**影响面**：
1. `election_time`（Z16）在单节点/无选举场景下**几乎不可能有非 NaN 值**——任何长期稳定运行、选举窗口
已滑出 Summary 统计窗口的 ZK 集群，只要该指标一变回 NaN，整个 summary 表当次批次的写入就会失败，
包括本该正常写入的 `fsynctime`/`snapshottime`/`jvm_gc_collection_seconds`。
2. `jvm_pause_time_ms`（Z23）同理——只要该 ZK 节点近期没有可观测的 JVM 暂停，也会永久 NaN。
3. 检查 `datasophon-worker/.../templates/otelcol.ftl`（生产 collector 配置模板）**同样没有任何
NaN 处理逻辑**，说明这不是本沙箱独有问题，是该 dorisexporter 版本（v0.154.0）的通用缺陷，
**生产环境接入任何"低频 Summary 指标"的服务都可能撞上**（不只是 ZooKeeper）。

**建议（未执行，需团队拍板）**：在 `otelcol.ftl` 的 metrics pipeline 里加一个 `transform` 处理器，
把 Summary datapoint 里的 NaN quantile 值替换成 0 或直接丢弃该 quantile 点（保留 count/sum 列不受影响，
因为 count/sum 本身永远是有效数值，只有 quantile 估计值在零观测时才会是 NaN）。这是 collector 配置层面
的通用修复，不属于本次 ZooKeeper 前后端迁移的范围，本任务的 Java/前端代码改动均按"数据一旦落库、查询
SQL 正确"验证完毕，**不受此采集侧缺陷影响其正确性**——一旦团队在 collector 层修好 NaN 问题，
Z16/Z19/Z20/Z22/Z23 无需再改代码即可恢复数据。

## 已知局限（沙箱层面，非代码缺陷）

- `quorum_size`（Z01）、`leader_uptime`（Z02）、`learners`/`synced_observers`（Z17）在
  `ZOO_STANDALONE_ENABLED=true` 单节点模式下**完全不存在**（这些是仅复制/quorum 模式才有的指标，
  standalone 模式跳过 Leader 选举）。真实多节点集群应有数据，本沙箱无法验证，需要真实 3+ 节点 ensemble。

