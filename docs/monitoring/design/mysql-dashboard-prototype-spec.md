# MySQL 监控看板原型设计 Spec

> **文档用途**：供 Claude design 阅读，根据本 spec 设计 React + AntV G2 看板原型，并最终生成可运行的组件代码。
> **组件**：MySQL 8.0.28
> **数据源**：exporter `prometheus/mysqld_exporter`（默认 `:9104/metrics`）
> **参考 Grafana 看板**：[MySQL Exporter Quickstart and Dashboard](https://grafana.com/grafana/dashboards/14057) (ID 14057)
> **Panel Catalog 路径**：`docs/monitoring/panel-catalog/MySQL.json`（27 个面板）
> **Phase**：Phase 2 —— 原型设计阶段

---

## 1. 架构约束

```
React(AntV G2)
  └──HTTP──> datasophon-api /api/v2/prometheus/query_range
               └──PromQL──> Prometheus :9090
                              └──scrape──> mysqld_exporter :9104/metrics
                                             └──MySQL Protocol──> MySQL :3306
```

**前端不直连 Prometheus**。所有 PromQL 通过后端代理端点转发（与 Prometheus 自监控完全相同的代理路径，详见 `prometheus-dashboard-prototype-spec.md` §1）。

### 1.1 MySQL（mysqld_exporter）指标特点

与原生 `/metrics` 组件（如 ZooKeeper、Nexus）不同，MySQL 走 **exporter 中转**：

- **统一前缀 `mysql_`**：所有指标名形如 `mysql_global_status_*`（运行时计数器/状态）、`mysql_global_variables_*`（配置项）、`mysql_info_schema_*`（information_schema 派生）。
- **双标签过滤**：`{job=~"$job", instance=~"$instance"}`，`instance` 对应 exporter 地址（如 `10.0.0.1:9104`），并非 MySQL 的 `:3306`。
- **Counter 为主**：`mysql_global_status_queries`/`questions`/`bytes_received` 等均为单调递增 Counter，看板里用 `rate(...[$__interval])` 转速率。
- **配置项即上限**：`mysql_global_variables_max_connections`、`*_open_files_limit` 等是饱和度面板的分母（用量 / 上限）。
- **⚠️ Latency 信号天然弱**：mysqld_exporter 默认不暴露请求延迟直方图。本看板以 `slow_queries` 速率作为延迟退化的代理信号；若目标实例启用了 `query_response_time` 插件，可追加 `mysql_perf_schema_*` / `query_response_time_seconds` 直方图面板（见 §9.5 增补建议）。

---

## 2. 图表类型映射字典

**完全复用 `docs/monitoring/design/prometheus-dashboard-prototype-spec.md` §2 中的映射字典**（`stat → <Statistic>`、`graph 单/多系列 → <Line>`、`graph 堆叠 → <Area>`、G2 v5 API 变更表）。

MySQL 特有补充：

| Grafana chartType（catalog） | 映射组件 | 备注 |
|---|---|---|
| `stat`（reverse threshold） | `<Statistic>` + `colorByThreshold(reverse=true)` | Uptime/QPS：值越**小**越危险（红），方向与默认相反 |
| `stat`（bytes） | `<Statistic>` + `formatBytes` | InnoDB Buffer Pool Size |
| `graph`（用量+上限双线） | `<Line>` 2 系列 + 警戒区 | Connections、Open Files：上限线为参考阈值 |
| `graph`（topk） | `<Line>` 多系列 | Top Command Counters（`topk(5, ...)`） |

---

## 3. 变量 / 过滤器规范

看板顶部工具栏包含以下变量：

| 变量 | PromQL 占位符 | 取值来源 | 默认值 | 说明 |
|---|---|---|---|---|
| 实例 | `$instance` | `label_values(mysql_up, instance)` | `.+`（全选） | 多选下拉，对应 mysqld_exporter 地址 |
| Job | `$job` | `label_values(mysql_up, job)` | `.+`（全选） | 多选下拉，对应 Prometheus job 名 |
| 速率窗口 | `$__interval` | 由时间范围自动计算 | — | 不暴露给用户，`max(4×scrape_interval, range/200)` |
| 时间范围 | — | 时间选择器 | `Last 1h` | 快速选择: 5m/15m/1h/6h/24h/7d |
| 刷新间隔 | — | — | `30s` | 自动轮询 |

> **归一化修正**：catalog 中 `MySQL Handlers` / `MySQL Transaction Handlers` 两面板误用了 `instance=~"$host"`，本 spec 统一改为 `instance=~"$instance"`，工具栏不引入 `$host` 变量。
> **`$instance` + `$job` 与 ZooKeeper/Prometheus 看板同构**，可复用同一套 `DashboardToolbar` 组件逻辑（保留 Interval 自动计算，不显示下拉）。

---

## 4. 看板布局（24 列 Grid）

裁剪策略：catalog 27 面板 → 聚焦四黄金信号的 **17 面板**。剔除 InnoDB 细分（page size 拆解）、Query Cache 系列（8.0 已移除 Query Cache）、Table/File 缓存细分等长尾面板（保留在 catalog 供深挖）。

```
┌──────────────────────────────────────────────────────────────────────┐
│  TOOLBAR: [实例▼]  [Job▼]      [Last 1h▼]   [🔄 30s▼]              │
└──────────────────────────────────────────────────────────────────────┘

行 R1 — 概览 Stat（高度 80px，col=4 ×6）
┌────────┬────────┬────────┬────────┬────────┬────────┐
│Uptime  │Current │Conn    │InnoDB  │Slow    │Aborted │
│        │ QPS    │Used %  │BufPool │Q /s    │Conn /s │
│ M01    │ M02    │ M03    │ M04    │ M05    │ M06    │
└────────┴────────┴────────┴────────┴────────┴────────┘

行 R2 — 流量 Traffic（高度 200px）
┌────────────────────────────┬────────────────────────────┐
│ M07 MySQL Questions (QPS)  │ M08 Network Traffic        │
│ [Line 1 系列]              │ [Area recv/sent，Bps]      │
│ col=12                     │ col=12                     │
└────────────────────────────┴────────────────────────────┘

行 R3 — 连接 & 线程 Saturation（高度 200px）
┌────────────────────────────┬────────────────────────────┐
│ M09 MySQL Connections      │ M10 Client Thread Activity │
│ [Line connected/maxused/   │ [Line connected/running]   │
│  maxconn(上限线)]          │ col=12                     │
└────────────────────────────┴────────────────────────────┘

行 R4 — 错误 Errors（高度 200px，col=8 ×3）
┌──────────────────┬──────────────────┬──────────────────┐
│ M11 Slow Queries │ M12 Aborted Conn │ M13 Table Locks  │
│ [Line]           │ [Line connects/  │ [Line immediate/ │
│                  │  clients]        │  waited]         │
└──────────────────┴──────────────────┴──────────────────┘

行 R5 — InnoDB & 内存 Saturation（高度 200px）
┌────────────────────────────┬────────────────────────────┐
│ M14 Internal Memory        │ M15 Temporary Objects      │
│ [Area 多系列 bytes 堆叠]   │ [Line tmp tables/disk/file]│
│ col=12                     │ col=12                     │
└────────────────────────────┴────────────────────────────┘

行 R6 — Handlers & 命令分布（高度 200px）
┌────────────────────────────┬────────────────────────────┐
│ M16 MySQL Handlers         │ M17 Top Command Counters   │
│ [Line 多系列 by handler]   │ [Line topk(5) by command]  │
│ col=12                     │ col=12                     │
└────────────────────────────┴────────────────────────────┘
```

---

## 5. 面板规格（逐面板）

### 5.0 Golden Signals 映射

| 维度 | 面板 | 说明 |
|---|---|---|
| **Latency（延迟）** | M05 Slow Queries（stat）、M11 Slow Queries（趋势） | mysqld_exporter 无原生延迟直方图，以慢查询速率作代理；可选增补 `query_response_time`（§9.5） |
| **Traffic（流量）** | M02 QPS、M07 Questions、M08 Network Traffic、M16 Handlers、M17 Top Commands | 请求量与网络吞吐 |
| **Errors（错误）** | M06 Aborted Conn（stat）、M11/M12/M13 慢查询/中断连接/锁等待 | 连接异常与锁竞争 |
| **Saturation（饱和度）** | M03 Conn Used %、M04 Buffer Pool、M09 Connections、M10 Thread Activity、M14 Internal Memory、M15 Temp Objects | 连接、缓冲池、线程、内存、临时对象压力 |

---

### 5.1 R1 — 概览 Stat

#### M01 Uptime

| 属性 | 值 |
|---|---|
| 标题 | Uptime |
| 图表类型 | `<Statistic>` + `formatDuration` |
| Query 类型 | instant query |
| PromQL | `mysql_global_status_uptime{job=~"$job", instance=~"$instance"}` |
| 单位 | 秒 → 自动换算 d/h/m |
| 阈值（**reverse**） | `< 300` → 红 `#ff4d4f`（刚重启）；`300–3600` → 橙 `#faad14`；`≥ 3600` → 绿 `#52c41a` |

#### M02 Current QPS

| 属性 | 值 |
|---|---|
| 标题 | Current QPS |
| 图表类型 | `<Statistic>` |
| Query 类型 | instant query |
| PromQL | `rate(mysql_global_status_queries{job=~"$job", instance=~"$instance"}[$__interval])` |
| 单位 | 无（保留 1 位小数） |
| 样式 | 大字体 32px，蓝色 `#1677ff` |

#### M03 Connections Used %

| 属性 | 值 |
|---|---|
| 标题 | Connections Used % |
| 图表类型 | `<Statistic>` + `colorByThreshold` |
| Query 类型 | instant query |
| PromQL | `sum(max_over_time(mysql_global_status_threads_connected{job=~"$job", instance=~"$instance"}[$__interval])) / sum(mysql_global_variables_max_connections{job=~"$job", instance=~"$instance"}) * 100` |
| 单位 | `%` |
| 阈值 | `< 80` → 绿；`80–90` → 橙；`≥ 90` → 红 |

> 派生面板：用「已连接 / max_connections」直观反映连接饱和度（catalog 原本只给绝对值，本 spec 升级为百分比）。

#### M04 InnoDB Buffer Pool

| 属性 | 值 |
|---|---|
| 标题 | InnoDB Buffer Pool |
| 图表类型 | `<Statistic>` + `formatBytes` |
| Query 类型 | instant query |
| PromQL | `mysql_global_variables_innodb_buffer_pool_size{job=~"$job", instance=~"$instance"}` |
| 单位 | bytes → 自动换算 |
| 样式 | 大字体，蓝色 |

#### M05 Slow Queries /s

| 属性 | 值 |
|---|---|
| 标题 | Slow Queries /s |
| 图表类型 | `<Statistic>` + `colorByThreshold` |
| Query 类型 | instant query |
| PromQL | `sum(rate(mysql_global_status_slow_queries{job=~"$job", instance=~"$instance"}[$__interval]))` |
| 单位 | `/s`（2 位小数） |
| 阈值 | `= 0` → 绿；`> 0` → 橙；`> 1` → 红 |

#### M06 Aborted Connections /s

| 属性 | 值 |
|---|---|
| 标题 | Aborted Connections /s |
| 图表类型 | `<Statistic>` + `colorByThreshold` |
| Query 类型 | instant query |
| PromQL | `sum(rate(mysql_global_status_aborted_connects{job=~"$job", instance=~"$instance"}[$__interval]))` |
| 单位 | `/s`（2 位小数） |
| 阈值 | `= 0` → 绿；`> 0` → 橙 |

---

### 5.2 R2 — 流量

#### M07 MySQL Questions (QPS)

| 属性 | 值 |
|---|---|
| 标题 | MySQL Questions |
| 图表类型 | `<Line>` 1 系列 |
| Query 类型 | range query |
| PromQL | `rate(mysql_global_status_questions{job=~"$job", instance=~"$instance"}[$__interval])` |
| y 轴 | `qps`，2 位小数 |
| 系列颜色 | `#1677ff` |

#### M08 Network Traffic

| 属性 | 值 |
|---|---|
| 标题 | MySQL Network Traffic |
| 图表类型 | `<Area>` 堆叠 |
| Query 类型 | range query（2 条 PromQL） |
| PromQL (inbound) | `sum(rate(mysql_global_status_bytes_received{job=~"$job", instance=~"$instance"}[$__interval]))` |
| PromQL (outbound) | `sum(rate(mysql_global_status_bytes_sent{job=~"$job", instance=~"$instance"}[$__interval]))` |
| y 轴 | bytes/s，自动换算（B/KB/MB） |
| 系列 | `Inbound`（绿）、`Outbound`（蓝） |

---

### 5.3 R3 — 连接 & 线程

#### M09 MySQL Connections

| 属性 | 值 |
|---|---|
| 标题 | MySQL Connections |
| 图表类型 | `<Line>` 3 系列（含上限参考线） |
| Query 类型 | range query（3 条 PromQL） |
| PromQL (connected) | `sum(max_over_time(mysql_global_status_threads_connected{job=~"$job", instance=~"$instance"}[$__interval]))` |
| PromQL (max used) | `sum(mysql_global_status_max_used_connections{job=~"$job", instance=~"$instance"})` |
| PromQL (limit) | `sum(mysql_global_variables_max_connections{job=~"$job", instance=~"$instance"})` |
| 系列 | `Connections`（蓝）、`Max Used`（橙）、`Max Connections`（红虚线，上限参考） |
| y 轴 | 整数 |

#### M10 Client Thread Activity

| 属性 | 值 |
|---|---|
| 标题 | MySQL Client Thread Activity |
| 图表类型 | `<Line>` 2 系列 |
| Query 类型 | range query |
| PromQL (connected) | `sum(max_over_time(mysql_global_status_threads_connected{job=~"$job", instance=~"$instance"}[$__interval]))` |
| PromQL (running) | `sum(max_over_time(mysql_global_status_threads_running{job=~"$job", instance=~"$instance"}[$__interval]))` |
| 系列 | `Threads Connected`（蓝）、`Threads Running`（橙） |

---

### 5.4 R4 — 错误

#### M11 Slow Queries

| 属性 | 值 |
|---|---|
| 标题 | MySQL Slow Queries |
| 图表类型 | `<Line>` 1 系列 |
| Query 类型 | range query |
| PromQL | `sum(rate(mysql_global_status_slow_queries{job=~"$job", instance=~"$instance"}[$__interval]))` |
| y 轴 | `/s` |
| 系列颜色 | `#faad14`（橙，告警色） |

#### M12 Aborted Connections

| 属性 | 值 |
|---|---|
| 标题 | MySQL Aborted Connections |
| 图表类型 | `<Line>` 2 系列 |
| Query 类型 | range query |
| PromQL (connects) | `sum(rate(mysql_global_status_aborted_connects{job=~"$job", instance=~"$instance"}[$__interval]))` |
| PromQL (clients) | `sum(rate(mysql_global_status_aborted_clients{job=~"$job", instance=~"$instance"}[$__interval]))` |
| 系列 | `Aborted Connects`（橙红）、`Aborted Clients`（红） |

#### M13 Table Locks

| 属性 | 值 |
|---|---|
| 标题 | MySQL Table Locks |
| 图表类型 | `<Line>` 2 系列 |
| Query 类型 | range query |
| PromQL (immediate) | `sum(rate(mysql_global_status_table_locks_immediate{job=~"$job", instance=~"$instance"}[$__interval]))` |
| PromQL (waited) | `sum(rate(mysql_global_status_table_locks_waited{job=~"$job", instance=~"$instance"}[$__interval]))` |
| 系列 | `Immediate`（绿）、`Waited`（红，锁等待为竞争信号） |

---

### 5.5 R5 — InnoDB & 内存

#### M14 Internal Memory Overview

| 属性 | 值 |
|---|---|
| 标题 | MySQL Internal Memory Overview |
| 图表类型 | `<Area>` 堆叠多系列 |
| Query 类型 | range query（多条 PromQL，详见 catalog `MySQL Internal Memory Overview`） |
| PromQL (Buffer Pool Data) | `sum(mysql_global_status_innodb_page_size{job=~"$job", instance=~"$instance"} * on (instance) mysql_global_status_buffer_pool_pages{job=~"$job", instance=~"$instance", state="data"})` |
| PromQL (Log Buffer) | `sum(mysql_global_variables_innodb_log_buffer_size{job=~"$job", instance=~"$instance"})` |
| PromQL (Key Buffer) | `sum(mysql_global_variables_key_buffer_size{job=~"$job", instance=~"$instance"})` |
| PromQL (Adaptive Hash) | `sum(mysql_global_status_innodb_mem_adaptive_hash{job=~"$job", instance=~"$instance"})` |
| y 轴 | bytes，自动换算 |
| 系列 | `legend` 右侧表格，按 avg 降序；隐藏空/零系列（`hideEmpty`/`hideZero`） |

> MySQL 8.0 已移除 Query Cache 与 `innodb_additional_mem_pool_size`，对应系列在 8.0.28 上恒为 0，由 `hideZero` 自动隐藏。

#### M15 Temporary Objects

| 属性 | 值 |
|---|---|
| 标题 | MySQL Temporary Objects |
| 图表类型 | `<Line>` 3 系列 |
| Query 类型 | range query |
| PromQL (tmp tables) | `sum(rate(mysql_global_status_created_tmp_tables{job=~"$job", instance=~"$instance"}[$__interval]))` |
| PromQL (tmp disk tables) | `sum(rate(mysql_global_status_created_tmp_disk_tables{job=~"$job", instance=~"$instance"}[$__interval]))` |
| PromQL (tmp files) | `sum(rate(mysql_global_status_created_tmp_files{job=~"$job", instance=~"$instance"}[$__interval]))` |
| 系列 | `Tmp Tables`（蓝）、`Tmp Disk Tables`（红，落盘临时表为饱和信号）、`Tmp Files`（橙） |

---

### 5.6 R6 — Handlers & 命令分布

#### M16 MySQL Handlers

| 属性 | 值 |
|---|---|
| 标题 | MySQL Handlers |
| 图表类型 | `<Line>` 多系列 by `handler` |
| Query 类型 | range query |
| PromQL | `rate(mysql_global_status_handlers_total{job=~"$job", instance=~"$instance", handler!~"commit\|rollback\|savepoint.*\|prepare"}[$__interval])` |
| 系列字段 | `handler`（read_rnd_next / write / update / delete 等） |
| Legend | 右侧表格，按 avg 降序 |

> **归一化**：catalog 原 PromQL 用 `instance=~"$host"`，本 spec 改为 `$instance`；事务类 handler（commit/rollback/...）拆到下方说明，不在本面板。

#### M17 Top Command Counters

| 属性 | 值 |
|---|---|
| 标题 | Top Command Counters |
| 图表类型 | `<Line>` 多系列 |
| Query 类型 | range query |
| PromQL | `topk(5, rate(mysql_global_status_commands_total{job=~"$job", instance=~"$instance"}[$__interval]) > 0)` |
| 系列字段 | `command`（select / insert / update / set_option 等） |
| 说明 | 仅展示速率前 5 的命令；`> 0` 过滤掉空闲命令 |

---

## 6. 主题 / 样式规范

**完全复用 `prometheus-dashboard-prototype-spec.md` §6 中的颜色 Token 和工具函数**（`CHART_COLORS`、`colorByThreshold`、`formatBytes`、`TIME_AXIS_CONFIG`、tooltip 格式）。

```ts
import { CHART_COLORS, colorByThreshold, formatBytes } from '../utils/formatters';
```

MySQL 特有补充：

```ts
// reverse 阈值（值越小越危险，用于 Uptime）
function colorByThresholdReverse(value: number, low: number, mid: number): string {
  if (value < low) return CHART_COLORS.error;     // 红
  if (value < mid) return CHART_COLORS.warning;   // 橙
  return CHART_COLORS.success;                     // 绿
}

// 秒 → 人类可读时长（Uptime）
function formatDuration(seconds: number): string {
  const d = Math.floor(seconds / 86400);
  const h = Math.floor((seconds % 86400) / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  return d > 0 ? `${d}d ${h}h` : h > 0 ? `${h}h ${m}m` : `${m}m`;
}
```

---

## 7. 数据层接口 TypeScript 定义

**复用 `prometheus-dashboard-prototype-spec.md` §7 中全部接口定义**（`PrometheusVector`、`PrometheusMatrix`、`TimeSeriesPoint`、`StatValue`）。

MySQL 特有补充：

```ts
interface MySQLDashboardQueryParams {
  clusterId: number;
  start: number;      // unix timestamp (seconds)
  end: number;        // unix timestamp (seconds)
  step: number;       // 建议 = (end - start) / 200
  variables: {
    instance: string;   // 正则，如 ".+" 或 "10.0.0.1:9104"
    job: string;        // 正则，如 ".+" 或 "mysql"
    // 注：$__interval 由 start/end 自动计算，不在 variables 中
  };
}
```

---

## 8. 组件树结构

```
<MySQLDashboard>                      # 页面容器，管理 variables + time range + refresh
  ├── <DashboardToolbar>              # 引用 `_shared/DashboardToolbar.tsx`（Instance + Job + 自动刷新）
  │
  ├── <Row R1>                        # 概览 Stat（6 个 Stat 面板）
  │   ├── <StatPanel M01>             # Uptime（reverse 阈值 + formatDuration）
  │   ├── <StatPanel M02>             # Current QPS
  │   ├── <StatPanel M03>             # Connections Used %（阈值染色）
  │   ├── <StatPanel M04>             # InnoDB Buffer Pool（formatBytes）
  │   ├── <StatPanel M05>             # Slow Queries /s（阈值染色）
  │   └── <StatPanel M06>             # Aborted Connections /s（阈值染色）
  │
  ├── <Row R2>                        # 流量
  │   ├── <TimeSeriesPanel M07>       # MySQL Questions (QPS)
  │   └── <AreaPanel M08>             # Network Traffic（recv/sent）
  │
  ├── <Row R3>                        # 连接 & 线程
  │   ├── <TimeSeriesPanel M09>       # MySQL Connections（含上限线）
  │   └── <TimeSeriesPanel M10>       # Client Thread Activity
  │
  ├── <Row R4>                        # 错误
  │   ├── <TimeSeriesPanel M11>       # Slow Queries
  │   ├── <TimeSeriesPanel M12>       # Aborted Connections
  │   └── <TimeSeriesPanel M13>       # Table Locks
  │
  ├── <Row R5>                        # InnoDB & 内存
  │   ├── <AreaPanel M14>             # Internal Memory Overview
  │   └── <TimeSeriesPanel M15>       # Temporary Objects
  │
  └── <Row R6>                        # Handlers & 命令
      ├── <TimeSeriesPanel M16>       # MySQL Handlers
      └── <TimeSeriesPanel M17>       # Top Command Counters

# 复用的基础组件（来自 `monitor/_shared/panels/`）
StatPanel / TimeSeriesPanel / AreaPanel / DashboardToolbar / useDashboardData ← 均来自 `monitor/_shared/`
```

---

## 9. 实现说明（供 Phase 3 编码参考）

### 9.1 文件路径

```
datasophon-ui-v2/src/pages/MySQLMonitor/
  ├── index.tsx                     # 页面容器（6 行布局）
  ├── panelQueries.ts               # PanelDef（17 个面板的 instant/range 定义）
  ├── hooks/
  │   └── useMySQLDashboard.ts      # 调用 `useDashboardData`（`_shared/useDashboardData.ts`）
  ├── panels/                       # 无此目录 — 直接从 `../../_shared/panels/` import
  ├── toolbar/                      # 引用 `_shared/DashboardToolbar.tsx`
  ├── mock/
  │   └── mysqlMockData.ts          # 确定性伪随机静态数据
  └── utils/                        # 无此目录 — 直接从 `../../_shared/charts/` import（追加 colorByThresholdReverse / formatDuration）
```

### 9.2 PromQL 变量替换规则（MySQL 版）

```ts
function replaceMySQLVars(promql: string, vars: MySQLDashboardQueryParams['variables'], interval: string): string {
  return promql
    .replace(/\$instance/g, vars.instance || '.+')
    .replace(/\$job/g,      vars.job      || '.+')
    .replace(/\[\$__interval\]/g, `[${interval}]`);   // interval = calcRateInterval(start, end)
}
```

### 9.2.1 Hook 集成（`useMySQLDashboard` 实现说明）

`useMySQLDashboard` 调用通用 `useDashboardData`，有两个 MySQL 特有点：

1. **`rateInterval` 注入**：`calcRateInterval(TIME_RANGE_SECONDS[timeRange])` 计算速率窗口，传给 `replaceMySQLVars` 作为第三参数（而非合入 `variables`，因为 `[$__interval]` 是括号语法而非 `$key` 语法）。

2. **`extras.up` 派生下拉**：传入 `extras = { up: { query: 'mysql_up', kind: 'instant' } }`，
   结果 `data.extras.up` 经 `deriveInstancesAndJobs` 得到实例/Job 选择器列表。

```ts
const data = useDashboardData({
  panelQueries: PANEL_QUERIES,
  replaceVars: (promql, vars) => replaceMySQLVars(promql, vars, rateInterval),
  variables,
  panelIds: ALL_PANEL_IDS,   // M01–M17 全量（单 segment）
  extras: { up: { query: 'mysql_up', kind: 'instant' } },
  timeRange, clusterId, refreshKey,
});
```

### 9.3 Mock 数据要求

`mysqlMockData.ts` 覆盖全部 17 个面板：

**Stat 面板（M01-M06，instant 值）：**
- M01 Uptime: `259200`（3 天，绿色）
- M02 Current QPS: `1240`（蓝色）
- M03 Conn Used %: `34`（绿色）
- M04 InnoDB Buffer Pool: `2147483648`（2 GB）
- M05 Slow Queries /s: `0`（绿色；偶发突刺到 0.3 验证橙色）
- M06 Aborted Conn /s: `0`（绿色）

**Range 面板（M07-M17）：**
- M07 Questions: 1100–1400 QPS 平稳波动
- M08 Network Traffic: inbound ≈ 2 MB/s，outbound ≈ 8 MB/s
- M09 Connections: connected 30–45、max used 120、max connections 151（上限线）
- M10 Thread Activity: connected 30–45、running 2–6
- M11 Slow Queries: 多数为 0，偶发 0.2–0.5/s 突刺
- M12 Aborted: connects 偶发 0–1/s，clients ≈ 0
- M13 Table Locks: immediate 高（200/s），waited ≈ 0（偶发突刺）
- M14 Internal Memory: Buffer Pool Data ≈ 1.5 GB、Key Buffer ≈ 64 MB、Adaptive Hash ≈ 80 MB（8.0 上 Query Cache/Additional Mem Pool 系列恒 0，被隐藏）
- M15 Temp Objects: tmp tables 5–15/s、tmp disk tables 偶发 0–1/s、tmp files ≈ 0
- M16 Handlers: read_rnd_next ≈ 5000/s、write ≈ 800/s、update ≈ 300/s 等多系列
- M17 Top Commands: select ≈ 1200/s、insert ≈ 200/s、update ≈ 150/s、set_option、commit（topk 5）

### 9.4 速率窗口与 `max_over_time` 说明

catalog 多处用 `max_over_time(...[$__interval])` 对 Gauge 类（threads_connected）取窗口最大值，避免采样抖动；`rate(...[$__interval])` 用于 Counter 类。两类在 `panelQueries.ts` 的 PanelDef 中按面板硬编码，前端仅做 `$__interval` 替换，不改变聚合算子。

### 9.5 MySQL 8.0 增补建议（可选，超出本原型范围）

selection.md 选型备注提到可结合 Grafana ID **20016**（专为 MySQL 8.0、更新至 2024）补充 8.0 特有面板。若后续接入真实 8.0.28 实例，建议追加：

- **Query Response Time 分布**（需启用 `query_response_time` 插件）：`query_response_time_seconds` 直方图 → 补齐真正的 Latency 黄金信号；
- **InnoDB Redo Log 容量**：`mysql_global_status_innodb_redo_log_*`（8.0 新增动态 redo log）；
- **Performance Schema 等待事件 Top-N**：`mysql_perf_schema_*`（需 `--collect.perf_schema.*` 采集器）。

---

## 10. Dev 环境配置注意事项

**完全复用 `prometheus-dashboard-prototype-spec.md` §10 中的三项配置**（publicPath、proxy bypass、mock 路径对齐）。

MySQL 看板无额外 dev 环境差异：后端代理端点路径与 PrometheusMonitor 完全相同（`/ddh/api/v2/prometheus/query` 和 `/query_range`），mock 文件路径写法相同。

---

## 11. 验收标准

Phase 2 原型（mock 阶段）完成后，需满足：

- [ ] 全部 17 个面板（M01-M17）按 §4 布局渲染（6 行 24 列 Grid）
- [ ] R1 行 6 个 Stat 面板使用 antd `<Statistic>` + 正确阈值方向（M01 reverse：Uptime 小→红；M03/M05/M06 正向：值大→红/橙）
- [ ] M01 Uptime 用 `formatDuration` 显示（如 `3d 0h`），非裸秒数
- [ ] M03 Connections Used % 为派生百分比面板（已连接 / max_connections × 100），非绝对值
- [ ] M08 Network Traffic、M14 Internal Memory 使用 `<Area>` + `formatBytes` y 轴格式化
- [ ] M09 Connections 含 Max Connections 上限参考线（红虚线）
- [ ] M16/M17 PromQL 已归一化为 `$instance`（不出现 `$host`）
- [ ] M14 在 8.0 数据下自动隐藏恒零的 Query Cache/Additional Mem Pool 系列（`hideZero`）
- [ ] 工具栏：实例多选 + Job 多选 + 时间范围 + 刷新（Interval 自动，无下拉）
- [ ] 颜色方案遵循 §6 Token，错误系（Slow Queries/Waited/Aborted）用告警橙红
- [ ] 在 1280px 宽度下 6 行布局无横向滚动条
- [ ] golden signals 四象限覆盖验证（见 §5.0 映射表）；Latency 维度以 Slow Queries 代理并在 §9.5 标注增补路径
