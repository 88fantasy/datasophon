# RustFS 监控看板原型设计 Spec

> **文档用途**：供 Claude design 阅读，根据本 spec 设计 React + AntV G2 看板原型，并最终生成可运行的组件代码。
> **组件**：RustFS 1.0.0-beta.8
> **数据源**：无原生 Prometheus 端点，走 OTLP 推送 → OTel Collector（:4318 HTTP）→ Doris（详见 §1）
> **参考看板**：无官方 Grafana 看板（RustFS/MinIO 均无），本 spec 基于 2026-07-04 本地沙箱实测的
> `rustfs_*` 原生指标手工设计；面板类别参考 MinIO 官方 `minio-dashboard.json`（grafana ID 13502）的
> 容量/健康/流量/延迟/错误分类，**PromQL/指标名不搬运**（RustFS 100% 用自有命名空间，无 `minio_*`）
> **Panel Catalog 路径**：`docs/monitoring/panel-catalog/Rustfs.json`（18 个面板，手工编写非抓取）
> **验证记录**：`docs/monitoring/rustfs-otel-verification.md`（Phase 2 GATE 结论 + 完整指标清单）
> **Phase**：Phase 3 —— 实现阶段（Phase 2 验证已通过，🟡命名不同但语义覆盖充分）

---

## 1. 架构约束

```
React(AntV G2)
  └──HTTP──> datasophon-api /api/v2/observability/otel/metrics/{query,query_range,labels}
               └──SQL──> Doris otel_metrics_{gauge,sum,histogram}
                              └──dorisexporter(Stream Load)──> OTel Collector
                                             └──OTLP/HTTP:4318──推送──> RustFS 进程
```

**与本仓库其余 11 个早期看板（JuiceFS/Nginx/Valkey 等）的 Path A（`/prometheus/query_range` 代理真实
Prometheus）不同，本看板走 Path B（OTel→Doris）**，对齐 2026-06-22 之后重构的 DorisMonitor/NexusMonitor：

- 数据服务：`_shared/dorisService.ts`（`DorisPanelDescriptor`/`queryDorisInstant`/`queryDorisRange`/
  `fetchDorisLabels`），**不是** `_shared/service.ts`。
- 取数 hook：`_shared/useDorisDashboardData.ts`，**不是** `_shared/useDashboardData.ts`。
- RustFS 是**主动推送方**（OTLP client），不是被抓取方：没有 `service_ddl.json`、没有纳管角色，
  `OtelScrapeConfigBuilder` 不会为它生成 scrape job；数据链路依赖 RustFS 进程自身设置
  `RUSTFS_OBS_ENDPOINT` 环境变量（见 `datasophon-cli-go/internal/cli/create/rustfs.go`）。

### 1.1 RustFS 指标特点（2026-07-04 实测确认，详见 verification.md）

- **统一原生前缀 `rustfs_*`**（另有 2 个 OTel 语义化点分格式 `rustfs.lock.*`），**完全没有 `minio_*`
  命名的指标**——不能直接复用任何 MinIO 官方/社区 Grafana JSON 的 PromQL。
- **三张表**：`otel_metrics_gauge`（116 个指标，容量/磁盘/进程资源等瞬时值）、`otel_metrics_sum`
  （41 个指标，counter，如 `rustfs_s3_operations_total`/`rustfs_http_server_requests_total`）、
  `otel_metrics_histogram`（10 个指标，如 `rustfs_http_server_request_duration_seconds`）。
  `otel_metrics_summary`/`otel_metrics_exponential_histogram` 本次未产生数据。
- **label 维度丰富**：`op`（S3 操作名，如 `s3:PutObject`）、`drive`（磁盘路径）、`server`（节点标识）、
  `status_class`（HTTP 状态码分类，如 `4xx`）均已实测确认存在，落在 `attributes` MAP。**`bucket`
  存在但本看板不使用**——`OtelMetricsQueryService` 的属性白名单故意排除 `bucket`，因为该 key 若被
  加入白名单会与后端范围查询已有的 `FLOOR(...) AS bucket`（时间分桶列）产生别名冲突。
- **两组语义近似的磁盘在线数指标**：`rustfs_cluster_health_drives_{online,offline}_count` 与
  `rustfs_system_drive_{online,offline}_count` 并存，未完全消歧；本 spec 统一选 `cluster_health_*`
  前缀作为集群总览口径。
- **无节点在线计数 gauge**：不存在类似 MinIO `minio_cluster_nodes_online_total` 的指标，单节点沙箱
  无法验证多节点场景是否有专门指标；本看板不做节点计数面板。
- **RustFS 独有、MinIO 基础看板没有的维度**：纠删集健康（`rustfs_cluster_erasure_set_*`）、副本队列
  （`rustfs_replication_*`）——本 spec 收录副本活跃 worker 数作为加分面板（R18）。

---

## 2. 图表类型映射字典

**完全复用 `docs/monitoring/design/prometheus-dashboard-prototype-spec.md` §2 中的映射字典**。

| Grafana chartType（catalog）  |        映射组件         |                        备注                         |
|-----------------------------|---------------------|---------------------------------------------------|
| `stat`                      | `<StatPanel>`       | 概览行 5 个面板，均为 `instant` 描述符                        |
| `timeseries`（counter rate）  | `<TimeSeriesPanel>` | S3 操作/HTTP 请求/错误，`table:'sum'` + `rate`           |
| `timeseries`（histogram 分位数） | `<TimeSeriesPanel>` | HTTP 延迟，`table:'histogram'` + `quantile:0.5/0.99` |
| `timeseries`（gauge 瞬时值）     | `<TimeSeriesPanel>` | CPU/内存/磁盘容量/IOPS，无 `rate`                         |
| `timeseries`（Bps 吞吐）        | `<TimeSeriesPanel>` | HTTP 收发字节，`unit` 用 `formatBytes`/s                |

---

## 3. 变量 / 过滤器规范

|  变量  |    参数名     |                        取值来源                         |    默认值    |         说明          |
|------|------------|-----------------------------------------------------|-----------|---------------------|
| 实例   | `instance` | `fetchDorisLabels('rustfs_process_uptime_seconds')` | `.+`（全选）  | 多选下拉，对应 RustFS 节点   |
| Job  | `job`      | 同上                                                  | `.+`（全选）  | 多选下拉                |
| 时间范围 | —          | 时间选择器                                               | `Last 1h` | 5m/15m/1h/6h/24h/7d |
| 刷新间隔 | —          | —                                                   | `30s`     | 自动轮询                |

> 用 `rustfs_process_uptime_seconds`（gauge，恒定上报）作为标签枚举基准指标，与 Nexus 用
> `jvm_vm_uptime` 同构。工具栏复用 `_shared/DashboardToolbar`（instance/job 多选 + 时间范围 + 刷新），
> 不需要 `$interval` 变量（速率窗口固定 `1m`，不暴露给用户，同 DorisMonitor 惯例）。

---

## 4. 看板布局（24 列 Grid）

裁剪策略：catalog 18 面板全部收录（初版规模适中，未来如需按 `op`/`drive` 深挖可在 catalog 基础上扩）。

```
┌──────────────────────────────────────────────────────────────────────┐
│  TOOLBAR: [实例▼]  [Job▼]      [Last 1h▼]   [🔄 30s▼]              │
└──────────────────────────────────────────────────────────────────────┘

行 R1 — 概览 Stat（高度 80px，col=4 ×5 + 4 spacer）
┌────────┬────────┬────────┬────────┬────────┐
│Uptime  │Buckets │Objects │Drives  │Drives  │
│        │        │        │Online  │Offline │
│ R01    │ R02    │ R03    │ R04    │ R05    │
└────────┴────────┴────────┴────────┴────────┘

行 R2 — S3/HTTP 流量 Traffic（高度 200px，col=12 ×2）
┌────────────────────────────┬────────────────────────────┐
│ R06 S3 Operations by API   │ R07 HTTP Requests by Status│
│ [Line by op]                │ [Line by status_class]     │
└────────────────────────────┴────────────────────────────┘

行 R3 — 吞吐 & 延迟 Traffic/Latency（高度 200px，col=12 ×2）
┌────────────────────────────┬────────────────────────────┐
│ R08 HTTP Traffic (Bps)     │ R09 HTTP Request Duration  │
│ [Area req/resp]             │ [Line p50/p99]              │
└────────────────────────────┴────────────────────────────┘

行 R4 — 错误 Errors（高度 200px，col=12 ×2）
┌────────────────────────────┬────────────────────────────┐
│ R10 HTTP Failures           │ R11 Drive I/O Errors       │
│ [Line rate]                 │ [Line io/timeout/avail]     │
└────────────────────────────┴────────────────────────────┘

行 R5 — 进程/容量 Saturation（高度 200px，col=8 ×3）
┌──────────────────┬──────────────────┬──────────────────┐
│ R12 Capacity     │ R13 Process CPU  │ R14 Process      │
│ Used %           │ %                │ Memory           │
└──────────────────┴──────────────────┴──────────────────┘

行 R6 — 磁盘 Saturation（高度 200px，col=8 ×3）
┌──────────────────┬──────────────────┬──────────────────┐
│ R15 Drive        │ R16 Drive IOPS   │ R17 File         │
│ Capacity by Drive│ by Drive         │ Descriptors      │
└──────────────────┴──────────────────┴──────────────────┘

行 R7 — 副本 Replication（高度 200px，RustFS 独有加分面板）
┌────────────────────────────────────────────────────────┐
│ R18 Replication Active Workers            col=24        │
└────────────────────────────────────────────────────────┘
```

---

## 5. 面板规格（逐面板）

### 5.0 Golden Signals 映射

|         维度          |            面板             |               说明               |
|---------------------|---------------------------|--------------------------------|
| **Latency（延迟）**     | R09 HTTP Request Duration | histogram 表 p50/p99 分位数        |
| **Traffic（流量）**     | R06/R07/R08               | S3 操作数、HTTP 请求数、收发字节速率         |
| **Errors（错误）**      | R10/R11                   | HTTP 失败率 + 磁盘 I/O 错误           |
| **Saturation（饱和度）** | R04/R05/R12–R17           | 磁盘在线数、容量占比、CPU/内存、每盘容量/IOPS、FD |

---

### 5.1 R1 — 概览 Stat（均为 `DorisInstantDescriptor`）

| ID  |       标题       |                    metric                    | agg |        说明        |
|-----|----------------|----------------------------------------------|-----|------------------|
| R01 | Uptime         | `rustfs_process_uptime_seconds`              | max | `formatDuration` |
| R02 | Buckets        | `rustfs_cluster_buckets_total`               | max |                  |
| R03 | Objects        | `rustfs_cluster_objects_total`               | max |                  |
| R04 | Drives Online  | `rustfs_cluster_health_drives_online_count`  | sum |                  |
| R05 | Drives Offline | `rustfs_cluster_health_drives_offline_count` | sum | 非 0 时橙/红         |

### 5.2 R2 — S3/HTTP 流量（`multi-range`，`table:'sum'`，`rate:'1m'`）

- **R06 S3 Operations by API**：`rustfs_s3_operations_total`，`groupBy:['op']`，y 轴 `ops`
- **R07 HTTP Requests by Status**：`rustfs_http_server_requests_total`，`groupBy:['status_class']`，y 轴 `rps`

### 5.3 R3 — 吞吐 & 延迟

- **R08 HTTP Traffic**：2 条 query（`rustfs_http_server_request_body_bytes_total` 标 `Request`、
  `rustfs_http_server_response_body_bytes_total` 标 `Response`），`table:'sum'`，`rate:'1m'`，
  y 轴 `Bps`（`formatBytes`/s）
- **R09 HTTP Request Duration**：2 条 query（同 metric `rustfs_http_server_request_duration_seconds`，
  `table:'histogram'`，`quantile:0.5` 标 `p50`、`quantile:0.99` 标 `p99`），y 轴 `s`

### 5.4 R4 — 错误（`table:'sum'`，`rate:'1m'`）

- **R10 HTTP Failures**：`rustfs_http_server_failures_total`
- **R11 Drive I/O Errors**：3 条 query（`rustfs_system_drive_io_errors_total`/
  `_timeout_errors_total`/`_availability_errors_total`）

### 5.5 R5 — 进程/容量 Saturation

- **R12 Capacity Used %**：`multi-range` + `denominatorMetric`，分子 `rustfs_cluster_capacity_used_bytes`，
  分母 `rustfs_cluster_capacity_usable_total_bytes`，`scale:100`，y 轴 `%`
- **R13 Process CPU %**：`rustfs_process_cpu_percent`，`table:'gauge'`（无 rate）
- **R14 Process Memory**：`rustfs_process_memory_bytes`，`table:'gauge'`，y 轴 bytes

### 5.6 R6 — 磁盘 Saturation（`table:'gauge'`，`groupBy:['drive']`）

- **R15 Drive Capacity by Drive**：2 条 query（`rustfs_system_drive_used_bytes` 标 `Used`、
  `rustfs_system_drive_total_bytes` 标 `Total`）
- **R16 Drive IOPS**：2 条 query（`rustfs_system_drive_reads_per_sec`/`_writes_per_sec`）
- **R17 File Descriptors**：2 条 query（`rustfs_system_process_file_descriptor_open_total`/
  `_limit_total`），`table:'gauge'`，不按 drive 分组

### 5.7 R7 — 副本（RustFS 独有）

- **R18 Replication Active Workers**：`rustfs_replication_current_active_workers`，`table:'gauge'`

---

## 6. 数据层接口

**完全复用 `_shared/dorisService.ts` 现有类型**（`DorisInstantDescriptor`/`DorisMultiRangeDescriptor`/
`DorisRangeQuery`），无需新增字段——RustFS 面板需要的 `filters`/`groupBy`/`denominatorMetric` 均已支持。
后端仅需扩展属性白名单（`OtelMetricsQueryService.ALLOWED_ATTR_FILTER_KEYS` 加 `op`/`drive`/`server`/
`status_class`，已完成，`bucket` 故意不加，见 §1.1）。

---

## 7. 组件树结构

```
<RustfsMonitor>                       # 页面容器，管理 instance/job + time range + refresh
  ├── <RustfsDashboardToolbar>        # 包 DashboardToolbar：instance/job 多选 + 时间范围 + 刷新
  │
  ├── <Row R1>                        # 概览 Stat（5 个）
  │   ├── <StatPanel R01>             # Uptime（formatDuration）
  │   ├── <StatPanel R02>             # Buckets
  │   ├── <StatPanel R03>             # Objects
  │   ├── <StatPanel R04>             # Drives Online
  │   └── <StatPanel R05>             # Drives Offline（阈值）
  │
  ├── <Row R2>                        # S3/HTTP 流量
  │   ├── <TimeSeriesPanel R06>       # S3 Operations by API
  │   └── <TimeSeriesPanel R07>       # HTTP Requests by Status
  │
  ├── <Row R3>                        # 吞吐 & 延迟
  │   ├── <TimeSeriesPanel R08>       # HTTP Traffic（Bps）
  │   └── <TimeSeriesPanel R09>       # HTTP Request Duration（p50/p99）
  │
  ├── <Row R4>                        # 错误
  │   ├── <TimeSeriesPanel R10>       # HTTP Failures
  │   └── <TimeSeriesPanel R11>       # Drive I/O Errors
  │
  ├── <Row R5>                        # 进程/容量 Saturation
  │   ├── <TimeSeriesPanel R12>       # Capacity Used %
  │   ├── <TimeSeriesPanel R13>       # Process CPU %
  │   └── <TimeSeriesPanel R14>       # Process Memory
  │
  ├── <Row R6>                        # 磁盘 Saturation
  │   ├── <TimeSeriesPanel R15>       # Drive Capacity by Drive
  │   ├── <TimeSeriesPanel R16>       # Drive IOPS by Drive
  │   └── <TimeSeriesPanel R17>       # File Descriptors
  │
  └── <Row R7>                        # 副本
      └── <TimeSeriesPanel R18>       # Replication Active Workers

# 复用的基础组件（来自 `monitor/_shared/`）
StatPanel / TimeSeriesPanel / MonitorDashboardLayout / PanelCol / DashboardToolbar
useDorisDashboardData / dorisService(queryDorisInstant/queryDorisRange/fetchDorisLabels)
```

---

## 8. 实现说明

### 8.1 文件路径

```
datasophon-ui-v2/src/pages/monitor/RustfsMonitor/
  ├── index.tsx                       # 页面容器（7 行布局）
  ├── panelQueries.ts                 # PANEL_QUERIES: Record<string, DorisPanelDescriptor>（18 个）
  ├── hooks/
  │   └── useRustfsDashboard.ts       # 仿 useNexusDashboard.ts：useDorisDashboardData + fetchDorisLabels
  ├── toolbar/
  │   └── RustfsDashboardToolbar.tsx  # instance/job 多选 + 时间范围 + 刷新
  ├── mock/
  │   └── rustfsMockData.ts           # 工具栏 instance/job 下拉默认值
  └── panelQueries.test.ts
```

### 8.2 Hook 集成要点

与 `useNexusDashboard` 同构：用 `rustfs_process_uptime_seconds` 作 `fetchDorisLabels` 基准指标派生
instance/job 下拉；`instant` 字段映射到 `RustfsInstantValues`（`uptime`/`buckets`/`objects`/
`drivesOnline`/`drivesOffline`）；其余全部走 `series.R06`…`series.R18`。

---

## 9. 验收标准

- [ ] 全部 18 个面板（R01–R18）按 §4 布局渲染（7 行 24 列 Grid）
- [ ] 工具栏为 instance/job 多选 + 时间范围 + 刷新（同 Nexus/ZooKeeper 惯例）
- [ ] R01 Uptime 用 `formatDuration`
- [ ] R09 HTTP Request Duration 用 `table:'histogram'` + `quantile`，**不是** `histogram_quantile`
  （Doris 侧无此函数，走后端 `LATERAL VIEW POSEXPLODE` 线性插值，前端只需传 quantile 参数）
- [ ] R08 用 `formatBytes`/s（Bps）
- [ ] R12 Capacity Used % 用 `denominatorMetric` 客户端比值合成（非后端算好）
- [ ] R15/R16 按 `drive` 分组（`groupBy:['drive']`），非按 `bucket`
- [ ] 后端未落数据时面板优雅降级（StatPanel 显示 `-`，图表空态），无控制台报错
- [ ] `npm run lint && npm run test && npm run build` 全过

