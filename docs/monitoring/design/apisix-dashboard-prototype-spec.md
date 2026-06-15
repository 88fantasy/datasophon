# APISIX 监控看板原型设计 Spec

> **文档用途**：供 Claude design 阅读，根据本 spec 设计 React + AntV G2 看板原型，并最终生成可运行的组件代码。  
> **组件**：Apache APISIX 3.16.0  
> **数据源**：原生 `/apisix/prometheus/metrics:9091`（无需 exporter）  
> **参考 Grafana 看板**：[Apache APISIX](https://grafana.com/grafana/dashboards/11719) (ID 11719)  
> **Panel Catalog 路径**：`docs/monitoring/panel-catalog/APISIX.json`（17 个面板）  
> **Phase**：Phase 2 —— 原型设计阶段

---

## 1. 架构约束

```
React(AntV G2)
  └──HTTP──> datasophon-api /api/v1/prometheus/query_range
               └──PromQL──> Prometheus :9090
                              └──scrape──> APISIX /apisix/prometheus/metrics:9091
```

**前端不直连 Prometheus**。所有 PromQL 通过后端代理端点转发。前端只需调用：

```
GET /ddh/api/v1/prometheus/query_range
  ?clusterId={clusterId}
  &query={PromQL}
  &start={unix_ts}
  &end={unix_ts}
  &step={step_seconds}

GET /ddh/api/v1/prometheus/query   # 用于 instant query（stat/singlestat 面板）
  ?clusterId={clusterId}
  &query={PromQL}
  &time={unix_ts}
```

后端代理返回原生 Prometheus JSON 格式（`matrix` / `vector` result type）。

---

## 2. 图表类型映射字典

本项目使用 **`@ant-design/plots`** v2.x（AntV G2 v5 的 React 声明式封装）。

> ⚠️ **注意**：`@ant-design/plots` v2 对应 G2 v5，API 与 v1（`@ant-design/charts`）有重大差异，详见下表。

| Grafana chartType | 面板特征 | AntV G2 组件 | 关键 props（G2 v5 / plots v2） |
|---|---|---|---|
| `singlestat` | 单一数值，无时序 | `<Statistic>` (antd) | `value`, `suffix`, `valueStyle` |
| `stat` with green/red thresholds | 状态值，颜色随阈值变化 | `<Statistic>` + `colorByThreshold()` | `valueStyle.color` 根据值计算 |
| `graph` 单系列折线 | 时序数据，1 条线 | `<Line>` (@ant-design/plots) | `xField='time'`, `yField='value'` |
| `graph` 多系列折线 | `by (label)` 多条线 | `<Line>` with `seriesField` | `seriesField='series'` |
| `graph` 堆叠面积 | legend.total=true, 带宽类 | `<Area>` stack | `stack={true}`（**非** `isStack`） |
| `graph` 单位 bytes | 数据量/带宽 | `<Area>` + `axis.y.labelFormatter` | 自动换算 B/KB/MB/GB |
| `graph` 单位 ms | 延迟 | `<Line>` + `axis.y.labelFormatter` | `(v) => \`${v}ms\`` |
| `graph` 单位 percent | 百分比 | `<Line>` + `axis.y.labelFormatter` | `(v) => \`${v}%\`` |

### G2 v5 关键 API 变更（v1 → v2）

| 属性 | v1（`@ant-design/charts`） | v2（`@ant-design/plots`，G2 v5） |
|---|---|---|
| 堆叠 | `isStack={true}` | `stack={true}` |
| 坐标轴 | `xAxis={{ ... }}` / `yAxis={{ ... }}` | `axis={{ x: { ... }, y: { ... } }}` |
| 坐标轴 label | `xAxis.label.formatter` | `axis.x.labelFormatter` |
| 多系列颜色 | `color={['#f00', '#00f']}` | `scale={{ color: { type: 'ordinal', range: ['#f00', '#00f'] } }}` |
| style 回调 | `style={{ stroke: ({ series }) => colorMap[series] }}` | **不支持**解构对象形式；改用 `scale.color.range` 指定颜色顺序 |
| tooltip formatter | `tooltip.formatter: (datum) => ...` | `tooltip.items: [(datum) => ({ name, value })]` |
| tooltip 标题 | `tooltip.title` (string) | `tooltip.title: (datum) => string` |

---

## 3. 变量 / 过滤器规范

看板顶部工具栏包含以下变量选择器，所有 PromQL 中的占位符对应替换：

| 变量 | PromQL 占位符 | 取值来源 | 默认值 | 说明 |
|---|---|---|---|---|
| 实例 | `$instance` | `label_values(apisix_http_requests_total, instance)` | `.+`（全选） | 多选下拉 |
| 服务 | `$service` | `label_values(apisix_http_status, service)` | `.+`（全选） | 多选下拉 |
| 路由 | `$route` | `label_values(apisix_http_status, route)` | `.+`（全选） | 多选下拉 |
| 消费者 | `$consumer` | `label_values(apisix_http_latency_bucket, consumer)` | `.+`（全选） | 多选下拉 |
| 上游节点 | `$node` | `label_values(apisix_http_latency_bucket, node)` | `.+`（全选） | 多选下拉 |
| 时间范围 | `$__rate_interval` | 由时间范围自动计算 | `Last 1h` | 快速选择: 5m/15m/1h/6h/24h/7d |
| 刷新间隔 | — | — | `30s` | 自动轮询 |

> `$__rate_interval` 在前端根据选定时间范围计算：`max(4 * scrape_interval, timeRange / resolution_points)`，通常取 `step` 的 4 倍。

---

## 4. 看板布局（24 列 Grid）

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│  TOOLBAR: [实例▼] [服务▼] [路由▼] [消费者▼] [节点▼]  [Last 1h▼]  [🔄 30s▼]     │
└─────────────────────────────────────────────────────────────────────────────────┘

行 R1 — 摘要统计（高度 80px）
┌──────────────────┬──────────────────┬──────────────────┐
│  Total Requests  │Accepted Connections│Handled Connections│
│  [Statistic]     │ [Statistic]       │ [Statistic]       │
│  col-span=8      │ col-span=8        │ col-span=8        │
└──────────────────┴──────────────────┴──────────────────┘

行 R2 — 状态指示（高度 80px）
┌────────────────────────────────────────┬───────────────────────────────────────┐
│  Etcd Reachable                        │  Nginx Metric Errors                  │
│  [StatusStatistic: 绿/红阈值染色]      │  [StatusStatistic: 绿/黄阈值染色]      │
│  col-span=12                           │ col-span=12                           │
└────────────────────────────────────────┴───────────────────────────────────────┘

行 R3 — 流量（高度 200px）
┌────────────────────────────────────────┬───────────────────────────────────────┐
│  Total RPS                             │  RPS by Status Code                   │
│  [Line: 1 系列]                        │  [Line: 多系列 by code]               │
│  col-span=12                           │  col-span=12                          │
└────────────────────────────────────────┴───────────────────────────────────────┘

行 R4 — 延迟（高度 200px）
┌──────────────────┬──────────────────┬──────────────────┐
│  Request Latency │  APISIX Latency  │  Upstream Latency │
│  [Line: p90/95/99│  [Line: p90/95/99│  [Line: p90/95/99 │
│  col-span=8      │  col-span=8      │  col-span=8       │
└──────────────────┴──────────────────┴──────────────────┘

行 R5 — 带宽（高度 200px）
┌────────────────────────────────────────┬───────────────────────────────────────┐
│  Total Bandwidth                       │  RPS per Service/Route                │
│  [Area: ingress/egress 堆叠]           │  [Line: 多系列 by service]            │
│  col-span=12                           │  col-span=12                          │
└────────────────────────────────────────┴───────────────────────────────────────┘

行 R6 — 连接 & 共享字典（高度 200px）
┌────────────────────────────────────────┬───────────────────────────────────────┐
│  Nginx Connection State                │  Nginx Shared Dict Free Space         │
│  [Line: 多系列 by state stacked]       │  [Line: 多系列 by dict name]          │
│  col-span=12                           │  col-span=12                          │
└────────────────────────────────────────┴───────────────────────────────────────┘

行 R7 — Etcd（高度 200px）
┌─────────────────────────────────────────────────────────────────────────────────┐
│  Etcd Modify Indexes                                                            │
│  [Line: 多系列 by key]                                                          │
│  col-span=24                                                                    │
└─────────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. 面板规格（逐面板）

### 5.1 R1 — 摘要统计

#### P01 Total Requests

| 属性 | 值 |
|---|---|
| 标题 | Total Requests |
| 图表类型 | `<Statistic>` (antd) |
| Query 类型 | instant query |
| PromQL | `sum(apisix_http_requests_total{instance=~"$instance"})` |
| 单位 | 无（整数，自动千位分隔符） |
| 样式 | 大字体 32px，绿色 `#52c41a`，标题灰色 |
| 变量替换 | `$instance` |

#### P02 Accepted Connections

| 属性 | 值 |
|---|---|
| 标题 | Accepted Connections |
| 图表类型 | `<Statistic>` (antd) |
| Query 类型 | instant query |
| PromQL | `sum(apisix_nginx_http_current_connections{state="accepted", instance=~"$instance"})` |
| 单位 | 无（整数） |
| 样式 | 大字体 32px，蓝色 `#1677ff` |

#### P03 Handled Connections

| 属性 | 值 |
|---|---|
| 标题 | Handled Connections |
| 图表类型 | `<Statistic>` (antd) |
| Query 类型 | instant query |
| PromQL | `sum(apisix_nginx_http_current_connections{state="handled", instance=~"$instance"})` |
| 单位 | 无（整数） |
| 样式 | 大字体 32px，蓝色 `#1677ff` |

---

### 5.2 R2 — 状态指示

#### P04 Etcd Reachable

| 属性 | 值 |
|---|---|
| 标题 | Etcd Reachable |
| 图表类型 | `<Statistic>` + `<Badge>` (antd) |
| Query 类型 | instant query |
| PromQL | `sum(apisix_etcd_reachable{instance=~"$instance"})` |
| 阈值规则 | `value >= 1` → 绿色 `#52c41a` + "Healthy"；`value < 1` → 红色 `#ff4d4f` + "Unreachable" |
| 展示格式 | 数字 + 右侧彩色 Badge 文字 |

#### P05 Nginx Metric Errors

| 属性 | 值 |
|---|---|
| 标题 | Nginx Metric Errors |
| 图表类型 | `<Statistic>` + 阈值染色 |
| Query 类型 | instant query |
| PromQL | `sum(apisix_nginx_metric_errors_total{instance=~"$instance"})` |
| 阈值规则 | `value = 0` → 绿色 `#52c41a`；`value >= 1` → 黄色 `#faad14` |
| 展示格式 | 整数 |

---

### 5.3 R3 — 流量

#### P06 Total RPS

| 属性 | 值 |
|---|---|
| 标题 | Total Requests per Second |
| 图表类型 | `<Line>` (@ant-design/charts) |
| Query 类型 | range query |
| PromQL | `sum(rate(apisix_http_status{instance=~"$instance"}[$__rate_interval]))` |
| x 轴 | 时间（ISO 8601 格式） |
| y 轴 | RPS，单位 `req/s`，保留 2 位小数 |
| 系列数 | 1（无 `by` 分组） |
| 系列颜色 | `#1677ff` |
| Tooltip | 时间 + 值 |

#### P07 RPS by Status Code

| 属性 | 值 |
|---|---|
| 标题 | RPS by Status Code |
| 图表类型 | `<Line>` 多系列 |
| Query 类型 | range query |
| PromQL | `sum(rate(apisix_http_status{service=~"$service",route=~"$route",instance=~"$instance"}[$__rate_interval])) by (code)` |
| x 轴 | 时间 |
| y 轴 | RPS，`req/s` |
| 系列字段 | `code`（HTTP 状态码） |
| 颜色规则 | `2xx` → 绿色；`3xx` → 蓝色；`4xx` → 黄色；`5xx` → 红色 |
| Legend | 显示在右侧，勾选切换系列 |

---

### 5.4 R4 — 延迟

以下 3 个面板结构完全相同，仅 `type` label 过滤值不同。

#### P08 Request Latency

| 属性 | 值 |
|---|---|
| 标题 | Request Latency |
| 图表类型 | `<Line>` 多系列（3 分位线） |
| Query 类型 | range query（3 条 PromQL 同时执行） |
| PromQL (p90) | `histogram_quantile(0.90, sum(rate(apisix_http_latency_bucket{type=~"request",service=~"$service",consumer=~"$consumer",node=~"$node",route=~"$route"}[$__rate_interval])) by (le))` |
| PromQL (p95) | 同上，`0.90` → `0.95` |
| PromQL (p99) | 同上，`0.90` → `0.99` |
| y 轴 | 延迟，单位 `ms` |
| 系列 | `p90`（蓝）、`p95`（橙）、`p99`（红） |
| Threshold 背景 | `value > 80ms` 时在图表背景加淡红色区域 |

#### P09 APISIX Latency

同 P08，`type=~"request"` 改为 `type=~"apisix"`。标题：APISIX Latency。

#### P10 Upstream Latency

同 P08，`type=~"request"` 改为 `type=~"upstream"`。标题：Upstream Latency。

---

### 5.5 R5 — 带宽

#### P11 Total Bandwidth

| 属性 | 值 |
|---|---|
| 标题 | Total Bandwidth |
| 图表类型 | `<Area>` 堆叠面积 |
| Query 类型 | range query |
| PromQL | `sum(rate(apisix_bandwidth{instance=~"$instance"}[$__rate_interval])) by (type)` |
| y 轴 | 带宽，单位 bytes/s，自动换算（B/KB/MB/GB） |
| 系列字段 | `type`（`ingress`/`egress`） |
| isStack | `true`，ingress 绿色、egress 蓝色 |
| Tooltip | 同时显示 ingress + egress + total |

#### P12 RPS per Service/Route

| 属性 | 值 |
|---|---|
| 标题 | RPS per Service/Route |
| 图表类型 | `<Line>` 多系列 |
| Query 类型 | range query |
| PromQL (by service) | `sum(rate(apisix_http_status{service=~"$service",route=~"$route",instance=~"$instance"}[$__rate_interval])) by (service)` |
| PromQL (by route) | 同上，`by (service)` → `by (route)` |
| 切换 | Tab 或 Select 切换 "by service" / "by route" |
| Legend 位置 | 右侧（alignAsTable=true） |
| 显示统计 | avg / max / current（对应 Grafana legend） |

---

### 5.6 R6 — 连接 & 共享字典

#### P13 Nginx Connection State

| 属性 | 值 |
|---|---|
| 标题 | Nginx Connection State |
| 图表类型 | `<Area>` 堆叠面积 |
| Query 类型 | range query |
| PromQL | `sum(apisix_nginx_http_current_connections{state=~"active\|reading\|writing\|waiting", instance=~"$instance"}) by (state)` |
| 系列字段 | `state` |
| 颜色 | active=蓝、reading=青、writing=橙、waiting=灰 |
| isStack | `true` |

#### P14 Nginx Shared Dict Free Space

| 属性 | 值 |
|---|---|
| 标题 | Nginx Shared Dict Free Space (%) |
| 图表类型 | `<Line>` 多系列 |
| Query 类型 | range query |
| PromQL | `(apisix_shared_dict_free_space_bytes * 100) / on (name) apisix_shared_dict_capacity_bytes` |
| y 轴 | 百分比 0–100%，刻度每 25% |
| 系列字段 | `name`（字典名称） |
| 警戒线 | y=20%，红色虚线（低于此值表示内存紧张） |

---

### 5.7 R7 — Etcd

#### P15 Etcd Modify Indexes

| 属性 | 值 |
|---|---|
| 标题 | Etcd Modify Indexes |
| 图表类型 | `<Line>` 多系列 |
| Query 类型 | range query |
| PromQL | `sum(apisix_etcd_modify_indexes{key=~"consumers\|global_rules\|max_modify_index\|prev_index\|protos\|routes\|services\|ssls\|stream_routes\|upstreams\|x_etcd_index"}) by (key)` |
| 系列字段 | `key` |
| y 轴 | 整数，无单位 |
| 图表宽度 | 全行（col-span=24） |

---

## 6. 主题 / 样式规范

本项目使用 Ant Design 5 + AntV G2 v5（`@ant-design/plots` v2.x）。

### 6.1 颜色 Token

```ts
const CHART_COLORS = {
  primary:   '#1677ff',  // Ant Design 默认蓝
  success:   '#52c41a',  // 绿（正常/healthy）
  warning:   '#faad14',  // 黄（告警）
  error:     '#ff4d4f',  // 红（危险/unreachable）
  // 多系列配色（循环使用）
  series: ['#1677ff', '#52c41a', '#faad14', '#ff4d4f', '#722ed1', '#eb2f96', '#13c2c2', '#fa8c16'],
};

// HTTP status code 专用配色
const STATUS_CODE_COLORS: Record<string, string> = {
  '2': '#52c41a',  // 2xx
  '3': '#1677ff',  // 3xx
  '4': '#faad14',  // 4xx
  '5': '#ff4d4f',  // 5xx
};
```

### 6.2 图表公共配置（G2 v5 语法）

```ts
// 所有时序图的 x 轴配置（G2 v5：用 axis.x 而非 xAxis）
const TIME_AXIS_CONFIG = {
  x: {
    labelFormatter: (v: number) => dayjs(v).format('HH:mm'),
    tickCount: 5,
  },
};

// 多系列颜色：通过 scale.color 指定，不用 style 回调
function buildColorScale(seriesNames: string[], colorMap: Record<string, string>) {
  const range = seriesNames.map((n) => colorMap[n] ?? '#1677ff');
  return { color: { type: 'ordinal' as const, range } };
}
```

### 6.3 Tooltip 格式（G2 v5 语法）

```ts
// G2 v5：tooltip.items 是函数数组，不是 formatter 对象
const latencyTooltip = {
  title: (d: TimeSeriesPoint) => dayjs(d.time).format('HH:mm:ss'),
  items: [
    (d: TimeSeriesPoint) => ({
      name: d.series,
      value: `${d.value.toFixed(1)} ms`,
    }),
  ],
};

const bandwidthTooltip = {
  title: (d: TimeSeriesPoint) => dayjs(d.time).format('HH:mm:ss'),
  items: [
    (d: TimeSeriesPoint) => ({
      name: d.series,
      value: `${formatBytes(d.value)}/s`,
    }),
  ],
};
```

---

## 7. 数据层接口 TypeScript 定义

```ts
// Prometheus instant query 响应
interface PrometheusVector {
  resultType: 'vector';
  result: Array<{
    metric: Record<string, string>;
    value: [number, string]; // [timestamp, value]
  }>;
}

// Prometheus range query 响应
interface PrometheusMatrix {
  resultType: 'matrix';
  result: Array<{
    metric: Record<string, string>;
    values: Array<[number, string]>; // [[timestamp, value], ...]
  }>;
}

// 前端看板查询参数
interface DashboardQueryParams {
  clusterId: number;
  start: number;    // unix timestamp (seconds)
  end: number;      // unix timestamp (seconds)
  step: number;     // 步长 (seconds)，建议 = (end - start) / 200
  variables: {
    instance: string;   // 正则，如 ".*" 或 "10.0.0.1:9091"
    service: string;
    route: string;
    consumer: string;
    node: string;
  };
}

// 时序面板数据点（转换后）
interface TimeSeriesPoint {
  time: number;   // timestamp ms（供 G2 时间轴使用）
  value: number;
  series: string; // legend 标签
}

// Statistic 面板数据
interface StatValue {
  value: number;
  color: string;  // 经阈值计算后的颜色
  label?: string; // 如 "Healthy" / "Unreachable"
}
```

---

## 8. 组件树结构

```
<ApisixDashboard>                   # 页面容器，管理 variables + time range + refresh
  ├── <DashboardToolbar>            # 工具栏：变量选择器 + 时间选择 + 刷新
  ├── <Row R1>                      # 摘要统计行
  │   ├── <StatPanel id="P01">      # Total Requests
  │   ├── <StatPanel id="P02">      # Accepted Connections
  │   └── <StatPanel id="P03">      # Handled Connections
  ├── <Row R2>                      # 状态指示行
  │   ├── <StatusStatPanel id="P04">  # Etcd Reachable
  │   └── <StatusStatPanel id="P05">  # Nginx Metric Errors
  ├── <Row R3>                      # 流量行
  │   ├── <TimeSeriesPanel id="P06">  # Total RPS
  │   └── <TimeSeriesPanel id="P07">  # RPS by Status Code
  ├── <Row R4>                      # 延迟行
  │   ├── <TimeSeriesPanel id="P08">  # Request Latency
  │   ├── <TimeSeriesPanel id="P09">  # APISIX Latency
  │   └── <TimeSeriesPanel id="P10">  # Upstream Latency
  ├── <Row R5>                      # 带宽行
  │   ├── <AreaPanel id="P11">      # Total Bandwidth
  │   └── <TimeSeriesPanel id="P12"> # RPS per Service/Route
  ├── <Row R6>                      # 连接 & 字典
  │   ├── <AreaPanel id="P13">      # Nginx Connection State
  │   └── <TimeSeriesPanel id="P14"> # Shared Dict Free Space
  └── <Row R7>                      # Etcd
      └── <TimeSeriesPanel id="P15"> # Etcd Modify Indexes

# 可复用基础组件（按职责抽取）
<StatPanel>          # instant query → <Statistic>
<StatusStatPanel>    # instant query + thresholds → <Statistic> + <Badge>
<TimeSeriesPanel>    # range query → <Line> (multi-series)
<AreaPanel>          # range query → <Area> (stacked)
<DashboardToolbar>   # 变量 + 时间范围 + 刷新
<usePrometheusQuery> # custom hook：封装 range/instant 请求 + 轮询
```

---

## 9. 实现说明（供 Phase 3 编码参考）

### 9.1 文件路径

**当前实现（datasophon-ui-v2，mock 阶段）**

```
datasophon-ui-v2/src/pages/ApisixMonitor/
  ├── index.tsx               # 主页面（路由 /apisix-monitor，临时一级路由）
  ├── panels/
  │   ├── StatPanel.tsx
  │   ├── StatusStatPanel.tsx
  │   ├── TimeSeriesPanel.tsx
  │   └── AreaPanel.tsx
  ├── toolbar/
  │   └── DashboardToolbar.tsx
  ├── mock/
  │   └── apisixMockData.ts   # 确定性伪随机静态数据（Math.sin 种子）
  └── utils/
      ├── formatters.ts       # formatBytes, colorByThreshold, labelByThreshold
      └── promql.ts           # 变量替换（Phase 3 接入真实数据时使用）
```

**Phase 3 目标路径（迁移后）**

```
datasophon-ui/src/pages/ServiceManage/Instance/Overview/
  ├── ApisixDashboard.tsx
  ├── panels/ toolbar/ hooks/ utils/   # 同上，新增 usePrometheusQuery hook
```

### 9.2 PromQL 变量替换规则

```ts
function replaceVars(promql: string, vars: DashboardQueryParams['variables']): string {
  return promql
    .replace(/\$instance/g, vars.instance || '.+')
    .replace(/\$service/g, vars.service || '.+')
    .replace(/\$route/g, vars.route || '.+')
    .replace(/\$consumer/g, vars.consumer || '.+')
    .replace(/\$node/g, vars.node || '.+')
    .replace(/\[\$__rate_interval\]/g, `[${calcRateInterval(params.start, params.end)}s]`);
}
```

### 9.3 Prometheus 数据转换（matrix → G2 数据）

```ts
function matrixToSeries(matrix: PrometheusMatrix, seriesField: string): TimeSeriesPoint[] {
  return matrix.result.flatMap((series) => {
    const label = series.metric[seriesField] ?? 'value';
    return series.values.map(([ts, val]) => ({
      time: ts * 1000,          // 秒→毫秒，供 G2 时间轴
      value: parseFloat(val),
      series: label,
    }));
  });
}
```

### 9.4 刷新轮询（usePrometheusQuery hook 基本结构）

```ts
function usePrometheusQuery(
  type: 'range' | 'instant',
  promql: string,
  params: DashboardQueryParams,
  refreshInterval: number,   // ms，0 = 不刷新
) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchData = () => { /* axios 调用后端代理 */ };
    fetchData();
    if (refreshInterval > 0) {
      const timer = setInterval(fetchData, refreshInterval);
      return () => clearInterval(timer);
    }
  }, [promql, params, refreshInterval]);

  return { data, loading };
}
```

---

## 10. Dev 环境配置注意事项

在 datasophon-ui-v2（UMI Max v4）开发时，以下三个配置缺一不可，否则会出现白屏或 504：

### 10.1 publicPath 必须在 dev 下设为 `'/'`

```ts
// config/config.ts
const PUBLIC_PATH =
  process.env.NODE_ENV === 'development' ? '/' : '/ddh/static/';
```

**原因**：生产环境 `publicPath='/ddh/static/'` 使静态资源 URL 带上 `/ddh` 前缀，而 dev proxy 规则 `'/ddh' → localhost:8080` 会把这些静态资源请求转发到后端，导致 504。

### 10.2 proxy bypass — 页面路由必须跳过代理

```ts
// config/proxy.ts
'/ddh': {
  target: 'http://localhost:8080',
  changeOrigin: true,
  bypass(req: import('http').IncomingMessage) {
    const accept = req.headers['accept'];
    if (accept?.includes('text/html')) return req.url ?? '/';
    return null;
  },
},
```

**原因**：UMI `base: '/ddh'` 让所有 React 路由都带 `/ddh` 前缀，proxy 规则会把页面导航请求（`GET /ddh/apisix-monitor`）也转发到后端，返回 504。`bypass` 让 `text/html` 请求由 dev server 自己处理（historyApiFallback → index.html）。

### 10.3 mock 路径必须与 baseURL 对齐

```ts
// src/app.tsx
export const request: RequestConfig = {
  baseURL: '/ddh/api/v2',   // 所有请求会加上这个前缀
  ...
};
```

```ts
// mock/user.ts — 路径必须写完整，不能只写 /api/xxx
'POST /ddh/api/v2/login/account': async (req, res) => { ... },
'GET  /ddh/api/v2/currentUser':   (_req, res) => { ... },
'POST /ddh/api/v2/logout':        (_req, res) => { ... },
```

**原因**：UMI mock 按路径精确匹配，`baseURL` 是 axios 运行时拼接的，mock 文件里写 `/api/login/account` 不会匹配实际请求路径 `/ddh/api/v2/login/account`，请求会穿透到 proxy → 504。

---

## 11. 验收标准

Phase 2 原型（mock 阶段）完成后，需满足：

- [ ] 全部 15 个面板（P01-P15）有对应的 React 组件 mock 展示（可用静态数据）
- [ ] 时序面板（P06-P15）使用 `@ant-design/charts` `<Line>` 或 `<Area>` 渲染
- [ ] 统计面板（P01-P05）使用 antd `<Statistic>` + 阈值染色
- [ ] 工具栏含时间范围快捷选择 + 刷新间隔 + 5 个变量下拉
- [ ] 布局与第 4 节 ASCII 图吻合（24 列 Grid，各行高度正确）
- [ ] 颜色方案遵循第 6.1 节 Token
- [ ] TypeScript 类型与第 7 节接口定义一致
- [ ] 响应式：在 1280px 宽度下不出现横向滚动条
