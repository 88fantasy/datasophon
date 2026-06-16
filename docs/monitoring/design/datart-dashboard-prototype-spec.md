# DATART 监控看板原型设计 Spec

> **文档用途**：供 Claude design 阅读，根据本 spec 设计 React + AntV G2 看板原型，并最终生成可运行的组件代码。
> **组件**：DATART 3.6.1（数据可视化平台，Spring Boot 应用）
> **数据源**：原生 `/actuator/prometheus`（Spring Boot Actuator + Micrometer，DATART server port）
> **参考 Grafana 看板**：[Spring Boot 2.1 Statistics](https://grafana.com/grafana/dashboards/10280) (ID 10280，通用 Spring Boot 基线)
> **Panel Catalog 路径**：`docs/monitoring/panel-catalog/DATART.json`（35 个面板）
> **Phase**：Phase 2 —— 原型设计阶段

---

## 1. 架构约束

```
React(AntV G2)
  └──HTTP──> datasophon-api /api/v2/prometheus/query_range
               └──PromQL──> Prometheus :9090
                              └──scrape──> DATART /actuator/prometheus
```

**前端不直连 Prometheus**。所有 PromQL 通过后端代理端点转发（详见 `prometheus-dashboard-prototype-spec.md` §1）。

### 1.1 DATART（Spring Boot Actuator / Micrometer）指标特点

- **Micrometer 命名规范**：`jvm_memory_used_bytes`、`http_server_requests_seconds_*`、`hikaricp_connections_*`、`tomcat_*`、`logback_events_total`、`process_*`、`system_*`。这是**通用 Spring Boot 基线看板**，覆盖 JVM / HTTP / 数据库连接池 / Web 容器 / 日志五大块。
- **双标签 `application` + `instance`**：`application` 区分应用（DATART），`instance` 区分节点。
- **业务指标暂无**：DATART 当前未暴露自定义业务指标（看板/数据源数量等）；如后续引入，可追加业务面板。本原型聚焦平台运行健康。
- **Histogram 延迟用 `_sum/_count` 比值**：`http_server_requests_seconds_sum / _count` 得平均响应时间；HikariCP 各时延同理。
- **四象限齐全**：与 Nginx/Kafka 不同，Spring Boot Actuator 自带完整四象限，无需补强——Response Time（Latency）、Request Count（Traffic）、ERROR logs + Tomcat error（Errors）、Heap%/CPU/连接池（Saturation）。

---

## 2. 图表类型映射字典

**完全复用 `prometheus-dashboard-prototype-spec.md` §2 中的映射字典**。

DATART 特有补充：

| Grafana chartType（catalog） | 映射组件 | 备注 |
|---|---|---|
| `singlestat`（Heap/NonHeap %） | `<Statistic>` + `colorByThreshold` | thresholds `70,90` |
| `singlestat`（Start time，×1000） | `<Statistic>` + 时间格式 | 显示为日期时间 |
| `graph`（_sum/_count 比值） | `<Line>` + s/ms 轴 | Response Time、HikariCP 时延 |
| `graph`（logback by level） | `<Line>` 多系列 | 日志事件按 level |

---

## 3. 变量 / 过滤器规范

| 变量 | PromQL 占位符 | 取值来源 | 默认值 | 说明 |
|---|---|---|---|---|
| 应用 | `$application` | `label_values(process_uptime_seconds, application)` | `datart` | 单选 |
| 实例 | `$instance` | `label_values(process_uptime_seconds{application="$application"}, instance)` | 第一个 | 多选下拉 |
| 内存池(heap) | `$memory_pool_heap` | `label_values(jvm_memory_used_bytes{area="heap"}, id)` | 第一个 | 单选，JVM heap 池 |
| 内存池(nonheap) | `$memory_pool_nonheap` | `label_values(jvm_memory_used_bytes{area="nonheap"}, id)` | 第一个 | 单选 |
| 连接池 | `$hikaricp` | `label_values(hikaricp_connections, pool)` | 第一个 | 单选，HikariCP pool |
| 时间范围 | — | 时间选择器 | `Last 1h` | 5m/15m/1h/6h/24h/7d |
| 刷新间隔 | — | — | `30s` | 自动轮询 |

> 速率窗口固定 `[5m]`（catalog 用 `irate(...[5m])`），不暴露 Interval 下拉。

---

## 4. 看板布局（24 列 Grid）

裁剪策略：catalog 35 面板 → 聚焦四黄金信号的 **18 面板**。合并 5 个日志级别为 1 个多系列面板、内存池下拉化、剔除 Direct/Mapped Buffers / Classes 等长尾（保留 catalog 供深挖）。

```
┌──────────────────────────────────────────────────────────────────────┐
│  TOOLBAR: [应用▼] [实例▼] [Heap池▼] [连接池▼]  [Last 1h▼] [🔄 30s▼] │
└──────────────────────────────────────────────────────────────────────┘

行 R1 — 概览 Stat（高度 80px，col=4 ×6）
┌────────┬────────┬────────┬────────┬────────┬────────┐
│Uptime  │Heap    │NonHeap │CPU     │HikariCP│Error   │
│        │Used %  │Used %  │Usage   │Active  │Logs/s  │
│ D01    │ D02    │ D03    │ D04    │ D05    │ D06    │
└────────┴────────┴────────┴────────┴────────┴────────┘

行 R2 — HTTP Latency/Traffic（高度 200px）
┌────────────────────────────┬────────────────────────────┐
│ D07 Request Count (RPS)    │ D08 Response Time          │
│ [Line by uri]              │ [Line 比值，s]             │
└────────────────────────────┴────────────────────────────┘

行 R3 — CPU & 内存 Saturation（高度 200px）
┌────────────────────────────┬────────────────────────────┐
│ D09 CPU / Load             │ D10 Heap Pool ($pool)      │
│ [Line system/process/load] │ [Line used/committed/max]  │
└────────────────────────────┴────────────────────────────┘

行 R4 — GC & 线程 Saturation（高度 200px，col=8 ×3）
┌──────────────────┬──────────────────┬──────────────────┐
│ D11 GC Count     │ D12 GC Pause     │ D13 JVM Threads  │
│ [Line by gc]     │ Duration (s)     │ [daemon/live/peak]│
└──────────────────┴──────────────────┴──────────────────┘

行 R5 — HikariCP 连接池 Saturation/Latency（高度 200px）
┌────────────────────────────┬────────────────────────────┐
│ D14 HikariCP Connections   │ D15 HikariCP Acquire/Usage │
│ [Line active/idle/pending] │ Time [Line 比值]           │
└────────────────────────────┴────────────────────────────┘

行 R6 — Tomcat & 日志 Traffic/Errors（高度 200px）
┌──────────────────┬──────────────────┬──────────────────┐
│ D16 Tomcat       │ D17 Tomcat       │ D18 Log Events   │
│ Threads/Sessions │ Sent/Recv Bytes  │ by Level         │
└──────────────────┴──────────────────┴──────────────────┘
```

---

## 5. 面板规格（逐面板）

### 5.0 Golden Signals 映射

| 维度 | 面板 | 说明 |
|---|---|---|
| **Latency（延迟）** | D08 Response Time、D12 GC Pause、D15 HikariCP Acquire/Usage | HTTP 响应 + GC 停顿 + 连接获取耗时 |
| **Traffic（流量）** | D07 Request Count、D17 Tomcat Bytes | 请求量与字节吞吐 |
| **Errors（错误）** | D06 Error Logs（stat）、D18 Log Events（error 系列）、Tomcat error（见说明） | 应用错误日志 + 容器错误 |
| **Saturation（饱和度）** | D02/D03 Heap/NonHeap%、D04 CPU、D05/D14 HikariCP、D09-D13 CPU/内存/GC/线程、D16 Tomcat 线程 | JVM/CPU/连接池/容器压力 |

---

### 5.1 R1 — 概览 Stat

#### D01 Uptime

| 属性 | 值 |
|---|---|
| 标题 | Uptime |
| 图表类型 | `<Statistic>` + `formatDuration` |
| Query 类型 | instant query |
| PromQL | `process_uptime_seconds{application="$application", instance="$instance"}` |
| 单位 | 秒 → d/h/m |

#### D02 Heap Used %

| 属性 | 值 |
|---|---|
| 标题 | Heap Used % |
| 图表类型 | `<Statistic>` + `colorByThreshold` |
| PromQL | `sum(jvm_memory_used_bytes{application="$application", instance="$instance", area="heap"})*100 / sum(jvm_memory_max_bytes{application="$application",instance="$instance", area="heap"})` |
| 单位 | `%` |
| 阈值 | `< 70` → 绿；`70–90` → 橙；`≥ 90` → 红（catalog `70,90`） |

#### D03 Non-Heap Used %

| 属性 | 值 |
|---|---|
| 标题 | Non-Heap Used % |
| 图表类型 | `<Statistic>` + `colorByThreshold` |
| PromQL | 同 D02，`area="nonheap"` |
| 阈值 | `70,90` |

#### D04 CPU Usage

| 属性 | 值 |
|---|---|
| 标题 | CPU Usage |
| 图表类型 | `<Statistic>` |
| PromQL | `process_cpu_usage{instance="$instance", application="$application"} * 100` |
| 单位 | `%` |

#### D05 HikariCP Active

| 属性 | 值 |
|---|---|
| 标题 | HikariCP Active |
| 图表类型 | `<Statistic>` |
| PromQL | `hikaricp_connections_active{instance="$instance", application="$application", pool="$hikaricp"}` |

#### D06 Error Logs /s

| 属性 | 值 |
|---|---|
| 标题 | Error Logs /s |
| 图表类型 | `<Statistic>` + `colorByThreshold` |
| PromQL | `sum(irate(logback_events_total{instance="$instance", application="$application", level="error"}[5m]))` |
| 阈值 | `= 0` → 绿；`> 0` → 橙；`> 1` → 红 |

---

### 5.2 R2 — HTTP

#### D07 Request Count

| 属性 | 值 |
|---|---|
| 标题 | Request Count |
| 图表类型 | `<Line>` 多系列 by uri |
| Query 类型 | range query |
| PromQL | `irate(http_server_requests_seconds_count{instance="$instance", application="$application", uri!~".*actuator.*"}[5m])` |
| 系列字段 | `uri`（排除 actuator 自身） |
| y 轴 | `req/s` |

#### D08 Response Time

| 属性 | 值 |
|---|---|
| 标题 | Response Time |
| 图表类型 | `<Line>` 多系列 by uri（比值法） |
| PromQL | `irate(http_server_requests_seconds_sum{instance="$instance", application="$application", exception="None", uri!~".*actuator.*"}[5m]) / irate(http_server_requests_seconds_count{instance="$instance", application="$application", exception="None", uri!~".*actuator.*"}[5m])` |
| 单位 | 秒 → 前端按量级显示 ms |
| 说明 | `exception="None"` 仅统计成功请求的耗时；错误请求耗时另计 |

---

### 5.3 R3 — CPU & 内存

#### D09 CPU / Load

| 属性 | 值 |
|---|---|
| 标题 | CPU / Load Average |
| 图表类型 | `<Line>` 3 系列 |
| PromQL (system cpu) | `system_cpu_usage{instance="$instance", application="$application"}` |
| PromQL (process cpu) | `process_cpu_usage{instance="$instance", application="$application"}` |
| PromQL (load1m) | `system_load_average_1m{instance="$instance", application="$application"}` |
| 系列 | `System CPU`（橙）、`Process CPU`（蓝）、`Load 1m`（灰） |

#### D10 Heap Pool ($memory_pool_heap)

| 属性 | 值 |
|---|---|
| 标题 | `$memory_pool_heap` (heap) |
| 图表类型 | `<Line>` 3 系列 |
| PromQL | `jvm_memory_used_bytes` / `jvm_memory_committed_bytes` / `jvm_memory_max_bytes`（均带 `{instance,application,id="$memory_pool_heap"}`） |
| y 轴 | bytes，`formatBytes` |
| 系列 | `Used`（蓝）、`Committed`（橙）、`Max`（灰虚线） |

---

### 5.4 R4 — GC & 线程

#### D11 GC Count

| 属性 | 值 |
|---|---|
| 标题 | GC Count |
| 图表类型 | `<Line>` 多系列 |
| PromQL | `irate(jvm_gc_pause_seconds_count{instance="$instance", application="$application"}[5m])` |
| 系列字段 | `action` / `cause`（GC 类型） |
| y 轴 | `ops/s` |

#### D12 GC Pause Duration

| 属性 | 值 |
|---|---|
| 标题 | GC Stop the World Duration |
| 图表类型 | `<Line>` |
| PromQL | `irate(jvm_gc_pause_seconds_sum{instance="$instance", application="$application"}[5m])` |
| 单位 | s（每秒 GC 停顿累计，反映 STW 占比） |

#### D13 JVM Threads

| 属性 | 值 |
|---|---|
| 标题 | JVM Threads |
| 图表类型 | `<Line>` 3 系列 |
| PromQL | `jvm_threads_daemon_threads` / `jvm_threads_live_threads` / `jvm_threads_peak_threads`（均带 `{instance,application}`） |
| 系列 | `Daemon`（灰）、`Live`（蓝）、`Peak`（橙） |

---

### 5.5 R5 — HikariCP 连接池

#### D14 HikariCP Connections

| 属性 | 值 |
|---|---|
| 标题 | HikariCP Connections |
| 图表类型 | `<Line>` 3 系列 |
| PromQL (active) | `hikaricp_connections_active{instance="$instance", application="$application", pool="$hikaricp"}` |
| PromQL (idle) | 同上，`hikaricp_connections_idle` |
| PromQL (pending) | 同上，`hikaricp_connections_pending` |
| 系列 | `Active`（蓝）、`Idle`（绿）、`Pending`（红，等待连接为饱和信号） |

#### D15 HikariCP Acquire / Usage Time

| 属性 | 值 |
|---|---|
| 标题 | HikariCP Acquire / Usage Time |
| 图表类型 | `<Line>` 2 系列（比值法） |
| PromQL (acquire) | `hikaricp_connections_acquire_seconds_sum{...,pool="$hikaricp"} / hikaricp_connections_acquire_seconds_count{...,pool="$hikaricp"}` |
| PromQL (usage) | 同上，`usage_seconds_*` |
| 单位 | 秒 → ms |
| 系列 | `Acquire Time`（橙）、`Usage Time`（蓝） |

---

### 5.6 R6 — Tomcat & 日志

#### D16 Tomcat Threads / Sessions

| 属性 | 值 |
|---|---|
| 标题 | Tomcat Threads & Sessions |
| 图表类型 | `<Line>` 3 系列 |
| PromQL (current) | `tomcat_threads_current_threads{instance="$instance", application="$application"}` |
| PromQL (busy) | `tomcat_threads_busy_threads{instance="$instance", application="$application"}` |
| PromQL (sessions) | `tomcat_sessions_active_current_sessions{instance="$instance", application="$application"}` |
| 系列 | `Current Threads`（蓝）、`Busy Threads`（橙）、`Active Sessions`（绿） |

#### D17 Tomcat Sent / Received Bytes

| 属性 | 值 |
|---|---|
| 标题 | Tomcat Sent & Received Bytes |
| 图表类型 | `<Area>` 2 系列 |
| PromQL (sent) | `irate(tomcat_global_sent_bytes_total{instance="$instance", application="$application"}[5m])` |
| PromQL (recv) | `irate(tomcat_global_received_bytes_total{instance="$instance", application="$application"}[5m])` |
| y 轴 | bytes/s，`formatBytes` |
| 系列 | `Sent`（蓝）、`Received`（绿） |

#### D18 Log Events by Level

| 属性 | 值 |
|---|---|
| 标题 | Log Events by Level |
| 图表类型 | `<Line>` 多系列 by level |
| Query 类型 | range query（合并 catalog 的 5 个日志级别面板） |
| PromQL | `sum(irate(logback_events_total{instance="$instance", application="$application"}[5m])) by (level)` |
| 系列字段 | `level`（error/warn/info/debug/trace） |
| 颜色 | `error`→红、`warn`→橙、`info`→蓝、`debug`→灰、`trace`→浅灰 |
| 说明 | 合并 catalog 的 INFO/ERROR/WARN/DEBUG/TRACE 5 个独立面板为 1 个 by-level 多系列；error/warn 是 Errors 象限主信号 |

> **Tomcat 错误计数补充**：`tomcat_global_error_total` 为 Web 容器级累计错误，可作为 D18 的补充 stat 或叠加系列（catalog 单独成 singlestat）。

---

## 6. 主题 / 样式规范

**完全复用 `prometheus-dashboard-prototype-spec.md` §6 中的颜色 Token 和工具函数**（含 `formatDuration` / `formatBytes` / `colorByThreshold`）。

DATART 特有补充：

```ts
// 日志级别配色（D18）
const logLevelColors: Record<string, string> = {
  error: '#ff4d4f',
  warn:  '#faad14',
  info:  '#1677ff',
  debug: '#8c8c8c',
  trace: '#d9d9d9',
};
```

---

## 7. 数据层接口 TypeScript 定义

**复用 `prometheus-dashboard-prototype-spec.md` §7 中全部接口定义**。

```ts
interface DatartDashboardQueryParams {
  clusterId: number;
  start: number;
  end: number;
  step: number;
  variables: {
    application: string;        // 单选，默认 "datart"
    instance: string;           // 多选
    memory_pool_heap: string;   // 单选，heap 池 id
    hikaricp: string;           // 单选，HikariCP pool 名
    // 注：固定 [5m] 窗口
  };
}
```

---

## 8. 组件树结构

```
<DatartDashboard>
  ├── <DashboardToolbar>              # application + instance + heap池 + hikaricp池 下拉 + 时间范围 + 刷新
  │
  ├── <Row R1>                        # 概览 Stat（6 个）
  │   ├── <StatPanel D01>             # Uptime（formatDuration）
  │   ├── <StatPanel D02>             # Heap Used %（70/90 阈值）
  │   ├── <StatPanel D03>             # Non-Heap Used %（70/90）
  │   ├── <StatPanel D04>             # CPU Usage
  │   ├── <StatPanel D05>             # HikariCP Active
  │   └── <StatPanel D06>             # Error Logs /s（>0 橙/>1 红）
  │
  ├── <Row R2>                        # HTTP
  │   ├── <TimeSeriesPanel D07>       # Request Count
  │   └── <TimeSeriesPanel D08>       # Response Time
  │
  ├── <Row R3>                        # CPU & 内存
  │   ├── <TimeSeriesPanel D09>       # CPU / Load
  │   └── <TimeSeriesPanel D10>       # Heap Pool（$memory_pool_heap）
  │
  ├── <Row R4>                        # GC & 线程
  │   ├── <TimeSeriesPanel D11>       # GC Count
  │   ├── <TimeSeriesPanel D12>       # GC Pause Duration
  │   └── <TimeSeriesPanel D13>       # JVM Threads
  │
  ├── <Row R5>                        # HikariCP
  │   ├── <TimeSeriesPanel D14>       # HikariCP Connections
  │   └── <TimeSeriesPanel D15>       # Acquire / Usage Time
  │
  └── <Row R6>                        # Tomcat & 日志
      ├── <TimeSeriesPanel D16>       # Tomcat Threads / Sessions
      ├── <AreaPanel D17>             # Tomcat Sent / Received Bytes
      └── <TimeSeriesPanel D18>       # Log Events by Level

# 复用的基础组件（来自 PrometheusMonitor/panels/）
StatPanel / TimeSeriesPanel / AreaPanel / DashboardToolbar / usePrometheusDashboard
```

---

## 9. 实现说明（供 Phase 3 编码参考）

### 9.1 文件路径

```
datasophon-ui-v2/src/pages/DatartMonitor/
  ├── index.tsx                     # 页面容器（6 行布局）
  ├── panelQueries.ts               # PanelDef（18 个面板）
  ├── hooks/useDatartDashboard.ts
  ├── panels/                       # 复用 PrometheusMonitor/panels/
  ├── toolbar/
  │   └── DatartDashboardToolbar.tsx # application/instance/池 多级下拉
  ├── mock/datartMockData.ts
  └── utils/                        # 复用 PrometheusMonitor/utils/（追加 logLevelColors）
```

### 9.2 PromQL 变量替换规则（DATART 版）

```ts
function replaceDatartVars(promql: string, vars: DatartDashboardQueryParams['variables']): string {
  return promql
    .replace(/\$application/g,        vars.application)
    .replace(/\$instance/g,           vars.instance || '.+')
    .replace(/\$memory_pool_heap/g,   vars.memory_pool_heap)
    .replace(/\$hikaricp/g,           vars.hikaricp);
}
```

### 9.3 级联下拉

工具栏存在级联：先选 `application` → 派生该应用的 `instance` 列表；`memory_pool_heap` / `hikaricp` 由 `jvm_memory_used_bytes{area="heap"}` 的 `id` 标签与 `hikaricp_connections` 的 `pool` 标签派生。建议默认选第一个，减少首屏交互。

### 9.4 Mock 数据要求

`datartMockData.ts` 覆盖 18 面板：
- D01 Uptime `259200`（3 天）、D02 Heap `58%`（绿）、D03 NonHeap `64%`（绿）、D04 CPU `22%`、D05 HikariCP Active `4`、D06 Error Logs `0`（绿）
- D07 Request Count: 各 uri 1–20 req/s
- D08 Response Time: 多数 uri 20–80 ms，偶有 /datart/view 慢查询 500 ms
- D09 CPU/Load: system ≈ 0.35、process ≈ 0.22、load1m ≈ 1.8
- D10 Heap Pool: G1 Old Gen used ≈ 1.2 GB / committed 1.5 GB / max 2 GB
- D11 GC Count: young ≈ 0.5/s、D12 GC Pause ≈ 0.01 s/s
- D13 Threads: daemon ≈ 40、live ≈ 60、peak ≈ 72
- D14 HikariCP: active 2–6、idle 4–8、pending 0（偶发 1 验证红）
- D15 Acquire ≈ 2 ms、Usage ≈ 15 ms
- D16 Tomcat: current ≈ 25、busy ≈ 3、sessions ≈ 18
- D17 Tomcat Bytes: sent ≈ 2 MB/s、recv ≈ 0.5 MB/s
- D18 Log Events: info ≈ 12/s、warn ≈ 0.5/s、error ≈ 0（偶发突刺）、debug ≈ 0

---

## 10. Dev 环境配置注意事项

**完全复用 `prometheus-dashboard-prototype-spec.md` §10**。后端代理端点与 PrometheusMonitor 相同。

---

## 11. 验收标准

- [ ] 全部 18 个面板（D01-D18）按 §4 布局渲染（6 行 24 列 Grid）
- [ ] R1 Stat：D02/D03 Heap/NonHeap % 用 `70,90` 阈值；D06 Error Logs `>0` 橙/`>1` 红
- [ ] D08 Response Time、D15 HikariCP 时延用 `_sum/_count` 比值法
- [ ] D10 Heap Pool 标题随 `$memory_pool_heap` 变量变化
- [ ] **D18 Log Events 合并 catalog 的 5 个日志级别面板为 1 个 by-level 多系列**，用 `logLevelColors`
- [ ] D17 Tomcat Bytes 用 `<Area>` + `formatBytes`/s
- [ ] D14 HikariCP Pending 系列配红色（等待连接为饱和信号）
- [ ] 工具栏级联：application → instance；heap池 / hikaricp池 下拉（§9.3）
- [ ] 在 1280px 宽度下 6 行布局无横向滚动条
- [ ] golden signals 四象限覆盖验证（见 §5.0）；Spring Boot Actuator 自带完整四象限，无需补强
