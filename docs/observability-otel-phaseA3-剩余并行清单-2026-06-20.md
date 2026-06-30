# 可观测重构 Phase A — A3 剩余工作并行清单(交付 Codex 编码)

## Context

A3a(配置下发主干)已于 4 commits 完成(`7797c699`+`7ebcb8f3`+`0e5e3ab1`+`ad285d9e`,双门禁通过)。
Phase A 控制面只剩 **A3b 监控 / A3c 告警器 / A3d 切换+凭据/schema(合并 A3f) / A3e UI 控制台** 四块,
外加一项独立的 **docs-spotless 清理**。

本文件不是逐行 TDD 计划,而是**并行编排清单**:给出依赖 DAG、两波并行分组、每个 sub-plan 的
范围 / 接口契约 / 文件地图 / 验收 / 关键约束。Codex 据此认领并行任务、自行展开 bite-sized 实现。

**最终交付物**:经批准后此内容写入 `docs/observability-otel-phaseA3-剩余并行清单-2026-06-20.md`(git 跟踪)。

---

## 依赖 DAG 与并行波次

```
A3a ✅(已完成,提供按节点下发原语 OtelCollectorConfigService.pushNodeConfig)
 │
 ├──> A3b 监控(self-metrics 抓取 + 监控 API)
 │     ├──> A3c 告警器(@Scheduled 查 A3b self-metrics)
 │     └──> A3e UI 控制台(监控 tab 消费 A3b API + 配置 tab 触发 A3a push)
 │
 └──> A3d 切换+凭据/schema(改模板/ddl + 新建切换 service,A2 OtelSchemaApplier)

docs-spotless: 完全独立,任意时刻可做
```

|     波次     |                 可并行任务                 |                            互斥原因                             |
|------------|---------------------------------------|-------------------------------------------------------------|
| **Wave 1** | **A3b** · **A3d** · **docs-spotless** | 三者文件地图零重叠(见下方冲突矩阵)                                          |
| **Wave 2** | **A3c** · **A3e**                     | A3c 纯 api `@Scheduled`,A3e 纯 ui;依赖 A3b 的抓取 service / API 契约 |

### 文件冲突矩阵(确认无写冲突才可并行)

|                         文件/区域                         | A3b | A3c | A3d  | A3e | docs |
|-------------------------------------------------------|-----|-----|------|-----|------|
| `datasophon-worker/.../templates/otelcol.ftl`         | -   | -   | ✏️写  | -   | -    |
| `datasophon-worker/.../templates/otelcol-env.ftl`     | -   | -   | ✏️写  | -   | -    |
| `package/raw/meta/.../OTELCOLLECTOR/service_ddl.json` | -   | -   | ✏️写  | 读   | -    |
| 新建 `api/.../observability/OtelSelfMetrics*`(抓取+解析)    | ✏️建 | 读   | -    | -   | -    |
| 新建 `api/.../observability/OtelMonitorController`      | ✏️建 | -   | -    | 读契约 | -    |
| 新建 `api/.../observability/OtelAlertScheduler`         | -   | ✏️建 | -    | -   | -    |
| 新建 `api/.../observability/OtelExporterSwitchService`  | -   | -   | ✏️建  | -   | -    |
| `OtelCollectorConfigService.java`(A3a 产物)             | -   | -   | 可能扩展 | -   | -    |
| 新建 `datasophon-ui-v2/src/pages/<Collector控制台>`        | -   | -   | -    | ✏️建 | -    |
| `docs/*.md`(全角空格表格)                                   | -   | -   | -    | -   | ✏️写  |

> A3d 若需给 `OtelCollectorConfigService` 增 exporterMode 构建分支,会写该文件;A3b 不碰它 → 仍无冲突。
> A3b 与 A3d 唯一交集是 `service_ddl.json`(A3d 写、A3b 不碰)与 A3a 产物(A3d 可能写、A3b 不碰)。

---

## Global Constraints(全 sub-plan 继承)

- 设计真相之源:`docs/observability-otel-doris-设计-2026-06-19.md`(§4.3/§5.4-5.7/§8 F1/F2/F5/F6)。
- 分支 `refactor/observability-otel`;每 Task 一次 commit,Conventional Commits 中文。
- 配置下发主干唯一入口 = A3a 的 `OtelCollectorConfigService.pushNodeConfig(clusterId, hostname, params)`;
  **otelcol 无热加载**,改配置=重启,不得引入 reload 假设。
- 服务名 `OTELCOLLECTOR`、角色名 `OtelCollector`(A1 既定);jmxPort/self-metrics 端口 `8888`。
- **DORIS 就绪判据** = 其服务角色实例达 `ServiceRoleState.RUNNING`(`MasterScheduledService` 15s/30s 巡检维护);
  禁止假设 Doris 在 collector 之前就绪。
- **逐节点非原子(F5)**:本阶段切换原语按节点下发,不引入全局原子切换点。
- **ack 边界与 S3 回灌延期(F2)**:仓库当前无持久化节点 ack/首写成功信号,也无一次性
  `awss3receiver` Worker 命令。2026-06-20 已确认选择方案 2:本轮只交付模板/DDL/凭据/schema/逐节点切换,
  ack/backfill 单独后续实现。
- **告警器独立于 Doris/Phase B(F6)**:只查 collector self-metrics `:8888`,不查 Doris。
- **凭据(F1)**:按集群生成 `otel_collector` 口令,经下发链路注入 `otelcol.env`,替换 A2 的 `CHANGE_ME_AT_A3`;
  不硬编码、不进静态脚本、不记日志。
- `@Async` 方法不叠加 `@Transactional`(本仓库约定,见 datasophon-api/CLAUDE.md)。
- 环境受限如实标注:本机无 Worker/Doris/Rustfs 实例,凡需真实下发/切换/抓取的步骤标"待真实环境",不伪造输出。

### 已核实接口契约(写进各 sub-plan brief,省 Codex 往返)

- `WorkerCallAdapter`(`api/.../master/transport/`):`configureServiceRole(hostname, GenerateServiceConfigCommand)→ExecResult`、
  `restartServiceRole(hostname, ServiceRoleOperateCommand)→ExecResult`、`serviceRoleStatus(hostname, ServiceRoleOperateCommand)→ExecResult`。
- A3a `OtelCollectorConfigService`:`buildConfigCommand(Integer clusterId, String hostname, Map<String,String> params)`、
  `pushNodeConfig(...)→ExecResult`(configure→restart 顺序、configure 失败短路)。
- A2 `OtelSchemaApplier.apply(JdbcClient doris)`(静态,运行期需真实 Doris 连接;`traces_graph_job` 的 CREATE JOB 非幂等待 A3d 容错)。
- `ClusterAlertHistoryService.saveAlertHistory(String alertMessage)`;`AlertService`(@Async("masterExecutor"))为告警处理样板。
- `PrometheusService`(`api/.../master/service/`):"注入 roleInstanceService/hostService + @Async('masterExecutor')"的领域服务样板。
- `MasterScheduledService`:`@Scheduled` 周期巡检样板(节点 30s/300s、角色 15s/30s、集群 30s/60s)。
- HTTP 抓取用 hutool `cn.hutool.http.HttpUtil`(`PrometheusService`/`AlertManagerHandlerStrategy` 已有先例)。
- self-metrics 端点 = `http://{nodeHost}:8888/metrics`,Prometheus 文本格式(模板 `otelcol.ftl` 已暴露 readers/pull/prometheus:8888)。
- 查角色实例 = `ClusterServiceRoleInstanceService`;查主机 = `ClusterHostService`。

---

## Wave 1

### A3b — 监控:self-metrics 抓取 + 监控数据 API

**范围**:为每个 OtelCollector 节点抓取 `:8888/metrics`,解析 otelcol 标准指标(队列水位/吞吐/丢弃/落盘),
暴露监控数据 REST API。**不依赖 Doris**(§5.4 地基)。**不下发配置**(纯读)。

**接口契约(Produces)**:
- `OtelSelfMetrics`(POJO/record):`queueSize` / `queueCapacity` / `sentTotal` / `sendFailedTotal` /
`refusedTotal` / `processorDroppedTotal` 等关键字段(以 otelcol 实际指标名为准)。
- `OtelSelfMetricsClient.fetch(String nodeHost)→OtelSelfMetrics`(hutool HttpUtil 抓 `:8888/metrics` + 解析 Prometheus 文本)。
- `OtelMonitorService.collectAll(Integer clusterId)→List<NodeOtelMetrics>`(遍历 RUNNING 的 OtelCollector 实例,逐节点 fetch,失败节点降级标记不健康)。
- `GET /api/observability/otelcol/monitor?clusterId=` → 统一 `Result`,data 为各节点指标列表。

**关键 otelcol 指标(解析目标)**:`otelcol_exporter_queue_size` / `otelcol_exporter_queue_capacity`(队列水位)、
`otelcol_exporter_sent_*`(吞吐)、`otelcol_exporter_send_failed_*`(发送失败=丢弃)、
`otelcol_receiver_refused_*`、`otelcol_processor_dropped_*`。

**文件地图**:
- Create:`api/.../observability/OtelSelfMetrics.java`、`OtelSelfMetricsClient.java`、`OtelMonitorService.java`
- Create:`api/.../controller/observability/OtelMonitorController.java`(extends `ApiController` — 否则绕过鉴权链,见 A3a 0e5e3ab1 教训)
- Create:测试 `OtelSelfMetricsClientTest`(喂 Prometheus 文本样本断言解析)、`OtelMonitorServiceTest`(mock client + roleInstanceService)

**验收**:解析器对真实 otelcol `/metrics` 文本样本正确提取队列/吞吐/丢弃;collectAll 跳过非 RUNNING、降级失败节点;
controller extends ApiController;真实抓取待真实环境。

**约束**:抓取超时短(避免巡检线程阻塞);解析容错(指标缺失给 0/null 不抛);controller 必须 extends ApiController。

---

### A3d — staged 逐节点切换(F5)+ 凭据/schema 编排(F1,合并 A3f)

**范围**:① 给 otelcol 模板加 `exporterMode`(s3|doris)分支与 Doris exporter 配置;② service_ddl 增 exporterMode + doris 连接参数;
③ DORIS 角色达 RUNNING → `OtelSchemaApplier.apply` + 按集群生成 `otel_collector` 口令注入 `otelcol.env`(替换 CHANGE_ME_AT_A3);
④ 逐节点经 A3a `pushNodeConfig` 下发 exporterMode=doris。ack 边界、首写确认与 `awss3receiver` S3 回灌不在本轮范围,
延期到具备持久化节点状态和一次性 Worker 命令后实现。

**接口契约(Produces)**:
- `OtelExporterSwitchService.switchNode(Integer clusterId, String hostname, ExporterMode mode)→ExecResult`(组装 params → 调 `pushNodeConfig`)。
- `OtelExporterSwitchService.isDorisReady(Integer clusterId)→boolean`(查 DORIS 角色实例是否达 `ServiceRoleState.RUNNING`)。
- `OtelSchemaOrchestrator.applyIfReady(Integer clusterId)`(Doris 就绪→`OtelSchemaApplier.apply(jdbcClient)`,幂等容错 traces_graph_job)。
- `otelcol.ftl` 新增 `<#if exporterMode == "doris">` 分支(doris exporter:endpoint/database/username + env 引用密码),
metrics/logs/traces 三 pipeline 的 exporters 据 mode 切 `[awss3]`↔`[doris]`(或 staged 双写)。
- `otelcol-env.ftl` 增 `OTEL_DORIS_USER` / `OTEL_DORIS_PASSWORD`。

**文件地图**:
- Modify:`datasophon-worker/.../templates/otelcol.ftl`(exporterMode 分支 + doris exporter)、`otelcol-env.ftl`(doris 凭据 env)
- Modify:`package/raw/meta/.../OTELCOLLECTOR/service_ddl.json`(参数加 `exporterMode` + `dorisEndpoint`/`dorisDatabase`/`dorisUser`/`dorisPassword`;
generators includeParams 同步;**md5 hook 不动**)
- Create:`api/.../observability/OtelExporterSwitchService.java`、`OtelSchemaOrchestrator.java`、`ExporterMode.java`(enum s3/doris)
- (可能 Modify)`OtelCollectorConfigService.java`:若 exporterMode 需进 buildConfigCommand 的 params 组装
- Create:测试 `OtelExporterSwitchServiceTest`(mock pushNodeConfig 验证 params 含 exporterMode=doris)、
`OtelSchemaOrchestratorTest`(mock JdbcClient 验证就绪才 apply)、otelcol.ftl 渲染测试补 doris 分支用例(worker 侧渲染测试)

**验收**:模板 doris 分支渲染产出合法 otelcol doris exporter;service_ddl 新参数可被 LoadServiceMeta 解析;
isDorisReady 正确读 RUNNING;applyIfReady 幂等(traces_graph_job 重复 apply 不致命);
口令不硬编码不入静态脚本;真实切换/Doris apply 待真实环境。

**约束**:切换保持逐节点非原子;本轮不伪造 ack 或回灌完成信号;
密码经 env 注入不进 yaml 明文不记日志;traces_graph_job 非幂等容错(吞 already-exists 错误码)。

---

### docs-spotless — 独立清理

**范围**:修 `docs/*.md`(A1/A2/设计文档)表格全角空格对齐,使 `./mvnw test` 无需 `-Dspotless.check.skip=true`。

**文件地图**:Modify `docs/observability-otel-*.md` 中被 spotless:check 拦的 markdown 表格行(全角空格→半角对齐)。

**关键约束**:**只手动修 docs markdown**,**不要跑 `spotless:apply` 全局**——全局 apply 会重排所有 java import,
与 Wave 1/2 在途 java 改动冲突。若必须用工具,限定到 docs 子集。

**验收**:`./mvnw -pl datasophon-api spotless:check`(或全量 test 的 spotless 阶段)对 docs 不再报错;无 java 文件被改动(`git diff --stat` 只含 docs)。

---

## Wave 2

### A3c — 最小 @Scheduled 告警器(F6)

**范围**:`@Scheduled` 周期拉 A3b 的 self-metrics,队列水位/丢弃超阈 → 写 `ClusterAlertHistory` + 通知通道。**独立于 Doris/Phase B**。

**接口契约(Consumes A3b)**:`OtelMonitorService.collectAll(clusterId)` / `OtelSelfMetrics`。
**Produces**:`OtelAlertScheduler`(@Scheduled 方法,遍历集群→评估阈值→`alertHistoryService.saveAlertHistory(msg)`)。

**文件地图**:
- Create:`api/.../observability/OtelAlertScheduler.java`(@Component + @Scheduled;构造注入 OtelMonitorService + ClusterAlertHistoryService)
- (可能)阈值配置进 `application.yml`(queueWatermark / sendFailedRate)
- Create:测试 `OtelAlertSchedulerTest`(mock 指标超阈→verify saveAlertHistory 调用;未超阈→never)

**验收**:超阈触发 saveAlertHistory,未超阈不触发;阈值可配;@Scheduled 周期合理(对齐 self-metrics 30s scrape)。
**约束**:不查 Doris;@Scheduled 不叠 @Transactional;告警去抖(避免每周期重复刷同一告警,参考 AlertService firing/resolved 语义)。

---

### A3e — UI 控制台页面(配置 tab + 监控 tab)

**范围**:Collector 控制台页面。**配置 tab**:基础旋钮(由 service_ddl `parameters` 渲染)+ YAML 兜底,触发 A3a `push`;
**监控 tab**:消费 A3b `monitor` API 展示健康/吞吐/队列/落盘。

**接口契约(Consumes)**:
- `GET /api/observability/otelcol/config?clusterId=`:返回 `service_ddl` 解析后的参数元数据与当前值。
- `POST /api/observability/otelcol/push?clusterId=&hostname=`:body 为完整参数 map;Doris 模式先调 schema 编排,
再经 A3d 切换服务注入凭据并调 A3a 的逐节点下发原语。
- `GET /api/observability/otelcol/monitor?clusterId=`(A3b):返回各 Collector 节点指标。
**Produces**:`datasophon-ui-v2/src/pages/<Collector控制台>/` 页面 + 页面同目录 `service.ts` 取数封装 + Umi 路由注册。

**文件地图**:
- Create:`datasophon-ui-v2/src/pages/Cluster/ObservabilityCollector/`(组件,PascalCase,一组件一文件)
- Create:`datasophon-ui-v2/src/pages/Cluster/ObservabilityCollector/service.ts`(otelcol push/monitor 封装)
- Modify:`OtelCollectorController`:push 接收 body 参数;`GET config` 暴露 DDL 解析后的配置元数据
- Modify:`otelcol.ftl` / `service_ddl.json`:增 `rawYaml` 全量覆盖入口,非空时原样生成 `otelcol.yaml`
- Modify:`datasophon-ui-v2/config/routes.ts`(Umi 路由)、`datasophon-ui-v2/src/pages/Cluster/Layout/index.tsx`(集群菜单入口)
- 遵循 `datasophon-ui-v2/CLAUDE.md`:ProTable(`request` 返回 `{data,success,total}`)、ProForm(`onFinish`→`Promise<boolean>`);
监控表用 `actionRef.reload`;配置元数与监控指标分端点独立取数
- Create:测试(Vitest + @testing-library,mock request prop,screen 全局查询)

**验收**:配置 tab 渲染 service_ddl 参数表单、提交完整 params 调 push;raw YAML 非空时全量覆盖;
监控 tab 调 monitor 渲染各节点指标;`npm run test` / `npm run lint` / `npx antd lint ./src` 绿。
**约束**:antd-pro 组件优先(不手搓 Table+Form);i18n 补齐现有 `zh-CN` / `en-US`;Umi Max 内置 `request` 取数;不破坏 `/ddh` base。

---

## 验证方式(全局)

- 每 sub-plan 单测:`JAVA_HOME=$JH17 ./mvnw -pl datasophon-api -Dtest=<Test> test -s ~/.m2/setting.xml -Dspotless.check.skip=true`(docs-spotless 完成后可去掉 skip)。
- worker 模板渲染测试(A3d):`./mvnw -pl datasophon-worker -Dtest=<RenderTest> test`。
- UI(A3e):`cd datasophon-ui-v2 && npm run test && npm run lint && npx antd lint ./src`。
- 真实端到端(下发 gRPC + 重启 + Doris apply + :8888 抓取)= **待真实 Worker/Doris 环境**,
  ack/backfill 另行实现后再纳入 Rustfs 回灌验证;不伪造环境输出。
- Phase A 收尾:A3b-A3e 各自完成定义勾齐后,过一次整支评审(由本会话 Claude **单道评审**;用户已确认不再走 A1/A2/A3a 的 Codex 对抗第二道门禁)。

## 并行执行给 Codex 的提示

- 认领顺序:先 Wave 1 三路(A3b / A3d / docs-spotless)并行;A3b 的 `OtelSelfMetrics` + monitor API 契约一旦冻结,
  Wave 2 的 A3c / A3e 即可并行启动(A3e 只依赖 API 契约,不依赖 A3b 实现完成)。
- 跨 sub-plan 不得互改对方文件地图内文件;A3d 改 `service_ddl.json` / `otelcol*.ftl` / A3a 产物为独占写区。
- 每路独立 commit,完成各自"验收"即可交回;真实环境步骤统一标注待验。

---

## 验收检查清单(Codex 交回后,我方逐项核验产出物)

> 用法:Codex 各路编码完成后,回到本会话按此清单逐项核验。每项是**可观测的产出物/行为**(grep / 读文件 / 跑测试 / 看 git diff 可证),不是"做没做"。任一项不符 → 打回对应 sub-plan。

### A3b 监控

- [ ] 文件存在:`OtelSelfMetrics` / `OtelSelfMetricsClient` / `OtelMonitorService` / `OtelMonitorController`
- [ ] `OtelMonitorController extends ApiController`(grep 确认,否则绕过鉴权链 — A3a 0e5e3ab1 教训)
- [ ] 解析器对真实 `/metrics` 文本样本正确提取 queue_size/queue_capacity/sent/send_failed(测试断言,非 mock 空壳)
- [ ] `collectAll` 跳过非 `RUNNING` 实例、降级标记失败节点(测试覆盖)
- [ ] 端点 `GET /api/observability/otelcol/monitor?clusterId=` 返回统一 `Result`
- [ ] **纯读**:grep 确认不调 `pushNodeConfig`、不下发配置
- [ ] 单测绿;真实抓取已标注"待真实环境"

### A3d 切换+凭据/schema

- [ ] `otelcol.ftl` 含 `exporterMode` doris 分支 + Doris exporter 块;三 pipeline exporters 据 mode 切换
- [ ] `otelcol-env.ftl` 含 `OTEL_DORIS_USER`/`OTEL_DORIS_PASSWORD`;密码经 env 引用,**grep 确认未硬编码、yaml 无明文**
- [ ] `service_ddl.json` 加 `exporterMode` + doris* 参数;generators `includeParams` 同步;**md5 hook 未改**(diff 确认)
- [ ] `OtelExporterSwitchService.switchNode/isDorisReady` 存在;`isDorisReady` 读 `ServiceRoleState.RUNNING`(非假设就绪)
- [ ] `OtelSchemaOrchestrator.applyIfReady` 就绪才 apply;`traces_graph_job` 重复 apply 容错(吞 already-exists 错误码,测试覆盖)
- [ ] `CHANGE_ME_AT_A3` 占位已被真实注入路径替换:grep 确认无裸占位流向生产配置
- [ ] 切换**逐节点**:grep 确认无单一全局原子切换点(F5)
- [ ] ack 边界/S3 回灌已在本计划明确标记延期,本轮代码不伪造完成信号
- [ ] worker 渲染测试补 doris 分支用例;切换 service 单测绿;真实切换/Doris apply 已标注"待真实环境"

### docs-spotless

- [ ] `./mvnw -pl datasophon-api spotless:check` 对 docs 不再报错
- [ ] `git diff --stat` **只含** `docs/*.md`,**零 java 改动**(确认未跑全局 `spotless:apply` 重排 import)

### A3c 告警器

- [ ] `OtelAlertScheduler` 存在:`@Scheduled` + 构造注入 `OtelMonitorService` + `ClusterAlertHistoryService`
- [ ] 超阈 → `saveAlertHistory` 被调;未超阈 → `never`(测试覆盖两路)
- [ ] 阈值可配(`application.yml`,非硬编码魔数)
- [ ] **不查 Doris**:grep 确认只消费 self-metrics(F6)
- [ ] `@Scheduled` 方法未叠 `@Transactional`
- [ ] 告警去抖:不每周期重复刷同一告警(参考 AlertService firing/resolved 语义)
- [ ] 单测绿

### A3e UI 控制台

- [ ] `datasophon-ui-v2/src/pages/Cluster/<Collector控制台>/` 页面存在;Umi 路由与集群菜单已注册
- [ ] 配置 tab 渲染 service_ddl 参数表单,提交调 `push` 端点
- [ ] 监控 tab 调 `monitor` 端点渲染各节点指标
- [ ] 用 ProTable/ProForm(grep 确认非手搓 vanilla Table+Form);`request` 返回 `{data,success,total}`
- [ ] 配置元数与监控指标分端点独立取数
- [ ] raw YAML 非空时模板原样覆盖,空值时仍使用 DDL 基础参数生成
- [ ] i18n 现有两语种(`src/locales/zh-CN` / `src/locales/en-US`)补齐
- [ ] `npm run test` + `npm run lint` + `npx antd lint ./src` 绿

### Phase A 整体收尾门禁

- [ ] **接口契约对齐**:A3c/A3e 消费的 `collectAll`/`OtelSelfMetrics`/`push`/`monitor` 与 A3b/A3a 实际签名/端点逐字一致(无 `clearLayers` vs `clearFullLayers` 式错位)
- [ ] **并行无串味**:git log 审各路 commit,无互改对方文件地图外文件(尤其 A3d 独占 `service_ddl.json`/`otelcol*.ftl`/A3a 产物)
- [ ] 各 sub-plan 上方验收勾齐后,过一次**整支评审 — 由本会话 Claude 单道评审**(用户已确认不再走 Codex 对抗第二道门禁)
- [ ] 真实环境项统一登记到 `apply-verify.md` / 各 sub-plan"待真实环境",不伪造已验

---

## 核验执行记录(2026-06-21,本会话 Claude 单道评审)

> 方式:读源码 + grep + 实跑测试 + `spotless:apply` 诊断。真实下发/Doris/抓取环境受限,按约定标注"待真实环境"。

### 测试硬证据(全绿)

|            范围            |                      命令                      |              结果               |
|--------------------------|----------------------------------------------|-------------------------------|
| A3b/A3c/A3d api 单测(11 类) | `./mvnw -pl datasophon-api -am test`(免 skip) | 22 用例 0 失败 0 错误,BUILD SUCCESS |
| A3d worker 模板渲染          | `OtelcolTemplateTest`                        | 4 用例 0 失败,BUILD SUCCESS       |
| A3e UI 单测                | `vitest run ObservabilityCollector`          | 2 文件 5 用例 passed              |

### 逐 sub-plan 结论

- **A3b 监控** ✅:`OtelMonitorController extends ApiController`;解析器正确提取 queue_size/capacity/sent/send_failed;`collectAll` 跳过非 RUNNING + 降级失败节点;纯读不调 `pushNodeConfig`;抓取超时 2s。
- **A3d 切换+凭据/schema** ✅:`otelcol.ftl` doris 分支 + 三 pipeline 据 mode 切换;密码走 `${env:OTEL_DORIS_PASSWORD}`,yaml 无明文;`service_ddl` 新参数 + generators `includeParams` 同步(密码只入 env generator)、md5 hook 未改;`isDorisReady` 读 `ServiceRoleState.RUNNING`;`applyIfReady` 就绪才 apply;`traces_graph_job` 吞 already-exists 容错;`CHANGE_ME_AT_A3` 仅 SQL 占位,运行期 `renderSql` 替换为真实凭据(测试断言 `doesNotContain`);逐节点 `switchNode` 无全局原子点。
- **A3c 告警器** ✅:`@Scheduled` + 构造注入;超阈→`saveAlertHistory`、未超阈→never(两路测试);阈值 `@Value` 可配;不查 Doris;未叠 `@Transactional`;`firingAlerts` Set + firing/resolved 去抖。
- **A3e UI** ✅:页面 + 路由 + 集群菜单已注册;ConfigTab 用 ProForm 系列、MonitorTab 用 `ProTable`;`request` 返回 `{data,success,total}`;配置/监控分端点取数;rawYaml 全量覆盖入口;i18n 两语种 + 菜单双语补齐。
- **接口契约对齐 / 并行无串味** ✅:A3c/A3e 消费的 `collectAll`/`NodeOtelMetrics`/`OtelSelfMetrics`/`push`/`monitor`/`config` 与 A3b/A3a 实际签名逐字一致;各路 commit 未互改对方文件地图外文件(A3e 改 `OtelCollectorController`、A3d 改 `OtelSchemaApplier` 均在清单允许范围)。

### ⚠️ spotless 收尾(本次一并修复)

- **本轮全部 java 未格式化**:A3b/A3c/A3d/A3e 的 25 个新建/修改 java 文件 commit 前未跑 `spotless:apply`,已统一 apply 格式化(纯格式,无逻辑改动)。
- **docs spotless 归根项目管,之前漏修**:`docs/*.md` 的 spotless 检查由**根项目 `datasophon`(父 pom)** 执行,`-pl datasophon-api` 扫不到。本清单 §验证方式中"`-pl datasophon-api spotless:check` 对 docs 不再报错"的命令有误——docs 校验须用 `./mvnw spotless:check`(全量)或 `-pl .`。本次已对 5 个 docs(含本文件)统一 apply。
- 收尾后全量 `./mvnw spotless:check` 转绿,`./mvnw test` 不再需要 `-Dspotless.check.skip=true`。

### 真实环境待验项(不伪造)

gRPC 配置下发 + otelcol 重启、Doris `OtelSchemaApplier.apply`、`:8888/metrics` 真实抓取、逐节点切换端到端 —— 均待真实 Worker/Doris/Rustfs 环境;ack 边界与 S3 回灌按 F2 方案 2 延期。
