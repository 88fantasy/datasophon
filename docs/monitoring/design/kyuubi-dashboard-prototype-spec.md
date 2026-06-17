# Kyuubi 监控看板原型设计 Spec

> **文档用途**：供 Claude design 阅读，根据本 spec 设计 React + AntV G2 看板原型，并最终生成可运行的组件代码。
> **组件**：Apache Kyuubi 1.11.1
> **数据源**：原生 Prometheus 端点 `/metrics:10019`（Kyuubi 内置 PrometheusReporter）
> **参考来源**：**grafana.com 市场无 Kyuubi 看板**，唯一来源为官方仓库 `apache/kyuubi` 的 `grafana/dashboard-template.json`（已归档至 `docs/monitoring/dashboards-reference/Kyuubi/dashboard-template.json`，31 个面板）
> **Panel Catalog 路径**：**无**（非 grafana.com 看板，未走 extract-panel-catalog；本 spec 直接从官方模板 JSON 抽取面板）
> **Phase**：Phase 2 —— 原型设计阶段

---

## 1. 架构约束

```
React(AntV G2)
  └──HTTP──> datasophon-api /api/v2/prometheus/query_range
               └──PromQL──> Prometheus :9090
                              └──scrape──> Kyuubi Server /metrics:10019
```

**前端不直连 Prometheus**。所有 PromQL 通过后端代理端点转发（详见 `prometheus-dashboard-prototype-spec.md` §1）。

### 1.1 Kyuubi 指标特点 & 模板特殊点

- **统一前缀 `kyuubi_`**：原生指标，命名与官方模板完全一致（`kyuubi_jvm_uptime`、`kyuubi_connection_opened_INTERACTIVE`、`kyuubi_operation_state_*` 等）。
- **★ 关键特殊点：变量插值进指标名**。官方模板用 `$connType` / `$opType` 直接拼进**指标名**，如 `kyuubi_${connType}_opened`、`kyuubi_operation_state_${opType}_error_total`。这与常规「变量只在 label selector 里」不同——**前端做 PromQL 替换时必须把 `${connType}` 替换进指标名字符串本身**（而非 `{}` 内）。
  - `$connType` 取值：`connection_total_INTERACTIVE` / `connection_total_BATCH` 等（连接类型）
  - `$opType` 取值：`ExecuteStatement` / `LaunchEngine` 等（操作类型）
- **`$baseFilter`**：官方模板的通用 label 过滤变量（如 `instance=~".+"` 或按集群标识），本 spec 保留为可选前缀过滤。
- **状态计数指标**：`kyuubi_operation_state_<OP>_<state>_total`（state ∈ running/pending/error/closed/finished），`<state>=error` 即 Error 信号。
- **GC 指标随回收器变化**：模板示例用 `kyuubi_gc_ZGC_Cycles_count`（ZGC）；不同 JVM 回收器指标名不同（G1 → `kyuubi_gc_G1_*`），落地需按实际 JVM 调整。

> **豁免说明**：Kyuubi 是 selection.md 中「唯一来源、豁免多候选要求」的组件。本 spec 以官方模板为基线裁剪，并按选型备注**补齐 Error 象限**（连接 failed + 操作 error）。

---

## 2. 图表类型映射字典

**完全复用 `prometheus-dashboard-prototype-spec.md` §2 中的映射字典**。

Kyuubi 特有补充：

| 模板 panel type | 映射组件 | 备注 |
|---|---|---|
| `stat`（Instances/Uptime） | `<Statistic>` | Uptime 用 `formatDuration` |
| `timeseries`（`increase(...[$trendInterval])`） | `<Line>` | 趋势类用 increase 窗口（new sessions/operations） |
| `timeseries`（state error/failed） | `<Line>` 错误红系 | Error 象限 |
| `timeseries`（memory pools） | `<Area>` 堆叠 | JVM Statistics |

---

## 3. 变量 / 过滤器规范

| 变量 | PromQL 占位符 | 取值来源 | 默认值 | 说明 |
|---|---|---|---|---|
| 实例 | `$instance` | `label_values(kyuubi_jvm_uptime, instance)` | `.+` | 多选下拉，Kyuubi Server 节点 |
| baseFilter | `$baseFilter` | 自定义（集群/cluster label） | 空或 `instance=~".+"` | 通用过滤前缀，可选 |
| 连接类型 | `$connType` | 固定枚举：`connection_total_INTERACTIVE` / `connection_total_BATCH` | INTERACTIVE | **插值进指标名** |
| 操作类型 | `$opType` | 固定枚举：`ExecuteStatement` / `LaunchEngine` 等 | ExecuteStatement | **插值进指标名** |
| 趋势窗口 | `$trendInterval` | 由时间范围派生 | `5m` | 用于 `increase()` |
| 时间范围 | — | 时间选择器 | `Last 1h` | 5m/15m/1h/6h/24h/7d |
| 刷新间隔 | — | — | `30s` | 自动轮询 |

> 原型可先固定 `$connType=INTERACTIVE`、`$opType=ExecuteStatement`（最常用），将这两个下拉作为高级选项；`$baseFilter` 默认空。

---

## 4. 看板布局（24 列 Grid）

裁剪策略：官方模板 31 面板 → 聚焦四黄金信号的 **16 面板**。保留 Overview 核心 + Connection/Operation 关键态（含 Error）+ JVM 内存合并；剔除 Extra、按需的 connType/opType 重复展开（保留模板供深挖）。

```
┌──────────────────────────────────────────────────────────────────────┐
│  TOOLBAR: [实例▼]  [连接类型▼] [操作类型▼]   [Last 1h▼]  [🔄 30s▼]  │
└──────────────────────────────────────────────────────────────────────┘

行 R1 — 概览 Stat（高度 80px，col=4 ×6）
┌────────┬────────┬────────┬────────┬────────┬────────┐
│Instances│Uptime │Connectn│Engine  │Exec    │Op Error│
│        │        │Opened  │Total   │Threads │Rate ★  │
│ KY01   │ KY02   │ KY03   │ KY04   │ KY05   │ KY06   │
└────────┴────────┴────────┴────────┴────────┴────────┘

行 R2 — 连接 & 操作流量 Traffic（高度 200px）
┌────────────────────────────┬────────────────────────────┐
│ KY07 Session (new)         │ KY08 Operation (new)       │
│ [Line increase]            │ [Line increase]            │
└────────────────────────────┴────────────────────────────┘

行 R3 — 延迟 & 引擎 Latency/Saturation（高度 200px）
┌────────────────────────────┬────────────────────────────┐
│ KY09 Operation pending/run │ KY10 Engine Launching &    │
│ [Line]                     │ Startup Permit             │
└────────────────────────────┴────────────────────────────┘

行 R4 — 错误 Errors ★（高度 200px）
┌────────────────────────────┬────────────────────────────┐
│ KY11 Connection Failed ★   │ KY12 Operation Error ★     │
│ [Line by connType]         │ [Line by opType]           │
└────────────────────────────┴────────────────────────────┘

行 R5 — 吞吐 & 批处理 Traffic/Latency（高度 200px）
┌────────────────────────────┬────────────────────────────┐
│ KY13 Fetch Rows (new)      │ KY14 Max Batch Pending     │
│ [Line increase]            │ Elapse [Line]              │
└────────────────────────────┴────────────────────────────┘

行 R6 — JVM Saturation（高度 200px）
┌────────────────────────────┬────────────────────────────┐
│ KY15 JVM Memory Usage      │ KY16 JVM Memory Pools      │
│ [Line used/ratio]          │ [Area 堆叠 by pool]        │
└────────────────────────────┴────────────────────────────┘
```

---

## 5. 面板规格（逐面板）

### 5.0 Golden Signals 映射

| 维度 | 面板 | 说明 |
|---|---|---|
| **Latency（延迟）** | KY09 Operation pending/running、KY14 Max Batch Pending Elapse | 操作排队/运行 + 批处理等待延迟 |
| **Traffic（流量）** | KY03 Connection Opened、KY07 Session new、KY08 Operation new、KY13 Fetch Rows | 连接/会话/操作/取数吞吐 |
| **Errors（错误）** | KY06 Op Error Rate ★、KY11 Connection Failed ★、KY12 Operation Error ★ | 连接失败 + 操作错误（补齐 Error 象限） |
| **Saturation（饱和度）** | KY04 Engine Total、KY05 Exec Threads、KY10 Engine Launching/Permit、KY15/KY16 JVM | 引擎数、线程池、启动许可、内存 |

> ★ selection.md 指出官方模板「Error 信号完全缺失」；实际模板含 `$connType(failed)` 与 `$opType(error)`，本 spec 将其提升为 KY06/KY11/KY12 三个显式 Error 面板，补齐象限。

---

### 5.1 R1 — 概览 Stat

#### KY01 Instances

| 属性 | 值 |
|---|---|
| 标题 | Instances |
| 图表类型 | `<Statistic>` |
| Query 类型 | instant query |
| PromQL | `count(kyuubi_jvm_uptime{$baseFilter})` |

#### KY02 Uptime

| 属性 | 值 |
|---|---|
| 标题 | Uptime |
| 图表类型 | `<Statistic>` + `formatDuration` |
| PromQL | `kyuubi_jvm_uptime{$baseFilter,instance=~"$instance"}` |
| 单位 | 秒（或 ms，按模板实际，需核对）→ d/h/m |

#### KY03 Connection Opened

| 属性 | 值 |
|---|---|
| 标题 | Connection Opened |
| 图表类型 | `<Statistic>` |
| PromQL | `sum(kyuubi_connection_opened_INTERACTIVE{$baseFilter,instance=~"$instance"})` |

#### KY04 Engine Total

| 属性 | 值 |
|---|---|
| 标题 | Engine Total |
| 图表类型 | `<Statistic>` |
| PromQL | `sum(kyuubi_engine_total{$baseFilter,instance=~"$instance"})` |

#### KY05 Exec Pool Threads

| 属性 | 值 |
|---|---|
| 标题 | Exec Pool Threads |
| 图表类型 | `<Statistic>` |
| PromQL | `sum(kyuubi_exec_pool_threads_alive{$baseFilter,instance=~"$instance"})` |

#### KY06 Operation Error Rate ★

| 属性 | 值 |
|---|---|
| 标题 | Operation Error Rate |
| 图表类型 | `<Statistic>` + `colorByThreshold` |
| PromQL | `sum(increase(kyuubi_operation_state_${opType}_error_total{$baseFilter,instance=~"$instance"}[$trendInterval]))` |
| 阈值 | `= 0` → 绿；`> 0` → 红 |
| 说明 | ★ 补强 Error 象限即时值 |

---

### 5.2 R2 — 连接 & 操作流量

#### KY07 Session (new)

| 属性 | 值 |
|---|---|
| 标题 | Session (new) [$trendInterval] |
| 图表类型 | `<Line>` |
| Query 类型 | range query |
| PromQL | `increase(kyuubi_connection_total_INTERACTIVE{$baseFilter,instance=~"$instance"}[$trendInterval])` |
| y 轴 | 新增会话数/窗口 |

#### KY08 Operation (new)

| 属性 | 值 |
|---|---|
| 标题 | Operation (new) [$trendInterval] |
| 图表类型 | `<Line>` |
| PromQL | `increase(kyuubi_operation_total_ExecuteStatement{$baseFilter,instance=~"$instance"}[$trendInterval])` |

---

### 5.3 R3 — 延迟 & 引擎

#### KY09 Operation Pending / Running

| 属性 | 值 |
|---|---|
| 标题 | Operation Pending / Running |
| 图表类型 | `<Line>` 2 系列 |
| PromQL (pending) | `kyuubi_operation_state_${opType}_pending_total{$baseFilter,instance=~"$instance"}` |
| PromQL (running) | `kyuubi_operation_state_${opType}_running_total{$baseFilter,instance=~"$instance"}` |
| 系列 | `Pending`（橙，排队）、`Running`（蓝） |

#### KY10 Engine Launching & Startup Permit

| 属性 | 值 |
|---|---|
| 标题 | Engine Launching & Startup Permit |
| 图表类型 | `<Line>` 2 系列 |
| PromQL (launching) | `kyuubi_operation_state_LaunchEngine_running_total{$baseFilter,instance=~"$instance"}` |
| PromQL (permit) | `kyuubi_engine_startup_permit_limit_total{$baseFilter,instance=~"$instance"}` |
| 系列 | `Launching`（蓝）、`Startup Permit Limit`（灰虚线，上限） |

---

### 5.4 R4 — 错误 ★

#### KY11 Connection Failed ★

| 属性 | 值 |
|---|---|
| 标题 | Connection Failed |
| 图表类型 | `<Line>` 错误红系 |
| Query 类型 | range query |
| PromQL | `kyuubi_${connType}_failed{$baseFilter,instance=~"$instance"}` |
| 系列颜色 | `#ff4d4f` |
| 说明 | ★ `$connType` 插值进指标名 |

#### KY12 Operation Error ★

| 属性 | 值 |
|---|---|
| 标题 | Operation Error |
| 图表类型 | `<Line>` 错误红系 |
| PromQL | `kyuubi_operation_state_${opType}_error_total{$baseFilter,instance=~"$instance"}` |
| 系列颜色 | `#ff4d4f` |
| 说明 | ★ 补强；建议同时叠加 `kyuubi_operation_failed_total` / `kyuubi_engine_open_failed_count`（selection.md 备注）作为额外系列 |

---

### 5.5 R5 — 吞吐 & 批处理

#### KY13 Fetch Rows (new)

| 属性 | 值 |
|---|---|
| 标题 | Fetch Rows [$trendInterval] |
| 图表类型 | `<Line>` |
| PromQL | `increase(kyuubi_backend_service_fetch_result_rows_rate_total{$baseFilter,instance=~"$instance"}[$trendInterval])` |
| y 轴 | 取数行数/窗口 |

#### KY14 Max Batch Pending Elapse

| 属性 | 值 |
|---|---|
| 标题 | Max Batch Pending Elapse |
| 图表类型 | `<Line>` |
| PromQL | `kyuubi_operation_batch_pending_max_elapse{$baseFilter,instance=~"$instance"}` |
| 单位 | ms（批处理最大等待时长，延迟信号） |

---

### 5.6 R6 — JVM

#### KY15 JVM Memory Usage

| 属性 | 值 |
|---|---|
| 标题 | JVM Memory Usage |
| 图表类型 | `<Line>` 2 系列（used + ratio 双 y 轴） |
| PromQL (used) | `kyuubi_memory_usage_total_used{$baseFilter,instance=~"$instance"}` |
| PromQL (ratio) | `kyuubi_memory_usage_total_used{$baseFilter,instance=~"$instance"} / kyuubi_memory_usage_heap_max{$baseFilter,instance=~"$instance"}` |
| y 轴 | 左：bytes（`formatBytes`）；右：比率（0–1，可 ×100 显示 %） |

#### KY16 JVM Memory Pools

| 属性 | 值 |
|---|---|
| 标题 | JVM Memory Pools |
| 图表类型 | `<Area>` 堆叠 |
| PromQL | `kyuubi_memory_usage_pools_PS_Eden_Space_used` / `..._PS_Old_Gen_used` / `..._PS_Survivor_Space_used` / `..._Metaspace_used` / `..._Code_Cache_used`（各加 `{$baseFilter,instance=~"$instance"}`） |
| 系列 | `Eden` / `Old Gen` / `Survivor` / `Metaspace` / `Code Cache`（复用 Nexus `jvmPoolColors`） |
| 说明 | 池名取决于 JVM 回收器（PS = ParallelGC）；ZGC/G1 命名不同，落地按实际调整 |

---

## 6. 主题 / 样式规范

**完全复用 `prometheus-dashboard-prototype-spec.md` §6 中的颜色 Token 和工具函数**（含 `formatDuration` / `formatBytes`），并复用 Nexus spec 的 `jvmPoolColors`（内存池配色）。Error 面板统一 `#ff4d4f` 红。

---

## 7. 数据层接口 TypeScript 定义

**复用 `prometheus-dashboard-prototype-spec.md` §7 中全部接口定义**。

```ts
interface KyuubiDashboardQueryParams {
  clusterId: number;
  start: number;
  end: number;
  step: number;
  variables: {
    instance: string;     // 正则
    baseFilter: string;   // 通用过滤前缀，可空
    connType: string;     // 插值进指标名，如 "connection_total_INTERACTIVE"
    opType: string;       // 插值进指标名，如 "ExecuteStatement"
    trendInterval: string;// increase 窗口，如 "5m"
  };
}
```

---

## 8. 组件树结构

```
<KyuubiDashboard>
  ├── <DashboardToolbar>              # Instance 多选 + connType/opType 下拉 + 时间范围 + 刷新
  │
  ├── <Row R1>                        # 概览 Stat（6 个）
  │   ├── <StatPanel KY01>            # Instances
  │   ├── <StatPanel KY02>            # Uptime（formatDuration）
  │   ├── <StatPanel KY03>            # Connection Opened
  │   ├── <StatPanel KY04>            # Engine Total
  │   ├── <StatPanel KY05>            # Exec Pool Threads
  │   └── <StatPanel KY06>            # Operation Error Rate ★（>0 红）
  │
  ├── <Row R2>                        # 连接 & 操作流量
  │   ├── <TimeSeriesPanel KY07>      # Session (new)
  │   └── <TimeSeriesPanel KY08>      # Operation (new)
  │
  ├── <Row R3>                        # 延迟 & 引擎
  │   ├── <TimeSeriesPanel KY09>      # Operation Pending / Running
  │   └── <TimeSeriesPanel KY10>      # Engine Launching & Permit
  │
  ├── <Row R4>                        # 错误 ★
  │   ├── <TimeSeriesPanel KY11>      # Connection Failed ★
  │   └── <TimeSeriesPanel KY12>      # Operation Error ★
  │
  ├── <Row R5>                        # 吞吐 & 批处理
  │   ├── <TimeSeriesPanel KY13>      # Fetch Rows (new)
  │   └── <TimeSeriesPanel KY14>      # Max Batch Pending Elapse
  │
  └── <Row R6>                        # JVM
      ├── <TimeSeriesPanel KY15>      # JVM Memory Usage（双 y 轴）
      └── <AreaPanel KY16>            # JVM Memory Pools（堆叠）

# 复用的基础组件（来自 `monitor/_shared/panels/`）
StatPanel / TimeSeriesPanel / AreaPanel / DashboardToolbar / useDashboardData ← 均来自 `monitor/_shared/`
```

---

## 9. 实现说明（供 Phase 3 编码参考）

### 9.1 文件路径

```
datasophon-ui-v2/src/pages/KyuubiMonitor/
  ├── index.tsx                     # 页面容器（6 行布局）
  ├── panelQueries.ts               # PanelDef（16 个面板）
  ├── hooks/useKyuubiDashboard.ts
  ├── panels/                       # 引用 `monitor/_shared/panels/`
  ├── toolbar/
  │   └── KyuubiDashboardToolbar.tsx # Instance + connType + opType 下拉
  ├── mock/kyuubiMockData.ts
  └── utils/                        # 无此目录 — 直接从 `../../_shared/charts/` import
```

### 9.2 PromQL 变量替换规则（Kyuubi 版，★ 含指标名插值）

```ts
function replaceKyuubiVars(promql: string, vars: KyuubiDashboardQueryParams['variables']): string {
  return promql
    // ★ connType/opType 插值进指标名（${var} 形式），必须先替换
    .replace(/\$\{connType\}/g, vars.connType)
    .replace(/\$\{opType\}/g,   vars.opType)
    // baseFilter 是 label 过滤前缀
    .replace(/\$baseFilter/g,   vars.baseFilter || '')
    .replace(/\$instance/g,     vars.instance   || '.+')
    .replace(/\$trendInterval/g,vars.trendInterval || '5m');
}
```

> ⚠️ `${connType}` / `${opType}` 用 `${...}` 包裹（Grafana 显式插值语法），替换后直接成为指标名一部分（如 `kyuubi_operation_state_ExecuteStatement_error_total`）。`$baseFilter` 为空时需清理可能残留的多余逗号（`{,instance=...}` → `{instance=...}`）。

### 9.2.1 Hook 集成（`useKyuubiDashboard` 实现说明）

`replaceKyuubiVars` 与通用 `replaceVars` 不兼容（`${connType}` 是花括号语法）。解决方案：将 `replaceKyuubiVars` 作为 adapter 传给 `useDashboardData`：

```ts
const data = useDashboardData({
  replaceVars: (promql, vars) =>
    replaceKyuubiVars(promql, vars as Partial<KyuubiDashboardVariables>),
  ...
});
```

**extras 中 query 须预展开**：`extras.instanceList.query` 需要在定义时就调用 `replaceKyuubiVars` 展开（`useDashboardData` 对 extras 不做变量替换，直接按原始 query 请求）：

```ts
const extras = useMemo(() => ({
  instanceList: { query: replaceKyuubiVars('kyuubi_jvm_uptime{$baseFilter}', effectiveVariables), kind: 'instant' },
}), [effectiveVariables]);
```

### 9.3 $baseFilter 空值处理

`$baseFilter` 默认空。PromQL 形如 `{$baseFilter,instance=~"$instance"}`，替换后可能出现 `{,instance=~".+"}`。替换函数末尾需做 `.replace(/\{\s*,/g, '{')` 清理前导逗号。

### 9.4 Mock 数据要求

`kyuubiMockData.ts` 覆盖 16 面板：
- KY01 Instances `2`、KY02 Uptime `172800`（2 天）、KY03 Connection Opened `45`、KY04 Engine Total `8`、KY05 Exec Threads `64`、KY06 Op Error Rate `0`（绿）
- KY07 Session new: 5–15/窗口、KY08 Operation new: 30–80/窗口
- KY09 pending 0–3 / running 2–6
- KY10 launching 0–2 / permit limit 10
- KY11 Connection Failed ≈ 0（偶发 1 突刺）、KY12 Operation Error ≈ 0（偶发突刺验证红色）
- KY13 Fetch Rows: 数十万行/窗口、KY14 Max Batch Pending: 200–2000 ms
- KY15 Memory: used ≈ 3 GB / ratio ≈ 0.45、KY16 Pools: Old Gen ≈ 2 GB、Eden 锯齿、Metaspace ≈ 200 MB

---

## 10. Dev 环境配置注意事项

**完全复用 `prometheus-dashboard-prototype-spec.md` §10**。后端代理端点与 PrometheusMonitor 相同。

---

## 11. 验收标准

- [ ] 全部 16 个面板（KY01-KY16）按 §4 布局渲染（6 行 24 列 Grid）
- [ ] **变量替换正确处理 `${connType}`/`${opType}` 插值进指标名**（§9.2），且 `$baseFilter` 空值时清理前导逗号（§9.3）
- [ ] KY02 Uptime 用 `formatDuration`；KY06 Op Error Rate `>0` 红
- [ ] **Error 象限补强到位**：KY06（stat）+ KY11 Connection Failed + KY12 Operation Error 三个面板，红色系
- [ ] KY10 含 Startup Permit Limit 上限参考线
- [ ] KY15 双 y 轴（used bytes / ratio）；KY16 用 `<Area>` 堆叠 + `jvmPoolColors`
- [ ] 工具栏：Instance 多选 + connType 下拉 + opType 下拉 + 时间范围 + 刷新
- [ ] 标注 GC/内存池指标名随 JVM 回收器变化（PS/G1/ZGC），落地需核对
- [ ] 在 1280px 宽度下 6 行布局无横向滚动条
- [ ] golden signals 四象限覆盖验证（见 §5.0）；Error 象限由 KY06/KY11/KY12 补强（修正官方模板缺口）
