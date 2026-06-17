# HDFS 监控看板原型设计 Spec

> **文档用途**：供 Claude design 阅读，根据本 spec 设计 React + AntV G2 看板原型，并最终生成可运行的组件代码。
> **组件**：Apache HDFS 3.5.0
> **数据源**：原生 `/prom`（PrometheusMetricsSink，HADOOP-16398）。NameNode Web 端口默认 `9870`，DataNode 默认 `9864`，与各 Daemon Web UI 同端口。
> **参考 Grafana 看板**：[HDFS - DataNode](https://grafana.com/grafana/dashboards/23175) (ID 23175)
> **Panel Catalog 路径**：`docs/monitoring/panel-catalog/HDFS.json`（152 个面板，**全部为 DataNode**）
> **Phase**：Phase 2 —— 原型设计阶段

---

## 1. 架构约束

```
React(AntV G2)
  └──HTTP──> datasophon-api /api/v2/prometheus/query_range
               └──PromQL──> Prometheus :9090
                              ├──scrape──> NameNode /prom:9870   (Hadoop_NameNode_*)
                              └──scrape──> DataNode /prom:9864   (Hadoop_DataNode_*)
```

**前端不直连 Prometheus**。所有 PromQL 通过后端代理端点转发（详见 `prometheus-dashboard-prototype-spec.md` §1）。

### 1.1 HDFS 指标特点 & 本看板的关键设计决策

- **JMX → Prometheus 命名**：HDFS 原生 `/prom` 端点把 JMX MBean 转成 `Hadoop_<Service>_<Metric>` 形式（如 `Hadoop_DataNode_BytesRead`、`Hadoop_NameNode_CapacityUsed`），带 `{namespace, instance}` 标签。
- **`*AvgTime` 是 Gauge、`*NumOps` 是 Counter**：JMX MutableRate 拆成一对指标——`XxxAvgTime`（近窗平均耗时，Gauge，直读）+ `XxxNumOps`（累计次数，Counter，用 `rate`）。延迟面板读 `AvgTime`，吞吐面板对 `NumOps` 取 `rate`。
- **⚠️ catalog 全是 DataNode，集群健康指标缺失**：参考看板 ID 23175 的 152 个面板**全部是 `Hadoop_DataNode_*`**，且大量是 RamDisk / EC（纠删码）/ 细分 FileIoRate 长尾。**运维最关心的集群级健康（容量水位、缺失块、坏块、存活/死亡 DataNode 数）全部在 NameNode 端，catalog 一个都没有**。
- **本看板的核心设计动作 = 双向裁剪**：
  1. **DataNode 做减法**：152 → 精选 10 个黄金信号面板（块操作延迟/吞吐/RPC/堆/GC/错误），砍掉 RamDisk/EC/细分 IoRate 长尾（保留在 catalog 供深挖）。
  2. **NameNode 做加法 ★**：基于 `Hadoop_NameNode_*` / `FSNamesystem` 原生指标补一整个集群健康区（容量、块健康、DataNode 存活、RPC）。这些指标在同源 `:9870/prom` 端点可取，但 catalog 未含。**落地时需在目标 NameNode 实测 `:9870/prom` 核对指标名**（见 §9.5）。

---

## 2. 图表类型映射字典

**完全复用 `prometheus-dashboard-prototype-spec.md` §2 中的映射字典**。

HDFS 特有补充：

| 特征 | 映射组件 | 备注 |
|---|---|---|
| `*AvgTime`（Gauge，ms） | `<Line>` + ms 轴 | 直读，**不** rate |
| `*NumOps`（Counter） | `<Line>` + `rate` | 块操作/RPC 次数 → ops/s |
| 容量（Total/Used/Remaining） | `<Area>` 堆叠 + `formatBytes` | NameNode 集群容量 |
| 健康计数（Missing/Corrupt/Dead） | `<Statistic>` + `colorByThreshold` | 0 绿、≥1 红 |
| Heap（Used/Max，单位 MB） | `<Line>` + `formatBytes`（**值 ×1024×1024**） | catalog 的 `MemHeapUsedM` 单位是 MB |

---

## 3. 变量 / 过滤器规范

| 变量 | PromQL 占位符 | 取值来源 | 默认值 | 说明 |
|---|---|---|---|---|
| 命名空间 | `$Namespace` | `label_values(Hadoop_NameNode_CapacityTotal, namespace)` | 第一个 | 单选，HDFS federation namespace |
| 实例 | `$Instance` | `label_values(Hadoop_DataNode_BytesRead, instance)` | `.+`（全选） | 多选下拉，HDFS 节点（NN/DN 主机） |
| 时间范围 | — | 时间选择器 | `Last 1h` | 5m/15m/1h/6h/24h/7d |
| 刷新间隔 | — | — | `30s` | 自动轮询 |

> **NN/DN 共用 `$Instance`**：指标名前缀（`Hadoop_NameNode_*` vs `Hadoop_DataNode_*`）天然区分角色，NameNode 指标只在 NN 主机存在、DataNode 指标只在 DN 主机存在，因此单一 `$Instance` 正则即可（NameNode 区面板自然只命中 NN 主机）。
> 速率窗口固定 `[5m]`（DataNode AvgTime 已是近窗平均，NumOps rate 用 5m 平滑），不暴露 Interval 下拉。

---

## 4. 看板布局（24 列 Grid）

裁剪策略：**NameNode 集群健康区（★ 补强，8 面板）+ DataNode 黄金信号区（catalog 精选，10 面板）= 18 面板**。

```
┌──────────────────────────────────────────────────────────────────────┐
│  TOOLBAR: [Namespace▼]  [实例▼]    [Last 1h▼]   [🔄 30s▼]          │
└──────────────────────────────────────────────────────────────────────┘

═══════════ NameNode 集群健康 ★（基于 Hadoop_NameNode_*，catalog 未含）═══════════

行 R1 — 集群健康 Stat（高度 80px，col=4 ×6）
┌────────┬────────┬────────┬────────┬────────┬────────┐
│Capacity│Live    │Dead    │Missing │Corrupt │Under-  │
│Used %  │DataNode│DataNode│Blocks  │Blocks  │Repl Blk│
│ H01    │ H02    │ H03    │ H04    │ H05    │ H06    │
└────────┴────────┴────────┴────────┴────────┴────────┘

行 R2 — 集群容量 & NameNode RPC（高度 200px）
┌────────────────────────────┬────────────────────────────┐
│ H07 Cluster Capacity       │ H08 NameNode RPC           │
│ [Area Used/Remaining]      │ [Line queue/proc + queueLen]│
└────────────────────────────┴────────────────────────────┘

═══════════════════════ DataNode（catalog 精选）═══════════════════════

行 R3 — 块操作延迟 Latency（高度 200px，col=8 ×3）
┌──────────────────┬──────────────────┬──────────────────┐
│ H09 Block Op     │ H10 DataNode RPC │ H11 Heartbeat    │
│ Latency          │ Latency          │ AvgTime          │
└──────────────────┴──────────────────┴──────────────────┘

行 R4 — 流量 Traffic（高度 200px，col=8 ×3）
┌──────────────────┬──────────────────┬──────────────────┐
│ H12 DataNode     │ H13 Network      │ H14 Block Ops    │
│ Read/Write Bytes │ Recv/Sent        │ Rate (NumOps)    │
└──────────────────┴──────────────────┴──────────────────┘

行 R5 — 饱和度 Saturation（高度 200px，col=8 ×3）
┌──────────────────┬──────────────────┬──────────────────┐
│ H15 Heap Usage   │ H16 GC           │ H17 Threads &    │
│ (Used/Max)       │ (Count/TimeMs)   │ Connections      │
└──────────────────┴──────────────────┴──────────────────┘

行 R6 — 错误 Errors（高度 200px）
┌─────────────────────────────────────────────────────────┐
│ H18 DataNode Errors（VolumeFailures/BlockVerification/   │
│      FileIoErrors/LogError）  col=24                     │
└─────────────────────────────────────────────────────────┘
```

---

## 5. 面板规格（逐面板）

### 5.0 Golden Signals 映射

| 维度 | 面板 | 说明 |
|---|---|---|
| **Latency（延迟）** | H08 NN RPC、H09 Block Op、H10 DN RPC、H11 Heartbeat | NameNode/DataNode RPC + 块操作 + 心跳耗时 |
| **Traffic（流量）** | H12 Read/Write Bytes、H13 Network、H14 Block Ops Rate | 数据读写吞吐与块操作速率 |
| **Errors（错误）** | H03 Dead DN、H04 Missing、H05 Corrupt、H06 Under-Repl（NN 级）、H18 DataNode Errors | 集群块健康 + DataNode 卷/校验/IO 错误 |
| **Saturation（饱和度）** | H01 Capacity %、H07 Capacity、H15 Heap、H16 GC、H17 Threads | 容量水位、堆、GC、线程/连接 |

> HDFS 的 Error 维度尤其重要：H04/H05 是数据**已丢失/损坏**的硬告警，H03 是节点失联，H06 是副本不足风险——这些都在补强的 NameNode 区。

---

### 5.1 R1 — NameNode 集群健康 Stat ★

> ★ 本行全部为补强面板，基于 `Hadoop_NameNode_*`（FSNamesystem）。catalog 未含，落地需按 §9.5 在 `:9870/prom` 核对指标名。

#### H01 Capacity Used %

| 属性 | 值 |
|---|---|
| 标题 | Capacity Used % |
| 图表类型 | `<Statistic>` + `colorByThreshold` |
| Query 类型 | instant query |
| PromQL | `Hadoop_NameNode_CapacityUsed{namespace="$Namespace"} / Hadoop_NameNode_CapacityTotal{namespace="$Namespace"} * 100` |
| 单位 | `%` |
| 阈值 | `< 75` → 绿；`75–90` → 橙；`≥ 90` → 红 |

#### H02 Live DataNodes

| 属性 | 值 |
|---|---|
| 标题 | Live DataNodes |
| 图表类型 | `<Statistic>` |
| PromQL | `Hadoop_NameNode_NumLiveDataNodes{namespace="$Namespace"}` |
| 样式 | 大字体，绿色 |

#### H03 Dead DataNodes

| 属性 | 值 |
|---|---|
| 标题 | Dead DataNodes |
| 图表类型 | `<Statistic>` + `colorByThreshold` |
| PromQL | `Hadoop_NameNode_NumDeadDataNodes{namespace="$Namespace"}` |
| 阈值 | `= 0` → 绿；`≥ 1` → 红 |

#### H04 Missing Blocks

| 属性 | 值 |
|---|---|
| 标题 | Missing Blocks |
| 图表类型 | `<Statistic>` + `colorByThreshold` |
| PromQL | `Hadoop_NameNode_MissingBlocks{namespace="$Namespace"}` |
| 阈值 | `= 0` → 绿；`≥ 1` → 红（数据已丢失，最高级告警） |

#### H05 Corrupt Blocks

| 属性 | 值 |
|---|---|
| 标题 | Corrupt Blocks |
| 图表类型 | `<Statistic>` + `colorByThreshold` |
| PromQL | `Hadoop_NameNode_CorruptBlocks{namespace="$Namespace"}` |
| 阈值 | `= 0` → 绿；`≥ 1` → 红 |

#### H06 Under-Replicated Blocks

| 属性 | 值 |
|---|---|
| 标题 | Under-Replicated Blocks |
| 图表类型 | `<Statistic>` + `colorByThreshold` |
| PromQL | `Hadoop_NameNode_UnderReplicatedBlocks{namespace="$Namespace"}` |
| 阈值 | `= 0` → 绿；`> 0` → 橙（副本不足，正在恢复或有风险） |

---

### 5.2 R2 — 集群容量 & NameNode RPC ★

#### H07 Cluster Capacity

| 属性 | 值 |
|---|---|
| 标题 | Cluster Capacity |
| 图表类型 | `<Area>` 堆叠 2 系列 |
| Query 类型 | range query |
| PromQL (used) | `Hadoop_NameNode_CapacityUsed{namespace="$Namespace"}` |
| PromQL (remaining) | `Hadoop_NameNode_CapacityRemaining{namespace="$Namespace"}` |
| y 轴 | bytes，`formatBytes` |
| 系列 | `Used`（蓝）、`Remaining`（绿）；堆叠后总高 = CapacityTotal |

#### H08 NameNode RPC

| 属性 | 值 |
|---|---|
| 标题 | NameNode RPC |
| 图表类型 | `<Line>` 3 系列（双 y 轴：ms 左 / 队列长度右） |
| Query 类型 | range query |
| PromQL (queue time) | `Hadoop_NameNode_RpcQueueTimeAvgTime{namespace="$Namespace", instance=~"$Instance"}` |
| PromQL (proc time) | `Hadoop_NameNode_RpcProcessingTimeAvgTime{namespace="$Namespace", instance=~"$Instance"}` |
| PromQL (call queue len) | `Hadoop_NameNode_CallQueueLength{namespace="$Namespace", instance=~"$Instance"}` |
| y 轴 | 左：`ms`（queue/proc time）；右：整数（CallQueueLength） |
| 系列 | `Queue Time`（橙）、`Processing Time`（蓝）、`Call Queue Length`（灰，右轴） |

---

### 5.3 R3 — DataNode 块操作延迟（catalog）

#### H09 Block Op Latency

| 属性 | 值 |
|---|---|
| 标题 | Block Op Latency |
| 图表类型 | `<Line>` 2 系列 |
| Query 类型 | range query（AvgTime Gauge 直读） |
| PromQL (read) | `Hadoop_DataNode_ReadBlockOpAvgTime{namespace="$Namespace", instance=~"$Instance"}` |
| PromQL (write) | `Hadoop_DataNode_WriteBlockOpAvgTime{namespace="$Namespace", instance=~"$Instance"}` |
| y 轴 | `ms` |
| 系列 | `Read`（绿）、`Write`（蓝） |

#### H10 DataNode RPC Latency

| 属性 | 值 |
|---|---|
| 标题 | DataNode RPC Latency |
| 图表类型 | `<Line>` 2 系列 |
| PromQL (queue) | `Hadoop_DataNode_RpcQueueTimeAvgTime{namespace="$Namespace", instance=~"$Instance"}` |
| PromQL (lock wait) | `Hadoop_DataNode_RpcLockWaitTimeAvgTime{namespace="$Namespace", instance=~"$Instance"}` |
| y 轴 | `ms` |
| 系列 | `Queue Time`（橙）、`Lock Wait Time`（红） |

#### H11 Heartbeat AvgTime

| 属性 | 值 |
|---|---|
| 标题 | Heartbeat AvgTime |
| 图表类型 | `<Line>` by instance |
| PromQL | `Hadoop_DataNode_HeartbeatsAvgTime{namespace="$Namespace", instance=~"$Instance"}` |
| y 轴 | `ms` |
| 警戒线 | y=多数心跳应 < 30ms；持续偏高表示 DN→NN 通信受阻 |

---

### 5.4 R4 — DataNode 流量（catalog）

#### H12 DataNode Read/Write Bytes

| 属性 | 值 |
|---|---|
| 标题 | DataNode Read/Write Bytes |
| 图表类型 | `<Area>` 2 系列 |
| Query 类型 | range query |
| PromQL (read) | `sum(rate(Hadoop_DataNode_BytesRead{namespace="$Namespace", instance=~"$Instance"}[5m])) by (instance)` |
| PromQL (write) | `sum(rate(Hadoop_DataNode_BytesWritten{namespace="$Namespace", instance=~"$Instance"}[5m])) by (instance)` |
| y 轴 | bytes/s，`formatBytes` |
| 系列 | `Read`（绿）、`Write`（蓝） |

#### H13 Network (Recv/Sent)

| 属性 | 值 |
|---|---|
| 标题 | DataNode Network |
| 图表类型 | `<Area>` 2 系列 |
| PromQL (recv) | `sum(rate(Hadoop_DataNode_ReceivedBytes{namespace="$Namespace", instance=~"$Instance"}[5m])) by (instance)` |
| PromQL (sent) | `sum(rate(Hadoop_DataNode_SentBytes{namespace="$Namespace", instance=~"$Instance"}[5m])) by (instance)` |
| y 轴 | bytes/s，`formatBytes` |
| 系列 | `Received`（绿）、`Sent`（蓝） |

#### H14 Block Ops Rate

| 属性 | 值 |
|---|---|
| 标题 | Block Ops Rate |
| 图表类型 | `<Line>` 2 系列 |
| PromQL (read ops) | `sum(rate(Hadoop_DataNode_ReadBlockOpNumOps{namespace="$Namespace", instance=~"$Instance"}[5m])) by (instance)` |
| PromQL (write ops) | `sum(rate(Hadoop_DataNode_WriteBlockOpNumOps{namespace="$Namespace", instance=~"$Instance"}[5m])) by (instance)` |
| y 轴 | `ops/s` |
| 系列 | `Read Ops`（绿）、`Write Ops`（蓝） |

---

### 5.5 R5 — DataNode 饱和度（catalog）

#### H15 Heap Usage

| 属性 | 值 |
|---|---|
| 标题 | DataNode Heap Usage |
| 图表类型 | `<Line>` 2 系列 |
| Query 类型 | range query |
| PromQL (used) | `Hadoop_DataNode_MemHeapUsedM{namespace="$Namespace", instance=~"$Instance"} * 1024 * 1024` |
| PromQL (max) | `Hadoop_DataNode_MemHeapMaxM{namespace="$Namespace", instance=~"$Instance"} * 1024 * 1024` |
| y 轴 | bytes，`formatBytes`（**catalog 单位是 MB，需 ×1024×1024 转 bytes**） |
| 系列 | `Used`（蓝）、`Max`（红虚线，上限） |

#### H16 GC

| 属性 | 值 |
|---|---|
| 标题 | DataNode GC |
| 图表类型 | `<Line>` 双 y 轴 |
| PromQL (count) | `sum(rate(Hadoop_DataNode_GcCount{namespace="$Namespace", instance=~"$Instance"}[5m])) by (instance)` |
| PromQL (time) | `sum(rate(Hadoop_DataNode_GcTimeMillis{namespace="$Namespace", instance=~"$Instance"}[5m])) by (instance)` |
| y 轴 | 左：`ops/s`（GcCount rate）；右：`ms/s`（GcTimeMillis rate，GC 占用时间比例） |
| 系列 | `GC Rate`（蓝）、`GC Time`（橙，右轴） |

#### H17 Threads & Connections

| 属性 | 值 |
|---|---|
| 标题 | Threads & Connections |
| 图表类型 | `<Line>` 3 系列 |
| PromQL (blocked) | `Hadoop_DataNode_ThreadsBlocked{namespace="$Namespace", instance=~"$Instance"}` |
| PromQL (open conn) | `Hadoop_DataNode_NumOpenConnections{namespace="$Namespace", instance=~"$Instance"}` |
| PromQL (call queue) | `Hadoop_DataNode_CallQueueLength{namespace="$Namespace", instance=~"$Instance"}` |
| 系列 | `Threads Blocked`（红）、`Open Connections`（蓝）、`Call Queue Length`（橙） |

---

### 5.6 R6 — DataNode 错误（catalog）

#### H18 DataNode Errors

| 属性 | 值 |
|---|---|
| 标题 | DataNode Errors |
| 图表类型 | `<Line>` 多系列 |
| Query 类型 | range query |
| PromQL (volume failures) | `sum(Hadoop_DataNode_VolumeFailures{namespace="$Namespace", instance=~"$Instance"}) by (instance)` |
| PromQL (block verification fail) | `sum(rate(Hadoop_DataNode_BlockVerificationFailures{namespace="$Namespace", instance=~"$Instance"}[5m])) by (instance)` |
| PromQL (file io errors) | `sum(rate(Hadoop_DataNode_TotalFileIoErrors{namespace="$Namespace", instance=~"$Instance"}[5m])) by (instance)` |
| PromQL (log error) | `sum(rate(Hadoop_DataNode_LogError{namespace="$Namespace", instance=~"$Instance"}[5m])) by (instance)` |
| 系列 | `Volume Failures`（红）、`Block Verification Failures`（橙红）、`File IO Errors`（橙）、`Log Errors`（黄） |
| 说明 | 4 类 DataNode 故障合一；任一 > 0 即需关注，VolumeFailures 表示磁盘卷损坏 |

---

## 6. 主题 / 样式规范

**完全复用 `prometheus-dashboard-prototype-spec.md` §6 中的颜色 Token 和工具函数**（含 `formatBytes` / `colorByThreshold`）。

```ts
import { CHART_COLORS, colorByThreshold, formatBytes } from '../utils/formatters';
```

HDFS 特有补充：

```ts
// MB → bytes（catalog 的 MemHeap*M / Mem*M 单位是 MB）
const MB = 1024 * 1024;

// 健康计数阈值（0 绿、≥1 红），用于 H03/H04/H05
function healthCountColor(value: number): string {
  return value >= 1 ? CHART_COLORS.error : CHART_COLORS.success;
}
```

---

## 7. 数据层接口 TypeScript 定义

**复用 `prometheus-dashboard-prototype-spec.md` §7 中全部接口定义**。

HDFS 特有补充：

```ts
interface HDFSDashboardQueryParams {
  clusterId: number;
  start: number;
  end: number;
  step: number;
  variables: {
    Namespace: string;  // 单选，HDFS federation namespace
    Instance: string;   // 正则，NN/DN 主机，全选 ".+"
    // 注：固定 [5m] 窗口，无 interval 变量
  };
}
```

---

## 8. 组件树结构

```
<HDFSDashboard>                       # 页面容器，管理 Namespace + Instance + time range + refresh
  ├── <DashboardToolbar>              # Namespace 单选 + Instance 多选 + 时间范围 + 刷新
  │
  ├── <Row R1>                        # NameNode 集群健康 Stat ★（6 个）
  │   ├── <StatPanel H01>             # Capacity Used %（阈值）
  │   ├── <StatPanel H02>             # Live DataNodes
  │   ├── <StatPanel H03>             # Dead DataNodes（≥1 红）
  │   ├── <StatPanel H04>             # Missing Blocks（≥1 红）
  │   ├── <StatPanel H05>             # Corrupt Blocks（≥1 红）
  │   └── <StatPanel H06>             # Under-Replicated Blocks（>0 橙）
  │
  ├── <Row R2>                        # 集群容量 & NN RPC ★
  │   ├── <AreaPanel H07>             # Cluster Capacity（Used/Remaining 堆叠）
  │   └── <TimeSeriesPanel H08>       # NameNode RPC（双 y 轴）
  │
  ├── <Row R3>                        # DataNode 块操作延迟
  │   ├── <TimeSeriesPanel H09>       # Block Op Latency
  │   ├── <TimeSeriesPanel H10>       # DataNode RPC Latency
  │   └── <TimeSeriesPanel H11>       # Heartbeat AvgTime
  │
  ├── <Row R4>                        # DataNode 流量
  │   ├── <AreaPanel H12>             # Read/Write Bytes
  │   ├── <AreaPanel H13>             # Network Recv/Sent
  │   └── <TimeSeriesPanel H14>       # Block Ops Rate
  │
  ├── <Row R5>                        # DataNode 饱和度
  │   ├── <TimeSeriesPanel H15>       # Heap Usage（MB→bytes）
  │   ├── <TimeSeriesPanel H16>       # GC（双 y 轴）
  │   └── <TimeSeriesPanel H17>       # Threads & Connections
  │
  └── <Row R6>                        # DataNode 错误
      └── <TimeSeriesPanel H18>       # DataNode Errors（4 系列）

# 复用的基础组件（来自 `monitor/_shared/panels/`）
StatPanel / TimeSeriesPanel / AreaPanel / DashboardToolbar / useDashboardData ← 均来自 `monitor/_shared/`
```

---

## 9. 实现说明（供 Phase 3 编码参考）

### 9.1 文件路径

```
datasophon-ui-v2/src/pages/HDFSMonitor/
  ├── index.tsx                     # 页面容器（6 行布局）
  ├── panelQueries.ts               # PanelDef（18 个面板，NN 区 H01-H08 标注 ★ 待核对）
  ├── hooks/
  │   └── useHDFSDashboard.ts       # 调用 `useDashboardData`（`_shared/useDashboardData.ts`）
  ├── panels/                       # 引用 `monitor/_shared/panels/`
  ├── toolbar/
  │   └── HDFSDashboardToolbar.tsx  # Namespace 单选 + Instance 多选
  ├── mock/
  │   └── hdfsMockData.ts
  └── utils/                        
```

### 9.2 PromQL 变量替换规则（HDFS 版）

```ts
function replaceHDFSVars(promql: string, vars: HDFSDashboardQueryParams['variables']): string {
  return promql
    .replace(/\$Namespace/g, vars.Namespace || '.+')
    .replace(/\$Instance/g,  vars.Instance  || '.+');
  // 注：固定 [5m] 窗口，不替换
}
```

### 9.3 单位换算注意（MB → bytes）

catalog 中 `MemHeapUsedM` / `MemHeapMaxM` / `MemNonHeapUsedM` 等 `*M` 后缀指标单位是 **MB**。H15 等堆面板的 PromQL 末尾 `* 1024 * 1024` 转 bytes，再交给 `formatBytes` 渲染，否则 y 轴会比真实值小 6 个数量级。

### 9.4 Mock 数据要求

`hdfsMockData.ts` 覆盖全部 18 个面板：

**NameNode Stat（H01-H06）：** Capacity Used `62%`（绿）、Live DataNodes `8`、Dead DataNodes `0`（绿）、Missing Blocks `0`（绿）、Corrupt Blocks `0`（绿）、Under-Replicated `0`（绿；偶发 `12` 验证橙色）。

**Range（H07-H18）：**
- H07 Capacity: Used ≈ 60 TB、Remaining ≈ 36 TB（堆叠总 96 TB）
- H08 NN RPC: queue ≈ 2ms、proc ≈ 5ms、call queue len ≈ 0–3
- H09 Block Op: read ≈ 3ms、write ≈ 8ms
- H10 DN RPC: queue ≈ 1ms、lock wait ≈ 0.5ms
- H11 Heartbeat: ≈ 5–15ms
- H12 Read/Write Bytes: read ≈ 200 MB/s、write ≈ 80 MB/s
- H13 Network: recv ≈ 90 MB/s、sent ≈ 220 MB/s
- H14 Block Ops Rate: read ≈ 150 ops/s、write ≈ 40 ops/s
- H15 Heap: used ≈ 2.4 GB、max 4 GB（锯齿）
- H16 GC: rate ≈ 0.3 ops/s、time ≈ 20 ms/s
- H17 Threads & Conn: blocked ≈ 0、open conn ≈ 45、call queue ≈ 0–2
- H18 Errors: 全部 0（偶发 1 个 VolumeFailures 突刺验证红色）

### 9.5 ★ NameNode 区落地核对清单（重要）

H01-H08 基于 `Hadoop_NameNode_*` / FSNamesystem，catalog（纯 DataNode）未含。落地接入真实集群前，在目标 NameNode `:9870/prom` 端点核对以下指标名是否存在、标签是否为 `{namespace,instance}`：

- `Hadoop_NameNode_CapacityTotal` / `CapacityUsed` / `CapacityRemaining`
- `Hadoop_NameNode_NumLiveDataNodes` / `NumDeadDataNodes`
- `Hadoop_NameNode_MissingBlocks` / `CorruptBlocks` / `UnderReplicatedBlocks`
- `Hadoop_NameNode_RpcQueueTimeAvgTime` / `RpcProcessingTimeAvgTime` / `CallQueueLength`

> HDFS 3.5.0 的 FSNamesystem/RPC JMX 字段名稳定，但不同发行版可能有 `Hadoop_NameNode_FSNamesystem_*` 等变体。若实测命名有差异，按实际名修正 PanelDef（仅 H01-H08）。DataNode 区（H09-H18）来自 catalog 实测命名，无需核对。

---

## 10. Dev 环境配置注意事项

**完全复用 `prometheus-dashboard-prototype-spec.md` §10**（publicPath、proxy bypass、mock 路径对齐）。后端代理端点与 PrometheusMonitor 相同。

---

## 11. 验收标准

Phase 2 原型（mock 阶段）完成后，需满足：

- [ ] 全部 18 个面板（H01-H18）按 §4 布局渲染（6 行 24 列 Grid）
- [ ] 看板分两区：NameNode 集群健康（H01-H08，★ 补强）+ DataNode 黄金信号（H09-H18，catalog 精选），有视觉分隔标题
- [ ] R1 行 6 个 Stat：H01 容量阈值（75/90）；H03/H04/H05 健康计数 ≥1 红；H06 >0 橙
- [ ] H07 Cluster Capacity 用 `<Area>` 堆叠（Used+Remaining）+ `formatBytes`
- [ ] H15 Heap 的 PromQL 已 `× 1024 × 1024`（MB→bytes），y 轴量级正确
- [ ] H09/H10/H11 延迟面板读 `*AvgTime`（Gauge 直读，**不** rate）；H14 块操作读 `*NumOps`（用 rate）
- [ ] H18 DataNode Errors 含 4 系列（VolumeFailures/BlockVerificationFailures/FileIoErrors/LogError）
- [ ] H01-H08（NameNode 区）在 PanelDef 中标注 ★ 待核对，并保留 §9.5 落地核对清单
- [ ] 工具栏：Namespace 单选 + Instance 多选 + 时间范围 + 刷新（无 Interval 下拉）
- [ ] 在 1280px 宽度下 6 行布局无横向滚动条
- [ ] golden signals 四象限覆盖验证（见 §5.0）；Error 维度以 NameNode 块健康（H03-H06）+ DataNode 故障（H18）双重覆盖
