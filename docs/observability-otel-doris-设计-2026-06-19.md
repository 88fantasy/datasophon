# 可观测重构:OpenTelemetry + Doris 设计文档

> 状态:设计已确认(2026-06-19),进入实施规划
> 分支:`refactor/observability-otel`(整个 epic 一条长分支,各子项目内部小步推进)
> 范围:用 OpenTelemetry 统一采集 + Doris 持久化 + 原生看板/告警,**彻底替换** Prometheus / Loki / Promtail / Grafana / AlertManager

---

## 1. 背景与目标

### 1.1 当前可观测架构(重构起点)

| 层 | 现状实现 |
|---|---|
| 采集(metrics) | 各服务角色暴露 JMX/node exporter(端口记于 `ServiceRoleJmxMap`,如 node_exporter 9100、worker 8585、master 8586);`PrometheusService` 动态生成 Prometheus file_sd scrape 配置(每角色一个 `*.json`),`POST /-/reload` 热加载 |
| 采集(logs) | 每节点 `PROMTAIL`(worker 角色)→ `LOKI`(依赖 MINIO+VALKEY)→ Grafana Explore |
| 存储 | Prometheus TSDB(指标)+ Loki/对象存储(日志) |
| 可视化 | `GRAFANA` 服务;`ClusterServiceDashboardServiceImpl` 从 `cluster_service_dashboard` 取 dashboard URL,直连或经 `GrafanaProxyConfiguration`(Jetty 反代) |
| 告警 | `ALERTMANAGER` 服务;`PrometheusService.generateAlertConfig` 生成规则推 Prometheus;`AlertService`/`ClusterAlertHistory`/`ClusterAlertQuota` 处理历史与阈值 |

> 元数据真相之源:`package/raw/meta/datacluster-physical/<SERVICE>/service_ddl.json`。

### 1.2 目标态

OTel Collector 统一采集三信号(Metrics + Logs + Traces)→ 持久化到 Doris → 原生 UI 看板(SQL 查 Doris)+ 原生定时 SQL 告警。旧栈五个组件(Prometheus/Loki/Promtail/Grafana/AlertManager)全部退场。

### 1.3 已确认的全局决策

| 维度 | 决策 | 理由 |
|---|---|---|
| 信号范围 | Metrics + Logs + Traces | 一步到位统一管道 |
| 存储 | 复用平台 DORIS 服务,专用 `otel` database + 独立资源组 | 省一套基础设施;资源组隔离防拖垮业务 |
| 可视化 | 原生 UI 看板,后端 `JdbcClient` 走 MySQL 协议 SQL 查 Doris,移除 Grafana | 接续在途"监控看板"工作;去除 Grafana 组件 |
| 告警 | 原生 `@Scheduled` 定时查 Doris 评估阈值,复用 `ClusterAlertHistory`/`Quota` + 通知通道;移除 AlertManager + Prometheus rule | 告警评估从 Prometheus 解耦 |
| 采集拓扑 | **每节点 agent 直写 Doris**,无中心 gateway;控制面集中在 Collector 控制台页面 | 控制面/数据面分离:控制集中、写库分散,无 mw1 单点 |
| 限流/重试 | OTel `memory_limiter` / `batch` / `sending_queue` / `retry_on_failure`,配置驱动下发 | 旋钮即 OTel processor/exporter 参数 |
| Traces 来源 | 先修管道,datasophon-api/worker 挂 OTel Java agent 首批埋点 | 大数据服务默认不发 OTLP,初期管道优先 |
| 引导期存储 | staged exporter:S3(Rustfs)兜底 → Doris 就绪后切 dorisexporter,**v1 不回灌** | Doris 在 DAG 中晚于 Collector;Rustfs 在 infra 阶段已就绪 |
| 推进方式 | 按服务灰度;一条长分支 | 风险最低,符合"持续推进" |

---

## 2. 目标态拓扑

```
            ┌─────────────────────────────────────────┐
            │ datasophon-api (mw1) ── 控制面            │
            │  ┌────────────────────────────────────┐  │
            │  │ Collector 控制台页面(新增)         │  │
            │  │  · 监控 tab:各节点 collector       │  │
            │  │    健康 / 吞吐 / 丢弃 / 队列水位     │  │
            │  │  · 配置 tab:receivers/processors/  │  │
            │  │    exporter —— 限流/重试/批量/队列  │  │
            │  └──────────────┬─────────────────────┘  │
            └─────────────────│ gRPC 下发配置 + restart ┘
                 ┌────────────┼────────────┐
                 ▼            ▼            ▼
            [mw1 agent]  [app1 agent]  [app2 agent]   ← OTELCOLLECTOR(每节点 worker 角色/N+)
              filelog      filelog       filelog       (otelcol-contrib v0.154.0)
              promrecv     promrecv      promrecv
              otlp         otlp          otlp
                 │            │            │
                 └────────────┼────────────┘  dorisexporter 各自直写(引导期 awss3→Rustfs)
                              ▼
                     平台 DORIS · otel database(独立资源组)
```

**控制面/数据面分离**:控制集中到 Collector 控制台(配置即真相,经 gRPC 下发,复用 `ServiceConfigureHandler` + restart),数据写库分散到各 agent。限流/重试/批量落到各节点的 OTel processor/exporter 参数上。

**代价(明确记录)**:无中心 gateway = 失去全局节流闸。N 节点同时直写,Doris 瞬时写入压力 = Σ各节点。补偿两层:① 每节点 `batch`+`sending_queue` 削峰;② Doris 侧独立资源组限制可观测库资源占用。

---

## 3. 实施 Roadmap(子项目分解)

每个 Phase 是可独立交付的子项目,后续各自走 spec → plan → implementation。

```
Phase A ── 地基(全局,无灰度)
  A1. OTELCOLLECTOR meta 服务(每节点 worker 角色/N+),otelcol-contrib v0.154.0 + control.sh
  A2. Doris:otel database + 表模型固化 + 独立资源组 + 每节点 batch/queue 削峰
  A3. Collector 控制台页面(监控 tab + 配置 tab)+ staged exporter 切换
  ▶ 验收见 §5

Phase B ── 原生消费层(全局)
  B1. 看板取数:JdbcClient(MySQL 协议)查 Doris,panel 定义 → SQL 聚合;替换 ClusterServiceDashboardServiceImpl + GrafanaProxy
  B2. 原生 UI 看板:接续在途"监控看板"工作,渲染 B1 数据
  B3. 原生告警:@Scheduled 定时查 Doris 评估阈值,复用 ClusterAlertHistory/Quota + 通知通道;替换 AlertManager + Prometheus rule

Phase C ── 按服务灰度迁移采集(循环子任务)
  对每个服务(HDFS→YARN→Kafka→…)依次:
    · metrics:PrometheusService 的 scrape 配置生成 → 改生成 OTel prometheusreceiver 配置
    · logs:该服务 logFile → OTel filelog 配置(下线该服务 Promtail 投递)
    · 验收该服务的看板+告警在 Doris 侧正常,再摘旧链路

Phase D ── Traces(与 C 正交,可并行)
  D1. traces 管道:otlp receiver(agent)→ Doris otel_traces
  D2. datasophon-api / worker 挂 OTel Java agent 自动埋点,导出 OTLP

Phase E ── 旧栈下线 + 清理
  · 移除 PROMETHEUS/LOKI/PROMTAIL/GRAFANA/ALERTMANAGER 五个 meta 服务
  · 删 PrometheusService / GrafanaProxyConfiguration / 仅可观测用途的 Loki+MINIO+VALKEY 依赖
    (注:MINIO 已被 Rustfs 取代,清理时一并核对)
  · 更新 deploy/deployment-standalone.md 等文档
```

**排序理由**:
- 地基(A)是所有后续子项目的公共依赖,且只新增组件、不触碰现有服务运行,可安全全局铺开,无需灰度。
- 消费层(B)排在灰度(C)之前:每迁一个服务都要"验收它的看板+告警正常",B 先就绪才能形成"迁移→看板亮→告警通"的标准闭环。
- 灰度真正单元是"采集"而非"消费":看板/告警横切(查同一套 Doris 表),只有"某服务指标/日志从哪条管道来"按服务可切换。故 B 全局建好,C 做成循环。

---

## 4. Phase A 详细设计

### 4.1 A1 — OTELCOLLECTOR meta 服务

- 路径:`package/raw/meta/datacluster-physical/OTELCOLLECTOR/{service_ddl.json, script/control.sh}`
- 角色:每节点 worker 角色,cardinality `N+`(与 Promtail 同构,每节点一个)
- 二进制:otelcol-contrib **v0.154.0**(含 `dorisexporter` + `awss3exporter` + `prometheusreceiver` + `filelogreceiver` + `otlpreceiver`)
- `service_ddl.json` 的 `configFields` 暴露旋钮:`batch` 大小、`sending_queue` 深度、`retry_on_failure` 次数、采样率、exporter 模式(s3/doris)
- 配置变更链路 ⟢:控制台改配置 → `ServiceConfigureHandler` 生成节点 otelcol YAML → `control.sh restart`
  - otelcol 无原生热加载,采用**优雅重启**(graceful restart),与现有服务配置链路一致
- Worker 侧需新增对应 `*HandlerStrategy`(参考 Promtail 策略)

### 4.2 A2 — Doris 存储

- 平台 DORIS 内建 `otel` database + 独立 **resource group**(限制可观测库 CPU/内存占用,防拖垮业务负载)
- 表模型:`dorisexporter` 自动建 `otel_metrics_*` / `otel_logs` / `otel_traces`;datasophon 只负责 database + 资源组 + 保留期(TTL/分区)管理
- **这套表结构 = Phase B 看板取数 / Phase B3 告警评估的硬契约**,旧栈退场前不得随意变更
- 每节点 `batch` + `sending_queue` 削峰,补偿失去的中心节流

### 4.3 A3 — Collector 控制台页面(新增 UI + 后端)

- **配置 tab**:
  - 基础形态:`configFields` 由 `LoadServiceMeta` 自动渲染表单,近零前端成本
  - 高级形态(增量):结构化旋钮(batch/队列/重试/采样)+ 原始 YAML 兜底覆盖
  - 改配置 → 每节点 otelcol YAML 重生成 → gRPC 下发 → restart
- **监控 tab** ⟢:master 按需轮询各节点 collector self-metrics(`:8888`,Prometheus 格式)取健康/吞吐/丢弃/队列水位
  - 不依赖 Doris,引导期(S3 模式)也能用
- **staged exporter 切换** ⟢:
  - 默认 S3 模式:`awss3exporter` → Rustfs(mw1 `:9040`,S3 API)
  - master 在 `DORIS` 服务安装完成后,自动重生成 Collector 配置切到 `dorisexporter` + restart
  - 控制台可手动覆盖 exporter 模式
  - v1 不回灌:切换后看板从 Doris 时间线开始;引导期 S3 数据仅作 durable 归档,不入表

> ⟢ 三个推荐机制的共同主线:**不让 Phase A 依赖尚未建成的 Phase B**——监控走 self-metrics 而非查 Doris、配置走重启而非未实现的热加载、切换由现成的服务安装完成流触发,使地基阶段能自我闭环验收。

---

## 5. Phase A 验收标准

1. 控制台改限流参数 → 下发到节点 → 合成数据按新参数落 S3(Rustfs)
2. 安装 Doris → 自动切 `dorisexporter` → 合成数据落 `otel_*` 表
3. canary 服务(HDFS)指标经 `prometheusreceiver` 进 Doris,SQL 可查
4. 监控 tab 正确显示各节点 collector 健康/吞吐/队列水位(S3 模式下亦可用)

---

## 6. 关键风险与约束

| 风险 | 缓解 |
|---|---|
| 无中心 gateway,N 节点直写 Doris 瞬时压力叠加 | 每节点 batch+sending_queue 削峰 + Doris 独立资源组 |
| Doris 在 DAG 晚于 Collector,首装无库可写 | staged exporter:S3(Rustfs)兜底,Doris 就绪自动切换 |
| 引导期 S3 数据(OTLP-json)不可 SQL 查 | v1 接受:仅归档防丢,看板从 Doris 切换点开始 |
| Rustfs 仍在 beta | 仅作引导期兜底 sink,稳态数据在 Doris,影响面可控 |
| otelcol 配置嵌套 YAML,标准扁平配置表单不适配 | 基础版 configFields 先行,结构化旋钮+YAML 兜底增量 |
| dorisexporter 表结构是后续看板/告警硬契约 | 旧栈退场前冻结表结构变更 |
| 每节点持 Doris 凭据(凭据面扩大) | 凭据经现有 gRPC 配置下发链路分发,不硬编码 |

---

## 7. 涉及的关键现有代码(改动锚点)

| 子项目 | 锚点 |
|---|---|
| A1/C metrics | `datasophon-api/.../master/service/PrometheusService.java`(scrape 配置生成 → 改 OTel 配置生成) |
| A1 配置下发 | `master/handler/service/ServiceConfigureHandler`、Worker 侧 `*HandlerStrategy` |
| A3/B 看板 | `service/impl/ClusterServiceDashboardServiceImpl.java`、`configuration/GrafanaProxyConfiguration.java` |
| B3 告警 | `master/service/AlertService.java`、`ClusterAlertHistoryServiceImpl`、`ClusterAlertQuotaServiceImpl` |
| meta 服务 | `package/raw/meta/datacluster-physical/{PROMETHEUS,LOKI,PROMTAIL,GRAFANA,ALERTMANAGER}` |
| 端口/JMX 映射 | `ServiceRoleJmxMap`、`load/LoadServiceMeta` |
