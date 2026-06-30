# Phase B 范围评估报告

> 写于 2026-06-21，基于 main 监控看板迁移合并后的全量代码状态。
> 目的：为下一轮 Phase B 实现决策提供架构分析和范围建议。

## 1. 现状盘点

### 1.1 main 带入的 14 个原生看板

合并后 `datasophon-ui-v2/src/pages/monitor/` 共有以下服务看板：

|             目录             |        服务        |               状态                |
|----------------------------|------------------|---------------------------------|
| `ApisixMonitor/`           | APISIX API 网关    | ✅ 有 panelQueries + hook + index |
| `DatartMonitor/`           | DATART 数据可视化     | ✅ 有 panelQueries + hook + index |
| `DolphinSchedulerMonitor/` | DolphinScheduler | ✅ 有 panelQueries + hook + index |
| `DorisMonitor/`            | Doris 数据库        | ✅ 有 panelQueries + hook + index |
| `JuiceFSMonitor/`          | JuiceFS 文件系统     | ✅ 有 panelQueries + hook + index |
| `KyuubiMonitor/`           | Kyuubi           | ✅ 有 panelQueries + hook + index |
| `MySQLMonitor/`            | MySQL            | ✅ 有 panelQueries + hook + index |
| `NexusMonitor/`            | Nexus 制品库        | ✅ 有 panelQueries + hook + index |
| `NginxMonitor/`            | Nginx            | ✅ 有 panelQueries + hook + index |
| `PrometheusMonitor/`       | Prometheus 自身监控  | ✅ 有 panelQueries + hook + index |
| `ValkeyMonitor/`           | Valkey（Redis 兼容） | ✅ 有 panelQueries + hook + index |
| `ZooKeeperMonitor/`        | ZooKeeper        | ✅ 有 panelQueries + hook + index |

注：Alertmanager、Kafka、HDFS、Loki、Promtail 的 Grafana JSON 已收录为参考物料
（`docs/monitoring/dashboards-reference/`），但尚未实现原生看板页面，不在已完成清单内。

### 1.2 取数架构（三层）

```
panelQueries.ts          ← 第 1 层：PromQL 字符串定义（纯声明，无副作用）
    ↓ import
hooks/use*Dashboard.ts   ← 第 2 层：React hook，调用 _shared/service.ts 发请求
    ↓ call
_shared/service.ts       ← 第 3 层：HTTP 封装
    queryInstant()   → GET /prometheus/query
    queryRange()     → GET /prometheus/query_range
    ↓ proxy
PrometheusProxyV2Controller (datasophon-api)
    ↓ forward
Prometheus HTTP API (:9090)
```

### 1.3 渲染架构（三层，与取数层分离）

```
_shared/panels/          ← 通用图表组件（AreaPanel/StatPanel/TablePanel/TimeSeriesPanel）
_shared/MonitorPanelCard ← 面板卡片容器（加载/错误/空状态统一处理）
_shared/MonitorDashboardLayout ← 网格布局
*Monitor/index.tsx       ← 各服务页面组装（使用上述组件 + hook 数据）
```

**关键结论：渲染层与数据源完全解耦**——`panels/` 组件接受的是 `data`（number/string/series[]）
而非 PromQL；只要 hook 返回相同形状的数据，无需改动渲染层。

### 1.4 本分支已有的 OTel 侧 UI

`datasophon-ui-v2/src/pages/Cluster/ObservabilityCollector/`：

- `MonitorTab.tsx`：读 Collector self-metrics（目前走后端 `/api/observability/otelcol/metrics`）
- `ConfigTab.tsx`：Collector 配置管理
- 后端：`OtelCollectorController`（`/api/observability/otelcol/*`）、`OtelAlertScheduler`

---

## 2. 资产复用矩阵

|  层  |               组件/文件               |     Phase B 动作     |            说明            |
|-----|-----------------------------------|--------------------|--------------------------|
| 渲染层 | `_shared/panels/*`                | **直接复用**           | 与数据源无关                   |
| 渲染层 | `_shared/MonitorPanelCard`        | **直接复用**           | 与数据源无关                   |
| 渲染层 | `_shared/MonitorDashboardLayout`  | **直接复用**           | 与数据源无关                   |
| 渲染层 | `*Monitor/toolbar/*`              | **直接复用**           | 时间范围/刷新控件                |
| 渲染层 | `*Monitor/index.tsx`              | **直接复用（暂时）**       | 待 Phase C 迁采集后再改取数       |
| 取数层 | `_shared/service.ts`              | **暂留（Prometheus）** | Phase B 新看板用新 service    |
| 取数层 | `*Monitor/panelQueries.ts`        | **暂留（PromQL）**     | Phase C 服务迁采集后逐个改        |
| 取数层 | `*Monitor/hooks/use*Dashboard.ts` | **暂留**             | 同上                       |
| 新建  | `_shared/dorisService.ts`         | **Phase B 新建**     | 走 JdbcClient SQL 查 Doris |
| 新建  | `OtelMetricsMonitor/`             | **Phase B 新建**     | OTel 三信号看板               |
| 后端  | `PrometheusProxyV2Controller`     | **暂留**             | Phase E 下线               |
| 后端  | `OtelCollectorController`         | **已有，Phase B 扩展**  | 新增 Doris 查询端点            |

---

## 3. Phase B 候选范围（供拍板）

### 方案 A：复用 UI 层，聚焦 OTel 三信号新看板（推荐）

**做什么：**

1. **后端**：新增 Doris 查询端点（`OtelDorisQueryController`），基于 `JdbcClient`（MySQL 协议）
   查 `otel.otel_metrics_*`、`otel_logs`、`otel_traces` 表，返回与 Prometheus 格式兼容的
   结构（time-series matrix）以便复用渲染层。
2. **前端**：新建 `OtelMetricsMonitor/`（otelcol self-metrics 看板，数据来自 Doris
   而非 Prometheus）和未来的三信号大盘；复用 `_shared/panels/*` 渲染层。
3. **告警**：`OtelAlertScheduler`（已有）扩充 SQL 规则，迁移 Prometheus Rule 逻辑为
   原生 `@Scheduled` + SQL 评估，走现有 `ClusterAlertHistory/Quota` 存储。
4. **暂不动**：14 个 Prometheus 看板的取数层不改，等 Phase C 服务迁采集后逐个切换。

**优点：** 与 roadmap 顺序最契合（C 才迁采集 → 届时取数层有真实数据支撑）；
Phase B 工作量聚焦、边界清晰；两套看板可并存、互不干扰。

**缺点：** 短期双栈（Prometheus + Doris 两套 API）并存，Phase E 前都要维护。

### 方案 B：先抽象取数层为可切换 datasource

**做什么：** 在 `_shared/service.ts` 之上建一层 `datasource` 抽象
（`PrometheusDatasource` / `DorisDatasource`），各看板通过配置切换。
再做 OTel 三信号看板。

**优点：** 后续每个看板迁移时只需切换 datasource 配置，不改 hook。

**缺点：** Phase B 前置大量框架性工作（datasource 抽象），而 Phase C 前 Doris 里
没有业务服务数据，抽象用不上；过早泛化。

### 方案 C：仅做告警，看板 Phase C 后再议

只做原生 @Scheduled SQL 告警扩充，看板完全推到 Phase C 后。工作量最小但延后可视化价值。

---

## 4. 依赖与前置条件

|              前置               |                  状态                   |                     说明                      |
|-------------------------------|---------------------------------------|---------------------------------------------|
| Doris `otel` 库 + 8 张三信号表 DDL  | ✅ A2 已完成（自管版本化）                       | `OtelSchemaApplier` 幂等应用                    |
| Collector 安装/配置下发/监控          | ✅ A3 全部完成                             | `OtelCollectorController` 已有                |
| Doris MySQL 协议端点配置            | ✅ `OtelCredentialService` 有 reader 账号 | Phase B 后端查询用                               |
| otelcol 实际采集业务服务数据            | ❌ Phase C 才有                          | **Phase B 阶段 Doris 三信号表仅有 self-metrics 数据** |
| 真实 Worker + Doris + Rustfs 环境 | ❌ 本机受限                                | 端到端验证依赖真实环境                                 |

**关键约束：Phase B 阶段 Doris 只有 `OTELCOLLECTOR` self-metrics（Prometheus :8888 采集的
otelcol 自身指标），业务服务（HDFS/Kafka/Doris 等）的三信号数据要等 Phase C
完成各服务采集接入后才会入库。**

Phase B 的 OTel 新看板在真实环境上线前将以 otelcol self-metrics 为首批数据验证。

---

## 5. 风险与未决

|                       风险                       | 影响 |                                 建议                                 |
|------------------------------------------------|----|--------------------------------------------------------------------|
| 两套看板菜单并存（Monitor Dashboards + OTel 大盘）期间用户体验割裂 | 中  | Phase B 在菜单中把 OTel 看板归入 `ObservabilityCollector` 控制台 tab，与现有监控看板分区 |
| 告警双栈（Prometheus Rule + 原生 SQL）过渡期规则重复          | 中  | Phase B 只做原生 SQL 告警用于 OTel 指标；Prometheus Rule 保留直到 Phase E         |
| `CREATE JOB`（traces graph job）非幂等，重复 apply 失败  | 低  | 已记录为 A3 遗留，traces 完整 schema 留 Phase D（traces 管道）再最终决策              |
| Doris MySQL 协议查询在大时间跨度下性能                      | 低  | Phase B 设默认时间窗口 1h，后续按实测调整                                         |

---

## 6. 建议（供决策）

**推荐方案 A**，理由：

- 与现有 roadmap（A→B→C→D→E）顺序一致，不超前抽象。
- Phase B 交付物清晰：OTel self-metrics 看板 + 原生 SQL 告警扩充 + Doris 查询端点。
- 14 个 Prometheus 看板暂不动，Phase C 迁采集时再逐个切取数层，每个服务可独立验证。
- 渲染层已完全可复用，新看板的 UI 工作量仅需参照现有 `*Monitor/` 模式照做，
  套路已经标准化。

**Phase B 推荐交付清单（仅供参考，最终由下一轮确定）：**

1. `OtelDorisQueryController` — Doris SQL 查询端点（metrics/logs）
2. `OtelMetricsMonitor/` — otelcol self-metrics 原生看板（复用 `_shared/panels`）
3. `OtelAlertScheduler` 扩充 — 更多 OTel 指标的 SQL 告警规则
4. Doris 查询的分页/时间窗口策略 + 异常处理

