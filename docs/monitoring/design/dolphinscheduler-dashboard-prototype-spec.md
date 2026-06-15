# DolphinScheduler 监控看板原型设计 Spec

> **文档用途**：供 Claude design 阅读，根据本 spec 设计 React + AntV G2 看板原型，并最终生成可运行的组件代码。  
> **组件**：Apache DolphinScheduler 3.4.1  
> **数据源**：Spring Boot Micrometer `/actuator/prometheus`（Master `:5679` / Worker `:1235` / Alert `:50053`），`/dolphinscheduler/actuator/prometheus`（API Server `:12345`）  
> **参考看板**：DolphinScheduler Worker Dashboard + Master Dashboard（官方 GitHub Grafana JSON）  
> **Panel Catalog 路径**：`docs/monitoring/panel-catalog/DolphinScheduler.json`（~70+ 原始面板，精选 31 个）  
> **Phase**：Phase 2 —— 原型设计阶段

---

## 1. 架构约束

```
React(AntV G2)
  └──HTTP──> datasophon-api /api/v2/prometheus/query_range
               └──PromQL──> Prometheus :9090
                              └──scrape──> DS Master :5679 / Worker :1235 / Alert :50053 / API :12345
```

**前端不直连 Prometheus**。所有 PromQL 通过后端代理端点转发（与 Prometheus 自监控完全相同的代理路径）。

### 1.1 多角色数据源

DolphinScheduler 是多角色 Spring Boot 微服务，各角色独立暴露 Micrometer 端点：

| 角色 | metrics 端口 | path |
|---|---|---|
| master-server | 5679 | `/actuator/prometheus` |
| worker-server | 1235 | `/actuator/prometheus` |
| alert-server | 50053 | `/actuator/prometheus` |
| api-server | 12345 | `/dolphinscheduler/actuator/prometheus` |

Prometheus 通过 `application` 标签区分角色（`{application="master-server"}`）。

### 1.2 ⚠️ 关键设计约束：三段式布局与 `$application` 作用域

本看板采用**单页三纵段**布局：

- **段 A — Worker**：PromQL 中 `application` 硬编码为 `"worker-server"`，**不受工具栏 `$application` 下拉影响**
- **段 B — Master + 调度**：PromQL 中 `application` 硬编码为 `"master-server"`，**不受工具栏 `$application` 下拉影响**
- **段 C — 通用 Spring Boot**：PromQL 使用 `$application` 占位符，**受工具栏 `$application` 下拉控制**

实现时必须在段 A / 段 B 的分区标题旁注明硬编码值（如 `application: "worker-server"`），避免运维人员误认为 `$application` 下拉会影响这两段。

---

## 2. 图表类型映射字典

**完全复用 `docs/monitoring/design/prometheus-dashboard-prototype-spec.md` §2 中的映射字典。**

以下为 DolphinScheduler 特有的补充说明：

| Grafana chartType（catalog） | 映射组件 | 备注 |
|---|---|---|
| `stat` | `<Statistic>` (antd) + 阈值染色 | Task 总数、Job 总数等累计值 |
| `gauge` | `<Statistic>` (antd) + 阈值染色 | Task 成功率、Job 成功率；不用径向仪表盘，统一用 Statistic |
| `graph` 多系列 | `<Line>` with `seriesField` | 执行时间 avg/max 双线 |
| `timeseries` 多 PromQL | `<Line>` + multi-range 合并 | 流程实例状态、Task 实例状态等多 PromQL 拼多系列 |
| `heatmap` | **全部跳过** | Quartz 耗时分布等 heatmap 面板不在原型范围 |

> ⚠️ `gauge` 类型原为 Grafana 径向仪表盘，在本项目中统一降级为 `<Statistic>`，使用 `colorByThreshold` 着色（rate 类：阈值 70/90，reverse=false）。

---

## 3. 变量 / 过滤器规范

看板顶部工具栏包含以下变量：

| 变量 | PromQL 占位符 | 取值来源 | 默认值 | 适用范围 |
|---|---|---|---|---|
| 角色 | `$application` | 固定选项列表 | `master-server` | **仅段 C 通用面板** |
| 实例 | `$instance` | `label_values(up{application="$application"}, instance)` | `.+`（全选） | 段 C 通用面板（段 A/B 不用） |
| 时间范围 | — | 时间选择器 | `Last 1h` | 所有面板 |
| 刷新间隔 | — | — | `30s` | 所有面板 |

**`$application` 固定选项**：`master-server`（默认）、`worker-server`、`api-server`、`alert-server`

> **无 `$job` 变量**：DolphinScheduler 通过 `application` 标签区分角色，不使用 Prometheus 惯用的 `job` 标签，PromQL 中不出现 `job=~"$job"` 占位符。  
> **无 `$interval` 变量**：DS 看板的累计/速率面板使用固定窗口（`[1m]`/`[5m]`），不提供用户可调的统计窗口。

---

## 4. 看板布局（24 列 Grid）

```
┌──────────────────────────────────────────────────────────────────────────┐
│  TOOLBAR: [角色▼ master-server]  [实例▼]    [Last 1h▼]   [🔄 30s▼]    │
│  ⚠️ 角色选择器仅影响通用段（段 C）                                           │
└──────────────────────────────────────────────────────────────────────────┘

══════════════════ SEGMENT A — WORKER  (application="worker-server") ══════

行 A-R1 — Worker 饱和度（高度 200px）
┌──────────────────────┬──────────────────────┬──────────────────────┐
│  Worker CPU Usage    │  Submit Queue Full   │  Worker Overload     │
│  [Line: 单系列]       │  /1m [Line: 单系列]  │  /1m [Line: 单系列]  │
│  col-span=8          │  col-span=8          │  col-span=8          │
└──────────────────────┴──────────────────────┴──────────────────────┘

行 A-R2 — Worker 任务 & 资源（高度 200px）
┌──────────────────────────────────────┬─────────────────────────────────────┐
│  Worker Tasks Total                  │  Resource Download Count/5m         │
│  [Line: 单系列]                       │  [Line: total/success/fail 3 系列]  │
│  col-span=12                         │  col-span=12                        │
└──────────────────────────────────────┴─────────────────────────────────────┘

行 A-R3 — 资源下载耗时（高度 200px）
┌────────────────────────────────────────────────────────────────────────┐
│  Resource Download Duration/5m                                         │
│  [Line: 单系列，单位 s]                                                 │
│  col-span=24                                                           │
└────────────────────────────────────────────────────────────────────────┘

══════════════════ SEGMENT B — MASTER  (application="master-server") ══════

行 B-R1 — Master 概览 Stat（高度 80px）
┌──────────┬──────────┬──────────┬──────────┐
│Task Total│Task Succ │ Job Total│ Job Succ │
│  Count   │  Rate %  │  Count   │  Rate %  │
│ col=6    │ col=6    │ col=6    │ col=6    │
└──────────┴──────────┴──────────┴──────────┘

行 B-R2 — Master 过载 & 消费（高度 200px）
┌────────────────────────────────────────┬───────────────────────────────────┐
│  Master Overload/1m                    │  Master Consume Command/1m        │
│  [Line: 单系列]                         │  [Line: 单系列]                    │
│  col-span=12                           │  col-span=12                      │
└────────────────────────────────────────┴───────────────────────────────────┘

行 B-R3 — Quartz 调度（高度 200px）
┌────────────────────────────────────────┬───────────────────────────────────┐
│  Quartz Job Executed Count/min         │  Quartz Job Execution Time        │
│  [Line: total/success/failure 3 系列]  │  [Line: avg/max 2 系列，单位 s]    │
│  col-span=12                           │  col-span=12                      │
└────────────────────────────────────────┴───────────────────────────────────┘

行 B-R4 — Task 执行（高度 200px）
┌────────────────────────────────────────┬───────────────────────────────────┐
│  Task Execution Total & Success/1m     │  Task Execution Time              │
│  [Line: total/success 2 系列]          │  [Line: avg/max 2 系列，单位 ms]  │
│  col-span=12                           │  col-span=12                      │
└────────────────────────────────────────┴───────────────────────────────────┘

行 B-R5 — 流程实例状态（高度 200px）
┌────────────────────────────────────────────────────────────────────────┐
│  Process Instance States/1m                                            │
│  [Line: submit/success/fail/timeout 4 系列]                            │
│  col-span=24                                                           │
└────────────────────────────────────────────────────────────────────────┘

行 B-R6 — Task 分发 & 实例状态（高度 200px）
┌────────────────────────────────────────┬───────────────────────────────────┐
│  Task Dispatch Count/1m                │  Task Instance States/1m          │
│  [Line: dispatch/failure/error 3 系列] │  [Line: submit/success/fail/retry]│
│  col-span=12                           │  col-span=12                      │
└────────────────────────────────────────┴───────────────────────────────────┘

══════════════ SEGMENT C — 通用 Spring Boot  ($application + $instance) ══

行 C-R1 — 运行状态 Stat（高度 80px）
┌────────────────────────┬────────────────────────┬────────────────────────┐
│  Uptime                │  Heap Used %           │  Non-Heap Used %       │
│  [Stat: 秒]             │  [Stat: %, 阈值 70/90] │  [Stat: %, 阈值 70/90] │
│  col=8                 │  col=8                 │  col=8                 │
└────────────────────────┴────────────────────────┴────────────────────────┘

行 C-R2 — HTTP 请求（高度 200px）
┌──────────────────────┬──────────────────────┬──────────────────────┐
│  HTTP Request Rate   │  HTTP 5xx Error Rate │  HTTP Duration       │
│  [Line: ops/s]       │  [Line: ops/s]       │  [Line: avg/max, s]  │
│  col-span=8          │  col-span=8          │  col-span=8          │
└──────────────────────┴──────────────────────┴──────────────────────┘

行 C-R3 — JVM 内存（高度 200px）
┌───────────────────────────────────────┬────────────────────────────────────┐
│  JVM Heap (bytes)                     │  JVM Non-Heap (bytes)              │
│  [Area: used/committed/max 3 系列]    │  [Area: used/committed/max 3 系列] │
│  col-span=12                          │  col-span=12                       │
└───────────────────────────────────────┴────────────────────────────────────┘

行 C-R4 — CPU & 线程（高度 200px）
┌──────────────────────┬──────────────────────┬──────────────────────┐
│  CPU Usage           │  System Load         │  Threads             │
│  [Line: sys/proc]    │  [Line: load + cores]│  [Line: live/daemon  │
│  col-span=8          │  col-span=8          │   /peak, col=8]      │
└──────────────────────┴──────────────────────┴──────────────────────┘

行 C-R5 — 日志 & GC（高度 200px）
┌───────────────────────────────────────┬────────────────────────────────────┐
│  Log Events/1m                        │  GC Pause Rate                     │
│  [Line: by level 多系列]              │  [Line: GC 集合速率，单位 ops/s]   │
│  col-span=12                          │  col-span=12                       │
└───────────────────────────────────────┴────────────────────────────────────┘
```

---

## 5. 面板规格（逐面板）

### 5.0 裁剪说明（~70 → 31）

**从 `panel-catalog/DolphinScheduler.json`（~70+ 面板）裁剪至 31 个。**

| 裁剪类别 | 代表面板 | 裁剪理由 |
|---|---|---|
| task_type 细分（/1d）| Shell/DATAX/EMR/DEPENDENT/BLOCKING Task Execute/1d ~10 个 | 同构高、运维价值低；保留聚合 Task 视图 |
| heatmap 面板 | Quartz Execution Time Distribution、Resource Download Size Distribution | 前端原型不支持 heatmap 渲染 |
| 重复面板 | DATA_QUALITY×2（catalog bug）、Quartz Job Executed Count（累计，重复/min 版） | 逻辑冗余 |
| 深度 JVM 池 | `$jvm_memory_pool_heap`/`$jvm_memory_pool_nonheap`（参数化池） | 需另一组变量，超出原型范围 |
| 次要运营指标 | Worker Start Time、Start time、File Descriptors、JVM Total、JVM Process Memory(VSS/RSS) | 可从其他面板推断 |
| Process Instance By Definition Code | `ds_workflow_instance_count_total{process_definition_code="dummy"}` | 使用虚拟代码，无生产意义 |
| Task Stop/Failover Count | `state="retry"` PromQL（catalog bug：Stop 重用了 retry PromQL）、failover | 数据可信度存疑，运维价值低 |
| Worker 下载大小 | Worker Resource Download Size/5m、Size Distribution | 流量而非健康，次要 |
| Master 线程（独立段 B 面板） | JVM Thread / Thread Status（硬编码 master-server） | 合并进通用段 C 的 Threads 面板 |

**golden signals 四象限覆盖**：

| 维度 | 面板 |
|---|---|
| **Latency** | Task Execution Time (avg/max)、Quartz Job Execution Time、HTTP Duration |
| **Traffic** | Task Execution Total/Success/1m、Process Instance States/1m、HTTP Request Rate |
| **Errors** | Task Dispatch Failure/Error、Task Instance Fail/Timeout、HTTP 5xx Error Rate |
| **Saturation** | Worker Submit Queue Full、Worker Overload、Heap%/Non-Heap%、GC Pause Rate |

---

### 5.1 段 A — Worker

#### D-A01 Worker CPU Usage

| 属性 | 值 |
|---|---|
| 标题 | Worker CPU Usage |
| 图表类型 | `<Line>` 单系列 |
| Query 类型 | range query |
| PromQL | `process_cpu_usage{application="worker-server"}` |
| y 轴 | 百分比（`percentunit`），`(v) => \`${(v * 100).toFixed(1)}%\`` |
| 阈值参考线 | y=0.8（80% CPU 警戒线，红色虚线） |
| col-span | 8 |
| 注意 | `application="worker-server"` 硬编码，不受 `$application` 下拉影响 |

#### D-A02 Worker Submit Queue Full/1m

| 属性 | 值 |
|---|---|
| 标题 | Worker Submit Queue Full / 1m |
| 图表类型 | `<Line>` 单系列 |
| Query 类型 | range query |
| PromQL | `increase(ds_worker_full_submit_queue_count_total[1m])` |
| y 轴 | 整数（次数） |
| 说明 | 值 > 0 表示 Worker 队列已满，任务无法提交；Saturation 核心指标 |
| col-span | 8 |

#### D-A03 Worker Overload/1m

| 属性 | 值 |
|---|---|
| 标题 | Worker Overload / 1m |
| 图表类型 | `<Line>` 单系列 |
| Query 类型 | range query |
| PromQL | `increase(ds_worker_overload_count_total[1m])` |
| y 轴 | 整数（次数） |
| 说明 | Worker 资源超载次数；与 A02 共同构成 Saturation 维度 |
| col-span | 8 |

#### D-A04 Worker Tasks Total

| 属性 | 值 |
|---|---|
| 标题 | Worker Tasks Total |
| 图表类型 | `<Line>` 单系列 |
| Query 类型 | range query |
| PromQL | `ds_worker_task{}` |
| y 轴 | 整数（任务数） |
| 说明 | Worker 端当前 task 数量趋势；Traffic 维度补充 |
| col-span | 12 |

#### D-A05 Resource Download Count/5m

| 属性 | 值 |
|---|---|
| 标题 | Worker Resource Download Count / 5m |
| 图表类型 | `<Line>` 多系列（3 系列） |
| Query 类型 | range query（multi-range，3 条 PromQL 合并） |
| PromQL 明细 | — |

| seriesLabel | PromQL |
|---|---|
| `total` | `sum(increase(ds_worker_resource_download_count_total[5m]))` |
| `success` | `increase(ds_worker_resource_download_count_total{status="success"}[5m])` |
| `fail` | `increase(ds_worker_resource_download_count_total{status="fail"}[5m])` |

| 属性 | 值 |
|---|---|
| 颜色 | `total` → `#1677ff`（主蓝）、`success` → `#52c41a`（绿）、`fail` → `#ff4d4f`（红） |
| y 轴 | 整数（下载次数） |
| col-span | 12 |

#### D-A06 Resource Download Duration/5m

| 属性 | 值 |
|---|---|
| 标题 | Worker Resource Download Duration / 5m |
| 图表类型 | `<Line>` 单系列 |
| Query 类型 | range query |
| PromQL | `increase(ds_worker_resource_download_duration_seconds[5m])` |
| y 轴 | 秒（`s`），保留 3 位小数 |
| 说明 | 资源下载耗时；Latency 补充指标 |
| col-span | 24 |

---

### 5.2 段 B — Master + 调度

#### D-B01 Task Total Count

| 属性 | 值 |
|---|---|
| 标题 | Task Total Count |
| 图表类型 | `<Statistic>` (antd) |
| Query 类型 | instant query |
| PromQL | `sum(ds_task_execution_count_total)` |
| 单位 | 整数（累计值，`formatCompact` 格式化） |
| 阈值规则 | 无（纯展示，蓝色 `#1677ff`） |
| col-span | 6 |
| 注意 | 累计值，不会下降；用于 Traffic 概览 |

#### D-B02 Task Successful Rate

| 属性 | 值 |
|---|---|
| 标题 | Task Successful Rate |
| 图表类型 | `<Statistic>` (antd) |
| Query 类型 | instant query |
| PromQL | `sum(ds_task_execution_count_total{result="success"}) / sum(ds_task_execution_count_total) * 100` |
| 单位 | `%`，保留 1 位小数 |
| 阈值规则 | **reverse（越高越好）**：`≥ 95` → 绿；`80 ≤ value < 95` → 黄；`< 80` → 红 |
| col-span | 6 |

#### D-B03 Quartz Job Total Count

| 属性 | 值 |
|---|---|
| 标题 | Quartz Job Total Count |
| 图表类型 | `<Statistic>` (antd) |
| Query 类型 | instant query |
| PromQL | `sum(ds_master_quartz_job_executed_total)` |
| 单位 | 整数（累计 Jobs） |
| 阈值规则 | 无（蓝色 `#1677ff`） |
| col-span | 6 |

#### D-B04 Quartz Job Successful Rate

| 属性 | 值 |
|---|---|
| 标题 | Quartz Job Successful Rate |
| 图表类型 | `<Statistic>` (antd) |
| Query 类型 | instant query |
| PromQL | `sum(ds_master_quartz_job_executed_total{result="success"}) / sum(ds_master_quartz_job_executed_total) * 100` |
| 单位 | `%`，保留 1 位小数 |
| 阈值规则 | reverse：`≥ 95` → 绿；`80 ≤ value < 95` → 黄；`< 80` → 红 |
| col-span | 6 |

#### D-B05 Master Overload/1m

| 属性 | 值 |
|---|---|
| 标题 | Master Overload / 1m |
| 图表类型 | `<Line>` 单系列 |
| Query 类型 | range query |
| PromQL | `increase(ds_master_overload_count_total[1m])` |
| y 轴 | 整数（过载次数） |
| 说明 | Master 资源超载计数；与 B06 联看诊断 Master 健康 |
| col-span | 12 |
| 注意 | `application="master-server"` 隐含在 `ds_master_*` 指标中，无需显式 label filter |

#### D-B06 Master Consume Command/1m

| 属性 | 值 |
|---|---|
| 标题 | Master Consume Command / 1m |
| 图表类型 | `<Line>` 单系列 |
| Query 类型 | range query |
| PromQL | `increase(ds_master_consume_command_count_total[1m])` |
| y 轴 | 整数（消费命令次数） |
| 说明 | Master 每分钟消费调度命令数；Traffic 维度（Master 侧吞吐） |
| col-span | 12 |

#### D-B07 Quartz Job Executed Count/min

| 属性 | 值 |
|---|---|
| 标题 | Quartz Job Executed Count / min |
| 图表类型 | `<Line>` 多系列（3 系列） |
| Query 类型 | range query（multi-range） |
| PromQL 明细 | — |

| seriesLabel | PromQL |
|---|---|
| `total` | `sum(increase(ds_master_quartz_job_executed_total[1m]))` |
| `success` | `increase(ds_master_quartz_job_executed_total{result="success"}[1m])` |
| `failure` | `increase(ds_master_quartz_job_executed_total{result="failure"}[1m])` |

| 属性 | 值 |
|---|---|
| 颜色 | `total` → `#1677ff`、`success` → `#52c41a`、`failure` → `#ff4d4f` |
| col-span | 12 |

#### D-B08 Quartz Job Execution Time

| 属性 | 值 |
|---|---|
| 标题 | Quartz Job Execution Time |
| 图表类型 | `<Line>` 多系列（2 系列） |
| Query 类型 | range query（multi-range） |
| PromQL 明细 | — |

| seriesLabel | PromQL |
|---|---|
| `avg` | `rate(ds_master_quartz_job_execution_time_seconds_sum[1m]) / rate(ds_master_quartz_job_execution_time_seconds_count[1m])` |
| `max` | `ds_master_quartz_job_execution_time_seconds_max` |

| 属性 | 值 |
|---|---|
| y 轴 | 秒（`s`），保留 3 位小数 |
| 颜色 | `avg` → `#1677ff`、`max` → `#faad14` |
| col-span | 12 |

#### D-B09 Task Execution Total & Success/1m

| 属性 | 值 |
|---|---|
| 标题 | Task Execution Total & Success / 1m |
| 图表类型 | `<Line>` 多系列（2 系列） |
| Query 类型 | range query（multi-range） |
| PromQL 明细 | — |

| seriesLabel | PromQL |
|---|---|
| `total` | `sum(increase(ds_task_execution_count_total[1m]))` |
| `success` | `increase(ds_task_execution_count_total{result="success"}[1m])` |

| 属性 | 值 |
|---|---|
| 颜色 | `total` → `#1677ff`、`success` → `#52c41a` |
| y 轴 | 整数（任务数） |
| col-span | 12 |

#### D-B10 Task Execution Time

| 属性 | 值 |
|---|---|
| 标题 | Task Execution Time |
| 图表类型 | `<Line>` 多系列（2 系列） |
| Query 类型 | range query（multi-range） |
| PromQL 明细 | — |

| seriesLabel | PromQL |
|---|---|
| `avg` | `rate(ds_task_execution_duration_seconds_sum[1m]) / rate(ds_task_execution_duration_seconds_count[1m]) * 1000` |
| `max` | `ds_task_execution_duration_seconds_max * 1000` |

| 属性 | 值 |
|---|---|
| y 轴 | 毫秒（`ms`），保留 1 位小数 |
| 颜色 | `avg` → `#1677ff`、`max` → `#faad14` |
| 说明 | avg/max 同框展示，可直观判断单 task 执行耗时是否存在长尾 |
| col-span | 12 |

#### D-B11 Process Instance States/1m

| 属性 | 值 |
|---|---|
| 标题 | Process Instance States / 1m |
| 图表类型 | `<Line>` 多系列（4 系列） |
| Query 类型 | range query（multi-range） |
| PromQL 明细 | — |

| seriesLabel | PromQL |
|---|---|
| `submit` | `sum(increase(ds_workflow_instance_count_total{state="submit"}[1m]))` |
| `success` | `sum(increase(ds_workflow_instance_count_total{state="success"}[1m]))` |
| `fail` | `sum(increase(ds_workflow_instance_count_total{state="fail"}[1m]))` |
| `timeout` | `sum(increase(ds_workflow_instance_count_total{state="timeout"}[1m]))` |

| 属性 | 值 |
|---|---|
| 颜色 | `submit` → `#1677ff`、`success` → `#52c41a`、`fail` → `#ff4d4f`、`timeout` → `#faad14` |
| y 轴 | 整数（流程实例数） |
| 说明 | 四条线对比展示流程实例生命周期状态；Traffic + Errors 核心面板 |
| col-span | 24 |

#### D-B12 Task Dispatch Count/1m

| 属性 | 值 |
|---|---|
| 标题 | Task Dispatch Count / 1m |
| 图表类型 | `<Line>` 多系列（3 系列） |
| Query 类型 | range query（multi-range） |
| PromQL 明细 | — |

| seriesLabel | PromQL |
|---|---|
| `dispatch` | `sum(increase(ds_task_dispatch_count_total[1m]))` |
| `failure` | `sum(increase(ds_task_dispatch_failure_count_total[1m]))` |
| `error` | `sum(increase(ds_task_dispatch_error_count_total[1m]))` |

| 属性 | 值 |
|---|---|
| 颜色 | `dispatch` → `#1677ff`、`failure` → `#ff7a45`、`error` → `#ff4d4f` |
| y 轴 | 整数 |
| 说明 | Errors 维度核心：dispatch 失败和错误应趋近 0 |
| col-span | 12 |

#### D-B13 Task Instance States/1m

| 属性 | 值 |
|---|---|
| 标题 | Task Instance States / 1m |
| 图表类型 | `<Line>` 多系列（4 系列） |
| Query 类型 | range query（multi-range） |
| PromQL 明细 | — |

| seriesLabel | PromQL |
|---|---|
| `submit` | `sum(increase(ds_task_instance_count_total{state="submit"}[1m]))` |
| `success` | `sum(increase(ds_task_instance_count_total{state="success"}[1m]))` |
| `fail` | `sum(increase(ds_task_instance_count_total{state="fail"}[1m]))` |
| `retry` | `sum(increase(ds_task_instance_count_total{state="retry"}[1m]))` |

| 属性 | 值 |
|---|---|
| 颜色 | `submit` → `#1677ff`、`success` → `#52c41a`、`fail` → `#ff4d4f`、`retry` → `#faad14` |
| y 轴 | 整数（task 实例数） |
| col-span | 12 |

---

### 5.3 段 C — 通用 Spring Boot

> 本段所有面板均使用 `application="$application"` 和 `instance="$instance"`，与工具栏选择器联动。

#### D-C01 Uptime

| 属性 | 值 |
|---|---|
| 标题 | Uptime |
| 图表类型 | `<Statistic>` (antd) |
| Query 类型 | instant query |
| PromQL | `process_uptime_seconds{application="$application", instance="$instance"}` |
| 单位 | 秒（`s`），自动换算为 `Xs / Xm / Xh / Xd`（实现时选用 formatDuration） |
| 阈值规则 | 无（蓝色 `#1677ff`） |
| col-span | 8 |

#### D-C02 Heap Used %

| 属性 | 值 |
|---|---|
| 标题 | Heap Used |
| 图表类型 | `<Statistic>` (antd) |
| Query 类型 | instant query |
| PromQL | `sum(jvm_memory_used_bytes{application="$application", instance="$instance", area="heap"}) * 100 / sum(jvm_memory_max_bytes{application="$application", instance="$instance", area="heap"})` |
| 单位 | `%`，保留 1 位小数 |
| 阈值规则 | `< 70` → 绿；`70 ≤ value < 90` → 黄；`≥ 90` → 红 |
| col-span | 8 |

#### D-C03 Non-Heap Used %

| 属性 | 值 |
|---|---|
| 标题 | Non-Heap Used |
| 图表类型 | `<Statistic>` (antd) |
| Query 类型 | instant query |
| PromQL | `sum(jvm_memory_used_bytes{application="$application", instance="$instance", area="nonheap"}) * 100 / sum(jvm_memory_max_bytes{application="$application", instance="$instance", area="nonheap"})` |
| 单位 | `%`，保留 1 位小数 |
| 阈值规则 | `< 70` → 绿；`70 ≤ value < 90` → 黄；`≥ 90` → 红 |
| col-span | 8 |

#### D-C04 HTTP Request Rate

| 属性 | 值 |
|---|---|
| 标题 | HTTP Request Rate |
| 图表类型 | `<Line>` 单系列 |
| Query 类型 | range query |
| PromQL | `sum(rate(http_server_requests_seconds_count{application="$application", instance="$instance"}[1m]))` |
| y 轴 | `ops/s`，保留 2 位小数 |
| 说明 | Traffic 维度：该角色的 HTTP 流量基线 |
| col-span | 8 |

#### D-C05 HTTP 5xx Error Rate

| 属性 | 值 |
|---|---|
| 标题 | HTTP 5xx Error Rate |
| 图表类型 | `<Line>` 单系列 |
| Query 类型 | range query |
| PromQL | `sum(rate(http_server_requests_seconds_count{application="$application", instance="$instance", status=~"5.."}[1m]))` |
| y 轴 | `ops/s`，保留 2 位小数 |
| 系列颜色 | `#ff4d4f`（红，Errors 维度） |
| 说明 | 正常应趋近 0；突刺即告警 |
| col-span | 8 |

#### D-C06 HTTP Duration

| 属性 | 值 |
|---|---|
| 标题 | HTTP Request Duration |
| 图表类型 | `<Line>` 多系列（2 系列） |
| Query 类型 | range query（multi-range） |
| PromQL 明细 | — |

| seriesLabel | PromQL |
|---|---|
| `avg` | `sum(rate(http_server_requests_seconds_sum{application="$application", instance="$instance", status!~"5.."}[1m])) / sum(rate(http_server_requests_seconds_count{application="$application", instance="$instance", status!~"5.."}[1m]))` |
| `max` | `max(http_server_requests_seconds_max{application="$application", instance="$instance", status!~"5.."})` |

| 属性 | 值 |
|---|---|
| y 轴 | 秒（`s`），保留 3 位小数 |
| 颜色 | `avg` → `#1677ff`、`max` → `#faad14` |
| col-span | 8 |

#### D-C07 JVM Heap (bytes)

| 属性 | 值 |
|---|---|
| 标题 | JVM Heap Memory |
| 图表类型 | `<Area>` 多系列（3 系列，非堆叠） |
| Query 类型 | range query（multi-range） |
| PromQL 明细 | — |

| seriesLabel | PromQL |
|---|---|
| `used` | `sum(jvm_memory_used_bytes{application="$application", instance="$instance", area="heap"})` |
| `committed` | `sum(jvm_memory_committed_bytes{application="$application", instance="$instance", area="heap"})` |
| `max` | `sum(jvm_memory_max_bytes{application="$application", instance="$instance", area="heap"})` |

| 属性 | 值 |
|---|---|
| y 轴 | bytes，自动换算（B/KB/MB/GB），`formatBytes` 函数 |
| 颜色 | `used` → `#1677ff`、`committed` → `#faad14`、`max` → `#8c8c8c` |
| 说明 | `max` 作为参考线（灰色），`used` 接近 `max` 时需关注 GC 压力 |
| col-span | 12 |

#### D-C08 JVM Non-Heap (bytes)

| 属性 | 值 |
|---|---|
| 标题 | JVM Non-Heap Memory |
| 图表类型 | `<Area>` 多系列（3 系列，非堆叠） |
| Query 类型 | range query（multi-range） |
| PromQL 明细 | 同 D-C07，将 `area="heap"` 替换为 `area="nonheap"` |
| y 轴 | bytes（formatBytes） |
| col-span | 12 |

#### D-C09 CPU Usage

| 属性 | 值 |
|---|---|
| 标题 | CPU Usage |
| 图表类型 | `<Line>` 多系列（2 系列） |
| Query 类型 | range query（multi-range） |
| PromQL 明细 | — |

| seriesLabel | PromQL |
|---|---|
| `system` | `system_cpu_usage{application="$application", instance="$instance"}` |
| `process` | `process_cpu_usage{application="$application", instance="$instance"}` |

| 属性 | 值 |
|---|---|
| y 轴 | 百分比（percentunit），`(v) => \`${(v * 100).toFixed(1)}%\`` |
| 颜色 | `system` → `#8c8c8c`（灰，背景参考）、`process` → `#1677ff` |
| col-span | 8 |

#### D-C10 System Load

| 属性 | 值 |
|---|---|
| 标题 | System Load |
| 图表类型 | `<Line>` 多系列（2 系列） |
| Query 类型 | range query（multi-range） |
| PromQL 明细 | — |

| seriesLabel | PromQL |
|---|---|
| `load_1m` | `system_load_average_1m{application="$application", instance="$instance"}` |
| `cpu_cores` | `system_cpu_count{application="$application", instance="$instance"}` |

| 属性 | 值 |
|---|---|
| 颜色 | `load_1m` → `#1677ff`、`cpu_cores` → `#8c8c8c`（参考线，理解负载饱和度） |
| y 轴 | 整数 |
| 说明 | `load_1m` 持续超过 `cpu_cores` 时表示 CPU 饱和 |
| col-span | 8 |

#### D-C11 Threads

| 属性 | 值 |
|---|---|
| 标题 | Threads |
| 图表类型 | `<Line>` 多系列（3–6 系列，动态） |
| Query 类型 | range query（multi-range） |
| PromQL 明细 | — |

| seriesLabel | PromQL |
|---|---|
| `live` | `jvm_threads_live_threads{application="$application", instance="$instance"}` |
| `daemon` | `jvm_threads_daemon_threads{application="$application", instance="$instance"}` |
| `peak` | `jvm_threads_peak_threads{application="$application", instance="$instance"}` |
| `tomcat_busy` | `tomcat_threads_busy_threads{application="$application", instance="$instance"}` |
| `tomcat_current` | `tomcat_threads_current_threads{application="$application", instance="$instance"}` |

| 属性 | 值 |
|---|---|
| y 轴 | 整数（线程数） |
| 说明 | tomcat_* 指标只在使用 Tomcat 容器时有值（非 Tomcat 则为空，自动不显示） |
| col-span | 8 |

#### D-C12 Log Events/1m

| 属性 | 值 |
|---|---|
| 标题 | Log Events / 1m |
| 图表类型 | `<Line>` 多系列（by level） |
| Query 类型 | range query |
| PromQL | `increase(logback_events_total{application="$application", instance="$instance"}[1m])` |
| 系列字段 | `level`（INFO/WARN/ERROR/DEBUG 各一条线） |
| 颜色 | ERROR → `#ff4d4f`、WARN → `#faad14`、INFO → `#1677ff`、DEBUG → `#8c8c8c` |
| y 轴 | 整数（事件数/min） |
| 说明 | ERROR/WARN 突增是异常预警信号 |
| col-span | 12 |

#### D-C13 GC Pause Rate

| 属性 | 值 |
|---|---|
| 标题 | GC Pause Rate |
| 图表类型 | `<Line>` 多系列（by cause/action） |
| Query 类型 | range query |
| PromQL | `rate(jvm_gc_pause_seconds_count{application="$application", instance="$instance"}[1m])` |
| 系列字段 | `cause` 或 `action`（由 label 自动区分）|
| y 轴 | `ops/s`（GC 集合速率），保留 2 位小数 |
| 说明 | GC 频率突增 + CPU 高 = 内存压力；Saturation 指标 |
| col-span | 12 |

---

## 6. 主题 / 样式规范

**完全复用 `prometheus-dashboard-prototype-spec.md` §6 中的颜色 Token 和工具函数。**

```ts
// 复用同名常量（来自 utils/formatters.ts）
import { CHART_COLORS, colorByThreshold, formatBytes, formatCompact } from '../utils/formatters';
```

DolphinScheduler 特有颜色补充（process instance / task instance 状态）：

```ts
const dsStateColors = {
  submit:  CHART_COLORS.primary,   // #1677ff 提交中
  success: CHART_COLORS.success,   // #52c41a 成功
  fail:    CHART_COLORS.error,     // #ff4d4f 失败
  timeout: CHART_COLORS.warning,   // #faad14 超时
  retry:   '#fa8c16',              // 橙（重试）
  dispatch:CHART_COLORS.primary,
  failure: '#ff7a45',
  error:   CHART_COLORS.error,
};
```

---

## 7. 数据层接口 TypeScript 定义

**复用 `prometheus-dashboard-prototype-spec.md` §7 中全部接口定义（`PrometheusVector`、`PrometheusMatrix`、`TimeSeriesPoint`、`StatValue`、`TableRow`）。**

DolphinScheduler 特有补充：

```ts
// 看板查询参数（DS 版，无 $interval/$job 变量）
interface DSDashboardQueryParams {
  clusterId: number;
  start: number;      // unix timestamp (seconds)
  end: number;        // unix timestamp (seconds)
  step: number;       // 建议 = (end - start) / 200
  variables: {
    application: string;  // 通用段用，如 "master-server"
    instance: string;     // 正则，如 ".+"
    // 注：Worker/Master 段的 application 在 PromQL 中硬编码，不从此处读取
  };
}
```

---

## 8. 组件树结构

```
<DolphinSchedulerDashboard>           # 页面容器，管理 variables + time range + refresh
  ├── <DashboardToolbar>              # 复用 PrometheusMonitor 同名组件（调整变量：角色▼ + 实例▼）
  │
  ├── <SectionHeader title="SEGMENT A — WORKER (application=worker-server)"/>
  ├── <Row R A-R1>                    # Worker 饱和度
  │   ├── <TimeSeriesPanel D-A01>     # Worker CPU Usage
  │   ├── <TimeSeriesPanel D-A02>     # Submit Queue Full/1m
  │   └── <TimeSeriesPanel D-A03>     # Worker Overload/1m
  ├── <Row A-R2>
  │   ├── <TimeSeriesPanel D-A04>     # Worker Tasks Total
  │   └── <TimeSeriesPanel D-A05>     # Resource Download Count/5m（3 系列）
  ├── <Row A-R3>
  │   └── <TimeSeriesPanel D-A06>     # Resource Download Duration/5m
  │
  ├── <SectionHeader title="SEGMENT B — MASTER (application=master-server)"/>
  ├── <Row B-R1>                      # 概览 Stat
  │   ├── <StatPanel D-B01>           # Task Total Count
  │   ├── <StatPanel D-B02>           # Task Successful Rate（reverse）
  │   ├── <StatPanel D-B03>           # Quartz Job Total Count
  │   └── <StatPanel D-B04>           # Quartz Job Successful Rate（reverse）
  ├── <Row B-R2>
  │   ├── <TimeSeriesPanel D-B05>     # Master Overload/1m
  │   └── <TimeSeriesPanel D-B06>     # Master Consume Command/1m
  ├── <Row B-R3>
  │   ├── <TimeSeriesPanel D-B07>     # Quartz Executed/min（3 系列）
  │   └── <TimeSeriesPanel D-B08>     # Quartz Execution Time（avg/max）
  ├── <Row B-R4>
  │   ├── <TimeSeriesPanel D-B09>     # Task Execution Total & Success/1m
  │   └── <TimeSeriesPanel D-B10>     # Task Execution Time（avg/max ms）
  ├── <Row B-R5>
  │   └── <TimeSeriesPanel D-B11>     # Process Instance States/1m（4 系列，col=24）
  ├── <Row B-R6>
  │   ├── <TimeSeriesPanel D-B12>     # Task Dispatch Count/1m（3 系列）
  │   └── <TimeSeriesPanel D-B13>     # Task Instance States/1m（4 系列）
  │
  ├── <SectionHeader title="SEGMENT C — 通用 Spring Boot ($application)"/>
  ├── <Row C-R1>                      # 运行状态 Stat
  │   ├── <StatPanel D-C01>           # Uptime
  │   ├── <StatPanel D-C02>           # Heap Used%（阈值）
  │   └── <StatPanel D-C03>           # Non-Heap Used%（阈值）
  ├── <Row C-R2>                      # HTTP 请求
  │   ├── <TimeSeriesPanel D-C04>     # HTTP Rate
  │   ├── <TimeSeriesPanel D-C05>     # HTTP 5xx Error Rate
  │   └── <TimeSeriesPanel D-C06>     # HTTP Duration（avg/max）
  ├── <Row C-R3>                      # JVM 内存
  │   ├── <AreaPanel D-C07>           # JVM Heap（bytes area）
  │   └── <AreaPanel D-C08>           # JVM Non-Heap（bytes area）
  ├── <Row C-R4>                      # CPU & 线程
  │   ├── <TimeSeriesPanel D-C09>     # CPU Usage
  │   ├── <TimeSeriesPanel D-C10>     # System Load
  │   └── <TimeSeriesPanel D-C11>     # Threads
  └── <Row C-R5>                      # 日志 & GC
      ├── <TimeSeriesPanel D-C12>     # Log Events/1m（by level）
      └── <TimeSeriesPanel D-C13>     # GC Pause Rate

# 复用的基础组件（来自 PrometheusMonitor/panels/）
StatPanel / TimeSeriesPanel / AreaPanel / DashboardToolbar / usePrometheusDashboard
```

---

## 9. 实现说明（供 Phase 3 编码参考）

### 9.1 文件路径

```
datasophon-ui-v2/src/pages/DolphinSchedulerMonitor/
  ├── index.tsx                 # 页面容器（三段式布局）
  ├── panelQueries.ts           # PanelDef（31 个面板的 instant/range/multi-range 定义）
  ├── hooks/
  │   └── useDSMonitorDashboard.ts   # 复用 usePrometheusDashboard 模式
  ├── panels/                   # 复用 PrometheusMonitor/panels/ 的 4 个组件，无需复制
  ├── toolbar/
  │   └── DSDashboardToolbar.tsx    # 改 DashboardToolbar（去掉 Job/Interval，增加 Application 选项）
  ├── mock/
  │   └── dsMockData.ts         # 确定性伪随机静态数据
  └── utils/                    # 直接复用 PrometheusMonitor/utils/
```

### 9.2 PromQL 变量替换规则（DS 版）

```ts
function replaceDSVars(
  promql: string,
  vars: DSDashboardQueryParams['variables'],
): string {
  return promql
    .replace(/\$application/g, vars.application || 'master-server')
    .replace(/\$instance/g,   vars.instance    || '.+');
  // 注意：Worker/Master 段的 PromQL 不含 $application，无需替换
}
```

### 9.3 SectionHeader 组件

段与段之间用分隔标题区分，传达硬编码 application 值：

```tsx
const SectionHeader: React.FC<{ title: string; subtitle?: string }> = ({ title, subtitle }) => (
  <div style={{ borderLeft: '4px solid #1677ff', padding: '4px 12px', marginBottom: 12, marginTop: 24 }}>
    <Typography.Text strong>{title}</Typography.Text>
    {subtitle && <Typography.Text type="secondary" style={{ marginLeft: 8 }}>{subtitle}</Typography.Text>}
  </div>
);

// 使用示例：
<SectionHeader title="WORKER" subtitle='application="worker-server"（固定，不受上方工具栏影响）' />
```

### 9.4 多系列面板（multi-range）实现模式

与 Prometheus spec §9.3 相同，对 D-B07/D-B09/D-B11/D-B12/D-B13 等多 PromQL 面板：

```ts
// 以 D-B11 Process Instance States 为例
const PROCESS_INSTANCE_QUERIES = [
  { label: 'submit',  promql: 'sum(increase(ds_workflow_instance_count_total{state="submit"}[1m]))' },
  { label: 'success', promql: 'sum(increase(ds_workflow_instance_count_total{state="success"}[1m]))' },
  { label: 'fail',    promql: 'sum(increase(ds_workflow_instance_count_total{state="fail"}[1m]))' },
  { label: 'timeout', promql: 'sum(increase(ds_workflow_instance_count_total{state="timeout"}[1m]))' },
];
```

### 9.5 Mock 数据要求

`dsMockData.ts` 覆盖 31 个面板：

**Worker 段（段 A）：**
- A01 Worker CPU: 约 30–60%（`process_cpu_usage` → 0.3–0.6），偶有突刺
- A02 Submit Queue: 正常为 0，偶发 1–3（轻微黄色压力）
- A03 Overload: 正常为 0
- A04 Worker Tasks: 5–20 个任务
- A05 Resource Download: success 约 10/5m，fail 约 0-1/5m
- A06 Duration: 0.5–2.5 秒

**Master 段（段 B）：**
- B01 Task Total Count: 12500（累计 instant）
- B02 Task Success Rate: 96.5%（绿色）
- B03 Job Total Count: 8750（累计 instant）
- B04 Job Success Rate: 99.1%（绿色）
- B05-B06 时序：正常稳定值，无过载
- B07 Quartz Count/min: total 约 5/min，failure=0
- B11 Process Instance States: submit 5/min，success 4/min，fail 0，timeout 偶发 1

**通用段（段 C）：**
- C02 Heap%: 65%（黄绿临界）、偶有 GC 后回落
- C05 HTTP 5xx: 正常为 0，偶发 0.01 ops/s 验证红色
- C07 JVM Heap: used ~200MB，committed ~350MB，max ~512MB（随时间缓慢上升）

---

## 10. Dev 环境配置注意事项

**完全复用 `prometheus-dashboard-prototype-spec.md` §10 中的三项配置（publicPath、proxy bypass、mock 路径对齐）。**

DS 看板额外注意：

- mock 路径与 PrometheusMonitor 完全相同（`/ddh/api/v2/prometheus/query` 和 `/query_range`），**无需新增 mock 端点**
- 工具栏的 `$application` 下拉是静态选项列表，不需要 `label_values` API 调用；但 `$instance` 选项需要调用 instant query 动态获取

---

## 11. 验收标准

Phase 2 原型（mock 阶段）完成后，需满足：

- [ ] 三段式布局正确呈现，每段有 SectionHeader（含硬编码 application 提示）
- [ ] 段 A 的 6 个面板全部渲染，PromQL 内 `application="worker-server"` 硬编码
- [ ] 段 B 的 4 个 Stat 面板（B01-B04）使用 antd `<Statistic>`；B02/B04 的成功率使用 `reverse=true` 阈值
- [ ] 段 B 的 9 个时序面板（B05-B13）多系列颜色符合 `dsStateColors` 定义
- [ ] 段 C 的工具栏 `$application` 下拉更改后，段 C 所有面板数据同步刷新，段 A/B 面板数据**不变**
- [ ] 段 C 的 JVM Heap/Non-Heap 面板使用 `<Area>` 组件 + `formatBytes` y 轴格式化
- [ ] Log Events 面板按 `level` 系列展示，ERROR → 红色，WARN → 黄色
- [ ] `colorByThreshold` reverse 方向在 B02/B04/C02/C03 处行为正确
- [ ] 在 1280px 宽度下三段布局无横向滚动条
- [ ] golden signals 四象限（延迟/流量/错误/饱和度）均有对应面板，验收人员能从看板判断集群健康状态

---

## 12. 联调踩坑记录（Phase 3 实现时必读）

本章引用 `prometheus-dashboard-prototype-spec.md` §12 中记录的三个踩坑案例——这些经验适用于 DolphinScheduler 看板的同类场景：

| 踩坑编号 | 标题 | 适用于 DS 的场景 |
|---|---|---|
| 12.1 | `service.ts` 请求路径双前缀 | D-C04~D-C06 HTTP 面板的 service.ts 路径写法 |
| 12.2 | PromQL `+` 运算符被后端解码为空格 | D-C02/D-C03 的 `*100/` 和 D-C06 的 avg 公式含 `/` 运算符，需确认后端正确编码 |
| 12.3 | `@ant-design/plots` 时间轴切换不更新 | 所有 TimeSeriesPanel/AreaPanel 均需 `chartKey` prop（`data[0].time-data[-1].time`） |

> 详见：`docs/monitoring/design/prometheus-dashboard-prototype-spec.md` §12

**DS 特有的一个额外注意**：段 A 和段 B 的 PromQL 中 **`application` 为硬编码字符串**，Phase 3 实现时禁止将其动态替换为 `$application` 变量。实现时建议在 `panelQueries.ts` 中对这些面板的 PromQL 直接写死，并加注释说明不走 `replaceVars()`，避免 code review 时被"修正"。
