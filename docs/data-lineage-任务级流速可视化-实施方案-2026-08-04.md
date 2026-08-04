# 数据血缘·任务级流速可视化 实施方案（2026-08-04）

> **本文是可执行的实施方案，不是调研。** 交付方式：Codex 按任务清单实现，Claude 审查与验证。
>
> 前置阅读：[流速采集调研](./data-lineage-流速采集调研-2026-08-04.md)（注意：该文 §2 的部分结论已被本文 §1 的实测推翻，以本文为准）。
>
> **Codex 执行规则见 §7，每完成一个任务必须立即回写 §6 进度表。**

---

## 1. 实测证据基线

以下全部为 2026-08-04 在五节点沙箱（ddh-02，`192.168.10.132`）的实机验证结果，非推断。
证据文件保留在 ddh-02 的 `/data/spark-sample/rate-probe{,2,3}/` 与 `/tmp/carbon-test.yaml`。

| # | 结论 | 验证方式 |
|---|---|---|
| E1 | Spark 3.5.8 自带 `GraphiteSink`，`metrics-graphite-4.2.19.jar` 已在 `jars/`，**零额外依赖** | `unzip -l spark-core_2.12-3.5.8.jar` + 真实推送成功 |
| E2 | `spark.metrics.conf.*.sink.graphite.*` **内联语法生效**，无需 `metrics.properties` 文件 | 真实作业中 `GraphiteReporter` 按 10s 周期正常工作 |
| E3 | `GraphiteSink` 支持 `regex` 键做**源头指标过滤**，另有 `prefix` 键 | 反编译 `GraphiteSink.class` 常量池 + 实测生效 |
| E4 | **`spark.metrics.conf` 的 `regex` 是子串匹配**，写 `.*executor.*` 会误纳 `LiveListenerBus.queue.executorManagement.*` | 实测漏进 135 个数据点 |
| E5 | Spark 全部 I/O 指标是 `Counter`/`Gauge`，**没有任何速率字段**；带 `m1_rate` 的只有 Timer（listener/scheduler 处理耗时，与吞吐无关）。**换 StatsD 同理** | 实测全指标后缀统计：1715 个 `.count`，0 个 I/O 类 rate |
| E6 | `otelcol-contrib 0.156.0` 含 `carbonreceiver`（带 regex parser）与 `statsdreceiver` | 二进制 `compileRegexRules` / `regexDefaultConfig` 符号 |
| E7 | carbonreceiver regex：`key_X`→标签，`name_N`→按序拼指标名，`type: cumulative/gauge`→`Sum`/`Gauge` | 隔离 collector 实例端到端实测，435 数据点 0 refused |
| E8 | **未匹配任何规则的报文不丢弃，回退 plaintext**：整条 path 当指标名且无标签 → appId 导致**指标名基数爆炸** | 实测输出 `Name: local-178581….driver.executor.filesystem.hdfs.write_bytes` |
| E9 | OpenLineage `spark_applicationDetails` 默认启用，但**只挂在 application 级 run**，SQL 级 run 无 appId | 40 个真实事件解析 |
| E10 | **`spark.openlineage.capturedProperties` 可让每个 SQL 级事件直接带 `spark.app.id`** → 关联键无需 parent 跳转 | 实测 `spark_properties` 含 `spark.app.id` |
| E11 | `outputStatistics` **落在 RUNNING 或 COMPLETE 事件上不确定**，取决于执行计划形态 | 两次实验各观察到一种 |
| E12 | **长作业执行期间 OpenLineage 零数据点**：3000 万行作业 3 分 40 秒内只有启动阶段 4 个事件，Graphite 同期 4726 行 | 1.2 亿行长作业对比实验 |
| E13 | `ExecutorSource` 累计量在 **task 结束时**才累加，粒度是 task 边界；只有 `activeTasks`/`runningStages` 等 Gauge 真正每周期更新 | 同上实验的指标时序 |
| E14 | Spark 指标**没有 task 总数**（无 `totalTasks`/`numTasks`）→ 进度无法带分母 | 全量指标名清单（100+ 项）核对 |
| E15 | `lineage_event.raw_event` 存完整原始事件，**所有事件类型（含 RUNNING）都持久化** | `JdbcLineageStorage.java:385` 无条件写入 |

### 1.1 未验证项（实施中需注意）

- **YARN 模式下的指标实例名格式**：local 模式恒为 `driver`；YARN 下应为 executor 编号。影响 `key_instance` 取值范围，**不影响规则结构**。阶段 B 装 YARN 后需复验。
- Doris 侧 rate builder 对 Spark 指标的适配（复用 JuiceFS 模式，但未实跑）。

---

## 2. 决策全表

| # | 决策点 | 结论 |
|---|---|---|
| D1 | 数据分工 | **运行中走 OTel，起止走 OpenLineage**（E12 证实此分工必要） |
| D2 | 图结构 | **三元图**：表 → 任务 → 表 |
| D3 | 三元图层位 | **前端渲染层变换**，按 `jobId` 去重；后端 `GraphData` 保持二元 |
| D4 | 数据汇合 | **双端点分离**：统计随 `/lineage/graph`（低频）；速率走新端点（高频轮询） |
| D5 | 运行态传递 | **进快照**，`GraphJob` 加字段 |
| D6 | 采集方式 | **Graphite 推送 + carbonreceiver regex**（E1/E2/E7） |
| D7 | 节点 label | **双态**：运行中显进度，空闲显总量 |
| D8 | 运行中 label 内容 | **进度为主 + 数值降级**，详情进 tooltip（因 E13 速率在 task 间隔恒为 0） |
| D9 | 进度分母 | **无分母**（E14），形如 `✓12 task · 6000万行` |
| D10 | 分期 | **两期**：期一零采集依赖，期二接 OTel |
| D11 | 配置下发 | DDL 参数就位 + ddh-02 手工配置验证，不等阶段 B |

---

## 3. 契约定义（**并行的前提，先于所有编码，不得擅自更改**）

任何一方偏离本节契约都会导致联调失败。如需变更，先改本节并在进度表备注。

### 3.1 `GraphJob` 扩展（gravitino fork）

```java
// lineage/src/main/java/org/apache/gravitino/lineage/LineageQuery.java
record GraphJob(
    long jobId,
    long edgeId,
    String flowType,
    String jobName,            // 期一新增：任务节点显示名
    @Nullable Long lastRowCount,   // 期一新增：最近一次写入该边目标表的行数
    @Nullable Long lastBytes,      // 期一新增：对应 size
    @Nullable Instant lastRunAt,   // 期一新增：该统计所属事件的 eventTime
    @Nullable String runningAppId  // 期二新增：运行中 run 的 spark.app.id；非运行中为 null
) {}
```

前端对应（`datasophon-ui-v2/src/pages/Cluster/Lineage/service.ts`）：

```typescript
export interface GraphJob {
  jobId: number;
  edgeId: number;
  flowType: string;
  jobName: string;
  lastRowCount: number | null;
  lastBytes: number | null;
  lastRunAt: string | null;      // ISO8601
  runningAppId: string | null;   // 期二起非 null 表示运行中
}
```

### 3.2 速率端点（期二，datasophon）

```
GET /ddh/v2/lineage/job-metrics?clusterId={id}&appIds=app1,app2,app3
```

响应（经 `V2ResponseBodyAdvice` 包一层 `ApiResponse`）：

```json
{
  "success": true,
  "data": {
    "local-1785810094051": {
      "completeTasks": 12,
      "activeTasks": 2,
      "recordsWritten": 60000000,
      "bytesWritten": 2204955464,
      "recordsWrittenRate": 51234.5,
      "runningStages": 1,
      "sampledAt": "2026-08-04T03:01:44Z"
    }
  }
}
```

- `appIds` 为空或全部无数据时返回空对象 `{}`，**不报错**
- `recordsWrittenRate` 为 Doris 侧差分算出（单位：行/秒）；无法计算时为 `null`
- 前端轮询间隔 **15 秒**，仅在图上存在 `runningAppId != null` 的任务节点时轮询

### 3.3 OTel 指标命名（期二）

carbonreceiver regex 规则产出的指标名与标签，**T6 查询侧与 T8 采集侧必须一致**：

| 指标名 | 类型 | 标签 |
|---|---|---|
| `spark_executor_recordsWritten` | Sum | `app_id`, `instance` |
| `spark_executor_bytesWritten` | Sum | `app_id`, `instance` |
| `spark_executor_recordsRead` | Sum | `app_id`, `instance` |
| `spark_executor_bytesRead` | Sum | `app_id`, `instance` |
| `spark_threadpool_activeTasks` | Gauge | `app_id`, `instance` |
| `spark_threadpool_completeTasks` | Gauge | `app_id`, `instance` |
| `spark_dagscheduler_stage_runningStages` | Gauge | `app_id`, `instance` |
| `spark_fs_read_bytes` / `spark_fs_write_bytes` | Gauge | `app_id`, `instance`, `scheme` |
| `spark_unmatched_*` | Gauge | `app_id`, `instance` | ← 兜底，仅用于监控规则遗漏，**前端不消费** |

---

## 4. 期一任务清单（零采集依赖，历史数据可直接验证）

> 期一交付后的可见效果：血缘图变成三元图，任务节点显示 `任务名` + `120万行 · 3分钟前`，点任务节点开 Drawer。
> **完全不依赖 Spark metrics 采集**，用 Gravitino 库里已有的历史事件即可验证。

### 并行组 A（无依赖，可同时开工）

#### T1 — gravitino：`GraphJob` record 扩展

- **仓库**：`/Users/pro/IdeaProjects/gravitino`（fork）
- **文件**：
  - `lineage/src/main/java/org/apache/gravitino/lineage/LineageQuery.java`（record 定义，第 123 行）
  - `lineage/src/main/java/org/apache/gravitino/lineage/storage/LineageEdgeValue.java`（引用处）
  - `lineage/src/main/java/org/apache/gravitino/lineage/storage/JdbcLineageStorage.java:592`（`new GraphJob(...)` 构造点）
- **改动**：按 §3.1 加 5 个字段。本任务只加 `jobName` 的真实值（从 `lineage_job.name` 取），统计类字段先传 `null`，由 T2 填充。
- **注意**：`JdbcLineageStorage.loadCurrentEdges()` 里有 `NOTE(C3)` 标注的待人工决策遗留问题（COMPLETE 事件空 outputs 的处理）—— **禁止在本方案内改动该逻辑**。
- **验收**：`./gradlew :lineage:test` 全绿；`GraphJob` 序列化后 JSON 含 `jobName` 字段。

#### T3 — 前端：三元图数据变换

- **仓库**：`datasophon-ui-v2`
- **文件**：`src/pages/Cluster/Lineage/lineageGraphData.ts` + `lineageGraphData.test.ts`
- **改动**：`toG6Data()` 把每条 `LogicalEdge` 展开为 `src → jobNode → dst`：
  - 用 `Map<jobId, 任务节点>` 去重 —— **同一 `jobId` 出现在多条边上时必须收敛成一个任务节点**（这是三元图消除多输出歧义的核心，务必有对应测试）
  - 任务节点 id 用 `job:${jobId}` 前缀，避免与表节点 id 冲突
  - 折叠占位节点（`isCollapsedPlaceholder`）逻辑保持不变，占位边仍直连表节点
  - 新增 `G6JobNodeData` 类型，含 `jobName` / `lastRowCount` / `lastBytes` / `lastRunAt` / `runningAppId`
- **验收**：`npm run test -- lineageGraphData` 全绿，且**必须包含**「一个 job 写 3 张表 → 只生成 1 个任务节点、3 条出边」的测试用例。

### 并行组 B（依赖组 A）

#### T2 — gravitino：解析 `outputStatistics` 入图（依赖 T1）

- **文件**：`lineage/src/main/java/org/apache/gravitino/lineage/storage/JdbcLineageStorage.java`
- **改动**：为快照中每个 `GraphJob` 填充 `lastRowCount` / `lastBytes` / `lastRunAt`：
  1. 对每个 job 的 `current_run_id`，查该 run 的事件（`event_id` 倒序）
  2. **同时接受 RUNNING 和 COMPLETE 事件**（E11），取第一个 `outputs[].outputFacets.outputStatistics` 非空的
  3. **按目标表匹配**：取 `outputs[]` 中 `name` 对应本边 `dst` dataset 的那一项，不能取数组第 0 项
  4. JSON pointer：`/outputs/{i}/outputFacets/outputStatistics/rowCount`、`.../size`
- **性能要求**：`raw_event` 是 MEDIUMTEXT，**禁止在 `loadCurrentEdges` 的主 JOIN 里直接拉该字段**；应对涉及的 run 批量单独查询。
- **验收**：`./gradlew :lineage:test` 全绿 + 新增单测覆盖「统计在 RUNNING」「统计在 COMPLETE」「多 output 按 dst 匹配」三种情形。

#### T4 — 前端：任务节点渲染与交互（依赖 T3）

- **文件**：`src/pages/Cluster/Lineage/LineageGraph.tsx`、`JobDetailDrawer.tsx` + 各自测试
- **改动**：
  - 任务节点用与表节点不同的形状/颜色区分（表节点现为矩形 `#85a5ff`）
  - label 显示：`{jobName}` 换行 `{lastRowCount 格式化} · {lastRunAt 相对时间}`；`lastRowCount` 为 null 时只显示 `jobName`
  - `node:click` 加分支：任务节点（id 以 `job:` 开头）→ 打开 `JobDetailDrawer`；折叠占位符逻辑保持不变
  - `JobDetailDrawer` 增加统计区：行数、字节数、最近运行时间
  - 数字格式化用中文习惯（万/亿），字节用 KB/MB/GB
- **验收**：`npm run lint` + `npm run test` 全绿；`LineageGraph.test.tsx` 新增「点任务节点打开 Drawer」用例。

---

## 5. 期二任务清单（接 OTel 实时链路）

> 期二交付后的可见效果：运行中的任务节点 label 变为 `✓12 task · 6000万行`，边上流动动画，Drawer 内有速率折线图。

### 并行组 C（三个任务完全独立，可同时开工）

#### T5 — gravitino：快照纳入运行中 run

- **文件**：`lineage/src/main/java/org/apache/gravitino/lineage/storage/JdbcLineageStorage.java`
- **改动**：
  - `loadCurrentEdges()` 目前 `WHERE run.terminal_state = 'COMPLETE'`，**排除了运行中的 run**。需额外查询 `terminal_state IS NULL` 的 run，取其最新事件的 `spark_properties.properties['spark.app.id']` 填入 `GraphJob.runningAppId`
  - JSON pointer：`/run/facets/spark_properties/properties/spark.app.id`
  - 若该字段缺失（未配 `capturedProperties` 的旧作业），回退读 `/run/facets/parent/run/runId` 再查父 run 的 `/run/facets/spark_applicationDetails/applicationId`；**两者都取不到则为 null，不报错**
  - **不得改动已有的 COMPLETE 边逻辑**（`NOTE(C3)` 禁区）
- **验收**：`./gradlew :lineage:test` 全绿 + 单测覆盖「有 capturedProperties」「仅有 parent facet」「两者都无」三种情形。

#### T7 — datasophon：SPARK3 DDL 加 metrics 配置

- **文件**：`package/raw/meta/datacluster-physical/SPARK3/service_ddl.json`
- **改动**：在 `parameters` 中新增（与现有 `spark.openlineage.*` 参数同一区块，走 `custom.spark.defaults.conf` 通路）：

  | 配置键 | 值 |
  |---|---|
  | `spark.metrics.conf.*.sink.graphite.class` | `org.apache.spark.metrics.sink.GraphiteSink` |
  | `spark.metrics.conf.*.sink.graphite.host` | `127.0.0.1`（推本机 collector） |
  | `spark.metrics.conf.*.sink.graphite.port` | `2003` |
  | `spark.metrics.conf.*.sink.graphite.period` | `10` |
  | `spark.metrics.conf.*.sink.graphite.unit` | `seconds` |
  | `spark.metrics.conf.*.sink.graphite.regex` | 见下 |
  | `spark.openlineage.capturedProperties` | `spark.master,spark.app.name,spark.app.id` |

- **`regex` 值要求**：**必须锚定路径段，不能用 `.*executor.*` 这种子串匹配**（E4）。参考写法：
  ```
  \.executor\.(records|bytes|shuffle|threadpool|filesystem)|\.DAGScheduler\.(job|stage)\.
  ```
  必须排除 `LiveListenerBus.queue.executorManagement.*` 和 `DAGScheduler.messageProcessingTime.*`（Timer 类型，一个指标展开 15 行，与吞吐无关）。
- **验收**：`python3 -c "import json;json.load(open('package/raw/meta/datacluster-physical/SPARK3/service_ddl.json'))"` 通过；人工核对 regex 不含裸 `.*executor.*`。

#### T8 — datasophon：OTELCOLLECTOR 模板加 carbonreceiver

- **文件**：`package/raw/meta/datacluster-physical/OTELCOLLECTOR/templates/otelcol.ftl`（139 行）
- **改动**：新增 `carbon` receiver 并接入 `metrics` pipeline。规则**直接采用已实测通过的配置**（ddh-02 `/tmp/carbon-test.yaml`）：

```yaml
  carbon:
    endpoint: 127.0.0.1:2003
    transport: tcp
    parser:
      type: regex
      config:
        name_separator: "_"
        rules:
          - regexp: '^(?P<key_app_id>[^.]+)\.(?P<key_instance>[^.]+)\.executor\.threadpool\.(?P<name_0>[a-zA-Z_]+)$'
            name_prefix: "spark_threadpool"
            type: gauge
          - regexp: '^(?P<key_app_id>[^.]+)\.(?P<key_instance>[^.]+)\.executor\.filesystem\.(?P<key_scheme>[^.]+)\.(?P<name_0>[a-zA-Z_]+)$'
            name_prefix: "spark_fs"
            type: gauge
          - regexp: '^(?P<key_app_id>[^.]+)\.(?P<key_instance>[^.]+)\.executor\.(?P<name_0>[a-zA-Z]+)\.count$'
            name_prefix: "spark_executor"
            type: cumulative
          - regexp: '^(?P<key_app_id>[^.]+)\.(?P<key_instance>[^.]+)\.DAGScheduler\.(?P<name_0>[a-zA-Z]+)\.(?P<name_1>[a-zA-Z]+)$'
            name_prefix: "spark_dagscheduler"
            type: gauge
          - regexp: '^(?P<key_app_id>[^.]+)\.(?P<key_instance>[^.]+)\.(?P<name_0>.+)$'
            name_prefix: "spark_unmatched"
            type: gauge
```

- **兜底规则（最后一条）必须保留**：E8 证明未匹配报文会回退 plaintext 造成指标名基数爆炸；兜底规则既能兜住又能用 `spark_unmatched_*` 监控规则遗漏。
- **端口 2003 需可配置**（沿用模板现有参数化风格），默认 2003。
- **验收**：用真实二进制校验渲染产物——
  ```bash
  /data/install_datasophon/otelcol-contrib_0.156.0/otelcol-contrib validate --config=<渲染后的 yaml>
  ```
  参照 `deploy/observability/otelcol/README.md` 既有做法。

### 并行组 D（依赖组 C 的契约，但契约已在 §3 定死，可同步开工）

#### T6 — datasophon：`/lineage/job-metrics` 端点

- **文件**：
  - `datasophon-api/src/main/java/com/datasophon/api/controller/v2/LineageV2Controller.java`（加端点）
  - 新增 service 类，复用 `com.datasophon.api.observability.OtelMetricsQueryService` 的 `queryInstant` / `queryRange`
- **改动**：按 §3.2 契约实现。要点：
  - `recordsWrittenRate` 用 Doris 侧时间窗差分，**复用 JuiceFS 看板的 counter 字段级 rate builder 模式**，不要另写一套
  - **差分必须先按 `instance` 分别算 rate 再求和**，不能先 sum 再 diff —— executor 动态增减时先 sum 会产生断崖负值
  - `appIds` 做长度上限保护（建议 ≤ 50），超限截断并记录日志
  - 该端点**不查 Gravitino**，只查 Doris；appId 由前端从图数据里带来
- **验收**：`./mvnw -pl datasophon-common,datasophon-grpc-api,datasophon-ui-v2,datasophon-api -Dskip.installnodenpm -Dskip.npm -Dtest=<新测试类> -DfailIfNoTests=false test` 通过。

#### T9 — 前端：运行态渲染

- **文件**：`LineageGraph.tsx`、`JobDetailDrawer.tsx`、`service.ts` + 测试
- **改动**：
  - `service.ts` 加 `getJobMetrics(clusterId, appIds)`
  - 图上存在 `runningAppId != null` 的任务节点时，启动 **15 秒**轮询；无运行中任务时**必须停止轮询**（组件卸载也要清理）
  - 运行中任务节点 label 切为 `✓{completeTasks} task · {recordsWritten 格式化}`；速率进 tooltip
  - 运行中任务节点的进出边加流动动画（G6 v5 边动画）
  - `JobDetailDrawer` 加速率折线图，数据来自 `queryRange`
- **验收**：`npm run lint` + `npm run test` 全绿；新增「无运行中任务时不轮询」「组件卸载清理定时器」测试用例。

---

## 6. 进度跟踪表

> **Codex：每完成一个任务立即更新本表对应行，不得批量更新。** 中断后从本表恢复。

状态取值：`NOT STARTED` / `IN PROGRESS` / `DONE` / `BLOCKED` / `REVIEW FAILED`

| 任务 | 期 | 并行组 | 依赖 | 仓库 | 状态 | 完成时间 | 证据（commit / 测试输出） |
|---|---|---|---|---|---|---|---|
| T1 `GraphJob` record 扩展 | 一 | A | — | gravitino | DONE | 2026-08-04 11:25 | `94828b65`; `./gradlew :lineage:test`: 27 tests, 0 failures/errors/skipped, BUILD SUCCESSFUL (17s); Docker IT 未执行（mac-docker-connector stopped） |
| T3 三元图数据变换 | 一 | A | — | ui-v2 | DONE | 2026-08-04 13:28 | `7769741f` + review fix `bd78629c` + 契约复用 `a4f06b2f`；G6 作业节点直接继承 `GraphJob`；lineage 10 files / 53 tests passed，`npm run lint` passed |
| T2 解析 `outputStatistics` | 一 | B | T1 | gravitino | DONE | 2026-08-04 11:34 | `179369cb`; `./gradlew :lineage:test`: 30 tests, 0 failures/errors/skipped, BUILD SUCCESSFUL (13s); Docker IT 未执行（mac-docker-connector stopped） |
| T4 任务节点渲染与交互 | 一 | B | T3 | ui-v2 | DONE | 2026-08-04 11:39 | `91880aa0` + review fix `bd78629c`; `npm run lint`: passed; `npm run test`: 73 files, 280 tests passed (8.41s) |
| — **期一验收（Claude）** | 一 | — | T1-T4 | — | DONE | 2026-08-04 11:57 | V1-V6 PASSED；standalone cluster 1 generation 38；浏览器实测任务名、三元图、Drawer `2行 / 687 B / 2026-08-01 10:55:25`、impact/depth；现场 <300 节点，折叠由自动化覆盖；Gravitino JAR `1224f010` |
| T5 快照纳入运行中 run | 二 | C | — | gravitino | DONE | 2026-08-04 12:17 | `5f80088c`; `./gradlew :lineage:test`: 36 tests, 0 failures/errors/skipped, BUILD SUCCESSFUL (13s)；修复复审 no findings；NOTE(C3) hash `780999ef`、COMPLETE SQL 未变；Docker IT 未执行（mac-docker-connector stopped） |
| T7 SPARK3 DDL metrics 配置 | 二 | C | — | datasophon | DONE | 2026-08-04 12:01 | `c51fd5ea`; JSON parse + 7 项 property 契约通过；regex 正向/排除样本通过，确认不含裸 `.*executor.*`；`git diff --check` passed |
| T8 OTELCOLLECTOR carbonreceiver | 二 | C | — | datasophon | DONE | 2026-08-04 12:03 | `d4ee6529`; `OtelcolTemplateTest`: 11 tests, 0 failures/errors/skipped, BUILD SUCCESS (8.000s)；FreeMarker 真渲染 + ddh-02 OTel Collector 0.156.0 `validate` exit 0；远端临时文件已清理 |
| T6 `/lineage/job-metrics` 端点 | 二 | D | §3.2/§3.3 契约 | datasophon | DONE | 2026-08-04 12:40 | `99b7ea1b` + 启动修复 `87f8d002`；定向 11 tests, 0 failures/errors/skipped, BUILD SUCCESS；新增最小 Spring Context 回归，独立复审 no findings；完整 manager 打包 BUILD SUCCESS；ddh-01 替换启动成功，8080/18081 监听，现场配置 hash 保持 `f1c410c0` |
| T9 前端运行态渲染 | 二 | D | §3.1/§3.2 契约 | ui-v2 | DONE | 2026-08-04 13:28 | `498c4d52` + 空值修复 `1342d147` + 格式化契约复用 `a4f06b2f`；lineage 10 files / 53 tests passed，`npm run lint` passed；真实页面标签从“✓3 task · 0行”更新为“✓5 task · 1.2亿行”，流动虚线生效；Drawer 终态显示 `1.2亿行 / 4.3 GB`，不再出现 `NaN行` |
| — **期二验收（Claude）** | 二 | — | T5-T9 | — | DONE | 2026-08-04 13:07 | V7-V12 PASSED；OTel Carbon `accepted=3151/refused=0`；真实 Spark/OpenLineage 作业使 graph generation `38→45→47`，运行态标签 `✓3→✓5 task` / `0行→1.2亿行`、流动虚线生效，Drawer 最终显示 `1.2亿行 / 4.3 GB`；ddh-01 API `8080/18081`、ddh-02 Gravitino `8090` 与 Collector `2003/8888` 均健康；NOTE(C3) 逻辑与完成态 SQL 保持不变 |

---

## 7. Codex 执行规则

1. **状态回写是硬性要求**：每个任务完成后，**立即**编辑本文档 §6 表格对应行，填入状态、完成时间（`YYYY-MM-DD HH:MM`）、证据（commit hash + 测试通过截断输出）。禁止攒批更新——中断时未回写的任务视为未完成，会被重做。
2. **一个任务一个 commit**，遵循 Conventional Commits，message 中标注任务号，例如 `feat(lineage): T1 extend GraphJob record with job name and stats fields`。
3. **测试不通过不得标记 DONE**。若卡住，状态填 `BLOCKED` 并在证据列写明阻塞原因，然后**继续做其他无依赖的任务**，不要停在原地。
4. **禁止改动 §3 契约**。确有必要时，先改 §3 并在进度表备注列写明，再动代码。
5. **禁区**：`JdbcLineageStorage.loadCurrentEdges()` 中 `NOTE(C3)` 标注的 COMPLETE 空 outputs 逻辑，本方案范围内不得改动。
6. **不要提交 `datasophon-ui-v2/config/proxy.ts`**——该文件的本机改动与本任务无关，提交前用 `git diff --cached` 核对。
7. 跨仓库：gravitino fork 在 `/Users/pro/IdeaProjects/gravitino`，与 datasophon 分别提交。

### 构建与测试命令

```bash
# gravitino fork
cd /Users/pro/IdeaProjects/gravitino && ./gradlew :lineage:test

# datasophon 后端单测（显式模块链 + 跳过前端构建）
export JH21=/Users/pro/Library/Java/JavaVirtualMachines/graalvm-jdk-21.0.7/Contents/Home
JAVA_HOME=$JH21 ./mvnw -pl datasophon-common,datasophon-grpc-api,datasophon-ui-v2,datasophon-api \
  -Dskip.installnodenpm -Dskip.npm -Dtest=<TestClass> -DfailIfNoTests=false test -s ~/.m2/setting.xml

# 前端
cd datasophon-ui-v2 && npm run lint && npm run test
```

---

## 8. Claude 审查与验证清单

Codex 交付后由 Claude 执行，**不接受"测试通过"作为唯一证据**。

### 期一验收

| # | 验证项 | 方式 |
|---|---|---|
| V1 | 三元图去重正确性 | 代码审查 + 构造「1 job 写 3 表」数据核对只生成 1 个任务节点 |
| V2 | `outputStatistics` 按 dst 匹配而非取数组第 0 项 | 代码审查 + 单测核对 |
| V3 | RUNNING/COMPLETE 两种落点都能取到统计 | 单测核对（E11） |
| V4 | `raw_event` 未被拉进主 JOIN | 审查 SQL，确认无 `MEDIUMTEXT` 字段进大结果集 |
| V5 | 折叠/depth/impact 逻辑未被破坏 | 回归现有测试 + 浏览器实测 |
| V6 | 浏览器实机验证 | ego-browser 打开 L3 血缘页，用沙箱既有历史数据核对任务节点与统计值 |

### 期二验收

| # | 验证项 | 方式 |
|---|---|---|
| V7 | `otelcol.ftl` 渲染产物能通过真实二进制 `validate` | 见 T8 验收命令 |
| V8 | 兜底规则存在且 `spark_unmatched_*` 可用于发现遗漏 | 配置审查 + 沙箱实跑观察该指标数量 |
| V9 | SPARK3 `regex` 不含裸子串匹配 | 配置审查（E4） |
| V10 | rate 计算先按 instance 算再求和 | 代码审查 SQL |
| V11 | 无运行中任务时前端停止轮询 | 代码审查 + 测试用例 |
| V12 | 端到端实机 | ddh-02 手工配置 Spark（D11），提交真实作业，观察任务节点 label 实时变化 |

### 已知不做（明确排除，避免范围蔓延）

- YARN 模式适配（阶段 B 立项后另行验证 `key_instance` 取值）
- task 总数分母（D9 已决策不做，需拉 Spark UI REST API，另行评估）
- Flink/DS 引擎的同类能力（本方案只覆盖 Spark）
- 历史 `spark_unmatched_*` 数据的清理策略

---

## 9. 沙箱资产

| 路径（ddh-02 `192.168.10.132`） | 内容 | 处置建议 |
|---|---|---|
| `/tmp/carbon-test.yaml` | **已实测通过的 carbonreceiver 配置**，T8 的直接蓝本 | 保留 |
| `/data/spark-sample/rate-probe2/graphite-raw.txt` | 13622 行真实 Graphite 报文 | 保留（regex 调试输入） |
| `/data/spark-sample/rate-probe{,2,3}/` 的 parquet 与 warehouse | 实验数据，约 1GB+ | 可删（需人工确认） |

实验全程 OpenLineage 走 `file` transport，**Gravitino 血缘库未被污染**；生产 collector 未受影响。
