# 平台级数据血缘架构设计（Lineage Epic）

> **状态**：架构已定稿，尚未实施（L0-L7 全部待办）
> **日期**：2026-07-29（同日架构讨论补充 **§3.4 内存图与一致性设计**，设计目标 **5000 作业**；§D2 论证已重写）
> **审查**：2026-07-29 经 Codex 两轮对抗性审查，共 4×P0 + 8×P1 + 4×P2 已全部回写
> · 一轮 `019fae1c-edff-7002-82c6-bf005bd70074`（写侧依赖陈旧快照、重建未串行化等）
> · 二轮 `019fae34-3112-7c40-8c05-b971d22258fb`（晚到旧 run 回滚结构、A→B→A 撞唯一键、`stale` 漏报、重建占用请求线程等）
> · **三轮自审**（2026-07-30，对第 1 批已交付代码逐行核对）：3 项已回写。三条**全部源自本文档的"沉默"而非"错误"** —— 规格没说的地方，实现方按字面照做，双方都不觉得自己错
> &nbsp;&nbsp;· **F1** `t_ddh_data_job` 未声明唯一性 → 建成普通索引 → `FOR UPDATE` 锁不住不存在的行 → 并发首次事件产生两个 `job_id`；**且原 L1 验收 8 对此失败模式不可见**（§3.1、验收 8/8b）
> &nbsp;&nbsp;· **F2** `Graphs.hasCycle()` 实测把自环判为环，而本文档同时声明自环"实际一定有" → 告警恒真即失效，须拆 `selfLoopCount` / `hasNonTrivialCycle`（§3.4.4 建图段、验收 20）
> &nbsp;&nbsp;· **F3** `LineageSnapshotMeta.stale/degraded/lastRebuildError` 三字段恒空且重建失败时不置位 → 是诱饵，须删除并改由查询侧现算（§3.4.5 陈旧性契约）
> &nbsp;&nbsp;· **F4** `LineageDdlContractTest` 断言"全仓库最新迁移 = 2.2.5" → 后续任何人加 `2.2.6/` 都会让血缘测试变红。**已决定留到第 2 批返工**，见任务清单 §2.1b R4
> &nbsp;&nbsp;· **F5** `SnapshotLoader` 的「同一只读 REPEATABLE READ 事务、同一连接」契约只在 Javadoc，签名无法强制；第 1 批的读一致性测试用内存 List 模拟翻页，隔离级别配错照样绿 → 事务边界须收归 Coordinator，见任务清单 §2.1b R5 与纪律 ④
> **仍未验证**：§3.4.2 / §3.4.3 的性能数字全为粗估；§3.4.4 的 watermark 取值依赖 L0 #8 的结论。**L1 第一件事是跑 §3.4.8 基准，不是写功能代码**
> **分支**：待定（建议 `feat/data-lineage-platform`）
> **取代范围**：本文档取代 `docs/observability-otel-phaseG-flink-血缘与监控-实施计划-2026-07-27.md` 的 **P4/P5/P6** 三个 Phase；该文档的 P0-P3、P7（Flink 监控看板 / FLINKCDC 服务 / 告警）继续有效，不受影响
> **前置依赖（已满足）**：GRAVITINO 1.3.0 已于 2026-07-29 合并进 main（PR #36）并在五节点沙箱 ddh-02 实机 RUNNING，见 `docs/gravitino-metadata-service-实施计划-2026-07-28.md`
> **关联 epic**：可观测重构 OTel+Doris（Roadmap A-F 已完成）

---

## Context（为什么另起一份文档）

Phase G 把血缘定位成「Flink 专属能力」，P4→P5→P6 的链路是：作业管理 → Flink SQL 静态解析 → 血缘图。

2026-07-29 的架构讨论推翻了这个前提，核心发现是 **Flink 恰恰是三类数据作业里唯一发不出血缘的那一个**。继续按 Flink 优先实施，等于把成本最高、成熟度最低的 provider 放在最前面，且拿不到任何可用于验证图渲染设计的真实数据。

同时 GRAVITINO 已经落地并跑通，它带来了 Phase G 写作时不存在的能力：**官方 Spark 血缘链路 + dataset identifier 规范化**。

因此把血缘从「Flink 的一个子功能」提升为**平台级 epic**，Flink 降级为其中一个 provider。

---

## 一、已验证的技术结论（不要重新调研）

### 1.1 三类数据作业的血缘上报能力矩阵

|            作业类型             | 能否原生上报 OpenLineage |                                                                                            依据                                                                                             |             结论              |
|-----------------------------|--------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------------|
| **Spark**                   | ✅ 官方现成，**含列级血缘**   | `spark.extraListeners=io.openlineage.spark.agent.OpenLineageSparkListener` + `GravitinoSparkPlugin`（来自 gravitino-spark-connector）；覆盖 Hive / Iceberg / Hudi / Paimon / JDBC / GVFS fileset | **第一个实施的 provider**         |
| **Flink**                   | ❌                  | FLIP-314 的 dataset 血缘依赖 connector 实现 `LineageVertexProvider`，**至今只有 Kafka**。Paimon / Doris / MySQL-CDC 全部产出空 dataset；且集成主要面向 DataStream API，SQL/Table API 覆盖更差                            | 只能静态 SQL 解析，**最后实施**        |
| **DolphinScheduler SQL 任务** | ❌                  | DS 无原生 OpenLineage 集成，社区 [discussion #6596](https://github.com/apache/dolphinscheduler/discussions/6596) 至今未落地；生产实践均为自建                                                                   | 拉 DS API 取 SQL 文本 → 复用同一解析器 |

**关键推论**：血缘的实施顺序必须是 **Spark → DS → Flink**，与 Phase G 原计划相反。Spark 这条能最快产出真实的表级/列级血缘数据，用来验证图渲染、identifier 规范化、边语义设计——这些全是后两个 provider 要复用的地基。

### 1.2 Gravitino lineage 模块的真实边界

Gravitino 1.3.0 的 lineage 是**事件管道（event pipeline）**，不是元数据存储：

|                配置项                 |                          默认值                           |
|------------------------------------|--------------------------------------------------------|
| `gravitino.lineage.source`         | `http`（端点 `POST /api/lineage`，走 webserver 的 8090）      |
| `gravitino.lineage.processorClass` | `org.apache.gravitino.lineage.processor.NoopProcessor` |
| `gravitino.lineage.sinks`          | `log`                                                  |
| `gravitino.lineage.queueCapacity`  | `10000`                                                |

内置 sink 仅两个：**log sink**（写 `gravitino_lineage.log` 文件）、**HTTP sink**（转发给 OpenLineage 兼容服务）。

**不落 entity store（即不写 `gravitino` 那个 MySQL 库）、无血缘查询 REST、Web UI 不展示。**

**推论**：Gravitino 不能当血缘后端。但它在链路里有一个不可替代的作用——**把 OpenLineage 的 dataset identifier 转换成 Gravitino 的 metalake/catalog 格式**，这正好解决血缘图最容易翻车的节点身份统一问题（见 §3.3）。

### 1.3 OpenLineage → OTel trace 的映射

OTel 社区正在孵化 `pipeline.*` 语义约定（[semantic-conventions#3762](https://github.com/open-telemetry/semantic-conventions/issues/3762)），提案原文明确：**"An OpenLineage RunEvent maps to a `pipeline.run` span"**，目标覆盖 Spark / dbt / Airflow / Glue / Databricks。

映射关系天然成立：

|                  OpenLineage                   |          OTel span           |
|------------------------------------------------|------------------------------|
| `run.runId`（UUID，16 字节）                        | `trace_id`（正好 16 字节）         |
| `eventType: START / COMPLETE / FAIL`           | span 起止 + `status_code`      |
| `ParentRunFacet`                               | `parent_span_id`             |
| `job.namespace` + `job.name`                   | `service_name` + `span_name` |
| `OutputStatisticsOutputDatasetFacet`（rowCount） | `span_attributes`            |

**注意**：#3762 仍是 proposal，未 stable。现在采用即为自定义属性，规范定稿后需跟随改名（见 §6 风险）。

### 1.4 为什么不引入 OpenMetadata

评估过并否决。OpenMetadata 血缘图的边点开只有 **Source / Target / Description / SQL Query**，全是静态元数据；节点上的 usage/profiler 是批量画像（昨天多少行、几个质量测试通过），**图上没有任何 throughput / freshness / execution-rate**。

即引入之后「流速」仍需自己从 Doris 查再叠加，而它的图是自有前端，要改边标签只能改源码或用 API 自己重画——一旦自己重画，OpenMetadata 就只剩存储价值，而存储恰是本方案自研成本最低的部分。

代价侧：Java server + MySQL/Postgres + Elasticsearch/OpenSearch + Airflow ingestion 一整套，且与刚落地的 Gravitino 在「元数据服务」位置上正面重复。

**若将来诉求变为完整数据治理**（多源自动采集、数据质量、术语表、资产权限），OpenMetadata 是最好的开源选择，但那是独立 epic，且与 Gravitino 二选一。

---

## 二、架构

### 2.1 总览

> ⚠️ **2026-07-30 L0 核查纠正**：下图原标注 Gravitino 做「identifier 规范化」，**这是错的**。
> Gravitino 的 lineage 模块只有 `NoopProcessor`，**逐字透传不改写任何字段**；
> `GravitinoSparkPlugin` 是 catalog 插件，与 OpenLineage 事件生成无关。
> dataset 命名完全由 `openlineage-spark` 决定。证据见
> [`docs/monitoring/data-lineage-verification.md`](./monitoring/data-lineage-verification.md) §3。

```text
Spark 作业 ──OpenLineage listener──┐
                                   ├→ Gravitino :8090 /api/lineage
                                   │    （只接收 + 转发，NoopProcessor 不改写字段）
                                   │         └─ 内置 http sink ─→ ┐
Flink SQL  ──静态解析（L6）────────┤                              │
DS SQL 任务 ──拉 DS API 取 SQL 文本─┘（复用同一解析器）           │
                                                                 ↓
                                              datasophon-api  POST /v2/lineage
                                                      │
                    ┌─────────────────────────────────┼──────────────────────────┐
                    ↓                                 ↓                          ↓
          MySQL t_ddh_lineage_*              OTLP pipeline.run span      （解析失败）
          结构 · 权威 · 长期 · 图遍历          → otel_traces               parse_log
                    │                                 │
                    │                                 ↓ Doris CREATE JOB 预聚合
                    │                          血缘边流量表（UNIQUE KEY，不滚删）
                    │                                 │
                    └──────── API 层 join ────────────┘
                                    ↓
                    血缘图页面（G6，复用 TopologyTab）
                    节点=表 · 边=数据流 · 边标签=流速/批量
```

**一份输入，三个出口，各用强项**：MySQL 管图结构，`otel_traces` 管单次运行诊断，预聚合表管边上的流量标签。

### 2.2 四个关键决策

#### D1 — Gravitino 只做转发，**不做二次开发**

Gravitino 内置 HTTP sink 本就是为转发给 OpenLineage 兼容服务设计的。datasophon-api 暴露一个兼容端点接住即可，**零 fork、零 Gravitino 源码改动**。

理由：一旦 fork，每次 Gravitino 升版本都要 rebase 自研 sink，而这个 sink 干的活（写库）datasophon-api 本来就在干；同时血缘功能会硬依赖 GRAVITINO 已安装且 RUNNING。

~~保留 Gravitino 在链路里的唯一理由是 **identifier 规范化**（§3.3），不是转发本身。~~

> ⚠️ **2026-07-30 L0 核查推翻了这条理由**：Gravitino **不做 identifier 规范化**（§3.3 已纠正）。
> 「零 fork、零源码改动」的结论仍然成立，但**留它在链路里的理由需要重述**：
>
> |  它实际提供的   |                                     说明                                     |
> |-----------|----------------------------------------------------------------------------|
> | HTTP 收集端点 | 现成的 OpenLineage 兼容 `POST /api/lineage`，返回 201                              |
> | 异步队列      | `gravitino.lineage.sinkQueueCapacity`，削峰                                   |
> | 转发与认证     | http sink 支持 `authType` ∈ {`apiKey`, `none`}，走 OpenLineage `TokenProvider` |
>
> 这些价值不为零，但**远小于原判断**，而代价是血缘链路硬依赖 GRAVITINO RUNNING。
> **L2 开工前应重新评估**：让 Spark 的 OpenLineage transport 直连 datasophon-api
> `/v2/lineage`（跳过 Gravitino）是否更简单 —— 我方端点本就兼容 OpenLineage，
> 队列与认证也都在我方可控范围内。证据见
> [`docs/monitoring/data-lineage-verification.md`](./monitoring/data-lineage-verification.md) §3.3。

#### D2 — 血缘结构存 **MySQL**，不存 Doris

> **本决策的论证已于 2026-07-29 重写。** 原论证是「多跳遍历在 Doris 上需递归 CTE，性能不如 MySQL 邻接表」—— §3.4 的纯投影模型落地后**这条理由整个失效了**（MySQL 侧已不做任何图遍历）。结论不变，但真正的理由是下面两条，与查询模式无关。

##### 理由一：数据归属（决定性）

**Doris 在本平台是「被管理的服务」，不是平台自身的依赖。** 它躺在 `meta/datacluster/DORIS/`，是 datasophon 部署出来的 27+ 个服务之一。可观测栈用它存 OTel 数据，那是"平台管理的集群里跑着 Doris"，不是"平台依赖 Doris"。

血缘元数据若存 Doris：

- 用户**没装 Doris** → 血缘功能整个不可用
- Doris 重装 / 挂了 → **元数据丢失**（不是遥测丢失）
- 平台核心元数据依赖平台自己部署的服务 → 循环依赖

而 datasophon-api 对 MySQL 本就是强依赖（几百张 `t_ddh_*` 表 + 自研 `DatabaseMigration`），血缘搬去 Doris **不减少任何组件**，只会让它成为唯一一张不在 MySQL 的元数据表。

##### 理由二：写入模式

"MySQL 只做持久化"是**读侧**的描述，写侧一点没退化：

|             操作              |             频率              |                                               Doris 上的问题                                                |
|-----------------------------|-----------------------------|---------------------------------------------------------------------------------------------------------|
| `last_seen` 更新              | **15～20 万行/天**（每事件 3～4 个节点） | **若逐事件小批写入**则每次产生 tablet 版本，compaction 跟不上即 `-235 too many versions`；攒批可缓解，但攒批本身又与"实时更新 last_seen"的诉求冲突 |
| node + edge + definition 写入 | 几十次/天                       | **需跨三表事务** —— Doris 单表导入原子，跨表不行，会出现"写了 node 没写 edge"的半成品                                                |
| `is_current` 翻转             | 几十次/天                       | UNIQUE KEY 表上的条件 UPDATE 需先读后写，是重操作                                                                      |
| `t_ddh_data_job` 台账 CRUD    | UI 操作                       | DELETE 同样是重操作                                                                                           |

此外 Doris 小查询有 FE 解析 + BE 调度的固定开销（几十 ms），扫 2 万行**不会比 MySQL 覆盖索引更快**。

##### 判据

|            血缘的哪部分             |           存储            |       理由       |
|-------------------------------|-------------------------|----------------|
| 节点 / 边（**结构**）                | **MySQL**               | 元数据，长期有效，写入需事务 |
| 作业台账 / 定义文本                   | **MySQL**               | 同上             |
| `pipeline.run` span（**单次运行**） | **Doris** `otel_traces` | 遥测，量大，按天滚删     |
| 边流量聚合（§L7）                    | **Doris** UNIQUE KEY 表  | 遥测衍生，追加为主      |

一句话判据：**丢了要紧的进 MySQL，不要紧的进 Doris。**

#### D3 — 作业**运行**建模成 `pipeline.run` span；血缘**图本身**不建模成 trace

这是两件事，可行性相反。

**可行（作业运行 → trace）**，白送的能力：DS 工作流实例 = 一个 trace，每个 task = 一个 span → 现有 trace 详情页直接能画工作流瀑布图，定位"昨晚这条链路 42 分钟，哪个 task 拖的"。平台目前完全没有此能力。

**不可行（血缘图 → trace）**，四条硬冲突：

|     冲突      |                                  说明                                   |
|-------------|-----------------------------------------------------------------------|
| tree vs DAG | span 只有一个 `parent_span_id`，trace 是树；血缘是 DAG——一张表被 3 个作业读 = 1 节点 3 条出边 |
| 生命周期        | trace 按天滚删，血缘要长期有效。今天没跑的作业其血缘边会消失                                     |
| 重复爆炸        | 5 分钟一跑的作业一天 288 个 trace，同一条边重复 288 次                                  |
| 语义错位        | **表不是 span**。span 是"一段时间内发生的操作"，表是持续存在的实体——一张表的 `duration` 是什么？       |

**桥接方式已在仓库中存在**：`otel_traces_graph` 是 **UNIQUE KEY 模型（非动态分区滚删）**，由 `CREATE JOB otel:otel_traces_graph_job` 每 10 分钟从 `otel_traces` 聚合。这正是"从会过期的 trace 提炼长期图结构"的现成范式，服务拓扑图已跑通，血缘边流量表照抄即可。

> 更准确的表述：血缘的**图结构**不能存在会滚删的表里，但**可以**存在 UNIQUE KEY 聚合表里。分层，而非二选一。

#### D4 — 边上的「流速」必须**按作业类型分型**

一旦 provider 从 Flink 扩展到 Spark/DS，"流速"就不是统一语义：

|    作业     | 真实语义 |         边标签展示         |                    数据来源                    |
|-----------|------|-----------------------|--------------------------------------------|
| Flink（流）  | 真·速率 | `1.2k rec/s` + 反压     | `otel_metrics_*`（`numRecordsOutPerSecond`） |
| Spark（批）  | 批次画像 | `320万行 · 12分钟前`       | **OpenLineage 事件自带** rowCount + duration   |
| DS SQL（批） | 批次画像 | `影响 8.2万行 · 今日 03:00` | 同上                                         |

不分型会导致图上一条边写 `1.2k rec/s`、旁边写 `320万行/次`，无法横向比较。统一抽象为**「最近一次数据流动」**，流/批用不同边样式（实线动画 vs 虚线）区分。

**重要推论：批作业的流量不走 OTel metrics**——OpenLineage 事件本身就带 `OutputStatisticsOutputDatasetFacet`（rowCount / fileCount / size）和 run 起止时间，一个数据源搞定。

### 2.3 存储分层总表

|      数据      |           存储            |   生命周期    |      查询方       |
|--------------|-------------------------|-----------|----------------|
| 血缘节点 / 边（结构） | MySQL `t_ddh_lineage_*` | 长期，版本化    | 血缘图 API（图遍历）   |
| 作业台账 / 定义文本  | MySQL `t_ddh_data_job*` | 长期，版本化不覆盖 | 作业管理页          |
| 单次运行（span）   | Doris `otel_traces`     | 按天滚删      | trace 瀑布图、单次诊断 |
| 边流量（聚合）      | Doris 新表（UNIQUE KEY）    | 长期        | 血缘图边标签         |
| Flink 实时速率   | Doris `otel_metrics_*`  | 按天滚删      | 血缘图边标签（流作业）    |
| 血缘变更审计       | Doris `otel_logs`       | 按天滚删      | **只写不读**（见纪律）  |

**纪律（必须写进代码注释）**：MySQL 是血缘的唯一权威，UI 任何查询都不读 `otel_logs`，否则半年后会出现两套真相。

---

## 三、数据模型

### 3.1 MySQL 表（`db/migration/2.2.5/V2.2.5__DDL.sql`）

当前最新迁移版本为 `2.2.4`，新建 `2.2.5`。`DatabaseMigration` 扫目录发现版本，无需注册。

由 Phase G 的 P4 表结构**泛化**而来（去掉 `flink` 前缀，增加 engine 维度）：

|              表              |                                                                                                关键列                                                                                                 |                                       说明                                        |
|-----------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------|
| `t_ddh_data_job`            | cluster_id, job_name, **engine**, **otel_service_name**, **current_structural_hash**, **current_watermark**, job_type, dw_layer, owner, external_url, state；**唯一键 (cluster_id, engine, job_name)** | join 键 + 当前结构哈希 + 顺序水位，见 §3.4.4。⚠️ 唯一键**必须是 UNIQUE 而非普通索引** —— 见下               |
| `t_ddh_data_job_definition` | job_id, version, definition_text, content_hash；**唯一键 (job_id, version)**                                                                                                                           | **版本化不覆盖**。⚠️ 唯一键**不能**用 `(job_id, content_hash)` —— A→B→A 回退必冲突（Codex 二轮 P0-1） |
| `t_ddh_lineage_event`       | **唯一键 (producer, run_id, event_type)**, job_id, run_started_at, received_at, status                                                                                                                | **投递幂等表**：重试 / 重复投递在此拦截，见 §3.4.4                                                |
| `t_ddh_lineage_node`        | connector, catalog_name, database_name, table_name, **canonical_name**(唯一键), dw_layer, first_seen, last_seen                                                                                       | 见 §3.3                                                                          |
| `t_ddh_lineage_edge`        | job_id, definition_version, src_node_id, dst_node_id, **is_current**；唯一键 (job_id, definition_version, src, dst)                                                                                    | `is_current` 见下                                                                 |
| `t_ddh_lineage_parse_log`   | job_id, definition_version, status, message, parsed_at                                                                                                                                             | 解析是旁路，失败不阻断作业保存                                                                 |
| `t_ddh_lineage_generation`  | 单行计数器，结构写事务内 +1                                                                                                                                                                                    | 快照代际协调点，见 §3.4.5                                                                |

**`is_current` 的作用（必需，非可选）**：边表版本化不覆盖，5000 作业规模下物理行数约 15～40 万，而当前边仅 1.5～2 万。若不加此列，"取每个 job 的最新版本边"只能写成 `JOIN (SELECT job_id, MAX(definition_version) ... GROUP BY job_id)`，每次重建都要多做一次全表 GROUP BY。写入新版本时把该 `job_id` 的旧行置 0。

```sql
ALTER TABLE t_ddh_lineage_edge ADD COLUMN is_current TINYINT NOT NULL DEFAULT 1;
ALTER TABLE t_ddh_data_job     ADD COLUMN current_structural_hash CHAR(64) NULL;
ALTER TABLE t_ddh_data_job     ADD COLUMN current_watermark      BIGINT   NULL;  -- 顺序水位，见 §3.4.4
ALTER TABLE t_ddh_data_job_definition ADD UNIQUE KEY uk_job_version (job_id, version);
ALTER TABLE t_ddh_data_job     ADD UNIQUE KEY uk_data_job_identity (cluster_id, engine, job_name);

-- 投递幂等：同一 (producer, runId, eventType) 只处理一次
CREATE TABLE t_ddh_lineage_event (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  producer VARCHAR(255) NOT NULL, run_id VARCHAR(64) NOT NULL, event_type VARCHAR(16) NOT NULL,
  job_id BIGINT, run_started_at DATETIME(3), received_at DATETIME(3), status VARCHAR(16),
  UNIQUE KEY uk_event (producer, run_id, event_type)
);

-- ⚠️ 索引列待定：下面是起点而非结论
KEY idx_edge_current (is_current, src_node_id, dst_node_id)
```

> **作业身份必须是 UNIQUE，不能是普通索引**（三轮自审 F1）：原表格只列字段、**未声明唯一性**，实现方照字面建成普通 `KEY`，本文档第一版即是如此。后果不在读侧而在写侧 —— §3.4.4 靠 `SELECT ... FOR UPDATE` 锁 job 行做条件写入，但**行不存在时 `FOR UPDATE` 锁不住任何东西**。两个并发的首次事件各自 `INSERT`，同一逻辑作业得到**两个 `job_id`**，从此两条 `is_current` 边链并存 —— 正是本节要防的"多个 current 并存"，只是从边层下沉到了作业层。
>
> 写路径必须 `INSERT ... ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)` 抢占身份行，拿到 `job_id` 后再 `FOR UPDATE`。
>
> ⚠️ **这个失败模式对原 L1 验收 8 是不可见的**：该条按 `WHERE job_id = ?` 断言，两个 `job_id` 各自自洽，测试全绿。验收条件是从同一份规格推导的，因而**继承了规格的盲区**。已在验收 8 补充作业身份行数断言。
>
> **为什么不能用 `(job_id, content_hash)` 做唯一键**（Codex 二轮 P0-1）：作业结构从 A 改到 B、再回退到 A 是**合法的日常操作**（发布回滚）。全历史内容唯一会让第三次写入直接撞唯一键。幂等应该由 `t_ddh_lineage_event` 的投递去重承担，而不是压在结构历史表上 —— **投递幂等与结构历史是两件事，不能用一个约束同时表达**。
>
> **索引必需，但列组合待 `EXPLAIN ANALYZE` 定案**（Codex 审查 P2-7）：纯投影方案下 MySQL 不做图遍历（不需要双向索引、不需要递归 CTE），但该索引会被 **480 次/天** 的全量重建扫描，缺失时退化为扫 40 万行物理表而非 2 万行当前边（**差 20 倍**）。
>
> 注意上面这个索引**并不覆盖建图查询的全部所需列** —— 建图还要 `job_id`、`definition_version`、edge 主键（用于 §L7 的 `edgeId`）。**不要把它称作覆盖索引**，最终列组合由 §3.4.8 的基准阶段用 `EXPLAIN ANALYZE` 确定。
>
> 列级血缘（Spark 能产出）暂不建表。L2 先落表级，列级数据先存进 `t_ddh_lineage_edge` 的扩展 JSON 列，等图页面证明有需求再拆表。

### 3.2 `pipeline.run` span 属性约定

对齐 [#3762](https://github.com/open-telemetry/semantic-conventions/issues/3762) 提案（未 stable，见 §6）：

|                     字段                      |                                 取值                                  |
|---------------------------------------------|---------------------------------------------------------------------|
| `trace_id`                                  | OpenLineage `run.runId`（UUID 去横线取 16 字节 hex）                        |
| `span_name`                                 | `pipeline.run`                                                      |
| `service_name`                              | `<engine>-<job-name-kebab>`，与 `t_ddh_data_job.otel_service_name` 一致 |
| `span_attributes["pipeline.job.namespace"]` | OpenLineage `job.namespace`                                         |
| `span_attributes["pipeline.job.name"]`      | OpenLineage `job.name`                                              |
| `span_attributes["pipeline.engine"]`        | `spark` / `flink` / `ds-sql`                                        |
| `span_attributes["pipeline.inputs"]`        | canonical_name 数组（JSON）                                             |
| `span_attributes["pipeline.outputs"]`       | 同上                                                                  |
| `span_attributes["pipeline.rows.written"]`  | `OutputStatisticsOutputDatasetFacet.rowCount`                       |
| `status_code`                               | COMPLETE→OK，FAIL/ABORT→ERROR                                        |

实现：datasophon-api 已挂 OTel Java Agent，取 `GlobalOpenTelemetry` 用 `SpanBuilder.setStartTimestamp/setEndTimestamp` 手工补录历史时间戳，**零新依赖**。

### 3.3 表节点身份规范（整个血缘图的地基）

```text
canonical_name = <connector>://<catalog|cluster>/<database>/<table>

paimon://prod/dwd/dwd_order
doris://ddh/ads/ads_gmv
mysql-cdc://10.0.0.5:3306/app_db/orders
```

不做规范化，ods→dwd 的边就跨不了作业（A 写的 `dwd_order` 与 B 读的 `catalog.dwd.dwd_order` 会成为两个节点）。

**关键约束**：三个 provider 产出的节点必须落在同一命名空间，否则图会断成两半。

> ⚠️ **2026-07-30 L0 核查纠正**：原文写「Spark 经 `GravitinoSparkPlugin` 上报的 dataset identifier
> **已被转换成** Gravitino 的 `metalake.catalog.schema.table` 格式」—— **不成立**。
> `gravitino-spark-connector-runtime` 整个 jar 零个 openlineage 条目，它是 catalog 插件，
> 不参与血缘事件生成；Gravitino 服务端也只有 `NoopProcessor`。
> **dataset 命名 100% 由 `openlineage-spark` 按其自身 naming 规范生成**（基于底层存储：
> Hive metastore URI / Paimon warehouse path / JDBC URL）。
> 因此规范化责任本就在我方 `CanonicalNameResolver`，好处是不必逆向 Gravitino 的拼写。
> 证据见 [`docs/monitoring/data-lineage-verification.md`](./monitoring/data-lineage-verification.md) §3。
>
> **L0 仍待核实（#2 剩余部分，整个 epic 的生死点）**：`openlineage-spark` 对实际使用的
> catalog 类型（Hive / Paimon / Iceberg / JDBC）产出的 `namespace` / `name` 确切拼写。
> **必须实机采样确认，不能推断** —— 各 catalog 的解析存在版本差异与回退分支。
> 前置条件：沙箱当前**没有 Spark**（L0 #3 已确认）。

`dw_layer` 由**可配置正则规则**推导（默认按 database 名 `ods`/`dwd`/`dws`/`ads`，其次按表名前缀），支持人工覆盖存 MySQL——**不要把 `ods_` 前缀硬编码进 Java**。

### 3.4 内存图与一致性设计（设计目标 5000 作业）

#### 3.4.1 规模基线与真实瓶颈

**设计目标规模：5000 个数据作业。** 换算成图：

|           量           |     估算      |         依据         |
|-----------------------|-------------|--------------------|
| 表节点                   | 7500～15000  | 作业数 × 1.5～3（去重后）   |
| **当前**边（is_current=1） | 15000～20000 | 每作业平均 3～4 条输入输出边   |
| `pipeline.run` span   | ~2.5万/天     | 5000 作业 × 日均 5 次运行 |

> **⚠️ 四种单位必须分开算，不能混用**（Codex 审查 P1-6：原文把"事件数"当成"边表行数"，一年估算错了 3～4 倍）：
>
> |              单位              |       量        |                          说明                          |
> |------------------------------|----------------|------------------------------------------------------|
> | **event**（OpenLineage 事件到达）  | ~5 万/天         | 2.5 万次运行 × 2（START + COMPLETE）                       |
> | **node update**（`last_seen`） | **15～20 万行/天** | 每事件涉及 3～4 个节点，**不是 5 万**                             |
> | **definition version**       | **待 L0 采样**    | 仅结构真变时 +1；变化率目前**无证据**，见 §3.4.4                      |
> | **edge row**（物理行数）           | 由上一行决定         | 若结构变化率 = 每次运行都变（最坏），则 15～20 万行/天、**一年 5500～7300 万行** |
>
> 边表物理行数**完全取决于 structural hash 的稳定性**。§3.4.4 的 diff 不是优化，是容量生死线；而它的实际效果**必须由 L0 实测**，不能推断。

**结论：SQL 不是瓶颈**（在结构变化率受控的前提下）。40 万行量级的 MySQL 表加对索引是毫秒级；`otel_traces` 是亿级设计，2.5 万 span/天是噪声。真正会炸的是另外四处，按实际暴露顺序：

| # |                 瓶颈                 |         炸点         |              与作业总数的关系               |
|---|------------------------------------|--------------------|-------------------------------------|
| 1 | 前端 G6 渲染 + antv-dagre 布局           | ~800 节点起卡，2000+ 秒级 | 直接相关，**最先炸**                        |
| 2 | 超级节点扇出（`dim_date` 被数百个作业读）         | 图里出现一个高度数节点即炸      | **与总数无关**，取决于度数分布尾部                 |
| 3 | 每跳都做 `MAX(definition_version)` 自连接 | 递归遍历时全表扫           | 已由 §3.1 `is_current` + §3.4.2 内存图消除 |
| 4 | 边流量标签 N+1 查 Doris                  | 200 条边 = 200 次查询   | 由 §L7 拆二次请求解决                       |

#### 3.4.2 存储与查询分离：MySQL 持久化 + Guava 内存图

**MySQL 是唯一权威，Guava 图是纯派生的读侧视图。** MySQL 侧退化为纯边列表存储，不承担任何图遍历能力。

选型 `com.google.common.graph.ValueGraph`。**guava 已是 `datasophon-common` 的直接依赖**（`datasophon-common/pom.xml:34-37`，版本 31.1-jre 由根 pom 统一管理），`datasophon-api` 传递可用，**零新增依赖**。

内存占用**粗估**（5000 作业规模：15000 节点 + 2 万边）：ValueGraph 约 5 MB + `nodeMeta` 侧表 ~2 MB ≈ **7～9 MB**，重建期新旧图并存峰值 ≈ 18 MB。

> **⚠️ 此数字未计入** `JobRef`/`List` 对象、`ImmutableMap` entry 开销、`NodeMeta` 中的 `canonical_name` 真实字符串长度、构建期临时对象（Codex 审查 P2-7）。**标记为「待基准验证」，不得作为容量承诺**，验证方式见 §3.4.8。即便低估 2～3 倍（20～30 MB）结论也不变 —— 选 Guava 而非手写 `int[][]` 的理由是 `predecessors()` 与可读性，不是省内存。

**三个必须知道的适配点**：

|                 适配点                 |                             说明                              |                                               处理                                               |
|-------------------------------------|-------------------------------------------------------------|------------------------------------------------------------------------------------------------|
| **并行边**（真实建模冲突）                     | Guava `ValueGraph` 中 (u,v) 只能有一条边，但同一对表可能由多个作业产生            | 用 `ValueGraph<Long, EdgeValue>`，`EdgeValue` 持 `List<JobRef>` —— **但这是 API 语义决策，不是纯技术选型，见下方专段** |
| **`Traverser` 用不上**                 | `Traverser.breadthFirst()` 返回不带深度信息的无限展开迭代器                 | 必须**手写分层 BFS**，只调 `successors()`/`predecessors()`；深度截断与度数折叠都要在循环内控制                            |
| **禁用 `Graphs.transitiveClosure()`** | O(V·E)，且在 300 入度维表上结果集爆炸。名字看起来正好是"查所有上游"，**是本方案最易被误用的 API** | 写进代码注释明令禁止                                                                                     |

##### 并行边：先定 API 语义，再定图结构（Codex 审查 P1-3）

原文直接下结论"不要用 `Network`"是**越过了前置问题**。同一对表 `(src, dst)` 可能由多个作业产生（两个 Spark 作业写同一张表；或一条 Spark BATCH 链路 + 一条 Flink STREAM 链路并存），聚合成一条 `ValueGraph` 边会产生三处矛盾：

|          矛盾点          |                                       表现                                       |
|-----------------------|--------------------------------------------------------------------------------|
| L1 验收                 | DB 有 2 条 current edge，`ValueGraph.edgeCount()` 只有 1 —— **"快照边数 == DB 行数"必然失败** |
| §D4 流量分型 / §L7        | 一条图边只能带一个 `flowType`，无法表达同一对表上批、流两种语义                                          |
| §L7 `{edgeIds:[...]}` | 未定义一条聚合边如何映射多个物理 edge ID                                                       |

**裁决：采用逻辑边（logical edge）模型 —— 图上一条边，载荷是作业列表。**

```jsonc
// GET /v2/lineage/graph 的 edge 元素
{
  "src": 101, "dst": 205,
  "jobs": [                              // 至少 1 个；>1 即并行边
    {"jobId": 7,  "edgeId": 3301, "flowType": "BATCH",  "flowLabel": "320万行 · 12分钟前"},
    {"jobId": 12, "edgeId": 4102, "flowType": "STREAM", "flowLabel": "1.2k rec/s"}
  ]
}
```

前端默认渲染"2 个作业"的聚合边，点击展开逐作业明细；`flowType` 混合时边样式取"存在 STREAM 则用流式样式"。

**相应的口径修正**：

- **L1 验收改为**：`Σ(所有 EdgeValue.jobRefs.size())` == DB current edge 行数；逻辑边数 == `SELECT COUNT(DISTINCT src_node_id, dst_node_id) WHERE is_current = 1`
- **`Network` 未被否决，只是当前不需要**：若产品后续要求"每个作业一条独立的边"（而非聚合），`Network` 反而更自然。此处保留判断依据而非结论

裁决依据是**产品语义**：血缘图回答的是"这张表的数据从哪来"，表对之间画一条边符合直觉；"哪个作业搬的"是二级信息，放在边的载荷里。

节点类型用 `Long`（`t_ddh_lineage_node.id`）而非 `String`（`canonical_name`）—— 后者是 `paimon://prod/dwd/dwd_order` 这类长串，做 HashMap key 每次全串比较。`canonical_name` 放侧表 `Map<Long, NodeMeta>`，BFS 结束后回填。

**Guava 相比手写邻接表的实际收益**：

- `predecessors()` 免费 —— 血缘核心操作是**双向**遍历（上游追溯 / 下游影响分析）。手写要维护两张 Map 并保证一致，或像 `api/dag/RepoDAG.java:267-268` 那样每次调用重建 `predecessorMap`
- `ImmutableValueGraph` 不可变 —— `volatile` 整体替换即并发安全，读侧零锁、零 defensive copy
- `Graphs.hasCycle()` —— 血缘图理论无环但实际一定有：`INSERT OVERWRITE t SELECT ... FROM t` 自环、A→B→A 双向同步作业。**构建期检测**，好过运行期 BFS 死循环。⚠️ **但不能直接拿它当告警条件**，见下
- `successors().size()` 直接给度数，度数折叠判断 O(1)，无需预计算

#### 3.4.3 纯投影模型：内存图不接受任何增量修改（核心决策）

**内存图是 MySQL 的纯函数投影，每 3 分钟整体重建；不存在"增量更新内存图"这条路径。**

##### 纯投影的完整定义（两条，缺一不可）

Codex 审查 P0-1 指出：原文只写了第一条，实际设计违反了第二条 —— **写侧曾依赖快照做版本判定**，这让快照参与了写入正确性，本质是"陈旧缓存参与权威写入"，比双写更隐蔽。

| # |              规则              |                 违反后果                 |
|---|------------------------------|--------------------------------------|
| ① | **写侧不修改内存图**                 | 内存里出现 DB 中不存在的边（原始双写问题）              |
| ② | **写侧不读取内存图** —— 快照只服务 GET 查询 | 陈旧快照导致重复写版本、并发写冲突、旧事件回滚新结构（见 §3.4.4） |

> **判定标准**：搜索代码里所有 `snapshotHolder.get*()` 的调用点，**必须全部位于 `@GetMapping` 的调用链内**。任何一个出现在 ingest / 写事务路径上，纯投影模型即告破产。这条要写进 code review checklist。

##### 为什么不做增量

增量方案（写库的同时改内存图）有一个无法根治的缺陷 —— **双写不一致是静默的**：

```java
@Transactional
public void ingest(LineageEvent e) {
    edgeMapper.insert(...);      // ← 可能回滚
    graphHolder.addEdge(...);    // ← 不会回滚 → 内存里存在一条 DB 中不存在的边
}
```

内存图不参与事务回滚。这类不一致**没有日志、没有异常、没有任何信号**，一直错到下次重启。即使用 `@TransactionalEventListener(AFTER_COMMIT)` 修正了事务边界，仍要靠对账兜底，且对账窗口内的错误无人知晓。

纯投影模型消灭的不是"不一致的后果"，而是**"不一致的可能性"**：内存图不再是被增量维护的状态，而是数据库的派生视图，最多偏离现实一个重建周期。

##### 成本核算（5000 作业规模）

> **以下全部为粗估，标记「待基准验证」**（Codex 审查 P2-7）。验证方法与验收口径见 §3.4.8，**基准跑完之前这些数字不得写进容量承诺或 SLA**。

|                步骤                |     粗估      |
|----------------------------------|-------------|
| MySQL 扫 `is_current = 1`（约 2 万行） | 100～300ms   |
| 建 `MutableValueGraph`            | 40～100ms    |
| `ImmutableValueGraph.copyOf()`   | 20～60ms     |
| **小计**                           | **约 500ms** |

**已知未计入项**（可能使实测显著高于 500ms）：`nodeMeta` 的独立查询、JDBC/MyBatis 结果集映射、分页往返、`EdgeValue`/`List<JobRef>`/字符串对象创建、`Graphs.hasCycle()`、冷 buffer pool、**远程 MySQL 网络往返**（本平台 MySQL 通常不与 Master 同机）。

3 分钟一次 → 每天 480 次。**注意 `480 × 500ms = 240s` 是墙钟时间，不是 CPU 时间** —— 其中大部分是等待 MySQL 的 I/O 阻塞，真实 CPU 占用低于此值；原文写"4 分钟 CPU"是错的。

|    维度    |             粗估             |                 判断                  |
|----------|----------------------------|-------------------------------------|
| 常驻内存     | 7～9 MB（未计入项见 §3.4.2）       | 即使低估 3 倍仍无感                         |
| 重建峰值     | 18 MB（新旧图并存）               | 无感                                  |
| **GC**   | 每 3 分钟产生一个 ~9MB 的对象图       | **见下方修正**                           |
| MySQL 扫描 | 960 万行/天 × **实例数**，一致性读不锁表 | 单实例无感；多实例需乘倍数，见 §3.4.5 的单 Master 约束 |

> **GC 判断修正**（Codex 审查 P2-7：原文断言"旧图全部死在 young gen 不晋升"，**这是错的**）：
>
> 已发布的快照要**存活整整 3 分钟**才被下一次重建替换。3 分钟远长于典型 G1 young GC 周期（数百毫秒到数秒），因此**当前快照几乎必然晋升 old gen**，被替换后成为 old gen 垃圾，由 mixed GC 回收。§3.4.6 的 300 节点查询上限限制的是"读操作钉住旧图"的时长，**不能改变图本身存活 3 分钟这一事实**。
>
> 影响评估：每 3 分钟产生 ~9MB old gen 垃圾（480 次/天 ≈ 4.3 GB/天晋升量）。对配置了数 GB 堆的 Master 进程，这会略微提高 mixed GC 频率，**大概率仍无感，但必须实测**而非断言。§3.4.8 的基准要求用 JFR 记录 promotion 与 old-gen 回收行为。

##### 方案对照

|      维度      |                       增量 + 对账（已否决）                        |         纯投影（采用）          |
|--------------|-----------------------------------------------------------|--------------------------|
| 代码量          | AFTER_COMMIT 事件 + debounce + `applyPendingChanges` + 每日对账 | 一个 `@Scheduled`          |
| 双写不一致        | **存在**，靠对账兜底                                              | **不存在**（前提：同时遵守规则 ① 与 ②） |
| 事务边界陷阱       | 写错则静默脏数据                                                  | **不存在**                  |
| L1 验收「回滚无脏边」 | 必须测                                                       | **不需要测**                 |
| 延迟           | ~200ms                                                    | 最坏 3 分钟，手动刷新可即时          |
| CPU          | 几十次/天 × 30ms                                              | 480 次/天 × 500ms          |

##### 事件触发（采纳，作为延迟优化）

**采纳 AFTER_COMMIT 事件触发重建**（Codex 审查 P1-4：手动刷新只能读已落库数据，对"结构已变但事件尚未送达"完全无效，不能作为唯一的低延迟手段）：

```java
/**
 * 结构变更落库后提前触发一次全量重建 —— 仅为降低延迟，不承担正确性。
 * 此事件丢失 / 重复 / 乱序均无害：最坏结果是等到下一个 3 分钟窗口。
 * 注意：这里是"触发重建"，不是"修改内存图"——后者违反 §3.4.3 规则 ①。
 */
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
void onLineageChanged(LineageStructureChangedEvent e) {
    rebuildCoordinator.requestRebuild(Trigger.EVENT);   // 进入 §3.4.5 的 single-flight
}
```

> **纪律**：这个 `@TransactionalEventListener` 是**延迟优化，不是正确性依赖** —— 与本仓库现有的普通 `@EventListener`（`WorkerCommandClient.java:262` 监听 `WorkerOfflineEvent`）语义不同，需在注释写明。
>
> 判断一个组件是否值得保留，看的不是它做了什么，而是**它出故障时的爆炸半径**：若事件用于增量修改图，丢一次 = 图上永久多/少一条边；用于触发重建，丢一次 = 慢 3 分钟。

三个触发源（定时 / 事件 / 手动）**全部经由 §3.4.5 的 rebuild coordinator**，不得各自直接调用 `rebuild()`。

#### 3.4.4 写路径：structural hash 幂等写入（**不读快照**）

此条约束的是**写库**。OpenLineage 是**每次运行**都发 START/COMPLETE 事件的，不是只在血缘改变时发 —— 若不做去重，边表会随事件数而非结构变化数膨胀（§3.4.1 的单位表）。

##### ⚠️ 已否决的做法：与内存快照 diff（Codex 审查 P0-1）

```text
✗ 事件 → 解析 (inputs, outputs) → 与内存快照当前边集 diff → 决定是否写库
```

这看起来只是"用缓存加速判断"，实际让**陈旧缓存参与了权威写入**，违反 §3.4.3 规则 ②。具体失败序列：

1. 快照仍是作业 v1（上次重建时的状态）
2. v2 的第一个事件与 v1 比较 → 判定"结构变了" → 写入 v2
3. **重建尚未发生**，快照仍是 v1
4. 同一 v2 的重试 / COMPLETE 事件 / 另一实例转发的相同事件到达 → **仍与 v1 比较** → 再次判定"结构变了" → 写入 v3、v4……

触发条件全是常态：Gravitino HTTP sink 重试、START 与 COMPLETE 先后到达、同一作业连续运行、多 Master 并发接收。并发时还会同时计算 `MAX(version)+1`、同时翻转 `is_current`，造成唯一键冲突或**多个 current 版本并存**；晚到的旧事件甚至能把新结构回滚成旧结构。此外，启动加载失败时快照为 null，原设计根本没定义此时如何 diff。

##### ✓ 采用的做法：三种语义分开处理（Codex 二轮 P0-1）

**关键认知：`FOR UPDATE` 只保证同一作业的事务串行，不能判断"结构不同"是新结构还是晚到的旧运行。** 仅靠 hash 比较无法消除"旧事件回滚新结构"。三件事必须用三种机制：

|     语义     |             机制              |                      失败后果                      |
|------------|-----------------------------|------------------------------------------------|
| ① **投递幂等** | `t_ddh_lineage_event` 唯一键去重 | 重试 / 重复投递被当成新事件反复处理                            |
| ② **顺序判定** | `current_watermark` 单调水位    | **晚到的旧 run 把已失效的旧结构重新设为 current**              |
| ③ **结构历史** | `(job_id, version)` 唯一键     | A→B→A 合法回退撞唯一键（原 `(job_id, content_hash)` 的错误） |

```text
事件 → 解析 (inputs, outputs) → 规范化 → structural_hash + watermark

① INSERT IGNORE INTO t_ddh_lineage_event (producer, run_id, event_type, ...)
   └─ 影响行数 = 0 → 重复投递，直接返回，不进入后续任何步骤

② 事务内按固定顺序加锁（见下方"加锁顺序"）：
   SELECT current_structural_hash, current_watermark FROM t_ddh_data_job WHERE id = ? FOR UPDATE
     ├─ watermark <= current_watermark  → 【晚到的旧 run】只记 parse_log，**绝不改 current**
     ├─ hash 相同                        → 只更新 last_seen + 推进 watermark，不写版本
     └─ hash 不同 且 watermark 更新        → is_current 翻转 + 写 node/edge/definition(version+1)
                                           + 更新 current_structural_hash / current_watermark
                                           + t_ddh_lineage_generation +1
```

**watermark 取值**：优先用 OpenLineage 的 `run.facets.nominalTime` 或 `eventTime` 的 run 起始时刻；**若上游不提供可靠单调序号，L0 必须给出结论**（见 §L0 #8）—— 因为此时无法区分"晚到的 A"与"合法回退到 A"，只能退而求其次用 `received_at`（接受乱序恢复时的误判，并在 `parse_log` 留痕）。

**A→B→A 回退是合法操作**，不是异常：`version` 递增到 3，`content_hash` 与 version 1 相同 —— 这正是为什么唯一键必须是 `(job_id, version)` 而不是 `(job_id, content_hash)`。

**structural hash 的计算规则（必须严格，否则误判为结构变化）**：

|                  规则                   |                          原因                           |
|---------------------------------------|-------------------------------------------------------|
| 对 inputs / outputs **排序 + 去重**后再 hash | OpenLineage 数组顺序不保证稳定，顺序变化不是结构变化                      |
| 只取 canonical_name                     | 排除统计 facet（rowCount / 字节数）、时间戳、runId —— 这些**每次运行都不同** |
| 排除 START/COMPLETE 差异                  | 两类事件携带的 facet 不同，但描述的是同一次运行的同一结构                      |
| 动态表名 / 临时表 / 分区需归一                    | 否则每天的分区都会被算作新结构，边表按天膨胀                                |

##### 锁与并发（Codex 二轮 P1-6 纠正了原文的错误定位）

> **原文的错误**：写"共享维表 `dim_date` 的 `FOR UPDATE` 会成为热点"是**错的** —— `FOR UPDATE` 锁的是 `t_ddh_data_job` 的 **job 行**，两个作业引用同一维表并不会竞争同一把锁。

真正的三个并发风险：

|          风险          |                         说明                         |                   处理                   |
|----------------------|----------------------------------------------------|----------------------------------------|
| **`last_seen` 更新死锁** | 两个事务以不同顺序更新多个共享节点 → 循环等待。MySQL 官方要求多行更新采用一致顺序并准备重试 | **固定加锁顺序** + 死锁异常按整个事务有限重试             |
| **generation 单行串行化** | 所有结构变更都要 +1 这一行 → 全局串行点                            | **只在结构真正变化时递增**（几十次/天时无影响，但该前提待 L0 验证） |
| 峰值远高于均值              | 5 万事件/天均值 0.58 次/秒，但批作业整点集中启动                      | **L0 实测峰值 QPS**；必要时 `last_seen` 改异步合并写 |

**固定加锁顺序（写进代码注释）**：

```text
t_ddh_data_job(按 job_id)
  → t_ddh_lineage_node(按 canonical_name 或 node_id 排序)
  → t_ddh_lineage_edge / t_ddh_data_job_definition
  → t_ddh_lineage_generation
```

**`last_seen` 若改异步合并写**，必须用 `GREATEST(last_seen, ?)` 而非直接赋值 —— 否则乱序到达会让时间**回退**；同时要定义最大合并延迟。

##### 必须埋的指标

原文声称"结构变化仅几十次/天"**目前没有任何证据**。四个计数器缺一不可，否则容量假设无法证伪：

`event_total` · `structure_change_total` · `edge_rows_written_total` · `last_seen_rows_updated_total`

#### 3.4.5 重建实现：single-flight coordinator + 单调发布

##### ⚠️ 三个触发源必须串行化（Codex 审查 P0-2）

定时、事件、手动三个入口共用 `rebuild()`，而 `@Scheduled` 用的 `taskScheduler` **池大小为 5**（`MasterAsyncConfig.java:100`），**不天然串行**。原设计的 5 秒节流只防连点，**既不是互斥也不保证发布顺序**。

失败序列：

1. 重建 A 读到旧数据，执行较慢（远程 MySQL 抖动）
2. 期间数据库提交了新的结构版本
3. 手动重建 B 读到新数据，**先完成、先发布**
4. A 后完成 → **把旧快照覆盖回去** → 图倒退，且下一次重建前无人察觉

```java
@Component
public class LineageRebuildCoordinator {

    private final AtomicBoolean inFlight = new AtomicBoolean();
    private final AtomicLong publishedGeneration = new AtomicLong(-1);
    private volatile LineageGraphSnapshot published;

    /** 独立单线程执行器 —— 重建绝不占用 Tomcat 线程或 @Scheduled 线程 */
    private final ExecutorService rebuildExecutor =
            Executors.newSingleThreadExecutor(r -> new Thread(r, "lineage-rebuild"));

    /**
     * 三个触发源的唯一入口。**只置脏 + 投递，立即返回**（Codex 二轮 P1-3）。
     * 原设计让调用线程直接执行 doRebuild()，与"手动接口返回 202"和
     * "AFTER_COMMIT 只是延迟优化"两条自相矛盾 —— POST 会同步等完整次重建，
     * ingest 请求会被 AFTER_COMMIT 拖长。
     */
    public void requestRebuild(Trigger trigger) {
        pending.set(true);
        if (inFlight.compareAndSet(false, true)) {
            rebuildExecutor.execute(this::drainPending);
        }
    }

    private void drainPending() {
        try {
            int rounds = 0;
            long deadline = clock.millis() + MAX_DRAIN_MILLIS;   // 墙钟预算，防饥饿
            while (pending.compareAndSet(true, false)) {
                try {
                    doRebuild();
                } catch (Exception e) {
                    lastRebuildError = e;                        // 不吞异常、不丢 pending
                    metrics.rebuildFailed(e);
                    break;
                }
                if (++rounds >= MAX_DRAIN_ROUNDS || clock.millis() > deadline) {
                    pending.set(true);                           // 让出线程，重新投递
                    break;
                }
            }
        } finally {
            inFlight.set(false);
            if (pending.get() && inFlight.compareAndSet(false, true)) {
                rebuildExecutor.execute(this::drainPending);     // 无丢唤醒交接
            }
        }
    }

    private void doRebuild(Trigger trigger) {
        // ① 整次读取（节点 + 边 + 全部分页）必须在同一个只读事务、同一连接内
        //    否则分页可能跨越一次 is_current 翻转 → 快照含某作业的新旧两版边，或一条都没有
        Snapshot next = txTemplate.execute(readOnlyRepeatableRead, tx -> {
            long generation = readDbGeneration();       // 见下方 generation 定义
            return buildFromDb(generation);
        });
        // ② 单调发布：只有代际不低于已发布的才允许覆盖，杜绝慢重建覆盖新快照
        publishIfNewer(next);
    }

    private synchronized void publishIfNewer(Snapshot next) {
        if (next.generation() < publishedGeneration.get()) {
            metrics.staleRebuildDiscarded(next.generation(), publishedGeneration.get());
            return;                                     // 丢弃，不覆盖
        }
        published = next;
        publishedGeneration.set(next.generation());
    }
}
```

**generation 的取法**（三选一，L1 实施时定）：`MAX(id)` of edge 表、`MAX(updated_at)`、或独立的 `t_ddh_lineage_generation` 单行计数器（结构写事务内 +1）。前两者实现简单但在时钟/自增回绕上有边界情况，计数器最稳妥 —— 但**计数器行会把所有结构写事务全局串行化**（Codex 二轮 P1-6）。因此**只在结构真正变化时递增**，`last_seen` 更新不碰它；L1 基准需测 generation 行的锁等待。

> **无丢唤醒**：`drainPending` 的 `finally` 在释放 `inFlight` 后**再次检查 pending 并重新投递**，因此不存在"退出窗口吞掉一次触发"的问题（这是上一版的缺陷）。极端情况下最多多投递一次空转，代价是一次 CAS。

##### 读一致性要求

|        要求         |                      原因                       |
|-------------------|-----------------------------------------------|
| 同一只读事务、同一连接       | 分页 autocommit 会跨越 `is_current` 翻转，产生"半个作业"的快照 |
| `REPEATABLE READ` | MySQL 默认隔离级别即是；显式声明避免被全局配置改掉                  |
| 节点与边查询同属该事务       | 否则边引用的 node_id 可能在 nodeMeta 中不存在              |

##### 建图三个必须项

- `ValueGraphBuilder.directed().allowsSelfLoops(true)` —— `INSERT OVERWRITE t SELECT ... FROM t` 是真实存在的自环，默认 builder 直接抛异常
- 构建后做环检测，有环记 `parse_log` 但**不阻断发布**（有环时图仍要能展示）。⚠️ **必须拆成两个指标，不能只发布一个 `hasCycle` 布尔值** —— 见下
- `ImmutableValueGraph.copyOf()` 后发布，读侧零锁

##### 环检测必须拆成两个指标（三轮自审 F2）

**实测**（GraalVM 21.0.7 + guava 31.1-jre，即本项目版本）：

```
directed + allowsSelfLoops，仅一条 1→1     ->  Graphs.hasCycle = true
directed，1→2→3 纯 DAG                    ->  Graphs.hasCycle = false
```

Guava 把自环判定为环。而本节上面刚刚强制要求 `allowsSelfLoops(true)`，理由正是"`INSERT OVERWRITE t SELECT ... FROM t` **实际一定有**"。两句合起来即：**任何真实集群里 `hasCycle` 恒为 `true`**。

一个恒真的告警等于没有告警 —— 运维两周内学会无视它，而它本该警示的**跨作业环**（A→B→A 双向同步，会让上下游 BFS 结果反直觉、让"影响面"包含自己）就此淹没。根因是**一个布尔值合并了两种严重性天差地别的情形**。

|          指标          |                含义                |             用途             |
|----------------------|----------------------------------|----------------------------|
| `selfLoopCount`      | `edges()` 中 `nodeU == nodeV` 的条数 | **信息量**，不告警。展示为"N 个作业自读自写" |
| `hasNonTrivialCycle` | **剥掉自环后**再跑 `Graphs.hasCycle()`  | **告警条件**。正常集群应恒为 `false`   |

```java
// 剥自环后再判环；成本是一次 O(E) 过滤，相对建图可忽略
MutableGraph<Long> stripped = GraphBuilder.directed().allowsSelfLoops(false).build();
graph.nodes().forEach(stripped::addNode);
graph.edges().stream()
     .filter(e -> !e.nodeU().equals(e.nodeV()))
     .forEach(e -> stripped.putEdge(e.nodeU(), e.nodeV()));
boolean hasNonTrivialCycle = Graphs.hasCycle(stripped);
```

> BFS 侧不受影响 —— 无论哪种环，遍历都靠 `visited` 集合终止，两个指标纯粹用于**可观测性分级**。

**不要把快照塞进 `CacheUtils`** —— 它是 Hutool `CacheUtil.newLRUCache(4096)` 的**全局共享单例**（`datasophon-common/.../cache/CacheUtils.java:34`），与 `UseRoleGroup_*`/`zkserver_*` 等业务键挤同一个 LRU，快照会被随机驱逐。

##### 陈旧性契约：API 必须暴露快照新鲜度（Codex 审查 P1-4）

**"最大偏离 3 分钟"是错的**，三条都会让偏离无界：

- `fixedDelay` 语义是「上次**完成后**再等 3 分钟」，健康上界已经是 `3 分钟 + 重建耗时`
- 重建连续失败时保留旧快照 —— **偏离可以无限增长**
- 手动刷新只能读已落库数据；结构已改但 OpenLineage 事件尚未送达时，**点刷新完全无效**

陈旧血缘不只是"慢"，会造成**用户可见的错误决策**：

|          场景          |                      后果                      |
|----------------------|----------------------------------------------|
| 删表/改作业前做影响分析         | **漏掉新下游**（照着删 → 生产事故）或展示已不存在的旧下游             |
| 新节点已落库但不在快照          | `GET /v2/lineage/table/{id}` 返回 404，用户以为血缘丢了 |
| 同页面表详情读 MySQL、图读快照   | 两处数据自相矛盾                                     |
| §L7 拿旧 edgeId 查新流量映射 | 空标签或张冠李戴的标签                                  |

##### ⚠️ `stale` 不能只按年龄算（Codex 二轮 P0-2）

原文定义 `stale = ageSeconds > 600` 有个致命盲区：快照刚建 10 秒、DB generation 已从 4471 涨到 4472、事件触发的重建**失败了** —— 系统**明知自己落后**，接口却继续返回 `stale=false` 长达 590 秒，fail closed 根本不会启动。

**两层新鲜度，分别回答两个不同的问题**：

```jsonc
{
  "data": { /* 图数据 */ },
  "snapshot": {
    "generation": 4471,            // 快照代际
    "targetGeneration": 4472,      // AFTER_COMMIT 立即推进，不等重建
    "builtAt": "2026-07-29T21:58:03+08:00",
    "ageSeconds": 47,
    "stale": true,                 // 见下方公式 —— 此例因落后 DB 而 true，与年龄无关
    "lastRebuildError": "..."      // 参与 stale 判定，不再只是展示字段
  },
  "sourceFreshness": {             // 独立维度：上游数据是否完整
    "lastEventReceivedAt": "2026-07-29T21:57:10+08:00",
    "status": "OK"                 // OK | LAGGING | UNKNOWN
  }
}
```

```text
snapshotStale = publishedGeneration < observedDbGeneration     // 已知落后
             || rebuildFailedAfterTargetGeneration            // 追赶失败
             || ageSeconds > threshold                        // 兜底
```

**两层必须分开**（这是 Codex 的关键指正）：`builtAt` 只能证明"何时读过 DB"，**不能证明上游 OpenLineage 没有积压**。把"快照新鲜"冒充"源数据完整"，会让用户在事件积压时误以为血缘是全的。上游状态拿不到就老实报 `UNKNOWN`。

> ##### ⚠️ `stale` 是**查询侧现算的派生量**，不是快照上的存储字段（三轮自审 F3）
>
> T2 已交付的 `LineageSnapshotMeta` record 带有 `stale` / `degraded` / `lastRebuildError` 三个字段，但唯一构造入口 `fresh()` 恒传 `false / false / null`，Coordinator 也从不构造别的取值 —— **它们恒空**。
>
> 更危险的是重建失败路径：`drainPending` 捕获异常后只更新 Coordinator 自己的 `lastRebuildError`，**已发布快照的 `meta.stale()` 仍是 `false`**。查询侧若读它，会拿到一个"自称新鲜"的旧图，直接违反上面这段契约。
>
> **规定**：
> - 这三个字段从 `LineageSnapshotMeta` 中**删除**（T2 返工项）。留着就是等人踩 —— 它们是恒假的诱饵。
> - `stale` 由查询侧按上面的公式**每次现算**，输入为快照的 `generation` / `builtAt` + `coordinator.lastRebuildError()` + 当次观测到的 `observedDbGeneration`。
> - `targetGeneration` **保留**：它是"这次重建想追到哪一代"的事实记录，非派生量。
>
> 一般规律：**只要一个字段的值在所有现有写入路径下都是同一个常量，它就不是字段，是注释。** 而注释不会被误当成判据。

##### 严格接口：fail closed，正确性优先

**决策已定：重建持续失败时，严格接口保持不可用并告警，不做可用性兜底。** 不引入"绕过快照直查 DB"的第二套查询路径 —— 那会让 §3.4.3 的纯投影模型出现例外分支，而例外分支正是这类设计最终腐化的起点。

**边界必须是显式契约，不能靠使用场景猜**：

|     接口类别     |            端点            |      `stale=true` 时      |
|--------------|--------------------------|--------------------------|
| **严格**（用于决策） | `GET /v2/lineage/impact` | **503 + 明确错误信息 + 告警**    |
| 展示（用于浏览）     | `GET /v2/lineage/graph`  | 正常返回，UI 标注数据时间与 stale 徽标 |

独立 `/impact` 端点承载"下游影响分析""删除前检查"这类决策操作。**不要**用同一个 `/graph` 加参数区分 —— 端点分开才能在网关、审计、告警层面各自处理，也才能让"哪些操作是决策级"变成代码里看得见的事实。

##### 手动刷新

```java
/** 页面按钮："重新加载快照"（不是"刷新"）。异步触发，立即返回当前代际。 */
@PostMapping("/v2/lineage/rebuild")
public Result<RebuildAccepted> rebuildNow() {
    coordinator.requestRebuild(Trigger.MANUAL);
    return Result.accepted(new RebuildAccepted(coordinator.currentGeneration()));  // 202
}
```

三点约束：

- **返回 202 + generation**，不在 Controller 线程同步执行重建（否则慢重建会占满 Tomcat 线程）
- 按钮文案是**「重新加载快照」并展示数据时间**，不能伪装成普通页面刷新 —— 用户需要知道它只能拉取"已落库"的数据
- 5 秒节流仅防连点，**不替代 single-flight**

##### 部署约束：当前仅支持单 Master（Codex 审查 P1-5）

`volatile published`、节流、`@Scheduled` 全是**实例本地状态**。多 Master 下会出现：

- 连续两次查询落到不同实例 → 看到不同图版本
- 手动刷新只刷新命中的那个实例
- 每实例各自每 3 分钟扫 MySQL → §3.4.3 的 480 次/天要**乘实例数**

**当前版本明确约束为单 Master 实例**。这与仓库现状一致，Codex 二轮已核实：K8s 场景中 API 仍部署在唯一 `mw1`、不进 Deployment（`deploy/deployment-k8s.md:45`）；standalone 同样只有一个 `mw1` API（`deploy/deployment-standalone.md:44`）。

##### ⚠️ 约束需要**强制**，打印日志不算（Codex 二轮 P1-5）

启动日志既拦不住另一台主机启第二个 API，也拦不住有人把副本数调成 2；固定端口只能防同机重复进程。**必须用 MySQL 单例租约**：

```text
启动 → 独立连接获取 advisory lock（GET_LOCK）
       或写租约表 { owner, heartbeat, expiresAt, fencing_token }
  ├─ 成功 → 正常提供血缘功能，后台心跳续租
  └─ 失败 → readiness DOWN + 拒绝血缘相关端点 + 告警（不是打 WARN 继续跑）
```

要点：租约用**独立连接**持有（连接归还池即释放锁，不能复用业务连接）；部署文档需写明升级交接流程与租约超时时长，避免蓝绿发布期间新实例因旧实例未过期而长时间不可用。

> 将来若要多 Master：以 MySQL generation 为唯一协调点 —— 各实例轮询 generation 并追赶，GET 接口暴露本地 generation，刷新请求广播到全实例。**但写侧无论如何都不得依赖本地快照**（§3.4.3 规则 ②），这条在多实例下从"纪律"升级为"硬约束"。

##### 启动加载

照抄 `LoadServiceMeta implements ApplicationRunner`（`LoadServiceMeta.java:55`）的模式复用同一个 `rebuild()`，但有四点差异：

|            问题            |                                                                              处理                                                                              |
|--------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **加载失败不能阻断 Master 启动**   | 血缘是旁路功能，`DAGExecutor` 才是主业。`run()` 整体 try-catch，失败标记 `degraded` 并告警 —— **与 `LoadServiceMeta` 的 `@Transactional(rollbackFor = Exception.class)` 语义相反，别照抄该注解** |
| **加载未完成时 API 返回 503**    | 返回空图会让用户以为"血缘数据丢了"，是比报错更糟的 UX。快照为 null → `503 + Retry-After`                                                                                                 |
| **大表分批读**                | 按 `id` 范围分页（每批 1 万）或 MyBatis 流式游标，避免单次 ResultSet 撑爆                                                                                                          |
| **不要用 `@PostConstruct`** | Bean 初始化阶段 DataSource 未必就绪，且会拖慢启动；`ApplicationRunner` 在容器完全就绪后执行                                                                                             |

##### 可观测性（必须做）

重建耗时**必须打点到日志/metrics**，否则半年后没人知道它已经从 500ms 涨到 3s。这是 §3.4.8 第一条重新评估触发点的唯一探测手段。

#### 3.4.6 查询侧：API 禁全图 + 动态度数折叠

**「全图视图」不提供，这是设计约束而非优化。** 5000 个节点 `fitView()` 缩到屏幕上是一片马赛克，信息量为零 —— 即使渲染得动也没有意义。

```text
GET /v2/lineage/graph
  rootNodeId  必填          ← 没有 root 不给图
  depth       默认 2，上限 5
  direction   upstream | downstream | both
  → 硬上限 300 节点，超出则截断并返回 truncated: true

GET /v2/lineage/overview   ← 全局概览另开端点
  → 按 dw_layer 聚合的 5 个块 + 层间边计数
     ODS ──(1243 条)──► DWD ──(867 条)──► DWS ──► ADS
```

概览页把"5000 作业"这个规模呈现成 **5 个节点**，点击某层才下钻到表列表（走 ProTable 分页，不是图）。用户永远从"一张具体的表"或"一个具体的作业"进入图视图。

**度数折叠**（解决 §3.4.1 瓶颈 1 与 2）：血缘图度数分布是幂律的，且比服务拓扑更极端 —— `dim_date`、`ods_user` 被数百个作业读是常态。**BFS 的实际复杂度不由总边数决定，由度数分布的尾部决定**，这正是"5000 作业 vs 500 作业"不是关键变量的原因。

BFS 展开时若某节点在当前方向度数超过阈值，不展开其邻居，产出虚拟聚合节点 `[+247 个下游]`，点击才二次请求。

**阈值必须动态，不能固定。** 5000 作业下一张常规 dwd 表就可能有 40～50 个下游，固定阈值 30 会把正常表也折叠掉，用户每次都要多点一次。

##### ⚠️ 预算必须按 frontier 分摊（Codex 审查 P2-8）

原文的 `min(50, 剩余预算/2)` **只看单个节点，没有除以当前层的节点数**：剩余 200、frontier 有 5 个节点时，每个都获准展开 50 个 → 理论需求 250 → 最终只能靠 300 硬上限**粗暴截断**，被截掉的是哪些分支取决于遍历顺序。

```text
每层开始前：
  1. 收集 frontier，按 (dw_layer 距离, 度数升序, node_id) 稳定排序   ← 保证确定性
  2. 商余分配（不设 max(1,...) 下限）：
        q = 剩余预算 / frontier 节点数
        r = 剩余预算 % frontier 节点数
        前 r 个节点得 q+1，其余得 q          ← 预算为 0 是合法结果
  3. 预算为 0 的分支直接返回 collapsed token，不展开
  4. 逐节点展开，实际用量少于预算时把余额还给后续节点
```

> **为什么不能用 `max(1, 剩余/frontier数)`**（Codex 二轮 P2-8）：剩余预算 2、frontier 有 5 个节点时，每个仍至少分到 1，总需求 5 > 2 —— 最终还是靠 300 硬上限粗暴截断，"按 frontier 分摊"名存实亡。**必须允许预算为 0**，让超额分支明确变成 collapsed 而不是被静默砍掉。
>
> 边界测试必须覆盖 `remaining = 0`、`remaining = 1`、`remaining = frontierCount - 1` 三组。

**确定性是硬要求**：Guava 邻接集合的迭代顺序不是稳定契约，不排序会导致同一查询两次返回不同的截断结果 —— 用户刷新一下图就变了，会被当成 bug。排序键里加 `node_id` 兜底保证全序。

**聚合节点的展开契约**：

```jsonc
{"type": "collapsed", "token": "n:1042:down:g4471", "hiddenCount": 247, "direction": "downstream"}
```

`token` 内含 **generation**，二次展开请求带回该 token；若此时快照已更新（generation 不匹配），返回 409 让前端重新加载整图，而不是在新旧两版图之间拼接出一个不存在的结构。

叠加**总节点预算 300** 做最后兜底。测试必须覆盖：环、菱形（多路径到同一节点）、多个超级节点同层、不同插入顺序下结果一致。

#### 3.4.7 完整链路

```text
【写侧】OpenLineage 事件（~5 万/天）
  → 解析 (inputs, outputs) → 规范化 → structural_hash
  → 事务内 SELECT current_structural_hash FOR UPDATE     ← 查 DB，不查快照
      ├─ hash 相同 → 只更 last_seen，结束
      └─ hash 不同 → is_current 翻转 + 写 node/edge/definition + 更新 hash
  → AFTER_COMMIT: coordinator.requestRebuild(EVENT)
  （写侧既不修改也不读取内存图 —— §3.4.3 规则 ① ②）

【重建】LineageRebuildCoordinator（single-flight，三源合一）
   触发源：@Scheduled 3 分钟 · AFTER_COMMIT 事件 · POST /v2/lineage/rebuild(202)
   执行  ：单个只读 REPEATABLE READ 事务内分页读 is_current=1
        → 建图(allowsSelfLoops) → hasCycle 告警 → copyOf
        → publishIfNewer(generation)  ← 代际单调，慢重建不覆盖新快照

【读侧】volatile 引用，零锁，300 节点上限 + frontier 分摊预算的分层 BFS
   响应携带 snapshot{generation, builtAt, ageSeconds, stale, lastRebuildError}
   stale=true 时：展示类接口可用但 UI 标注；影响分析类接口 fail closed
```

**健康态下内存图与 MySQL 的偏离 ≈ 3 分钟 + 重建耗时**；重建持续失败时偏离无界，由 `stale` 标志对外暴露 —— 这是**契约，不是保证**。

#### 3.4.8 失效边界与重新评估触发点

|       作业规模       |  当前边数  | 单次重建（**粗估**） |           结论           |
|------------------|--------|--------------|------------------------|
| **5000（本次设计目标）** | ~2 万   | ~500ms       | 余量充足                   |
| 2 万              | ~10 万  | 1～3s         | 仍可行，或放宽间隔到 10 分钟       |
| 20 万             | ~100 万 | 10s+         | 需退回增量 / 预计算传递闭包 / 图数据库 |

##### 基准验证（L1 实施第一件事，先于功能开发）

§3.4.2 / §3.4.3 的所有数字都是**粗估**，必须先测出真值再谈容量。基准条件不能省：

|   项   |                                                  要求                                                   |
|-------|-------------------------------------------------------------------------------------------------------|
| 环境    | JDK 21（GraalVM 21.0.7）、生产堆配置、**远程 MySQL**（非本机同进程）                                                     |
| 数据    | 真实形态的 2 万条边 + 1.5 万节点，`canonical_name` 用真实长度                                                          |
| 指标    | 重建耗时 **p50 / p95 / p99**，不是单次采样                                                                       |
| 分段    | 拆 DB read · 结果映射 · 建图 · copyOf · hasCycle · publish 六段                                                |
| 内存    | JOL 测 retained heap；**JFR 测 allocation 与 promotion**（验证 §3.4.3 的 GC 修正）                               |
| SQL   | 对真实 SELECT 跑 `EXPLAIN ANALYZE`，**据此反推索引列**（见下）                                                        |
| **锁** | `lock_wait` p95/p99 · deadlock count · **generation 行等待** · `history_list_length` · 主从复制延迟 · 最长只读事务时长 |

> **长 read view 需要运行期门禁**（Codex 二轮 P1-6）：单个 REPEATABLE READ 事务读完整图**不阻塞普通写**，但会阻止相关 undo 被 purge。重建耗时若从 500ms 涨到 10s+，`history_list_length` 会持续增长。**基准阶段就要给这个指标定告警阈值**，不能等线上发现。
>
> **索引待定**：§3.1 的 `idx_edge_current(is_current, src_node_id, dst_node_id)` **不是建图查询的覆盖索引** —— 建图还需要 `job_id`、`definition_version`、edge 主键等列（Codex 审查 P2-7）。最终索引列表由 `EXPLAIN ANALYZE` 决定，不要提前拍板。

##### 回头触发点

|              触发条件              |         含义          |        动作         |
|--------------------------------|---------------------|-------------------|
| 单次重建 p99 > **2s**              | 墙钟占比过高              | 间隔放宽到 10 分钟，或转增量  |
| 当前边数 > **10 万**                | 约 2.5 万作业           | 转增量               |
| `staleRebuildDiscarded` 计数 > 0 | 慢重建被代际保护丢弃，说明并发压力上升 | 检查触发源是否过于频繁       |
| old-gen 回收频率显著上升               | 快照晋升带来的 GC 压力超预期    | 增大堆或延长重建间隔        |
| 重建时段查询 P99 抖动                  | 读一致性事务与写入产生锁竞争      | 排查 —— 只读事务理论上不该阻塞 |

前两条依赖 §3.4.5 的重建耗时打点，**没有打点这些触发点全是摆设**。

第一条依赖 §3.4.5 的重建耗时打点，**没有打点这三条触发点全是摆设**。

---

## 四、阶段划分

| Phase |                      目标                       |   依赖    | 状态 |
|-------|-----------------------------------------------|---------|----|
| L0    | 现场核查 spike（Gravitino lineage 端点 + Spark 事件采样） | —       | 待办 |
| L1    | 血缘接收端 + MySQL 存储 + 作业台账                       | L0      | 待办 |
| L2    | **Spark provider 打通**（第一个真实数据源）               | L1      | 待办 |
| L3    | 血缘图页面（复用 TopologyTab）                         | L2      | 待办 |
| L4    | `pipeline.run` span 输出 + trace 瀑布图            | L1      | 待办 |
| L5    | DS SQL provider（拉 DS API + 静态解析）              | L1      | 待办 |
| L6    | Flink provider（静态 SQL/YAML 解析）                | L1      | 待办 |
| L7    | 边流速叠加（分型显示）                                   | L3 + L4 | 待办 |

```text
L0 → L1 → L2 → L3 ─┬→ L7
          ├→ L4 ───┘
          ├→ L5
          └→ L6
```

**实施顺序原则**：L2（Spark）必须排在 L5/L6 之前——它是唯一能立刻产出真实血缘数据的 provider，用来验证 L1 的表结构、L3 的图渲染、§3.3 的 identifier 规范。用 Flink 开头会在拿到第一条边之前烧掉整个 L6 的成本。

### L0 — 现场核查 spike

| # |                            待核实                             |                            方式                            |                           影响                           |
|---|------------------------------------------------------------|----------------------------------------------------------|--------------------------------------------------------|
| 1 | Gravitino `/api/lineage` 在当前 1.3.0 部署上是否可用（默认 `sinks=log`） | curl 一条最小 RunEvent，看 `gravitino_lineage.log`             | 决定 L1 接收端形态                                            |
| 2 | **Gravitino 转换后 dataset 的 namespace/name 确切拼写**            | Spark 提交最简作业，抓 log sink 输出                               | **决定 canonical_name 转换函数，最关键的一项**                      |
| 3 | 沙箱是否有 Spark 作业可用于产出真实血缘                                    | 查 SPARK3 服务与现有作业                                         | 无 Spark 则 L2 需先造样例作业                                   |
| 4 | Gravitino HTTP sink 的重试/超时行为                               | 读配置 + 故意让接收端 500                                         | 决定 L1 是否需要幂等去重                                         |
| 5 | DS 3.4.1 API 能否拉到 SQL 任务定义文本                               | 调 DS OpenAPI                                             | 决定 L5 可行性                                              |
| 6 | **事件量与结构变化率实测**（Codex 审查 P1-6）                             | 采集**一个完整运行周期**（≥24h），统计下表五项                              | **决定边表容量与 §3.4.4 的可行性，不能推断**                           |
| 7 | **structural hash 的稳定性**                                   | 对同一作业的连续多次运行算 hash，看是否恒定                                 | hash 不稳则边表按运行次数膨胀，方案失效                                 |
| 8 | **事件能否提供可靠的单调顺序**（Codex 二轮 P0-1）                           | 检查 RunEvent 是否带 `nominalTime` / run 起始时刻；重叠 run 的时间戳是否单调 | **决定 §3.4.4 的 watermark 取值**；拿不到则无法区分"晚到的 A"与"合法回退到 A" |
| 9 | 重复投递时 `(producer, runId, eventType)` 是否稳定                  | 故意让接收端 500，看 Gravitino 重试事件的三元组是否一致                      | 决定 `t_ddh_lineage_event` 幂等键是否可用                       |

**L0 #6 必须统计的五项**（原计划"采 3 条事件"远远不够）：

`events/job` · `edges/event` · 事件重复率（重试 + START/COMPLETE） · **structural hash 变化率** · **峰值 QPS**（批作业整点集中启动时的瞬时值，不是日均 0.58/s）

**L0 #7 重点观察**：输入输出数组顺序是否稳定、动态表名 / 临时表 / 日期分区是否被编码进 `canonical_name`、START 与 COMPLETE 携带的 dataset 是否一致。任何一项不稳定，都会让"结构变化仅几十次/天"的假设崩塌。

**产出**：`docs/monitoring/data-lineage-verification.md`（格式照抄 `docs/monitoring/zookeeper-otel-verification.md`），含真实 OpenLineage 事件样本 ≥3 条 + 一个完整周期的统计表。

**验收**：9 项全有明确结论；#2 有逐字记录的 namespace/name 样本；#6/#7 有实测数字而非估算；**#8 若结论为"上游无可靠单调序号"，§3.4.4 的 watermark 需降级为 `received_at` 并在文档中记录该妥协的后果**（乱序恢复时会误判，`parse_log` 留痕）。

### L1 — 接收端 + 存储

**实施第一件事是 §3.4.8 的基准验证**，先测出重建真实耗时与内存占用，再写功能代码 —— 否则整个 §3.4 的容量论证悬空。

- `db/migration/2.2.5/V2.2.5__DDL.sql`（§3.1 七张表 + `current_structural_hash` / `current_watermark` + `uk_job_version` + `t_ddh_lineage_event` + `t_ddh_lineage_generation`）
- `datasophon-api/.../lineage/` 独立包 —— **解析器/OpenLineage 类型绝不外泄**到 service/controller 层，对外只暴露自定义 POJO（将来换 provider 只换实现）
- `controller/v2/LineageV2Controller.java` —— `POST /v2/lineage`（OpenLineage 兼容）、`GET /v2/lineage/graph`（`rootNodeId` 必填，见 §3.4.6）、`GET /v2/lineage/overview`、`GET /v2/lineage/table/{id}`、`POST /v2/lineage/rebuild`（**202 + generation**，不同步执行）
- `LineageIngestService` —— 事件 → canonical_name 归一 → **规范化 structural hash** → 事务内与 **DB 中的** `current_structural_hash` 比较 → 落表（§3.4.4）。**写侧既不修改也不读取内存图**
- `LineageRebuildCoordinator` + `LineageGraphSnapshot` —— single-flight、单只读事务内分页读、代际单调发布、三触发源合一、重建耗时分段打点（§3.4.5）
- 作业台账 CRUD（`t_ddh_data_job`）+ `JobDiscoveryService`（只读对账：metrics 里活跃的 `service_name` 与台账 diff）

**验收**：

**写路径（§3.4.4）**

1. 灌入 L0 采集的真实事件样本 → 节点/边正确入库
2. **投递幂等** —— 同一 `(producer, runId, eventType)` 重复投递 100 次，`t_ddh_lineage_event` 只有 1 行，边表行数不变
3. **结构未变不写新版本** —— 同一作业不同 run 连灌 100 次相同结构，`definition` 版本数不变，只有 `last_seen` 被更新
4. **晚到的旧 run 不改 current**（二轮 P0-1）—— 先灌 v2（watermark 大），再灌 v1 的 COMPLETE（watermark 小），`current_structural_hash` 必须仍是 v2，且 `parse_log` 有记录
5. **重叠 run** —— 两个 run 交错到达（START₁ START₂ COMPLETE₂ COMPLETE₁），最终 current 由 watermark 最大者决定
6. **A→B→A 合法回退**（二轮 P0-1）—— 结构改到 B 再改回 A，必须成功产生 version 3 且**不撞唯一键**，`content_hash` 与 version 1 相同
7. **写路径不读快照** —— 快照**置空 / 置为过期版本**时灌入事件，行为完全不变；静态检查所有 `snapshotHolder.get*()` 调用点都在 `@GetMapping` 链路内
8. **并发写不产生多个 current** —— 20 线程并发灌同一作业同一新结构，结束后 `COUNT(*) WHERE job_id=? AND is_current=1` 等于该结构边数，且 **`COUNT(DISTINCT definition_version) = 1`** 并与 job 当前版本一致

   > **必须同时断言作业身份唯一**（三轮自审 F1）：`SELECT COUNT(*) FROM t_ddh_data_job WHERE cluster_id=? AND engine=? AND job_name=?` **必须等于 1**。
   >
   > 缺这一条时上面的 `WHERE job_id=?` 断言在"并发 INSERT 出两个 job_id"的场景下**依然全绿** —— 每个 job_id 各自只有一个 current。**这是本轮唯一一处「测试无法证伪自己前提」的实例**，见 §3.1。

8b. **并发首次事件只建一个作业**（三轮自审 F1）—— 对**从未出现过**的 `(cluster_id, engine, job_name)` 用 20 线程并发灌首个事件，`t_ddh_data_job` 只增 1 行；去掉 `uk_data_job_identity` 后此测试**必须失败**（用于证明该约束真正生效，而非碰巧没并发）
9. **死锁可恢复** —— 构造反序更新共享节点的并发事务，死锁后整事务重试成功，`deadlock count` 有埋点

**重建与发布（§3.4.5）**

10. **coordinator 并发度恒为 1**（二轮 P1-4 拆分）—— 多个触发源同时请求时，活跃重建数始终为 1，请求被合并，最终发布最新 generation

    > 原验收"慢 A 与快 B 并发、A 被丢弃"**在 single-flight 下不可能成立**，是自相矛盾的测试，已删除

11. **`publishIfNewer` 单元测试** —— 直接注入 generation 11 再注入 10，确认 10 被丢弃且 `staleRebuildDiscarded` +1
12. **异步 202** —— `POST /v2/lineage/rebuild` 在重建未完成时即返回 202，Tomcat 线程不被占用
13. **持续 pending 不饥饿** —— 重建期间持续触发，`drainPending` 达到轮数/墙钟预算后让出线程并重新投递，不无限循环
14. **重建失败可恢复** —— 注入一次重建异常，`lastRebuildError` 被记录、pending 不丢、下一轮正常恢复
15. **读一致性** —— 分页读取期间并发翻转 `is_current`，快照不得含同一作业的两个版本，也不得一条边都没有
16. **单 Master 租约**（二轮 P1-5）—— 启动第二个实例时**获取租约失败、readiness DOWN、血缘端点拒绝服务**（不是只打日志）

**查询侧**

17. **逻辑边口径** —— `Σ(EdgeValue.jobRefs.size())` == DB current edge 行数；逻辑边数 == `COUNT(DISTINCT src,dst)`；构造并行边样本验证
18. **两层新鲜度**（二轮 P0-2）—— 构造"快照年龄仅 10s 但 generation 落后 DB"的场景，`stale` 必须为 `true`；`/impact` 返回 503 且告警，`/graph` 正常返回并带 stale 标记
19. **BFS 确定性与预算** —— 同一查询连续 10 次结果完全相同；覆盖环、菱形、多超级节点同层；边界 `remaining = 0 / 1 / frontierCount-1`
20. **环检测分级**（三轮自审 F2 改写）—— 三组样本各自断言，**不得只测一个 `hasCycle` 布尔值**：

    |                 样本                 | `selfLoopCount` | `hasNonTrivialCycle` | 告警  |
    |------------------------------------|-----------------|----------------------|-----|
    | 纯 DAG                              | 0               | `false`              | 不告警 |
    | `INSERT OVERWRITE t SELECT FROM t` | 1               | **`false`**          | 不告警 |
    | A→B→A 跨作业双向同步                      | 0               | **`true`**           | 告警  |

    三组均**不阻断发布**、BFS 均不死循环。第二行是本条的关键 —— 若实现直接用 `Graphs.hasCycle()`，该行会得到 `true` 而测试失败

**其他**

21. 启动加载失败不阻断 Master 启动，快照未就绪时 GET 返回 503 而非空图
22. **重建耗时分段打点** + **锁指标**（`lock_wait` p95/p99、deadlock count、`history_list_length`）
23. `@WebMvcTest` + mock service 绕开 gRPC 18081 端口冲突

### L2 — Spark provider

- GRAVITINO `service_ddl.json` 的 `configWriter.generators[0].includeParams`（当前 9 项）增加 4 项：
  `gravitino.lineage.source` / `.processorClass` / `.sinks` / `.queueCapacity`，`sinks` 指向 datasophon-api 的 `/v2/lineage`
- SPARK3 侧下发 `spark.extraListeners` + `spark.openlineage.transport.*` + `spark.sql.gravitino.uri` / `.metalake`
- 新增 `GravitinoDdlLoadTest` 补充用例（照抄现有 DDL 加载测试）

**验收**：提交一个真实 Spark SQL 作业 → `t_ddh_lineage_edge` 出现正确的边；`gravitino_lineage.log` 与 MySQL 内容一致。

### L3 — 血缘图页面

`src/pages/Cluster/DataLineage/{index.tsx,LineageGraph.tsx,service.ts,lineageGraph.ts,lineageGraph.test.ts}` + `config/routes.ts`

**强复用** `src/pages/Cluster/ObservabilityCollector/TopologyTab.tsx`：`toGraphData()`（已 export）、G6 v5 `Graph`/`IElementEvent` 用法、`antv-dagre` LR 布局、`fitView`/`zoomTo(0.68)`/`focusElement` 那整段渲染兜底（含 `renderFailed` 降级）。血缘图与调用拓扑图形语义几乎同构。

差异：节点按 `dw_layer` 分列（CDC→ODS→DWD→DWS→ADS），dagre rank 固定层级；点击节点抽屉展示上下游表 + 产出作业 + 该作业指标。

**⚠️ 三处不能照抄**（`TopologyTab` 对节点数**零保护**：`toGraphData()` 全量转换 → `graph.render()` → `graph.fitView()`，`TopologyTab.tsx:224-240`；`renderFailed` 只是 `.catch()` 兜底而非限流。服务拓扑几十个节点无碍，血缘图直接照抄必挂）：

|        TopologyTab 现有        |                 血缘图                  |
|------------------------------|--------------------------------------|
| `toGraphData()` 全量转换         | 前置 300 节点断言，超出走截断提示而非渲染              |
| `fitView()` + `zoomTo(0.68)` | 默认**聚焦 root 节点**并保持 1.0 缩放，不 fitView |
| 单次 `setData()` 全量            | 折叠节点展开走增量 `addData()`，不重布局全图         |

300 这个上限是从 antv-dagre 的 crossing minimization 复杂度 `O(V·E)` 倒推的：300 节点几百毫秒，2000 节点是秒级到十几秒。

##### 逻辑边的展开交互（Codex 二轮 P2-7：§3.4.2 定了数据模型，但 L3 交付契约漏了）

§3.4.2 裁决图上一条边可携带多个作业，§L7 又假设前端能维护 `edge.jobs[]` 并按 `edgeId` 回填 —— 但 L3 原本只定义了**节点**抽屉，边的交互是空白。补齐：

|        交互         |                      契约                       |
|-------------------|-----------------------------------------------|
| 点击聚合边             | 打开作业列表抽屉，每行一个作业；**行的稳定键是 `edgeId`**（流量标签按此回填） |
| 边样式               | `jobs[]` 中存在 `STREAM` 则用流式样式，否则批式；标签显示"N 个作业" |
| **generation 变化** | **原子清空**节点、逻辑边、展开状态、流量标签四者 —— 不能只换图数据留着旧的展开态  |
| L7 返回 409         | 同上，整图重载                                       |

> **前端最容易出的 bug**：折叠节点展开走增量 `addData()`，若 generation 变了却只追加新数据，会把两个代际的图**拼接**成一个现实中不存在的结构。这个状态清理必须是原子的。

**L3/L7 验收补充**：同一逻辑边同时含 BATCH + STREAM 时渲染正确；generation 切换后不出现混图；`/edges/flow` 除校验 generation 外还需验证 `edgeId` 确属当前快照的 current 集合。

### L4 — `pipeline.run` span

按 §3.2 在 `LineageIngestService` 落库成功后同步发 span。**必须走不采样的 pipeline**，否则丢的是元数据不是遥测。

### L5 / L6 — DS 与 Flink provider

两者复用同一个 SQL 解析器（`lineage/parser/SqlLineageParser`）。Flink 额外需要 `FlinkCdcPipelineLineageParser`（SnakeYAML，因为 `flinkcdc → ods` 这一跳只存在于 CDC pipeline 定义里，SQL 解析器完全看不到）。

**L6 第一天先做 30 分钟 `./mvnw -pl datasophon-api dependency:tree` spike** —— `flink-sql-parser` 会带 Calcite/avatica/guava/protobuf，与 Spring Boot 3.4.5 + gRPC 1.68.1 可能撞版本。Plan B：解析器独立成 Maven module，或降级为正则+轻量词法（表级粒度可覆盖 80%，CTE/子查询/视图会漏）。

### L7 — 边流速叠加

- Doris 新建血缘边流量表（UNIQUE KEY，**照抄 `otel_traces_graph` 的建表与 `CREATE JOB` 写法**，注意 JOB 名格式 `database:table_graph_job`）
- **流量标签走独立的第二次请求，不塞进 `GET /v2/lineage/graph`**：

  ```text
  GET  /v2/lineage/graph          → 只返回结构（内存快照，<1ms）
  POST /v2/lineage/edges/flow     → { generation: 4471, edgeIds: [...] }，一次 IN 批量查 Doris
  ```

  边先渲染成灰色骨架，标签到了再染色。理由：200 条边逐条查 Doris 是 N+1（单查询 50～200ms × 200 = 10～40 秒）；且 §D4 的分型设计下流作业标签还要额外查 `otel_metrics_*`，两条链路串行等待不可接受。拆开后 **Doris 慢查询不会让整张图打不开**。

- **`edgeIds` 是物理 edge ID，不是逻辑边**（Codex 审查 P1-3）：§3.4.2 裁决图上一条逻辑边可携带多个作业，因此前端要把 `edge.jobs[].edgeId` 全部摊平后再请求，返回结果按 `edgeId` 回填到对应的 job 上。**一条逻辑边可以同时有 BATCH 和 STREAM 两个标签**，边样式取"存在 STREAM 则用流式样式"。

- 请求必须带 `generation`：若与当前快照代际不符，返回 409 让前端重新加载图 —— 否则会拿旧 edgeId 查新流量映射，显示张冠李戴的标签（§3.4.5 陈旧性契约）

- 前端按 §D4 分型渲染

**Flink 侧前置改动**（回写 Phase G 的 P1/P2）：
- P1 的 `metrics.reporter.otel.filter.excludes` **只排 subtask 级，保留 operator 级** —— 边级速率在多输入/多输出作业上必须靠 source/sink operator 指标（`operator_name` 含表名）才能归属到具体边。基数账：20 作业 × 50 subtask ≈ 数万 series（不可接受）；20 作业 × ~8 operator ≈ 数百 series（可接受）
- P2 的 `ALLOWED_ATTR_FILTER_KEYS` 中 `operator_name` 从"可选"升级为**必需**

---

## 五、与 Phase G 的合并方式

|    Phase G 条目     |                      处置                       |
|-------------------|-----------------------------------------------|
| P0 Flink 现场核查     | **保留**（Flink 监控仍需要）                           |
| P1 Flink 配置接入     | **保留**，但按 §L7 修改 `filter.excludes` 默认值        |
| P2 Flink 监控看板     | **保留**，`operator_name` 提级为必需                  |
| P3 FLINKCDC 服务    | **保留**，不受影响                                   |
| **P4 Flink 作业管理** | **由 L1 取代**（泛化为 `t_ddh_data_job`，覆盖三种 engine） |
| **P5 Flink 血缘采集** | **由 L2/L5/L6 取代**（Flink 降为第三个 provider）       |
| **P6 血缘图页面**      | **由 L3 取代**（页面路径改 `DataLineage`）              |
| P7 告警 + 文档        | **保留**，不受影响                                   |

Phase G 文档头部已加指针说明。

---

## 六、风险清单

### 会导致返工的

|                  风险                   |                            影响                            |                                              缓解                                               |
|---------------------------------------|----------------------------------------------------------|-----------------------------------------------------------------------------------------------|
| **Gravitino dataset identifier 拼写未知** | canonical_name 转换函数写错 → Spark 与 Flink/DS 产出的节点对不上，图断成两半  | **L0 #2 必须实机采样**，不得推断                                                                         |
| `pipeline.*` 语义约定未 stable             | #3762 定稿后属性需改名                                           | 属性名集中在一个常量类，改名只动一处；span 是旁路，改名不影响 MySQL 权威数据                                                  |
| 沙箱无可用 Spark 作业                        | L2 拿不到真实数据，退化为构造事件                                       | L0 #3 提前确认；无则先造一个最简 Spark SQL 样例作业                                                            |
| ~~内存图与 MySQL 双写不一致~~（**已消除**）         | ~~内存里存在 DB 中不存在的边，无日志无异常~~                               | **§3.4.3 规则 ① 消除**：内存图不接受增量修改。此行保留仅作记录 —— 谁若重新引入增量路径，风险会一并回来                                  |
| **写路径读到陈旧快照**（一轮 P0-1）                | 重复写版本、并发写冲突、多个 `is_current` 并存；**表现为边表异常膨胀**             | §3.4.3 规则 ② + §3.4.4 DB 内 structural hash 条件写入；L1 验收 7/8                                      |
| **晚到的旧 run 回滚新结构**（二轮 P0-1）           | 已失效的旧结构被重新设为 current，图上显示错误的上下游                          | §3.4.4 `current_watermark` 单调水位 + `t_ddh_lineage_event` 投递幂等；**依赖 L0 #8 确认上游有可靠序号**；L1 验收 4/5 |
| **A→B→A 回退撞唯一键**（二轮 P0-1）             | 合法的发布回滚直接写入失败                                            | 唯一键改为 `(job_id, version)`，**不能用 `(job_id, content_hash)`**；L1 验收 6                            |
| **三个重建触发源竞争**（一轮 P0-2）                | 慢重建覆盖新快照 → 图倒退；分页跨 `is_current` 翻转 → 快照含半个作业             | §3.4.5 single-flight + 单只读事务 + 代际单调发布；L1 验收 10/11/15                                          |
| **`stale` 漏报**（二轮 P0-2）               | 已知落后 DB 却报告新鲜 → **fail closed 不启动** → 拿旧图做删表决策           | §3.4.5 两层新鲜度：`snapshotStale` 含 generation 落后与重建失败；L1 验收 18                                    |
| **重建占用请求线程**（二轮 P1-3）                 | 手动 POST 同步等整次重建；AFTER_COMMIT 拖长 ingest；补跑循环线程饥饿          | §3.4.5 独立单线程 executor + 轮数/墙钟预算 + 无丢唤醒交接；L1 验收 12/13/14                                       |
| **多实例误启动**（二轮 P1-5）                   | 两个 Master 各持不同快照，写侧各自判定 → 版本重复放大                         | §3.4.5 MySQL 单例租约，失败则 readiness DOWN（**不是打日志**）；L1 验收 16                                      |
| **共享节点更新死锁**（二轮 P1-6）                 | 整点批作业并发更新共享维表 `last_seen`，反序加锁 → 死锁                      | 固定加锁顺序 + 整事务有限重试；`GREATEST(last_seen, ?)` 防时间回退；L1 验收 9                                       |
| **结构变化率假设未经验证**（Codex P1-6）           | 若 hash 不稳定（数组顺序 / 动态表名 / 分区），边表按运行次数膨胀，一年可达 5500～7300 万行 | **L0 #6/#7 实测**，不得推断；四个计数器上线即埋（§3.4.4）                                                        |
| **快照陈旧导致错误决策**（Codex P1-4）            | 删表前的影响分析漏掉新下游 → **生产事故**（不只是"慢"）                         | §3.4.5 陈旧性契约：GET 返回 `stale`，影响分析类接口 `stale=true` 时 fail closed                                |

### 技术选型不确定

- **`flink-sql-parser` 传递依赖冲突**（L6）：Calcite 带 avatica/guava/protobuf，与 Spring Boot 3.4.5 + gRPC 1.68.1 可能撞版本。L6 第一天先 spike。
- **`CREATE VIEW` 展开**：不展开则 dwd→dws 断链。建议先不展开，在 `parse_log` 标记 `UNRESOLVED_VIEW` 让人工可见，后续补。

### 容量风险

Flink metrics 基数：20 作业 × 50 subtask 不过滤 → 数万 series，`otel_metrics_gauge` 日增量与 `buildRangeRateSql` 的多层 `LAG` 窗口函数都会吃不消。**保留 operator 级、排除 subtask 级**是同时满足边级归属与容量的唯一点。

---

## 七、验证命令

```bash
cd /Users/pro/IdeaProjects/datasophon

# 通用（每 Phase 收尾必跑）
./mvnw spotless:apply                    # docs/*.md 的 spotless 归父 pom，-pl 扫不到
./mvnw -pl datasophon-api -am test
cd datasophon-ui-v2 && npm run lint && npm run test

# L0 现场核查
curl -X POST http://<gravitino>:8090/api/lineage \
  -H 'Content-Type: application/json' -d @sample-runevent.json
ssh <gravitino节点> 'tail -20 $GRAVITINO_HOME/logs/gravitino_lineage.log'

# L1/L2
./mvnw -pl datasophon-api -am test -Dtest='LineageIngestServiceTest,GravitinoDdlLoadTest'
mysql -h<master> -uroot -e "SELECT canonical_name FROM datasophon.t_ddh_lineage_node LIMIT 20"

# L4
mysql -h127.0.0.1 -P9030 -uroot -e \
  "SELECT trace_id, span_name, CAST(span_attributes AS STRING) FROM otel.otel_traces WHERE span_name='pipeline.run' LIMIT 5"

# L3/L7 浏览器 E2E（人工）
cd datasophon-ui-v2 && npm run dev
```

**新增 `@SpringBootTest` 必须加 `@DirtiesContext`**（否则抢 gRPC 18081，全量测试必挂，报错表象会伪装成 MySQL 连接失败）。

---

## 八、参考

- [Gravitino Server Lineage support (1.3.0)](https://gravitino.apache.org/docs/1.3.0/lineage/gravitino-server-lineage/)
- [Gravitino Spark Lineage support (1.3.0)](https://gravitino.apache.org/docs/1.3.0/lineage/gravitino-spark-lineage/)
- [OpenTelemetry semantic-conventions#3762 — `pipeline.*` proposal](https://github.com/open-telemetry/semantic-conventions/issues/3762)
- [OpenLineage — Flink 2.x integration](https://openlineage.io/docs/integrations/flink/flink2/)
- [FLIP-314: Support Customized Job Lineage Listener](https://cwiki.apache.org/confluence/display/FLINK/FLIP-314:+Support+Customized+Job+Lineage+Listener)
- [DolphinScheduler Lineage · discussion #6596](https://github.com/apache/dolphinscheduler/discussions/6596)
- 仓库内：`docs/gravitino-metadata-service-实施计划-2026-07-28.md`、`docs/observability-otel-phaseG-flink-血缘与监控-实施计划-2026-07-27.md`、`docs/observability-otel-doris-设计-2026-06-19.md`

