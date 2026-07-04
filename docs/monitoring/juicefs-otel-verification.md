# JuiceFS OTel + Doris 监控验证记录

> 目标：验证 JuiceFSMonitor 从 Prometheus 迁移到 OTel Collector → Doris 后，所需指标、标签和 histogram 语义是否满足看板查询。
>
> 当前状态：待真实 JuiceFS 环境回填。本文先交付可执行 SQL 与核对清单。

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

结论：待回填。

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

| Prometheus 原指标族 | 预期 Doris metric_name | 真实结果 |
|---|---|---|
| `juicefs_fuse_ops_durations_histogram_seconds_bucket/_sum/_count` | `juicefs_fuse_ops_durations_histogram_seconds` | 待回填 |
| `juicefs_fuse_written_size_bytes_bucket/_sum/_count` | `juicefs_fuse_written_size_bytes` | 待回填 |
| `juicefs_fuse_read_size_bytes_bucket/_sum/_count` | `juicefs_fuse_read_size_bytes` | 待回填 |
| `juicefs_transaction_durations_histogram_seconds_bucket/_sum/_count` | `juicefs_transaction_durations_histogram_seconds` | 待回填 |
| `juicefs_object_request_durations_histogram_seconds_bucket/_sum/_count` | `juicefs_object_request_durations_histogram_seconds` | 待回填 |

结论：待回填。

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

结论：待回填。

## Step 4：看板指标存在性核对

```sql
WITH expected(metric_name, table_name) AS (
  SELECT 'juicefs_uptime', 'gauge' UNION ALL
  SELECT 'juicefs_used_space', 'gauge' UNION ALL
  SELECT 'juicefs_used_inodes', 'gauge' UNION ALL
  SELECT 'juicefs_staging_blocks', 'gauge' UNION ALL
  SELECT 'juicefs_blockcache_bytes', 'gauge' UNION ALL
  SELECT 'juicefs_memory', 'gauge' UNION ALL
  SELECT 'juicefs_blockcache_hits', 'sum' UNION ALL
  SELECT 'juicefs_blockcache_miss', 'sum' UNION ALL
  SELECT 'juicefs_blockcache_hit_bytes', 'sum' UNION ALL
  SELECT 'juicefs_blockcache_miss_bytes', 'sum' UNION ALL
  SELECT 'juicefs_object_request_errors', 'sum' UNION ALL
  SELECT 'juicefs_transaction_restart', 'sum' UNION ALL
  SELECT 'juicefs_object_request_data_bytes', 'sum' UNION ALL
  SELECT 'juicefs_cpu_usage', 'sum' UNION ALL
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

结论：待回填。
