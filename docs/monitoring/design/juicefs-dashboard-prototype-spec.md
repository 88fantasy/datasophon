# JuiceFS 监控看板原型设计 Spec

> **文档用途**：供 Claude design 阅读，根据本 spec 设计 React + AntV G2 看板原型，并最终生成可运行的组件代码。
> **组件**：JuiceFS 1.3.1
> **数据源**：原生 `/metrics:9567`（客户端内置 Prometheus 端点，无需 exporter）
> **参考 Grafana 看板**：[JuiceFS Dashboard](https://grafana.com/grafana/dashboards/20794) (ID 20794，juicedata 官方维护)
> **Panel Catalog 路径**：`docs/monitoring/panel-catalog/JuiceFS.json`（25 个面板）
> **Phase**：Phase 2 —— 原型设计阶段

---

## 1. 架构约束

```
React(AntV G2)
  └──HTTP──> datasophon-api /api/v2/prometheus/query_range
               └──PromQL──> Prometheus :9090
                              └──scrape──> JuiceFS Client /metrics:9567
```

**前端不直连 Prometheus**。所有 PromQL 通过后端代理端点转发（详见 `prometheus-dashboard-prototype-spec.md` §1）。

### 1.1 JuiceFS 指标特点

- **统一前缀 `juicefs_`**：官方客户端内置导出，指标名与官方 `docs/en/grafana_template.json` 完全一致，无需任何映射。
- **核心维度 `vol_name`**：JuiceFS 以**卷（volume）** 为监控主体，绝大多数指标带 `{vol_name="$name"}`。看板首选变量是卷名 `$name`，而非 instance。
- **多挂载点**：同一卷可被多个客户端（不同 `instance` + `mp` 挂载点）挂载，吞吐/延迟类按 `by (instance, mp)` 聚合。
- **Histogram 延迟**：延迟用 `*_durations_histogram_seconds_{sum,count}`，**以比值法算平均延迟**：`sum(rate(_sum)) / sum(rate(_count)) * 1e6` → 微秒（µs）。**不是** `histogram_quantile`。
- **健壮性过滤**：官方 PromQL 对速率类加了 `< 5000000000` 上限过滤（剔除客户端重启瞬间的异常尖峰），本 spec 保留。
- **错误信号**：`juicefs_object_request_errors`（对象存储后端请求错误）、`juicefs_transaction_restart`（元数据事务重试）是仅有的两个显式错误指标，本 spec 将其提升为独立错误面板（catalog 原把 errors 混在 Objects Requests 内）。

---

## 2. 图表类型映射字典

**完全复用 `prometheus-dashboard-prototype-spec.md` §2 中的映射字典**。

JuiceFS 特有补充：

| Grafana chartType（catalog） | 映射组件 | 备注 |
|---|---|---|
| `table`（Uptime） | `<Statistic>` + `formatDuration` | 单卷场景退化为 stat；多客户端时取 max |
| `timeseries`（histogram 比值延迟） | `<Line>` + µs 轴 | `rate(_sum)/rate(_count)*1e6`，y 轴 µs |
| `timeseries`（hit ratio） | `<Line>` + percent 轴 | Block Cache Hit Ratio，0–100% |
| `timeseries`（binBps） | `<Area>` + `formatBytes`/s | IO Throughput、Compacted Data |

---

## 3. 变量 / 过滤器规范

| 变量 | PromQL 占位符 | 取值来源 | 默认值 | 说明 |
|---|---|---|---|---|
| 卷名 | `$name` | `label_values(juicefs_uptime, vol_name)` | 第一个卷 | **单选**下拉（看板以卷为主体） |
| 速率窗口 | `$__rate_interval` | 由时间范围自动计算 | — | 不暴露给用户 |
| 时间范围 | — | 时间选择器 | `Last 1h` | 5m/15m/1h/6h/24h/7d |
| 刷新间隔 | — | — | `30s` | 自动轮询 |

> **主变量是 `$name`（卷）非 `$instance`**：与 MySQL/Nexus 不同，JuiceFS 工具栏第一个下拉是卷名单选。`instance`/`mp` 不作为顶层过滤，而是在面板内 `by (instance, mp)` 分系列展示（同一卷的多个挂载点）。
> catalog 中 `Transaction Restarts` 用 `vol_name=~"$name"`（正则），其余用 `vol_name="$name"`（精确）；本 spec 统一为精确匹配 `="$name"`（单选场景）。

---

## 4. 看板布局（24 列 Grid）

裁剪策略：catalog 25 面板 → 聚焦四黄金信号的 **17 面板**。合并 Staging 三连（Blocks/Usage/Delay）、剔除 Go threads 等长尾，并将 errors 拆为独立面板。

```
┌──────────────────────────────────────────────────────────────────────┐
│  TOOLBAR: [卷名▼(单选)]          [Last 1h▼]   [🔄 30s▼]            │
└──────────────────────────────────────────────────────────────────────┘

行 R1 — 概览 Stat（高度 80px，col=4 ×6）
┌────────┬────────┬────────┬────────┬────────┬────────┐
│Uptime  │Data    │Files   │Client  │Cache   │Staging │
│        │Size    │        │Sessions│Hit %   │Blocks  │
│ J01    │ J02    │ J03    │ J04    │ J05    │ J06    │
└────────┴────────┴────────┴────────┴────────┴────────┘

行 R2 — 流量 Traffic（高度 200px）
┌────────────────────────────┬────────────────────────────┐
│ J07 Operations             │ J08 IO Throughput          │
│ [Line by instance]         │ [Area read/write，binBps]  │
└────────────────────────────┴────────────────────────────┘

行 R3 — 延迟 Latency（高度 200px，col=8 ×3）
┌──────────────────┬──────────────────┬──────────────────┐
│ J09 IO Latency   │ J10 Transaction  │ J11 Objects      │
│ (µs)             │ Latency (µs)     │ Latency (µs)     │
└──────────────────┴──────────────────┴──────────────────┘

行 R4 — 对象存储 & 错误 Traffic/Errors（高度 200px）
┌────────────────────────────┬────────────────────────────┐
│ J12 Objects Requests       │ J13 Errors & Restarts ★    │
│ [Line by method]           │ [Line obj_errors/tx_restart]│
└────────────────────────────┴────────────────────────────┘

行 R5 — 缓存 Saturation（高度 200px）
┌──────────────────┬──────────────────┬──────────────────┐
│ J14 Block Cache  │ J15 Block Cache  │ J16 Objects      │
│ Size             │ Hit Ratio (%)    │ Throughput (Bps) │
└──────────────────┴──────────────────┴──────────────────┘

行 R6 — 客户端资源 & 暂存 Saturation（高度 200px）
┌────────────────────────────┬────────────────────────────┐
│ J17 Client CPU & Memory    │ (Staging 合并入 J06 stat + │
│ [Line cpu%/mem]            │  说明，见 §5.6)            │
└────────────────────────────┴────────────────────────────┘
```

---

## 5. 面板规格（逐面板）

### 5.0 Golden Signals 映射

| 维度 | 面板 | 说明 |
|---|---|---|
| **Latency（延迟）** | J09 IO、J10 Transaction、J11 Objects（µs，histogram 比值） | FUSE 操作 / 元数据事务 / 对象存储三层延迟 |
| **Traffic（流量）** | J07 Operations、J08 IO Throughput、J12 Objects Requests、J16 Objects Throughput | 操作数与读写吞吐 |
| **Errors（错误）** | J13 Object Request Errors + Transaction Restarts ★ | 后端请求错误 + 元数据事务重试（补强为独立面板） |
| **Saturation（饱和度）** | J05/J14/J15 Block Cache、J06 Staging、J17 CPU/Memory、J02/J03 容量 | 缓存命中、暂存积压、客户端资源、容量 |

---

### 5.1 R1 — 概览 Stat

#### J01 Uptime

| 属性 | 值 |
|---|---|
| 标题 | Uptime |
| 图表类型 | `<Statistic>` + `formatDuration` |
| Query 类型 | instant query |
| PromQL | `max(juicefs_uptime{vol_name="$name"})` |
| 单位 | 秒 → d/h/m |
| 阈值（reverse） | `< 300` → 橙（刚启动）；否则绿。**修正**：catalog 的 `value:80` 红阈值弃用 |

#### J02 Data Size

| 属性 | 值 |
|---|---|
| 标题 | Data Size |
| 图表类型 | `<Statistic>` + `formatBytes` |
| Query 类型 | instant query |
| PromQL | `avg(juicefs_used_space{vol_name="$name"})` |
| 单位 | bytes |

#### J03 Files

| 属性 | 值 |
|---|---|
| 标题 | Files |
| 图表类型 | `<Statistic>` |
| Query 类型 | instant query |
| PromQL | `avg(juicefs_used_inodes{vol_name="$name"})` |
| 单位 | 整数（千位分隔） |

#### J04 Client Sessions

| 属性 | 值 |
|---|---|
| 标题 | Client Sessions |
| 图表类型 | `<Statistic>` |
| Query 类型 | instant query |
| PromQL | `count(juicefs_uptime{vol_name="$name"})` |
| 说明 | 当前挂载该卷的客户端数 |

#### J05 Cache Hit %

| 属性 | 值 |
|---|---|
| 标题 | Block Cache Hit % |
| 图表类型 | `<Statistic>` + `colorByThreshold` |
| Query 类型 | instant query |
| PromQL | `sum(rate(juicefs_blockcache_hits{vol_name="$name"}[$__rate_interval])) * 100 / (sum(rate(juicefs_blockcache_hits{vol_name="$name"}[$__rate_interval])) + sum(rate(juicefs_blockcache_miss{vol_name="$name"}[$__rate_interval])))` |
| 单位 | `%` |
| 阈值（reverse） | `< 70` → 红；`70–90` → 橙；`≥ 90` → 绿 |

#### J06 Staging Blocks

| 属性 | 值 |
|---|---|
| 标题 | Staging Blocks |
| 图表类型 | `<Statistic>` + `colorByThreshold` |
| Query 类型 | instant query |
| PromQL | `sum(juicefs_staging_blocks{vol_name="$name"})` |
| 阈值 | `= 0` → 绿；持续 `> 0` 且增长 → 橙（写缓冲积压，回写未跟上） |

---

### 5.2 R2 — 流量

#### J07 Operations

| 属性 | 值 |
|---|---|
| 标题 | Operations |
| 图表类型 | `<Line>` 多系列 by instance |
| Query 类型 | range query |
| PromQL | `sum(rate(juicefs_fuse_ops_durations_histogram_seconds_count{vol_name="$name"}[$__rate_interval]) < 5000000000) by (instance)` |
| y 轴 | `ops/s` |

#### J08 IO Throughput

| 属性 | 值 |
|---|---|
| 标题 | IO Throughput |
| 图表类型 | `<Area>` 2 系列 |
| Query 类型 | range query |
| PromQL (write) | `sum(rate(juicefs_fuse_written_size_bytes_sum{vol_name="$name"}[$__rate_interval]) < 5000000000) by (instance)` |
| PromQL (read) | `sum(rate(juicefs_fuse_read_size_bytes_sum{vol_name="$name"}[$__rate_interval]) < 5000000000) by (instance)` |
| y 轴 | binBps，`formatBytes`/s |
| 系列 | `Write`（蓝）、`Read`（绿） |

---

### 5.3 R3 — 延迟

> 三个面板均为 histogram 比值法（`*1e6` 转 µs），结构相同，仅指标族不同。

#### J09 IO Latency

| 属性 | 值 |
|---|---|
| 标题 | IO Latency |
| 图表类型 | `<Line>` by instance,mp |
| Query 类型 | range query |
| PromQL | `sum(rate(juicefs_fuse_ops_durations_histogram_seconds_sum{vol_name="$name"}[$__rate_interval])) by (instance,mp) * 1000000 / sum(rate(juicefs_fuse_ops_durations_histogram_seconds_count{vol_name="$name"}[$__rate_interval])) by (instance,mp)` |
| y 轴 | `µs` |

#### J10 Transaction Latency

| 属性 | 值 |
|---|---|
| 标题 | Transaction Latency |
| 图表类型 | `<Line>` |
| PromQL | 同 J09，指标族换为 `juicefs_transaction_durations_histogram_seconds_{sum,count}` |
| y 轴 | `µs` |

#### J11 Objects Latency

| 属性 | 值 |
|---|---|
| 标题 | Objects Latency |
| 图表类型 | `<Line>` by instance |
| PromQL | `sum(rate(juicefs_object_request_durations_histogram_seconds_sum{vol_name="$name"}[$__rate_interval])) by (instance) * 1000000 / sum(rate(juicefs_object_request_durations_histogram_seconds_count{vol_name="$name"}[$__rate_interval])) by (instance)` |
| y 轴 | `µs` |

---

### 5.4 R4 — 对象存储 & 错误

#### J12 Objects Requests

| 属性 | 值 |
|---|---|
| 标题 | Objects Requests |
| 图表类型 | `<Line>` 多系列 by method |
| Query 类型 | range query |
| PromQL | `sum(rate(juicefs_object_request_durations_histogram_seconds_count{vol_name="$name"}[$__rate_interval])) by (method)` |
| 系列字段 | `method`（GET / PUT / DELETE / HEAD / LIST） |
| y 轴 | `req/s` |

#### J13 Errors & Restarts ★（补强）

| 属性 | 值 |
|---|---|
| 标题 | Object Errors & Transaction Restarts |
| 图表类型 | `<Line>` 2 系列 |
| Query 类型 | range query |
| PromQL (obj errors) | `sum(rate(juicefs_object_request_errors{vol_name="$name"}[$__rate_interval]))` |
| PromQL (tx restarts) | `sum(rate(juicefs_transaction_restart{vol_name="$name"}[$__rate_interval])) by (instance)` |
| 系列 | `Object Request Errors`（红）、`Transaction Restarts`（橙） |
| 说明 | ★ catalog 把 errors 混在 Objects Requests 面板里；本 spec 拆为独立 Errors 面板，补齐黄金信号 Error 维度 |

---

### 5.5 R5 — 缓存

#### J14 Block Cache Size

| 属性 | 值 |
|---|---|
| 标题 | Block Cache Size |
| 图表类型 | `<Line>` by instance,mp |
| PromQL | `sum(juicefs_blockcache_bytes{vol_name="$name"}) by (instance,mp)` |
| y 轴 | bytes，`formatBytes` |

#### J15 Block Cache Hit Ratio

| 属性 | 值 |
|---|---|
| 标题 | Block Cache Hit Ratio |
| 图表类型 | `<Line>` 2 系列（按次数 / 按字节） |
| PromQL (by count) | `sum(rate(juicefs_blockcache_hits{vol_name="$name"}[$__rate_interval])) by (instance,mp) *100 / (sum(rate(juicefs_blockcache_hits{vol_name="$name"}[$__rate_interval])) by (instance,mp) + sum(rate(juicefs_blockcache_miss{vol_name="$name"}[$__rate_interval])) by (instance,mp))` |
| PromQL (by bytes) | 同上，`hits`→`hit_bytes`、`miss`→`miss_bytes` |
| y 轴 | `%`（0–100） |
| 警戒线 | y=70%，橙虚线 |

#### J16 Objects Throughput

| 属性 | 值 |
|---|---|
| 标题 | Objects Throughput |
| 图表类型 | `<Line>` 多系列 |
| PromQL (PUT) | `sum(rate(juicefs_object_request_data_bytes{method="PUT",vol_name="$name"}[$__rate_interval])) by (instance,method)` |
| PromQL (GET) | 同上，`method="GET"` |
| y 轴 | Bps，`formatBytes`/s |
| 系列 | `PUT`（蓝，上行）、`GET`（绿，下行） |

---

### 5.6 R6 — 客户端资源 & 暂存

#### J17 Client CPU & Memory

| 属性 | 值 |
|---|---|
| 标题 | Client CPU & Memory |
| 图表类型 | `<Line>` 双 y 轴（cpu% 左 / mem 右） |
| Query 类型 | range query |
| PromQL (CPU) | `sum(rate(juicefs_cpu_usage{vol_name="$name"}[$__rate_interval])*100 < 1000) by (instance,mp)` |
| PromQL (Memory) | `sum(juicefs_memory{vol_name="$name"}) by (instance,mp)` |
| y 轴 | 左：`%`；右：bytes（`formatBytes`） |
| 系列 | `CPU %`（橙）、`Memory`（蓝） |

> **Staging 与 Compaction 说明**：J06 stat 已展示 Staging Blocks 即时值；如需趋势，可在 catalog 中追加 `juicefs_staging_block_bytes`（暂存用量）、`juicefs_staging_block_delay_seconds`（回写延迟）、`juicefs_compact_size_histogram_bytes_{count,sum}`（压实速率/数据量）面板。本原型为聚焦黄金信号，将其降级为可选深挖项（保留在 panel-catalog）。

---

## 6. 主题 / 样式规范

**完全复用 `prometheus-dashboard-prototype-spec.md` §6 中的颜色 Token 和工具函数**（含 MySQL spec 引入的 `formatDuration` / `colorByThresholdReverse`）。

```ts
import { CHART_COLORS, colorByThreshold, formatBytes } from '../utils/formatters';
import { formatDuration, colorByThresholdReverse } from '../utils/formatters';
```

JuiceFS 特有补充：

```ts
// µs 轴格式化（延迟面板）
const usAxisFormatter = (v: number) =>
  v >= 1000 ? `${(v / 1000).toFixed(1)}ms` : `${v.toFixed(0)}µs`;
```

---

## 7. 数据层接口 TypeScript 定义

**复用 `prometheus-dashboard-prototype-spec.md` §7 中全部接口定义**。

JuiceFS 特有补充：

```ts
interface JuiceFSDashboardQueryParams {
  clusterId: number;
  start: number;
  end: number;
  step: number;
  variables: {
    name: string;       // 卷名（单选，精确匹配 vol_name）
    // 注：instance/mp 不在顶层变量，面板内 by (instance,mp) 分系列
    // 注：$__rate_interval 自动计算
  };
}
```

---

## 8. 组件树结构

```
<JuiceFSDashboard>                    # 页面容器，管理 $name + time range + refresh
  ├── <DashboardToolbar>              # 改造版：卷名单选 + 时间范围 + 刷新（无 Instance/Job 多选）
  │
  ├── <Row R1>                        # 概览 Stat（6 个）
  │   ├── <StatPanel J01>             # Uptime（formatDuration）
  │   ├── <StatPanel J02>             # Data Size（formatBytes）
  │   ├── <StatPanel J03>             # Files
  │   ├── <StatPanel J04>             # Client Sessions
  │   ├── <StatPanel J05>             # Cache Hit %（reverse 阈值）
  │   └── <StatPanel J06>             # Staging Blocks（阈值）
  │
  ├── <Row R2>                        # 流量
  │   ├── <TimeSeriesPanel J07>       # Operations
  │   └── <AreaPanel J08>             # IO Throughput
  │
  ├── <Row R3>                        # 延迟（3 个 µs 面板）
  │   ├── <TimeSeriesPanel J09>       # IO Latency
  │   ├── <TimeSeriesPanel J10>       # Transaction Latency
  │   └── <TimeSeriesPanel J11>       # Objects Latency
  │
  ├── <Row R4>                        # 对象存储 & 错误
  │   ├── <TimeSeriesPanel J12>       # Objects Requests
  │   └── <TimeSeriesPanel J13>       # Errors & Restarts ★
  │
  ├── <Row R5>                        # 缓存
  │   ├── <TimeSeriesPanel J14>       # Block Cache Size
  │   ├── <TimeSeriesPanel J15>       # Block Cache Hit Ratio
  │   └── <TimeSeriesPanel J16>       # Objects Throughput
  │
  └── <Row R6>                        # 客户端资源
      └── <TimeSeriesPanel J17>       # Client CPU & Memory（双 y 轴）

# 复用的基础组件（来自 `monitor/_shared/panels/`）
StatPanel / TimeSeriesPanel / AreaPanel / DashboardToolbar / useDashboardData ← 均来自 `monitor/_shared/`
```

---

## 9. 实现说明（供 Phase 3 编码参考）

### 9.1 文件路径

```
datasophon-ui-v2/src/pages/JuiceFSMonitor/
  ├── index.tsx                     # 页面容器（6 行布局）
  ├── panelQueries.ts               # PanelDef（17 个面板）
  ├── hooks/
  │   └── useJuiceFSDashboard.ts    # 调用 `useDashboardData`（`_shared/useDashboardData.ts`）
  ├── panels/                       # 引用 `monitor/_shared/panels/`
  ├── toolbar/
  │   └── JuiceFSDashboardToolbar.tsx # 卷名单选下拉 + 时间范围 + 刷新
  ├── mock/
  │   └── juicefsMockData.ts
  └── utils/                        
```

### 9.2 PromQL 变量替换规则（JuiceFS 版）

```ts
function replaceJuiceFSVars(promql: string, vars: JuiceFSDashboardQueryParams['variables'], interval: string): string {
  return promql
    .replace(/\$name/g, vars.name || '.+')
    .replace(/\$__rate_interval/g, interval);   // 注意：占位符在 [] 内，整体替换
}
```

### 9.2.1 Hook 集成（`useJuiceFSDashboard` 实现说明）

`useJuiceFSDashboard` 有两个 JuiceFS 特有点：

1. **`rateInterval` 合入 variables**：`$__rate_interval` 是 `$key` 风格（无方括号），可直接由通用 `replaceVars` 替换。
   把 `rateInterval` 合入 `effectiveVars = { ...variables, __rate_interval: rateInterval }`，然后传给 `useDashboardData`。

2. **卷名 extras**：传入 `extras = { volumeList: { query: 'juicefs_uptime{vol_name=~".+"}', kind: 'instant' } }`，
   结果 `data.extras.volumeList` 中提取 `vol_name` 标签值得到卷名列表。

```ts
const effectiveVars = { ...variables, __rate_interval: rateInterval };
const data = useDashboardData({
  replaceVars: (promql, vars) => replaceVars(promql, vars, { name: '.+' }),
  variables: effectiveVars, panelIds: ALL_PANEL_IDS,
  extras: { volumeList: { query: 'juicefs_uptime{vol_name=~".+"}', kind: 'instant' } },
  ...
});
```

### 9.3 卷名下拉派生

工具栏卷名下拉由 `extras.volumeList`（`juicefs_uptime{vol_name=~".+"}`）派生，从结果中提取 `vol_name` 标签值列表。默认选中第一个卷。

### 9.4 Mock 数据要求

`juicefsMockData.ts` 覆盖全部 17 个面板：

**Stat 面板（J01-J06）：** Uptime `432000`（5 天）、Data Size `5.4e11`（≈500 GB）、Files `1280000`、Client Sessions `3`、Cache Hit % `94`（绿）、Staging Blocks `0`（绿）。

**Range 面板（J07-J17）：**
- J07 Operations: 3 个 instance 各 200–500 ops/s
- J08 IO Throughput: write ≈ 40 MB/s、read ≈ 120 MB/s
- J09 IO Latency: 800–1500 µs
- J10 Transaction Latency: 2000–5000 µs（元数据较慢）
- J11 Objects Latency: 15000–40000 µs（对象存储后端最慢）
- J12 Objects Requests: GET ≈ 80/s、PUT ≈ 20/s、DELETE ≈ 2/s
- J13 Errors & Restarts: object errors ≈ 0（偶发 0.1/s 突刺）、tx restarts ≈ 0.5/s
- J14 Block Cache Size: ≈ 40 GB 稳定
- J15 Cache Hit Ratio: by count ≈ 94%、by bytes ≈ 90%
- J16 Objects Throughput: PUT ≈ 30 MB/s、GET ≈ 8 MB/s
- J17 CPU & Memory: CPU 30–60%、Memory ≈ 1.2 GB

---

## 10. Dev 环境配置注意事项

**完全复用 `prometheus-dashboard-prototype-spec.md` §10**（publicPath、proxy bypass、mock 路径对齐）。后端代理端点与 PrometheusMonitor 相同。

---

## 11. 验收标准

Phase 2 原型（mock 阶段）完成后，需满足：

- [ ] 全部 17 个面板（J01-J17）按 §4 布局渲染（6 行 24 列 Grid）
- [ ] 工具栏首项为**卷名单选**下拉（非 Instance/Job 多选），由 `juicefs_uptime` 的 `vol_name` 标签派生
- [ ] J01 Uptime 用 `formatDuration`；J05 Cache Hit % 用 reverse 阈值（低→红）
- [ ] J09/J10/J11 延迟面板用 histogram 比值法（`rate(_sum)/rate(_count)*1e6`）+ µs 轴，**未**误用 histogram_quantile
- [ ] J08/J16 吞吐面板用 `formatBytes`/s（binBps/Bps）
- [ ] **J13 为独立 Errors 面板**（object errors + transaction restarts），已从 catalog 的 Objects Requests 面板拆出
- [ ] J15 Cache Hit Ratio 含 70% 橙色警戒线
- [ ] 速率类 PromQL 保留官方 `< 5000000000` 尖峰过滤
- [ ] 颜色方案遵循 §6 Token
- [ ] 在 1280px 宽度下 6 行布局无横向滚动条
- [ ] golden signals 四象限覆盖验证（见 §5.0 映射表）；Error 维度由 J13 补强独立呈现
