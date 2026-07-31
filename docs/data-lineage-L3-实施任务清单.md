# 血缘 L3 实施任务清单 —— 集群维度 + 查询端点 + 前端表级血缘图

> **本文是本轮的唯一进度载体。** 每完成一个任务，**必须**回来更新 §2 进度表对应行
> （状态 + 验证结论 + commit）。任务可中断：任何时候接手，先读 §2 表格确定断点，
> 再读该任务在 §4/§5 的定义与验收标准。
>
> 立项日期：2026-07-31 · 分支 `feat/data-lineage-l1`（基于 `main` 的 `0b80a856`，未 push）

---

## 1. 本轮范围与既定决策

L1 已交付摄入 + 内存图 + 查询 API 骨架（详见 `data-lineage-L1-交接-2026-07-30.md`）。
本轮（L3）做两件事：**把血缘真正做成集群维度的**，**交付前端表级血缘图**。

L2（Spark/Gravitino 采集侧）与本轮并行，互不阻塞。

### 1.1 决策记录（grilling 定稿，不再重议）

| # | 决策点 | 结论 |
|---|---|---|
| D1 | 进图入口 | 新增**表清单分页端点**（非搜索端点） |
| D2 | 清单数据源 | **读内存快照**，不为 `last_seen` 扩 `NodeMeta` |
| D3 | 路由作用域 | **集群作用域** `/cluster/:clusterId/lineage`，后端真加 cluster 维度 |
| D4 | 节点建模 | **按集群彻底隔离**：`t_ddh_lineage_node` 加 `cluster_id`，唯一键改 `(cluster_id, canonical_name)` |
| D5 | 运行时分片 | **完全分片**：Holder → `Map<clusterId, Snapshot>`；generation 每集群一行；Coordinator 按集群管脏标记 |
| D6 | 租约粒度 | **保持全局单租约**，锁名与 `requireOwner()` 签名均不变 |
| D7 | 页面形态 | **两个路由**：清单页 + 图详情页；图页内置搜索框可直接换根 |
| D8 | 边上作业 | **展示**，新增 `/v2/lineage/job/{id}` 端点 |
| D9 | 折叠展开 | **可点击 `+N` 节点** → `?expand=token` 增量 merge；**409 → 提示并自动重拉根查询** |
| D10 | 新鲜度表达 | **顶部 Alert 条 + 重建按钮**；`lastRebuildError` 非空升 error 色 |
| D11 | dw_layer | **本轮补分层推断规则**（表名/库名前缀，可配） |

### 1.2 顺带修复的两个 L1 既有缺陷

| 缺陷 | 现象 | 归属任务 |
|---|---|---|
| `dw_layer` 恒为 `NULL` | `CanonicalNameResolver.Default` 两个分支都硬传 `null`，导致 `layerDistance()` 恒返回 `Integer.MAX_VALUE`，**T5 分层 BFS 完全失效**，退化成纯度数排序 | B3 |
| `SourceFreshness` 硬编码 | `LineageV2Controller` 恒返回 `(null, "UNKNOWN")`，前端拿不到"最后收到血缘事件的时间"，而 D10 的 Alert 条需要它 | B8 |

### 1.3 明确不做（边界）

- 列级血缘（本轮只做表级）
- 每集群独立租约（D6 已定：全局单租约）
- 存量数据回填脚本（`2.2.5` 未进 main，DDL 原地改 + 沙箱库重建）
- 新建 `2.2.6` 迁移目录（同上）
- `t_ddh_lineage_edge` 加 `cluster_id`（`src/dst_node_id` 已集群隔离，边天然隔离）
- `npm run openapi` 生成前端 client（照 `ObservabilityCollector/service.ts` 先例手写）

---

## 2. 进度跟踪表 ⟵ **每完成一项必须更新**

状态取值：`⬜ 未开始` / `🔄 进行中` / `✅ 已完成并验证` / `⚠️ 完成但有遗留` / `⛔ 阻塞`

### 批 1 —— 后端

| 任务 | 内容 | 状态 | 验证结论 | commit |
|---|---|---|---|---|
| B1 | DDL 集群维度改造（原地改 2.2.5） | ✅ | `LineageDdlContractTest` 2/2 绿。node 唯一键 → `uk_lineage_node_identity(cluster_id, canonical_name)`；generation 主键 → `cluster_id`，CHECK 与种子行已删。**顺带定位并解决了 L1 遗留的「测试跑不起来」问题，见 §3.1** | 待提交 |
| B2 | 写路径加集群维度（`LineageIngestService`） | 🔄 | 主代码完成：upsert 带 cluster_id、回查加集群条件（P1）、generation 改按集群 upsert、`LineageStructureChangedEvent` 加 clusterId。**测试未更新，无验证证据** | 待提交 |
| B3 | 分层推断 `DwLayerInferrer`（修缺陷 1） | 🔄 | 主代码完成：新增 `DwLayerInferrer`（6 条前缀规则，表名优先、库名兜底），接入 `CanonicalNameResolver.Default` 两个分支；顺带给 `LAYER_RANK` 补 `DIM`（否则推断出的 DIM 仍是 `MAX_VALUE`，等于白推）。**测试未更新** | 待提交 |
| B4 | `NodeMeta` 加 clusterId + Loader 按集群加载 | 🔄 | 主代码完成：`NodeMeta` 加 `clusterId`（校验 > 0）；`load(clusterId)`；节点 SQL 加集群条件；**边 SQL 改为 JOIN `t_ddh_data_job` 按 `j.cluster_id` 过滤**；generation 行缺失视为 0。**测试未更新** | 待提交 |
| B5 | `LineageGraphSnapshotHolder` 分片 | 🔄 | 主代码完成：`ConcurrentHashMap<Long, Snapshot>` + `compute` 单键原子发布（去掉了原先的全局 `synchronized`，避免跨集群串行）。**测试未更新** | 待提交 |
| B6 | `LineageRebuildCoordinator` 分片 | 🔄 | 主代码完成：`pending` → `dirtyClusters` 集合，`inFlight` 保持全局单个，drain 收敛/yield 逻辑保留；`lastRebuildErrors` 按集群；`SnapshotLoader` 增 `knownClusterIds()`（不再是函数式接口，故构造器与 Bean 装配零改动）。**行为变更见 §7.1**。**测试未更新** | 待提交 |
| B7 | `LineageGenerationReader` 按集群 | 🔄 | 主代码完成：`readCurrentGeneration(clusterId)`，行缺失返回 0（种子行已删，缺失是正常状态）。**测试未更新** | 待提交 |
| B8 | 查询端点集群化 + 2 个新端点 + SourceFreshness（修缺陷 2） | 🔄 | 主代码完成：6 个端点加 `clusterId`；新增 `/lineage/tables`、`/lineage/job/{id}`（集群作为查询条件，不泄露他集群作业存在性）；新增 `LineageJobDetailReader`；SourceFreshness 接真值（OK/LAGGING/NO_DATA/UNKNOWN）。**测试未更新** | 待提交 |
| B9 | `LineageGraphQuery` 表清单查询 | 🔄 | 主代码完成：`list(...)` 遍历快照做过滤/排序/分页，固定按 `canonicalName` 升序（`ImmutableMap` 迭代序非契约），`MAX_PAGE_SIZE=200` 截断。**测试未更新** | 待提交 |
| B10 | 批 1 测试更新 + 全量回归 + `spotless:apply` | 🔄 | **当前断点在此**。主代码 `clean compile` 全绿、`spotless` 干净；`clean test-compile` 暴露 10 个测试文件待更新，清单见 §7.2 | — |

### 批 2 —— 前端

| 任务 | 内容 | 状态 | 验证结论 | commit |
|---|---|---|---|---|
| F1 | `service.ts` + 类型定义 | ⬜ | | |
| F2 | 表清单页（ProTable） | ⬜ | | |
| F3 | 血缘图页骨架（G6 v5 + antv-dagre） | ⬜ | | |
| F4 | 折叠节点展开 + 409 恢复 | ⬜ | | |
| F5 | 边点击 → 作业详情 Drawer | ⬜ | | |
| F6 | stale Alert 条 + 手动重建 | ⬜ | | |
| F7 | impact 影响分析模式 | ⬜ | | |
| F8 | overview 分层概览 | ⬜ | | |
| F9 | 路由 + 菜单 + i18n 三语 | ⬜ | | |
| F10 | 批 2 `npm run lint` + `npm run test` | ⬜ | | |

---

## 3. 全局踩坑点（改动前必读）

| # | 坑 | 说明 |
|---|---|---|
| P1 | **回查 node id 必须带 cluster_id** | `upsertNodes` 末尾的 `SELECT id ... WHERE canonical_name = ?` 是 L1 修 `GeneratedKeyHolder` 缺陷的产物。加集群维度后**必须**改成 `WHERE cluster_id = ? AND canonical_name = ?`，否则跨集群同名表静默串号 |
| P2 | **锁顺序不可调换** | `LineageIngestService:194` 注释锁定：`node → edge/definition → generation`。generation 改按集群行后顺序不变 |
| P3 | **`GeneratedKeyHolder` 不能用于 `ON DUPLICATE KEY UPDATE`** | L1 已踩过，改 upsert 时不要"顺手优化"回去 |
| P4 | **`MysqlSnapshotLoader` 事务契约** | 无事务注解、不自取连接；必须在调用方的同一个只读 REPEATABLE READ 事务、同一连接内完成 generation + 节点 + 边分页读取 |
| P5 | **禁用 `Graphs.transitiveClosure()`** | O(V·E) + 超级节点，L1 纪律 ③，查询侧一律有界分层 BFS |
| P6 | **写侧不得读内存图** | L1 纪律 ②：`snapshotHolder.get*()` 在 ingest 链路必须零命中 |
| P7 | **`@SpringBootTest` 抢 gRPC 18081** | 新增集成测试须加 `@DirtiesContext`，否则全量测试必现失败且报错表象像 MySQL 连接问题 |
| P8 | **`proxy.ts` 本机改动勿提交** | 工作区 `datasophon-ui-v2/config/proxy.ts` 有 `localhost:8080 → 192.168.10.131:8080` 的联调改动，与本轮无关 |
| P9 | **构建须显式指定 JDK 21** | `JAVA_HOME=/Users/pro/Library/Java/JavaVirtualMachines/graalvm-jdk-21.0.7/Contents/Home`，且带 `-s ~/.m2/setting.xml` |
| P10 | **跑 Java 测试必须绕过前端构建** | `datasophon-api` 把 `datasophon-ui-v2` 声明为 **jar 依赖**，而前端的 npm install/build 绑在 **`generate-resources`** 阶段 —— 于是任何 `test` 都会拖起完整前端构建（L1 第 4 批 Codex 两轮卡死的根因）。同时 `-pl datasophon-api` 单模块又会因 `${revision}` 未解析而报「找不到 `com.datasophon:datasophon:pom:${revision}`」。**唯一可用姿势见 §3.1** |
| P11 | **`t_ddh_lineage_event` 无 `cluster_id`** | 只有可空的 `job_id`。B8 的 SourceFreshness 必须 `JOIN t_ddh_data_job` 才能按集群过滤，且 **`job_id IS NULL` 的解析失败事件统计不到** —— 这是已知口径限制，需在响应语义中体现 |

### 3.1 本轮唯一可用的后端测试命令

```bash
JAVA_HOME=/Users/pro/Library/Java/JavaVirtualMachines/graalvm-jdk-21.0.7/Contents/Home \
  ./mvnw -pl datasophon-common,datasophon-grpc-api,datasophon-ui-v2,datasophon-api \
  -Dskip.installnodenpm -Dskip.npm \
  -Dtest=<TestClass> -DfailIfNoTests=false \
  test -s ~/.m2/setting.xml
```

- 显式列四个模块（**不能用 `-am`**，会拉全量前端构建；**不能只写 `datasophon-api`**，`${revision}` 解析不了）
- `-Dskip.installnodenpm -Dskip.npm` 是 frontend-maven-plugin 内置 skip 属性
- 跑全量去掉 `-Dtest` / `-DfailIfNoTests` 即可
- 实测：单个测试类端到端 ~27s

---

## 4. 批 1 任务定义（后端）

### B1 · DDL 集群维度改造

**文件**：`datasophon-api/src/main/resources/db/migration/2.2.5/V2.2.5__DDL.sql`（**原地改**）

1. `t_ddh_lineage_node` 增加 `cluster_id INT NOT NULL`（紧跟 `id` 之后），
   唯一键 `uk_lineage_node_canonical_name` 改为 `uk_lineage_node_identity (cluster_id, canonical_name)`。
2. `t_ddh_lineage_generation` 改为每集群一行：
   - 主键 `id TINYINT` → `cluster_id INT`
   - 删除 `CHECK (chk_lineage_generation_singleton)`
   - 删除末尾的 `INSERT IGNORE ... VALUES (1, 0)` 种子行（改由写路径按需 upsert）
3. `t_ddh_lineage_edge` **不动**。

**验收**：
- `LineageDdlContractTest` 同步更新并通过（它锁 DDL 契约，不改必红）
- 沙箱库 drop 后重建，`SHOW CREATE TABLE` 三张表与脚本一致

---

### B2 · 写路径加集群维度

**文件**：`LineageIngestService.java`

1. `upsertNodes(...)` 签名加 `long clusterId`；INSERT 列表加 `cluster_id`。
2. **P1**：回查改 `SELECT id FROM t_ddh_lineage_node WHERE cluster_id = ? AND canonical_name = ?`。
3. generation bump（第 213 行）改为按集群 upsert：
   ```sql
   INSERT INTO t_ddh_lineage_generation (cluster_id, generation) VALUES (?, 1)
   ON DUPLICATE KEY UPDATE generation = generation + 1
   ```
   （种子行已删，必须能自建行）
4. **P2**：锁顺序保持 `node → edge/definition → generation`。

**验收**：
- `LineageIngestMysqlTest` 新增**跨集群同名表**用例：同一 `canonical_name` 用 clusterId=1/2 各 ingest 一次，
  断言产生**两个不同 node id**，且各自 generation 独立自增
- 既有并发/死锁重试用例（`LineageDeadlockRetryMysqlTest`）仍绿

---

### B3 · 分层推断（修缺陷 1）

**新文件**：`DwLayerInferrer.java`；**改**：`CanonicalNameResolver.java`

- 按 `tableName` 前缀推断，未命中再试 `databaseName`，都不中返回 `null`。
- 默认规则（可由 `datasophon.lineage.dw-layer-rules` 覆盖）：
  `ods_→ODS` / `dwd_→DWD` / `dws_→DWS` / `dim_→DIM` / `ads_→ADS` / `tmp_|temp_→TMP`
- `CanonicalNameResolver.Default` 的两个分支（`resolveJdbcStyle` 第 91 行、路径式第 76 行）
  把当前硬传的 `null` 换成推断结果。

**验收**：
- 单测覆盖 6 条规则命中 + 大小写不敏感 + 未命中返回 `null`
- **回归断言**：构造带分层的图，验证 `LineageGraphQuery` 的 `layerDistance()` 不再恒为
  `Integer.MAX_VALUE`（即分层 BFS 真的生效了）—— 这是缺陷 1 的修复证据

---

### B4 · NodeMeta 加 clusterId + Loader 按集群加载

**文件**：`NodeMeta.java`、`MysqlSnapshotLoader.java`

1. `NodeMeta` record 加 `long clusterId`（**不加** `lastSeen`，D2 已定）。
2. `SnapshotLoader.load()` → `load(long clusterId)`。
3. 节点 SQL 加 `WHERE cluster_id = ?`。
4. **边 SQL 需 join 过滤集群**（edge 表无 cluster_id）：
   ```sql
   ... FROM t_ddh_lineage_edge e
   JOIN t_ddh_data_job j ON e.job_id = j.id AND j.cluster_id = ?
   WHERE e.is_current = 1 ...
   ```
5. generation 读取改 `WHERE cluster_id = ?`；行不存在时视为 `0`（尚无事件的集群）。
6. **P4**：事务契约不变。

**验收**：
- 真实 MySQL 灌入双集群数据，`load(1)` 结果**不含**集群 2 的任何节点与边
- `LineageSnapshotIsolationMysqlTest` 参数化跑双集群，`REPEATABLE_READ` 下仍一致

---

### B5 · Holder 分片

**文件**：`LineageGraphSnapshotHolder.java`

- `volatile LineageGraphSnapshot published` → `ConcurrentHashMap<Long, LineageGraphSnapshot>`
- `getForQuery(long clusterId)` / `currentGeneration(long clusterId)` / `publishIfNotOlder(long clusterId, next)`
- `publishIfNotOlder` 的 `synchronized` 改为按集群加锁（`compute` 原子更新即可，避免全局串行）

**验收**：单测覆盖「集群 A 发布不影响集群 B」「同集群旧 generation 被拒绝发布」

---

### B6 · Coordinator 分片 ⟵ **本轮最大风险项**

**文件**：`LineageRebuildCoordinator.java`

设计（**刻意最小化改动**，保住 L1 第 2 批验证过的 drain 收敛逻辑）：

- `AtomicBoolean pending` → `Set<Long> dirtyClusters = ConcurrentHashMap.newKeySet()`
- `AtomicBoolean inFlight` **保持全局单个**（单线程执行器 + 全局单租约 D6，无需按集群并行）
- `requestRebuild(long clusterId, Trigger)`：`dirtyClusters.add(clusterId)` + `submitDrainIfIdle()`
- `drainPending()`：每轮从 `dirtyClusters` 取出一个集群重建；
  `maxDrainRounds` / `maxDrainMillis` 收敛与 yield 逻辑**原样保留**
- `lastRebuildError` → `Map<Long, Throwable>`，`lastRebuildError(clusterId)`
- `STARTUP` / `SCHEDULED` 触发时枚举集群：
  **`SELECT DISTINCT cluster_id FROM t_ddh_data_job`**
  （只关心有作业的集群，不耦合 `t_ddh_cluster_info`）

**验收**：
- `LineageRebuildCoordinatorTest` 重写，逐条覆盖：单集群 single-flight、
  多集群轮转不互相饿死、yield 后重新投递、某集群重建失败不影响其他集群、close 后不再投递
- 原有「陈旧快照丢弃」用例按集群重写并通过

---

### B7 · GenerationReader 按集群

**文件**：`LineageGenerationReader.java`

- `readCurrentGeneration()` → `readCurrentGeneration(long clusterId)`，SQL 加 `WHERE cluster_id = ?`
- 行缺失时返回 `0`（不再抛 "generation row is missing"，因为种子行已删）

**验收**：单测覆盖行存在/不存在两条路径

---

### B8 · 查询端点集群化 + 2 个新端点 + SourceFreshness

**文件**：`LineageV2Controller.java`

1. 五个 GET 端点（`graph` / `overview` / `table/{id}` / `impact` / 新增 `tables`）统一加
   `@RequestParam long clusterId`；`readiness` 与 `rebuild` 的租约语义不变（D6）。
   `rebuild` 加 `clusterId` 以指定重建哪个分片。
2. **新增** `GET /v2/lineage/tables`：
   `clusterId, page, size, keyword?, layer?, connector?, database?`
   → `{list: NodeMeta[], total}`；读快照（D2），`keyword` 对 `canonicalName` 做
   大小写不敏感 `contains`；默认 `size=20`，上限 `200`。
3. **新增** `GET /v2/lineage/job/{id}`：查 `t_ddh_data_job`，返回
   `jobName / engine / owner / state / externalUrl / jobType / dwLayer / updateTime`。
   **必须校验 `cluster_id` 与入参一致**，否则跨集群越权读取。
4. **修缺陷 2**：`SourceFreshness` 接真值 ——
   `SELECT MAX(received_at) FROM t_ddh_lineage_event WHERE cluster_id = ?`
   （字段名以实际 DDL 为准），`status` 按距今时长给 `OK` / `LAGGING` / `NO_DATA`。

**验收**：
- `LineageV2ControllerTest` 更新：新增端点的 200/400/404 契约 + 越权读 job 返回 404
- 用**真实 Spring 上下文**（非 `@WebMvcTest`）验一次状态码透传 —— L1 踩过
  `V2ApiExceptionHandler` 吞状态码的坑（commit `86856178`）

---

### B9 · LineageGraphQuery 表清单查询

**文件**：`LineageGraphQuery.java`

- 新增 `TablePage list(snapshot, keyword, layer, connector, database, page, size)`
- 排序：`canonicalName` 升序（稳定，`ImmutableMap` 迭代序不可依赖）
- 复用既有 `layerOf()` 归一

**验收**：单测覆盖过滤组合、分页边界、空结果、超上限 size 被截断

---

### B10 · 批 1 收尾

```bash
export JH21=/Users/pro/Library/Java/JavaVirtualMachines/graalvm-jdk-21.0.7/Contents/Home
JAVA_HOME=$JH21 ./mvnw spotless:apply -s ~/.m2/setting.xml
JAVA_HOME=$JH21 ./mvnw -pl datasophon-api test -s ~/.m2/setting.xml
```

**验收**：默认组全绿；真实 MySQL 组全绿；与本轮无关的既有失败（如
`OtelMetricsQueryServiceTest`）需用 `git stash` 复现证实是既有缺陷，不得含糊带过。

---

## 5. 批 2 任务定义（前端）

目录：`datasophon-ui-v2/src/pages/Cluster/Lineage/`
（照 `ObservabilityCollector/` 的模块布局：`service.ts` / `*.tsx` / `*.test.ts(x)`）

| 任务 | 内容 | 验收 |
|---|---|---|
| F1 | `service.ts` + `lineageTypes.ts`：8 个端点的手写 client 与类型 | `service.test.ts` mock 请求，覆盖参数拼装与错误分支 |
| F2 | `index.tsx` 表清单页：ProTable，`request` 返回 `{data, success, total}`，筛选 layer/connector/database + 关键字 | 点行跳 `/cluster/:id/lineage/:nodeId`；分页 `total` 正确 |
| F3 | `LineageGraph.tsx`：G6 v5 + `antv-dagre`（`rankdir: LR`）+ `cubic-horizontal`；depth 1–5 控件、方向切换（默认 depth=2 / both）；根节点视觉强调 | 渲染真实 `GraphData`；`truncated` 时给出提示 |
| F4 | 折叠节点 `+N`（虚线样式）→ 点击调 `?expand=token` 增量 merge 并重跑布局；**409 → message 提示"血缘已更新"并自动重拉根查询** | 单测覆盖 merge 去重与 409 恢复路径 |
| F5 | 边点击 → `JobDetailDrawer`：一条边多个 job 时列表展示，`externalUrl` 可跳转 | 空 job 列表时的降级展示 |
| F6 | `stale` Alert 条：展示 `builtAt` 相对时间 + `ageSeconds`；`lastRebuildError` 非空升 error 色并展示原因；"立即重建"按钮调 `POST /rebuild` | 时间一律 `dayjs.utc().local()`（既有踩坑） |
| F7 | 影响分析模式开关：切换后调 `/impact`（仅下游）并高亮受影响节点；**503 时明确提示"快照陈旧，影响分析不可用"并引导重建** | 503 分支有单测 |
| F8 | 清单页顶部 overview 分层概览小图（复用 G6 或简单 flex 块） | dw_layer 已由 B3 补齐，验收时应能看到多个层块而非单个 UNKNOWN |
| F9 | `config/routes.ts` 加两条路由；侧边栏菜单项；i18n 三语文案 | 中/英/繁三份 locale 均无缺 key |
| F10 | 收尾 | `npm run lint`（Biome + tsc）与 `npm run test` 全绿 |

---

## 7. 批 1 实施记录

### 7.1 相对 L1 的行为变更（评审时重点看这里）

| # | 变更 | 原因 |
|---|---|---|
| C1 | 单个集群重建失败由 `break` 改为 `continue` | 分片后一个集群失败不应饿死其他集群。**注意**：成功与失败都计入 `rounds` 并检查 deadline，否则大量集群持续失败时 drain 会长跑不 yield、独占重建线程 |
| C2 | `RejectedExecutionException` 记到当前全部脏集群 | 执行器拒绝是全局性失败，无单一集群归属，但这些集群的重建确实落空了，查询侧要能看到 |
| C3 | `SnapshotLoader` 不再是 `@FunctionalInterface` | 增加 `knownClusterIds()` 承担集群枚举。换来的是协调器构造器与 Bean 装配零改动 |
| C4 | `Holder.publishIfNotOlder` 去掉 `synchronized` | 改用 `ConcurrentHashMap.compute` 单键原子更新，避免集群 A 的发布阻塞集群 B |
| C5 | generation 行缺失不再抛异常 | 种子行已删，"该集群尚无结构性事件"是正常状态，返回 0 |
| C6 | `LAYER_RANK` 增加 `DIM`（与 DWD 同级） | `DwLayerInferrer` 会产出 DIM，不在排名表里则 `layerDistance` 仍是 `MAX_VALUE`。TMP 刻意不入排名 |

### 7.2 待更新测试清单（B10 断点）

`clean test-compile` 暴露，共 10 个文件。**注意**：Maven 增量编译只看源文件时间戳，
不 `clean` 会报 "Nothing to compile - all classes are up to date" 而**静默掩盖**这些错误。

| 文件 | 主要改动点 |
|---|---|
| `LineageRebuildCoordinatorTest` | 最大头。`SnapshotLoader` lambda → 匿名类（含 `knownClusterIds`）；`requestRebuild`/`lastRebuildError`/`publishIfNotOlder`/`getForQuery` 全部加 clusterId；**新增多集群轮转、单集群失败不影响其他集群两组用例** |
| `LineageV2ControllerTest` | `NodeMeta` 构造器加 clusterId；控制器构造器加 `jobDetailReader` + `sourceLaggingThresholdSeconds`；`SnapshotLoader` 匿名类；**新增 `/tables`、`/job/{id}` 契约与跨集群越权 404 用例** |
| `LineageGraphQueryTest` | `NodeMeta` 构造器；**新增 `list()` 过滤/分页/截断用例** |
| `LineageGraphSnapshotTest` | `NodeMeta` 构造器 |
| `LineageGenerationReaderTest` | `readCurrentGeneration(clusterId)`；**新增行缺失返回 0 用例** |
| `LineageObservabilityTest` | `load(clusterId)`、`publishIfNotOlder(clusterId, ...)` |
| `LineageQueryMysqlTest` | `load(clusterId)` |
| `LineageMasterLeaseMysqlTest` | `SnapshotLoader` 匿名类、控制器构造器 |
| `LineageRebuildBenchmark` | `NodeMeta` 构造器 |
| `LineageIngestMysqlTest` | **新增跨集群同名表用例**（B2 验收项，验证 P1 串号缺陷） |

还需新增：`DwLayerInferrerTest`（B3 验收）、`LineageGraphSnapshotHolderTest` 分片用例（B5 验收）。

---

## 6. 交付纪律

1. 批 1 全绿并经你验收后，才开批 2。
2. 每个任务完成即更新 §2 进度表（状态 + 验证结论 + commit），**不攒批更新**。
3. 无验证证据不得标 `✅`；跑不动的验证标 `⚠️` 并写明原因。
4. commit 遵循 Conventional Commits，前缀 `feat(lineage)` / `fix(lineage)` / `docs(lineage)`。
