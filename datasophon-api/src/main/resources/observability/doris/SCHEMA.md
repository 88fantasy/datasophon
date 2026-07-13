# otel Doris Schema — vendoring 说明

> **文件位置变更(2026-07-12)**：`V1__otel_database.sql` / `V1__otel_tables.sql` / `V1__otel_views.sql` 三个 SQL 文件已从本目录迁移到 `package/raw/meta/datacluster-physical/DORIS/sql/`，随 DORIS 服务 DDL 一起经 `datasophon-cli upload registry` 上传到 Nexus raw 仓库；运行时由 `OtelSchemaApplier` 通过 `MetaStorage.getResourceAsString`（而非 classpath）读取。本目录只保留说明文档。触发方式见 `apply-verify.md`。

## 版本

| 属性 | 值 |
|---|---|
| schema 版本 | v1 |
| vendoring 来源 | opentelemetry-collector-contrib **v0.156.0** |
| 数据库名 | `otel` |
| 生成时间 | 2026-06-19（2026-07-12 复核升级到 v0.156.0，12 个源文件逐字节对比与 v0.154.0 完全一致，无需改动） |

## 源文件列表（pin v0.156.0）

| 文件名 | URL |
|---|---|
| `logs_ddl.sql` | https://raw.githubusercontent.com/open-telemetry/opentelemetry-collector-contrib/v0.156.0/exporter/dorisexporter/sql/logs_ddl.sql |
| `metrics_gauge_ddl.sql` | https://raw.githubusercontent.com/open-telemetry/opentelemetry-collector-contrib/v0.156.0/exporter/dorisexporter/sql/metrics_gauge_ddl.sql |
| `metrics_sum_ddl.sql` | https://raw.githubusercontent.com/open-telemetry/opentelemetry-collector-contrib/v0.156.0/exporter/dorisexporter/sql/metrics_sum_ddl.sql |
| `metrics_histogram_ddl.sql` | https://raw.githubusercontent.com/open-telemetry/opentelemetry-collector-contrib/v0.156.0/exporter/dorisexporter/sql/metrics_histogram_ddl.sql |
| `metrics_exponential_histogram_ddl.sql` | https://raw.githubusercontent.com/open-telemetry/opentelemetry-collector-contrib/v0.156.0/exporter/dorisexporter/sql/metrics_exponential_histogram_ddl.sql |
| `metrics_summary_ddl.sql` | https://raw.githubusercontent.com/open-telemetry/opentelemetry-collector-contrib/v0.156.0/exporter/dorisexporter/sql/metrics_summary_ddl.sql |
| `traces_ddl.sql` | https://raw.githubusercontent.com/open-telemetry/opentelemetry-collector-contrib/v0.156.0/exporter/dorisexporter/sql/traces_ddl.sql |
| `traces_graph_ddl.sql` | https://raw.githubusercontent.com/open-telemetry/opentelemetry-collector-contrib/v0.156.0/exporter/dorisexporter/sql/traces_graph_ddl.sql |
| `logs_view.sql` | https://raw.githubusercontent.com/open-telemetry/opentelemetry-collector-contrib/v0.156.0/exporter/dorisexporter/sql/logs_view.sql |
| `metrics_view.sql` | https://raw.githubusercontent.com/open-telemetry/opentelemetry-collector-contrib/v0.156.0/exporter/dorisexporter/sql/metrics_view.sql |
| `traces_view.sql` | https://raw.githubusercontent.com/open-telemetry/opentelemetry-collector-contrib/v0.156.0/exporter/dorisexporter/sql/traces_view.sql |
| `traces_graph_job.sql` | https://raw.githubusercontent.com/open-telemetry/opentelemetry-collector-contrib/v0.156.0/exporter/dorisexporter/sql/traces_graph_job.sql |

## 占位符说明

源 DDL 文件中使用 Go `fmt.Sprintf` 的 `%s` 占位符：
- 第 1 处 `%s`：表名/视图名（由 `cfg.Logs` / `cfg.Traces` / `cfg.Metrics` 决定）
- 第 2 处 `%s`：`PROPERTIES(...)` 块（由 `cfg.propertiesStr()` 生成）

默认值（取自 `factory.go` 默认配置）：
- `Logs` = `otel_logs`
- `Traces` = `otel_traces`
- `Metrics` = `otel_metrics`（各子类型追加 `_gauge` / `_sum` / `_histogram` / `_exponential_histogram` / `_summary`）
- `replication_num` = 1
- `compaction_policy` = `time_series`（普通表）/ `size_based`（UNIQUE KEY 表）
- `dynamic_partition.start` = -2147483648（`defaultStart = IntMin`，即不限历史分区起始）
- `dynamic_partition.history_partition_num` = 0

## 对象清单

### 基表（8 张）— V1__otel_tables.sql

| 对象名 | 类型 | 源文件 | 键类型 |
|---|---|---|---|
| `otel.otel_logs` | TABLE | `logs_ddl.sql` | DUPLICATE KEY(timestamp, service_name) |
| `otel.otel_metrics_gauge` | TABLE | `metrics_gauge_ddl.sql` | DUPLICATE KEY(service_name, timestamp) |
| `otel.otel_metrics_sum` | TABLE | `metrics_sum_ddl.sql` | DUPLICATE KEY(service_name, timestamp) |
| `otel.otel_metrics_histogram` | TABLE | `metrics_histogram_ddl.sql` | DUPLICATE KEY(service_name, timestamp) |
| `otel.otel_metrics_exponential_histogram` | TABLE | `metrics_exponential_histogram_ddl.sql` | DUPLICATE KEY(service_name, timestamp) |
| `otel.otel_metrics_summary` | TABLE | `metrics_summary_ddl.sql` | DUPLICATE KEY(service_name, timestamp) |
| `otel.otel_traces` | TABLE | `traces_ddl.sql` | DUPLICATE KEY(service_name, timestamp) |
| `otel.otel_traces_graph` | TABLE | `traces_graph_ddl.sql` | UNIQUE KEY(timestamp, caller_*, callee_*) |

### 视图与 Job（4 个）— V1__otel_views.sql

| 对象名 | 类型 | 源文件 | 说明 |
|---|---|---|---|
| `otel.otel_logs_services` | MATERIALIZED VIEW | `logs_view.sql` | 按 service_name + service_instance_id 聚合 |
| `otel.otel_metrics_services` | MATERIALIZED VIEW | `metrics_view.sql` | 基于 otel_metrics_gauge 聚合 |
| `otel.otel_traces_services` | MATERIALIZED VIEW | `traces_view.sql` | 按 service_name + service_instance_id + span_name 聚合 |
| `otel:otel_traces_graph_job` | JOB | `traces_graph_job.sql` | 每 10 分钟聚合调用图，写入 otel_traces_graph |

### 数据库对象 — V1__otel_database.sql

| 对象名 | 类型 | 说明 |
|---|---|---|
| `otel` | DATABASE | 可观测数据专用库 |
| `otel_wg` | WORKLOAD GROUP | cpu_share=10, memory_limit=20%, enable_memory_overcommit=true |
| `otel_collector` | USER | LOAD_PRIV on otel.*（仅 Stream Load，无 DDL 权限） |
| `otel_reader` | USER | SELECT_PRIV on otel.*（看板只读账号） |

## 升级说明

- 升级 dorisexporter 版本时，重新下载对应版本 SQL 文件，与当前版本 diff，决定是否需要 V2__ 迁移脚本。
- 口令占位 `CHANGE_ME_AT_A3` 须在 Phase A3 部署时通过密钥管理系统替换为实际口令，切勿硬编码提交。
- `otel_wg` 的 `cpu_share` / `memory_limit` 应根据实际部署节点规格调整，当前值为保守默认。
