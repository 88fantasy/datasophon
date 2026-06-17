# Doris 监控看板原型设计 Spec

> **文档用途**：供 Claude design 阅读，根据本 spec 设计 React + AntV G2 看板原型，并最终生成可运行的组件代码。  
> **组件**：Apache Doris 4.0.5  
> **数据源**：原生 `/metrics`（FE `:18030` / BE `:18040`，**DataSophon 默认端口，非官方 8030/8040**）  
> **参考看板**：Doris Overview（Grafana ID 9734，revision 5，适配 1.2.x；4.0.5 高风险项已标注 ⚠️）  
> **Panel Catalog 路径**：`docs/monitoring/panel-catalog/Doris.json`（84 个原始面板，精选 32 个）  
> **Phase**：Phase 2 —— 原型设计阶段

---

## 1. 架构约束

```
React(AntV G2)
  └──HTTP──> datasophon-api /ddh/api/v2/prometheus/query_range
               └──PromQL──> Prometheus :9090
                              ├──scrape──> DorisFE  :18030/metrics
                              └──scrape──> DorisBE  :18040/metrics
```

**前端不直连 Prometheus**。所有 PromQL 通过后端代理端点转发（与 Prometheus 自监控完全相同的代理路径）。

后端代理两个端点：

| 端点 | 用途 | 关键参数 |
|---|---|---|
| `GET /ddh/api/v2/prometheus/query` | 即时查询（instant） | `query`, `time?`, `clusterId?` |
| `GET /ddh/api/v2/prometheus/query_range` | 范围查询（range） | `query`, `start`, `end`, `step`, `clusterId?` |

响应格式：`ApiResponse<{resultType, result}>` —— 成功时 `data` 字段即 Prometheus 原生 `data` 对象。

> ⚠️ **PromQL 中 `+`、`/`、`*` 等运算符**必须由后端 `URLEncoder.encode` 正确编码（`+` → `%2B`），否则 Go 的 `net/url.ParseQuery` 会将其解码为空格，导致 PromQL 语法错误。本看板 FE JVM Heap 等面板含大量运算符，Phase 3 联调时须重点验证。

### 1.1 多角色数据源

Doris 双角色均暴露原生 Prometheus 文本格式端点（无需 exporter）：

| 角色 | DataSophon 默认端口 | 官方默认端口 | path |
|---|---|---|---|
| DorisFE | **18030** | 8030 | `/metrics` |
| DorisBE | **18040** | 8040 | `/metrics` |

> Prometheus 抓取配置须使用 18030/18040，service_ddl.json 已验证。无 Broker 角色。

### 1.2 ⚠️ 标签依赖与抓取配置前置要求

Grafana 9734 看板的所有 PromQL **依赖抓取端 relabel 注入**，这些不是 Doris 原生标签，必须由 Prometheus scrape_config 的 `relabel_configs` 手动注入：

| 标签 | 注入方式 | 用途 |
|---|---|---|
| `group="fe"` | relabel，DorisFE job 静态注入 | 区分 FE/BE 面板（全面板依赖） |
| `group="be"` | relabel，DorisBE job 静态注入 | 区分 FE/BE 面板（全面板依赖） |
| `job` | Prometheus scrape job name（=集群名） | `$cluster` 变量过滤 |
| `node_info{type="is_master"}` | Doris FE 原生指标（用于 FE Master 变量） | 识别当前 FE Master |

**Phase 3 联调前置**：必须在 Prometheus scrape 配置中完成上述 relabel；否则依赖 `group` 标签的所有面板将显示空图。Phase 2 mock 数据中须在 vector 结果里带上 `group` 和 `job` 标签。

### 1.3 三段式布局与变量作用域

本看板采用**单页三纵段**布局：

- **段 A — 集群概览**：跨 FE/BE 汇总视图，使用 `$cluster`；stat 行 + 集群级趋势行。
- **段 B — FE（Frontend）**：查询统计、Compaction、JVM、Transaction 等 FE 核心指标。使用 `$cluster` + `$fe_instance`。
- **段 C — BE（Backend）**：CPU/内存、磁盘、Compaction 速率、读写吞吐量。使用 `$cluster` + `$be_instance`。

工具栏的 `$cluster` 下拉影响全部三段；`$fe_instance` 仅影响段 B；`$be_instance` 仅影响段 C。

---

## 2. 图表类型映射字典

**完全复用 `docs/monitoring/design/prometheus-dashboard-prototype-spec.md` §2 中的映射字典。**

以下为 Doris 特有的补充说明：

| Grafana chartType（catalog） | 映射组件 | 备注 |
|---|---|---|
| `graph` 单系列 | `<Line>` 单系列 | 9734 大量使用（71/84 面板） |
| `graph` 多系列（多 target） | `<Line>` multi-range 合并多系列 | e.g. Disk Usage 双比率曲线 |
| `stat` | `<Statistic>` (antd) + 阈值染色 | FE/BE 节点数、容量等 6 个汇总值 |
| `table-old` | **全部跳过** | Jobs 的 table-old（6 面板）不在原型范围；表格渲染复杂度高、业务数据非实时 |
| heatmap | **全部跳过** | 9734 无 heatmap，留此说明兼容未来 |

---

## 3. 变量 / 过滤器规范

看板顶部工具栏包含以下变量：

| 变量 | PromQL 占位符 | 取值来源 | 默认值 | 适用范围 |
|---|---|---|---|---|
| 集群 | `$cluster` | `label_values(up{group="fe"}, job)` | 第一个可用值 | 全部三段 |
| FE 实例 | `$fe_instance` | `label_values(up{group="fe", job="$cluster"}, instance)` | `.+`（全选） | 段 B FE 面板 |
| BE 实例 | `$be_instance` | `label_values(up{group="be", job="$cluster"}, instance)` | `.+`（全选） | 段 C BE 面板 |
| Rate 窗口 | `$interval` | 固定选项 `1m,2m,5m,10m` | `2m` | 所有 rate 面板 |
| 时间范围 | — | 时间选择器 | `Last 1h` | 所有面板 |
| 刷新间隔 | — | — | `30s` | 所有面板 |

> **注**：9734 原版含 `$fe_master`（从 `node_info{type="is_master"}` 动态解析 FE Master 实例）。原型阶段该变量不在工具栏暴露，相关面板（Scheduling Tablets、BDBJE Write）PromQL 中的 `instance="$fe_master"` 在 mock 阶段以固定 instance 标签代替；Phase 3 联调时再实现动态解析。

---

## 4. 看板布局（24 列 Grid）

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  TOOLBAR: [集群▼ prod-doris]  [FE实例▼ .+]  [BE实例▼ .+]  [Rate▼ 2m]      │
│           [Last 1h▼]   [🔄 30s▼]                                            │
└──────────────────────────────────────────────────────────────────────────────┘

══════════════════ SEGMENT A — 集群概览  ($cluster) ══════════════════

行 A-R1 — 节点 & 容量 Stat（高度 80px）
┌──────────┬──────────┬──────────┬──────────┬──────────────────┬──────────────────┐
│ FE Node  │ FE Alive │ BE Node  │ BE Alive │ Used Capacity    │ Total Capacity   │
│ [Stat]   │ [Stat]   │ [Stat]   │ [Stat]   │ [Stat: bytes]    │ [Stat: bytes]    │
│ col=4    │ col=4    │ col=4    │ col=4    │ col=4            │ col=4            │
└──────────┴──────────┴──────────┴──────────┴──────────────────┴──────────────────┘

行 A-R2 — 集群级趋势（高度 200px）
┌──────────────────────────┬──────────────────────────┬──────────────────────────┐
│ Cluster QPS              │ FE JVM Heap %            │ BE CPU Idle              │
│ [Line: ops/s]            │ [Line: %, per instance]  │ [Line: %, per instance]  │
│ col-span=8               │ col-span=8               │ col-span=8               │
└──────────────────────────┴──────────────────────────┴──────────────────────────┘

══════════════════ SEGMENT B — FE ($cluster + $fe_instance) ══════════════════

行 B-R1 — 查询吞吐（高度 200px）
┌──────────────────────────────────────┬─────────────────────────────────────┐
│ RPS                                  │ QPS                                 │
│ [Line: request/s]                    │ [Line: query/s]                     │
│ col-span=12                          │ col-span=12                         │
└──────────────────────────────────────┴─────────────────────────────────────┘

行 B-R2 — 延迟（高度 200px）
┌──────────────────────────────────────┬─────────────────────────────────────┐
│ 99th Latency                         │ Query Percentile (0.50/0.75/0.99)  │
│ [Line: ms, per FE instance] ⚠️       │ [Line: ms, 3 系列] ⚠️             │
│ col-span=12                          │ col-span=12                         │
└──────────────────────────────────────┴─────────────────────────────────────┘

行 B-R3 — 错误（高度 200px）
┌──────────────────────────────────────┬─────────────────────────────────────┐
│ Query Error Count [1m] ⚠️            │ Query Error Rate % ★新建            │
│ [Line: 计数/增量]                    │ [Line: %, 错误率比率]               │
│ col-span=12                          │ col-span=12                         │
└──────────────────────────────────────┴─────────────────────────────────────┘

行 B-R4 — FE 连接 & 压实（高度 200px）
┌──────────────────────────────────────┬─────────────────────────────────────┐
│ Connections                          │ FE Compaction Score ⚠️             │
│ [Line: 连接数]                       │ [Line: compaction score]            │
│ col-span=12                          │ col-span=12                         │
└──────────────────────────────────────┴─────────────────────────────────────┘

行 B-R5 — FE 元数据（高度 200px）
┌──────────────────────────────────────┬─────────────────────────────────────┐
│ Scheduling Tablets                   │ BDBJE Write Latency ⚠️             │
│ [Line: tablet 数]                    │ [Line: ms (99th) + write rate]     │
│ col-span=12                          │ col-span=12                         │
└──────────────────────────────────────┴─────────────────────────────────────┘

行 B-R6 — FE JVM（高度 200px）
┌──────────────────────────────────────┬─────────────────────────────────────┐
│ JVM Heap ⚠️                          │ JVM Old GC ⚠️                      │
│ [Area: used/max bytes, 2 系列]       │ [Line: count + avg_time, 2 系列]   │
│ col-span=12                          │ col-span=12                         │
└──────────────────────────────────────┴─────────────────────────────────────┘

══════════════════ SEGMENT C — BE ($cluster + $be_instance) ══════════════════

行 C-R1 — BE 资源（高度 200px）
┌──────────────────┬──────────────────┬──────────────────┐
│ BE CPU Idle      │ BE Mem           │ Disk Usage       │
│ [Line: %]        │ [Line: bytes]    │ [Line: %, by path│
│ col-span=8       │ col-span=8       │ col-span=8]      │
└──────────────────┴──────────────────┴──────────────────┘

行 C-R2 — Compaction & IO（高度 200px）
┌──────────────────────────────────────┬─────────────────────────────────────┐
│ BE Compaction (Base + Cumulate)      │ Disk IO Util                       │
│ [Line: Bps, 2 系列]                  │ [Line: %]                          │
│ col-span=12                          │ col-span=12                         │
└──────────────────────────────────────┴─────────────────────────────────────┘

行 C-R3 — 读吞吐（高度 200px）
┌──────────────────────────────────────┬─────────────────────────────────────┐
│ BE Scan Bytes                        │ BE Scan Rows                       │
│ [Line: Bps]                          │ [Line: rows/s]                     │
│ col-span=12                          │ col-span=12                         │
└──────────────────────────────────────┴─────────────────────────────────────┘

行 C-R4 — 写吞吐 & BE 延迟（高度 200px）
┌──────────────────────────────────────┬─────────────────────────────────────┐
│ BE Push Bytes                        │ BE Push Rows                       │
│ [Line: Bps]                          │ [Line: rows/s]                     │
│ col-span=12                          │ col-span=12                         │
└──────────────────────────────────────┴─────────────────────────────────────┘

行 C-R5 — BE 延迟 & 网络（高度 200px）
┌──────────────────────────────────────┬─────────────────────────────────────┐
│ BE Engine Request Latency ★新建      │ Net send/recv Bytes                │
│ [Line: ms，BE 引擎操作延迟]          │ [Line: Bps, 2 系列]                │
│ col-span=12                          │ col-span=12                         │
└──────────────────────────────────────┴─────────────────────────────────────┘
```

---

## 5. 面板规格（逐面板）

### 5.0 裁剪说明（84 → 32）

**从 `panel-catalog/Doris.json`（84 面板）裁剪至 32 个。**

| 裁剪类别 | 代表面板 | 裁剪理由 |
|---|---|---|
| `table-old` 全部 | Broker/Insert/Routine/Spark Load Job、SC Job、Rollup Job（6 面板） | 前端不支持 table-old；含动态列、离线状态数据，超出原型范围 |
| Jobs 趋势图 | Broker/Insert/Routine/Spark load tendency（4 面板） | 业务级 ETL 监控，非黄金信号；`doris_fe_job{}` 指标 4.0.5 兼容性存疑 |
| BE tasks 细粒度计数 | Tablets Report/Single Tablet/Finish task report/Delete/Clone/Create rollup/Schema change/Create tablet（8 面板） | `doris_be_engine_requests_total{type=xxx}` 计数同构高；保留汇总级 BE Engine Latency 新建面板 |
| Image 操作 | Image Write/Push/Clean（3 面板） | 低频运维操作，非日常黄金信号 |
| Transaction 细分 | fe_txn_status、Publish Task on BE、Txn Load Bytes/Rows（3 面板） | 与段 B 的 Txn 面板重叠；Txn Load 在 BE Scan/Push 中已覆盖 |
| Cluster Number | Cluster Number（stat，依赖 `node_info`） | FE/BE Node 面板已替代，且 `node_info` 仅 FE Master 暴露 |
| 重复 Compaction Score | BE Compaction Score（`doris_fe_tablet_max_compaction_score`，与 FE Collect Compaction Score 逻辑重叠） | 保留命名更明确的 FE Collect Compaction Score 一个 |
| 深度 JVM 池 | JVM Young、JVM Non Heap（单独面板，已并入 JVM Heap 多系列参考） | 基础 Heap 面板已提供足够的 JVM 饱和度信号 |

**golden signals 四象限覆盖**：

| 维度 | 面板 |
|---|---|
| **Latency（延迟）** | B03 99th Latency、B04 Query Percentile（FE 查询）、C10 BE Engine Request Latency ★新建 |
| **Traffic（流量）** | A07 Cluster QPS、B01 RPS、B02 QPS、C06 BE Scan Bytes、C07 BE Scan Rows、C08 BE Push Bytes、C09 BE Push Rows |
| **Errors（错误）** | B05 Query Error Count、B06 Query Error Rate % ★新建 |
| **Saturation（饱和度）** | A08 FE JVM Heap%、A09 BE CPU Idle、B11 JVM Heap bytes、B12 JVM Old GC、C01 BE CPU Idle、C02 BE Mem、C03 Disk Usage、C04 Disk IO Util、C05 BE Compaction |

---

### 5.1 段 A — 集群概览

#### DO-A01 FE Node Count

| 属性 | 值 |
|---|---|
| 标题 | FE Node Count |
| 图表类型 | `<Statistic>` (antd) |
| Query 类型 | instant query |
| PromQL | `count(up{group="fe", job="$cluster"})` |
| 单位 | 整数（台） |
| 阈值规则 | 无（蓝色 `#1677ff`） |
| col-span | 4 |

#### DO-A02 FE Alive

| 属性 | 值 |
|---|---|
| 标题 | FE Alive |
| 图表类型 | `<Statistic>` (antd) |
| Query 类型 | instant query |
| PromQL | `count(up{group="fe", job="$cluster"} == 1)` |
| 单位 | 整数（台） |
| 阈值规则 | reverse（越高越好）：`== FE Node` → 绿；`< FE Node` → 红（实现时与 A01 值比较） |
| col-span | 4 |

#### DO-A03 BE Node Count

| 属性 | 值 |
|---|---|
| 标题 | BE Node Count |
| 图表类型 | `<Statistic>` (antd) |
| Query 类型 | instant query |
| PromQL | `count(up{group="be", job="$cluster"})` |
| 单位 | 整数（台） |
| 阈值规则 | 无（蓝色 `#1677ff`） |
| col-span | 4 |

#### DO-A04 BE Alive

| 属性 | 值 |
|---|---|
| 标题 | BE Alive |
| 图表类型 | `<Statistic>` (antd) |
| Query 类型 | instant query |
| PromQL | `count(up{group="be", job="$cluster"} == 1)` |
| 单位 | 整数（台） |
| 阈值规则 | reverse：`== BE Node` → 绿；`< BE Node` → 红 |
| col-span | 4 |

#### DO-A05 Used Disk Capacity

| 属性 | 值 |
|---|---|
| 标题 | Used Disk Capacity |
| 图表类型 | `<Statistic>` (antd) |
| Query 类型 | instant query |
| PromQL | `SUM(doris_be_disks_local_used_capacity{job="$cluster"})` |
| 单位 | bytes（`formatBytes`，自动换算 GB/TB） |
| 阈值规则 | 无（灰蓝色） |
| col-span | 4 |

#### DO-A06 Total Disk Capacity

| 属性 | 值 |
|---|---|
| 标题 | Total Disk Capacity |
| 图表类型 | `<Statistic>` (antd) |
| Query 类型 | instant query |
| PromQL | `SUM(doris_be_disks_total_capacity{job="$cluster"})` |
| 单位 | bytes（`formatBytes`） |
| 阈值规则 | 无（灰色） |
| col-span | 4 |

#### DO-A07 Cluster QPS

| 属性 | 值 |
|---|---|
| 标题 | Cluster QPS |
| 图表类型 | `<Line>` 单系列 |
| Query 类型 | range query |
| PromQL | `sum by (job)(rate(doris_fe_query_total{group="fe", job="$cluster"}[$interval]))` |
| y 轴 | `ops/s`，保留 2 位小数 |
| 说明 | **Traffic 集群级核心面板**；趋势与 B02 QPS 联看 |
| col-span | 8 |

#### DO-A08 FE JVM Heap %

| 属性 | 值 |
|---|---|
| 标题 | FE JVM Heap % (per instance) |
| 图表类型 | `<Line>` 多系列（by instance） |
| Query 类型 | range query |
| PromQL | `sum(jvm_heap_size_bytes{group="fe", job="$cluster", type="used"} * 100) by (instance) / sum(jvm_heap_size_bytes{group="fe", job="$cluster", type="max"}) by (instance)` |
| 系列字段 | `instance` |
| y 轴 | `%`，保留 1 位小数（`(v) => \`${v.toFixed(1)}%\``） |
| 阈值参考线 | y=70（黄色警告）、y=90（红色告警） |
| 说明 | **Saturation 集群 FE JVM 概览**；⚠️ 需在 4.0.5 `:18030/metrics` 验证 `jvm_heap_size_bytes` 指标名 |
| col-span | 8 |

#### DO-A09 BE CPU Idle

| 属性 | 值 |
|---|---|
| 标题 | BE CPU Idle % (per job) |
| 图表类型 | `<Line>` 单系列 |
| Query 类型 | range query |
| PromQL | `(sum(rate(doris_be_cpu{mode="idle", job="$cluster"}[$interval])) by (job)) / (sum(rate(doris_be_cpu{job="$cluster"}[$interval])) by (job)) * 100` |
| y 轴 | `%`，保留 1 位小数 |
| 阈值参考线 | y=20（红色虚线，Idle < 20% = CPU 饱和）—— **注意：越低越告警（reverse）** |
| 说明 | **Saturation 集群 BE CPU 概览**；Idle 接近 0 时表示 CPU 接近满载 |
| col-span | 8 |

---

### 5.2 段 B — FE（Frontend）

> 本段面板均使用 `job="$cluster"` 过滤，实例级面板额外用 `instance=~"$fe_instance"`。

#### DO-B01 RPS

| 属性 | 值 |
|---|---|
| 标题 | FE Request Rate (RPS) |
| 图表类型 | `<Line>` 多系列（by instance） |
| Query 类型 | range query |
| PromQL | `rate(doris_fe_request_total{job="$cluster", group="fe"}[$interval])` |
| 系列字段 | `instance` |
| y 轴 | `req/s`，保留 2 位小数 |
| 说明 | **Traffic — 所有 HTTP 请求速率**（包括查询、DDL、导入等） |
| col-span | 12 |

#### DO-B02 QPS

| 属性 | 值 |
|---|---|
| 标题 | FE Query Rate (QPS) |
| 图表类型 | `<Line>` 多系列（by instance） |
| Query 类型 | range query |
| PromQL | `rate(doris_fe_query_total{job="$cluster", group="fe"}[$interval])` |
| 系列字段 | `instance` |
| y 轴 | `query/s`，保留 2 位小数 |
| 说明 | **Traffic — 纯查询速率**；与 RPS 差值反映非查询请求占比 |
| col-span | 12 |

#### DO-B03 99th Latency

| 属性 | 值 |
|---|---|
| 标题 | FE Query 99th Latency |
| 图表类型 | `<Line>` 多系列（by instance） |
| Query 类型 | range query |
| PromQL | `sum(doris_fe_query_latency_ms{job="$cluster", quantile="0.99"}) by (instance)` |
| 系列字段 | `instance` |
| y 轴 | 毫秒（`ms`），保留 1 位小数 |
| 说明 | **Latency 核心面板**。⚠️ `doris_fe_query_latency_ms{quantile="0.99"}` 是 summary 形式；若 4.0.5 改为 histogram，需改用 `histogram_quantile(0.99, rate(doris_fe_query_latency_ms_bucket{...}[$interval]))`——需在 `:18030/metrics` 实测核验 |
| col-span | 12 |

#### DO-B04 Query Percentile

| 属性 | 值 |
|---|---|
| 标题 | Query Latency Percentile (p50/p75/p99) |
| 图表类型 | `<Line>` 多系列（3 系列） |
| Query 类型 | range query（multi-range） |
| PromQL 明细 | — |

| seriesLabel | PromQL |
|---|---|
| `p50` | `doris_fe_query_latency_ms{job="$cluster", instance=~"$fe_instance", quantile="0.5"}` |
| `p75` | `doris_fe_query_latency_ms{job="$cluster", instance=~"$fe_instance", quantile="0.75"}` |
| `p99` | `doris_fe_query_latency_ms{job="$cluster", instance=~"$fe_instance", quantile="0.99"}` |

| 属性 | 值 |
|---|---|
| 颜色 | `p50` → `#52c41a`（绿）、`p75` → `#faad14`（黄）、`p99` → `#ff4d4f`（红） |
| y 轴 | `ms`，保留 1 位小数 |
| 说明 | **Latency 三分位详情**；⚠️ 同 B03，需在 4.0.5 核验 summary quantile 是否仍有效 |
| col-span | 12 |

#### DO-B05 Query Error Count

| 属性 | 值 |
|---|---|
| 标题 | Query Error Count |
| 图表类型 | `<Line>` 多系列（by instance） |
| Query 类型 | range query（multi-range） |
| PromQL 明细 | — |

| seriesLabel | PromQL |
|---|---|
| `cumulative` | `doris_fe_query_err{job="$cluster", instance=~"$fe_instance"}` |
| `rate_1m` | `rate(doris_fe_query_err{job="$cluster", instance=~"$fe_instance"}[$interval])` |

| 属性 | 值 |
|---|---|
| 颜色 | `cumulative` → `#8c8c8c`（灰，背景参考）、`rate_1m` → `#ff4d4f`（红） |
| y 轴 | 整数 |
| 说明 | **Errors 计数**。⚠️ 9734 使用 `doris_fe_query_err`（无 `_total` 后缀）；若 4.0.5 已迁移至 `doris_fe_query_err_total`（Counter 规范），rate 面板会显示空，需将 PromQL 中的 `doris_fe_query_err` 替换为 `doris_fe_query_err_total`——必须在 `:18030/metrics` 实测核验 |
| col-span | 12 |

#### DO-B06 Query Error Rate % ★新建

| 属性 | 值 |
|---|---|
| 标题 | Query Error Rate % |
| 图表类型 | `<Line>` 单系列 |
| Query 类型 | range query |
| PromQL | `rate(doris_fe_query_err{job="$cluster", instance=~"$fe_instance"}[$interval]) / rate(doris_fe_query_total{job="$cluster", group="fe", instance=~"$fe_instance"}[$interval]) * 100` |
| 系列颜色 | `#ff4d4f`（红） |
| y 轴 | `%`，保留 2 位小数 |
| 阈值参考线 | y=1（1% 错误率告警线，红色虚线） |
| 说明 | **Errors 核心缺口面板——9734 中无、按选型要求新建。** 正常应趋近 0；突刺即告警。⚠️ 分子同 B05，若 `doris_fe_query_err` 指标名已变，需同步更新 |
| col-span | 12 |

#### DO-B07 Connections

| 属性 | 值 |
|---|---|
| 标题 | FE Connections |
| 图表类型 | `<Line>` 多系列（by instance） |
| Query 类型 | range query |
| PromQL | `doris_fe_connection_total{job="$cluster", instance=~"$fe_instance"}` |
| 系列字段 | `instance` |
| y 轴 | 整数（连接数） |
| 说明 | **Traffic — 并发连接数**；连接堆积表示后端处理能力不足 |
| col-span | 12 |

#### DO-B08 FE Compaction Score

| 属性 | 值 |
|---|---|
| 标题 | FE Tablet Max Compaction Score |
| 图表类型 | `<Line>` 多系列（by instance） |
| Query 类型 | range query |
| PromQL | `doris_fe_max_tablet_compaction_score{job="$cluster", instance=~"$fe_instance"}` |
| 系列字段 | `instance` |
| y 轴 | 整数（score） |
| 阈值参考线 | y=100（score 高表示 Compaction 压力大，数值因版本不同而异） |
| 说明 | **Saturation — Compaction 积压程度**。⚠️ 9734 同时存在 `doris_fe_max_tablet_compaction_score` 与 `doris_fe_tablet_max_compaction_score` 两个命名，两者疑似历史遗留双命名——需在 4.0.5 `:18030/metrics` 确认实际指标名，使用存在的那个 |
| col-span | 12 |

#### DO-B09 Scheduling Tablets

| 属性 | 值 |
|---|---|
| 标题 | Scheduling Tablets |
| 图表类型 | `<Line>` 单系列 |
| Query 类型 | range query |
| PromQL | `doris_fe_scheduled_tablet_num{job="$cluster"}` |
| y 轴 | 整数（tablet 数） |
| 说明 | **Saturation — FE 调度待处理 tablet 数**；持续上升表示调度能力不足 |
| col-span | 12 |

#### DO-B10 JVM Heap

| 属性 | 值 |
|---|---|
| 标题 | FE JVM Heap Memory |
| 图表类型 | `<Area>` 多系列（2 系列，非堆叠） |
| Query 类型 | range query（multi-range） |
| PromQL 明细 | — |

| seriesLabel | PromQL |
|---|---|
| `used` | `jvm_heap_size_bytes{job="$cluster", instance=~"$fe_instance", type="used"}` |
| `max` | `jvm_heap_size_bytes{job="$cluster", instance=~"$fe_instance", type="max"}` |

| 属性 | 值 |
|---|---|
| 颜色 | `used` → `#1677ff`（蓝）、`max` → `#8c8c8c`（灰参考线） |
| y 轴 | bytes（`formatBytes`，自动换算 MB/GB） |
| 说明 | **Saturation — FE JVM Heap**；`used` 接近 `max` 时需关注 GC 压力。⚠️ `jvm_heap_size_bytes{type="used"/"max"}` 为 9734 沿用指标名；需在 4.0.5 `:18030/metrics` 核验（较新版本可能已迁移至 `jvm_memory_used_bytes{area="heap"}`）|
| col-span | 12 |

#### DO-B11 JVM Old GC

| 属性 | 值 |
|---|---|
| 标题 | FE JVM Old GC |
| 图表类型 | `<Line>` 多系列（2 系列） |
| Query 类型 | range query（multi-range） |
| PromQL 明细 | — |

| seriesLabel | PromQL |
|---|---|
| `gc_count` | `jvm_old_gc{job="$cluster", instance=~"$fe_instance", type="count"}` |
| `avg_time_ms` | `sum(jvm_old_gc{job="$cluster", instance=~"$fe_instance", type="time"}) / sum(jvm_old_gc{job="$cluster", instance=~"$fe_instance", type="count"})` |

| 属性 | 值 |
|---|---|
| 颜色 | `gc_count` → `#8c8c8c`（灰）、`avg_time_ms` → `#ff4d4f`（红） |
| y 轴 | 双轴（gc_count 整数，avg_time_ms ms）— 实现时可使用两个独立 y 轴或归一化 |
| 说明 | **Saturation — Old GC 频率与耗时**；Old GC avg_time 突增 + CPU 高 = FE JVM 内存压力。⚠️ `jvm_old_gc{type="count"/"time"}` 为 1.2.x 时代指标名；4.0.5 可能已迁移至 `jvm_gc_pause_seconds_count/sum{action="end of major GC"}`——必须核验 |
| col-span | 12 |

#### DO-B12 BDBJE Write Latency

| 属性 | 值 |
|---|---|
| 标题 | FE Edit Log Write Latency (p99) |
| 图表类型 | `<Line>` 单系列 |
| Query 类型 | range query |
| PromQL | `doris_fe_editlog_write_latency_ms{job="$cluster", quantile="0.99"}` |
| y 轴 | `ms`，保留 1 位小数 |
| 说明 | **Latency — FE 元数据写入延迟**；BDBJE 写入 p99 升高表示元数据同步存在瓶颈。⚠️ 同样为 summary quantile 形式，需核验是否仍有效 |
| col-span | 12 |

---

### 5.3 段 C — BE（Backend）

> 本段面板均使用 `job="$cluster"` + `instance=~"$be_instance"` 过滤。

#### DO-C01 BE CPU Idle

| 属性 | 值 |
|---|---|
| 标题 | BE CPU Idle % (per instance) |
| 图表类型 | `<Line>` 多系列（by instance） |
| Query 类型 | range query |
| PromQL | `(sum(rate(doris_be_cpu{mode="idle", job="$cluster"}[$interval])) by (job, instance)) / (sum(rate(doris_be_cpu{job="$cluster"}[$interval])) by (job, instance)) * 100` |
| 系列字段 | `instance` |
| y 轴 | `%`，保留 1 位小数 |
| 阈值参考线 | y=20（红色虚线，Idle < 20% = CPU 告警） |
| 说明 | **Saturation — BE CPU 空闲率**；值越低代表 CPU 越繁忙（reverse 方向） |
| col-span | 8 |

#### DO-C02 BE Memory

| 属性 | 值 |
|---|---|
| 标题 | BE Memory Allocated |
| 图表类型 | `<Line>` 多系列（by instance） |
| Query 类型 | range query |
| PromQL | `doris_be_memory_allocated_bytes{job="$cluster", instance=~"$be_instance"}` |
| 系列字段 | `instance` |
| y 轴 | bytes（`formatBytes`） |
| 说明 | **Saturation — BE 内存分配量**；持续增长且无回落需关注内存泄漏 |
| col-span | 8 |

#### DO-C03 Disk Usage

| 属性 | 值 |
|---|---|
| 标题 | BE Disk Usage % (per path) |
| 图表类型 | `<Line>` 多系列（by instance+path） |
| Query 类型 | range query（multi-range） |
| PromQL 明细 | — |

| seriesLabel | PromQL |
|---|---|
| `used_pct` | `(SUM(doris_be_disks_total_capacity{job="$cluster", instance=~"$be_instance"}) by (instance, path) - SUM(doris_be_disks_avail_capacity{job="$cluster", instance=~"$be_instance"}) by (instance, path)) / SUM(doris_be_disks_total_capacity{job="$cluster", instance=~"$be_instance"}) by (instance, path)` |
| `local_used_pct` | `SUM(doris_be_disks_local_used_capacity{job="$cluster", instance=~"$be_instance"}) by (instance, path) / SUM(doris_be_disks_total_capacity{job="$cluster", instance=~"$be_instance"}) by (instance, path)` |

| 属性 | 值 |
|---|---|
| 颜色 | `used_pct` → `#ff4d4f`（红）、`local_used_pct` → `#faad14`（橙） |
| y 轴 | `percentunit`（0–1），formatter `(v) => \`${(v*100).toFixed(1)}%\`` |
| 阈值参考线 | y=0.8（80% 告警线） |
| 说明 | **Saturation — 磁盘容量**；`used_pct` 接近 1 时 Doris 会拒绝写入 |
| col-span | 8 |

#### DO-C04 Disk IO Util

| 属性 | 值 |
|---|---|
| 标题 | BE Disk IO Utilization |
| 图表类型 | `<Line>` 多系列（by instance） |
| Query 类型 | range query |
| PromQL | `rate(doris_be_disk_io_time_ms{job="$cluster", instance=~"$be_instance"}[$interval]) / 10` |
| 系列字段 | `instance` |
| y 轴 | `%`，保留 1 位小数 |
| 阈值参考线 | y=80（80% IO 饱和线） |
| 说明 | **Saturation — 磁盘 IO 利用率**；`disk_io_time_ms / 10` 转换为 % |
| col-span | 12 |

#### DO-C05 BE Compaction

| 属性 | 值 |
|---|---|
| 标题 | BE Compaction Throughput |
| 图表类型 | `<Line>` 多系列（2 系列） |
| Query 类型 | range query（multi-range） |
| PromQL 明细 | — |

| seriesLabel | PromQL |
|---|---|
| `base` | `rate(doris_be_compaction_bytes_total{type="base", job="$cluster", instance=~"$be_instance"}[$interval])` |
| `cumulative` | `rate(doris_be_compaction_bytes_total{type="cumulative", job="$cluster", instance=~"$be_instance"}[$interval])` |

| 属性 | 值 |
|---|---|
| 颜色 | `base` → `#1677ff`（蓝）、`cumulative` → `#52c41a`（绿） |
| y 轴 | `Bps`（`formatBytes/s`） |
| 说明 | **Saturation — Compaction 吞吐**；持续低于写入速率说明 Compaction 存在积压，影响查询性能 |
| col-span | 12 |

#### DO-C06 BE Scan Bytes

| 属性 | 值 |
|---|---|
| 标题 | BE Scan Bytes (Read Throughput) |
| 图表类型 | `<Line>` 多系列（by instance） |
| Query 类型 | range query |
| PromQL | `rate(doris_be_query_scan_bytes{job="$cluster", instance=~"$be_instance"}[$interval])` |
| 系列字段 | `instance` |
| y 轴 | `Bps`（`formatBytes/s`） |
| 说明 | **Traffic — BE 扫描字节速率**；反映查询时 BE 的数据读取压力 |
| col-span | 12 |

#### DO-C07 BE Scan Rows

| 属性 | 值 |
|---|---|
| 标题 | BE Scan Rows (Read Rate) |
| 图表类型 | `<Line>` 多系列（by instance） |
| Query 类型 | range query |
| PromQL | `rate(doris_be_query_scan_rows{job="$cluster", instance=~"$be_instance"}[$interval])` |
| 系列字段 | `instance` |
| y 轴 | `rows/s`，整数 |
| 说明 | **Traffic — BE 行扫描速率**；与 C06 联看可计算平均行宽 |
| col-span | 12 |

#### DO-C08 BE Push Bytes

| 属性 | 值 |
|---|---|
| 标题 | BE Push Bytes (Write Throughput) |
| 图表类型 | `<Line>` 多系列（by instance） |
| Query 类型 | range query |
| PromQL | `rate(doris_be_push_request_write_bytes{job="$cluster", instance=~"$be_instance"}[$interval])` |
| 系列字段 | `instance` |
| y 轴 | `Bps`（`formatBytes/s`） |
| 说明 | **Traffic — BE 数据写入字节速率**（导入流量） |
| col-span | 12 |

#### DO-C09 BE Push Rows

| 属性 | 值 |
|---|---|
| 标题 | BE Push Rows (Write Rate) |
| 图表类型 | `<Line>` 多系列（by instance） |
| Query 类型 | range query |
| PromQL | `rate(doris_be_push_request_write_rows{job="$cluster", instance=~"$be_instance"}[$interval])` |
| 系列字段 | `instance` |
| y 轴 | `rows/s`，整数 |
| 说明 | **Traffic — BE 行写入速率**；与 C08 联看可计算平均写入行宽 |
| col-span | 12 |

#### DO-C10 BE Engine Request Latency ★新建

| 属性 | 值 |
|---|---|
| 标题 | BE Engine Request Latency (Push Task) |
| 图表类型 | `<Line>` 多系列（by instance） |
| Query 类型 | range query |
| PromQL | `irate(doris_be_push_request_duration_us{job="$cluster", instance=~"$be_instance"}[$interval]) / 1000` |
| 系列字段 | `instance` |
| y 轴 | `ms`，保留 2 位小数 |
| 说明 | **Latency — BE 引擎请求延迟核心缺口面板——9734 中无、按选型要求新建。** 9734 的 BE tasks 行只有 `doris_be_engine_requests_total` 计数，无延迟 histogram。此处使用 `doris_be_push_request_duration_us`（Push Task Cost Time）作为 BE 端操作延迟的代理指标；Phase 3 联调时若 4.0.5 暴露 `doris_be_engine_requests_duration_us` histogram，可升级为 `histogram_quantile(0.99, ...)` 写法 |
| col-span | 12 |

#### DO-C11 Net send/recv Bytes

| 属性 | 值 |
|---|---|
| 标题 | BE Network Bytes |
| 图表类型 | `<Line>` 多系列（2 系列） |
| Query 类型 | range query（multi-range） |
| PromQL 明细 | — |

| seriesLabel | PromQL |
|---|---|
| `send` | `irate(doris_be_network_send_bytes{job="$cluster", group="be", device!="lo", instance=~"$be_instance"}[$interval])` |
| `recv` | `irate(doris_be_network_receive_bytes{job="$cluster", group="be", device!="lo", instance=~"$be_instance"}[$interval])` |

| 属性 | 值 |
|---|---|
| 颜色 | `send` → `#1677ff`（蓝）、`recv` → `#52c41a`（绿） |
| y 轴 | `Bps`（`formatBytes/s`） |
| 说明 | **Traffic — BE 网络吞吐**；排除 lo 回环接口，仅统计物理网卡 |
| col-span | 24 |

---

## 6. 主题 / 样式规范

**完全复用 `prometheus-dashboard-prototype-spec.md` §6 中的颜色 Token 和工具函数。**

```ts
import { CHART_COLORS, colorByThreshold, formatBytes, formatCompact } from '../_shared/charts/formatters';
```

Doris 特有颜色补充：

```ts
// Doris 角色分色（FE 蓝系 / BE 绿系）
const dorisRoleColors = {
  fe:         CHART_COLORS.primary,    // #1677ff FE
  be:         CHART_COLORS.success,    // #52c41a BE
  error:      CHART_COLORS.error,      // #ff4d4f 错误 / 红
  warning:    CHART_COLORS.warning,    // #faad14 告警 / 黄
  saturation: '#fa8c16',               // 橙：Compaction score 高压
  reference:  '#8c8c8c',               // 灰：max/参考线
};

// Latency 分位颜色
const latencyColors = {
  p50: CHART_COLORS.success,   // #52c41a
  p75: CHART_COLORS.warning,   // #faad14
  p99: CHART_COLORS.error,     // #ff4d4f
};

// Compaction 类型颜色
const compactionColors = {
  base:       CHART_COLORS.primary,    // #1677ff
  cumulative: CHART_COLORS.success,    // #52c41a
};
```

---

## 7. 数据层接口 TypeScript 定义

**复用 `prometheus-dashboard-prototype-spec.md` §7 中全部接口定义（`PrometheusVector`、`PrometheusMatrix`、`TimeSeriesPoint`、`StatValue`）。**

Doris 特有补充：

```ts
// 看板查询变量（Doris 版）
interface DorisDashboardVariables {
  cluster: string;        // Prometheus job label，等于集群名（替代 9734 的 $cluster_name）
  feInstance: string;     // 正则，如 ".+" 或 "192.168.1.10:18030"
  beInstance: string;     // 正则，如 ".+" 或 "192.168.1.11:18040"
  interval: string;       // rate 窗口，如 "2m"
}

interface DorisDashboardQueryParams {
  clusterId: number;
  start: number;          // unix timestamp (seconds)
  end: number;
  step: number;           // 建议 = (end - start) / 200
  variables: DorisDashboardVariables;
}

// Stat 面板快照值
interface DorisInstantValues {
  feNodeCount: number;
  feAliveCount: number;
  beNodeCount: number;
  beAliveCount: number;
  usedCapacityBytes: number;
  totalCapacityBytes: number;
}
```

---

## 8. 组件树结构

```
<DorisDashboard>                          # 页面容器，管理 variables + timeRange + refresh
  ├── <DorisDashboardToolbar>             # 集群▼ + FE实例▼ + BE实例▼ + Rate窗口▼ + 时间▼ + 刷新
  │
  ├── <SectionHeader title="SEGMENT A — 集群概览"/>
  ├── <Row A-R1>                          # 节点 & 容量 Stat（高度 80px）
  │   ├── <StatPanel DO-A01>              # FE Node Count
  │   ├── <StatPanel DO-A02>              # FE Alive
  │   ├── <StatPanel DO-A03>              # BE Node Count
  │   ├── <StatPanel DO-A04>              # BE Alive
  │   ├── <StatPanel DO-A05>              # Used Disk Capacity
  │   └── <StatPanel DO-A06>              # Total Disk Capacity
  ├── <Row A-R2>                          # 集群级趋势
  │   ├── <TimeSeriesPanel DO-A07>        # Cluster QPS
  │   ├── <TimeSeriesPanel DO-A08>        # FE JVM Heap %（by instance）
  │   └── <TimeSeriesPanel DO-A09>        # BE CPU Idle %
  │
  ├── <SectionHeader title="SEGMENT B — FE Frontend ($fe_instance)"/>
  ├── <Row B-R1>
  │   ├── <TimeSeriesPanel DO-B01>        # RPS
  │   └── <TimeSeriesPanel DO-B02>        # QPS
  ├── <Row B-R2>
  │   ├── <TimeSeriesPanel DO-B03>        # 99th Latency ⚠️
  │   └── <TimeSeriesPanel DO-B04>        # Query Percentile p50/p75/p99 ⚠️
  ├── <Row B-R3>
  │   ├── <TimeSeriesPanel DO-B05>        # Query Error Count ⚠️
  │   └── <TimeSeriesPanel DO-B06>        # Query Error Rate % ★新建
  ├── <Row B-R4>
  │   ├── <TimeSeriesPanel DO-B07>        # Connections
  │   └── <TimeSeriesPanel DO-B08>        # FE Compaction Score ⚠️
  ├── <Row B-R5>
  │   ├── <TimeSeriesPanel DO-B09>        # Scheduling Tablets
  │   └── <TimeSeriesPanel DO-B12>        # BDBJE Write Latency ⚠️
  └── <Row B-R6>
      ├── <AreaPanel DO-B10>              # JVM Heap bytes ⚠️
      └── <TimeSeriesPanel DO-B11>        # JVM Old GC ⚠️
  │
  ├── <SectionHeader title="SEGMENT C — BE Backend ($be_instance)"/>
  ├── <Row C-R1>
  │   ├── <TimeSeriesPanel DO-C01>        # BE CPU Idle %
  │   ├── <TimeSeriesPanel DO-C02>        # BE Memory
  │   └── <TimeSeriesPanel DO-C03>        # Disk Usage %
  ├── <Row C-R2>
  │   ├── <TimeSeriesPanel DO-C04>        # Disk IO Util
  │   └── <TimeSeriesPanel DO-C05>        # BE Compaction (base + cumulate)
  ├── <Row C-R3>
  │   ├── <TimeSeriesPanel DO-C06>        # BE Scan Bytes
  │   └── <TimeSeriesPanel DO-C07>        # BE Scan Rows
  ├── <Row C-R4>
  │   ├── <TimeSeriesPanel DO-C08>        # BE Push Bytes
  │   └── <TimeSeriesPanel DO-C09>        # BE Push Rows
  └── <Row C-R5>
      ├── <TimeSeriesPanel DO-C10>        # BE Engine Request Latency ★新建
      └── <TimeSeriesPanel DO-C11>        # Net send/recv Bytes（col=24）

# 复用的基础组件（来自 `monitor/_shared/panels/`、utils/、service.ts）
StatPanel / TimeSeriesPanel / AreaPanel / queryInstant / queryRange
CHART_COLORS / colorByThreshold / formatBytes / formatCompact
PrometheusVector / PrometheusMatrix / TimeSeriesPoint
```

---

## 9. 实现说明（供 Phase 3 编码参考）

### 9.1 文件路径

```
datasophon-ui-v2/src/pages/DorisMonitor/
  ├── index.tsx                     # 页面容器（三段式布局）
  ├── panelQueries.ts               # PanelDef（32 个面板的 instant/range/multi-range 定义）
  ├── panelQueries.test.ts          # vitest 测试（catalog 完整性 + replaceDorisVars）
  ├── hooks/
  │   └── useDorisMonitorDashboard.ts   # 数据层 hook（复用 DS hook 模式）
  ├── toolbar/
  │   └── DorisDashboardToolbar.tsx    # 集群/FE实例/BE实例/Rate窗口 选择器
  └── (panels/ 和 utils/ 直接引用 `monitor/_shared/` 中的共享组件，无需复制)
```

**6 个接线文件**（与 DolphinSchedulerMonitor 完全相同的模式）：

```
config/routes.ts                           ← 添加 /monitor/doris 路由
src/locales/zh-CN/menu.ts                  ← 'menu.doris-monitor': 'Doris 监控'
src/locales/en-US/menu.ts                  ← 'menu.doris-monitor': 'Doris Monitor'
src/locales/zh-CN/dorisMonitor.ts          ← 新建：pages.dorisMonitor.* 键值
src/locales/en-US/dorisMonitor.ts          ← 新建：pages.dorisMonitor.* 键值
src/locales/zh-CN.ts                       ← import + spread dorisMonitor
src/locales/en-US.ts                       ← import + spread dorisMonitor
```

### 9.2 PromQL 变量替换规则（Doris 版）

```ts
// panelQueries.ts 中的替换函数
export function replaceDorisVars(
  promql: string,
  vars: Partial<DorisDashboardVariables>,
): string {
  return promql
    .replace(/\$cluster/g,    vars.cluster    || 'doris')
    .replace(/\$fe_instance/g, vars.feInstance || '.+')
    .replace(/\$be_instance/g, vars.beInstance || '.+')
    .replace(/\$interval/g,   vars.interval   || '2m');
}
// 注：$fe_master（FE Master 实例）在原型阶段不替换，保持 PromQL 中省略或使用 $fe_instance
```

### 9.2.1 Hook 集成（`useDorisMonitorDashboard` 实现说明）

Doris 有多 segment 硬约束：**每次只拉当前激活 segment 的面板，不允许一次性拉全部**（防止后端超时）。

```ts
// panelIds 按 activeSegment 动态计算
const panelIds = useMemo(() => getDorisSegmentPanelIds(activeSegment), [activeSegment]);
// extras: 三个 up 查询分别派生集群列表 / FE 实例 / BE 实例
const extras = useMemo(() => ({
  clustersVec: { query: 'up{group="fe"}', kind: 'instant' },
  feUp: { query: `up{group="fe", job="${variables.cluster || 'doris'}"}`, kind: 'instant' },
  beUp: { query: `up{group="be", job="${variables.cluster || 'doris'}"}`, kind: 'instant' },
}), [variables.cluster]);

const data = useDashboardData({
  replaceVars: (p, v) => replaceDorisVars(p, v as Partial<DorisDashboardVariables>),
  variables, panelIds, extras, ...
});
```

`activeSegment` 变化（切 Tab）→ `panelIds` 变化 → hook 只重拉该 segment 的 PromQL。

### 9.3 多系列面板（multi-range）实现模式

与 DolphinSchedulerMonitor 完全相同。以 DO-B04 Query Percentile 为例：

```ts
// panelQueries.ts
'DO-B04': {
  type: 'multi-range',
  queries: [
    { label: 'p50', promql: 'doris_fe_query_latency_ms{job="$cluster", instance=~"$fe_instance", quantile="0.5"}' },
    { label: 'p75', promql: 'doris_fe_query_latency_ms{job="$cluster", instance=~"$fe_instance", quantile="0.75"}' },
    { label: 'p99', promql: 'doris_fe_query_latency_ms{job="$cluster", instance=~"$fe_instance", quantile="0.99"}' },
  ],
},
```

### 9.4 SectionHeader 组件

复用 DolphinSchedulerMonitor 中的 `SectionHeader` 组件写法：

```tsx
<SectionHeader
  title="SEGMENT B — FE"
  subtitle='数据源：FE :18030/metrics | 变量：$cluster + $fe_instance'
/>
```

### 9.5 Mock 数据要求

`dorisMonitor.ts` mock 覆盖 32 个面板（Phase 2 mock 阶段，不真实连接 Prometheus）：

**段 A 概览 Stat：**
- A01 FE Node: 3（台）
- A02 FE Alive: 3（绿色）
- A03 BE Node: 5（台）
- A04 BE Alive: 5（绿色）
- A05 Used Capacity: 3.5 TB（`3_500_000_000_000` bytes）
- A06 Total Capacity: 10 TB

**段 A 趋势：**
- A07 QPS: 800–1200 query/s，偶有峰值
- A08 FE JVM Heap%: 60–75%（3 条线，偶有 GC 后回落至 50%）
- A09 BE CPU Idle: 40–60%（正常值，5 条线）

**段 B FE：**
- B01 RPS: 1000–1500 req/s
- B02 QPS: 800–1200 query/s
- B03 99th Latency: 80–200ms（3 条线，偶有 500ms 尖刺）
- B05 Query Error Count rate: 正常为 0，偶发 0.01/s（验证红色）
- B06 Query Error Rate: 正常 < 0.01%，偶发 0.5%

**段 C BE：**
- C01 BE CPU Idle: 40–60%（5 台 BE）
- C02 BE Mem: 8–12 GB per instance
- C03 Disk Usage: 30–50%（每 BE 2 个 path）
- C06 BE Scan Bytes: 500–800 MB/s
- C08 BE Push Bytes: 50–200 MB/s
- C10 BE Engine Latency: 1–5 ms（正常导入任务）

---

## 10. Dev 环境配置注意事项

**完全复用 `prometheus-dashboard-prototype-spec.md` §10 中的三项配置（publicPath、proxy bypass、mock 路径对齐）。**

Doris 看板额外注意：

- mock 路径与 PrometheusMonitor 完全相同（`/ddh/api/v2/prometheus/query` 和 `/query_range`），**无需新增 mock 端点**。
- 段 B/C 的 PromQL 中 `instance=~"$be_instance"` 使用正则匹配，mock 中的 vector 结果 `metric.instance` 字段需与 `$be_instance` 默认值 `.+` 可匹配（即非空字符串即可）。
- mock vector 的 `metric` 字段**必须包含 `group` 标签**（`group: "fe"` 或 `group: "be"`），否则依赖 `group` 过滤的面板会出现空图——即便是 mock 数据也需模拟此标签。

---

## 11. 验收标准

Phase 2 原型（mock 阶段）完成后，需满足：

- [ ] 三段式布局（A 集群概览 / B FE / C BE）正确呈现，每段有 SectionHeader 含端口提示（18030/18040）
- [ ] 段 A 的 6 个 Stat 面板（A01–A06）使用 `<Statistic>` 正确渲染；A02/A04（Alive 数）reverse 阈值染色正常
- [ ] 段 A 的 3 个趋势面板（A07–A09）`<Line>` 正常渲染 mock 时序数据
- [ ] **DO-B06 Query Error Rate % 面板存在**（★新建面板，错误维度核心）；正常 mock 值 < 0.1%，颜色绿色
- [ ] **DO-C10 BE Engine Request Latency 面板存在**（★新建面板，BE 延迟维度）；mock 值 1–5ms
- [ ] DO-B03/B04 的 Latency 面板已标注 ⚠️ 风险注记（在 UI tooltip 或面板 description 中体现）
- [ ] DO-B08 FE Compaction Score 面板标注 ⚠️ 双命名风险
- [ ] DO-B10 JVM Heap `<Area>` 组件正确渲染，y 轴 `formatBytes` 自动换算 GB
- [ ] 工具栏 `$cluster` 下拉更改后，全部三段面板数据同步刷新
- [ ] 工具栏 `$fe_instance` 仅影响段 B 面板，段 C 面板不受影响
- [ ] 工具栏 `$be_instance` 仅影响段 C 面板，段 B 面板不受影响
- [ ] `doris_be_network_*` 的 Net send/recv 面板排除 `lo` 接口，mock metric 的 `device` 字段为 `eth0`
- [ ] 在 1280px 宽度下三段布局无横向滚动条
- [ ] golden signals 四象限（延迟/流量/错误/饱和度）均有对应面板，§5.0 四象限表对应关系可追溯
- [ ] `panelQueries.test.ts` 通过（catalog 完整性：32 个 panel ID 全部存在；`replaceDorisVars` 替换 `$cluster`/`$fe_instance`/`$be_instance`/`$interval` 正确）

---

## 12. 联调踩坑记录（Phase 3 实现时必读）

本章引用 `prometheus-dashboard-prototype-spec.md` §12 中记录的三个踩坑案例：

| 踩坑编号 | 标题 | 适用于 Doris 的场景 |
|---|---|---|
| 12.1 | `service.ts` 请求路径双前缀 | 所有 range/instant 面板的 service.ts 调用路径（同样的 `/ddh/api/v2/prometheus/` 基础路径） |
| 12.2 | PromQL `+` 运算符被后端解码为空格 | Doris PromQL 含大量 `*100`、`/`、`+0` 运算符（FE JVM Heap%、BE CPU Idle%、Disk Usage%、Error Rate 等均受影响）—— 后端 URLEncoder 必须正确编码，这比 DS 看板更容易踩坑 |
| 12.3 | `@ant-design/plots` 时间轴切换不更新 | 所有 `<TimeSeriesPanel>` / `<AreaPanel>` 均需 `chartKey` prop（`data[0].time + '-' + data[-1].time`） |

> 详见：`docs/monitoring/design/prometheus-dashboard-prototype-spec.md` §12

**Doris 特有注意点：**

1. **`group` 标签缺失导致全盘空图**：9734 所有 PromQL 依赖 `group="fe"/"be"` 标签。Phase 3 联调时，若 Prometheus 抓取未配置 relabel 注入此标签，FE/BE 所有面板全部空图（无数据，非报错）。排查方法：在 Prometheus UI 执行 `up{job="<cluster>"}` 确认是否有 `group` 标签返回。
2. **端口 18030/18040 vs 8030/8040**：DataSophon `service_ddl.json` 的端口与 Doris 官方默认端口不同。若测试环境使用官方默认端口部署，Prometheus 抓取 target 填写 8030/8040；若 DataSophon 部署则填 18030/18040。两套端口对应同一指标体系，PromQL 无差异。
3. **`doris_fe_query_err` 指标名风险**：B05/B06 使用此指标。若 4.0.5 已迁移至 `doris_fe_query_err_total`，两个面板均需改写 PromQL，rate 计算同时需改为 `increase(...[1m])` 或 `rate(...[interval])`。
4. **Summary quantile vs Histogram**：B03/B04 的 latency 面板和 B12 的 BDBJE 延迟面板使用 `quantile="0.99"` label 过滤。若 4.0.5 已将指标类型从 Summary 改为 Histogram，`quantile` label 将不存在，需改用 `histogram_quantile(0.99, rate(doris_fe_query_latency_ms_bucket{...}[$interval]))`——两种写法互不兼容，Phase 3 前必须在 4.0.5 的 `:18030/metrics` 原始输出中确认 `TYPE doris_fe_query_latency_ms` 是 `summary` 还是 `histogram`。
