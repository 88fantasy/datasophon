# Kyuubi 指标接入验证（OTel Collector → Doris）

> 用途：记录 KyuubiMonitor 从 Prometheus 查询迁移到 Doris OTel 查询所依据的真实数据结论。

## 真实数据结论

- `kyuubi_jvm_uptime`、连接/操作累计值和 JVM 内存指标落在 `otel_metrics_gauge`；`kyuubi_operation_state_*_total` 与 `kyuubi_backend_service_fetch_*_total` 落在 `otel_metrics_sum`。
- 运行中的 Kyuubi JVM memory pool 名称为 `Eden_Space`、`Tenured_Gen`、`Survivor_Space`、`Metaspace`、`Code_Cache`，不能使用原型中的 `PS_*` 名称。
- 同一 `service_instance_id` 可被多个 `service_name` 采集。看板必须单选 scrape job，不能把不同 job 的同一实例直接汇总。
- 当前样本未产生 connection failed 和 startup permit limit 序列；这两类面板保留为按需出现的无数据状态，不用零值替代。

## 核验 SQL

```sql
SELECT service_name, service_instance_id, metric_name, MAX(timestamp)
FROM otel.otel_metrics_gauge
WHERE metric_name LIKE 'kyuubi_%'
GROUP BY service_name, service_instance_id, metric_name;

SELECT service_name, metric_name, MAX(timestamp)
FROM otel.otel_metrics_sum
WHERE metric_name LIKE 'kyuubi_%'
GROUP BY service_name, metric_name;
```

## 查询映射

- 累计 session / operation 使用 gauge 表的 rate 查询，并按趋势窗口秒数缩放为窗口增量。
- operation state 与 fetch rows 使用 sum 表；KY06 使用最新 rate bucket 作为 Statistic 值。
- 所有 API 查询通过 `/v2/observability/otel/metrics/*`，不再请求 Prometheus Proxy。
