# Gravitino 血缘绑定改造 — 任务清单（交 Codex 实现）

> 出清单：Claude，2026-08-24。实现：Codex。验收：Claude。
> 目标仓库：**Gravitino fork** `/Users/pro/IdeaProjects/gravitino`（不是 datasophon 主仓）。
> 上游背景与决策依据：datasophon 仓库 `.scratch/ds-workflow-tab/`（本地，已 gitignore）的
> 票 `11`（绑定通道选型）与票 `05`（沙箱实测）。

## 0. 一句话目标

让平台能够**按 DS 任务实例 id 精确查到那一次运行的血缘统计**，从而在 DolphinScheduler
工作流 DAG 的节点上显示「该任务本次写了多少行、多大」。

## 1. 为什么要改（现状与缺口，均已实测）

### 1.1 数据已经在库里了

2026-08-24 在沙箱 `ddh-02` 跑真实 Spark 作业验证通过：

- Spark 侧加 `--conf spark.datasophon.dsTaskInstanceId=<id>`，并把该 key 加进
  `spark.openlineage.capturedProperties`，该键值**原样进入** OpenLineage 事件的
  `spark_properties` run facet：

  ```json
  "properties":{"spark.app.id":"local-1787563137766","spark.master":"local[2]",
                "spark.datasophon.dsTaskInstanceId":"DSTI-99887766",
                "spark.app.name":"ds-bind-probe-20260824"}
  ```

- 该事件完整落在 `gravitino_lineage_1.lineage_event_payload.raw_event`（byte-for-byte 原文）。
  沙箱现有 **35 条**事件带此 key。
- 同一批事件的 `outputStatistics` 行数**精确**（探针写 1000 / 500 行，facet 即
  `rowCount:1000,size:9495,fileCount:2` 与 `rowCount:500,size:5537,fileCount:2`）。

### 1.2 但读不出来

`gravitino/lineage/src/main/java/.../JdbcLineageStorage.java` 的解析路径**写死**：

| 现状 | 位置 | 问题 |
|---|---|---|
| `runningAppId` 只取 `/run/facets/spark_properties/properties/**spark.app.id**`，取不到再顺 parent runId 找 `/run/facets/spark_applicationDetails/applicationId` | `:1212-1224` | **只认这两个固定路径**，自定义 key 一律读不到 |
| 候选 run **只有** `lineage_job.current_run_id` | 见 `loadCurrentEdges` / `withOutputStatistics` `:1044` | 历史运行的统计取不到 |
| 统计只取 RUNNING / COMPLETE，排除 START | SQL `:1272` + Java `:1317` | （保留此语义，不要改） |
| 每 run 最多解析最近 `MAX_STATISTICS_EVENTS_PER_RUN=20` 条 | `:1257` | （保留） |
| facet 缺失存 `null` 而非 0（`nullableLong` `:1359`） | `:1338-1359` | （保留） |
| `lineage_job.name` 是 **TEXT 且无索引**，且无「按作业名查 job」的接口 | — | 名字不能当查询键 |
| `flowType` 恒为字面量 `"TABLE"`，三处硬编码 | `:1044` / `:1130` / `:367` | 不区分批流（**本次不修**，平台侧改用 DS 的 `taskType` 判定） |

## 2. 改造范围

### T1 — 事件解析：把「自定义运行标识」做成**可配置**，不要再硬编码

**不要**再加一个写死的 `spark.datasophon.dsTaskInstanceId` 分支——那是在重复现状的错误。

- 新增配置项（沿用 `LineageConfig` 的既有风格，参考其中 `storage.abandonedRunTimeoutHours`
  默认 24 的写法，`LineageConfig.java:199-207`）：
  ```
  gravitino.lineage.storage.externalRunKeys = spark.datasophon.dsTaskInstanceId
  ```
  逗号分隔，允许多个；默认空（**空 = 完全保持现有行为**）。
- 解析时从 `/run/facets/spark_properties/properties/<key>` 依次取值，取到第一个非空即止。
- **只解析，不推断**：取不到就是 null，不要回退到别的字段。

**完成判据**：喂一条 §1.1 那样的事件，能解析出 `DSTI-99887766`；不带该 key 的事件解析为 null，
且其余行为与改造前完全一致。

### T2 — 存储：新增列 + 索引 + schema 版本

- 在 **`lineage_run`** 上新增 `external_run_key VARCHAR(255) NULL`
  （**放 run 不放 job**：该 id 标识的是"某一次运行"，一次 DS 任务实例 = 一个 Spark 应用 = 一个 run）。
- 建索引：`KEY idx_lineage_run_external_key (external_run_key)`。
  ⚠️ 必须建索引——这是查询主键，`lineage_job.name` 无索引的教训就在眼前。
- 走既有的版本化迁移机制（`lineage_schema_version` 表 + `1.0.0 → 1.1.0` 的脚本目录约定），
  新增下一个版本号的脚本；**不要直接改历史脚本**。
- **幂等**：迁移脚本重复执行不得报错。

### T3 — 回填（可选但建议）

库里已有 35 条带该 key 的事件。写一个可重跑的回填：
扫 `lineage_event_payload.raw_event`，按 T1 的规则解析，回填对应 `lineage_run.external_run_key`。
**幂等、可中断续跑、不得改动 `raw_event`**。

### T4 — 读取路径：放宽候选 run，支持"按指定 run 取统计"

现状 `withOutputStatistics` 只对 `current_run_id` 计算统计（`:1044` 附近，`.orElse(job)` 保留
`loadCurrentEdges` 里初始的三个 null）。

- 抽出一个「给定 runId → 该 run 的 outputStatistics」的方法，**保持原有取值语义不变**
  （只取 RUNNING/COMPLETE、排除 START、按 `event_id DESC` 取第一个非空 facet、
  最多 20 条、缺失为 null、`lastRunAt` 取被采纳事件的 `lineage_event.event_time`）。
- 原 `current_run_id` 路径改为调用它，**行为必须逐字节等价**（这是回归风险最高的一处）。

### T5 — 新接口：按 external run key 查

新增（路径风格与既有 `/api/lineage/*` 一致）：

```
GET /api/lineage/run/by-external-key/{key}
```

返回该 run 的：`runId` · `jobId` · `jobName` · `eventType/terminalState` · `startedAt` · `lastRunAt`
· **每个输出数据集的 `rowCount` / `size`**（**按数据集分列，不要求和**——一次提交会产出多条 job，
平台侧要按输出表分别展示）。

- key 不存在 → **404**，不要返回空对象。
- 一个 key 命中多个 run（不该发生，但要防）→ 取最新的一个，并在响应里标注 `ambiguous: true`。

### T6 — 补齐平台侧透传（datasophon 仓库，非 Gravitino）

`datasophon-api/.../controller/v2/LineageV2Controller.java` 目前**未透传**
`/run/{runId}/events`、`/table/{id}/history`、`/diff`，且 graph/tables 透传时**丢掉了 `asOf` 参数**。
本次至少补上 T5 的新端点；其余按需。

## 3. 明确不做

- **不改** `flowType` 的硬编码（平台侧改用 DS 的 `taskType` 判定批/流）。
- **不改** START 事件被排除、20 条上限、null 语义这些既有取值规则。
- **不做** 按 `job.name` 的模糊匹配查询——两侧命名格式完全不一致
  （Spark 官方集成强制规范化为 `<appName>.<节点名>.<数据集名>`；Flink emitter 是人工自由文本），
  按名字匹配会张冠李戴。

## 4. 验收（Claude 执行）

1. **回归**：改造前后，对同一批现有数据调 `/api/lineage/graph`，`GraphJob` 的
   `lastRowCount` / `lastBytes` / `lastRunAt` / `runningAppId` **逐字段一致**。
2. **新能力**：用 §1.1 的探针重跑一次
   （`ddh-02:/data/spark-sample/dsbind-probe-20260824/run-probe.sh`，token 从 stdin 喂），
   `GET /api/lineage/run/by-external-key/DSTI-<新id>` 能返回 `rowCount` 与实际写入行数一致。
3. **历史运行**：同一 DS 任务实例 id 跑两次，第一次的 key 仍能查到**第一次**的行数
   （证明已摆脱 `current_run_id` 的限制）。
4. **未配置时零影响**：`externalRunKeys` 留空，全部行为与改造前一致。
5. **迁移幂等**：迁移脚本连跑两次不报错。

## 5. 沙箱环境备忘

- Gravitino 实际运行实例：`ddh-02:/data/install_datasophon/gravitino` →
  软链到 `gravitino-1.3.1-SNAPSHOT-bin-new`，REST `8090`。
- 鉴权：`gravitino.authenticators = oauth`，`signAlgorithmType = **HS256**`（对称），
  `serviceAudience = GravitinoServer`；签名密钥在其 `conf/gravitino.conf` 的
  `gravitino.authenticator.oauth.defaultSignKey`（44 字符 base64）。
  自签 JWT 即可访问（`sub` 用 `datasophon-lineage-proxy`）。**密钥不得出现在任何提交物里。**
- 血缘存储：`ddh-01` 的 MySQL，库 `gravitino_lineage_1`。
- ⚠️ **重启风险**：该实例正在承接沙箱里 `lineage_flink_verify` 等库的血缘事件，
  **重新部署前先确认没有其它验证任务在跑**。
- ⚠️ 图查询走 `LineageGraphCache` 的**内存快照**，不是实时查库；
  `lineage_graph_generation` 只在 COMPLETE 事件时递增。改完记得确认快照会刷新。

---

# 追加：T7 返工 —— 按 external key 查必须**聚合该 key 下的全部 run**

> 2026-08-25 实机验收（`ddh-02`，新 jar 已上线）抓出。**这是本清单 T5 的建模错误，不是实现错误**：
> Codex 忠实实现了 T5 写的语义，是 T5 自己把「一次 DS 任务实例 = 一个 Spark 应用 = 一个 run」
> 当成了事实。实机否证了这个前提。

## 1. 实测事实（一次 `spark-sql` 会话 = 7 个 run）

探针 `run-probe-0825.sh`（注入 `DSTI-20260825-01`，SQL 见 `probe.sql`：两次 CTAS，写 1000 / 500 行）
跑完后，库里该 key 下有 **7 个 run**：

| job 名 | 带 `outputStatistics` |
|---|---|
| `ds_bind_probe_20260824`（**应用级 run**，job 名 = appName） | ❌ |
| `….execute_create_data_source_table_as_select_command.default_dsbind_dst_20260824` | ❌ |
| `….execute_insert_into_hadoop_fs_relation_command.warehouse_dsbind_dst_20260824` | ✅ `rowCount=500,size=5537` |
| `….execute_create_data_source_table_as_select_command.default_dsbind_src_20260824` | ❌ |
| `….execute_insert_into_hadoop_fs_relation_command.warehouse_dsbind_src_20260824` | ✅ `rowCount=1000,size=9495` |
| `….drop_table` × 2 | ❌ |

**只有 `execute_insert_into_hadoop_fs_relation_command.*` 那类 run 带统计**，而
**`event_id` 最大的那个恰恰是应用级 run（无 outputs）**。

现接口取「最新 run」，于是：

```
GET /api/lineage/run/by-external-key/DSTI-20260825-01
→ {"runId":"01a03676-…","jobName":"ds_bind_probe_20260824",
   "terminalState":"COMPLETE","outputs":[],"ambiguous":true}
```

**`outputs` 恒为空** —— 而库里明明有 1000 / 500 两条。DAG 节点上永远显示不出行数。
昨天回填的 `DSTI-99887766` 同样：14 个 run、4 个带统计、返回 `outputs: []`。

## 2. 改成什么

接口语义从「按 key 找**那一个** run」改为「按 key 聚合**这次任务实例的全部 run**」：

```
GET /api/lineage/run/by-external-key/{key}
{
  "externalRunKey": "DSTI-20260825-01",
  "runCount": 7,
  "startedAt":  <该 key 下所有 run 的最早 event_time>,
  "lastRunAt":  <被采纳的统计事件里最晚的 event_time；无统计则 null>,
  "state":      "COMPLETE" | "FAILED" | "RUNNING",
  "outputs": [                       // 按输出数据集分列，**不求和**（沿用 D6）
    {"namespace":"file","name":"…/dsbind_src_20260824","rowCount":1000,"size":9495,
     "runId":"…","jobId":221,"jobName":"…","lastRunAt":"…"},
    {"namespace":"file","name":"…/dsbind_dst_20260824","rowCount":500,"size":5537, …}
  ],
  "runs": [ {"runId","jobId","jobName","eventType","terminalState","startedAt"} ]
}
```

- **`state` 的聚合规则**：任一 run 是 `FAIL`/`ABORT` → `FAILED`；全部终态且无失败 → `COMPLETE`；
  否则 `RUNNING`。（DS 自己也有任务状态，前端交叉验证用，见 `11` 的末尾清单。）
- **同一数据集被多个 run 写入**时取 `event_id` 最大的那条统计，其余不显示；
  需要排查时看 `runs`。
- **删掉 `ambiguous`**：多 run 是**常态**不是歧义，这个字段现在恒为 true，没有信息量。
  真正需要防的歧义是"同一 key 跨了两次**不同的**任务提交"，用 `runs[].startedAt` 的跨度即可判断，
  不必单列布尔。
- **404 语义不变**：该 key 下一个 run 都没有 → 404。

## 3. 实现要点

- 存储层把 `externalRun(String)` 换成 `externalRunSummary(String)`：
  先一次 `SELECT run_id … WHERE external_run_key = ?` 取全部 runId，
  再复用**已有的** `loadStatisticsEvents(connection, Set<String> runIds, payloadDualWrite)`
  （它本来就收 Set），最后按 `(namespace,name)` 聚合。
- **每 run 20 条统计事件的上限、RUNNING/COMPLETE 过滤、缺失为 null 这三条语义保持不变。**
- `LineageQuery.ExternalRun` 相应改为 `ExternalRunSummary`；`RunOutputStatistics` 加
  `runId/jobId/jobName/lastRunAt` 四个字段。
- 平台侧（datasophon）路径不变，无需改 `GravitinoLineageClient`；但前端契约按新结构写。
- 单测/IT 补一条**关键回归**：一个 key 下混有「应用级 run（无 outputs）」与「带统计的 run」，
  且应用级 run 的 `event_id` 最大 —— 现实现会在这条上失败。

## 4. 已通过的部分不要动

T1（解析）/ T2（迁移）/ T3（回填）/ T4（放宽候选 run）**实机全部验证通过**，见票 `05`。
本次返工只碰 T5 的响应建模与其存储层查询。

## 5. T7 返工进度（Codex，2026-08-25）

> 状态：**代码返工完成，源码与 OpenAPI 校验通过；MySQL 容器 IT、沙箱部署和实机探针待执行。**
> Gravitino 工作区：`/Users/pro/IdeaProjects/gravitino`，分支 `feat/lineage`，当前尚未提交。

| 检查项 | 状态 | 实现/证据 |
|---|---|---|
| `ExternalRun` 改为聚合响应 | DONE | `LineageQuery.externalRunSummary` 返回 `ExternalRunSummary`；根对象包含 `externalRunKey/runCount/startedAt/lastRunAt/state/outputs/runs`。 |
| 查询 external key 下全部 run | DONE | `JdbcLineageStorage.externalRunSummary` 一次读取全部 run 元数据，并将完整 runId 集合传给既有 `loadStatisticsEvents(connection, Set<String>, payloadDualWrite)`。 |
| 按输出数据集聚合统计 | DONE | 以 `(namespace,name)` 为键；同一数据集跨 run 重复时保留 `event_id` 最大的统计。无统计的应用级 run 保留在 `runs`，不进入 `outputs`。 |
| 输出项携带来源信息 | DONE | `RunOutputStatistics` 已增加 `runId/jobId/jobName/lastRunAt`。 |
| 聚合状态 | DONE | 任一 `FAIL/ABORT` → `FAILED`；全部 run 有终态且无失败 → `COMPLETE`；否则 → `RUNNING`。 |
| 删除 `ambiguous` | DONE | Java 响应模型、REST 测试、OpenAPI 和用户文档均已删除该字段。 |
| 404 语义 | DONE | key 无任何 run 时存储返回 empty，REST 路径继续映射为 404。 |
| 关键回归用例 | IMPLEMENTED | `TestJdbcLineageStorageIT.testExternalRunSummaryAggregatesApplicationAndCommandRuns`：最后插入无统计的应用级 run，仍断言返回较早命令级 run 的 1000/500 行统计；另覆盖重复数据集取最新统计和 FAILED/RUNNING 聚合。 |
| T1–T4 | UNCHANGED | 本轮未扩展其语义；START 排除、每 run 最多 20 条、RUNNING/COMPLETE 过滤及 null 语义沿用原实现。 |

### 5.1 已完成的自动验证

以下命令均使用 JDK 17：

```text
./gradlew :lineage:check -PskipITs
→ BUILD SUCCESSFUL

./gradlew :docs:build
→ BUILD SUCCESSFUL；docs/open-api/openapi.yaml validated

git diff --check
→ PASS
```

非 Docker 的 lineage 全量单测、REST 响应契约、Spotless 和编译均已通过。

### 5.2 尚未完成，禁止标记验收通过

1. **MySQL/Testcontainers IT 未实际执行**：本机 Docker Desktop 可用，但
   `mac-docker-connector` 未运行；Gravitino 构建脚本主动排除了 `gravitino-docker-test`，定向执行
   `TestJdbcLineageStorageIT` 时报告 `No tests found`。用例已编译，但这不等于运行通过。
2. **尚未重新打包、部署到 `ddh-02`**；按 §5 的风险提示，部署前必须确认没有其他血缘验证任务在跑。
3. **尚未重跑 `run-probe-0825.sh`**，因此还没有新接口返回 7 个 run、1000/500 行统计的实机证据。
4. **尚未提交/推送**；Claude 复审前应先确认本节契约与 diff，再决定提交范围。

### 5.3 Claude 下一步验收

1. 在 OrbStack 或已运行 `mac-docker-connector` 的环境执行：

   ```text
   ./gradlew :lineage:test \
     --tests org.apache.gravitino.lineage.storage.TestJdbcLineageStorageIT \
     -PskipDockerTests=false
   ```

2. 安全部署新包后重跑探针，确认：
   - `runCount` 等于该 external key 在库中的实际 run 数；
   - `runs` 同时包含应用级和命令级 run；
   - `outputs` 返回源表 1000 行、目标表 500 行及对应 size；
   - `outputs[].runId/jobId/jobName/lastRunAt` 指向实际携带统计的 run；
   - 响应中不存在 `ambiguous`。
3. 用不存在的 key 验证 404，并构造 RUNNING/FAIL 场景复核聚合状态。
