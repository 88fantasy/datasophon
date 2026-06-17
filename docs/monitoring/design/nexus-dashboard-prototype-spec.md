# Nexus 监控看板原型设计 Spec

> **文档用途**：供 Claude design 阅读，根据本 spec 设计 React + AntV G2 看板原型，并最终生成可运行的组件代码。
> **组件**：Sonatype Nexus Repository 3.85.0
> **数据源**：原生 Dropwizard Prometheus 端点 `/service/rest/metrics/prometheus:8081`（需 Basic Auth，`nx-metrics-all` 权限；3.81 之前为 `/service/metrics/prometheus`）
> **参考 Grafana 看板**：[Infra / Nexus](https://grafana.com/grafana/dashboards/16459) (ID 16459)
> **Panel Catalog 路径**：`docs/monitoring/panel-catalog/Nexus.json`（32 个面板）
> **Phase**：Phase 2 —— 原型设计阶段

---

## 1. 架构约束

```
React(AntV G2)
  └──HTTP──> datasophon-api /api/v2/prometheus/query_range
               └──PromQL──> Prometheus :9090
                              └──scrape(Basic Auth)──> Nexus /service/rest/metrics/prometheus:8081
```

**前端不直连 Prometheus**。所有 PromQL 通过后端代理端点转发（与 Prometheus 自监控完全相同的代理路径，详见 `prometheus-dashboard-prototype-spec.md` §1）。

### 1.1 Nexus（Dropwizard）指标特点

Nexus 内置 Dropwizard Metrics + Prometheus 导出，与 mysqld_exporter / Micrometer 体系差异很大：

- **三类指标族**：
  - `jvm_*`：JVM 运行时（heap/non-heap/GC/thread/buffers），命名为 Dropwizard 旧式（如 `jvm_memory_heap_used`、`jvm_thread_states_count`，**非** Micrometer 的 `jvm_memory_used_bytes`）。
  - `org_eclipse_jetty_*`：嵌入式 Jetty Web 容器（响应码、请求耗时、线程池）。
  - `org_sonatype_nexus_*` / `com_sonatype_nexus_*`：Nexus 组件级 timer（BlobStore、Repository、Search、Security、Ldap 等）与异常计数器。
- **Timer = 分位数 Gauge**：组件 timer 以 `{quantile="0.5"|"0.99"}` 标签直接给出分位值（秒），**不是** histogram bucket，无需 `histogram_quantile`，直接读值即可。
- **scrape 注入 `instance`/`job` 标签**：catalog 原始 PromQL **未带任何 label 选择器**（裸指标名）。本 spec 统一补 `{instance=~"$instance", job=~"$job"}`，使其支持多实例过滤。
- **⚠️ 两处脆弱点（本 spec 已修正）**：
  1. **Jetty 线程池指标名内嵌运行时 id**：`org_eclipse_jetty_util_thread_QueuedThreadPool_qtp2134194857_jobs` 中的 `qtp2134194857` 是 JVM 启动时随机分配，重启即变。必须改用正则匹配 `{__name__=~"org_eclipse_jetty_util_thread_QueuedThreadPool_qtp.*_jobs"}`。
  2. **stat 面板阈值是模板残留**：catalog 中 `Uptime`/`Readonly Enabled` 等的 `value: 80` 红阈值是 Grafana 默认值，对 ms 级 uptime / 布尔值无意义，本 spec 按语义重设。

---

## 2. 图表类型映射字典

**完全复用 `docs/monitoring/design/prometheus-dashboard-prototype-spec.md` §2 中的映射字典**。

Nexus 特有补充：

| Grafana chartType（catalog） | 映射组件 | 备注 |
|---|---|---|
| `stat`（percentunit） | `<Statistic>` + `colorByThreshold` | Heap Ratio / FD Ratio：值为 0–1，显示为 `%` |
| `stat`（布尔状态） | `<StatusStatPanel>` | Readonly Enabled：0→绿"R/W"，1→红"Read-Only" |
| `graph`（quantile 分位线） | `<Line>` 多系列 by quantile | Jetty Requests / 组件 timer：p50 / p99 双线，**直接读 `{quantile=...}` 值** |
| `graph`（响应码 rate） | `<Line>` 多系列 + status 配色 | Jetty Responses Code（1xx–5xx） |
| `graph`（内存池 bytes） | `<Area>` 堆叠 | JVM Memory Pools |

---

## 3. 变量 / 过滤器规范

| 变量 | PromQL 占位符 | 取值来源 | 默认值 | 说明 |
|---|---|---|---|---|
| 实例 | `$instance` | `label_values(jvm_vm_uptime, instance)` | `.+`（全选） | 多选下拉，对应 Nexus 节点地址 |
| Job | `$job` | `label_values(jvm_vm_uptime, job)` | `.+`（全选） | 多选下拉，对应 Prometheus job 名 |
| 时间范围 | — | 时间选择器 | `Last 1h` | 快速选择: 5m/15m/1h/6h/24h/7d |
| 刷新间隔 | — | — | `30s` | 自动轮询 |

> **无 `$interval` 变量**：Nexus 看板多为 Gauge（heap/thread/quantile 直接读值），少量 Counter（响应码/GC/异常）用固定 `[1m]`/`[5m]` 窗口 `rate`，不暴露给用户。
> **`$instance` + `$job` 与 ZooKeeper/Prometheus 看板同构**，复用同一套 `DashboardToolbar`（去掉 Interval 下拉）。

---

## 4. 看板布局（24 列 Grid）

裁剪策略：catalog 32 面板 → 聚焦四黄金信号的 **18 面板**。合并 JVM 内存池细分（Eden/Old/Survivor/Metaspace/CodeCache 5 个 → 1 个堆叠面积）、Direct/Mapped Buffers（4 个 → 1 个），剔除单组件 timer 长尾（保留 catalog 供深挖）。

```
┌──────────────────────────────────────────────────────────────────────┐
│  TOOLBAR: [实例▼]  [Job▼]      [Last 1h▼]   [🔄 30s▼]              │
└──────────────────────────────────────────────────────────────────────┘

行 R1 — 概览 Stat（高度 80px，col=4 ×6）
┌────────┬────────┬────────┬────────┬────────┬────────┐
│Uptime  │Heap    │FD      │Readonly│JVM     │Deadlock│
│        │Ratio   │Ratio   │Enabled │Threads │Threads │
│ N01    │ N02    │ N03    │ N04    │ N05    │ N06    │
└────────┴────────┴────────┴────────┴────────┴────────┘

行 R2 — 流量 & 错误 Traffic/Errors（高度 200px）
┌────────────────────────────┬────────────────────────────┐
│ N07 Jetty Responses by Code│ N08 Component Exceptions   │
│ [Line 1xx-5xx，status 配色]│ [Line rate，topk]          │
│ col=12                     │ col=12                     │
└────────────────────────────┴────────────────────────────┘

行 R3 — 延迟 Latency（高度 200px，col=8 ×3）
┌──────────────────┬──────────────────┬──────────────────┐
│ N09 Jetty Request│ N10 Component    │ N11 BlobStore Op │
│ Latency p50/p99  │ Read p99         │ Latency p99      │
│                  │ (Repo/Search/..) │ (get/create/del) │
└──────────────────┴──────────────────┴──────────────────┘

行 R4 — JVM 内存 Saturation（高度 200px）
┌────────────────────────────┬────────────────────────────┐
│ N12 JVM Heap               │ N13 JVM Memory Pools       │
│ [Line max/used/committed]  │ [Area 堆叠 by pool]        │
│ col=12                     │ col=12                     │
└────────────────────────────┴────────────────────────────┘

行 R5 — GC & 线程 Saturation（高度 200px，col=8 ×3）
┌──────────────────┬──────────────────┬──────────────────┐
│ N14 GC Collection│ N15 GC Pause     │ N16 Thread States│
│ Rate             │ Durations (ms)   │                  │
└──────────────────┴──────────────────┴──────────────────┘

行 R6 — Jetty 容量 & Non-Heap（高度 200px）
┌────────────────────────────┬────────────────────────────┐
│ N17 Jetty ThreadPool       │ N18 Non-Heap & Buffers     │
│ [Line jobs/size]           │ [Line non_heap/direct/map] │
│ col=12                     │ col=12                     │
└────────────────────────────┴────────────────────────────┘
```

---

## 5. 面板规格（逐面板）

### 5.0 Golden Signals 映射

| 维度 | 面板 | 说明 |
|---|---|---|
| **Latency（延迟）** | N09 Jetty Request、N10 Component Read p99、N11 BlobStore Op p99 | HTTP 请求耗时 + 组件/存储操作耗时（quantile 直读） |
| **Traffic（流量）** | N07 Jetty Responses by Code | 各响应码请求速率即吞吐量 |
| **Errors（错误）** | N07 中的 4xx/5xx 系列、N08 Component Exceptions、N04 Readonly Enabled | HTTP 错误响应 + 组件异常 + 只读降级（关键故障态） |
| **Saturation（饱和度）** | N02 Heap Ratio、N03 FD Ratio、N05/N06/N16 线程、N12/N13/N18 内存、N14/N15 GC、N17 线程池 | JVM 与 Jetty 容量压力 |

---

### 5.1 R1 — 概览 Stat

#### N01 Uptime

| 属性 | 值 |
|---|---|
| 标题 | Uptime |
| 图表类型 | `<Statistic>` + `formatDuration` |
| Query 类型 | instant query |
| PromQL | `jvm_vm_uptime{instance=~"$instance", job=~"$job"}` |
| 单位 | **ms** → 自动换算 d/h/m（catalog 单位为 ms） |
| 阈值（**reverse**） | `< 300000`（<5min）→ 橙（刚重启）；否则绿。**修正**：catalog 的 `value:80` 红阈值无意义，弃用 |

#### N02 Heap Ratio

| 属性 | 值 |
|---|---|
| 标题 | Heap Ratio |
| 图表类型 | `<Statistic>` + `colorByThreshold` |
| Query 类型 | instant query |
| PromQL | `jvm_memory_heap_usage{instance=~"$instance", job=~"$job"} * 100` |
| 单位 | `%`（catalog 为 percentunit 0–1，前端 ×100） |
| 阈值 | `< 80` → 绿；`80–90` → 橙；`≥ 90` → 红 |

#### N03 FileDescriptor Ratio

| 属性 | 值 |
|---|---|
| 标题 | FileDescriptor Ratio |
| 图表类型 | `<Statistic>` + `colorByThreshold` |
| Query 类型 | instant query |
| PromQL | `jvm_fd_usage{instance=~"$instance", job=~"$job"} * 100` |
| 单位 | `%` |
| 阈值 | `< 80` → 绿；`80–90` → 橙；`≥ 90` → 红 |

#### N04 Readonly Enabled

| 属性 | 值 |
|---|---|
| 标题 | Readonly Enabled |
| 图表类型 | `<StatusStatPanel>`（`<Statistic>` + `<Badge>`） |
| Query 类型 | instant query |
| PromQL | `readonly_enabled{instance=~"$instance", job=~"$job"}` |
| 阈值规则 | `= 0` → 绿 + "Read / Write"；`≥ 1` → 红 + "Read-Only"（BlobStore 进入只读，写入将失败，关键告警） |

#### N05 JVM Threads

| 属性 | 值 |
|---|---|
| 标题 | JVM Threads |
| 图表类型 | `<Statistic>` |
| Query 类型 | instant query |
| PromQL | `jvm_thread_states_count{instance=~"$instance", job=~"$job"}` |
| 样式 | 大字体，蓝色 |

#### N06 Deadlock Threads

| 属性 | 值 |
|---|---|
| 标题 | Deadlock Threads |
| 图表类型 | `<Statistic>` + `colorByThreshold` |
| Query 类型 | instant query |
| PromQL | `jvm_thread_states_deadlock_count{instance=~"$instance", job=~"$job"}` |
| 阈值 | `= 0` → 绿；`≥ 1` → 红（线程死锁） |

---

### 5.2 R2 — 流量 & 错误（N07 / N08）

#### N07 Jetty Responses by Code

| 属性 | 值 |
|---|---|
| 标题 | Jetty Responses by Code |
| 图表类型 | `<Line>` 多系列（5 条 PromQL） |
| Query 类型 | range query |
| PromQL (1xx) | `rate(org_eclipse_jetty_webapp_WebAppContext_1xx_responses_total{instance=~"$instance", job=~"$job"}[1m])` |
| PromQL (2xx) | 同上，`1xx` → `2xx` |
| PromQL (3xx/4xx/5xx) | 同上，依次替换 |
| 系列 | `1xx`（灰）、`2xx`（绿）、`3xx`（蓝）、`4xx`（橙）、`5xx`（红） |
| y 轴 | `/s` |

> 配色复用 APISIX spec 的 `STATUS_CODE_COLORS`（2xx 绿 / 3xx 蓝 / 4xx 橙 / 5xx 红）。

#### N08 Component Exceptions

| 属性 | 值 |
|---|---|
| 标题 | Component Exceptions |
| 图表类型 | `<Line>` 多系列 |
| Query 类型 | range query |
| PromQL | `topk(10, sum by (__name__) (rate({__name__=~".*_exceptions_total", instance=~"$instance", job=~"$job"}[5m])) > 0)` |
| 系列字段 | `__name__`（异常计数器名，前端截取 `*Component_<op>` 段做 legend） |
| y 轴 | `/s` |
| 说明 | catalog 列举了 20 个组件的 `*_exceptions_total`（见 catalog `Component Exceptions`）；原型用 `__name__` 正则聚合 + topk(10) 取活跃异常，避免硬编码 20 条 PromQL |

---

### 5.3 R3 — 延迟

#### N09 Jetty Request Latency

| 属性 | 值 |
|---|---|
| 标题 | Jetty Request Latency |
| 图表类型 | `<Line>` 2 系列 by quantile |
| Query 类型 | range query（quantile 直读，**非** histogram_quantile） |
| PromQL (p50) | `org_eclipse_jetty_webapp_WebAppContext_requests{quantile="0.5", instance=~"$instance", job=~"$job"} * 1000` |
| PromQL (p99) | 同上，`quantile="0.99"` |
| 单位 | catalog 为 s，前端 `×1000` 转 `ms` |
| 系列 | `p50`（蓝）、`p99`（红） |

#### N10 Component Read Latency p99

| 属性 | 值 |
|---|---|
| 标题 | Component Read Latency (p99) |
| 图表类型 | `<Line>` 多系列 |
| Query 类型 | range query |
| PromQL (Repository) | `org_sonatype_nexus_coreui_RepositoryComponent_read_timer{quantile="0.99", instance=~"$instance", job=~"$job"} * 1000` |
| PromQL (Search) | `org_sonatype_nexus_coreui_SearchComponent_read_timer{quantile="0.99", ...} * 1000` |
| PromQL (Browse) | `org_sonatype_nexus_coreui_BrowseComponent_read_timer{quantile="0.99", ...} * 1000` |
| PromQL (Security) | `org_sonatype_nexus_rapture_internal_security_SecurityComponent_getPermissions_timer{quantile="0.99", ...} * 1000` |
| 单位 | `ms` |
| 系列 | `Repository` / `Search` / `Browse` / `Security`（多色循环） |

#### N11 BlobStore Op Latency p99

| 属性 | 值 |
|---|---|
| 标题 | FileBlobStore Op Latency (p99) |
| 图表类型 | `<Line>` 多系列 |
| Query 类型 | range query |
| PromQL (get) | `org_sonatype_nexus_blobstore_file_FileBlobStore_get_timer{quantile="0.99", instance=~"$instance", job=~"$job"} * 1000` |
| PromQL (create) | 同上，`create_timer` |
| PromQL (delete) | 同上，`delete_timer` |
| PromQL (copy) | 同上，`copy_timer` |
| 单位 | `ms` |
| 系列 | `get` / `create` / `delete` / `copy`（多色循环） |

---

### 5.4 R4 — JVM 内存

#### N12 JVM Heap

| 属性 | 值 |
|---|---|
| 标题 | JVM Heap |
| 图表类型 | `<Line>` 3 系列 |
| Query 类型 | range query |
| PromQL (max) | `jvm_memory_heap_max{instance=~"$instance", job=~"$job"}` |
| PromQL (used) | `jvm_memory_heap_used{instance=~"$instance", job=~"$job"}` |
| PromQL (committed) | `jvm_memory_heap_committed{instance=~"$instance", job=~"$job"}` |
| y 轴 | bytes，自动换算 |
| 系列 | `Max`（灰虚线，上限）、`Used`（蓝）、`Committed`（橙） |

#### N13 JVM Memory Pools

| 属性 | 值 |
|---|---|
| 标题 | JVM Memory Pools (used) |
| 图表类型 | `<Area>` 堆叠 |
| Query 类型 | range query（5 条 PromQL，used 值） |
| PromQL | `jvm_memory_pools_PS_Eden_Space_used` / `jvm_memory_pools_PS_Old_Gen_used` / `jvm_memory_pools_PS_Survivor_Space_used` / `jvm_memory_pools_Metaspace_used` / `jvm_memory_pools_Code_Cache_used`（各加 `{instance=~"$instance", job=~"$job"}`） |
| y 轴 | bytes，自动换算 |
| 系列 | `Eden` / `Old Gen` / `Survivor` / `Metaspace` / `Code Cache` |
| 说明 | 合并 catalog 中 5 个独立 Pool 面板为 1 个堆叠面积，直观看各代占比 |

---

### 5.5 R5 — GC & 线程

#### N14 GC Collection Rate

| 属性 | 值 |
|---|---|
| 标题 | GC Collection Rate |
| 图表类型 | `<Line>` 2 系列 |
| Query 类型 | range query |
| PromQL (MarkSweep) | `rate(jvm_garbage_collectors_PS_MarkSweep_count{instance=~"$instance", job=~"$job"}[1m])` |
| PromQL (Scavenge) | `rate(jvm_garbage_collectors_PS_Scavenge_count{instance=~"$instance", job=~"$job"}[1m])` |
| y 轴 | `ops/s` |
| 系列 | `MarkSweep`（Full GC，红）、`Scavenge`（Young GC，蓝） |

#### N15 GC Pause Durations

| 属性 | 值 |
|---|---|
| 标题 | GC Pause Durations |
| 图表类型 | `<Line>` 2 系列 |
| Query 类型 | range query |
| PromQL (MarkSweep) | `rate(jvm_garbage_collectors_PS_MarkSweep_time{instance=~"$instance", job=~"$job"}[5m]) / rate(jvm_garbage_collectors_PS_MarkSweep_count{instance=~"$instance", job=~"$job"}[5m])` |
| PromQL (Scavenge) | 同上，`Scavenge` |
| 单位 | `ms`（每次 GC 平均暂停时长） |
| 系列 | `MarkSweep`（红）、`Scavenge`（蓝） |

#### N16 Thread States

| 属性 | 值 |
|---|---|
| 标题 | Thread States |
| 图表类型 | `<Line>` 多系列 |
| Query 类型 | range query |
| PromQL | `jvm_thread_states_runnable_count` / `jvm_thread_states_blocked_count` / `jvm_thread_states_waiting_count` / `jvm_thread_states_timed_waiting_count`（各加 `{instance=~"$instance", job=~"$job"}`） |
| 系列 | `Runnable`（绿）、`Blocked`（红）、`Waiting`（灰）、`Timed Waiting`（蓝） |

---

### 5.6 R6 — Jetty 容量 & Non-Heap

#### N17 Jetty ThreadPool

| 属性 | 值 |
|---|---|
| 标题 | Jetty ThreadPool |
| 图表类型 | `<Line>` 2 系列 |
| Query 类型 | range query |
| PromQL (jobs) | `{__name__=~"org_eclipse_jetty_util_thread_QueuedThreadPool_qtp.*_jobs", instance=~"$instance", job=~"$job"}` |
| PromQL (size) | `{__name__=~"org_eclipse_jetty_util_thread_QueuedThreadPool_qtp.*_size", instance=~"$instance", job=~"$job"}` |
| 系列 | `Queued Jobs`（橙，积压队列）、`Pool Size`（蓝） |
| 说明 | **必须用 `__name__` 正则**匹配，规避指标名内嵌的运行时 `qtp<随机id>`（见 §1.1 脆弱点 1） |

#### N18 Non-Heap & Buffers

| 属性 | 值 |
|---|---|
| 标题 | Non-Heap & Buffers |
| 图表类型 | `<Line>` 多系列 |
| Query 类型 | range query |
| PromQL (non-heap used) | `jvm_memory_non_heap_used{instance=~"$instance", job=~"$job"}` |
| PromQL (direct) | `jvm_buffers_direct_used{instance=~"$instance", job=~"$job"}` |
| PromQL (mapped) | `jvm_buffers_mapped_used{instance=~"$instance", job=~"$job"}` |
| y 轴 | bytes，自动换算 |
| 系列 | `Non-Heap`（蓝）、`Direct Buffers`（橙）、`Mapped Buffers`（绿） |

---

## 6. 主题 / 样式规范

**完全复用 `prometheus-dashboard-prototype-spec.md` §6 中的颜色 Token 和工具函数**（`CHART_COLORS`、`colorByThreshold`、`formatBytes`、`TIME_AXIS_CONFIG`、tooltip 格式），并复用 APISIX spec 的 `STATUS_CODE_COLORS`。

```ts
import { CHART_COLORS, colorByThreshold, formatBytes } from '../utils/formatters';
import { STATUS_CODE_COLORS } from '../utils/statusColors';   // 复用 APISIX 配色
```

Nexus 特有补充：

```ts
// Memory Pool 配色（堆叠面积，按代际冷暖区分）
const jvmPoolColors: Record<string, string> = {
  'Eden':        '#69b1ff',  // 浅蓝（新生）
  'Survivor':    '#95de64',  // 浅绿
  'Old Gen':     '#1677ff',  // 深蓝（老年代主体）
  'Metaspace':   '#faad14',  // 橙（类元数据）
  'Code Cache':  '#722ed1',  // 紫（JIT）
};

// 复用 MySQL spec 的 formatDuration / colorByThresholdReverse（utils/formatters.ts）
```

---

## 7. 数据层接口 TypeScript 定义

**复用 `prometheus-dashboard-prototype-spec.md` §7 中全部接口定义**（`PrometheusVector`、`PrometheusMatrix`、`TimeSeriesPoint`、`StatValue`）。

Nexus 特有补充：

```ts
interface NexusDashboardQueryParams {
  clusterId: number;
  start: number;      // unix timestamp (seconds)
  end: number;        // unix timestamp (seconds)
  step: number;       // 建议 = (end - start) / 200
  variables: {
    instance: string;   // 正则，如 ".+" 或 "10.0.0.1:8081"
    job: string;        // 正则，如 ".+" 或 "nexus"
    // 注：无 interval 变量（固定 [1m]/[5m] 窗口）
  };
}
```

---

## 8. 组件树结构

```
<NexusDashboard>                      # 页面容器，管理 variables + time range + refresh
  ├── <DashboardToolbar>              # 引用 `_shared/DashboardToolbar.tsx`（children 注入 Instance + Job 选择器）
  │
  ├── <Row R1>                        # 概览 Stat（6 个 Stat 面板）
  │   ├── <StatPanel N01>             # Uptime（formatDuration，reverse 阈值）
  │   ├── <StatPanel N02>             # Heap Ratio（×100，阈值染色）
  │   ├── <StatPanel N03>             # FD Ratio（×100，阈值染色）
  │   ├── <StatusStatPanel N04>       # Readonly Enabled（0 绿 / 1 红）
  │   ├── <StatPanel N05>             # JVM Threads
  │   └── <StatPanel N06>             # Deadlock Threads（≥1 红）
  │
  ├── <Row R2>                        # 流量 & 错误
  │   ├── <TimeSeriesPanel N07>       # Jetty Responses by Code（status 配色）
  │   └── <TimeSeriesPanel N08>       # Component Exceptions（topk）
  │
  ├── <Row R3>                        # 延迟
  │   ├── <TimeSeriesPanel N09>       # Jetty Request Latency p50/p99
  │   ├── <TimeSeriesPanel N10>       # Component Read p99
  │   └── <TimeSeriesPanel N11>       # BlobStore Op p99
  │
  ├── <Row R4>                        # JVM 内存
  │   ├── <TimeSeriesPanel N12>       # JVM Heap（max/used/committed）
  │   └── <AreaPanel N13>             # JVM Memory Pools（堆叠）
  │
  ├── <Row R5>                        # GC & 线程
  │   ├── <TimeSeriesPanel N14>       # GC Collection Rate
  │   ├── <TimeSeriesPanel N15>       # GC Pause Durations
  │   └── <TimeSeriesPanel N16>       # Thread States
  │
  └── <Row R6>                        # Jetty 容量 & Non-Heap
      ├── <TimeSeriesPanel N17>       # Jetty ThreadPool（__name__ 正则）
      └── <TimeSeriesPanel N18>       # Non-Heap & Buffers

# 复用的基础组件（来自 `monitor/_shared/panels/`）
StatPanel / StatusStatPanel / TimeSeriesPanel / AreaPanel / DashboardToolbar / useDashboardData ← 均来自 `monitor/_shared/`
```

---

## 9. 实现说明（供 Phase 3 编码参考）

### 9.1 文件路径

```
datasophon-ui-v2/src/pages/NexusMonitor/
  ├── index.tsx                     # 页面容器（6 行布局）
  ├── panelQueries.ts               # PanelDef（18 个面板的 instant/range/multi-range 定义）
  ├── hooks/
  │   └── useNexusDashboard.ts      # 调用 `useDashboardData`（`_shared/useDashboardData.ts`）
  ├── panels/                       # 无此目录 — 直接从 `../../_shared/panels/` import
  ├── toolbar/
  │   └── NexusDashboardToolbar.tsx # 复用 ZK 版（Instance + Job，无 Interval）
  ├── mock/
  │   └── nexusMockData.ts          # 确定性伪随机静态数据
  └── utils/                        
```

### 9.2 PromQL 变量替换规则（Nexus 版）

```ts
function replaceNexusVars(promql: string, vars: NexusDashboardQueryParams['variables']): string {
  return promql
    .replace(/\$instance/g, vars.instance || '.+')
    .replace(/\$job/g,      vars.job      || '.+');
  // 注：无 $interval 替换（固定窗口）
}
```

### 9.3 Timer quantile 直读说明

Nexus 组件/Jetty timer 以 `{quantile="0.5"|"0.99"}` 标签直接暴露分位值（单位秒），**不要**对其用 `histogram_quantile`（它们不是 `_bucket`）。前端只需：① 加 `instance`/`job` 过滤；② `× 1000` 转 ms。这与 ZooKeeper 的 `avg_latency`（Gauge 直读）同理，区别仅在多了 `quantile` 维度。

### 9.4 `__name__` 正则匹配注意

N08（Component Exceptions）与 N17（Jetty ThreadPool）使用 `{__name__=~"..."}` 形式匹配指标名。后端 Prometheus 代理需原样转发该查询；前端 `service.ts` 对 query 做 `encodeURIComponent`，注意正则中的 `.*` 不要被二次编码（参考 prometheus-dashboard-prototype-spec.md §12 的 PromQL 编码踩坑）。

### 9.5 Mock 数据要求

`nexusMockData.ts` 覆盖全部 18 个面板：

**Stat 面板（N01-N06，instant 值）：**
- N01 Uptime: `604800000`（7 天，ms，绿色）
- N02 Heap Ratio: `42`（%，绿色）
- N03 FD Ratio: `18`（%，绿色）
- N04 Readonly Enabled: `0`（绿色 "Read / Write"）
- N05 JVM Threads: `186`
- N06 Deadlock Threads: `0`（绿色）

**Range 面板（N07-N18）：**
- N07 Responses: 2xx ≈ 12/s 为主、3xx ≈ 1/s、4xx 偶发 0.2/s、5xx ≈ 0（偶发突刺验证红色）、1xx ≈ 0
- N08 Exceptions: 多数为 0，偶发 1 条 `SearchComponent_read` 突刺
- N09 Jetty Request: p50 ≈ 8ms、p99 ≈ 45ms
- N10 Component Read: Repository p99 ≈ 30ms、Search p99 ≈ 120ms、Browse ≈ 25ms、Security ≈ 5ms
- N11 BlobStore: get p99 ≈ 12ms、create ≈ 35ms、delete ≈ 20ms、copy ≈ 50ms
- N12 Heap: max 4 GB、used 1.5–1.8 GB 锯齿（GC 回收）、committed 2 GB
- N13 Pools: Old Gen ≈ 1.2 GB、Eden 0–400 MB 锯齿、Survivor ≈ 30 MB、Metaspace ≈ 180 MB、Code Cache ≈ 90 MB
- N14 GC Rate: Scavenge ≈ 0.05 ops/s、MarkSweep ≈ 0（健康）
- N15 GC Pause: Scavenge ≈ 15ms、MarkSweep 偶发 80ms
- N16 Thread States: Runnable ≈ 40、Timed Waiting ≈ 120、Waiting ≈ 25、Blocked ≈ 0
- N17 ThreadPool: size ≈ 200、jobs（积压）≈ 0–3
- N18 Non-Heap: non-heap used ≈ 280 MB、direct ≈ 64 MB、mapped ≈ 0

---

## 10. Dev 环境配置注意事项

**完全复用 `prometheus-dashboard-prototype-spec.md` §10 中的三项配置**（publicPath、proxy bypass、mock 路径对齐）。

Nexus 看板无额外 dev 环境差异：后端代理端点路径与 PrometheusMonitor 完全相同（`/ddh/api/v2/prometheus/query` 和 `/query_range`），mock 文件路径写法相同。

> **生产采集提醒（非 dev 范围）**：Prometheus 抓取 Nexus 时需配置 `metrics_path: /service/rest/metrics/prometheus` + Basic Auth（`nx-metrics-all` 权限账号）。3.85.0 路径为 `/service/rest/metrics/prometheus`（3.81 之前为 `/service/metrics/prometheus`，旧路径有重定向但部分 scrape 客户端不跟随）。此为采集配置，不影响前端原型。

---

## 11. 验收标准

Phase 2 原型（mock 阶段）完成后，需满足：

- [ ] 全部 18 个面板（N01-N18）按 §4 布局渲染（6 行 24 列 Grid）
- [ ] R1 行 6 个 Stat 面板使用 antd `<Statistic>`；N04 用 `<StatusStatPanel>`（0 绿 "Read/Write" / 1 红 "Read-Only"）
- [ ] N01 Uptime 用 `formatDuration` 显示（ms 输入，输出如 `7d 0h`），catalog 残留的 `value:80` 红阈值未被使用
- [ ] N02/N03 Ratio 面板已 `×100` 转百分比显示（catalog 为 0–1 percentunit）
- [ ] N07 Jetty Responses 按 status 配色（5xx 红 / 4xx 橙 / 3xx 蓝 / 2xx 绿 / 1xx 灰）
- [ ] N09/N10/N11 Latency 面板为 quantile 直读（`{quantile="..."}` × 1000），**未**误用 `histogram_quantile`
- [ ] **N17 Jetty ThreadPool 使用 `{__name__=~"...qtp.*_jobs"}` 正则**，未硬编码 `qtp2134194857`
- [ ] N08 Component Exceptions 用 `{__name__=~".*_exceptions_total"}` + topk(10) 聚合，未硬编码 20 条 PromQL
- [ ] N13 JVM Memory Pools 使用 `<Area>` 堆叠 + `jvmPoolColors` 配色 + `formatBytes`
- [ ] 全部 PromQL 已补 `{instance=~"$instance", job=~"$job"}` 过滤（catalog 原始裸指标名已修正）
- [ ] 工具栏：实例多选 + Job 多选 + 时间范围 + 刷新（**无 Interval 下拉**）
- [ ] 在 1280px 宽度下 6 行布局无横向滚动条
- [ ] golden signals 四象限覆盖验证（见 §5.0 映射表）；Errors 维度含 Readonly 降级态
