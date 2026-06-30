# 可观测重构:OpenTelemetry + Doris 设计文档

> 状态:设计已确认(2026-06-19),进入实施规划
> 分支:`refactor/observability-otel`(整个 epic 一条长分支,各子项目内部小步推进)
> 范围:用 OpenTelemetry 统一采集 + Doris 持久化 + 原生看板/告警,**彻底替换** Prometheus / Loki / Promtail / Grafana / AlertManager

---

## 1. 背景与目标

### 1.1 当前可观测架构(重构起点)

|      层      |                                                                                            现状实现                                                                                             |
|-------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 采集(metrics) | 各服务角色暴露 JMX/node exporter(端口记于 `ServiceRoleJmxMap`,如 node_exporter 9100、worker 8585、master 8586);`PrometheusService` 动态生成 Prometheus file_sd scrape 配置(每角色一个 `*.json`),`POST /-/reload` 热加载 |
| 采集(logs)    | 每节点 `PROMTAIL`(worker 角色)→ `LOKI`(依赖 MINIO+VALKEY)→ Grafana Explore                                                                                                                         |
| 存储          | Prometheus TSDB(指标)+ Loki/对象存储(日志)                                                                                                                                                          |
| 可视化         | `GRAFANA` 服务;`ClusterServiceDashboardServiceImpl` 从 `cluster_service_dashboard` 取 dashboard URL,直连或经 `GrafanaProxyConfiguration`(Jetty 反代)                                                  |
| 告警          | `ALERTMANAGER` 服务;`PrometheusService.generateAlertConfig` 生成规则推 Prometheus;`AlertService`/`ClusterAlertHistory`/`ClusterAlertQuota` 处理历史与阈值                                                 |

> 元数据真相之源:`package/raw/meta/datacluster-physical/<SERVICE>/service_ddl.json`。

### 1.2 目标态

OTel Collector 统一采集三信号(Metrics + Logs + Traces)→ 持久化到 Doris → 原生 UI 看板(SQL 查 Doris)+ 原生定时 SQL 告警。旧栈五个组件(Prometheus/Loki/Promtail/Grafana/AlertManager)全部退场。

### 1.3 已确认的全局决策

|       维度       |                                                    决策                                                    |                                 理由                                 |
|----------------|----------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------|
| 信号范围           | Metrics + Logs + Traces                                                                                  | 一步到位统一管道                                                           |
| 存储             | 复用平台 DORIS 服务,专用 `otel` database + 独立资源组                                                                 | 省一套基础设施;资源组隔离防拖垮业务                                                 |
| 可视化            | 原生 UI 看板,后端 `JdbcClient` 走 MySQL 协议 SQL 查 Doris,移除 Grafana                                               | 接续在途"监控看板"工作;去除 Grafana 组件                                         |
| 告警             | 原生 `@Scheduled` 定时查 Doris 评估阈值,复用 `ClusterAlertHistory`/`Quota` + 通知通道;移除 AlertManager + Prometheus rule | 告警评估从 Prometheus 解耦                                                |
| 采集拓扑           | **每节点 agent 直写 Doris**,无中心 gateway;控制面集中在 Collector 控制台页面                                                | 控制面/数据面分离:控制集中、写库分散,无 mw1 单点                                       |
| 限流/重试          | OTel `memory_limiter` / `batch` / `sending_queue` / `retry_on_failure`,配置驱动下发                            | 旋钮即 OTel processor/exporter 参数                                     |
| **背压/韧性**      | **`file_storage` 磁盘持久化队列**:过载或 Doris 宕机时落盘不丢,恢复后重放;队列水位/丢弃由 self-metrics 告警                              | 无中心 gateway 后,持久化队列替代全局背压,过载时不静默丢遥测(审查 Finding 3)                  |
| **Schema 所有权** | **Datasophon 自管 DDL**:`create_schema=false`,自建并版本化 `otel_metrics`/`otel_logs`/`otel_traces` + 契约测试       | 看板/告警 SQL 不耦合 exporter 自动建表;升级不破契约(审查 Finding 4)                   |
| **写入凭据**       | **按集群隔离的 INSERT-only Stream Load 账号**,与看板读用的 MySQL 协议账号分离;手动轮换(控制台下发);Doris 启用 TLS 则强制                   | collector 永不需要 CREATE/DELETE/DROP,被攻陷 worker 无法投毒/删表(审查 Finding 1) |
| Traces 来源      | 先修管道,datasophon-api/worker 挂 OTel Java agent 首批埋点                                                        | 大数据服务默认不发 OTLP,初期管道优先                                              |
| 引导期存储          | staged exporter:S3(Rustfs)兜底 → Doris 就绪后切 dorisexporter;**Doris 就绪后用 `awss3receiver` 时间窗回灌引导期数据**        | Doris 在 DAG 中晚于 Collector;消除切换前的可见性缺口(审查 Finding 2)                |
| 推进方式           | 按服务灰度;一条长分支;**灰度期旧栈保留至每服务验收通过再下线**                                                                       | 风险最低,符合"持续推进";替代系统证伪前不失去可见性                                        |

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
- `service_ddl.json` 的 `configFields` 暴露旋钮:`batch` 大小、`sending_queue` 深度、`retry_on_failure` 次数、采样率、exporter 模式(s3/doris)、磁盘队列容量上限
- **每节点挂 `file_storage` 扩展 + `sending_queue.storage` 指向它**:过载/Doris 宕机时遥测落盘不丢,恢复后自动重放(替代失去的中心背压)
- 配置变更链路 ⟢:控制台改配置 → `ServiceConfigureHandler` 生成节点 otelcol YAML → `control.sh restart`
  - otelcol 无原生热加载,采用**优雅重启**(graceful restart),与现有服务配置链路一致
- Worker 侧需新增对应 `*HandlerStrategy`(参考 Promtail 策略)

### 4.2 A2 — Doris 存储(schema 自管)

- 平台 DORIS 内建 `otel` database + 独立 **resource group**(限制可观测库 CPU/内存占用,防拖垮业务负载)
- **schema 自管(`create_schema=false`)**:dorisexporter 不再自动建表,由 datasophon 拥有并版本化 `otel_metrics` / `otel_logs` / `otel_traces` 的 DDL
  - 取数方式:在隔离沙箱用 `create_schema=true` 跑一次,**导出 exporter 期望的精确 DDL**,再纳入版本管理(避免手写 schema 与 exporter 写入格式不一致)
  - schema 带**版本号**;Phase B 的看板/告警 SQL 配**契约测试**(跑在固定 schema 上),collector/exporter 升级先过契约测试再放行
- **这套表结构 = Phase B 看板取数 / Phase B3 告警评估的硬契约**,旧栈退场前冻结,变更必须走版本化 + 契约测试
- 写入路径 = **Stream Load(HTTP)**;`create_schema=false` 后 exporter 的 MySQL 端点运行期不再使用
- 每节点 `batch` + `file_storage` 持久化队列削峰,补偿失去的中心节流

### 4.3 A3 — Collector 控制台页面(新增 UI + 后端)

- **配置 tab**:
  - 基础形态:`configFields` 由 `LoadServiceMeta` 自动渲染表单,近零前端成本
  - 高级形态(增量):结构化旋钮(batch/队列/重试/采样)+ 原始 YAML 兜底覆盖
  - 改配置 → 每节点 otelcol YAML 重生成 → gRPC 下发 → restart
- **监控 tab**:展示各节点 collector self-metrics(`:8888`,Prometheus 格式)健康/吞吐/丢弃/队列水位/落盘量;不依赖 Doris,引导期(S3 模式)也能用
- **持续告警器(Phase A 内,Finding 3/F6)**:不只靠控制台按需轮询——Phase A 即落一个**轻量 `@Scheduled` 评估器**,周期拉取各节点 self-metrics,持久化末值,队列水位/丢弃超阈即走通知通道
  - **独立于 Doris 与 Phase B 原生告警**(Phase B 的告警查 Doris;此评估器查 collector self-metrics),避免"A 阶段依赖未建成的 B"
  - 明确:轮询节奏、self-metrics 末值持久化、通知路径;无人值守下队列满/丢弃即告警
- **staged exporter 切换(逐节点,非全局原子,F5)** ⟢:
  - 默认 S3 模式:`awss3exporter` → Rustfs(mw1 `:9040`,S3 API)。**按节点**记录引导期起点(各节点首次 S3 写入)
  - master 在 `DORIS` 安装完成后逐节点重生成配置切 `dorisexporter`(`create_schema=false`)+ restart
  - **切换 ack:某节点 restart 后产生首条成功 Doris 写入,才记录该节点的切换边界(S3 末写时刻)**——gRPC 下发/restart 逐节点可能延迟或失败,故不依赖单一全局切换点
  - 控制台可手动覆盖 exporter 模式;展示各节点切换状态(S3 / 切换中 / Doris-acked)
- **引导期回灌(逐节点边界,Finding 2 + F5)**:Doris 切换后,master 对**每个节点各自**触发一次性回灌
  - 机制:一次性 collector 运行 `awss3receiver`(`endpoint`→Rustfs,`starttime`=该节点引导期起点,`endtime`=**该节点已 ack 的切换边界**)→ `dorisexporter`(写同一套自管表)
  - 收发对偶:awss3receiver 读回 awss3exporter 写出的 OTLP,经同一 exporter 入表,**无需手写 OTLP-json→表映射**
  - 时间窗按节点闭右开,边界取已 ack 的切换点,既不漏(覆盖到该节点真正停写 S3 处)也不与稳态重叠双写
  - 回灌完成前 UI 标注该节点"引导期数据回灌中";完成后看板时间线连续
  - ⚠️ `awss3receiver` 为 **alpha** 组件,见 §6 风险

> ⟢ 三个推荐机制的共同主线:**不让 Phase A 依赖尚未建成的 Phase B**——监控走 self-metrics 而非查 Doris、配置走重启而非未实现的热加载、切换由现成的服务安装完成流触发,使地基阶段能自我闭环验收。

---

## 5. Phase A 验收标准

**功能(顺利路径)**
1. 控制台改限流参数 → 下发到节点 → 合成数据按新参数落 S3(Rustfs)
2. 安装 Doris → 自动切 `dorisexporter` → 合成数据落自管 `otel_*` 表
3. canary 服务(HDFS)指标经 `prometheusreceiver` 进 Doris,SQL 可查
4. 监控 tab 正确显示各节点 collector 健康/吞吐/队列水位/落盘量(S3 模式下亦可用)
5. **逐节点回灌**:切换后对每节点按其已 ack 切换边界回灌,引导期窗口数据补齐入表,看板时间线无断点、无与稳态数据重叠双写
5b. **切换非原子(F5)**:人为让某节点 restart 延迟/失败,验证其仍处 S3 模式期间的数据落在该节点回灌窗口内、不丢、不与稳态重叠

**韧性(故障路径,审查新增)**
6. **持续过载(无人值守)**:注入超过 Doris 摄取能力的合成流量,验证落盘不丢、不静默丢弃;超磁盘预算按策略丢弃;**Phase A 内的 `@Scheduled` 告警器自动触发队列/丢弃告警(无需人工盯控制台)**
7. **Doris 部分宕机**:停 Doris N 分钟,验证遥测落盘缓冲 + 告警器报警;Doris 恢复后队列重放、数据最终一致
8. **schema 契约**:Phase B 的看板/告警 SQL 对固定版本 schema 跑契约测试通过;模拟 exporter schema 漂移时契约测试能拦截

**安全(审查新增)**
9. collector 写入账号仅 INSERT/LOAD 权限,验证其无法 CREATE/DROP/DELETE otel 表;按集群隔离,A 集群凭据无法写 B 集群库
10. Doris 启用 TLS 时 Stream Load 走 TLS;凭据不出现在日志/明文配置回显中
11. **投毒残余风险(F7,如实记录)**:确认 INSERT-only 不阻止"被攻陷 worker 为本集群注入任意遥测";此残余风险在内部信任模型下接受,服务端节点身份校验列为后续增强

---

## 6. 关键风险与约束

|                风险                 |                                                       缓解                                                       |
|-----------------------------------|----------------------------------------------------------------------------------------------------------------|
| 无中心 gateway,N 节点直写 Doris 瞬时压力叠加   | 每节点 batch + `file_storage` 持久化队列削峰 + Doris 独立资源组                                                               |
| 过载/Doris 宕机时静默丢遥测(Finding 3)      | 磁盘持久化队列落盘缓冲 + 队列水位/落盘量 self-metrics 告警 + §5.6/5.7 故障验收;超磁盘预算才丢弃且必告警                                            |
| Doris 在 DAG 晚于 Collector,首装无库可写   | staged exporter:S3(Rustfs)兜底,Doris 就绪自动切换                                                                      |
| 引导期可见性缺口(Finding 2)               | Doris 就绪后 `awss3receiver` 时间窗回灌补齐;回灌前 UI 标注降级                                                                  |
| **`awss3receiver` 为 alpha 组件**    | 回灌为一次性、非关键路径(失败不影响稳态);保留 S3 归档可重试;回灌结果做计数核对                                                                    |
| Rustfs 仍在 beta                    | 仅作引导期兜底 sink + 归档,稳态数据在 Doris,影响面可控                                                                            |
| otelcol 配置嵌套 YAML,标准扁平配置表单不适配     | 基础版 configFields 先行,结构化旋钮+YAML 兜底增量                                                                            |
| otel 表结构是后续看板/告警硬契约(Finding 4)    | schema 自管 + 版本化 + 契约测试(§4.2);`create_schema=false` 防 exporter 升级擅改表                                            |
| 每节点持 Doris 写凭据,信任边界扩散(Finding 1)  | 按集群隔离 + INSERT/LOAD-only(无 CREATE/DELETE/DROP)+ 与看板读账号分离 + TLS + 手动轮换(经配置下发,不硬编码);被攻陷 worker **无法删表/改 schema** |
| **被攻陷 worker 仍可注入假遥测污染看板/告警(F7)** | INSERT-only **不能**阻止投毒;**如实记录为残余风险**,内部信任模型下接受;后续增强:按节点/角色摄取身份 + 服务端校验节点标签 + 隔离不匹配遥测(不在 Phase A 范围)            |
| 凭据轮换 v1 为手动                       | 控制台改账号 → gRPC 下发 → restart;自动轮换列入后续增强,非 Phase A 阻塞项                                                            |

---

## 7. 涉及的关键现有代码(改动锚点)

|     子项目      |                                                  锚点                                                   |
|--------------|-------------------------------------------------------------------------------------------------------|
| A1/C metrics | `datasophon-api/.../master/service/PrometheusService.java`(scrape 配置生成 → 改 OTel 配置生成)                 |
| A1 配置下发      | `master/handler/service/ServiceConfigureHandler`、Worker 侧 `*HandlerStrategy`                          |
| A3/B 看板      | `service/impl/ClusterServiceDashboardServiceImpl.java`、`configuration/GrafanaProxyConfiguration.java` |
| B3 告警        | `master/service/AlertService.java`、`ClusterAlertHistoryServiceImpl`、`ClusterAlertQuotaServiceImpl`    |
| meta 服务      | `package/raw/meta/datacluster-physical/{PROMETHEUS,LOKI,PROMTAIL,GRAFANA,ALERTMANAGER}`               |
| 端口/JMX 映射    | `ServiceRoleJmxMap`、`load/LoadServiceMeta`                                                            |
| A2 schema 自管 | otel DDL 版本化(参考 `migration/DatabaseMigration` 自研执行器模式)                                                |
| A2/A3 凭据     | 复用 `ServiceConfigureHandler` 配置下发链路分发 Stream Load 凭据                                                  |

---

## 8. 安全与韧性设计(对抗性审查整改追溯)

两轮 Codex 对抗性审查(2026-06-19)的 finding 处理映射如下(技术可行性已对 dorisexporter / awss3receiver v0.154.0 官方文档核实):

**第一轮(结构性缺口)**

|  #   |         审查 finding          |                                        决策                                         |           落点            |
|------|-----------------------------|-----------------------------------------------------------------------------------|-------------------------|
| F1 高 | 每节点 Doris 凭据扩大信任边界,无最小权限/轮换 | 按集群隔离 + INSERT/LOAD-only(`create_schema=false` 后无需 CREATE)+ 与看板读账号分离 + TLS + 手动轮换 | §1.3、§4.2、§5.9-10、§6    |
| F2 高 | 引导期可见性缺口,无回灌/告警兜底           | `awss3receiver` 时间窗回灌(收发对偶)+ 灰度期旧栈保留至每服务验收 + UI 降级标注                              | §1.3、§4.3、§5.5          |
| F3 中 | 直写拓扑过载/队列溢出无可辩护故障模式         | `file_storage` 磁盘持久化队列(落盘不丢、恢复重放)+ 队列/落盘 self-metrics 告警 + 过载/宕机验收                | §1.3、§4.1-4.2、§5.6-7、§6 |
| F4 中 | 表结构当硬契约却交 exporter auto-DDL | schema 自管(`create_schema=false`)+ 版本化 + 契约测试 + 升级先过契约                             | §1.3、§4.2、§5.8          |

**第二轮(部分失败/被攻陷下的二阶问题)**

|  #   |            审查 finding             |                                      决策                                       |       落点        |
|------|-----------------------------------|-------------------------------------------------------------------------------|-----------------|
| F5 高 | 单一全局切换点假设原子,逐节点 restart 延迟/失败会漏回灌 | 切换/回灌**按节点**:节点产生首条 Doris 写入才记其切换边界,按各自已 ack 边界回灌                             | §4.3、§5.5、§5.5b |
| F6 高 | Phase A 声称队列告警却依赖排在 B 的告警层        | **最小 `@Scheduled` 告警器提前进 Phase A**,查 collector self-metrics,独立于 Doris/Phase B | §4.3、§5.6-7     |
| F7 中 | INSERT-only 不能阻止投毒,缓解措辞夸大         | **删去"防投毒"表述**,如实记残余风险(内部信任模型接受);服务端身份校验列后续增强                                  | §5.11、§6        |

**核实要点(防臆测)**:
- dorisexporter v0.154.0 支持 `create_schema=false`;关闭后 MySQL 端点仅建表用、运行期忽略,数据走 Stream Load(HTTP)——故运行期写凭据可独立于看板读账号单独授权。
- `awss3receiver` v0.154.0 支持按 `starttime`/`endtime` 时间窗回放、支持 custom `endpoint`(接 Rustfs);**稳定度 alpha**,故回灌设计为一次性、非关键路径、可重试、结果计数核对。
- `file_storage` 扩展 + `sending_queue.storage` 实现磁盘持久化队列;需为每节点设磁盘预算,超预算才丢弃且必告警。

**遗留为后续增强(非 Phase A 阻塞)**:凭据自动轮换、回灌的精确去重(当前靠逐节点 ack 边界闭右开避免重叠)、服务端节点身份校验/遥测投毒防护。
