# ZooKeeper 监控看板原型设计 Spec

> **文档用途**：供 Claude design 阅读，根据本 spec 设计 React + AntV G2 看板原型，并最终生成可运行的组件代码。  
> **组件**：Apache ZooKeeper 3.8.6  
> **数据源**：原生 `/metrics:7000`（PrometheusMetricsProvider，无需独立 exporter；ZK 内置 JVM metrics）  
> **参考看板**：ZooKeeper by Prometheus（Grafana ID 10465）  
> **Panel Catalog 路径**：`docs/monitoring/panel-catalog/ZooKeeper.json`（~85 个原始面板，精选 24 个）  
> **Phase**：Phase 2 —— 原型设计阶段

---

## 1. 架构约束

```
React(AntV G2)
  └──HTTP──> datasophon-api /api/v2/prometheus/query_range
               └──PromQL──> Prometheus :9090
                              └──scrape──> ZooKeeper :7000/metrics
```

**前端不直连 Prometheus**。所有 PromQL 通过后端代理端点转发（与 Prometheus 自监控完全相同的代理路径）。

### 1.1 ZooKeeper 指标特点

与 Spring Boot Micrometer 不同，ZooKeeper 指标：

- **原生无前缀**：指标名直接为 `znode_count`、`avg_latency`、`fsynctime` 等（无 `zk_` 等前缀）
- **JVM 指标**：ZooKeeper 3.8.x 内置 Prometheus JVM 指标（`jvm_threads_current`/`jvm_gc_collection_seconds` 等），但指标名为旧版格式（如 `jvm_threads_current` 而非 Spring Boot 的 `jvm_threads_live_threads`）
- **单 `instance` 标签**：每节点一个 target，通过 `{instance=~"$instance",job=~"$job"}` 过滤
- **Counter vs Gauge 混合**：部分指标是 Counter（如 `packets_received`），部分是 Gauge（如 `znode_count`、`avg_latency`）

---

## 2. 图表类型映射字典

**完全复用 `docs/monitoring/design/prometheus-dashboard-prototype-spec.md` §2 中的映射字典。**

ZooKeeper 特有补充：

| Grafana chartType（catalog） |             映射组件             |                             备注                              |
|----------------------------|------------------------------|-------------------------------------------------------------|
| `singlestat`               | `<Statistic>` (antd)         | quorum_size、leader_uptime、jvm 计数等                           |
| `bargauge`                 | `<Statistic>` 或 `<Line>` 多系列 | global/local sessions 和 learners；统一用 StatPanel 显示 instant 值 |
| `graph` 多 PromQL           | `<Line>` + multi-range 合并    | max/min/avg latency 多系列，历史值合并展示                             |

---

## 3. 变量 / 过滤器规范

看板顶部工具栏包含以下变量：

|  变量  | PromQL 占位符  |                   取值来源                    |    默认值    |            说明             |
|------|-------------|-------------------------------------------|-----------|---------------------------|
| 实例   | `$instance` | `label_values(up{job=~"$job"}, instance)` | `.+`（全选）  | 多选下拉，对应 ZK 节点 IP:Port     |
| Job  | `$job`      | `label_values(up, job)`                   | `.+`（全选）  | 多选下拉，对应 Prometheus job 名  |
| 时间范围 | —           | 时间选择器                                     | `Last 1h` | 快速选择: 5m/15m/1h/6h/24h/7d |
| 刷新间隔 | —           | —                                         | `30s`     | 自动轮询                      |

> **无 `$interval` 变量**：ZooKeeper 看板中多数指标为 Gauge（直接读值），少量使用 `[5m]` 固定窗口的 `rate/irate`；不提供用户可调的统计窗口。  
> **`$instance` 与 `$job` 与 Prometheus 自监控相同**，可复用同一套工具栏组件逻辑。

---

## 4. 看板布局（24 列 Grid）

```
┌──────────────────────────────────────────────────────────────────────┐
│  TOOLBAR: [实例▼]  [Job▼]     [Last 1h▼]   [🔄 30s▼]              │
└──────────────────────────────────────────────────────────────────────┘

行 R1 — 集群概览 Stat（高度 80px）
┌────────┬────────┬────────┬────────┬────────┬────────┐
│Quorum  │Leader  │JVM     │JVM     │Alive   │Open    │
│ Size   │Uptime  │Threads │Deadlock│Conns   │FileDes │
│ col=4  │ col=4  │ col=4  │ col=4  │ col=4  │ col=4  │
└────────┴────────┴────────┴────────┴────────┴────────┘

行 R2 — 请求 & 延迟（高度 200px）
┌──────────────────────┬────────────────────────────────────────────┐
│  Outstanding Requests│  Max / Min / Avg Latency (ms)              │
│  [Line: by instance] │  [Line: 3 系列 max/min/avg]                │
│  col-span=8          │  col-span=16                               │
└──────────────────────┴────────────────────────────────────────────┘

行 R3 — 会话 & 数据（高度 200px）
┌───────────────────────┬───────────────────────┬──────────────────────┬────────────────────┐
│  Sessions             │  Znodes               │  Data Size           │  Watch Count       │
│  [Line: global+local] │  [Line: znode+ephemeral]│ [Line: bytes]      │  [Line: by inst]   │
│  col-span=6           │  col-span=6           │  col-span=6          │  col-span=6        │
└───────────────────────┴───────────────────────┴──────────────────────┴────────────────────┘

行 R4 — 网络 & 连接（高度 200px）
┌──────────────────────┬──────────────────────┬──────────────────────┐
│  Packets Recv / Sent │  Alive Connections   │  Connection Errors   │
│  [Line: recv/sent]   │  [Line: by instance] │  [Line: reject/drop] │
│  col-span=8          │  col-span=8          │  col-span=8          │
└──────────────────────┴──────────────────────┴──────────────────────┘

行 R5 — Leader & 选举（高度 200px）
┌──────────────────────┬──────────────────────┬──────────────────────┐
│  Election Time (ms)  │  Learners / Observers│  Quorum Counts       │
│  [Line: avg]         │  [Line: 2 系列]       │  [Line: commit/snap  │
│  col-span=8          │  col-span=8          │   /proposal 3 系列]  │
│                      │                      │  col-span=8          │
└──────────────────────┴──────────────────────┴──────────────────────┘

行 R6 — 持久化延迟（高度 200px）
┌───────────────────────────────────────┬────────────────────────────────────┐
│  Fsync Time (avg ms)                  │  Snapshot Time (avg ms)            │
│  [Line: by instance]                  │  [Line: by instance]               │
│  col-span=12                          │  col-span=12                       │
└───────────────────────────────────────┴────────────────────────────────────┘

行 R7 — JVM（高度 200px）
┌──────────────────────┬──────────────────────┬──────────────────────┐
│  JVM Memory Pools    │  GC Collection Rate  │  GC Pause Time       │
│  [Area: by pool,     │  [Line: by GC name]  │  [Line: rate ms/s]   │
│   bytes, col=8]      │  col-span=8          │  col-span=8          │
└──────────────────────┴──────────────────────┴──────────────────────┘
```

---

## 5. 面板规格（逐面板）

### 5.0 裁剪说明（~85 → 24）

**从 `panel-catalog/ZooKeeper.json`（~85 个面板）裁剪至 24 个。**

|          裁剪类别          |                                                                                                                                                                                                                    代表面板（panel 标题或指标名）                                                                                                                                                                                                                     |                              裁剪理由                               |
|------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------|
| Processor 链路细节（约 30 个） | `prep_process_time` / `prep_processor_queue_time` / `sync_process_time` / `sync_processor_queue_flush_time` / `commit_process_time` / `commit_proc_req_queued` / `commit_proc_issued` / `concurrent_request_processing_in_commit_processor` / `outstanding_changes_queued` / `requests_in_session_queue` / `local_write_committed_time` / `server_write_committed_time` / `write_batch_time_in_commit_processor` / `reads_after_write_in_session_queue` 等 | 内部 processor 队列调优指标，集群级运维无需关注；性能调优阶段按需查询                        |
| Per-namespace 读写       | `write_per_namespace_sum` / `read_per_namespace_sum`                                                                                                                                                                                                                                                                                                                                                                                                      | 细粒度 namespace 分析，与通用监控无关                                        |
| Response packet cache  | `response_packet_cache_misses` / `response_packet_cache_hits` / `response_packet_get_children_*`                                                                                                                                                                                                                                                                                                                                                          | 缓存命中率次要，不影响集群健康判断                                               |
| TLS & auth             | `tls_handshake_exceeded` / `ensemble_auth_fail` / `ensemble_auth_success`                                                                                                                                                                                                                                                                                                                                                                                 | 安全层指标，非基础运营监控                                                   |
| Observer Master        | `om_commit_process_time_ms_sum` / `om_proposal_process_time_ms_sum`                                                                                                                                                                                                                                                                                                                                                                                       | 需 ZK Observer 部署才有值，非通用                                         |
| Watch 细分               | `node_changed_watch_count_sum` / `node_children_watch_count_sum` / `node_deleted_watch_count_sum` / `node_created_watch_count_sum`                                                                                                                                                                                                                                                                                                                        | 聚合进 `watch_count`；细分调试级信息                                       |
| 会话过期/失效                | `revalidate_count` / `stale_sessions_expired` / `dead_watchers_*` / `sessionless_connections_expired` / `connection_token_deficit`                                                                                                                                                                                                                                                                                                                        | 次要连接生命周期指标                                                      |
| 重复 Latency             | `read_latency` / `update_latency` / `quorum_ack_latency` / `ack_latency` / `propagation_latency` / `commit_propagation_latency` / `proposal_ack_creation_latency`                                                                                                                                                                                                                                                                                         | `max/min/avg_latency` 已覆盖整体延迟；细分延迟保留 proposal_latency（Quorum 行） |
| 次要计数                   | `diff_count` / `learner_commit_received_count` / `proposal_size` / `follower_sync_time` / `learner_handler_qp_size` / `looking_count` / `quit_leading_due_to_disloyal_voter`                                                                                                                                                                                                                                                                              | 低频选举指标、同步细节，非日常运营                                               |
| 启动指标                   | `startup_txns_loaded` / `db_init_time`                                                                                                                                                                                                                                                                                                                                                                                                                    | 一次性启动统计，运营期无意义                                                  |
| 重复 Singlestat          | `leader`（与 `leader_uptime` 重复） / `quorum uptime`（与 `leader_uptime` 语义重叠）                                                                                                                                                                                                                                                                                                                                                                                  | 合并到 R1 stat 行                                                   |
| 次要连接                   | `bytes_received_count` / `client_response_size` / `connection_request_count` / `connection_drop_probability`                                                                                                                                                                                                                                                                                                                                              | 二级连接指标；`packets_received/sent` 已覆盖网络流量                          |
| Rate 变体                | `znode_count_rate`（rate of znode changes）                                                                                                                                                                                                                                                                                                                                                                                                                 | 与 `znode_count` Gauge 重叠，运维价值低                                  |

**golden signals 四象限覆盖**（对齐 `dashboard-selection.md` 结论：error 维度原本偏弱，已补强）：

|       维度       |                                              面板                                               |                     补强说明                     |
|----------------|-----------------------------------------------------------------------------------------------|----------------------------------------------|
| **Latency**    | Max/Min/Avg Latency、Fsync Time、Snapshot Time                                                  | —                                            |
| **Traffic**    | Outstanding Requests、Packets Recv/Sent、Alive Connections                                      | —                                            |
| **Errors**     | Connection Rejected/Drop（Z15）、`unrecoverable_error_count`（★补强）、`digest_mismatches_count`（★补强） | 原 catalog 中 error 仅有 connection 维度；补充数据一致性错误 |
| **Saturation** | Outstanding Requests、Open File Descriptor（R1 stat）、JVM Memory Pools、GC Pause Time             | —                                            |

> ★ 补强面板：`unrecoverable_error_count` 和 `digest_mismatches_count` 在 catalog 中有对应条目，但 `dashboard-selection.md` Phase 1 评分时标注"error 维度偏弱"——这两个指标是 ZK 数据一致性错误的关键信号，纳入 Z15 的多系列设计中。

---

### 5.1 R1 — 集群概览 Stat

#### Z01 Quorum Size

|    属性    |                           值                           |
|----------|-------------------------------------------------------|
| 标题       | Quorum Size                                           |
| 图表类型     | `<Statistic>` (antd)                                  |
| Query 类型 | instant query                                         |
| PromQL   | `max(quorum_size{instance=~"$instance",job=~"$job"})` |
| 单位       | 整数                                                    |
| 阈值规则     | `≥ 3` → 绿（奇数 quorum 正常）；`< 3` → 红（法定节点不足）             |
| 说明       | 集群最关键的存活性指标；< 3 即集群不可用                                |
| col-span | 4                                                     |

#### Z02 Leader Uptime

|    属性    |                          值                          |
|----------|-----------------------------------------------------|
| 标题       | Leader Uptime                                       |
| 图表类型     | `<Statistic>` (antd)                                |
| Query 类型 | instant query                                       |
| PromQL   | `leader_uptime{instance=~"$instance",job=~"$job"}`  |
| 单位       | 毫秒（ms）；实现时用 `formatDuration(ms)` 转为可读格式（如 `2h 15m`） |
| 阈值规则     | 无（蓝色 `#1677ff`）；频繁重置（短时间内重归零）表示频繁重新选举               |
| col-span | 4                                                   |

#### Z03 JVM Threads Current

|    属性    |                               值                               |
|----------|---------------------------------------------------------------|
| 标题       | JVM Threads                                                   |
| 图表类型     | `<Statistic>` (antd)                                          |
| Query 类型 | instant query                                                 |
| PromQL   | `max(jvm_threads_current{instance=~"$instance",job=~"$job"})` |
| 单位       | 整数（线程数）                                                       |
| 阈值规则     | `< 200` → 绿；`200 ≤ value < 500` → 黄；`≥ 500` → 红               |
| col-span | 4                                                             |

#### Z04 JVM Threads Deadlocked

|    属性    |                                值                                 |
|----------|------------------------------------------------------------------|
| 标题       | Deadlocked Threads                                               |
| 图表类型     | `<Statistic>` (antd)                                             |
| Query 类型 | instant query                                                    |
| PromQL   | `max(jvm_threads_deadlocked{instance=~"$instance",job=~"$job"})` |
| 单位       | 整数                                                               |
| 阈值规则     | `= 0` → 绿（正常）；`≥ 1` → 红（死锁即异常）                                   |
| 说明       | 死锁线程数应始终为 0；非零即需立即告警                                             |
| col-span | 4                                                                |

#### Z05 Alive Connections

|    属性    |                                值                                |
|----------|-----------------------------------------------------------------|
| 标题       | Alive Connections                                               |
| 图表类型     | `<Statistic>` (antd)                                            |
| Query 类型 | instant query                                                   |
| PromQL   | `sum(num_alive_connections{instance=~"$instance",job=~"$job"})` |
| 单位       | 整数（连接数）                                                         |
| 阈值规则     | 无（蓝色 `#1677ff`）；结合历史趋势判断异常                                      |
| col-span | 4                                                               |

#### Z06 Open File Descriptors

|    属性    |                                  值                                   |
|----------|----------------------------------------------------------------------|
| 标题       | Open File Descriptors                                                |
| 图表类型     | `<Statistic>` (antd)                                                 |
| Query 类型 | instant query                                                        |
| PromQL   | `max(open_file_descriptor_count{instance=~"$instance",job=~"$job"})` |
| 单位       | 整数                                                                   |
| 阈值规则     | `< 5000` → 绿；`5000 ≤ value < 8000` → 黄；`≥ 8000` → 红（接近 ulimit）       |
| 说明       | Saturation 维度：文件描述符耗尽导致 ZK 拒绝连接                                      |
| col-span | 4                                                                    |

---

### 5.2 R2 — 请求 & 延迟

#### Z07 Outstanding Requests

|    属性    |                             值                             |
|----------|-----------------------------------------------------------|
| 标题       | Outstanding Requests                                      |
| 图表类型     | `<Line>` 多系列（by instance）                                 |
| Query 类型 | range query                                               |
| PromQL   | `outstanding_requests{instance=~"$instance",job=~"$job"}` |
| 系列字段     | `instance`（每节点一条线）                                        |
| y 轴      | 整数（待处理请求数）                                                |
| 阈值参考线    | y=10（ZK 官方建议：持续 > 10 表示积压，橙色虚线）                           |
| 说明       | **Saturation 维度核心指标**：值持续增大表示 ZK 处理能力不足                   |
| col-span | 8                                                         |

#### Z08 Latency (Max / Min / Avg)

|    属性     |            值             |
|-----------|--------------------------|
| 标题        | Request Latency (ms)     |
| 图表类型      | `<Line>` 多系列（3 系列）       |
| Query 类型  | range query（multi-range） |
| PromQL 明细 | —                        |

| seriesLabel |                      PromQL                      |
|-------------|--------------------------------------------------|
| `max`       | `max_latency{instance=~"$instance",job=~"$job"}` |
| `avg`       | `avg_latency{instance=~"$instance",job=~"$job"}` |
| `min`       | `min_latency{instance=~"$instance",job=~"$job"}` |

|    属性    |                               值                                |
|----------|----------------------------------------------------------------|
| 颜色       | `max` → `#ff4d4f`（红）、`avg` → `#1677ff`（蓝）、`min` → `#52c41a`（绿） |
| y 轴      | 毫秒（`ms`），保留 1 位小数                                              |
| 说明       | **Latency 维度核心面板**；`max` 大幅超过 `avg` 表示存在长尾请求                   |
| col-span | 16                                                             |

---

### 5.3 R3 — 会话 & 数据

#### Z09 Sessions

|    属性     |             值             |
|-----------|---------------------------|
| 标题        | Sessions (Global / Local) |
| 图表类型      | `<Line>` 多系列（2 系列）        |
| Query 类型  | range query（multi-range）  |
| PromQL 明细 | —                         |

| seriesLabel |                        PromQL                        |
|-------------|------------------------------------------------------|
| `global`    | `global_sessions{instance=~"$instance",job=~"$job"}` |
| `local`     | `local_sessions{instance=~"$instance",job=~"$job"}`  |

|    属性    |                    值                     |
|----------|------------------------------------------|
| 颜色       | `global` → `#1677ff`、`local` → `#52c41a` |
| y 轴      | 整数（会话数）                                  |
| col-span | 6                                        |

#### Z10 Znodes

|    属性     |            值             |
|-----------|--------------------------|
| 标题        | Znodes                   |
| 图表类型      | `<Line>` 多系列（2 系列）       |
| Query 类型  | range query（multi-range） |
| PromQL 明细 | —                        |

|  seriesLabel  |                        PromQL                         |
|---------------|-------------------------------------------------------|
| `znode_count` | `znode_count{instance=~"$instance",job=~"$job"}`      |
| `ephemerals`  | `ephemerals_count{instance=~"$instance",job=~"$job"}` |

|    属性    |                         值                          |
|----------|----------------------------------------------------|
| 颜色       | `znode_count` → `#1677ff`、`ephemerals` → `#faad14` |
| y 轴      | 整数（节点数）                                            |
| 说明       | `ephemerals` 突增后快速下降通常表示客户端重连；持续增大表示会话泄漏           |
| col-span | 6                                                  |

#### Z11 Approximate Data Size

|    属性    |                             值                              |
|----------|------------------------------------------------------------|
| 标题       | Approximate Data Size                                      |
| 图表类型     | `<Line>` 单系列                                               |
| Query 类型 | range query                                                |
| PromQL   | `approximate_data_size{instance=~"$instance",job=~"$job"}` |
| y 轴      | bytes（`formatBytes` 函数，B/KB/MB/GB）                         |
| 系列颜色     | `#1677ff`                                                  |
| 说明       | ZK 数据目录大小；接近磁盘容量时需关注 snapshot 清理                           |
| col-span | 6                                                          |

#### Z12 Watch Count

|    属性    |                        值                         |
|----------|--------------------------------------------------|
| 标题       | Watch Count                                      |
| 图表类型     | `<Line>` 多系列（by instance）                        |
| Query 类型 | range query                                      |
| PromQL   | `watch_count{instance=~"$instance",job=~"$job"}` |
| 系列字段     | `instance`                                       |
| y 轴      | 整数（watch 数量）                                     |
| 说明       | Watch 数量持续异常增大表示客户端 watch 泄漏；Traffic 补充指标        |
| col-span | 6                                                |

---

### 5.4 R4 — 网络 & 连接

#### Z13 Packets Received / Sent

|    属性     |            值             |
|-----------|--------------------------|
| 标题        | Packets (Recv / Sent)    |
| 图表类型      | `<Line>` 多系列（2 系列）       |
| Query 类型  | range query（multi-range） |
| PromQL 明细 | —                        |

| seriesLabel |                        PromQL                         |
|-------------|-------------------------------------------------------|
| `received`  | `packets_received{instance=~"$instance",job=~"$job"}` |
| `sent`      | `packets_sent{instance=~"$instance",job=~"$job"}`     |

|    属性    |                     值                     |
|----------|-------------------------------------------|
| 颜色       | `received` → `#1677ff`、`sent` → `#52c41a` |
| y 轴      | 整数（累计包数）；counter 形态，趋势斜率反映流量速率            |
| 说明       | Traffic 维度：两线斜率应接近；大幅差异表示网络异常             |
| col-span | 8                                         |

#### Z14 Alive Connections (Trend)

|    属性    |                             值                              |
|----------|------------------------------------------------------------|
| 标题       | Alive Connections (Trend)                                  |
| 图表类型     | `<Line>` 多系列（by instance）                                  |
| Query 类型 | range query                                                |
| PromQL   | `num_alive_connections{instance=~"$instance",job=~"$job"}` |
| 系列字段     | `instance`（每节点一条线，展示连接分布）                                  |
| y 轴      | 整数（连接数）                                                    |
| 说明       | 与 Z05 (stat) 互补：stat 显示当前值，trend 显示历史变化和节点间负载均衡            |
| col-span | 8                                                          |

#### Z15 Connection Errors（补强 Errors 维度）

|    属性     |            值             |
|-----------|--------------------------|
| 标题        | Connection & Data Errors |
| 图表类型      | `<Line>` 多系列（4 系列）       |
| Query 类型  | range query（multi-range） |
| PromQL 明细 | —                        |

|    seriesLabel    |                             PromQL                             |         说明         |
|-------------------|----------------------------------------------------------------|--------------------|
| `conn_rejected`   | `connection_rejected{instance=~"$instance",job=~"$job"}`       | 连接被拒绝（ZK 连接数超限）    |
| `conn_drop`       | `connection_drop_count{instance=~"$instance",job=~"$job"}`     | 连接被中断              |
| `unrecoverable`   | `unrecoverable_error_count{instance=~"$instance",job=~"$job"}` | ★ 不可恢复错误（数据一致性问题）  |
| `digest_mismatch` | `digest_mismatches_count{instance=~"$instance",job=~"$job"}`   | ★ 摘要校验不匹配（数据一致性问题） |

|    属性    |                                                       值                                                       |
|----------|---------------------------------------------------------------------------------------------------------------|
| 颜色       | `conn_rejected` → `#ff7a45`、`conn_drop` → `#fa541c`、`unrecoverable` → `#ff4d4f`、`digest_mismatch` → `#cf1322` |
| y 轴      | 整数（累计次数）；应始终为 0；任何非零值立即需要关注                                                                                   |
| 说明       | ★ `unrecoverable_error_count` 和 `digest_mismatches_count` 为 Errors 维度补强指标（见 §5.0）                             |
| col-span | 8                                                                                                             |

---

### 5.5 R5 — Leader & 选举

#### Z16 Election Time

|    属性    |                                 值                                 |
|----------|-------------------------------------------------------------------|
| 标题       | Election Time (avg ms)                                            |
| 图表类型     | `<Line>` 单系列                                                      |
| Query 类型 | range query                                                       |
| PromQL   | `irate(election_time_sum{instance=~"$instance",job=~"$job"}[1m])` |
| y 轴      | 毫秒（`ms`），保留 0 位小数                                                 |
| 系列颜色     | `#1677ff`                                                         |
| 说明       | 大多数时候此值为 0（无选举）；突然出现非零值表示发生了 Leader 切换。Latency 补充指标               |
| col-span | 8                                                                 |

#### Z17 Learners / Synced Observers

|    属性     |              值              |
|-----------|-----------------------------|
| 标题        | Learners / Synced Observers |
| 图表类型      | `<Line>` 多系列（2 系列）          |
| Query 类型  | range query（multi-range）    |
| PromQL 明细 | —                           |

|    seriesLabel     |                           PromQL                           |
|--------------------|------------------------------------------------------------|
| `learners`         | `max(learners{instance=~"$instance",job=~"$job"})`         |
| `synced_observers` | `max(synced_observers{instance=~"$instance",job=~"$job"})` |

|    属性    |                             值                             |
|----------|-----------------------------------------------------------|
| 颜色       | `learners` → `#1677ff`、`synced_observers` → `#52c41a`     |
| y 轴      | 整数（节点数）                                                   |
| 说明       | `learners` 掉线时需关注节点状态；`synced_observers` 增减反映 Observer 伸缩 |
| col-span | 8                                                         |

#### Z18 Quorum Counts

|    属性     |                 值                  |
|-----------|------------------------------------|
| 标题        | Commit / Snapshot / Proposal Count |
| 图表类型      | `<Line>` 多系列（3 系列）                 |
| Query 类型  | range query（multi-range）           |
| PromQL 明细 | —                                  |

| seriesLabel |                       PromQL                        |
|-------------|-----------------------------------------------------|
| `commits`   | `commit_count{instance=~"$instance",job=~"$job"}`   |
| `snapshots` | `snap_count{instance=~"$instance",job=~"$job"}`     |
| `proposals` | `proposal_count{instance=~"$instance",job=~"$job"}` |

|    属性    |                                   值                                   |
|----------|-----------------------------------------------------------------------|
| 颜色       | `commits` → `#1677ff`、`snapshots` → `#faad14`、`proposals` → `#52c41a` |
| y 轴      | 整数（累计计数，counter 形态）                                                   |
| 说明       | Traffic 补充：proposal 速率反映写入吞吐；snapshot 频率影响持久化性能                       |
| col-span | 8                                                                     |

---

### 5.6 R6 — 持久化延迟

#### Z19 Fsync Time

|    属性    |                               值                               |
|----------|---------------------------------------------------------------|
| 标题       | Fsync Time (avg ms)                                           |
| 图表类型     | `<Line>` 多系列（by instance）                                     |
| Query 类型 | range query                                                   |
| PromQL   | `irate(fsynctime_sum{instance=~"$instance",job=~"$job"}[1m])` |
| 系列字段     | `instance`                                                    |
| y 轴      | 毫秒（`ms`），保留 1 位小数                                             |
| 说明       | **Latency 维度**：fsync 耗时高表示磁盘 I/O 瓶颈，直接影响 ZK 写入延迟              |
| col-span | 12                                                            |

#### Z20 Snapshot Time

|    属性    |                                值                                |
|----------|-----------------------------------------------------------------|
| 标题       | Snapshot Time (avg ms)                                          |
| 图表类型     | `<Line>` 多系列（by instance）                                       |
| Query 类型 | range query                                                     |
| PromQL   | `rate(snapshottime_sum{instance=~"$instance",job=~"$job"}[5m])` |
| 系列字段     | `instance`                                                      |
| y 轴      | 毫秒（`ms`），保留 1 位小数                                               |
| 说明       | Snapshot 写入耗时；耗时突增通常与数据目录大小相关                                   |
| col-span | 12                                                              |

---

### 5.7 R7 — JVM

#### Z21 JVM Memory Pool Usage

|    属性    |                                值                                |
|----------|-----------------------------------------------------------------|
| 标题       | JVM Memory Pool Usage                                           |
| 图表类型     | `<Area>` 多系列（by pool，非堆叠）                                       |
| Query 类型 | range query                                                     |
| PromQL   | `jvm_memory_pool_bytes_used{instance=~"$instance",job=~"$job"}` |
| 系列字段     | `pool`（G1 Eden Space / G1 Old Gen / Metaspace 等，由 label 自动区分）   |
| y 轴      | bytes（`formatBytes`）                                            |
| 说明       | **Saturation 维度**：Old Gen 持续接近 max 表示内存压力                       |
| col-span | 8                                                               |

#### Z22 GC Collection Rate

|    属性    |                                       值                                        |
|----------|--------------------------------------------------------------------------------|
| 标题       | GC Collection Rate                                                             |
| 图表类型     | `<Line>` 多系列（by gc name）                                                       |
| Query 类型 | range query                                                                    |
| PromQL   | `rate(jvm_gc_collection_seconds_count{instance=~"$instance",job=~"$job"}[5m])` |
| 系列字段     | `gc`（G1 Young Generation / G1 Old Generation 等）                                |
| y 轴      | `ops/s`（GC 频率），保留 3 位小数                                                        |
| 说明       | Young GC 频率高但 Old GC 频率低是健康状态；Old GC 频率高需关注内存泄漏                                |
| col-span | 8                                                                              |

#### Z23 JVM Pause Time Rate

|    属性    |                                       值                                       |
|----------|-------------------------------------------------------------------------------|
| 标题       | JVM GC Pause Time                                                             |
| 图表类型     | `<Line>` 多系列（by instance）                                                     |
| Query 类型 | range query                                                                   |
| PromQL   | `rate(jvm_pause_time_ms_sum{instance=~"$instance",job=~"$job"}[5m])`          |
| 系列字段     | `instance`                                                                    |
| y 轴      | `ms/s`（GC 暂停时间速率），保留 2 位小数                                                    |
| 说明       | **Saturation 维度**：STW（Stop-the-World）暂停直接影响 ZK 响应延迟；值高时与 Z08 avg_latency 上升对应 |
| col-span | 8                                                                             |

---

## 6. 主题 / 样式规范

**完全复用 `prometheus-dashboard-prototype-spec.md` §6 中的颜色 Token 和工具函数。**

```ts
// 复用同名常量（来自 utils/formatters.ts）
import { CHART_COLORS, colorByThreshold, formatBytes } from '../utils/formatters';
```

ZooKeeper 特有颜色补充（错误类面板，Z15）：

```ts
const zkErrorColors = {
  conn_rejected: '#ff7a45',  // 橙红（连接拒绝）
  conn_drop:     '#fa541c',  // 深橙（连接断开）
  unrecoverable: '#ff4d4f',  // 标准错误红
  digest_mismatch: '#cf1322', // 深红（数据一致性告警）
};

const zkLatencyColors = {
  max: '#ff4d4f',  // 红（最大延迟）
  avg: '#1677ff',  // 蓝（平均延迟）
  min: '#52c41a',  // 绿（最小延迟）
};
```

---

## 7. 数据层接口 TypeScript 定义

**复用 `prometheus-dashboard-prototype-spec.md` §7 中全部接口定义（`PrometheusVector`、`PrometheusMatrix`、`TimeSeriesPoint`、`StatValue`）。**

ZooKeeper 特有补充：

```ts
// 看板查询参数（ZK 版，与 Prometheus 自监控结构相同，去掉 $interval）
interface ZKDashboardQueryParams {
  clusterId: number;
  start: number;      // unix timestamp (seconds)
  end: number;        // unix timestamp (seconds)
  step: number;       // 建议 = (end - start) / 200
  variables: {
    instance: string;   // 正则，如 ".+" 或 "192.168.1.1:7000"
    job: string;        // 正则，如 ".+" 或 "zookeeper"
    // 注：无 interval 变量
  };
}
```

---

## 8. 组件树结构

```
<ZooKeeperDashboard>                  # 页面容器，管理 variables + time range + refresh
  ├── <DashboardToolbar>              # 引用 `_shared/DashboardToolbar.tsx`（去掉 Interval 下拉）
  │
  ├── <Row R1>                        # 集群概览 Stat（6 个 Stat 面板）
  │   ├── <StatPanel Z01>             # Quorum Size（阈值 ≥3 绿/<3 红）
  │   ├── <StatPanel Z02>             # Leader Uptime（ms，formatDuration）
  │   ├── <StatPanel Z03>             # JVM Threads Current（阈值）
  │   ├── <StatPanel Z04>             # Deadlocked Threads（0=绿，≥1=红）
  │   ├── <StatPanel Z05>             # Alive Connections
  │   └── <StatPanel Z06>             # Open File Descriptors（阈值）
  │
  ├── <Row R2>                        # 请求 & 延迟
  │   ├── <TimeSeriesPanel Z07>       # Outstanding Requests（by instance，阈值参考线 y=10）
  │   └── <TimeSeriesPanel Z08>       # Latency max/min/avg（3 系列，ms）
  │
  ├── <Row R3>                        # 会话 & 数据
  │   ├── <TimeSeriesPanel Z09>       # Sessions（global+local 2 系列）
  │   ├── <TimeSeriesPanel Z10>       # Znodes（znode_count+ephemerals 2 系列）
  │   ├── <TimeSeriesPanel Z11>       # Approximate Data Size（bytes）
  │   └── <TimeSeriesPanel Z12>       # Watch Count（by instance）
  │
  ├── <Row R4>                        # 网络 & 连接
  │   ├── <TimeSeriesPanel Z13>       # Packets Recv/Sent（2 系列）
  │   ├── <TimeSeriesPanel Z14>       # Alive Connections Trend（by instance）
  │   └── <TimeSeriesPanel Z15>       # Connection & Data Errors（4 系列，错误红系）
  │
  ├── <Row R5>                        # Leader & 选举
  │   ├── <TimeSeriesPanel Z16>       # Election Time（avg ms）
  │   ├── <TimeSeriesPanel Z17>       # Learners / Synced Observers（2 系列）
  │   └── <TimeSeriesPanel Z18>       # Quorum Counts（commit/snap/proposal 3 系列）
  │
  ├── <Row R6>                        # 持久化延迟
  │   ├── <TimeSeriesPanel Z19>       # Fsync Time（avg ms，by instance）
  │   └── <TimeSeriesPanel Z20>       # Snapshot Time（avg ms，by instance）
  │
  └── <Row R7>                        # JVM
      ├── <AreaPanel Z21>             # JVM Memory Pool Usage（by pool，bytes area）
      ├── <TimeSeriesPanel Z22>       # GC Collection Rate（by gc name，ops/s）
      └── <TimeSeriesPanel Z23>       # JVM GC Pause Time（by instance，ms/s）

# 复用的基础组件（来自 `monitor/_shared/panels/`）
StatPanel / TimeSeriesPanel / AreaPanel / DashboardToolbar / useDashboardData ← 均来自 `monitor/_shared/`
```

---

## 9. 实现说明（供 Phase 3 编码参考）

### 9.1 文件路径

```
datasophon-ui-v2/src/pages/ZooKeeperMonitor/
  ├── index.tsx                     # 页面容器（7 行布局）
  ├── panelQueries.ts               # PanelDef（24 个面板的 instant/range/multi-range 定义）
  ├── hooks/
  │   └── useZKDashboard.ts         # 调用 `useDashboardData`（`_shared/useDashboardData.ts`）
  ├── panels/                       # 无此目录 — 直接从 `../../_shared/panels/` import
  ├── toolbar/
  │   └── ZKDashboardToolbar.tsx    # 改 DashboardToolbar（去掉 Interval 下拉，保留 Instance + Job）
  ├── mock/
  │   └── zkMockData.ts             # 确定性伪随机静态数据
  └── utils/                        # 无此目录 — 直接从 `../../_shared/charts/` import
```

### 9.2 PromQL 变量替换规则（ZK 版）

```ts
function replaceZKVars(
  promql: string,
  vars: ZKDashboardQueryParams['variables'],
): string {
  return promql
    .replace(/\$instance/g, vars.instance || '.+')
    .replace(/\$job/g,      vars.job      || '.+');
  // 注：无 $interval 替换
}
```

### 9.3 Toolbar 差异

与 PrometheusMonitor 的 DashboardToolbar 相比，ZooKeeper 版本：
- **去掉** `Interval` 下拉（ZK 看板使用固定窗口，不暴露给用户）
- **保留** `Instance` 多选下拉 + `Job` 多选下拉 + 时间范围 + 刷新间隔

### 9.4 Counter 指标可视化说明

ZK catalog 中 `packets_received`、`packets_sent`、`commit_count`、`snap_count`、`proposal_count` 等是单调递增 Counter。

在 range query 中它们呈斜率上升的曲线，斜率即速率。建议在 y 轴标签旁加注 `(cumulative)` 或用 tooltip 显示 `rate(...)` 值（两种方式都可），让运维人员清楚这是累计值而非瞬时值。

### 9.5 Mock 数据要求

`zkMockData.ts` 覆盖全部 24 个面板：

**Stat 面板（Z01-Z06，instant 值）：**
- Z01 Quorum Size: `3`（3 节点 quorum，绿色）
- Z02 Leader Uptime: `7254000`（约 2 小时，ms）
- Z03 JVM Threads: `85`（绿色正常）
- Z04 Deadlocked: `0`（绿色正常）
- Z05 Alive Connections: `42`
- Z06 Open FD: `356`（绿色正常）

**Range 面板（Z07-Z23）：**
- Z07 Outstanding Requests: 0–3（健康范围，偶发 1 个突刺至 15 验证告警线）
- Z08 Latency: avg 2-5ms、max 20-50ms（max 偶有 100ms 突刺）
- Z09 Sessions: global 55–65，local 40–50
- Z10 Znodes: znode_count 约 12000，ephemerals 约 200
- Z11 Data Size: 约 2.5 MB，缓慢增长
- Z12 Watch Count: 约 500，平稳
- Z13 Packets: received 和 sent 均约 1000/s（counter 累计，时序图呈直线斜率）
- Z14 Connections Trend: 每节点约 14 个连接（3 节点 × 14 ≈ 42）
- Z15 Errors: 全部为 0（偶发模拟 1 个 conn_rejected 突刺）
- Z19 Fsync Time: 1–5ms，偶有 20ms 突刺
- Z20 Snapshot Time: 200–500ms（快照较慢属正常）
- Z21 JVM Memory: G1 Old Gen 约 120MB，Metaspace 约 60MB，稳定
- Z22 GC Rate: Young GC 约 0.01 ops/s，Old GC 约 0（健康）
- Z23 GC Pause: 约 0.5 ms/s，稳定

---

## 10. Dev 环境配置注意事项

**完全复用 `prometheus-dashboard-prototype-spec.md` §10 中的三项配置（publicPath、proxy bypass、mock 路径对齐）。**

ZooKeeper 看板无额外 dev 环境差异：
- 后端代理端点路径与 PrometheusMonitor 完全相同（`/ddh/api/v2/prometheus/query` 和 `/query_range`）
- mock 文件路径写法与 PrometheusMonitor 相同，两者共享同一对代理端点

---

## 11. 验收标准

Phase 2 原型（mock 阶段）完成后，需满足：

- [ ] 全部 24 个面板（Z01-Z23，注：R1 有 6 个 stat，共 6+2+4+3+3+2+3=23，加 Z15 共 24）按 §4 布局渲染
- [ ] R1 行 6 个 Stat 面板使用 antd `<Statistic>` + 正确阈值规则（Z01 quorum_size < 3 → 红；Z04 deadlocked ≥ 1 → 红）
- [ ] Z08 Latency 面板 3 系列颜色：max 红 / avg 蓝 / min 绿
- [ ] Z15 Connection & Data Errors 包含 4 系列（含 ★ 补强的 unrecoverable 和 digest_mismatch）
- [ ] Z21 JVM Memory Pool 使用 `<Area>` 组件 + `formatBytes` y 轴格式化
- [ ] 工具栏：实例多选 + Job 多选 + 时间范围 + 刷新（**无 Interval 下拉**）
- [ ] `colorByThreshold` 在 Z01/Z04/Z06/Z03 的阈值方向正确（Z01 Z02 Z03 反向：值小→红）
- [ ] Z07 Outstanding Requests 有阈值参考线（y=10，橙色虚线）
- [ ] 颜色方案遵循 §6 Token，错误系用 `zkErrorColors`，延迟系用 `zkLatencyColors`
- [ ] 在 1280px 宽度下 7 行布局无横向滚动条
- [ ] golden signals 四象限覆盖验证（见 §5.0 映射表）；Errors 维度包含数据一致性补强指标

---

## 12. 联调踩坑记录（Phase 3 实现时必读）

本章引用 `prometheus-dashboard-prototype-spec.md` §12 中记录的三个踩坑案例——这些经验完全适用于 ZooKeeper 看板：

| 踩坑编号 |              标题              |                            适用于 ZK 的场景                            |
|------|------------------------------|------------------------------------------------------------------|
| 12.1 | `service.ts` 请求路径双前缀         | ZooKeeperMonitor 的 service.ts 路径写法相同                             |
| 12.2 | PromQL `+` 运算符被后端解码为空格       | Z08 的 `max_latency + min_latency`（若有加减运算时）；ZK 看板 PromQL 简单，此风险较低 |
| 12.3 | `@ant-design/plots` 时间轴切换不更新 | 所有 TimeSeriesPanel/AreaPanel 均需 `chartKey` prop                  |

> 详见：`docs/monitoring/design/prometheus-dashboard-prototype-spec.md` §12

**ZK 特有的额外注意**：ZooKeeper 原生 JVM metrics 使用旧版格式（`jvm_threads_current` 而非 Spring Boot 的 `jvm_threads_live_threads`），联调时若发现 R1 的 Z03/Z04 返回空数据，需确认 ZK metrics 端口 `:7000` 已正确配置 `PrometheusMetricsProvider`，以及 Prometheus scrape 配置中 job 与 instance 标签是否符合 `$instance=~"$instance",job=~"$job"` 的过滤条件。
