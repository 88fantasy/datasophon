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

## ⚠️ 采集侧发现的真实缺陷（已修复，见下方两个 ✅ 章节）

**`otel_metrics_summary` 表的 Stream Load 写入会被同批次里任意一个 `NaN` 值的 datapoint 整体拖垮。**

ZooKeeper 内部有约 45 个 processor 队列类 Summary 指标（`prep_processor_queue_size`、
`proposal_latency`、`election_time`、`jvm_pause_time_ms` 等）在从未被观测到时会输出
`{quantile="0.5",} NaN`（Prometheus Summary 无观测值时的标准行为）。otelcol 的 dorisexporter 把这类
NaN datapoint 序列化进 Stream Load 的 JSON payload 时报
`"error": "json: unsupported value: NaN"`，导致**这次批次导出请求整体失败并重试**。

**⚠️ 2026-07-04 二次实测更正**：最初观察窗口较短，误判为"gauge/sum 表因走独立 Stream Load 请求不受
影响"。**实际是同一条 `metrics` pipeline（`prometheus/juicefs`+`prometheus/zookeeper`+`prometheus/doris`+
`otlp` 四个 receiver 共用一个 `batch` 处理器 + 一个 `doris` exporter）**，NaN 导出失败触发
`retry_on_failure` 重试，重试 backoff 持续增长（实测从数秒涨到 40+ 秒），`sending_queue`
（`num_consumers:4, queue_size:500`）逐渐被阻塞的重试请求占满后，**gauge/sum 表的新数据最终也会
停止写入**——本次调试会话中实测 `num_alive_connections`（纯 gauge,与 summary 无关）数据陈旧到
980 秒（远超代码 300 秒新鲜度窗口），触发下方 NPE 报错。`docker compose restart obs-otelcol`
可临时恢复,但因 NaN 源头指标（`election_time`/`jvm_pause_time_ms` 等）持续产生,**重启后几分钟内
会再次堵塞**,不是一次性修复。

**影响面**：
1. `election_time`（Z16）在单节点/无选举场景下**几乎不可能有非 NaN 值**——任何长期稳定运行、选举窗口
已滑出 Summary 统计窗口的 ZK 集群，只要该指标一变回 NaN，就会触发上述连锁反应，最终拖累整条 pipeline
（含 gauge/sum 表）的新数据写入，不只是 summary 表。
2. `jvm_pause_time_ms`（Z23）同理——只要该 ZK 节点近期没有可观测的 JVM 暂停，也会永久 NaN。
3. Doris 自身指标 `doris_be_tablet_version_num_distribution` 的 `'le' label missing` 接收侧报错是另一个
独立诱因（Doris histogram 格式问题，与 ZK 无关），同样会拖累同一条共享 pipeline。
4. 检查 `datasophon-worker/.../templates/otelcol.ftl`（生产 collector 配置模板）**同样没有任何
NaN 处理逻辑**，说明这不是本沙箱独有问题，是该 dorisexporter 版本（v0.154.0）的通用缺陷，
**生产环境接入任何"低频 Summary 指标"的服务都可能撞上**（不只是 ZooKeeper）。

## ✅ 2026-07-04 已部分修复：`filter/drop_empty_summary` 处理器

在 `deploy/compose/conf/otelcol-juicefs.yaml`（沙箱）和
`datasophon-worker/.../templates/otelcol.ftl`（生产模板，两处均已提交）的 metrics pipeline 里加了：

```yaml
processors:
  filter/drop_empty_summary:
    metrics:
      datapoint:
        - 'metric.type == METRIC_DATA_TYPE_SUMMARY and count == 0'
```

**已用 otelcol 自身暴露的 `otelcol_processor_filter_datapoints_filtered` self-metric 验证生效**
（沙箱实测持续增长，几分钟内过滤掉 150+ 个空 Summary 数据点）。覆盖"Summary 从未被观测到、
count=0、quantile 恒为 NaN"这一种成因。

**未能覆盖的第二种成因（尝试过两种方案均失败，已放弃，如实记录）**：`election_time`/
`fsynctime`/`snapshottime`/`jvm_pause_time_ms` 即使 `count>0`（历史上真实被观测过），quantile
计算用滑动时间窗口衰减，窗口内长时间无新观测时 quantile 仍会衰减回 NaN——`count==0` 过滤不到
这种情况（沙箱实测 `fsynctime` count=1579、sum=671 仍输出 NaN）。尝试过：

1. `transform` 处理器 `set(quantile_values, [])`——otelcol 正常启动、无报错，但用 `debug` exporter
   `verbosity: detailed` 抓包验证发现 quantile_values 并未被清空，`set()` 对这个 slice-of-struct
   字段静默无效。
2. `filter` 处理器用索引 `quantile_values[0].value != quantile_values[0].value`（NaN 自比较技巧）
   在条件里探测——otelcol **直接拒绝启动**，报错 `"the keys indexing quantile_values were not
   used by the context - this likely means you are trying to index a path that does not support
   indexing"`，确认这个 collector 版本的 OTTL 不支持对 `quantile_values` 做索引访问。

结论：**当前 OTTL（otelcol-contrib v0.154.0）不支持读写 Summary 的 `quantile_values` 字段**，
这个衰减性 NaN 无法用"清空/改写 quantile"这类值级别手段可靠解决。

## ✅ 2026-07-04 最终修复：整体丢弃这 4 个指标，不导入 Doris

值级别处理走不通后，改为更简单的指标级别丢弃：`election_time`/`fsynctime`/`snapshottime`/
`jvm_pause_time_ms` 这 4 个指标本身在 ZK 看板里就只服务 Z16/Z19/Z20/Z23 四个面板，用户拍板直接让
这 4 个指标不进 Doris，从根上避免它们拖累同一 pipeline 里其它指标的导出。在
`deploy/compose/conf/otelcol-juicefs.yaml`（沙箱）和 `datasophon-worker/.../templates/otelcol.ftl`
（生产模板，两处均已提交）新增一个 `metric` 级别（而非 `datapoint` 级别）的 filter 处理器：

```yaml
processors:
  filter/drop_zk_decaying_summary:
    metrics:
      metric:
        - 'name == "election_time" or name == "fsynctime" or name == "snapshottime" or name == "jvm_pause_time_ms"'
```

这个条件只判断 `metric.name`，不涉及 `quantile_values` 字段，因此不受上面提到的 OTTL 限制影响，
写法也比值级别处理简单得多。

**验证结果（2026-07-04，`docker compose restart obs-otelcol` 后）**：
- self-metric `otelcol_processor_filter_datapoints_filtered{filter="filter/drop_zk_decaying_summary"}`
从 0 持续增长（观测到 6+），证明规则确实在拦截数据点。
- Doris `otel_metrics_summary` 表里 `fsynctime`/`snapshottime` 的最后一条记录时间戳停留在重启前
（`08:28:05`），重启后再无新记录写入；`election_time`/`jvm_pause_time_ms` 表里本就没有任何历史
记录（此前一直是 NaN，被 `filter/drop_empty_summary` 挡在门外）。
- 重启后的 otelcol 日志里不再出现新的 `"unsupported value: NaN"` 报错（重启瞬间那条来自旧进程
shutdown 时刷已入队数据，与新配置无关）。

**产品侧代价（已知且是本次修复的直接后果）**：`election_time`/`fsynctime`/`snapshottime`/
`jvm_pause_time_ms` 从采集侧就被丢弃，根本不会落库，对应的 Z16/Z19/Z20/Z23 四个面板**已从
`datasophon-ui-v2` 的 ZooKeeper 看板里整体移除**（`panelQueries.ts`/`index.tsx`/locale 文件/mock
数据均已同步删除，详见对应 commit）——不留"看起来能查但永远无数据"的面板，比留着更诚实。这四个
面板背后的 SQL 查询层代码（`buildRangeFieldRateSql`）逻辑本身没有问题，只是没有数据源；如果未来
升级 OTel Collector 让 OTTL 支持操作 `quantile_values`，或团队决定换用其它方式处理 NaN，去掉采集侧
这一条 filter 规则、把面板加回前端即可恢复，Java 查询层代码无需改动。
`Z01/Z02/Z17`（quorum 专属指标）不受影响，仍需要真实多节点 ensemble 才能验证。

## 附:调试会话中发现并修复的 NPE（与采集侧 NaN 缺陷相关联但独立成因）

**现象**（用户在 IDEA 本地调试 `datasophon-api` 连接本沙箱 Doris 时触发）：

```
NullPointerException: Cannot invoke "java.lang.Number.doubleValue()" because the return value of
"java.util.Map.get(Object)" is null
  at OtelMetricsQueryService.buildVector(OtelMetricsQueryService.java:744)
  at OtelMetricsQueryService.queryInstant(...)
```

**根因**：`buildInstantAggSql` 生成 `SELECT SUM(value) AS value, ... FROM (子查询) t`（无 `GROUP BY`），
这类无分组聚合查询即使子查询命中 0 行，仍会返回**恰好一行**、`value` 列为 SQL `NULL`
（`SUM(空集) = NULL`，不是 `0`）。触发条件是查询窗口（`evalTime - 300` 秒）内没有该指标的新鲜数据——
本次是上文"采集侧 NaN 缺陷"导致 otelcol 的 Doris 导出队列被拖慢，`num_alive_connections` 980 秒未更新
（远超 300 秒新鲜度窗口），并非 ZooKeeper 迁移代码本身的缺陷。

**修复**：`buildVector`/`buildMatrix` 遇到 `row.get("value") == null` 时跳过该行（视同"无数据"，
与项目既有约定"空结果按 NaN 处理，非真实 0"一致），而不是直接拆箱抛异常。**这是
`OtelMetricsQueryService` 的通用健壮性修复，影响所有走 instant-agg 查询路径的看板（不限于
ZooKeeper）**——任何指标在评估窗口内暂时无新鲜数据时都会触发同样的 NPE，此前未被发现是因为此前的
沙箱验证窗口内数据一直新鲜。已加 `buildVector_nullValue_skipsRowInsteadOfThrowing` +
`buildMatrix_nullValue_skipsRowInsteadOfThrowing` 两个单测覆盖。

## 已知局限（沙箱层面，非代码缺陷）

- `quorum_size`（Z01）、`leader_uptime`（Z02）、`learners`/`synced_observers`（Z17）在
  `ZOO_STANDALONE_ENABLED=true` 单节点模式下**完全不存在**（这些是仅复制/quorum 模式才有的指标，
  standalone 模式跳过 Leader 选举）。真实多节点集群应有数据，本沙箱无法验证，需要真实 3+ 节点 ensemble。

