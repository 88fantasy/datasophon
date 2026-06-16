# Kafka 监控看板原型设计 Spec

> **文档用途**：供 Claude design 阅读，根据本 spec 设计 React + AntV G2 看板原型，并最终生成可运行的组件代码。
> **组件**：Apache Kafka 4.3.0
> **数据源**：双 exporter —— ① `danielqsj/kafka_exporter`（topic/消费组 offset、lag，默认 `:9308`）；② Prometheus JMX Exporter javaagent 挂载到 Broker JVM（Broker 端全量指标，默认 `:7071`）
> **参考 Grafana 看板**：[Kafka Exporter Overview](https://grafana.com/grafana/dashboards/7589) (ID 7589)；Broker 侧参考 [Kafka JMX Exporter](https://grafana.com/grafana/dashboards/721)
> **Panel Catalog 路径**：`docs/monitoring/panel-catalog/Kafka.json`（5 个面板，均为 kafka_exporter）
> **Phase**：Phase 2 —— 原型设计阶段

---

## 1. 架构约束

```
React(AntV G2)
  └──HTTP──> datasophon-api /api/v2/prometheus/query_range
               └──PromQL──> Prometheus :9090
                              ├──scrape──> kafka_exporter :9308   (kafka_topic_* / kafka_consumergroup_*)
                              └──scrape──> JMX Exporter :7071      (kafka_server_* / kafka_network_* / kafka_controller_*) ★
```

**前端不直连 Prometheus**。所有 PromQL 通过后端代理端点转发（详见 `prometheus-dashboard-prototype-spec.md` §1）。

### 1.1 Kafka 双数据源与黄金信号缺口

- **kafka_exporter（catalog 来源）**：只暴露 **topic offset、分区数、消费组 lag**——属于黄金信号的 **Traffic（流量）** 与部分 **Saturation（lag 积压）**。
- **⚠️ Broker 端延迟与错误缺失**：kafka_exporter **不暴露** Broker 请求延迟、under-replicated / offline 分区、controller 状态。这些是 Kafka 运维最关键的健康信号，必须由 **JMX Exporter（★ 补强）** 补齐 Latency 与 Errors 象限。
- **指标前缀**：kafka_exporter → `kafka_topic_*` / `kafka_consumergroup_*`；JMX Exporter → `kafka_server_*` / `kafka_network_*` / `kafka_controller_*`（具体名取决于 jmx_exporter 的 pattern 配置，本 spec 采用社区标准 kafka jmx 配置的命名）。
- **指标维度**：kafka_exporter 用 `{instance, topic, consumergroup}`；JMX 用 `{instance}`（每 Broker 一个 target）。两类 `instance` 可能不同地址（exporter vs javaagent 端口），落地时按 `job` 区分。

---

## 2. 图表类型映射字典

**完全复用 `prometheus-dashboard-prototype-spec.md` §2 中的映射字典**。

Kafka 特有补充：

| 特征 | 映射组件 | 备注 |
|---|---|---|
| 健康计数（Under-Repl/Offline） | `<Statistic>` + `colorByThreshold` | 0 绿、≥1 红 |
| offset delta（消息速率） | `<Line>` + `delta()/rate()` | catalog 用 `delta([5m])/5` 近似每分钟 |
| Broker 请求延迟（JMX）★ | `<Line>` by request type | `kafka_network_requestmetrics_*` |

---

## 3. 变量 / 过滤器规范

| 变量 | PromQL 占位符 | 取值来源 | 默认值 | 说明 |
|---|---|---|---|---|
| 实例 | `$instance` | `label_values(kafka_brokers, instance)` | `.+` | 多选，集群/Broker |
| 主题 | `$topic` | `label_values(kafka_topic_partitions{instance=~"$instance"}, topic)` | `.+`（全选） | 多选下拉 |
| 时间范围 | — | 时间选择器 | `Last 1h` | 5m/15m/1h/6h/24h/7d |
| 刷新间隔 | — | — | `30s` | 自动轮询 |

> catalog 中 `$instance` 多处混用 `="$instance"`（精确）与 `=~'$instance'`（正则单引号）；本 spec 统一为 `=~"$instance"`（正则双引号），支持多选。
> 速率窗口固定（`[1m]` / `delta([5m])/5`），不暴露 Interval 下拉。

---

## 4. 看板布局（24 列 Grid）

裁剪策略：catalog 5 面板全保留 + **JMX 补强 7 面板（★）** = **12 面板**。

```
┌──────────────────────────────────────────────────────────────────────┐
│  TOOLBAR: [实例▼]  [主题▼]        [Last 1h▼]   [🔄 30s▼]           │
└──────────────────────────────────────────────────────────────────────┘

行 R1 — 集群健康 Stat（高度 80px，col=4 ×6）
┌────────┬────────┬────────┬────────┬────────┬────────┐
│Brokers │Active  │Under-  │Offline │Topics  │Max     │
│Online★ │Ctrl ★  │Repl ★  │Parts ★ │        │Lag     │
│ K01    │ K02    │ K03    │ K04    │ K05    │ K06    │
└────────┴────────┴────────┴────────┴────────┴────────┘

行 R2 — 流量 Traffic（高度 200px）
┌────────────────────────────┬────────────────────────────┐
│ K07 Messages In per sec    │ K08 Broker Bytes In/Out ★  │
│ [Line by topic]            │ [Area in/out，bytes/s]     │
└────────────────────────────┴────────────────────────────┘

行 R3 — 消费 & Lag Traffic/Saturation（高度 200px）
┌────────────────────────────┬────────────────────────────┐
│ K09 Lag by Consumer Group  │ K10 Message Consume /min   │
│ [Line by consumergroup]    │ [Line by consumergroup]    │
└────────────────────────────┴────────────────────────────┘

行 R4 — Broker 延迟 Latency ★（高度 200px）
┌────────────────────────────┬────────────────────────────┐
│ K11 Request Total Time ★   │ K12 Request Rate ★         │
│ [Line by request type]     │ [Line by request type]     │
└────────────────────────────┴────────────────────────────┘
```

---

## 5. 面板规格（逐面板）

### 5.0 Golden Signals 映射

| 维度 | 面板 | 说明 |
|---|---|---|
| **Latency（延迟）** | K11 Request Total Time ★ | Produce/Fetch 请求端到端耗时（JMX 补强） |
| **Traffic（流量）** | K07 Messages In/sec、K08 Bytes In/Out ★、K10 Consume/min、K12 Request Rate ★ | 消息与字节吞吐 |
| **Errors（错误）** | K03 Under-Replicated ★、K04 Offline Partitions ★ | 副本不足 / 分区下线（JMX 补强） |
| **Saturation（饱和度）** | K06 Max Lag、K09 Lag by Group、K01/K02 集群状态 | 消费积压与集群健康 |

---

### 5.1 R1 — 集群健康 Stat

> K01–K04 为 JMX 补强（★），catalog 不含；K05/K06 来自 kafka_exporter。

#### K01 Brokers Online ★

| 属性 | 值 |
|---|---|
| 标题 | Brokers Online |
| 图表类型 | `<Statistic>` |
| Query 类型 | instant query |
| PromQL | `count(kafka_server_kafkaserver_brokerstate{instance=~"$instance"})` 或 kafka_exporter 的 `kafka_brokers` |
| 说明 | 在线 Broker 数；落地按 JMX 实际指标名调整 |

#### K02 Active Controller ★

| 属性 | 值 |
|---|---|
| 标题 | Active Controllers |
| 图表类型 | `<Statistic>` + `colorByThreshold` |
| PromQL | `sum(kafka_controller_kafkacontroller_activecontrollercount{instance=~"$instance"})` |
| 阈值 | `= 1` → 绿（正常应恰好 1 个 controller）；`≠ 1` → 红 |

#### K03 Under-Replicated Partitions ★

| 属性 | 值 |
|---|---|
| 标题 | Under-Replicated Partitions |
| 图表类型 | `<Statistic>` + `colorByThreshold` |
| PromQL | `sum(kafka_server_replicamanager_underreplicatedpartitions{instance=~"$instance"})` |
| 阈值 | `= 0` → 绿；`≥ 1` → 红（副本同步落后，可用性风险） |

#### K04 Offline Partitions ★

| 属性 | 值 |
|---|---|
| 标题 | Offline Partitions |
| 图表类型 | `<Statistic>` + `colorByThreshold` |
| PromQL | `sum(kafka_controller_kafkacontroller_offlinepartitionscount{instance=~"$instance"})` |
| 阈值 | `= 0` → 绿；`≥ 1` → 红（分区无 leader，数据不可读写） |

#### K05 Topics

| 属性 | 值 |
|---|---|
| 标题 | Topics |
| 图表类型 | `<Statistic>` |
| PromQL | `count(count by (topic) (kafka_topic_partitions{instance=~"$instance",topic=~"$topic"}))` |

#### K06 Max Consumer Lag

| 属性 | 值 |
|---|---|
| 标题 | Max Consumer Lag |
| 图表类型 | `<Statistic>` + `colorByThreshold` |
| PromQL | `max(kafka_consumergroup_lag{instance=~"$instance",topic=~"$topic"})` |
| 阈值 | 按业务设定（如 `< 10000` 绿、`< 100000` 橙、`≥ 100000` 红） |

---

### 5.2 R2 — 流量

#### K07 Messages In per sec

| 属性 | 值 |
|---|---|
| 标题 | Message in per second |
| 图表类型 | `<Line>` 多系列 by topic |
| Query 类型 | range query |
| PromQL | `sum(rate(kafka_topic_partition_current_offset{instance=~"$instance", topic=~"$topic"}[1m])) by (topic)` |
| y 轴 | `msg/s` |
| Legend | 右侧表格，按 max 降序（catalog `sideWidth=480`） |

#### K08 Broker Bytes In/Out ★

| 属性 | 值 |
|---|---|
| 标题 | Broker Bytes In/Out |
| 图表类型 | `<Area>` 2 系列 |
| PromQL (in) | `sum(rate(kafka_server_brokertopicmetrics_bytesinpersec{instance=~"$instance"}[1m]))` |
| PromQL (out) | `sum(rate(kafka_server_brokertopicmetrics_bytesoutpersec{instance=~"$instance"}[1m]))` |
| y 轴 | bytes/s，`formatBytes` |
| 系列 | `Bytes In`（绿）、`Bytes Out`（蓝） |
| 说明 | ★ JMX；`*persec` 系 Meter，JMX exporter 导出为 `_count`，需 rate |

---

### 5.3 R3 — 消费 & Lag

#### K09 Lag by Consumer Group

| 属性 | 值 |
|---|---|
| 标题 | Lag by Consumer Group |
| 图表类型 | `<Line>` 多系列 |
| Query 类型 | range query |
| PromQL | `sum(kafka_consumergroup_lag{instance=~"$instance",topic=~"$topic"}) by (consumergroup, topic)` |
| 系列字段 | `consumergroup` + `topic` |
| y 轴 | 整数（消息条数） |

#### K10 Message Consume per minute

| 属性 | 值 |
|---|---|
| 标题 | Message consume per minute |
| 图表类型 | `<Line>` 多系列 |
| PromQL | `sum(delta(kafka_consumergroup_current_offset{instance=~"$instance",topic=~"$topic"}[5m])/5) by (consumergroup, topic)` |
| 系列字段 | `consumergroup` + `topic` |
| 说明 | catalog 用 `delta([5m])/5` 近似每分钟消费速率 |

---

### 5.4 R4 — Broker 延迟 ★

> K11/K12 为 JMX 补强，补齐 Latency 象限。`kafka_network_requestmetrics_*` 按 `request`（Produce/FetchConsumer/FetchFollower）维度。

#### K11 Request Total Time ★

| 属性 | 值 |
|---|---|
| 标题 | Request Total Time (ms) |
| 图表类型 | `<Line>` 多系列 by request |
| Query 类型 | range query |
| PromQL (p99) | `kafka_network_requestmetrics_totaltimems{instance=~"$instance", quantile="0.99"}` |
| 备选（mean） | `rate(kafka_network_requestmetrics_totaltimems_sum[1m]) / rate(kafka_network_requestmetrics_totaltimems_count[1m])` |
| 系列字段 | `request`（Produce / FetchConsumer / FetchFollower） |
| y 轴 | `ms` |
| 说明 | ★ JMX；指标是否带 `quantile` 取决于 jmx_exporter 配置（histogram vs summary），落地二选一 |

#### K12 Request Rate ★

| 属性 | 值 |
|---|---|
| 标题 | Request Rate |
| 图表类型 | `<Line>` 多系列 by request |
| PromQL | `sum(rate(kafka_network_requestmetrics_requestspersec{instance=~"$instance"}[1m])) by (request)` |
| 系列字段 | `request` |
| y 轴 | `req/s` |
| 说明 | ★ JMX |

> **Partitions per Topic / Messages in per minute**（catalog 第 3、5 面板）为偏静态/重复信息，降级为可选深挖项（保留在 panel-catalog）；如需可追加为 R5 行。

---

## 6. 主题 / 样式规范

**完全复用 `prometheus-dashboard-prototype-spec.md` §6 中的颜色 Token 和工具函数**（含 `formatBytes` / `colorByThreshold`）。Kafka 无额外特有配色，多系列用 `CHART_COLORS.series`，错误计数用 `error` 红。

---

## 7. 数据层接口 TypeScript 定义

**复用 `prometheus-dashboard-prototype-spec.md` §7 中全部接口定义**。

```ts
interface KafkaDashboardQueryParams {
  clusterId: number;
  start: number;
  end: number;
  step: number;
  variables: {
    instance: string;   // 正则，Broker/exporter 地址
    topic: string;      // 正则，主题
    // 注：固定窗口，无 interval 变量
  };
}
```

---

## 8. 组件树结构

```
<KafkaDashboard>
  ├── <DashboardToolbar>              # Instance 多选 + Topic 多选 + 时间范围 + 刷新
  │
  ├── <Row R1>                        # 集群健康 Stat（6 个）
  │   ├── <StatPanel K01>             # Brokers Online ★
  │   ├── <StatPanel K02>             # Active Controllers ★（=1 绿）
  │   ├── <StatPanel K03>             # Under-Replicated ★（≥1 红）
  │   ├── <StatPanel K04>             # Offline Partitions ★（≥1 红）
  │   ├── <StatPanel K05>             # Topics
  │   └── <StatPanel K06>             # Max Consumer Lag（阈值）
  │
  ├── <Row R2>                        # 流量
  │   ├── <TimeSeriesPanel K07>       # Messages In per sec
  │   └── <AreaPanel K08>             # Broker Bytes In/Out ★
  │
  ├── <Row R3>                        # 消费 & Lag
  │   ├── <TimeSeriesPanel K09>       # Lag by Consumer Group
  │   └── <TimeSeriesPanel K10>       # Message Consume /min
  │
  └── <Row R4>                        # Broker 延迟 ★
      ├── <TimeSeriesPanel K11>       # Request Total Time ★
      └── <TimeSeriesPanel K12>       # Request Rate ★

# 复用的基础组件（来自 PrometheusMonitor/panels/）
StatPanel / TimeSeriesPanel / AreaPanel / DashboardToolbar / usePrometheusDashboard
```

---

## 9. 实现说明（供 Phase 3 编码参考）

### 9.1 文件路径

```
datasophon-ui-v2/src/pages/KafkaMonitor/
  ├── index.tsx                     # 页面容器（4 行布局）
  ├── panelQueries.ts               # PanelDef（12 个面板，★ JMX 面板标注待核对 jmx_exporter 命名）
  ├── hooks/useKafkaDashboard.ts
  ├── panels/                       # 复用 PrometheusMonitor/panels/
  ├── toolbar/                      # Instance + Topic 多选
  ├── mock/kafkaMockData.ts
  └── utils/                        # 复用 PrometheusMonitor/utils/
```

### 9.2 PromQL 变量替换

```ts
function replaceKafkaVars(promql: string, vars: KafkaDashboardQueryParams['variables']): string {
  return promql
    .replace(/\$instance/g, vars.instance || '.+')
    .replace(/\$topic/g,    vars.topic    || '.+');
}
```

### 9.3 ★ JMX 面板落地核对

K01-K04、K08、K11、K12 依赖 JMX Exporter。jmx_exporter 的指标名由其 YAML pattern 配置决定，社区标准 kafka 配置产出本 spec 采用的命名（`kafka_server_*` / `kafka_network_*` / `kafka_controller_*`）。落地时核对：
- `kafka_controller_kafkacontroller_activecontrollercount` / `offlinepartitionscount`
- `kafka_server_replicamanager_underreplicatedpartitions`
- `kafka_server_brokertopicmetrics_bytesinpersec` / `bytesoutpersec`（Meter → `_count`，需 rate）
- `kafka_network_requestmetrics_totaltimems`（summary quantile 或 histogram `_sum`/`_count`）/ `requestspersec`

若目标集群仅部署 kafka_exporter（无 JMX javaagent），★ 面板降级为占位提示「需启用 JMX Exporter」。

### 9.4 Mock 数据要求

`kafkaMockData.ts` 覆盖 12 面板：
- K01 Brokers `3`、K02 Active Controllers `1`（绿）、K03 Under-Repl `0`（绿）、K04 Offline `0`（绿）、K05 Topics `24`、K06 Max Lag `1500`（绿）
- K07 Messages In: 3 个 topic 各 2000–8000 msg/s
- K08 Bytes In/Out: in ≈ 12 MB/s、out ≈ 35 MB/s
- K09 Lag: 各消费组 0–2000，偶发 1 组涨到 5 万验证积压
- K10 Consume/min: 与生产速率匹配
- K11 Request Total Time: Produce p99 ≈ 8ms、FetchConsumer ≈ 25ms、FetchFollower ≈ 5ms
- K12 Request Rate: Produce ≈ 1200/s、Fetch ≈ 3000/s

---

## 10. Dev 环境配置注意事项

**完全复用 `prometheus-dashboard-prototype-spec.md` §10**。后端代理端点与 PrometheusMonitor 相同。

---

## 11. 验收标准

- [ ] 全部 12 个面板（K01-K12）按 §4 布局渲染（4 行 24 列 Grid）
- [ ] R1 集群健康：K02 Active Controllers `=1` 绿/否则红；K03/K04 `≥1` 红
- [ ] catalog 来源面板（K05/K06/K07/K09/K10）PromQL 已统一 `=~"$instance"`（修正单/双引号、精确/正则混用）
- [ ] ★ JMX 面板（K01-K04/K08/K11/K12）在 PanelDef 中标注待核对 jmx_exporter 命名，并保留 §9.3 核对清单
- [ ] K08 Bytes In/Out 用 `<Area>` + `formatBytes`/s
- [ ] K11 Request Total Time 按 `request` 类型分系列（Produce/Fetch*）
- [ ] 工具栏：Instance 多选 + Topic 多选 + 时间范围 + 刷新
- [ ] 在 1280px 宽度下 4 行布局无横向滚动条
- [ ] golden signals 四象限覆盖验证（见 §5.0）；Latency/Errors 由 ★ JMX 补强，并标注无 JMX 时的降级行为
