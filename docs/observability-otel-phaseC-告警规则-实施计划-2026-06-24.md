# Phase C — 完善 Nexus / Doris OTel 告警规则

> **状态**：待实施（设计已确认）  
> **日期**：2026-06-24  
> **分支**：`refactor/observability-otel`  
> **关联 epic**：可观测重构 OTel+Doris（[[project_observability_otel_doris]]）

## Context（为什么做这件事）

可观测重构 epic 的 Phase C 已完成 Nexus 与 Doris 的 OTel 采集 + Doris 落库 + 原生看板。但**告警侧仍是空白**：现有 `OtelAlertScheduler` 只对 Collector 自身的 self-metrics（:8888）做 2 条硬编码规则（队列水位、发送失败率），**完全没有针对被监控服务（Nexus/Doris）业务指标的告警**。本任务把联网检索到的 Nexus/Doris 常用告警规则落地，让这两个组件在原生告警链路上"看板亮 + 告警通"形成闭环。

旧栈的 Prometheus alert rule（存 DB 表 `t_ddh_cluster_alert_quota` → 渲染 Prometheus `alert.yml`）在 OTel 模式下无数据可评估（Prometheus 不再抓 Nexus/Doris），因此需要把规则定义保留在 DB（便于 UI 查看/调阈值），但**改由 `OtelAlertScheduler` 读取这些规则并查 Doris 评估**。

## 决策（已与用户确认）

1. **规则存储 = DB quota 表**（`t_ddh_cluster_alert_quota`）。阈值 / 开关 / 建议 / 级别由 DB 行承载（UI 可编辑）；查询形参（指标名、agg、scale、filters、table、rate、分子分母）quota 表无对应列，由**代码侧静态描述符**（按 `alertQuotaName` 关联）承载。
2. **范围 = 7 条**：Nexus 4 条单指标 + Doris 3 条比值（官方 P0）。
3. **仅做有权威阈值的规则**（来源见下表）。
4. 通知沿用现有链路 `ClusterAlertHistoryService.saveAlertHistory → AlertService`（落库 + 改服务状态，不外发，与现有 OtelCollector 告警一致）。

## 最终规则集（含来源阈值）

### Nexus（serviceCategory=`NEXUS`，serviceRoleName=`NexusRepository` 仅作标签，table=gauge）

| alertQuotaName |           指标(alertExpr)            | agg | scale | compareMethod | threshold |  级别   |         来源         |
|----------------|------------------------------------|-----|-------|---------------|-----------|-------|--------------------|
| Nexus实例只读      | `readonly_enabled`                 | max | 1     | `>`           | 0         | 异常(2) | 进只读=不可写            |
| Nexus线程死锁      | `jvm_thread_states_deadlock_count` | max | 1     | `>`           | 0         | 异常(2) | awesome-prometheus |
| Nexus堆内存使用率    | `jvm_memory_heap_usage`            | max | 100   | `>`           | 85        | 警告(1) | JVM/Sonatype 最佳实践  |
| Nexus文件描述符使用率  | `jvm_fd_usage`                     | max | 100   | `>`           | 90        | 警告(1) | awesome-prometheus |

> Nexus 无 `service_ddl.json`、非 datasophon 托管角色 → AlertService 角色状态变更在 `roleInstance != null` 处优雅 no-op（`AlertService.java:166`），仅记录告警历史，安全。

### Doris（serviceCategory=`DORIS`，比值=分子÷分母×scale）

| alertQuotaName |                      分子                       |                    分母                    | filters  | table/rate  | scale | cmp | thr |  级别   |   角色    |     来源     |
|----------------|-----------------------------------------------|------------------------------------------|----------|-------------|-------|-----|-----|-------|---------|------------|
| DorisBE磁盘使用率   | `doris_be_disks_local_used_capacity`(agg=sum) | `doris_be_disks_total_capacity`(agg=sum) | group=be | gauge       | 100   | `>` | 85  | 异常(2) | DorisBE | 官方"80-85%" |
| DorisFE堆内存使用率  | `jvm_heap_size_bytes`{type=used}              | `jvm_heap_size_bytes`{type=max}          | group=fe | gauge       | 100   | `>` | 85  | 警告(1) | DorisFE | 官方"85%"    |
| Doris查询错误率     | `doris_fe_query_err`                          | `doris_fe_query_total`                   | group=fe | sum/rate 2m | 100   | `>` | 5   | 警告(1) | DorisFE | 官方 P0      |

来源参考：[Doris 官方 Monitor Metrics](https://doris.apache.org/docs/3.x/admin-manual/maint-monitor/metrics/)、[awesome-prometheus-alerts JVM 规则](https://samber.github.io/awesome-prometheus-alerts/rules/runtimes/jvm)、[Sonatype 社区监控建议](https://community.sonatype.com/t/alert-needed-to-monitoring-nexus-repository-manager/10301)。

## 实施步骤

### 1. DB 种子 → 新建 `db/migration/2.2.1/V2.2.1__DML.sql`

- 7 条 `INSERT INTO t_ddh_cluster_alert_quota`，列序同现有种子（见 `1.1.0/V1.1.0__DML.sql:147`）：
  `(id, alertQuotaName, serviceCategory, alertExpr, alertLevel, alertGroupId, noticeGroupId, alertAdvice, compareMethod, alertThreshold, alertTactic, intervalDuration, triggerDuration, serviceRoleName, quotaState, createTime)`
- `alertExpr` 存**裸指标名**（如 `readonly_enabled`），不存 PromQL：评估器把它当 Doris 指标名；即使被旧 Prometheus-render 路径拼成 `readonly_enabled > 0` 也是合法 PromQL（无数据、不误触发）。
- `quotaState = 1`(RUNNING)，使评估器无需 UI 手动"启动"即纳入。
- `alertGroupId / noticeGroupId` 复用现有有效值（沿用 `11 / 1`，仅 Prometheus-render 路径用，本评估器忽略）。
- 新 ID 取未占用高段（执行前 `SELECT max(id)` 核实，预留 600–606）。
- **验证**：迁移执行器 `DatabaseMigration` 是否要求每版本目录 DDL+DML 成对——若是，补一个仅含注释的 `V2.2.1__DDL.sql`；若容忍 DML-only 则省略。执行前确认。

### 2. 扩展 `OtelAlertScheduler.java`（`datasophon-api/.../observability/`）

新增**第二个 `@Scheduled` 方法** `checkMetricRules()`，复用现有 `firingAlerts` 边沿触发 + 告警历史落库，独立于现有 `checkCollectors()`（self-metrics）：

- **新增注入**：`OtelMetricsQueryService`（已是 Spring bean）、`ClusterAlertQuotaService`（`lambdaQuery()` 取 `serviceCategory IN (NEXUS,DORIS)` 且 `quotaState=RUNNING` 的行）。
- **静态描述符注册表** `Map<String, OtelAlertRuleSpec>`（按 `alertQuotaName` 关联），承载 quota 表缺失的查询形参：

  ```java
  record OtelAlertRuleSpec(
      String metric, String table, String rateWindow, String agg,
      Map<String,String> filters,
      String denomMetric, String denomTable, Map<String,String> denomFilters
  )
  ```
- **取最新标量**辅助方法 `Map<String,Double> latestPerSeries(...)`:
  - gauge → `queryInstant(clusterId, metric, agg, scale=1, ".+", ".+", filters, null, now)`，按返回 series 的 label JSON 串为 key。
  - counter+rate → `queryRange(..., table="sum", rateWindow, ...)` 取每 series **最后一个点**。
- **评估**：单指标 → `value×scale` 比阈值；比值 → 分子 map ÷ 分母 map（按相同 label key 配对，参考前端 `divideMatrixPointwise` 但用单点）×scale 比阈值。`compareMethod`（`>`/`<`/`!=`）+ `alertThreshold`（Long）来自 DB 行。
- **复用并泛化** `alertMessage(...)`：现版本硬编码 `serviceRoleName="OtelCollector"`、`instance=host:8888`、`severity="warning"`；改为入参，使 Nexus/Doris 规则可传 `serviceRoleName`（NexusRepository/DorisFE/DorisBE）、`severity`（由 alertLevel 映射 warning/critical）、`instance`（指标 series 的 instance label）。`firingAlerts` key 追加 seriesKey。
- 阈值/开关动态来自 DB；`@Value` 注入新的周期开关 `datasophon.observability.otel-metric-alert.{interval-ms,initial-delay-ms}`（默认 60000）。

### 3. 配置 `application.yml`

在 `datasophon.observability` 下补充：

```yaml
datasophon:
  observability:
    otel-metric-alert:
      interval-ms: 60000
      initial-delay-ms: 60000
```

（`@Value` 已设默认值兜底，显式写出便于运维感知和调整。）

### 4. 扩展 `OtelAlertSchedulerTest.java`（已存在）

mock `OtelMetricsQueryService` 返回构造的 vector/matrix、mock `ClusterAlertQuotaService` 返回种子规则行，覆盖以下 5 类场景：

1. 单指标越界触发 + 回落 resolved（边沿仅各 1 次落库）
2. 比值规则分子/分母按 label 配对相除越界触发
3. rate 规则取最后点
4. compareMethod `<` / `!=` 分支
5. `queryInstant` 抛异常时不影响其他集群/规则（沿用 `checkCollectors` 的 try-catch 隔离）

## 关键复用点（避免造轮子）

|                     复用目标                      |                                位置                                 |
|-----------------------------------------------|-------------------------------------------------------------------|
| `queryInstant(...)` / `queryRange(...)`       | `OtelMetricsQueryService`（不新增方法）                                  |
| `firingAlerts` 边沿触发 + `alertMessage(...)`     | `OtelAlertScheduler`（泛化入参，保留现有 OtelCollector 语义）                  |
| `ClusterAlertHistoryService.saveAlertHistory` | 告警历史落库（unchanged）                                                 |
| `ClusterAlertQuotaService.lambdaQuery()`      | 直接取 NEXUS/DORIS quota 行                                           |
| `divideMatrixPointwise` 逻辑                    | 参考前端 `_shared/useDorisDashboardData.ts`，Java 侧按 label JSON key 配对 |

## 风险与边界

- **服务状态翻转**：firing 时 AlertService 会把匹配到的 Doris 角色置 `EXISTS_ALARM`（warning 级别也会）。这是平台既有语义，UI 服务卡片显示告警态。Nexus 无角色 → no-op。属预期行为。
- **err 率走 rate 路径**：`queryInstant` 只查 gauge 表、不支持 rate；查询错误率分子分母是 counter，必须走 `queryRange + rate` 取最后点。已在描述符 `rateWindow` 字段体现。
- **本批不做的规则**：query_latency p99、editlog p99、compaction_score、连接数——无权威阈值或需 summary 表，留下一批。
- **比值 series 配对**：filter key（group/type）不出现在返回 labels（仅进 WHERE），故分子分母 label 天然一致，配对稳健。

## 验收标准

| # |                     验收项                     |                                  检查方式                                  |
|---|---------------------------------------------|------------------------------------------------------------------------|
| 1 | 编译通过，无新增 warning                            | `./mvnw -pl datasophon-api -am compile`                                |
| 2 | 单测全绿，5 类场景覆盖，边沿各落库一次                        | `./mvnw -pl datasophon-api -Dtest=OtelAlertSchedulerTest test`         |
| 3 | Spotless 格式检查通过                             | `./mvnw spotless:check`                                                |
| 4 | DML 列数=16、列序正确、id 不冲突、quotaState=1、7 行齐全    | 读文件 + SQL `SELECT count(*) WHERE serviceCategory IN ('NEXUS','DORIS')` |
| 5 | 评估器仅复用 `queryInstant/queryRange`，无新增查询方法    | code review                                                            |
| 6 | `alertMessage` 泛化后现有 `checkCollectors` 行为不变 | 单测 OtelCollector 路径仍 pass                                              |
| 7 | 查询异常按集群/规则 try-catch 隔离，不致整轮失败              | 单测场景 ⑤                                                                 |
| 8 | （条件具备时）DB 出现 7 条 NEXUS/DORIS quota 行        | 本地启动后 SQL 查询                                                           |

## 端到端验证命令

```bash
# 1. 单元测试（无需真实 Doris）
JAVA_HOME=$JH17 ./mvnw -pl datasophon-api -Dtest=OtelAlertSchedulerTest test -s ~/.m2/setting.xml

# 2. 编译
JAVA_HOME=$JH17 ./mvnw -pl datasophon-api -am compile -DskipTests -s ~/.m2/setting.xml

# 3. 格式
JAVA_HOME=$JH17 ./mvnw spotless:check -s ~/.m2/setting.xml

# 4. 迁移验证（本地 MySQL 启动后）
#   SELECT id, alert_quota_name, service_category, quota_state
#   FROM t_ddh_cluster_alert_quota
#   WHERE service_category IN ('NEXUS','DORIS');
#   → 应返回 7 行，quota_state=1

# 5. 真实环境（受限）：制造 Nexus 只读 / Doris 磁盘>85%
#   → 60s 内 t_ddh_cluster_alert_history 出现 firing 记录
#   → 恢复后出现 resolved（边沿各一次）
```

