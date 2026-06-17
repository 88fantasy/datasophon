# Valkey 监控看板原型设计 Spec

> **文档用途**：供 Claude design 阅读，根据本 spec 设计 React + AntV G2 看板原型，并最终生成可运行的组件代码。
> **组件**：Valkey 8.6（Redis 协议兼容）
> **数据源**：exporter `redis_exporter`（或 Valkey 兼容的 Redis exporter，默认 `:9121/metrics`）
> **参考 Grafana 看板**：[Redis Exporter Dashboard](https://grafana.com/grafana/dashboards/763) (ID 763)
> **Panel Catalog 路径**：`docs/monitoring/panel-catalog/Valkey.json`（13 个面板）
> **Phase**：Phase 2 —— 原型设计阶段

---

## 1. 架构约束

```
React(AntV G2)
  └──HTTP──> datasophon-api /api/v2/prometheus/query_range
               └──PromQL──> Prometheus :9090
                              └──scrape──> redis_exporter :9121/metrics
                                             └──RESP(INFO)──> Valkey :6379
```

**前端不直连 Prometheus**。所有 PromQL 通过后端代理端点转发（详见 `prometheus-dashboard-prototype-spec.md` §1）。

### 1.1 Valkey（redis_exporter）指标特点

- **统一前缀 `redis_`**：Valkey 8.6 fork 自 Redis 7.2，INFO 输出与 Redis 兼容，`redis_exporter` 抓取后导出标准 `redis_*` 指标，**无需 Valkey 专用 exporter**。
- **⚠️ 兼容性验证点（落地时）**：少数 Valkey 8.x 特有字段（如多线程 IO、`valkey`-prefixed INFO 段）`redis_exporter` 可能不导出；落地时需在目标实例核对 `redis_up`/`redis_version` 等关键指标是否齐全，并按需调整 `job` 过滤。本原型沿用标准 `redis_*` 命名。
- **单标签 `instance`**：每个 Valkey 节点一个 exporter target，`{instance=~"$instance"}` 过滤；命令维度用 `by (cmd)`，DB 维度用 `by (db)`。
- **Counter + Gauge 混合**：`redis_commands_total`/`redis_keyspace_hits_total`/`redis_net_*_bytes_total` 为 Counter（用 `rate`/`irate`）；`redis_connected_clients`/`redis_memory_used_bytes`/`redis_db_keys` 为 Gauge（直读）。
- **延迟用比值法**：命令平均耗时 = `irate(redis_commands_duration_seconds_total) / irate(redis_commands_total)`，单位秒。
- **catalog 阈值残留**：除 Memory Usage gauge（80/95 真阈值）外，其余 stat 的 `value:80` 是 Grafana 模板默认，本 spec 重设或弃用。

---

## 2. 图表类型映射字典

**完全复用 `prometheus-dashboard-prototype-spec.md` §2 中的映射字典**。

Valkey 特有补充：

| Grafana chartType（catalog） | 映射组件 | 备注 |
|---|---|---|
| `gauge`（Memory Usage） | `<Statistic>` + `colorByThreshold`（环形可选） | 80→橙、95→红 真阈值 |
| `stat`（Max Uptime/Clients） | `<Statistic>` | uptime 用 `formatDuration` |
| `timeseries`（比值延迟） | `<Line>` + ms 轴 | Average Time Spent by Command |
| `timeseries`（命令分布 by cmd） | `<Line>` 多系列 | Total Commands / sec |

---

## 3. 变量 / 过滤器规范

| 变量 | PromQL 占位符 | 取值来源 | 默认值 | 说明 |
|---|---|---|---|---|
| 实例 | `$instance` | `label_values(redis_up, instance)` | `.+`（全选） | 多选下拉，对应 redis_exporter 地址 |
| 速率窗口 | `$__interval` | 由时间范围自动计算 | — | 仅 Total Commands 用 `[1m]`，其余固定 `[5m]`/`[1m]` |
| 时间范围 | — | 时间选择器 | `Last 1h` | 5m/15m/1h/6h/24h/7d |
| 刷新间隔 | — | — | `30s` | 自动轮询 |

> catalog 多数面板用固定窗口（`[1m]`/`[5m]`），仅 `Total Commands / sec` 用 `[1m]`。本 spec 不暴露 Interval 下拉，窗口在 PanelDef 中按面板硬编码。

---

## 4. 看板布局（24 列 Grid）

裁剪策略：catalog 13 面板基本全保留（小盘），**补强 1 个错误面板**（Rejected Connections ★）补齐黄金信号 Error 维度，共 **14 面板**。

```
┌──────────────────────────────────────────────────────────────────────┐
│  TOOLBAR: [实例▼]                [Last 1h▼]   [🔄 30s▼]            │
└──────────────────────────────────────────────────────────────────────┘

行 R1 — 概览 Stat（高度 80px，col=6 ×4）
┌──────────┬──────────┬──────────┬──────────┐
│Max Uptime│Clients   │Memory    │Cache     │
│          │          │Usage %   │Hit % ★   │
│ V01      │ V02      │ V03      │ V04      │
└──────────┴──────────┴──────────┴──────────┘

行 R2 — 流量 Traffic（高度 200px）
┌────────────────────────────┬────────────────────────────┐
│ V05 Total Commands / sec   │ V06 Hits / Misses per Sec  │
│ [Line by cmd]              │ [Line hits/misses]         │
└────────────────────────────┴────────────────────────────┘

行 R3 — 延迟 & 网络 Latency/Traffic（高度 200px）
┌────────────────────────────┬────────────────────────────┐
│ V07 Avg Time by Command    │ V08 Network I/O            │
│ [Line by cmd，ms]          │ [Area in/out，bytes/s]     │
└────────────────────────────┴────────────────────────────┘

行 R4 — 内存 & 连接 Saturation（高度 200px）
┌────────────────────────────┬────────────────────────────┐
│ V09 Total Memory Usage     │ V10 Connected/Blocked      │
│ [Line used/max(上限线)]    │ [Line connected/blocked]   │
└────────────────────────────┴────────────────────────────┘

行 R5 — 键空间 Saturation/Errors（高度 200px，col=8 ×3）
┌──────────────────┬──────────────────┬──────────────────┐
│ V11 Items per DB │ V12 Expiring vs  │ V13 Expired/     │
│                  │ Not-Expiring     │ Evicted Keys     │
└──────────────────┴──────────────────┴──────────────────┘

行 R6 — 错误 Errors（高度 200px）
┌─────────────────────────────────────────────────────────┐
│ V14 Rejected Connections ★                              │
│ [Line rejected_connections]  col=24                     │
└─────────────────────────────────────────────────────────┘
```

---

## 5. 面板规格（逐面板）

### 5.0 Golden Signals 映射

| 维度 | 面板 | 说明 |
|---|---|---|
| **Latency（延迟）** | V07 Avg Time by Command（比值法 ms） | 各命令平均执行耗时 |
| **Traffic（流量）** | V05 Commands/sec、V06 Hits/Misses、V08 Network I/O | 命令速率、缓存命中、网络吞吐 |
| **Errors（错误）** | V13 Evicted Keys、V14 Rejected Connections ★ | 内存驱逐 + 连接拒绝（补强） |
| **Saturation（饱和度）** | V03 Memory %、V09 Memory、V10 Clients、V11/V12 键空间 | 内存、连接、键数压力 |

---

### 5.1 R1 — 概览 Stat

#### V01 Max Uptime

| 属性 | 值 |
|---|---|
| 标题 | Max Uptime |
| 图表类型 | `<Statistic>` + `formatDuration` |
| Query 类型 | instant query |
| PromQL | `max(redis_uptime_in_seconds{instance=~"$instance"})` |
| 单位 | 秒 → d/h/m |
| 阈值（reverse） | `< 300` → 橙；否则绿（catalog `value:80` 弃用） |

#### V02 Clients

| 属性 | 值 |
|---|---|
| 标题 | Clients |
| 图表类型 | `<Statistic>` |
| Query 类型 | instant query |
| PromQL | `sum(redis_connected_clients{instance=~"$instance"})` |

#### V03 Memory Usage %

| 属性 | 值 |
|---|---|
| 标题 | Memory Usage |
| 图表类型 | `<Statistic>` + `colorByThreshold`（可选环形 gauge 样式） |
| Query 类型 | instant query |
| PromQL | `sum(100 * (redis_memory_used_bytes{instance=~"$instance"} / redis_memory_max_bytes{instance=~"$instance"}))` |
| 单位 | `%` |
| 阈值 | `< 80` → 绿；`80–95` → 橙；`≥ 95` → 红（catalog 真阈值，保留） |

> ⚠️ 若 `redis_memory_max_bytes`（maxmemory）未配置（=0），该比值为 `+Inf`/`NaN`；前端需对 max=0 兜底显示 "unlimited"。

#### V04 Cache Hit % ★（补强 stat）

| 属性 | 值 |
|---|---|
| 标题 | Cache Hit % |
| 图表类型 | `<Statistic>` + `colorByThreshold` |
| Query 类型 | instant query |
| PromQL | `sum(rate(redis_keyspace_hits_total{instance=~"$instance"}[5m])) * 100 / (sum(rate(redis_keyspace_hits_total{instance=~"$instance"}[5m])) + sum(rate(redis_keyspace_misses_total{instance=~"$instance"}[5m])))` |
| 单位 | `%` |
| 阈值（reverse） | `< 80` → 红；`80–95` → 橙；`≥ 95` → 绿 |

---

### 5.2 R2 — 流量

#### V05 Total Commands / sec

| 属性 | 值 |
|---|---|
| 标题 | Total Commands / sec |
| 图表类型 | `<Line>` 多系列 by cmd |
| Query 类型 | range query |
| PromQL | `sum(rate(redis_commands_total{instance=~"$instance"}[1m])) by (cmd)` |
| 系列字段 | `cmd`（get/set/hget/...） |
| y 轴 | `ops/s` |

#### V06 Hits / Misses per Sec

| 属性 | 值 |
|---|---|
| 标题 | Hits / Misses per Sec |
| 图表类型 | `<Line>` 2 系列 |
| Query 类型 | range query |
| PromQL (hits) | `irate(redis_keyspace_hits_total{instance=~"$instance"}[5m])` |
| PromQL (misses) | `irate(redis_keyspace_misses_total{instance=~"$instance"}[5m])` |
| 系列 | `Hits`（绿）、`Misses`（橙） |

---

### 5.3 R3 — 延迟 & 网络

#### V07 Average Time Spent by Command

| 属性 | 值 |
|---|---|
| 标题 | Average Time Spent by Command / sec |
| 图表类型 | `<Line>` 多系列 by cmd |
| Query 类型 | range query（比值法） |
| PromQL | `sum(irate(redis_commands_duration_seconds_total{instance=~"$instance"}[1m])) by (cmd) / sum(irate(redis_commands_total{instance=~"$instance"}[1m])) by (cmd)` |
| 单位 | 秒 → 前端 `×1000` 显示 ms（或 µs，按量级自适应） |
| 系列字段 | `cmd` |

#### V08 Network I/O

| 属性 | 值 |
|---|---|
| 标题 | Network I/O |
| 图表类型 | `<Area>` 2 系列 |
| Query 类型 | range query |
| PromQL (in) | `sum(rate(redis_net_input_bytes_total{instance=~"$instance"}[5m]))` |
| PromQL (out) | `sum(rate(redis_net_output_bytes_total{instance=~"$instance"}[5m]))` |
| y 轴 | bytes/s，`formatBytes` |
| 系列 | `Input`（绿）、`Output`（蓝） |

---

### 5.4 R4 — 内存 & 连接

#### V09 Total Memory Usage

| 属性 | 值 |
|---|---|
| 标题 | Total Memory Usage |
| 图表类型 | `<Line>` 2 系列（含上限线） |
| Query 类型 | range query |
| PromQL (used) | `redis_memory_used_bytes{instance=~"$instance"}` |
| PromQL (max) | `redis_memory_max_bytes{instance=~"$instance"}` |
| y 轴 | bytes，`formatBytes` |
| 系列 | `Used`（蓝）、`Max`（红虚线，上限参考；为 0 时隐藏） |

#### V10 Connected / Blocked Clients

| 属性 | 值 |
|---|---|
| 标题 | Connected / Blocked Clients |
| 图表类型 | `<Line>` 2 系列 |
| Query 类型 | range query |
| PromQL (connected) | `sum(redis_connected_clients{instance=~"$instance"})` |
| PromQL (blocked) | `sum(redis_blocked_clients{instance=~"$instance"})` |
| 系列 | `Connected`（蓝）、`Blocked`（橙，阻塞客户端为压力信号） |

---

### 5.5 R5 — 键空间

#### V11 Total Items per DB

| 属性 | 值 |
|---|---|
| 标题 | Total Items per DB |
| 图表类型 | `<Line>` 多系列 by db |
| Query 类型 | range query |
| PromQL | `sum(redis_db_keys{instance=~"$instance"}) by (db, instance)` |
| 系列字段 | `db`（db0/db1/...） |

#### V12 Expiring vs Not-Expiring Keys

| 属性 | 值 |
|---|---|
| 标题 | Expiring vs Not-Expiring Keys |
| 图表类型 | `<Line>` 2 系列 |
| Query 类型 | range query |
| PromQL (not-expiring) | `sum(redis_db_keys{instance=~"$instance"}) by (instance) - sum(redis_db_keys_expiring{instance=~"$instance"}) by (instance)` |
| PromQL (expiring) | `sum(redis_db_keys_expiring{instance=~"$instance"}) by (instance)` |
| 系列 | `Not-Expiring`（蓝）、`Expiring`（绿） |

#### V13 Expired / Evicted Keys

| 属性 | 值 |
|---|---|
| 标题 | Expired / Evicted Keys |
| 图表类型 | `<Line>` 2 系列 |
| Query 类型 | range query |
| PromQL (expired) | `sum(rate(redis_expired_keys_total{instance=~"$instance"}[5m])) by (instance)` |
| PromQL (evicted) | `sum(rate(redis_evicted_keys_total{instance=~"$instance"}[5m])) by (instance)` |
| 系列 | `Expired`（蓝，正常 TTL 过期）、`Evicted`（红，内存不足驱逐——错误信号） |

> Evicted Keys 持续 > 0 表示 maxmemory 已触顶并强制驱逐，是内存饱和的关键错误信号，故配红色。

---

### 5.6 R6 — 错误（补强）

#### V14 Rejected Connections ★

| 属性 | 值 |
|---|---|
| 标题 | Rejected Connections |
| 图表类型 | `<Line>` 1 系列 |
| Query 类型 | range query |
| PromQL | `sum(rate(redis_rejected_connections_total{instance=~"$instance"}[5m])) by (instance)` |
| 系列颜色 | `#ff4d4f`（红） |
| 说明 | ★ catalog 无显式连接错误面板；`redis_rejected_connections_total`（超过 maxclients 被拒）是 Redis/Valkey 最直接的连接级错误信号，补强为独立 Error 面板。若目标 exporter 未导出该指标，回退用 V13 Evicted 作为主 Error 信号 |

---

## 6. 主题 / 样式规范

**完全复用 `prometheus-dashboard-prototype-spec.md` §6 中的颜色 Token 和工具函数**（含 `formatDuration` / `colorByThresholdReverse` / `formatBytes`）。

```ts
import { CHART_COLORS, colorByThreshold, colorByThresholdReverse, formatBytes, formatDuration } from '../utils/formatters';
```

Valkey 无额外特有配色，命令多系列用 `CHART_COLORS.series` 循环。

---

## 7. 数据层接口 TypeScript 定义

**复用 `prometheus-dashboard-prototype-spec.md` §7 中全部接口定义**。

Valkey 特有补充：

```ts
interface ValkeyDashboardQueryParams {
  clusterId: number;
  start: number;
  end: number;
  step: number;
  variables: {
    instance: string;   // 正则，如 ".+" 或 "10.0.0.1:9121"
    // 注：固定窗口，无 interval 变量
  };
}
```

---

## 8. 组件树结构

```
<ValkeyDashboard>                     # 页面容器，管理 instance + time range + refresh
  ├── <DashboardToolbar>              # 引用 `_shared/DashboardToolbar.tsx`（children 注入 Instance 选择器）
  │
  ├── <Row R1>                        # 概览 Stat（4 个）
  │   ├── <StatPanel V01>             # Max Uptime（formatDuration）
  │   ├── <StatPanel V02>             # Clients
  │   ├── <StatPanel V03>             # Memory Usage %（80/95 阈值，max=0 兜底）
  │   └── <StatPanel V04>             # Cache Hit % ★（reverse 阈值）
  │
  ├── <Row R2>                        # 流量
  │   ├── <TimeSeriesPanel V05>       # Total Commands / sec
  │   └── <TimeSeriesPanel V06>       # Hits / Misses per Sec
  │
  ├── <Row R3>                        # 延迟 & 网络
  │   ├── <TimeSeriesPanel V07>       # Avg Time by Command（ms）
  │   └── <AreaPanel V08>             # Network I/O
  │
  ├── <Row R4>                        # 内存 & 连接
  │   ├── <TimeSeriesPanel V09>       # Total Memory Usage（含上限线）
  │   └── <TimeSeriesPanel V10>       # Connected / Blocked Clients
  │
  ├── <Row R5>                        # 键空间
  │   ├── <TimeSeriesPanel V11>       # Items per DB
  │   ├── <TimeSeriesPanel V12>       # Expiring vs Not-Expiring
  │   └── <TimeSeriesPanel V13>       # Expired / Evicted Keys
  │
  └── <Row R6>                        # 错误
      └── <TimeSeriesPanel V14>       # Rejected Connections ★

# 复用的基础组件（来自 `monitor/_shared/panels/`）
StatPanel / TimeSeriesPanel / AreaPanel / DashboardToolbar / useDashboardData ← 均来自 `monitor/_shared/`
```

---

## 9. 实现说明（供 Phase 3 编码参考）

### 9.1 文件路径

```
datasophon-ui-v2/src/pages/ValkeyMonitor/
  ├── index.tsx                     # 页面容器（6 行布局）
  ├── panelQueries.ts               # PanelDef（14 个面板，窗口按面板硬编码 [1m]/[5m]）
  ├── hooks/
  │   └── useValkeyDashboard.ts     # 调用 `useDashboardData`（`_shared/useDashboardData.ts`）
  ├── panels/                       # 引用 `monitor/_shared/panels/`
  ├── toolbar/                      # 引用 `_shared/DashboardToolbar.tsx`（children 注入 Instance 选择器）
  ├── mock/
  │   └── valkeyMockData.ts
  └── utils/                        # 无此目录 — 直接从 `../../_shared/charts/` import
```

### 9.2 PromQL 变量替换规则（Valkey 版）

```ts
function replaceValkeyVars(promql: string, vars: ValkeyDashboardQueryParams['variables']): string {
  return promql.replace(/\$instance/g, vars.instance || '.+');
  // 注：窗口 [1m]/[5m] 已硬编码在 PanelDef，不替换
}
```

### 9.3 maxmemory=0 兜底

V03 / V09 依赖 `redis_memory_max_bytes`。当 Valkey 未配置 `maxmemory`（值为 0），V03 比值需兜底为显示 "unlimited" 且不染红，V09 的 Max 上限线隐藏。前端在数据转换层判断 `max <= 0`。

### 9.4 Mock 数据要求

`valkeyMockData.ts` 覆盖全部 14 个面板：

**Stat（V01-V04）：** Max Uptime `864000`（10 天）、Clients `128`、Memory Usage `46%`（绿）、Cache Hit `97%`（绿）。

**Range（V05-V14）：**
- V05 Commands: get ≈ 8000/s、set ≈ 1500/s、hget ≈ 600/s 等
- V06 Hits/Misses: hits ≈ 9000/s、misses ≈ 300/s
- V07 Avg Time: 多数 cmd ≈ 0.05–0.2 ms，偶有 keys 命令 ≈ 5 ms
- V08 Network I/O: input ≈ 3 MB/s、output ≈ 12 MB/s
- V09 Memory: used ≈ 6 GB、max 12 GB（上限线）
- V10 Clients: connected ≈ 128、blocked ≈ 0–2
- V11 Items per DB: db0 ≈ 240 万
- V12 Expiring vs Not: not-expiring ≈ 80 万、expiring ≈ 160 万
- V13 Expired/Evicted: expired ≈ 200/s、evicted ≈ 0（偶发突刺验证红色）
- V14 Rejected Connections: ≈ 0（偶发 1/s 突刺）

---

## 10. Dev 环境配置注意事项

**完全复用 `prometheus-dashboard-prototype-spec.md` §10**（publicPath、proxy bypass、mock 路径对齐）。后端代理端点与 PrometheusMonitor 相同。

---

## 11. 验收标准

Phase 2 原型（mock 阶段）完成后，需满足：

- [ ] 全部 14 个面板（V01-V14）按 §4 布局渲染（6 行 24 列 Grid）
- [ ] V01 Max Uptime 用 `formatDuration`；V03 Memory Usage 用 80/95 真阈值（catalog 保留）；V04 Cache Hit 用 reverse 阈值
- [ ] V03/V09 对 `redis_memory_max_bytes = 0`（未配 maxmemory）做兜底（"unlimited" / 隐藏上限线）
- [ ] V07 Avg Time by Command 用比值法（duration_total / commands_total）+ ms 轴
- [ ] V08 Network I/O 用 `<Area>` + `formatBytes`/s
- [ ] V13 Evicted Keys 系列配红色（内存驱逐为错误信号）
- [ ] **V14 Rejected Connections 为补强的独立 Error 面板**；exporter 缺该指标时回退 V13
- [ ] 工具栏：仅实例多选 + 时间范围 + 刷新（无 Job/Interval 下拉）
- [ ] catalog 残留的 stat `value:80` 阈值未被误用（除 V03 真阈值外）
- [ ] 在 1280px 宽度下 6 行布局无横向滚动条
- [ ] golden signals 四象限覆盖验证（见 §5.0）；Error 维度由 V13/V14 补强
- [ ] （落地提醒）已在目标 Valkey 8.6 实例核对 `redis_*` 指标齐全性（见 §1.1 兼容性验证点）
