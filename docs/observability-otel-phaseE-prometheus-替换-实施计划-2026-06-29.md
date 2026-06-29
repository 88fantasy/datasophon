# Phase E — 用 OTel Collector 替代 Prometheus 采集

> 计划日期：2026-06-29 | 分支：`refactor/observability-otel`

## Context

可观测重构 epic 进入 **Phase E** 最后收尾：旧监控栈 Grafana/Loki/Promtail/Alertmanager 的 meta/模板
已在工作树删除待提交，**Prometheus 是最后一个仍在跑的旧采集组件**。

本次目标：让每节点的 **OTel Collector 接管全部 metrics 抓取职责**，Prometheus 服务彻底退役。
metrics 经 otelcol 落 Doris `otel` 库（已验证管道）。看板取数层（12 个 `*Monitor` 仍走
`PrometheusProxyV2Controller` 查 Prometheus）**本次不迁**，留待以后逐个切到 `OtelMetricsQueryController`。

**用户已拍板的两个架构决策**：
1. **采集拓扑 = 每节点本地就近抓**：每个 worker 的 otelcol 只抓本机部署角色的 exporter（`127.0.0.1:port`）。
   天然分片、省跨节点流量，契合"每节点 agent 无 gateway"设计与现有 `switchNode` 按 hostname 下发。
2. **过渡策略 = 立即停用 Prometheus 采集**：Prometheus 进程/meta/编排链直接退役；
   未迁看板过渡期无数据（可接受），倒逼后续尽快迁取数层。

## 范围

**做**：otelcol 通用 scrape 生成（本地就近）+ 触发接线 + Prometheus 全链退役 + DML 清理。

**不做（明确排除）**：
- 不动看板取数层、`panelQueries.ts`、各 `*Monitor` 页面。
- **保留** `PrometheusProxyV2Controller` + `PrometheusProxyProperties`（看板迁移期的旧入口，过渡期返回空/超时，用户接受）。
- 不动 `OtelMetricsQueryController` / `OtelMetricsQueryService`（看板迁移时才用）。
- 不动已验证的 Nexus/Doris OTel 看板。

## 现状关键事实（已调研确认）

### Prometheus 中心化模型
1 实例 + `file_sd_configs` → `configs/*.json`。`PrometheusService`(@Async)
在服务启动成功（`ServiceCommandService:~190`）/ 节点纳管（`WorkerStartService:~129`）时，用
`ServiceRoleJmxMap`（key = `frameCode_serviceName_roleName` → `jmxPort`）拼 `hostname:jmxPort`，
渲染 `scrape.ftl`/`starrocks-prom.ftl` 写到 Prometheus 节点 `configs/`，POST `/-/reload`。
`prometheus.ftl` 约 50 个 job，均使用 `file_sd_configs` 动态发现。

### otelcol 每节点模型
`otelcol.ftl` 当前 = `otlp + prometheus/self + 条件 prometheus/doris（写死特例）`。
`OtelExporterSwitchService.switchNode(clusterId, hostname, mode, overrides)` 按 hostname 下发；
`OtelCollectorConfigService.pushNodeConfig` 走 configure→restart（gRPC）。
- `getServiceRoleListByHostnameAndClusterId(hostname, clusterId)` **已存在** → 直接支撑本地就近。
- `prometheus.ftl` 约 50 job，绝大多数 `metrics_path=/metrics`，少数自定义（DolphinScheduler
  `/actuator/prometheus`、nacos、apisix、minio）。
- node_exporter（`:9100`）每节点本地抓；worker/master JMX exporter（`:8585/:8586`）Phase D 后已废弃，
  metrics 走 OTel Java Agent → **不再抓**（S1 需核实确认）。

## 实施阶段（进度跟踪）

| 阶段 | 内容 | 验证 | 状态 |
|---|---|---|---|
| S1 | `otelcol.ftl` 通用 scrape 渲染 | 渲染单测绿 | ⬜ 未开始 |
| S2 | API 侧本地就近 scrape 生成器 + job 注册表 | 生成器单测绿 | ⬜ 未开始 |
| S3 | 触发点接线（替换 PrometheusService 调用） | 启动/纳管/停服触发 push | ⬜ 未开始 |
| S4 | Prometheus 全链退役 + DML 清理 | 编译无残引用 | ⬜ 未开始 |
| S5 | 端到端验证 | 真实/沙箱抓取落 Doris | ⬜ 未开始 |

---

### S1 — otelcol.ftl 通用 scrape 渲染

把写死的 `prometheus/doris` 特例泛化为**通用 scrape receiver**。

**方案**：API 侧用 Java 直接拼好 `scrape_configs` 的 YAML 片段（端口/路径/label 逻辑都在 Java，易测），
作为单一 param（`localScrapeJobsYaml`）注入，`otelcol.ftl` 内 `${localScrapeJobsYaml}`
挂到一个 `prometheus/local` receiver，并加入 metrics pipeline 的 receivers。

优于 freemarker `<#list>` 展开复杂对象——50 种 job 各有 path/label 差异，字符串列表不够表达。

**具体变更**：
- `otelcol.ftl`：删除 `prometheus/doris` 特例（`<#if scrapeDoris>` 块），替换为：
  ```
  <#if (localScrapeJobsYaml!"")?has_content>
  prometheus/local:
    config:
      scrape_configs:
  ${localScrapeJobsYaml}
  </#if>
  ```
  并在 metrics pipeline receivers 中改 `[otlp, prometheus/self<#if localScrapeJobsYaml?has_content>, prometheus/local</#if>]`
- scrape 与 `exporterMode` **解耦**（当前 doris scrape 错误地绑在 DORIS 模式，s3 模式也应抓）

**文件**：`datasophon-worker/src/main/resources/templates/otelcol.ftl`

**测试**：`datasophon-worker` 中已有 `OtelcolTemplateTest`，扩充以下场景断言：
- `localScrapeJobsYaml` 非空时，渲染出 `prometheus/local` receiver 且在 metrics pipeline 中引用
- `localScrapeJobsYaml` 为空时，不出现 `prometheus/local`（nil 保护）
- `exporterMode=s3` 时 `prometheus/local` 依然出现（scrape 与 exporter 解耦）

---

### S2 — API 侧本地就近 scrape 生成器

新建 `OtelScrapeConfigBuilder`（`datasophon-api/src/main/java/com/datasophon/api/observability/`）。

**逻辑**：
```
build(clusterId, hostname) → String localScrapeJobsYaml
```
1. `roleService.getServiceRoleListByHostnameAndClusterId(hostname, clusterId)` 取本机角色实例（RUNNING 状态）。
2. 每个角色：
   - 构造 key `frameCode_serviceName_roleName`，从 `ServiceRoleJmxMap` 取 `jmxPort`。
   - 无端口 → 跳过。
   - 生成 scrape job（YAML 片段）：
     ```yaml
         - job_name: '<roleName>'
           scrape_interval: 15s
           metrics_path: '<PATH_OVERRIDES.getOrDefault(roleName, "/metrics")>'
           static_configs:
             - targets: ['127.0.0.1:<jmxPort>']
               labels: {job: '<roleName>', instance: '<hostname>:<jmxPort>'<, group: fe|be>}
     ```
   - Doris FE/BE 特殊处理：追加 `group: fe` / `group: be` label（对齐现有 Doris 看板 SQL 维度）。
3. 追加 node_exporter 固定 job（`127.0.0.1:9100`，`metrics_path=/metrics`）。
4. 拼接所有 job YAML 片段（缩进 4 空格，对齐 `otelcol.ftl` 中 `scrape_configs:` 下的列表格式），返回字符串。

**`PATH_OVERRIDES` 注册表**（仿 Phase C `METRIC_RULE_SPECS` 模式，静态 Map）：

| role 名称 | metrics_path 覆盖 | 备注 |
|---|---|---|
| `ApiServer` / `MasterServer` / `WorkerServer` / `AlertServer` | `/actuator/prometheus` | DolphinScheduler |
| `NacosServer` | `/nacos/actuator/prometheus` | Nacos |
| `Apisix` | `/apisix/prometheus/metrics` | APISIX |
| `Minio` | `/minio/v2/metrics/cluster` | MinIO（仅集群 metrics；多 path 取主要一条） |

从 `prometheus.ftl` 逐行核对，其余 job 均 `/metrics`。

**依赖注入**：构造器注入 `ClusterServiceRoleInstanceService`、`ServiceRoleJmxMap`（已是 `@Component`）。

**测试**：新建 `OtelScrapeConfigBuilderTest`：
- 本机多角色 → 各生成对应 job
- 无端口角色 → 跳过（不出现在输出）
- PATH_OVERRIDES 中的 role → 正确 path
- DorisFE / DorisBE → 含 `group: fe|be` label
- node_exporter 固定 job 始终出现
- 空角色列表 → 仅 node_exporter job

---

### S3 — 触发点接线

把"重新生成本机 scrape 配置并下发 otelcol"接到生命周期事件，**完全替换** `PrometheusService` 调用。

**改写位置**：

1. **`ServiceCommandService`**（`datasophon-api/.../master/service/ServiceCommandService.java`，约 `:190`）：
   服务启动/停止成功 callback → 取受影响角色的 hostname → 调 `OtelCollectorConfigService.pushNodeConfig`
   （内含 `OtelScrapeConfigBuilder.build(clusterId, hostname)` 生成的最新 scrape 配置）。
   删除原 `prometheusService.generatePrometheus(...)` / `generateSRPromConfig(...)` 调用。

2. **`WorkerStartService`**（约 `:129`）：
   新节点纳管 → push 该节点 otelcol 配置（含 node_exporter + 本机角色 scrape）。
   删除原 `prometheusService.generateHostPrometheusConfig(...)` 调用。

3. **`OtelExporterSwitchService.switchNode`**：
   内部 doris scrape 拼装逻辑（`dorisFeScrapeTargets`、`dorisBeScrapeTargets`）迁入
   `OtelScrapeConfigBuilder`（已在 S2 中实现为统一的角色遍历），`switchNode` 只保留
   exporter 模式切换参数，调用 `pushNodeConfig`（由生成器自动处理 Doris group label）。

4. **`OtelCollectorConfigService.pushNodeConfig`**：
   接收新增的 `localScrapeJobsYaml` param（由调用方通过 `OtelScrapeConfigBuilder.build` 生成后传入），
   放入 param map 传递给 `buildConfigCommand`。

---

### S4 — Prometheus 全链退役 + DML 清理

**删除 meta**：
- `package/raw/meta/datacluster-physical/PROMETHEUS/`（`service_ddl.json` + `control.sh`）
- `datasophon-api/src/main/resources/frameworktpl/PROMETHEUS/`（若存在副本）

**删除模板**（先 ripgrep 确认无其他引用）：
- `datasophon-worker/src/main/resources/templates/prometheus.ftl`
- `datasophon-worker/src/main/resources/templates/starrocks-prom.ftl`
- `datasophon-worker/src/main/resources/templates/scrape.ftl`
- Prometheus alert 告警模板（`alert.yml`，若仅 Prometheus 用）

**删除 Java**：
- `datasophon-api/.../master/service/PrometheusService.java`（全删）
- `datasophon-worker/.../handler/PrometheusInstallHandler.java`（全删）
- **保留** `configuration/PrometheusProxyProperties`（看板代理用）
- **保留** `controller/v2/PrometheusProxyV2Controller`（看板代理用）
- 清理 `FreemakerUtils` 中仅 Prometheus 用的渲染分支（`generatePromAlertFile` 等），
  先 ripgrep 确认无其他调用方

**删除 strategy 注册**（若有 `PROMETHEUS` 相关 Strategy）：
- `ServiceRoleStrategyContext`（工作树已有修改，确认 PROMETHEUS 已移除）

**DML 清理**：
在已有 `db/migration/2.2.2/V2.2.2__DML.sql` 中追加（与 GRAFANA/LOKI 同构）：
```sql
-- Phase E: Remove stale alert seed data for PROMETHEUS.
DELETE FROM t_ddh_cluster_alert_rule
WHERE expression_id IN (
    SELECT id FROM t_ddh_cluster_alert_expression
    WHERE service_category = 'PROMETHEUS'
);
DELETE FROM t_ddh_cluster_alert_expression WHERE service_category = 'PROMETHEUS';
DELETE FROM t_ddh_cluster_alert_quota WHERE service_category = 'PROMETHEUS';
DELETE FROM t_ddh_alert_group WHERE alert_group_category = 'PROMETHEUS';
```

**全仓残引用扫描**：
```bash
rg -i "PrometheusService|prometheus\.ftl|starrocks-prom|scrape\.ftl|generatePrometheus|generateSRPromConfig|generateHostPrometheusConfig|generateAlertConfig|prometheusService\." --glob '!**/PrometheusProxy*' --glob '!**/prometheus*_test*'
```
应无业务残引用（`PrometheusProxy*` 和 `PrometheusProxyProperties` 的引用正常保留）。

---

### S5 — 端到端验证（Codex 完成后由 Claude 审查）

1. **单测**：
   ```bash
   JAVA_HOME=$JH17 ./mvnw -pl datasophon-worker -am test -s ~/.m2/setting.xml
   JAVA_HOME=$JH17 ./mvnw -pl datasophon-api -am test -s ~/.m2/setting.xml
   ```
2. **残引用扫描**：`rg` 命令（见 S4），输出应只含 ProxyV2Controller 相关。
3. **编译验证**：`JAVA_HOME=$JH17 ./mvnw -pl datasophon-api -am compile -DskipTests -s ~/.m2/setting.xml`
4. **Spotless**：`JAVA_HOME=$JH17 ./mvnw spotless:apply -s ~/.m2/setting.xml`
5. **沙箱验证**（若可用）：装一个组件 → 该节点 otelcol `config/otelcol.yaml` 含 `prometheus/local` scrape job
   → Doris otel 表出现该 job 指标：`SELECT DISTINCT job FROM otel.otel_metrics_gauge`。

---

## 关键文件速查

| 文件 | 操作 | 阶段 |
|---|---|---|
| `datasophon-worker/src/main/resources/templates/otelcol.ftl` | 修改（通用 scrape receiver） | S1 |
| `datasophon-worker/src/test/.../OtelcolTemplateTest.java` | 扩充断言 | S1 |
| `datasophon-api/.../observability/OtelScrapeConfigBuilder.java` | **新建** | S2 |
| `datasophon-api/.../observability/OtelScrapeConfigBuilderTest.java` | **新建** | S2 |
| `datasophon-api/.../master/service/ServiceCommandService.java` | 替换 Prometheus 调用 | S3 |
| `datasophon-api/.../master/service/WorkerStartService.java` | 替换 Prometheus 调用 | S3 |
| `datasophon-api/.../observability/OtelExporterSwitchService.java` | 简化 doris scrape 拼装 | S3 |
| `datasophon-api/.../observability/OtelCollectorConfigService.java` | 接收 scrapeYaml param | S3 |
| `package/raw/meta/datacluster-physical/PROMETHEUS/` | 删除 | S4 |
| `prometheus.ftl`、`starrocks-prom.ftl`、`scrape.ftl` | 删除 | S4 |
| `master/service/PrometheusService.java` | 删除 | S4 |
| `worker/.../handler/PrometheusInstallHandler.java` | 删除 | S4 |
| `db/migration/2.2.2/V2.2.2__DML.sql` | 追加 PROMETHEUS 清理 | S4 |

**保留不动**：`PrometheusProxyV2Controller`、`PrometheusProxyProperties`、`OtelMetricsQueryController`、`OtelMetricsQueryService`、各 `*Monitor` 看板页面。

---

## 风险与遗留

| 风险 | 说明 | 处理方式 |
|---|---|---|
| 过渡期看板黑屏 | 12 个 `*Monitor` Prometheus 退役后无数据 | 用户已接受；后续 Phase 迁取数层 |
| 端口真相核实 | 个别服务端口非 `jmxPort`（minio/nacos/apisix/DolphinScheduler） | PATH_OVERRIDES 注册表需逐一核对 `prometheus.ftl` 原始定义 |
| 本地就近前提 | 被抓服务须与 otelcol 同节点 | otelcol cardinality `1+`（每节点），前提成立；S5 验证覆盖 |
| worker/master JMX exporter 弃抓 | Phase D 后 `:8585/:8586` 已废弃，不应再抓 | S1 核实后在 `OtelScrapeConfigBuilder` 过滤 |
| 告警覆盖缺口 | Prometheus 规则退役，非 OTel 规则覆盖的服务暂无告警 | 已知缺口；`OtelAlertScheduler` Phase C 的 7 条规则已覆盖 Nexus/Doris |

---

## Codex 实施指引

请 **分阶段（S1→S2→S3→S4）** 提交，每阶段完成后更新本文档对应行的状态（`⬜ 未开始` → `🟡 进行中` → `✅ 已完成`），并记录关键实现决策或偏差。S5（端到端验证）由 Claude 审查执行。

**Codex 实施记录**（请在此追加）：

<!-- S1 完成后在此记录 -->
<!-- S2 完成后在此记录 -->
<!-- S3 完成后在此记录 -->
<!-- S4 完成后在此记录 -->
