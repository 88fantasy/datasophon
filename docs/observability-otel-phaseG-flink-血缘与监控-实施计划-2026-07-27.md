# Phase G — Flink 血缘与监控接入 OTel + Doris

> **状态**：计划已定稿，尚未开始实施（P0-P3、P7 待办；**P4/P5/P6 已被取代**）
> **日期**：2026-07-27
> **⚠️ 2026-07-29 更新**：**P4/P5/P6（作业管理 / 血缘采集 / 血缘图）已由 [`docs/data-lineage-平台级血缘架构-2026-07-29.md`](./data-lineage-平台级血缘架构-2026-07-29.md) 取代**。原因：本文档把血缘定位为 Flink 专属能力，但 Flink 恰恰是三类数据作业（Spark / DS SQL / Flink）里唯一发不出 OpenLineage 血缘的一个；Gravitino 落地后带来了官方 Spark 血缘链路，实施顺序应改为 Spark → DS → Flink。**P0-P3、P7（Flink 监控看板 / FLINKCDC / 告警）继续有效**，但 P1、P2 有两处必须按新文档 §L7 修改，见下方对应章节的「2026-07-29 修订」。
> **分支**：待定（建议 `feat/observability-otel-phaseG-flink`）
> **关联 epic**：可观测重构 OTel+Doris（`docs/observability-otel-doris-设计-2026-06-19.md`；Roadmap A-F 已完成，本批为 Phase G）
> **前置阅读**：`docs/observability-otel-phaseF-中间件链路追踪接入-实施计划-2026-07-21.md`（traces 注入机制）、`docs/monitoring/zookeeper-otel-verification.md`（验证文档格式样板）

## Context（为什么做这件事）

平台的可观测栈已完成 OTel + Doris 重构（Phase A–F）：otelcol 每节点直采、Doris `otel` 库 8 张表、查询层（metrics/traces/logs）、G6 服务拓扑、原生告警调度器全部就位。但 **Flink 是这套体系里唯一完全空白的服务** —— 无 `jmxPortParam`、无 metrics reporter、无看板、无告警、无设计文档。

诉求：把 Flink 数仓链路（flinkcdc → ods → dwd → dws → ads）的**血缘**和**监控数据**接进 OTel/Doris 并在 UI 展示。

用户已拍板的约束（2026-07-27 会话）：

- 作业当前由 **StreamPark 等外部平台**提交，平台不管理 Flink 作业
- 分层存储 = **Paimon 分层 + Doris 落 ads，或 Doris 全分层**
- 血缘粒度 = **表级**
- 范围四项全要：监控看板、血缘采集+图页面、新建 FLINKCDC 服务、作业管理

---

## 一、已验证的技术结论（不要重新调研）

### 1.1 阻塞项：`flink-conf.yaml` 在 Flink 2.x 已失效

Flink 官方文档明确：*"Starting with Flink version 2.0, Flink only supports the configuration file `config.yaml`... The previous `flink-conf.yaml` configuration file is no longer supported."*

而 `package/raw/meta/datacluster-physical/FLINK/service_ddl.json` 的 configWriter 写的是 `flink-conf.yaml`，包是 `flink-2.3.0-bin-scala_2.12.tgz`。

**推论：FLINK 现有 17 个参数（内存 / state backend / S3 / historyserver 端口）今天全部静默失效，集群跑的是 Flink 默认值。** 不先修这个，后面加的 reporter 配置会被写进一个没人读的文件，表现为"配置下发成功、Doris 零数据"，极难排查。

好消息：`config.yaml` 同时接受扁平点号键格式（`metrics.reporter.otel.exporter.endpoint: http://...`），所以 **`properties3.ftl` 模板不用动，只改 generator 的 `filename` 字段**。

### 1.2 Flink 原生能力边界

|       能力       |                                    状态                                     |        落点        |
|----------------|---------------------------------------------------------------------------|------------------|
| Metrics → OTLP | 内置 `org.apache.flink.metrics.otel.OpenTelemetryMetricReporterFactory`     | `otel_metrics_*` |
| Traces → OTLP  | 内置 OTel trace reporter，但**只有 `Checkpoint` 和 `JobInitialization` 两种 span** | `otel_traces`    |
| 血缘             | FLIP-314 `LineageVertexProvider` **官方只有 Kafka connector 实现**，无列级血缘        | 不可用              |
| Paimon 原生血缘    | PIP-5 投票通过但 Release 字段为空，实现状态不明；平台也无 Paimon 服务                            | 未来增强             |

**关键推论**：

- Checkpoint span 的属性只有 `startTs`/`endTs`/`checkpointId`/`checkpointedSize`/`fullSize`/`checkpointStatus`/`checkpointType`/`isUnaligned` —— **没有 jobId/jobName**。若所有作业共用一个 `service.name`，checkpoint span 无法归属到具体作业，traces 通路价值归零。
- Checkpoint span 无跨服务父子关系 → `otel:otel_traces_graph_job` 对 Flink 不产出任何边，拓扑图上 Flink 是孤立节点。这是预期，不是缺陷。
- 血缘**只能走 Flink SQL 静态解析**；而 SQL 文本只有"谁管理作业"才有 → **作业管理是血缘的前置依赖，不是可选项**。

### 1.3 `service.name` 命名规范（整个方案的关联键）

|   层级    |            值            |                                   设置位置                                    |
|---------|-------------------------|---------------------------------------------------------------------------|
| 集群兜底    | `flink`                 | `config.yaml` 的 `metrics.reporter.otel.service.name`                      |
| 作业级（必须） | `flink-<jobName-kebab>` | StreamPark 提交时 `-Dmetrics.reporter.otel.service.name=flink-ods-user-sync` |

天然适配现有查询层：`OtelMetricsQueryService.JOB_EXPR = service_name` 且用 `REGEXP`，看板传 `job=^flink` 一次覆盖所有作业；`queryLabels()` 返回的 `jobs` 列表**直接就是作业下拉框数据源**，不用写新接口。

### 1.4 Flink metrics 标签的落库位置

|                             Flink 侧                             |              Doris 列               |
|-----------------------------------------------------------------|------------------------------------|
| `service.name`                                                  | `otel_metrics_*.service_name`（扁平列） |
| `service.version`                                               | `resource_attributes` VARIANT      |
| Flink metric variables（`job_name`/`host`/`tm_id`/`task_name` 等） | **`attributes` VARIANT（MAP）**      |
| `service.instance.id`                                           | **大概率为空**（reporter 无此选项）           |

`service_instance_id` 为空是安全的：`OtelMetricsQueryService.needsFilter()` 把 `.+` 当"不过滤"跳过。但 TM 维度拆分不能靠 instance，必须 `groupBy` 到 `host`/`tm_id`。

---

## 二、架构：三条通路

```text
Flink 作业（StreamPark 提交）
  ├─ metrics ─→ 内置 OTel reporter ─→ OTLP gRPC 127.0.0.1:4317 ─→ 现成 otelcol ─→ otel_metrics_*
  ├─ traces  ─→ 内置 OTel reporter ─→ 同上 ─→ otel_traces（仅 checkpoint/init span）
  └─ 血缘    ─→ datasophon 作业台账（SQL/YAML 文本）─→ 表级静态解析 ─→ MySQL 血缘表
                                                                    └─→ OTLP logs（审计流）
```

**metrics/traces 零自研采集代码**：otelcol 已有 otlp receiver（4317/4318）和 traces/logs/metrics 三条 pipeline，Flink 直推即可。

### 血缘存储决策：MySQL 权威 + OTLP logs 审计

**采用**：血缘快照存 datasophon MySQL（权威，UI 只读它）；血缘**变更事件**经 SLF4J → OTel Java Agent 自动转 OTLP log → `otel_logs`（只写不读的审计流）。

**不采用**"血缘全部落 Doris `otel_lineage_*` 表"，理由：

1. **生命周期不匹配是硬伤**。`otel_logs` 是 `dynamic_partition.time_unit=DAY` 配合滚动删除的遥测生命周期；血缘是元数据 —— 一张 `ads_gmv` 的上游关系三个月不变就不产生新事件，按天滚删会让"当前血缘"凭空消失。
2. **第二跳成本被低估**。新增 Doris 表要改 `OtelSchema.EXPECTED_TABLES` + `DDL_RESOURCES`（schema 升版）、改 `OtelSchemaContractTest`、再加一条 `CREATE JOB`（而 `OtelSchemaApplier.executeCreateJob` 的幂等靠字符串匹配 "already exist" 兜底，每加一条多一份脆弱），并让血缘功能硬依赖 DORIS 已安装。
3. **"推送到 OTel"的诉求由审计流零成本满足**。`bin/datasophon-api.sh:81-91` 已确认 datasophon-api 默认挂 OTel Java Agent 且 `OTEL_LOGS_EXPORTER=otlp` —— 一行 `logger.info(json)` 由 logback 插桩自动转成 OTLP log record 落 `otel_logs`，`scope_name` 即 logger 名。零新依赖、零 collector 改动、零 Doris schema 改动。`otel_logs.body` 有 unicode 倒排索引，按表名全文检索血缘变更史是现成能力。

**纪律（必须写进代码注释和文档）**：MySQL 是唯一权威，UI 任何查询都不读 `otel_logs`，否则半年后会出现两套真相。事件仅在**解析成功且内容哈希变化**时发；单条 JSON 超过 64KB 降级为摘要。

---

## 三、阶段进度表

| Phase |                目标                 | 依赖 |                           状态                            |
|-------|-----------------------------------|----|---------------------------------------------------------|
| P0    | 现场事实核查 spike（不产出生产代码）             | —  | 待办                                                      |
| P1    | Flink metrics/traces 配置接入（DDL 驱动） | P0 | 待办                                                      |
| P2    | Flink 监控看板（前端 + 后端白名单）            | P1 | 待办                                                      |
| P3    | 新建 FLINKCDC 服务                    | P1 | 待办                                                      |
| P4    | ~~Flink 作业管理~~                    | —  | **❌ 已取代** → 血缘架构文档 L1（泛化为 `t_ddh_data_job`，覆盖三种 engine） |
| P5    | ~~血缘采集（表级静态解析）~~                  | —  | **❌ 已取代** → L2/L5/L6（Flink 降为第三个 provider）              |
| P6    | ~~血缘图页面~~                         | —  | **❌ 已取代** → L3（页面路径改 `DataLineage`）                     |
| P7    | 告警规则 + 文档收尾                       | P2 | 待办                                                      |

**取代后的链路**（血缘部分整体迁出本文档）：

```text
P0 → P1 → P2 ─┬→ P7        P3 与 P2 并行
              │
              └→（血缘见 data-lineage-平台级血缘架构-2026-07-29.md：L0→L1→L2→L3）
```

---

## 四、各 Phase 详情

### P0 — 现场核查 spike

把 6 个"猜错就返工"的未知量变成写进文档的事实。产出 `docs/monitoring/flink-otel-verification.md`（格式照抄 `docs/monitoring/zookeeper-otel-verification.md`）。

| # |                     待核实                     |                      方式                       |                                       影响                                        |
|---|---------------------------------------------|-----------------------------------------------|---------------------------------------------------------------------------------|
| 1 | **StreamPark 用哪个 FLINK_HOME/conf**          | 问运维 + 看提交参数                                   | 若 StreamPark 自带 conf，改 DDL 对已提交作业**完全无效**，必须改走 `-D` 动态参数。**整条通路第二个生死点**         |
| 2 | OTel reporter jar 在 `plugins/` 还是 `opt/`    | `find $FLINK_HOME -iname '*otel*'`            | 在 `opt/` 需加 `link` hook（照抄现有 s3-fs-hadoop hook）                                 |
| 3 | traces reporter 的 factory 全限定类名             | `unzip -l <jar> \| grep -i Factory`           | 写错则 JM 启动失败                                                                     |
| 4 | YARN 是否 Docker container runtime            | `grep container-executor.class yarn-site.xml` | 决定 `127.0.0.1:4317` 是否可达（非 Docker 时可达，同 Nacos/DorisFE 已验证机制）                    |
| 5 | 每个 NodeManager 是否都有 RUNNING 的 OtelCollector | 平台服务页                                         | `OtelCollector` 是 `1+` 不是每节点。TM 落到无 collector 的节点 → 指标静默丢失                      |
| 6 | checkpoint 是否真在成功执行                         | Flink UI / 日志                                 | `flink-s3-fs-hadoop-1.16.2.jar` 与 2.3.0 不匹配，若 checkpoint 根本没跑，FL-D segment 失去意义 |

**手工接入验证（正餐）**：在一个 Flink 节点手改 `config.yaml` 加 reporter，用 StreamPark 提交最简作业，然后：

```sql
SELECT DISTINCT metric_name FROM otel.otel_metrics_gauge WHERE service_name REGEXP '^flink' ORDER BY 1;
SELECT DISTINCT metric_name FROM otel.otel_metrics_sum   WHERE service_name REGEXP '^flink' ORDER BY 1;
SELECT CAST(attributes AS STRING) FROM otel.otel_metrics_gauge WHERE service_name REGEXP '^flink' LIMIT 5;
SELECT span_name, count(*) FROM otel.otel_traces WHERE service_name REGEXP '^flink' GROUP BY 1;
```

记录：**真实 metric_name 全集（≥30 行）、attributes key 的确切拼写（`job_name` vs `jobName`）、指标落 gauge/sum/histogram 哪张表、`service_instance_id` 是否为空**。这些直接决定 P2 每一条面板描述符，写错就是空面板。

**验收**：6 项全有明确结论 + 文档含真实 metric_name 清单和 3 条 attributes 样本。

---

### P1 — Flink 配置接入（唯一生产改动是一个 JSON）

**文件**：

- `package/raw/meta/datacluster-physical/FLINK/service_ddl.json`
- 新增 `datasophon-api/src/test/java/com/datasophon/api/load/FlinkDdlLoadTest.java`（照抄 `OtelCollectorDdlLoadTest`，纯 JUnit 无 Spring）

**改动**：

1. **`configWriter.generators[0].filename`：`flink-conf.yaml` → `config.yaml`** ← 阻塞项修复
2. `parameters[]` 新增（同步加进 `includeParams`）：
   - `metrics.reporter.otel.factory.class` = `org.apache.flink.metrics.otel.OpenTelemetryMetricReporterFactory`（`hidden: true`）
   - `metrics.reporter.otel.exporter.endpoint` = `http://127.0.0.1:4317`
   - `metrics.reporter.otel.exporter.protocol` = `gRPC`
   - `metrics.reporter.otel.service.name` = `flink`，`.service.version` = `2.3.0`
   - `metrics.reporter.otel.interval` = `30 SECONDS`
   - `traces.reporter.otel.*` 四项（类名取自 P0 #3），建议给一个开关参数
   - **基数控制**：`metrics.reporter.otel.filter.excludes`，**只排除 subtask 级，必须保留 operator 级**（2026-07-29 修订，原文为「排除 subtask/operator 级」）。理由：血缘图的边级流速在多输入（JOIN）/多输出（STATEMENT SET）作业上无法用 job 级速率归属到具体边，必须靠 source/sink **operator 级**指标（`operator_name` 通常含表名，如 `Source: ods_orders[1]`）做匹配。基数账：20 作业 × 50 subtask ≈ 数万 series（`buildRangeRateSql` 的多层 `LAG` 吃不消）；20 作业 × ~8 operator ≈ 数百 series（可接受）。详见 `data-lineage-平台级血缘架构-2026-07-29.md` §L7。若该选项不存在，退化为 collector 侧 filter processor（要改 `otelcol.ftl`，成本更高）
3. 若 P0 #2 显示 jar 在 `opt/`，给两个角色各加 `POST_INSTALL link` hook，写法逐字照抄同文件已有的 `flink-s3-fs-hadoop` hook
4. 顺手修 `FlinkHistory.externalLink` 硬编码 8082 → `${historyserver.web.port}`（先确认 `externalLink.url` 支持参数插值，不支持则保持现状加注释）

**明确不做**：不给 FlinkClient/FlinkHistory 加 `jmxPortParam`。JM/TM 是 YARN 临时容器端口不固定，`OtelScrapeConfigBuilder` 的 `127.0.0.1:<固定端口>` 模型不适用；OTLP 直推与 scrape 互斥，加了只会重复计数。

**`properties3.ftl` 的固有风险**（写进参数 description）：该模板不做引号转义，值若以 `*`/`&`/`{`/`[` 开头或含 `: `（冒号+空格）会炸 YAML。`30 SECONDS`（含空格）和 `http://...`（含 `://`）作为 plain scalar 都合法。

**验收**：

```bash
./mvnw -pl datasophon-api -am test -Dtest=FlinkDdlLoadTest
# 集群侧：FLINK 保存配置+重启后 cat conf/config.yaml 能看到全部 reporter 键
```

```sql
SELECT count(*) FROM otel.otel_metrics_gauge
 WHERE service_name REGEXP '^flink' AND timestamp > date_sub(now(), INTERVAL 10 MINUTE);  -- > 0
SELECT count(*) FROM otel.otel_traces WHERE span_name='Checkpoint';                        -- > 0
```

---

### P2 — Flink 监控看板

**后端**：

- `datasophon-api/.../observability/OtelMetricsQueryService.java`
  - 第 79 行 `ALLOWED_ATTR_FILTER_KEYS` 加 7 个 key（实际拼写以 P0 采样为准）：`job_name`、`job_id`、`host`、`tm_id`、`task_name`、**`operator_name`**、`subtask_index`。其中 **`operator_name` 于 2026-07-29 由「可选」提级为「必需」**——血缘图的边级流速归属依赖它，见 `data-lineage-平台级血缘架构-2026-07-29.md` §L7
  - **绝不能加名为 `job`/`instance`/`bucket`/`value`/`series_key` 的 key** —— 这些 key 会被逐字拼成 SQL 别名（`buildExtraSelect` 第 1046 行），与 `INST_EXPR`/`JOB_EXPR` 别名撞车生成非法 SQL。上述 7 个安全。
  - 第 86 行 `INSTANT_SERIES_ATTR_KEYS` 是**另一份逐字重复的常量**，控制 instant 查询返回哪些 label，**必须同步加**，否则 instant 面板图例全退化成一条线。**顺手把它改成从单一 `List` 派生 `Set.copyOf(LIST)`**，消除手工同步的雷。
  - `ALLOWED_ATTR_FILTER_KEYS` 同时是 `toValidGroupBy()`（第 1035 行）的白名单，加进去即同时获得 filter + groupBy 能力。
- 新增 `.../observability/FlinkCheckpointQueryService.java` —— 查 `otel_traces` 的 `span_name='Checkpoint'` + `CAST(span_attributes['checkpointStatus'] AS STRING)`，经 `OtelDorisReaderFactory.create(clusterId)` 取连接。**不要塞进已 672 行的 `OtelTracesQueryService`**
- 新增 `.../controller/v2/FlinkObservabilityV2Controller.java` —— `/v2/observability/flink/checkpoints`、`/v2/observability/flink/collector-coverage`（P0 #5 的覆盖率校验，复用 `ClusterServiceRoleInstanceService`，在看板顶栏渲染缺失节点告警条）
- 测试：新增 `FlinkCheckpointQueryServiceTest`（纯 SQL 构造器断言，照抄 `OtelMetricsQueryServiceTest` 风格）+ 在 `OtelMetricsQueryServiceTest` 补白名单新键用例

**前端**（严格照抄 `datasophon-ui-v2/src/pages/monitor/DorisMonitor/` 骨架）：

```text
src/pages/monitor/FlinkMonitor/
├── index.tsx                          # props 必须是 {clusterId?: number; embedded?: boolean}
├── panelQueries.ts + panelQueries.test.ts
├── hooks/useFlinkMonitorDashboard.ts
├── toolbar/FlinkDashboardToolbar.tsx  # 比 Doris 多一个作业下拉框
└── service.ts                         # checkpoint 接口
```

- 作业下拉框数据源 = `/v2/observability/otel/metrics/labels?job=^flink` 返回的 `jobs`，**不用写新接口**
- metrics 复用 `_shared/dorisService.ts`（默认 baseURL `/ddh/api/v2`）；checkpoint 走新 v2 controller **也用默认 baseURL** —— 不要像 `ObservabilityCollector/service.ts:28` 那样加 `{baseURL:'/ddh/api'}`
- 挂载：`src/pages/Cluster/ServiceInstance/index.tsx` 第 176-206 行的 if-else 链加 `isFlink`
- 文档：`docs/monitoring/design/flink-dashboard-prototype-spec.md` + `docs/monitoring/panel-catalog/Flink.json`

**segment 划分**（必须按 segment 分开取数，照抄 `getDorisSegmentPanelIds`，避免一次拉全部超时）：

|      segment       |                                                            面板                                                            |                    数据源                     |
|--------------------|--------------------------------------------------------------------------------------------------------------------------|--------------------------------------------|
| `overview` FL-A    | 活跃作业数、TM 数、可用/总 slot、JM 堆占比、重启次数                                                                                         | metrics `job=^flink`                       |
| `job` FL-B         | 入/出记录速率、反压（`busyTimeMsPerSecond`/`backPressuredTimeMsPerSecond`）、watermark lag、source pending records、numRestarts、uptime | metrics `job=<选中>`，`groupBy=['task_name']` |
| `taskmanager` FL-C | CPU load、堆、GC、网络 buffer                                                                                                  | metrics `groupBy=['host','tm_id']`         |
| `checkpoint` FL-D  | 时长趋势/P99、失败计数、size                                                                                                       | checkpoint 接口（traces）                      |

**时区**：所有 checkpoint 时间戳必须 `dayjs.utc(v).local()`（Doris DATETIME 存 UTC 无时区标记）。

**验收**：`npm run lint && npm run test`；`./mvnw -pl datasophon-api -am test`；浏览器进 FLINK 服务详情页，四 segment 各自出数，切换作业下拉曲线跟随；拔一个 TM 后 FL-C 少一条线。

---

### P3 — 新建 FLINKCDC 服务

**定位**：Flink CDC 3.x 是提交 YAML pipeline 的 CLI 分发包，**无常驻进程** → 参照 `SPARK3`（唯一角色 `SparkClient3`，`roleType: client`，`cardinality: 1+`）建成**纯 client 型单角色服务**，不要臆造 server 角色。

**文件**：

- 新增 `package/raw/meta/datacluster-physical/FLINKCDC/service_ddl.json`
  - `dependencies: ["FLINK"]`，角色 `FlinkCdcClient`（client，1+）
  - configWriter 生成 `conf/flink-cdc.yaml`（复用 `properties3.ftl`）
  - `POST_INSTALL link` hook 把 connector jar 链到 Flink `lib/`
  - 已知坑：`append_line.text` 里的 `${ROOT.X}` **不会被替换**，但 `parameters[].defaultValue` 和 `link.source` **会**
- `package/manifest.json` 加一条记录
- 新增 `datasophon-worker/.../strategy/FlinkCdcHandlerStrategy.java`（仅 Kerberos keytab，逐字照抄 `FlinkHandlerStrategy`）
- `datasophon-worker/.../strategy/ServiceRoleStrategyContext.java` 第 61 行附近注册 `FlinkCdcClient`
- 新增 `datasophon-api/src/test/java/com/datasophon/api/load/FlinkCdcDdlLoadTest.java`

**不需要 DML**：`LoadServiceMeta` 启动自动注册 service_ddl.json。

**验收**：`./mvnw -pl datasophon-api -am test -Dtest=FlinkCdcDdlLoadTest`；向导里出现 FLINKCDC 且依赖链正确；安装后 `bin/flink-cdc.sh --version` 有输出；`docs/` 有提交样例 YAML。

---

### P4 — Flink 作业管理（最小可用）

> **⚠️ 本节已于 2026-07-29 被取代**，请改读 [`data-lineage-平台级血缘架构-2026-07-29.md`](./data-lineage-平台级血缘架构-2026-07-29.md) 的 **L1**。以下内容仅作历史参考——表结构已泛化为 `t_ddh_data_job`（增加 `engine` 维度覆盖 SPARK/FLINK/DS_SQL），不再是 Flink 专属。下方「定位 / 不做」的边界原则**依然有效**，已被新文档继承。
>
> **定位：DataSophon 做"作业台账 + 血缘视图 + 监控入口"，不做作业运行时控制面。**
> **边界原则：任何需要 DataSophon 主动向 Flink/YARN 发出写请求的功能都在范围外；只读查询和本地元数据管理在范围内。** 这一条就能挡住 90% 的范围蔓延。

**做**：

1. 作业登记 CRUD：名称、`otel_service_name`（与监控的关联键）、类型、数仓层级、负责人、StreamPark 外链
2. 作业定义的**版本化文本存储**（SQL / CDC YAML）—— 这是血缘解析的唯一输入，是作业管理存在的根本理由
3. 只读对账：metrics 里活跃的 `service_name` 与台账 diff，报"有作业在跑但没登记"/"登记了但没数据"
4. 跳转：该作业的监控看板（带 job 过滤）、血缘子图、StreamPark 原始页面

**不做**（写进文档防蔓延）：提交/停止/重启作业；savepoint 触发与恢复；jar 上传与依赖管理；SQL 在线校验/调试（需真实 planner，与 P5 刻意避开 flink-table-planner 的决定冲突）；作业调度编排（平台已有 DolphinScheduler 3.4.1）。

**数据模型** `datasophon-api/src/main/resources/db/migration/2.2.5/V2.2.5__DDL.sql`（`DatabaseMigration` 扫目录发现版本，无需注册）：

|                表                |                                                           关键列                                                            |
|---------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| `t_ddh_flink_job`               | cluster_id, job_name, **otel_service_name**, job_type(FLINK_SQL/FLINKCDC_YAML/JAR), dw_layer, owner, external_url, state |
| `t_ddh_flink_job_definition`    | job_id, version, definition_text, content_hash —— **版本化不覆盖**                                                             |
| `t_ddh_flink_lineage_node`      | connector, catalog_name, database_name, table_name, **canonical_name**(唯一键), dw_layer, first_seen/last_seen              |
| `t_ddh_flink_lineage_edge`      | job_id, definition_version, src_node_id, dst_node_id，唯一键 (job_id, definition_version, src, dst)                          |
| `t_ddh_flink_lineage_parse_log` | job_id, definition_version, status, message, parsed_at                                                                   |

**Java**（照抄 `ClusterNodeLabel*` 五件套分层）：5 个 entity + mapper（简单 CRUD 用 MyBatis-Plus 注解，图查询才写 `resources/mapper/FlinkLineageMapper.xml`）+ `FlinkJobService`/`Impl` + `controller/v2/FlinkJobV2Controller.java` + `FlinkJobDiscoveryService`（只读对账，复用 `OtelDorisReaderFactory`，零新依赖）

**前端**：`src/pages/Cluster/FlinkJob/{index.tsx,service.ts,JobFormDrawer.tsx,DefinitionEditor.tsx}`；路由在 `config/routes.ts` 与 `/cluster/:clusterId/observability-collector`（第 160 行）同级

**验收**：新建作业 → 粘贴 SQL 存为 v1 → 改一次产生 v2；对账页列出 metrics 里有但台账没有的 `service_name`；后端测试 + `npm run lint/test` 通过。controller 测试用 `@WebMvcTest` + mock service，绕开 gRPC 18081 端口冲突。

**范围够不够的验证**：P4 完成后用户应能回答"`ads_gmv` 是哪个作业产出的、它现在健康吗、上游断了没"。要改作业本身，点外链去 StreamPark。

---

### P5 — 血缘采集（表级静态解析）

> **⚠️ 本节已于 2026-07-29 被取代**，请改读 [`data-lineage-平台级血缘架构-2026-07-29.md`](./data-lineage-平台级血缘架构-2026-07-29.md) 的 **L2（Spark）/ L5（DS SQL）/ L6（Flink）**。核心变化：Flink 静态解析从「唯一方案」降级为「第三个 provider」，Spark 经 Gravitino 的官方 OpenLineage 链路先行。本节的模块边界原则、canonical_name 规范、CDC YAML 解析器需求**均已被新文档继承**。

**模块边界（关键设计决定）**：解析器放独立包 `datasophon-api/src/main/java/com/datasophon/api/lineage/`，**Calcite/SqlNode 类型绝不外泄**到 service/controller 层，对外只暴露自定义 POJO。将来换 FLIP-314 listener 或 Paimon PIP-5 时只换 parser 实现。

**两个前端解析器（缺一不可）**：

- `parser/FlinkSqlLineageParser.java` —— 基于 `org.apache.flink:flink-sql-parser`（Calcite + Flink 方言，**不引入重量级 flink-table-planner**）。需覆盖：`CREATE TABLE ... WITH (connector=...)`（建表名→connector 映射）、`CREATE CATALOG`/`USE CATALOG`/`USE <db>`（上下文）、`INSERT INTO t SELECT ... FROM a JOIN b`、`CREATE TABLE AS SELECT`、`EXECUTE STATEMENT SET BEGIN ... END`（多 INSERT）
- `parser/FlinkCdcPipelineLineageParser.java` —— **YAML 不是 SQL**。`flinkcdc → ods` 这一跳只存在于 CDC pipeline 定义（`source:`/`sink:`/`route:`/`transform:`），SQL 解析器完全看不到。用 SnakeYAML；`source.tables` 的正则（如 `app_db.\.*`）展开为通配节点，`route.source-table → sink-table` 产生映射边

**表节点身份规范（整个血缘图的地基）**：

```text
canonical_name = <connector>://<catalog|cluster>/<database>/<table>
paimon://prod/dwd/dwd_order  |  doris://ddh/ads/ads_gmv  |  mysql-cdc://10.0.0.5:3306/app_db/orders
```

不做规范化，ods→dwd 的边就跨不了作业（A 写的 `dwd_order` 和 B 读的 `catalog.dwd.dwd_order` 会变成两个节点）。`dw_layer` 由**可配置正则规则**推导（默认按 database 名 `ods`/`dwd`/`dws`/`ads`，其次按表名前缀），支持人工覆盖存 MySQL —— **不要把 `ods_` 前缀硬编码进 Java**。

**其它文件**：

- `lineage/FlinkLineageService.java` —— 解析 → diff → 落血缘表 → 发事件
- `lineage/FlinkLineageEventEmitter.java` —— 专用 logger `com.datasophon.lineage.event`，OpenLineage 风格 JSON；超过 64KB 降级摘要；仅内容哈希变化时发
- `controller/v2/FlinkLineageV2Controller.java` —— `/v2/flink/lineage/graph`（全图 / 按节点 N 跳）、`/v2/flink/lineage/table/{id}`
- `datasophon-api/pom.xml` 加 `flink-sql-parser` 依赖
- 测试：`FlinkSqlLineageParserTest` + `FlinkCdcPipelineLineageParserTest`，用**真实五层样例**（flinkcdc YAML + ods/dwd/dws/ads 四段 SQL）做端到端断言

**验收**：真实 5 个作业定义喂进解析器，产出节点/边与人工画的图一致；`otel_logs` 能查到 `scope_name='com.datasophon.lineage.event'`；重复保存同一 SQL 不产生新事件（哈希 diff 生效）；**解析失败在 `t_ddh_flink_lineage_parse_log` 有可读错误且不阻断作业保存**（解析是旁路）。

---

### P6 — 血缘图页面

> **⚠️ 本节已于 2026-07-29 被取代**，请改读 [`data-lineage-平台级血缘架构-2026-07-29.md`](./data-lineage-平台级血缘架构-2026-07-29.md) 的 **L3**（页面路径由 `FlinkLineage` 改为 `DataLineage`）+ **L7**（边流速叠加）。本节「强复用 TopologyTab」的结论**依然正确且已被继承**。

**文件**：`src/pages/Cluster/FlinkLineage/{index.tsx,LineageGraph.tsx,service.ts,lineageGraph.ts,lineageGraph.test.ts}` + `config/routes.ts`

**强复用** `src/pages/Cluster/ObservabilityCollector/TopologyTab.tsx`：`toGraphData()`（第 54 行，已 export）、G6 v5 `Graph`/`IElementEvent` 用法、`antv-dagre` LR 布局、`fitView`/`zoomTo(0.68)`/`focusElement` 那整段渲染兜底（第 224-245 行，含 `renderFailed` 降级）。血缘图与调用拓扑图形语义几乎同构，**照抄比重写风险低一个数量级**。

**差异点**：节点按 `dw_layer` 分列（CDC→ODS→DWD→DWS→ADS 五列），用 dagre rank 固定层级；点击节点抽屉展示"上下游表 + 产出作业 + 该作业实时指标"（指标调 P2 接口，`job=<otel_service_name>`）。

**验收**：五层渲染成从左到右五列；点任一表展开上下游；导出 PNG（照抄 TopologyTab 的 `DownloadOutlined`）；`npm run lint && npm run test`。

---

### P7 — 告警 + 文档收尾

- `observability/OtelAlertScheduler.java` 第 320 行 `metricRuleSpecs()` 加 4 条（中文名作关联键）：`Flink作业重启次数`、`Flink检查点失败`、`FlinkTaskManager堆内存使用率`、`Flink作业反压`
- `db/migration/2.2.5/V2.2.5__DML.sql` 插 `t_ddh_cluster_alert_quota` 种子（`alert_expr` 存**裸指标名**不存 PromQL；格式照抄 2.2.1 的 704-706 行；`service_role_name` 必须逐字等于 role 名 `FlinkHistory`/`FlinkClient` —— 不要复制历史上 `FLINK-FlinkHistoryServer` 那个对不上的错误）
- `src/pages/Cluster/AlarmManage/otelBuiltinRules.ts` 的 `READONLY_OTEL_BUILTIN_RULE_NAMES` 加同名 4 条
- `OtelAlertSchedulerTest` 补用例
- 文档：`docs/monitoring/flink-otel-verification.md` 定稿

---

## 五、验证命令

```bash
cd /Users/pro/IdeaProjects/datasophon

# 通用（每 Phase 收尾必跑）
./mvnw spotless:apply                    # docs/*.md 的 spotless 归父 pom，-pl 扫不到
./mvnw -pl datasophon-api -am test
./mvnw -pl datasophon-worker -am test    # P3

cd datasophon-ui-v2 && npm run lint && npm run test

# P0 现场核查（只读）
ssh <flink节点> 'ls -la $FLINK_HOME/conf/; find $FLINK_HOME -iname "*otel*"'
./mvnw dependency:get -Dartifact=org.apache.flink:flink-sql-parser:2.3.0

# P1/P2 沙箱 + Doris 直查
docker compose -f deploy/compose/docker-compose.observability.yml up -d
mysql -h127.0.0.1 -P9030 -uroot -e "SELECT DISTINCT metric_name FROM otel.otel_metrics_gauge WHERE service_name REGEXP '^flink'"
./mvnw -pl datasophon-api -am test -Dtest='FlinkDdlLoadTest,OtelMetricsQueryServiceTest,FlinkCheckpointQueryServiceTest'
cd datasophon-ui-v2 && npx vitest run src/pages/monitor/FlinkMonitor

# P4/P5
./mvnw -pl datasophon-api -am test -Dtest='FlinkJobServiceImplTest,FlinkSqlLineageParserTest,FlinkCdcPipelineLineageParserTest'
mysql -h127.0.0.1 -P9030 -uroot -e "SELECT body FROM otel.otel_logs WHERE scope_name='com.datasophon.lineage.event' ORDER BY timestamp DESC LIMIT 3"

# P6 浏览器 E2E（人工）
cd datasophon-ui-v2 && npm run dev       # 联调本地后端，无 mock
```

**新增 `@SpringBootTest` 必须加 `@DirtiesContext`**（否则抢 gRPC 18081，全量测试必挂，且报错表象会伪装成 MySQL 连接失败）。

---

## 六、风险清单

### 会让 epic 大返工的（P0 必须关闭）

|             风险              |                              影响                               |
|-----------------------------|---------------------------------------------------------------|
| `flink-conf.yaml` 不被读取      | **已证实成立**，P1 改 filename 即修复                                   |
| **StreamPark 用自己的 conf 目录** | DDL 改配置对已提交作业零效果，必须改走 `-D` 或统一 FLINK_HOME。**P0 #1**           |
| Checkpoint span 无作业标识       | 若坚持全局单一 `service.name`，FL-D 整个 segment 做不出来 → 采纳 §1.3 每作业命名规范 |

### 必须真实环境验证（读代码无解）

metric_name 确切字符串与所属表 / attributes key 拼写 / `filter.excludes` 选项是否存在及语法 / OtelCollector 在 NodeManager 的覆盖率 / YARN 是否 Docker runtime。

**基数是隐藏的容量风险**：20 作业 × 50 subtask 不过滤 → 数万 series，`otel_metrics_gauge` 日增量和 `buildRangeRateSql` 的多层 `LAG` 都会吃不消。

### 技术选型不确定

- **`flink-sql-parser` 的传递依赖冲突**：Calcite 会带 avatica/guava/protobuf，与 Spring Boot 3.4.5 + gRPC 1.68.1 可能撞版本。**P5 第一天先做 30 分钟 `./mvnw -pl datasophon-api dependency:tree` spike**。Plan B：解析器做成独立 Maven module，或降级为正则+轻量词法（表级粒度下能覆盖 80%，CTE/子查询/视图会漏）。
- **`CREATE VIEW` 展开**：不展开则 dwd→dws 断链。建议 P5 先不展开，在 `parse_log` 标记 `UNRESOLVED_VIEW` 让人工可见，P5.1 再补。

### 已知遗留缺陷：修 / 不修

|                                       缺陷                                       |                              建议                              |
|--------------------------------------------------------------------------------|--------------------------------------------------------------|
| configWriter 写 `flink-conf.yaml`                                               | **必修 P1** —— 它就是本 epic 的阻塞项                                  |
| `FlinkHistory.externalLink` 硬编码 8082                                           | **顺手修 P1**（已经要动这个文件）                                         |
| `INSTANT_SERIES_ATTR_KEYS` 与 `ALLOWED_ATTR_FILTER_KEYS` 手工同步的重复常量              | **顺手修 P2** —— 本 epic 要加 7 个 key，不修就是埋雷                       |
| `flink-s3-fs-hadoop-1.16.2.jar` 与 2.3.0 不匹配                                    | **不修**（独立故障域）。但 P0 #6 若发现 checkpoint 根本没跑，FL-D 失去意义，那时提级为阻塞项 |
| 告警 job 名 `FLINK-FlinkHistoryServer` 与 role 名 `FlinkHistory` 不一致                | **不修**，但 P7 不要复制这个错误                                         |
| api 侧 `FlinkHandlerStrategy` 引用不存在的 `enableKerberos`/`enableJMHA`（今天是彻底 no-op） | **不修**，在 P1 的 PR 描述里点明，防止后来者以为加了参数就能开 HA                     |
| FlinkHistory 在 Kerberos 集群拿不到 keytab                                           | **不修**（当前无 Kerberos 集群），记文档                                  |

### 澄清：两处原以为是缺陷、实际不是

- `ServiceRoleStrategyContext` 未注册 `FlinkHistory` —— **不是缺陷**。`WorkerCommandGrpcService.startServiceRole`（第 263 行）对 strategy 为 null 的角色回落到 `ServiceHandler.start(startRunner...)`，正常执行 `control_history.sh start`，这正是 FlinkHistory 需要的行为。
- worker 侧 `FlinkHandlerStrategy` override 后直接返回成功 —— 对 `client` 型角色是**正确**的（client 角色没有进程要启）。

