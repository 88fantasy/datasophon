# JuiceFS OTel + Doris 监控验证记录

> 目标：验证 JuiceFSMonitor 从 Prometheus 迁移到 OTel Collector → Doris 后，所需指标、标签和 histogram 语义是否满足看板查询。
>
> **当前状态：已在本地沙箱（`deploy/compose/docker-compose.observability.yml`，JuiceFS 1.3.1 +
> Redis + RustFS 后端）跑通全部 4 步验证，结论已回填（2026-07-04）。**

## ⚠️ 核心发现：非 `_total` 后缀的计数器落在 gauge 表，不是 sum 表

沙箱真实数据显示：JuiceFS client 暴露的指标里，只有 **Prometheus 命名带 `_total` 后缀** 的才会被
otelcol 的 prometheusreceiver + dorisexporter 路由进 `otel_metrics_sum` 表（如
`juicefs_fuse_ops_total`、`juicefs_process_cpu_seconds_total`、`go_memstats_*_total`）；**不带
`_total` 后缀的指标一律落 `otel_metrics_gauge` 表，即使其 Prometheus `# TYPE` 声明为 `counter`**
（如 `juicefs_object_request_errors`、`juicefs_object_request_data_bytes`、`juicefs_cpu_usage`、
`juicefs_blockcache_hits/miss/hit_bytes/miss_bytes`）。JuiceFS 全部 panel 用到的计数器类指标均属于
后一类，**JuiceFSMonitor 之前实现（`panelQueries.ts`）里 6 处 `table: 'sum'` 配置全部是错的**，
已改为 `table: 'gauge'` 并重新用真实数据核实（J05/J13/J15/J16/J17 共 6 个 query）。

**教训**：不能仅凭 Prometheus `# TYPE` 声明推断 OTel 落表位置，必须用真实采集数据核实；本文档
Step 4 最初设计的"预期"列同样基于这个错误假设，已一并修正。

## ⚠️ 第二个发现：`buildRangeHistogramSql` 带 groupBy 时报 "列名 is ambiguous"（真实报错，已修复）

沙箱上线后端到端联调时，J09（IO Latency，`groupBy: ['mp']`）实测触发后端报错：

```
PreparedStatementCallback; uncategorized SQLException ... errCode = 2,
detailMessage = mp is ambiguous: mp#74, mp#82.
```

**根因**：`OtelMetricsQueryService.buildRangeHistogramSql` 的 `exploded` CTE 里
`curr_exploded c LEFT JOIN prev_exploded p`，两边都投影了 groupBy 额外列（如 `mp`），但外层
`SELECT` 直接写裸列名 `mp`（未加 `c.`/`p.` 限定），Doris 无法判断取哪边。**此前所有 histogram
分位数看板（RustFS/Doris/Nexus）调用 `buildRangeHistogramSql` 时均未传 `groupBy`，这条代码路径
从未被真实触发过**——JuiceFS J09 是第一个传 `groupBy` 给该方法的调用方，属于典型"共享查询层潜伏
bug，只有新调用形态才触发"。

**修复**：新增 `buildExtraColsQualified(keys, alias)` 辅助方法，`exploded` CTE 的 SELECT 列表改用
`c.mp AS mp`（显式限定 + 别名），JOIN ON 子句本身不受影响（`buildJoinCols` 早已正确限定）。已用
真实 Doris 数据验证修复后 SQL 正常返回 p50 延迟值（约 0.1ms 量级，符合本地 FUSE + 块缓存读的预期）。
新增回归断言 `rangeHistogram_withGroupBy_addsExtraDimension` 检查 `c.type AS type` 出现在 SQL 里，
后端 69 个相关测试全绿。**其余共享查询层调用方（RustFS/Doris/Nexus）不受影响，因为它们从未触发
`buildRangeHistogramSql` 的 groupBy 分支**——但如果未来任何看板给 histogram 分位数查询加 groupBy，
会自动受益于这次修复。

## Step 1：确认三类指标表有数据

```sql
SELECT COUNT(*) AS gauge_rows
FROM otel.otel_metrics_gauge
WHERE service_name = 'JuicefsMount';

SELECT COUNT(*) AS sum_rows
FROM otel.otel_metrics_sum
WHERE service_name = 'JuicefsMount';

SELECT COUNT(*) AS histogram_rows
FROM otel.otel_metrics_histogram
WHERE service_name = 'JuicefsMount';
```

**结论：✅ 三张表均有数据**（沙箱实测：gauge 276 行 / sum 84 行 / histogram 27 行，单次挂载 + 持续
读写压流约 20s 采集窗口）。

## Step 2：枚举真实 metric_name

```sql
SELECT 'gauge' AS table_name, metric_name, COUNT(*) AS rows
FROM otel.otel_metrics_gauge
WHERE service_name = 'JuicefsMount'
GROUP BY metric_name
UNION ALL
SELECT 'sum' AS table_name, metric_name, COUNT(*) AS rows
FROM otel.otel_metrics_sum
WHERE service_name = 'JuicefsMount'
GROUP BY metric_name
UNION ALL
SELECT 'histogram' AS table_name, metric_name, COUNT(*) AS rows
FROM otel.otel_metrics_histogram
WHERE service_name = 'JuicefsMount'
GROUP BY metric_name
ORDER BY table_name, metric_name;
```

重点回填：

|                             Prometheus 原指标族                             |                 预期 Doris metric_name                 |  真实结果  |
|-------------------------------------------------------------------------|------------------------------------------------------|--------|
| `juicefs_fuse_ops_durations_histogram_seconds_bucket/_sum/_count`       | `juicefs_fuse_ops_durations_histogram_seconds`       | ✅ 命中一致 |
| `juicefs_fuse_written_size_bytes_bucket/_sum/_count`                    | `juicefs_fuse_written_size_bytes`                    | ✅ 命中一致 |
| `juicefs_fuse_read_size_bytes_bucket/_sum/_count`                       | `juicefs_fuse_read_size_bytes`                       | ✅ 命中一致 |
| `juicefs_transaction_durations_histogram_seconds_bucket/_sum/_count`    | `juicefs_transaction_durations_histogram_seconds`    | ✅ 命中一致 |
| `juicefs_object_request_durations_histogram_seconds_bucket/_sum/_count` | `juicefs_object_request_durations_histogram_seconds` | ✅ 命中一致 |

**结论：✅ histogram metric_name 确认去 `_bucket/_sum/_count` 后缀，与预期完全一致。**
沙箱额外产出的 histogram（本文档设计时未覆盖，压流场景下真实出现）：
`juicefs_blockcache_read_hist_seconds`、`juicefs_blockcache_write_hist_seconds`、
`juicefs_meta_ops_durations_histogram_seconds`、`juicefs_compact_size_histogram_bytes`
（当前看板未使用，仅记录供后续扩展参考）。

## Step 3：抽查标签落位

```sql
SELECT
  metric_name,
  CAST(resource_attributes['service']['instance']['id'] AS STRING) AS instance,
  CAST(attributes['vol_name'] AS STRING) AS vol_name,
  CAST(attributes['mp'] AS STRING) AS mp,
  CAST(attributes['method'] AS STRING) AS method,
  attributes
FROM otel.otel_metrics_gauge
WHERE service_name = 'JuicefsMount'
  AND metric_name = 'juicefs_uptime'
ORDER BY timestamp DESC
LIMIT 20;

SELECT
  metric_name,
  CAST(resource_attributes['service']['instance']['id'] AS STRING) AS instance,
  CAST(attributes['vol_name'] AS STRING) AS vol_name,
  CAST(attributes['mp'] AS STRING) AS mp,
  CAST(attributes['method'] AS STRING) AS method,
  count,
  sum,
  bucket_counts,
  explicit_bounds
FROM otel.otel_metrics_histogram
WHERE service_name = 'JuicefsMount'
ORDER BY timestamp DESC
LIMIT 20;
```

期望：

- `instance` 来自 `resource_attributes['service']['instance']['id']`。
- `vol_name`、`mp`、`method` 来自普通 `attributes`。
- histogram 表为每个采样点一行，包含 `count`、`sum`、`bucket_counts`、`explicit_bounds`。

**结论：✅ 全部符合预期。** 实测样例（`juicefs_used_space`）：
`vol_name='myjfs'`、`mp='/mnt/jfs'`、`instance='obs-juicefs'`（= 容器 hostname，生产环境为真实
主机名）。`method` 标签实测出现在 `juicefs_object_request_data_bytes{method="PUT",...}`（压流写入
触发块落盘到 RustFS 的 S3 PUT）；本次压流场景读操作全部命中本地块缓存，未触发 GET 请求，故
J16 GET 序列本次沙箱验证周期内无数据，属正常现象（非 bug）。额外发现一个未在白名单里的属性
`storage_class="STANDARD"`（S3 存储类别），当前无 panel 引用，暂不需要扩白名单。

## Step 4：看板指标存在性核对

```sql
WITH expected(metric_name, table_name) AS (
  SELECT 'juicefs_uptime', 'gauge' UNION ALL
  SELECT 'juicefs_used_space', 'gauge' UNION ALL
  SELECT 'juicefs_used_inodes', 'gauge' UNION ALL
  SELECT 'juicefs_staging_blocks', 'gauge' UNION ALL
  SELECT 'juicefs_blockcache_bytes', 'gauge' UNION ALL
  SELECT 'juicefs_memory', 'gauge' UNION ALL
  SELECT 'juicefs_blockcache_hits', 'gauge' UNION ALL
  SELECT 'juicefs_blockcache_miss', 'gauge' UNION ALL
  SELECT 'juicefs_blockcache_hit_bytes', 'gauge' UNION ALL
  SELECT 'juicefs_blockcache_miss_bytes', 'gauge' UNION ALL
  SELECT 'juicefs_object_request_errors', 'gauge' UNION ALL
  SELECT 'juicefs_transaction_restart', 'gauge' UNION ALL
  SELECT 'juicefs_object_request_data_bytes', 'gauge' UNION ALL
  SELECT 'juicefs_cpu_usage', 'gauge' UNION ALL
  SELECT 'juicefs_fuse_ops_durations_histogram_seconds', 'histogram' UNION ALL
  SELECT 'juicefs_fuse_written_size_bytes', 'histogram' UNION ALL
  SELECT 'juicefs_fuse_read_size_bytes', 'histogram' UNION ALL
  SELECT 'juicefs_transaction_durations_histogram_seconds', 'histogram' UNION ALL
  SELECT 'juicefs_object_request_durations_histogram_seconds', 'histogram'
),
actual AS (
  SELECT metric_name, 'gauge' AS table_name
  FROM otel.otel_metrics_gauge
  WHERE service_name = 'JuicefsMount'
  GROUP BY metric_name
  UNION ALL
  SELECT metric_name, 'sum' AS table_name
  FROM otel.otel_metrics_sum
  WHERE service_name = 'JuicefsMount'
  GROUP BY metric_name
  UNION ALL
  SELECT metric_name, 'histogram' AS table_name
  FROM otel.otel_metrics_histogram
  WHERE service_name = 'JuicefsMount'
  GROUP BY metric_name
)
SELECT e.table_name, e.metric_name,
       CASE WHEN a.metric_name IS NULL THEN 'missing' ELSE 'ok' END AS status
FROM expected e
LEFT JOIN actual a
  ON e.metric_name = a.metric_name AND e.table_name = a.table_name
ORDER BY e.table_name, e.metric_name;
```

**结论：✅ 除 `juicefs_transaction_restart` 外全部命中**（该指标零值时不注册，本次沙箱压流未触发
元数据事务重试，见下方说明；语义上"缺失=0 次重试"，非查询错误）。**上表已按实测结果修正为
`gauge` 表**（原设计稿此处误判为 `sum`，见文首"核心发现"）。

**已同步修复的代码问题**：`datasophon-ui-v2/.../JuiceFSMonitor/panelQueries.ts` 里 J05
（`juicefs_blockcache_hits`/`miss`）、J13（`juicefs_object_request_errors`/
`juicefs_transaction_restart`）、J15（`juicefs_blockcache_hits`/`miss`/`hit_bytes`/`miss_bytes`）、
J16（`juicefs_object_request_data_bytes`）、J17（`juicefs_cpu_usage`）共 6 处 `table: 'sum'` 已
全部改为 `table: 'gauge'`，`panelQueries.test.ts` 对应断言同步更新，前端 `pnpm test`（8/8）与
`pnpm lint`/`tsc` 已重新验证通过。**修复前这 5 个面板会静默查询空表（无报错、无数据）。**
