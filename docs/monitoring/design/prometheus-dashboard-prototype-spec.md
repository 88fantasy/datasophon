# Prometheus 监控看板原型设计 Spec

> **文档用途**：供 Claude design 阅读，根据本 spec 设计 React + AntV G2 看板原型，并最终生成可运行的组件代码。  
> **组件**：Prometheus 3.12.0  
> **数据源**：原生 `/metrics:9090`（Prometheus 自监控，无需 exporter）  
> **参考 Grafana 看板**：[Prometheus 2.0 Overview](https://grafana.com/grafana/dashboards/3662) (ID 3662)  
> **Panel Catalog 路径**：`docs/monitoring/panel-catalog/Prometheus.json`（30 个原始面板，精选 24 个）  
> **Phase**：Phase 2 —— 原型设计阶段

---

## 1. 架构约束

```
React(AntV G2)
  └──HTTP──> datasophon-api /api/v1/prometheus/query_range
               └──PromQL──> Prometheus :9090
                              └──scrape──> Prometheus 自身 /metrics:9090
```

**前端不直连 Prometheus**。所有 PromQL 通过后端代理端点转发。前端只需调用：

```
GET /ddh/api/v1/prometheus/query_range
  ?clusterId={clusterId}
  &query={PromQL}
  &start={unix_ts}
  &end={unix_ts}
  &step={step_seconds}

GET /ddh/api/v1/prometheus/query   # 用于 instant query（stat/table 面板）
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
| `singlestat` 带 reverse 阈值 | 值越高越好（如 Uptime） | `<Statistic>` + `colorByThreshold({reverse:true})` | `valueStyle.color` 反向计算 |
| `stat` with thresholds | 状态值，颜色随阈值变化 | `<Statistic>` + `colorByThreshold()` | `valueStyle.color` 根据值计算 |
| `table` | 向量查询结果列表 | `<Table>` (antd) | `dataSource`, `columns`，列含 `instance`/`job`/`value` |
| `graph` 单系列折线 | 时序数据，1 条线 | `<Line>` (@ant-design/plots) | `xField='time'`, `yField='value'` |
| `graph` 多系列折线 | `by (label)` 多条线 | `<Line>` with `seriesField` | `seriesField='series'` |
| `graph` 堆叠面积 | 多实例 0/1 叠加 | `<Area>` stack | `stack={true}` |
| `graph` 单位 bytes | 内存/存储数据量 | `<Area>` + `axis.y.labelFormatter` | 自动换算 B/KB/MB/GB |
| `graph` 单位 ms | 延迟/持续时间 | `<Line>` + `axis.y.labelFormatter` | `(v) => \`${v.toFixed(1)}ms\`` |
| `graph` 单位 s | 抓取时长（秒） | `<Line>` + `axis.y.labelFormatter` | `(v) => \`${v.toFixed(3)}s\`` |
| `graph` 单位 min | 配置距今时长（分钟） | `<Line>` + `axis.y.labelFormatter` | `(v) => \`${v.toFixed(1)}min\`` |

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
| 实例 | `$instance` | `label_values(up{job=~"$job"}, instance)` | `.+`（全选） | 多选下拉 |
| Job | `$job` | `label_values(up, job)` | `.+`（全选） | 多选下拉 |
| 统计窗口 | `$interval` | 固定选项列表 | `5m` | 单选下拉；替换 `sum_over_time/avg_over_time` 的时间窗口 |
| 时间范围 | — | 时间选择器 | `Last 1h` | 快速选择: 5m/15m/1h/6h/24h/7d |
| 刷新间隔 | — | — | `30s` | 自动轮询 |

> **`$interval` 替换规则**：PromQL 中出现 `[$interval]` 时，将 `$interval` 直接替换为选中值（如 `5m`），结果为 `[5m]`。  
> 与 APISIX 的 `$__rate_interval`（由时间范围自动计算）不同，`$interval` 是用户手动选择的固定窗口，语义为"过去多长时间内的累计/平均值"。  
> **`$interval` 可选值**：`1m`、`5m`（默认）、`15m`、`30m`、`1h`

---

## 4. 看板布局（24 列 Grid）

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│  TOOLBAR: [实例▼] [Job▼] [Interval▼ 5m]     [Last 1h▼]   [🔄 30s▼]            │
└──────────────────────────────────────────────────────────────────────────────────┘

行 R1 — 概览统计（高度 80px）
┌────────┬────────┬────────┬────────┬────────┬────────┐
│ Uptime │ Total  │ Memory │ Reload │ Missed │Skipped │
│  [%]   │Series  │Chunks  │Failures│ Itera- │Scrapes │
│ col=4  │ col=4  │ col=4  │ col=4  │ col=4  │ col=4  │
└────────┴────────┴────────┴────────┴────────┴────────┘

行 R2 — 目标健康（高度 200px）
┌───────────────────────────┬────────────────────────────────────────────────────┐
│  Currently Down           │  Upness (stacked by instance)                      │
│  [Table: up<1 列表]       │  [Area: 0/1 堆叠，每实例一层]                      │
│  col-span=8               │  col-span=16                                        │
└───────────────────────────┴────────────────────────────────────────────────────┘

行 R3 — 抓取延迟（高度 200px）
┌───────────────────────────────────────┬────────────────────────────────────────┐
│  Scrape Duration                      │  Target Sync (ms)                      │
│  [Line: 多实例 by instance]           │  [Line: 多系列 by scrape_job]          │
│  col-span=12                          │  col-span=12                           │
└───────────────────────────────────────┴────────────────────────────────────────┘

行 R4 — 抓取池 & 拒绝（高度 200px）
┌───────────────────────────────────────┬────────────────────────────────────────┐
│  Scrape Sync Total                    │  Rejected Scrapes                      │
│  [Line: 多系列 by scrape_job]         │  [Line: 4 系列 by 拒绝原因]           │
│  col-span=12                          │  col-span=12                           │
└───────────────────────────────────────┴────────────────────────────────────────┘

行 R5 — TSDB Series（高度 200px）
┌──────────────────────┬──────────────────────┬──────────────────────┐
│  Series Count        │  Series Created /    │  Appended Samples/s  │
│  [Line: by instance] │  Removed             │  [Line: by instance] │
│  col-span=8          │  [Line: 2 系列]      │  col-span=8          │
│                      │  col-span=8          │                      │
└──────────────────────┴──────────────────────┴──────────────────────┘

行 R6 — 存储 & Go 运行时（高度 200px）
┌──────────────────────┬──────────────────────┬──────────────────────┐
│  Storage Memory      │  Go Memory Usage     │  GC Rate / 2m        │
│  Chunks              │  (6 key series)      │  [Line: by instance] │
│  [Line: by instance] │  [Area: bytes]       │  col-span=8          │
│  col-span=8          │  col-span=8          │                      │
└──────────────────────┴──────────────────────┴──────────────────────┘

行 R7 — 规则评估 & 查询（高度 200px）
┌──────────────────────┬──────────────────────┬──────────────────────┐
│  Rule Evaluator      │  Avg Rule Eval       │  Engine Query        │
│  Iterations          │  Duration (ms)       │  Duration by slice   │
│  [Line: 3 系列]      │  [Line: 单系列]      │  [Line: by slice]    │
│  col-span=8          │  col-span=8          │  col-span=8          │
└──────────────────────┴──────────────────────┴──────────────────────┘

行 R8 — 错误 & 配置 & 通知（高度 200px）
┌────────────────────────────────────────┬──────────────┬──────────────┐
│  Failures and Errors                   │ Notifications│ Minutes Since│
│  [Line: 6 代表性错误系列]              │ Sent         │ Config Reload│
│  col-span=12                           │ [Line: 单系列│ [Line: 单系列│
│                                        │  col-span=6] │  col-span=6] │
└────────────────────────────────────────┴──────────────┴──────────────┘
```

---

## 5. 面板规格（逐面板）

### 5.1 R1 — 概览统计

#### P01 Uptime

| 属性 | 值 |
|---|---|
| 标题 | Uptime [$interval] |
| 图表类型 | `<Statistic>` (antd) |
| Query 类型 | instant query |
| PromQL | `avg(avg_over_time(up{instance=~"$instance",job=~"$job"}[$interval]) * 100)` |
| 单位 | `%`，保留 1 位小数 |
| 阈值规则 | **reverse（越高越好）**：`value >= 99` → 绿色 `#52c41a`；`90 ≤ value < 99` → 黄色 `#faad14`；`value < 90` → 红色 `#ff4d4f` |
| 样式 | 大字体 32px，颜色由阈值决定，标题灰色 |
| 变量替换 | `$instance`、`$job`、`$interval` |

> ⚠️ 阈值方向与其他 stat 面板相反（`colorByThreshold` 需传 `{ reverse: true }`）

#### P02 Total Series

| 属性 | 值 |
|---|---|
| 标题 | Total Series |
| 图表类型 | `<Statistic>` (antd) |
| Query 类型 | instant query |
| PromQL | `sum(prometheus_tsdb_head_series{job=~"$job",instance=~"$instance"})` |
| 单位 | 无（整数，千位分隔符） |
| 阈值规则 | `value < 1,000,000` → 绿色；`1,000,000 ≤ value < 2,000,000` → 黄色；`value ≥ 2,000,000` → 红色 |
| 样式 | 大字体 32px |

#### P03 Memory Chunks

| 属性 | 值 |
|---|---|
| 标题 | Memory Chunks |
| 图表类型 | `<Statistic>` (antd) |
| Query 类型 | instant query |
| PromQL | `sum(prometheus_tsdb_head_chunks{job=~"$job",instance=~"$instance"})` |
| 单位 | 无（整数，千位分隔符） |
| 阈值规则 | 无（纯展示，使用默认蓝色 `#1677ff`） |
| 样式 | 大字体 32px，蓝色 `#1677ff` |

#### P04 Reload Failures

| 属性 | 值 |
|---|---|
| 标题 | Reload Failures [$interval] |
| 图表类型 | `<Statistic>` (antd) |
| Query 类型 | instant query |
| PromQL | `sum(sum_over_time(prometheus_tsdb_reloads_failures_total{job=~"$job",instance=~"$instance"}[$interval]))` |
| 单位 | 无（整数） |
| 阈值规则 | `value = 0` → 绿色 `#52c41a`；`1 ≤ value < 10` → 黄色 `#faad14`；`value ≥ 10` → 红色 `#ff4d4f` |
| 变量替换 | `$instance`、`$job`、`$interval` |

#### P05 Missed Iterations

| 属性 | 值 |
|---|---|
| 标题 | Missed Iterations [$interval] |
| 图表类型 | `<Statistic>` (antd) |
| Query 类型 | instant query |
| PromQL | `sum(sum_over_time(prometheus_evaluator_iterations_missed_total{job=~"$job",instance=~"$instance"}[$interval]))` |
| 单位 | 无（整数） |
| 阈值规则 | `value = 0` → 绿色；`1 ≤ value < 10` → 黄色；`value ≥ 10` → 红色 |
| 变量替换 | `$instance`、`$job`、`$interval` |

#### P06 Skipped Scrapes

| 属性 | 值 |
|---|---|
| 标题 | Skipped Scrapes [$interval] |
| 图表类型 | `<Statistic>` (antd) |
| Query 类型 | instant query |
| PromQL | 4 项累加（单行表达式）：`sum(sum_over_time(prometheus_target_scrapes_exceeded_sample_limit_total{job=~"$job",instance=~"$instance"}[$interval])) + sum(sum_over_time(prometheus_target_scrapes_sample_duplicate_timestamp_total{job=~"$job",instance=~"$instance"}[$interval])) + sum(sum_over_time(prometheus_target_scrapes_sample_out_of_bounds_total{job=~"$job",instance=~"$instance"}[$interval])) + sum(sum_over_time(prometheus_target_scrapes_sample_out_of_order_total{job=~"$job",instance=~"$instance"}[$interval]))` |
| 单位 | 无（整数） |
| 阈值规则 | `value = 0` → 绿色；`1 ≤ value < 10` → 黄色；`value ≥ 10` → 红色 |
| 变量替换 | `$instance`、`$job`、`$interval` |

---

### 5.2 R2 — 目标健康

#### P07 Currently Down

| 属性 | 值 |
|---|---|
| 标题 | Currently Down |
| 图表类型 | `<Table>` (antd)，**新增图表类型** |
| Query 类型 | instant query |
| PromQL | `up{instance=~"$instance",job=~"$job"} < 1` |
| 列定义 | `instance`（字符串）、`job`（字符串）、`value`（数值，0=Down） |
| 空状态 | 无数据时展示 "All targets are up ✅"（绿色文字） |
| 行高亮 | 所有行背景淡红色 `#fff1f0`（因为只有 down 的才显示） |
| col-span | 8 |

#### P08 Upness

| 属性 | 值 |
|---|---|
| 标题 | Upness (stacked) |
| 图表类型 | `<Area>` 堆叠面积 |
| Query 类型 | range query |
| PromQL | `up{instance=~"$instance",job=~"$job"}` |
| x 轴 | 时间 |
| y 轴 | 无单位（0 或 1），刻度 0/1 |
| 系列字段 | `instance`（每实例一层堆叠） |
| isStack | `true`（`stack={true}`） |
| 颜色 | 使用 `series` 调色板（循环使用 CHART_COLORS.series） |
| col-span | 16 |

---

### 5.3 R3 — 抓取延迟

#### P09 Scrape Duration

| 属性 | 值 |
|---|---|
| 标题 | Scrape Duration |
| 图表类型 | `<Line>` 多系列 |
| Query 类型 | range query |
| PromQL | `scrape_duration_seconds{instance=~"$instance"}` |
| x 轴 | 时间 |
| y 轴 | 秒（`s`），保留 3 位小数 |
| 系列字段 | `instance` |
| Tooltip | 时间 + 各实例值（秒） |
| col-span | 12 |

#### P10 Target Sync

| 属性 | 值 |
|---|---|
| 标题 | Target Sync (ms) |
| 图表类型 | `<Line>` 多系列 |
| Query 类型 | range query |
| PromQL | `sum(rate(prometheus_target_sync_length_seconds_sum{job=~"$job",instance=~"$instance"}[2m])) by (scrape_job) * 1000` |
| x 轴 | 时间 |
| y 轴 | 毫秒（`ms`），保留 1 位小数 |
| 系列字段 | `scrape_job` |
| Tooltip | 时间 + 各 scrape_job 值（ms） |
| col-span | 12 |

---

### 5.4 R4 — 抓取池 & 拒绝

#### P11 Scrape Sync Total

| 属性 | 值 |
|---|---|
| 标题 | Scrape Sync Total |
| 图表类型 | `<Line>` 多系列 |
| Query 类型 | range query |
| PromQL | `sum(prometheus_target_scrape_pool_sync_total{job=~"$job",instance=~"$instance"}) by (scrape_job)` |
| x 轴 | 时间 |
| y 轴 | 整数（累计同步次数，无单位） |
| 系列字段 | `scrape_job` |
| Legend | 右侧，显示各 scrape_job 名称 |
| col-span | 12 |

#### P12 Rejected Scrapes

| 属性 | 值 |
|---|---|
| 标题 | Rejected Scrapes |
| 图表类型 | `<Line>` 多系列（4 系列） |
| Query 类型 | range query |
| PromQL（4 条） | 见下表，`seriesLabel` 为前端赋名 |
| x 轴 | 时间 |
| y 轴 | 整数（累计拒绝次数） |
| 系列字段 | 手动赋名（前端 `seriesLabel` 注入）：`sample_limit` / `duplicate_ts` / `out_of_bounds` / `out_of_order` |
| 颜色 | 按 CHART_COLORS.series 循环 |
| col-span | 12 |

**P12 PromQL 明细（4 条分别执行，合并为多系列）：**

| seriesLabel | PromQL |
|---|---|
| `sample_limit` | `sum(prometheus_target_scrapes_exceeded_sample_limit_total{job=~"$job",instance=~"$instance"})` |
| `duplicate_ts` | `sum(prometheus_target_scrapes_sample_duplicate_timestamp_total{job=~"$job",instance=~"$instance"})` |
| `out_of_bounds` | `sum(prometheus_target_scrapes_sample_out_of_bounds_total{job=~"$job",instance=~"$instance"})` |
| `out_of_order` | `sum(prometheus_target_scrapes_sample_out_of_order_total{job=~"$job",instance=~"$instance"})` |

---

### 5.5 R5 — TSDB Series

#### P13 Series Count

| 属性 | 值 |
|---|---|
| 标题 | Series Count |
| 图表类型 | `<Line>` 多系列 |
| Query 类型 | range query |
| PromQL | `prometheus_tsdb_head_series{job=~"$job",instance=~"$instance"}` |
| x 轴 | 时间 |
| y 轴 | 整数（时序数量） |
| 系列字段 | `instance` |
| col-span | 8 |

#### P14 Series Created / Removed

| 属性 | 值 |
|---|---|
| 标题 | Series Created / Removed |
| 图表类型 | `<Line>` 多系列（2 系列） |
| Query 类型 | range query |
| PromQL（2 条） | 见下表 |
| x 轴 | 时间 |
| y 轴 | 整数 |
| 系列 | `created`（绿色 `#52c41a`）、`removed`（红色 `#ff4d4f`） |
| col-span | 8 |

**P14 PromQL 明细：**

| seriesLabel | PromQL |
|---|---|
| `created` | `sum(increase(prometheus_tsdb_head_series_created_total{instance=~"$instance"}[5m]))` |
| `removed` | `sum(increase(prometheus_tsdb_head_series_removed_total{instance=~"$instance"}[5m]))` |

#### P15 Appended Samples/s

| 属性 | 值 |
|---|---|
| 标题 | Appended Samples per Second |
| 图表类型 | `<Line>` 多系列 |
| Query 类型 | range query |
| PromQL | `rate(prometheus_tsdb_head_samples_appended_total{job=~"$job",instance=~"$instance"}[1m])` |
| x 轴 | 时间 |
| y 轴 | `samples/s`，保留 0 位小数 |
| 系列字段 | `instance` |
| 颜色 | `#1677ff`（单实例）或 series 调色板（多实例） |
| col-span | 8 |

---

### 5.6 R6 — 存储 & Go 运行时

#### P16 Storage Memory Chunks

| 属性 | 值 |
|---|---|
| 标题 | Storage Memory Chunks |
| 图表类型 | `<Line>` 多系列 |
| Query 类型 | range query |
| PromQL | `prometheus_tsdb_head_chunks{job=~"$job",instance=~"$instance"}` |
| x 轴 | 时间 |
| y 轴 | 整数（chunk 数量） |
| 系列字段 | `instance` |
| col-span | 8 |

#### P17 Go Memory Usage

| 属性 | 值 |
|---|---|
| 标题 | Go Memory Usage |
| 图表类型 | `<Area>` 多系列（6 关键 series） |
| Query 类型 | range query |
| PromQL（6 条） | 见下表 |
| x 轴 | 时间 |
| y 轴 | bytes，自动换算（B/KB/MB/GB） |
| 系列字段 | 手动赋名（`heap_alloc` / `heap_sys` / `heap_inuse` / `heap_idle` / `stack_inuse` / `sys`） |
| isStack | `false`（折叠展示，便于对比各内存区段） |
| Legend | 右侧，显示所有 6 个系列 |
| col-span | 8 |

**P17 PromQL 明细（6 条，从原始 18 条精选）：**

| seriesLabel | PromQL | 说明 |
|---|---|---|
| `heap_alloc` | `sum(go_memstats_heap_alloc_bytes{job=~"$job",instance=~"$instance"})` | 当前堆已分配 |
| `heap_sys` | `sum(go_memstats_heap_sys_bytes{job=~"$job",instance=~"$instance"})` | 堆从 OS 获取总量 |
| `heap_inuse` | `sum(go_memstats_heap_inuse_bytes{job=~"$job",instance=~"$instance"})` | 堆活跃使用 |
| `heap_idle` | `sum(go_memstats_heap_idle_bytes{job=~"$job",instance=~"$instance"})` | 堆空闲可释放 |
| `stack_inuse` | `sum(go_memstats_stack_inuse_bytes{job=~"$job",instance=~"$instance"})` | 栈活跃使用 |
| `sys` | `sum(go_memstats_sys_bytes{job=~"$job",instance=~"$instance"})` | 进程总内存 |

#### P18 GC Rate / 2m

| 属性 | 值 |
|---|---|
| 标题 | GC Rate / 2m |
| 图表类型 | `<Line>` 多系列 |
| Query 类型 | range query |
| PromQL | `sum(rate(go_gc_duration_seconds_sum{instance=~"$instance",job=~"$job"}[2m])) by (instance)` |
| x 轴 | 时间 |
| y 轴 | `s/s`（GC 耗时速率），保留 4 位小数 |
| 系列字段 | `instance` |
| col-span | 8 |

---

### 5.7 R7 — 规则评估 & 查询

#### P19 Rule Evaluator Iterations

| 属性 | 值 |
|---|---|
| 标题 | Rule Evaluator Iterations |
| 图表类型 | `<Line>` 多系列（3 系列） |
| Query 类型 | range query |
| PromQL（3 条） | 见下表 |
| x 轴 | 时间 |
| y 轴 | `iter/s`，保留 2 位小数 |
| 系列 | `total`（蓝 `#1677ff`）、`missed`（红 `#ff4d4f`）、`skipped`（黄 `#faad14`） |
| Tooltip | 同时显示 3 条线的值 |
| col-span | 8 |

**P19 PromQL 明细：**

| seriesLabel | PromQL |
|---|---|
| `total` | `sum(rate(prometheus_evaluator_iterations_total{job=~"$job",instance=~"$instance"}[5m]))` |
| `missed` | `sum(rate(prometheus_evaluator_iterations_missed_total{job=~"$job",instance=~"$instance"}[5m]))` |
| `skipped` | `sum(rate(prometheus_evaluator_iterations_skipped_total{job=~"$job",instance=~"$instance"}[5m]))` |

#### P20 Avg Rule Eval Duration

| 属性 | 值 |
|---|---|
| 标题 | Average Rule Evaluation Duration |
| 图表类型 | `<Line>` 单系列 |
| Query 类型 | range query |
| PromQL | `1000 * rate(prometheus_evaluator_duration_seconds_sum{job=~"$job",instance=~"$instance"}[5m]) / rate(prometheus_evaluator_duration_seconds_count{job=~"$job",instance=~"$instance"}[5m])` |
| x 轴 | 时间 |
| y 轴 | 毫秒（`ms`），保留 2 位小数 |
| 系列颜色 | `#1677ff` |
| 警戒线 | y=100ms，红色虚线（规则评估超过 100ms 可能影响告警实时性） |
| col-span | 8 |

#### P21 Engine Query Duration

| 属性 | 值 |
|---|---|
| 标题 | Prometheus Engine Query Duration |
| 图表类型 | `<Line>` 多系列 |
| Query 类型 | range query |
| PromQL | `sum(prometheus_engine_query_duration_seconds_sum{job=~"$job",instance=~"$instance"}) by (slice)` |
| x 轴 | 时间 |
| y 轴 | 秒（`s`），保留 4 位小数 |
| 系列字段 | `slice`（如 `inner_eval`、`prepare_time`、`queue_time`、`result_sort` 等） |
| Legend | 右侧，勾选切换 slice |
| col-span | 8 |

---

### 5.8 R8 — 错误 & 配置 & 通知

#### P22 Failures and Errors

| 属性 | 值 |
|---|---|
| 标题 | Failures and Errors |
| 图表类型 | `<Line>` 多系列（6 代表性系列） |
| Query 类型 | range query |
| PromQL（6 条） | 见下表；仅在 `> 0` 时有数据点（避免常态 0 值占满图表） |
| x 轴 | 时间 |
| y 轴 | 整数（5 分钟内增量） |
| 系列字段 | 手动赋名（前端注入） |
| 颜色 | 全部用错误红系（`#ff4d4f` / `#ff7a45` / `#fa541c` 等变体）或 series 调色板 |
| col-span | 12 |

**P22 PromQL 明细（从原始 20 条精选最具操作价值的 6 条）：**

| seriesLabel | PromQL |
|---|---|
| `conn_failed` | `sum(increase(net_conntrack_dialer_conn_failed_total{instance=~"$instance"}[5m])) > 0` |
| `rule_eval_failed` | `sum(increase(prometheus_rule_evaluation_failures_total{instance=~"$instance"}[5m])) > 0` |
| `scrape_sample_limit` | `sum(increase(prometheus_target_scrapes_exceeded_sample_limit_total{instance=~"$instance"}[5m])) > 0` |
| `tsdb_reload_failed` | `sum(increase(prometheus_tsdb_reloads_failures_total{instance=~"$instance"}[5m])) > 0` |
| `tsdb_compaction_failed` | `sum(increase(prometheus_tsdb_compactions_failed_total{instance=~"$instance"}[5m])) > 0` |
| `sample_out_of_order` | `sum(increase(prometheus_target_scrapes_sample_out_of_order_total{instance=~"$instance"}[5m])) > 0` |

#### P23 Notifications Sent

| 属性 | 值 |
|---|---|
| 标题 | Notifications Sent |
| 图表类型 | `<Line>` 单系列 |
| Query 类型 | range query |
| PromQL | `rate(prometheus_notifications_sent_total{instance=~"$instance"}[5m])` |
| x 轴 | 时间 |
| y 轴 | `notif/s`，保留 2 位小数 |
| 系列颜色 | `#722ed1`（紫色，区分通知类型） |
| col-span | 6 |

#### P24 Minutes Since Config Reload

| 属性 | 值 |
|---|---|
| 标题 | Minutes Since Successful Config Reload |
| 图表类型 | `<Line>` 多系列 |
| Query 类型 | range query |
| PromQL | `(time() - prometheus_config_last_reload_success_timestamp_seconds{job=~"$job",instance=~"$instance"}) / 60` |
| x 轴 | 时间 |
| y 轴 | 分钟（`min`），保留 1 位小数 |
| 系列字段 | `instance` |
| 说明 | 值越小表示最近一次 reload 越新鲜；若 reload 失败则值持续增大 |
| col-span | 6 |

---

### 5.9 面板裁剪说明（30 → 24）

从 `panel-catalog/Prometheus.json`（30 个面板）裁剪 6 个，保留 24 个。另有 2 个面板「清理」(保留但内容精简)：

#### 直接剔除的 6 个面板

| 原面板标题 | 裁剪原因 |
|---|---|
| **Skipped Iterations [$interval]**（singlestat） | 被时序面板 P19 Rule Evaluator Iterations 完全覆盖（`missed`/`skipped` 均有时序曲线），单值 stat 信息密度更低 |
| **Tardy Scrapes [$interval]**（singlestat） | PromQL 与 P06 Skipped Scrapes 完全重叠（均基于 `prometheus_target_scrapes_exceeded_sample_limit_total`），逻辑冗余 |
| **HTTP Request Duration**（graph） | 基于 `http_request_duration_microseconds_count`，该指标在 Prometheus 3.x 已移除，重命名为 `prometheus_http_request_duration_seconds`；使用旧指标在 3.12.0 中返回空结果 |
| **Successful Config Reload**（graph，0/1 二值） | 信息密度极低；P24 "Minutes Since Config Reload" 已完全覆盖其语义：reload 成功时间戳越近则值越小，reload 失败则值持续增大，语义更丰富 |
| **Target Scrapes / 5m**（graph） | `sum(rate(prometheus_target_interval_length_seconds_count[5m])) by (interval)` 统计的是抓取触发频次，与 P11 Scrape Sync Total 表达的信号高度重叠；后者 `by (scrape_job)` 维度更具操作性 |
| **Scrape Duration（第二个）**（graph，基于 `prometheus_target_interval_length_seconds`） | 标题与 P09 重名，但 metric 含义不同（此处反映的是配置的抓取间隔长度，是准静态值，非实际耗时）；与 P09（真实抓取耗时）混用容易误读 |

#### 清理但保留的 2 个面板

| 面板 | 清理内容 |
|---|---|
| **P17 Go Memory Usage** | 原始 18 个 `go_memstats_*` 系列 → 精选 6 个关键系列（heap_alloc / heap_sys / heap_inuse / heap_idle / stack_inuse / sys）；去掉原标题中的 `(FIXME)` |
| **P22 Failures and Errors** | 原始 20 条 PromQL → 精选 6 条代表性错误类型（conntrack / rule_eval / scrape_limit / tsdb_reload / tsdb_compaction / sample_order）；其余 14 条覆盖 azure/consul/ec2/gce 等云服务发现，与 datasophon 本地部署场景无关 |

---

## 6. 主题 / 样式规范

本项目使用 Ant Design 5 + AntV G2 v5（`@ant-design/plots` v2.x）。

### 6.1 颜色 Token

```ts
const CHART_COLORS = {
  primary:   '#1677ff',  // Ant Design 默认蓝
  success:   '#52c41a',  // 绿（正常/healthy）
  warning:   '#faad14',  // 黄（告警）
  error:     '#ff4d4f',  // 红（危险）
  // 多系列配色（循环使用）
  series: ['#1677ff', '#52c41a', '#faad14', '#ff4d4f', '#722ed1', '#eb2f96', '#13c2c2', '#fa8c16'],
};
```

### 6.2 阈值染色函数（含 reverse 方向支持）

```ts
// 相对 APISIX 新增 opts.reverse 参数
function colorByThreshold(
  value: number,
  thresholds: [number, number],  // [warnThreshold, critThreshold]
  opts?: { reverse?: boolean }
): string {
  const [t1, t2] = thresholds;
  if (opts?.reverse) {
    // 值越高越好（如 Uptime）
    // value >= t2 → success(绿)；t1 <= value < t2 → warning(黄)；value < t1 → error(红)
    if (value >= t2) return CHART_COLORS.success;
    if (value >= t1) return CHART_COLORS.warning;
    return CHART_COLORS.error;
  }
  // 值越低越好（默认，如 Failures/Latency）
  // value < t1 → success(绿)；t1 <= value < t2 → warning(黄)；value >= t2 → error(红)
  if (value < t1) return CHART_COLORS.success;
  if (value < t2) return CHART_COLORS.warning;
  return CHART_COLORS.error;
}
```

**使用示例：**
```ts
// P01 Uptime（reverse）
colorByThreshold(99.8, [90, 99], { reverse: true })   // → '#52c41a'（绿）
colorByThreshold(94.2, [90, 99], { reverse: true })   // → '#faad14'（黄）

// P04 Reload Failures（默认方向）
colorByThreshold(0, [1, 10])    // → '#52c41a'（绿）
colorByThreshold(3, [1, 10])    // → '#faad14'（黄）
colorByThreshold(12, [1, 10])   // → '#ff4d4f'（红）
```

### 6.3 图表公共配置（G2 v5 语法）

```ts
// 所有时序图的 x 轴配置（G2 v5：用 axis.x 而非 xAxis）
const TIME_AXIS_CONFIG = {
  x: {
    labelFormatter: (v: number) => dayjs(v).format('HH:mm'),
    tickCount: 5,
  },
};

// bytes 轴格式化
function formatBytes(bytes: number): string {
  if (bytes >= 1e9) return `${(bytes / 1e9).toFixed(2)} GB`;
  if (bytes >= 1e6) return `${(bytes / 1e6).toFixed(2)} MB`;
  if (bytes >= 1e3) return `${(bytes / 1e3).toFixed(2)} KB`;
  return `${bytes.toFixed(0)} B`;
}
```

### 6.4 Tooltip 格式（G2 v5 语法）

```ts
// 延迟类 tooltip（ms）
const msTooltip = {
  title: (d: TimeSeriesPoint) => dayjs(d.time).format('HH:mm:ss'),
  items: [
    (d: TimeSeriesPoint) => ({
      name: d.series,
      value: `${d.value.toFixed(1)} ms`,
    }),
  ],
};

// 内存类 tooltip（bytes）
const memoryTooltip = {
  title: (d: TimeSeriesPoint) => dayjs(d.time).format('HH:mm:ss'),
  items: [
    (d: TimeSeriesPoint) => ({
      name: d.series,
      value: formatBytes(d.value),
    }),
  ],
};
```

---

## 7. 数据层接口 TypeScript 定义

```ts
// Prometheus instant query 响应（复用 APISIX spec）
interface PrometheusVector {
  resultType: 'vector';
  result: Array<{
    metric: Record<string, string>;
    value: [number, string]; // [timestamp, value]
  }>;
}

// Prometheus range query 响应（复用 APISIX spec）
interface PrometheusMatrix {
  resultType: 'matrix';
  result: Array<{
    metric: Record<string, string>;
    values: Array<[number, string]>; // [[timestamp, value], ...]
  }>;
}

// 前端看板查询参数（Prometheus 自监控版，3 个变量）
interface DashboardQueryParams {
  clusterId: number;
  start: number;    // unix timestamp (seconds)
  end: number;      // unix timestamp (seconds)
  step: number;     // 步长 (seconds)，建议 = (end - start) / 200
  variables: {
    instance: string;   // 正则，如 ".+" 或 "localhost:9090"
    job: string;        // 正则，如 ".+" 或 "prometheus"
    interval: string;   // 字面量，如 "5m"（无括号，替换时由函数拼 [...] ）
  };
}

// 时序面板数据点（复用 APISIX spec）
interface TimeSeriesPoint {
  time: number;   // timestamp ms（供 G2 时间轴使用）
  value: number;
  series: string; // legend 标签
}

// Statistic 面板数据（复用 APISIX spec，新增 reverse 字段）
interface StatValue {
  value: number;
  color: string;    // 经阈值计算后的颜色（含 reverse 方向）
  label?: string;   // 可选描述文字
  suffix?: string;  // 单位后缀，如 "%"
}

// Table 面板数据（新增，用于 P07 Currently Down）
interface TableRow {
  instance: string;
  job: string;
  value: number;  // 0 = down（通过 up < 1 过滤，正常不会出现 1）
  key: string;    // antd Table 需要的唯一 key，用 `${instance}-${job}`
}
```

---

## 8. 组件树结构

```
<PrometheusDashboard>               # 页面容器，管理 variables + time range + refresh
  ├── <DashboardToolbar>            # 工具栏：实例▼ Job▼ Interval▼ + 时间选择 + 刷新
  ├── <Row R1>                      # 概览统计行
  │   ├── <StatPanel id="P01">      # Uptime（reverse 阈值）
  │   ├── <StatPanel id="P02">      # Total Series
  │   ├── <StatPanel id="P03">      # Memory Chunks
  │   ├── <StatPanel id="P04">      # Reload Failures
  │   ├── <StatPanel id="P05">      # Missed Iterations
  │   └── <StatPanel id="P06">      # Skipped Scrapes
  ├── <Row R2>                      # 目标健康行
  │   ├── <TablePanel id="P07">     # Currently Down ← 新增组件类型
  │   └── <AreaPanel id="P08">      # Upness（stacked by instance）
  ├── <Row R3>                      # 抓取延迟行
  │   ├── <TimeSeriesPanel id="P09"> # Scrape Duration
  │   └── <TimeSeriesPanel id="P10"> # Target Sync (ms)
  ├── <Row R4>                      # 抓取池 & 拒绝行
  │   ├── <TimeSeriesPanel id="P11"> # Scrape Sync Total
  │   └── <TimeSeriesPanel id="P12"> # Rejected Scrapes（4 系列）
  ├── <Row R5>                      # TSDB Series 行
  │   ├── <TimeSeriesPanel id="P13"> # Series Count
  │   ├── <TimeSeriesPanel id="P14"> # Series Created / Removed
  │   └── <TimeSeriesPanel id="P15"> # Appended Samples/s
  ├── <Row R6>                      # 存储 & Go 运行时行
  │   ├── <TimeSeriesPanel id="P16"> # Storage Memory Chunks
  │   ├── <AreaPanel id="P17">       # Go Memory Usage（6 series bytes）
  │   └── <TimeSeriesPanel id="P18"> # GC Rate / 2m
  ├── <Row R7>                      # 规则评估 & 查询行
  │   ├── <TimeSeriesPanel id="P19"> # Rule Evaluator Iterations（3 系列）
  │   ├── <TimeSeriesPanel id="P20"> # Avg Rule Eval Duration（ms，警戒线 100ms）
  │   └── <TimeSeriesPanel id="P21"> # Engine Query Duration by slice
  └── <Row R8>                      # 错误 & 配置 & 通知行
      ├── <TimeSeriesPanel id="P22"> # Failures and Errors（6 系列）
      ├── <TimeSeriesPanel id="P23"> # Notifications Sent
      └── <TimeSeriesPanel id="P24"> # Minutes Since Config Reload

# 可复用基础组件（按职责抽取）
<StatPanel>          # instant query → <Statistic>（含 reverse 阈值支持）
<TablePanel>         # instant query → antd <Table>（新增，用于 Currently Down）
<TimeSeriesPanel>    # range query → <Line> (multi-series)
<AreaPanel>          # range query → <Area> (stacked 或普通 area)
<DashboardToolbar>   # 实例▼ + Job▼ + Interval▼ + 时间范围 + 刷新
<usePrometheusQuery> # custom hook：封装 range/instant 请求 + 轮询
```

---

## 9. 实现说明（供 Phase 3 编码参考）

### 9.1 文件路径

**当前实现（datasophon-ui-v2，mock 阶段）**

```
datasophon-ui-v2/src/pages/PrometheusMonitor/
  ├── index.tsx                   # 主页面（路由 /prometheus-monitor，临时一级路由）
  ├── panels/
  │   ├── StatPanel.tsx           # 复用 ApisixMonitor 同名组件（新增 reverse prop）
  │   ├── TablePanel.tsx          # 新增（Currently Down 专用）
  │   ├── TimeSeriesPanel.tsx     # 复用 ApisixMonitor 同名组件
  │   └── AreaPanel.tsx           # 复用 ApisixMonitor 同名组件
  ├── toolbar/
  │   └── DashboardToolbar.tsx    # 复用并调整（3 变量：实例/Job/Interval）
  ├── mock/
  │   └── prometheusMockData.ts   # 确定性伪随机静态数据（Math.sin 种子）
  └── utils/
      ├── formatters.ts           # formatBytes、colorByThreshold（新增 reverse）
      └── promql.ts               # 变量替换（Phase 3 接入真实数据时使用）
```

**Phase 3 目标路径（迁移后）**

```
datasophon-ui/src/pages/ServiceManage/Instance/Overview/
  ├── PrometheusDashboard.tsx
  ├── panels/ toolbar/ hooks/ utils/   # 同上，新增 usePrometheusQuery hook
```

### 9.2 PromQL 变量替换规则

```ts
function replaceVars(
  promql: string,
  vars: DashboardQueryParams['variables'],
  params: Pick<DashboardQueryParams, 'start' | 'end'>
): string {
  return promql
    .replace(/\$instance/g, vars.instance || '.+')
    .replace(/\$job/g,      vars.job      || '.+')
    .replace(/\$interval/g, vars.interval || '5m');
    // 注意：无 $__rate_interval，Prometheus 自监控看板使用固定窗口或 $interval
}
```

> **与 APISIX 的差异**：不需要 `calcRateInterval()`，`$interval` 直接用用户选择的值替换。

### 9.3 多 PromQL 面板合并为多系列（P12 / P14 / P19 / P22）

```ts
// 以 P14 Series Created/Removed 为例（2 条 PromQL → 2 条 series）
const SERIES_QUERIES = [
  { label: 'created', promql: 'sum(increase(prometheus_tsdb_head_series_created_total{instance=~"$instance"}[5m]))' },
  { label: 'removed', promql: 'sum(increase(prometheus_tsdb_head_series_removed_total{instance=~"$instance"}[5m]))' },
];

// 并行执行，合并结果
async function fetchMultiSeries(queries: typeof SERIES_QUERIES, params: DashboardQueryParams) {
  const results = await Promise.all(
    queries.map(({ label, promql }) =>
      fetchRangeQuery(replaceVars(promql, params.variables, params), params).then(
        (matrix) => matrixToSeries(matrix, '__name__').map((pt) => ({ ...pt, series: label }))
      )
    )
  );
  return results.flat();
}
```

### 9.4 TablePanel 数据转换（vector → TableRow[]）

```ts
function vectorToTableRows(vector: PrometheusVector): TableRow[] {
  return vector.result.map((item) => ({
    instance: item.metric.instance ?? '',
    job:      item.metric.job      ?? '',
    value:    parseFloat(item.value[1]),
    key:      `${item.metric.instance}-${item.metric.job}`,
  }));
}
```

### 9.5 Mock 数据要求

`prometheusMockData.ts` 需覆盖全部 24 个面板，包含：

**Instant query 返回值（P01-P07）：**
- P01 Uptime：`99.8`（%，健康状态）
- P02 Total Series：`125_000`
- P03 Memory Chunks：`45_600`
- P04 Reload Failures：`0`（健康）
- P05 Missed Iterations：`0`（健康）
- P06 Skipped Scrapes：`2`（轻微黄色）
- P07 Currently Down：空数组 `[]`（全部 up）

**Range query 时序数据（P08-P24）：**
- 时间跨度：最近 1 小时，步长 30s，共约 120 个点
- P08 Upness：值固定为 1（0 = 正常；仅偶发一两个点降为 0 以验证堆叠效果）
- P09 Scrape Duration：0.001–0.010s，随机波动
- P15 Appended Samples/s：100–500 samples/s，带随机波动
- P17 Go Memory：heap_alloc ~50MB，heap_sys ~200MB，sys ~300MB，带缓慢上升趋势
- P19 Rule Evaluator：total 约 20/s，missed 和 skipped 趋近 0
- P20 Avg Rule Eval Duration：5–20ms，偶有突刺至 50ms
- P22 Failures and Errors：全部为 0（偶发模拟一个 rule_eval_failed 突刺验证颜色）

### 9.6 刷新轮询（usePrometheusQuery hook 基本结构）

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

与 ApisixMonitor 完全一致，以下三项缺一不可：

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

**原因**：UMI `base: '/ddh'` 让所有 React 路由都带 `/ddh` 前缀，proxy 规则会把页面导航请求（`GET /ddh/prometheus-monitor`）也转发到后端，返回 504。`bypass` 让 `text/html` 请求由 dev server 自己处理。

### 10.3 mock 路径必须与 baseURL 对齐

```ts
// src/app.tsx
export const request: RequestConfig = {
  baseURL: '/ddh/api/v2',   // 所有请求会加上这个前缀
  ...
};
```

```ts
// mock/prometheus.ts — 路径必须写完整
'GET /ddh/api/v2/prometheus/query':       (_req, res) => { ... },
'GET /ddh/api/v2/prometheus/query_range': (_req, res) => { ... },
```

**原因**：UMI mock 按路径精确匹配，`baseURL` 是 axios 运行时拼接的，mock 文件里写 `/api/prometheus/query` 不会匹配实际请求路径 `/ddh/api/v2/prometheus/query`，请求会穿透到 proxy → 504。

---

## 11. 验收标准

Phase 2 原型（mock 阶段）完成后，需满足：

- [ ] 全部 24 个面板（P01-P24）有对应的 React 组件 mock 展示
- [ ] Stat 面板（P01-P06）使用 antd `<Statistic>` + 阈值染色；P01 使用 `reverse` 方向
- [ ] Table 面板（P07）使用 antd `<Table>` 渲染，空态显示绿色 "All targets are up ✅"
- [ ] 时序面板（P08-P24）使用 `@ant-design/plots` `<Line>` 或 `<Area>` 渲染
- [ ] 工具栏含：实例多选下拉 + Job 多选下拉 + **Interval 单选下拉**（1m/5m/15m）+ 时间范围快捷选择 + 刷新间隔
- [ ] 布局与第 4 节 ASCII 图吻合（8 行 24 列 Grid，各行高度正确）
- [ ] `colorByThreshold` 支持 `{ reverse: true }`，P01 Uptime 染色方向正确
- [ ] 颜色方案遵循第 6.1 节 Token（primary 蓝、success 绿、warning 黄、error 红）
- [ ] TypeScript 接口与第 7 节定义一致，新增 `TableRow` 类型
- [ ] 响应式：在 1280px 宽度下不出现横向滚动条
