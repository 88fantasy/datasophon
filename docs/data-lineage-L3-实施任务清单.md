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
| B2 | 写路径加集群维度（`LineageIngestService`） | ✅ | 主代码 + `LineageIngestMysqlTest` 新增 `sameCanonicalNameAcrossTwoClustersProducesTwoIndependentNodesAndGenerations`（真实 MySQL，验证 P1 串号缺陷已修复：跨集群同名表产生两个独立 node id + 独立 generation）。真实 MySQL 组 7/7 绿（详见 §9.1） | 待提交 |
| B3 | 分层推断 `DwLayerInferrer`（修缺陷 1） | ✅ | 主代码 + `DwLayerInferrerTest` 6/6（默认规则/大小写不敏感/表名优先库名兜底/自定义规则覆盖顺序/空规则过滤）+ `LineageGraphQueryTest` 新增 `layerDistancePrioritizesFrontierNodeCloserToRootLayerWhenDegreesAreTied` 回归用例，用度数相同、层级不同的两个分支证明 `layerDistance()` 真的在起作用（不再退化成度数排序） | 待提交 |
| B4 | `NodeMeta` 加 clusterId + Loader 按集群加载 | ✅ | 主代码 + `LineageQueryMysqlTest`/`LineageSnapshotIsolationMysqlTest`（真实 MySQL）验证过，批 1 已交付 | 待提交 |
| B5 | `LineageGraphSnapshotHolder` 分片 | ✅ | 主代码 + 新增 `LineageGraphSnapshotHolderTest` 4/4：集群 A 发布不影响集群 B 可见性、同集群旧 generation 被拒绝且不覆盖已发布快照 | 待提交 |
| B6 | `LineageRebuildCoordinator` 分片 | ✅ | 主代码 + `LineageRebuildCoordinatorTest` 8/8，批 1 已交付 | 待提交 |
| B7 | `LineageGenerationReader` 按集群 | ✅ | 主代码 + `LineageGenerationReaderTest` 2/2（行存在/行缺失两条路径） | 待提交 |
| B8 | 查询端点集群化 + 2 个新端点 + SourceFreshness（修缺陷 2） | ✅ | 主代码 + `LineageV2ControllerTest` 新增 `tablesPaginatesSortedByCanonicalNameAndFiltersByLayerAndKeyword`、`tablesRejectsNonPositivePageOrSize`、`jobReturnsKnownJobAndRejectsUnknownOrCrossClusterAccessWithNotFound`（含跨集群越权读 job 返回 404） | 待提交 |
| B9 | `LineageGraphQuery` 表清单查询 | ✅ | 主代码 + `LineageGraphQueryTest` 新增 5 个 `list()` 用例：过滤组合（keyword/layer/connector/database 交集）、空结果、排序分页边界、size 超上限截断（250 节点验证真实截到 200）、page/size 非法值拒绝 | 待提交 |
| B10 | 批 1 测试更新 + 全量回归 + `spotless:apply` | ✅ | **2026-08-01 两轮合并验证完成**：`Lineage*Test` 分组从 clean 重跑 **77/77 全绿**（含全部真实 MySQL 测试，本机用 Docker 起了一个 `mysql:8.0` 容器验证，见 §9.4）。批 1 遗留 3 个失败类 + B2/B3/B5/B8/B9 全部新增验收用例，本轮已悉数补齐 | 待提交 |

### 批 2 —— 前端

| 任务 | 内容 | 状态 | 验证结论 | commit |
|---|---|---|---|---|
| F1 | `service.ts` + 类型定义 | ✅ | `service.test.ts` 7/7 绿。8 端点（readiness/tables/graph/overview/table/job/impact/rebuild）；确认默认 `baseURL=/ddh/api/v2` 已覆盖 `LineageV2Controller`（`@RequestMapping("/v2")` + `datasophon.path-prefix=/api`），**不需要**像 ObservabilityCollector 那样覆盖 `legacyRequestOptions` | 待提交 |
| F2 | 表清单页（ProTable） | ✅ | `index.test.tsx` 2/2 绿。`@ant-design/pro-components` 在 vitest 下有 ESM/CJS 互操作坏问题（`exports is not defined`），本仓库既有测试**一律 mock `ProTable` 本体**、拿 `request`/`columns` props 断言（照 `AlarmManage/History.test.tsx` 先例），不是我引入的新坑；新鲜度信息复用 `/lineage/tables` 响应自带的 `snapshot`/`sourceFreshness`，不发起独立请求 | 待提交 |
| F3 | 血缘图页骨架（G6 v5 + antv-dagre） | ✅ | `LineageGraph.test.tsx` 7/7 绿（含 F4/F7 用例，三者同一文件一次交付）。抓到一个真实 bug：`fetchRoot`/`handleExpand` 的 `useCallback` deps 里带了 `t`（来自 `useIntl()`），`t` 每次渲染重新创建导致 `useEffect` 无限重新拉取；修法是把 `t` 从两处 deps 移除（只在函数体内闭包引用，不参与依赖判定），照抄 `TopologyTab.tsx` 数据拉取 `useEffect` 故意不依赖 `t` 的先例 | 待提交 |
| F4 | 折叠节点展开 + 409 恢复 | ✅ | 同上。`lineageGraphData.test.ts` 5/5 覆盖 merge 去重/占位方向；`LineageGraph.test.tsx` 覆盖展开成功与 409 自动重拉根查询两条路径 | 待提交 |
| F5 | 边点击 → 作业详情 Drawer | ✅ | `JobDetailDrawer.test.tsx` 4/4 绿。同边多 job 按 jobId 去重请求；单个 job 详情失败降级展示，不阻塞其余 job | 待提交 |
| F6 | stale Alert 条 + 手动重建 | ✅ | `FreshnessAlert.test.tsx` 3/3 绿。修正了本文档原先写的 `dayjs.utc().local()` 假设（见 §5 F6 行纠偏说明），改用后端算好的 `ageSeconds` | 待提交 |
| F7 | impact 影响分析模式 | ✅ | 同 F3。503 分支用例通过；展开折叠节点在 impact 模式下复用同一个 `/lineage/graph?expand=` 端点——确认了 token 里已编码原查询方向（impact 内部固定 downstream），所以展开不会把上游节点混进纯下游的影响分析结果 | 待提交 |
| F8 | overview 分层概览 | ✅ | `LineageOverview.test.tsx` 2/2 绿。简单 flex 条形块（非 G6），0 节点层过滤不展示 | 待提交 |
| F9 | 路由 + 菜单 + i18n | ✅ | 两条路由（清单页 + 图详情页）加进 `Cluster/Layout` 下；侧边栏菜单项插进 `bottomItems`（K8s/物理集群共用，跟随 observability-collector 之后），`Layout/index.test.tsx` 既有断言只查首/次项不受影响，8/8 相关测试文件 33/33 用例全绿；zh-CN/en-US 两语言 46 个 `pages.lineage.*` + 1 个 `menu.lineage` key 全部补齐（语言范围纠偏见 §6.1） | 待提交 |
| F10 | 批 2 `npm run lint` + `npm run test` | ✅ | `npm run lint`（Biome+tsc）、`npx antd lint ./src`、`npm run test` 三项全绿：71 个测试文件 266 个用例（本轮新增 8 个文件 39 个用例）。过程中修了 3 类真实问题：① `lineageGraphData.ts` 两处 `forEach` 回调带返回值（Biome `useIterableCallbackReturn`）；② G6 `NodeData`/`EdgeData` 要求 `Record<string,unknown>` 索引签名，给 `G6Node`/`G6Edge`/`G6NodeData`/`G6EdgeData` 补 `[key:string]:unknown`；③ `lineDash` 回调返回 `null` 与类型期望的 `undefined` 不符。`npx antd lint` 另揪出 3 处 antd 6 废弃 API（`Alert.message`→`title`、`Drawer.width`→`size`），按 `TopologyTab.tsx` 既有先例改正 | 待提交 |

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
| F6 | `stale` Alert 条：展示 `builtAt` 相对时间 + `ageSeconds`；`lastRebuildError` 非空升 error 色并展示原因；"立即重建"按钮调 `POST /rebuild` | ~~时间一律 `dayjs.utc().local()`（既有踩坑）~~ **纠偏**：`builtAt`/`updateTime`/`lastEventReceivedAt` 都是后端 `java.time.Instant`，走 Spring Boot 默认 Jackson `InstantSerializer` 输出 UTC ISO（`Z` 后缀），**不受** `application.yml` 的 `spring.jackson.time-zone: GMT+8` 影响（该配置只作用于 `java.util.Date`）；dayjs 原生按 `Date` 解析 `Z` 后缀字符串已正确换算本地时区，`.utc().local()` 是多余动作。`ObservabilityCollector/TracesTab.tsx` 等既有页面对同类 Instant 字段也是直接 `dayjs(value)`，与此结论一致。`ageSeconds` 干脆用后端算好的值格式化，不用前端相对当前时刻二次计算 |
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

### 7.3 实跑结果与剩余缺口（下一轮从这里接手）

命令见 §3.1，加 `-Dtest='Lineage*Test'`。

**已全绿（5 个类，31 个用例）**

| 测试类 | 结果 |
|---|---|
| `LineageRebuildCoordinatorTest` | **8/8** —— 本轮最大风险项（分片后的 single-flight、drain 收敛、yield 重投递、失败恢复）全部通过 |
| `LineageGraphQueryTest` | 9/9 |
| `LineageIngestComponentsTest` | 8/8 |
| `LineageGraphSnapshotTest` | 3/3 |
| `LineageDdlContractTest` | 2/2 |

**仍失败（3 个类）**

| 测试类 | 现象 | 根因 |
|---|---|---|
| `LineageGenerationReaderTest` | 1 error：`IllegalStateException: No DataSource set` | **已确认**：测试的 stub `JdbcTemplate` 只覆盖了 `queryForObject(String, Class)`，而 `readCurrentGeneration` 改用 `queryForList(String, Class, Object...)`，于是打到真实实现。改 stub 覆盖新签名即可 |
| `LineageObservabilityTest` | 1 error | 同类问题，stub 未适配新签名 |
| `LineageV2ControllerTest` | 13/15 失败（12 error + 1 failure），耗时均 0.001s ⇒ ApplicationContext 加载失败 | **未最终确认**。已适配构造器、stub JdbcTemplate、`ReflectionTestUtils` 字段名与 21 处 `clusterId` 查询参数；需单独抓 context 启动栈定位。**不要假设已修好** |

**尚未开始**：跨集群同名表用例（B2 验收）、`DwLayerInferrerTest`（B3 验收）、
Holder 分片用例（B5 验收）、`/tables` 与 `/job/{id}` 端点契约用例（B8 验收）、
真实 MySQL 组（数据准备 SQL 需补 `cluster_id`，否则 NOT NULL 插入失败）、全量回归。

---

## 6. 交付纪律

1. ~~批 1 全绿并经你验收后，才开批 2。~~ **2026-08-01 用户显式改单**："继续开发批2,完成后再合并验证"——
   批 1 遗留的 3 个失败测试类 + 待补验收用例**不阻塞批 2 开工**，批 2 完成后两批一起做最终合并验证。
   原规则 1 作废，不再执行。
2. 每个任务完成即更新 §2 进度表（状态 + 验证结论 + commit），**不攒批更新**。
3. 无验证证据不得标 `✅`；跑不动的验证标 `⚠️` 并写明原因。
4. commit 遵循 Conventional Commits，前缀 `feat(lineage)` / `fix(lineage)` / `docs(lineage)`。

### 6.1 F9 语言数量纠偏

原 §5 F9 写"中/英/繁三语"。实地核查 `datasophon-ui-v2/src/locales/` 只有 `zh-CN` / `en-US`
两个目录（`zh-TW` 只是语言切换器里的可选项，没有对应的 locale 文件，属于框架模板残留选项，
不是本仓库的实际维护范围——RustFS/ZooKeeper/Doris 等近期监控看板功能全部只交付这两语）。
F9 按仓库实际约定只做 zh-CN + en-US，不新增 zh-TW 目录。

---

## 8. 批 2 实施记录

### 8.1 交付文件清单

`datasophon-ui-v2/src/pages/Cluster/Lineage/`：`service.ts`（+test）、`lineageGraphData.ts`（+test，
纯函数：G6 数据映射 + 折叠展开合并）、`FreshnessAlert.tsx`（+test）、`LineageOverview.tsx`（+test）、
`JobDetailDrawer.tsx`（+test）、`index.tsx`（+test，表清单页）、`LineageGraph.tsx`（+test，图详情页，
F3/F4/F7 三个任务合一文件交付）。另改 `config/routes.ts`（2 条路由）、
`Cluster/Layout/index.tsx`（1 个菜单项）、`locales/{zh-CN,en-US}/menu.ts`（各 1 key）、
新增 `locales/{zh-CN,en-US}/lineage.ts`（各 39 key）并接入 `locales/{zh-CN,en-US}.ts`。

### 8.2 开工前接口核对（与 §1 决策/§5 任务定义比对，均吻合，无需改计划）

- **baseURL 确认**：`LineageV2Controller extends ApiController` 走 `AppConfiguration.configurePathMatch`
  的 `/api` 前缀 + `@RequestMapping("/v2")`，服务端最终路径 `/ddh/api/v2/lineage/**`，与
  `app.tsx` 里 `request` 的默认 `baseURL: '/ddh/api/v2'` 天然吻合 —— `service.ts` **不需要**像
  `ObservabilityCollector/service.ts` 那样覆盖 `legacyRequestOptions`。
- **错误信封确认**：`V2ApiExceptionHandler` 把非 2xx 统一转成 `ApiResponse{success:false,
  errorCode, errorMessage, showType}`，但 axios 默认对非 2xx 直接 reject，不会走
  `requestErrorConfig.ts` 里 `data.success===false` 那条 `errorThrower` 分支（那条分支只处理
  "HTTP 200 但业务失败" 的旧接口）。因此本软所有需要**自定义**处理的错误（409 展开过期、
  503 影响分析不可用）一律传 `skipErrorHandler:true` 自行 catch `error.response.status`；
  其余错误（400/404/500）放行给默认 `errorHandler`，走通用 toast，不重复处理。

### 8.3 过程中发现并修正的问题

| # | 问题 | 修正 |
|---|---|---|
| Q1 | 我在 grilling 定稿的 F6 验收标准里写了"时间一律 `dayjs.utc().local()`"，实测该模式是为 Doris 原始 JDBC 时间戳踩过的坑，**不适用于本轮**：`builtAt`/`updateTime`/`lastEventReceivedAt` 都是后端 `java.time.Instant`，Spring Boot 默认 Jackson `InstantSerializer` 输出带 `Z` 的 UTC ISO 字符串，**不受** `application.yml` 的 `spring.jackson.time-zone: GMT+8` 影响（该配置只作用于 `java.util.Date`）。dayjs 原生解析 `Z` 后缀已经正确换算本地时区 | `FreshnessAlert.tsx` 改用后端算好的 `ageSeconds` 格式化，不引入 `.utc()` |
| Q2 | **真实 bug**：`LineageGraph.tsx` 的 `fetchRoot`/`handleExpand` 两个 `useCallback` 最初把 `t`（源自 `useIntl()`）放进依赖数组；`useIntl()` 若不是每次渲染返回同一引用（本轮 mock 环境里就不是——测试用 factory 函数每次新建对象），`t` 就会每次渲染重新创建，进而让依赖它的 `useEffect` 无限重新拉取数据。写单测时被 `getGraph` 调用次数从预期 1 次暴露成 363 次直接抓到 | 把 `t` 从两处依赖数组移除（仅在函数体内闭包引用，不参与依赖判定），照抄 `TopologyTab.tsx` 数据拉取 `useEffect` 故意不依赖 `t` 的既有先例 |
| Q3 | `@ant-design/pro-components` 在 vitest 下有 ESM/CJS 互操作坏问题（`ReferenceError: exports is not defined`），直接渲染真实 `ProTable` 会整个测试文件挂掉 | 不是我引入的新坑——本仓库所有触碰 `ProTable`/`PageContainer` 的既有测试（`AlarmManage/History.test.tsx` 等）**一律 mock `@ant-design/pro-components`**，拿 `request`/`columns` props 出来断言。`index.test.tsx` 照此先例 |
| Q4 | `npx antd lint ./src` 揪出 3 处 antd 6 已废弃 API：`Alert.message`（应为 `title`）、`Drawer.width`（应为 `size`） | 全部改正；顺带发现 `TopologyTab.tsx` 早就在用新版 `title`，本该一开始就抄对 |
| Q5 | G6 v5 的 `NodeData`/`EdgeData` 类型要求 `data` 字段满足 `Record<string, unknown>` 索引签名；`lineDash` 回调期望返回 `undefined` 而非 `null` | 给自定义的 `G6Node`/`G6Edge`/`G6NodeData`/`G6EdgeData` 补 `[key: string]: unknown`；`lineDash` 三元表达式把 `null` 分支改 `undefined` |

### 8.4 折叠展开在 impact 模式下的正确性说明（F4×F7 交叉点）

`/lineage/impact` 端点本身不支持 `expand` 参数；折叠节点展开统一走 `/lineage/graph?expand=token`。
关键在于 `LineageGraphQuery.expand()` 解析 token 时用的是 token 里编码的 `Direction`（对应
`n:<id>:<up|down|both>:g<generation>` 里的 `up`/`down`/`both` 段），而不是当前 UI 的 `direction`
state。因为 `/lineage/impact` 内部固定用 `Direction.DOWNSTREAM` 发起遍历，它产生的所有
`CollapsedNode.token` 天然编码着 `down`——所以即便在"影响分析"模式下点击展开，也不会意外拉入
上游节点污染纯下游的影响分析结果。这条正确性依赖后端既有实现，前端不需要、也没有做任何额外的
方向过滤。

### 8.5 验证结论

- `npm run lint`（Biome + tsc）：0 error（8 个与本轮无关的既有 info，见 F10 行）
- `npx antd lint ./src`：0 issue
- `npm run test`：71 files / 266 cases 全绿，含本轮新增 8 个文件 39 个用例
- **未做**：浏览器实机联调（需要本地后端起一个已有真实血缘快照的集群；批 1 后端遗留的 3 个
  失败测试类与待补验收用例仍未处理，"合并验证"见 §9）

---

## 9. 合并验证（2026-08-01）

按用户指令"继续开发批2,完成后再合并验证"，批 2 完成后回头处理批 1 遗留的 3 个失败测试类，
两批一起给出最终验证结论。

### 9.0 下一轮补验收用例时顺带抓到的严重 bug：`service.ts` 没解包 `V2ResponseBodyAdvice` 信封

写 B8 的 `/tables`/`/job/{id}` 契约用例前重新核对响应体形状，发现 `V2ResponseBodyAdvice`
（`@RestControllerAdvice(basePackages = "com.datasophon.api.controller.v2")`）会把
**这个包下所有控制器的成功返回值**统一包一层 `ApiResponse{success,data,errorCode,
errorMessage,showType}`——包括控制器自己已经返回 `LineageQueryResponse{data,snapshot,
sourceFreshness}` 的端点，因此真实响应体是 `data.data` 双层嵌套。`LineageV2ControllerTest`
的既有断言（`jsonPath("$.data.data.nodes...")`、`jsonPath("$.data.snapshot...")`）其实早就
在验证这层嵌套，只是批 2 写前端时没有回头核对，凭 `LineageQueryResponse<T>` 的字面定义直接
当成了 `request()` 的返回值类型。

**实际影响**：`service.ts` 8 个函数全部只解包了一层，真实联调时每个字段都会读到
`undefined`——`TablePage.list` 是 `undefined`，`.map()` 直接崩；`snapshot`/`sourceFreshness`
全部拿不到，`FreshnessAlert` 无法渲染。这个 bug 在纯 mock 单测下**完全测不出来**：
`service.test.ts` 原来的 7 个用例只断言"调用 `request` 时传的 URL/参数对不对"，
从未断言过"函数返回值对不对"——mock 的 `request` 解析值本身就是按错误假设手写的，
错误假设和错误实现自洽，全绿但没有验证到关键契约。

**修复**：`service.ts` 新增 `ApiEnvelope<T>{success,data}` 类型 + `unwrap()` helper，
8 个函数统一 `request<ApiEnvelope<X>>(...).then(res => res.data)`，调用方（`index.tsx`/
`LineageGraph.tsx`/`FreshnessAlert.tsx`/`JobDetailDrawer.tsx`/`LineageOverview.tsx`）
**零改动**——它们本来就是按 `LineageQueryResponse<T>`/`JobDetail`/`RebuildAccepted` 在用，
现在这层解包挪到 service.ts 内部后这些类型才是真实的。`service.test.ts` 同步改成还原真实
双层信封的 mock 数据，并新增对函数**返回值**的断言（不再只测调用参数），避免同类 bug
下次又在"mock 和实现互相自洽但都错"的状态下全绿溜走。

**教训**：手写 `service.ts` 直连后端时，`ResponseBodyAdvice`/`ControllerAdvice` 这类
"运行期动态改写响应体"的机制，光看被调控制器方法的返回类型签名是看不出来的，必须去确认
有没有生效中的 advice；`LineageV2ControllerTest` 里现成的 `jsonPath` 断言其实已经把真实
形状写明白了，本该在写 `service.ts` 前先读一遍这个测试文件的断言，而不是只读控制器方法签名。

### 9.1 前端

- `npm run lint`（Biome + tsc）：0 error
- `npx antd lint ./src`：0 issue
- `npm run test`：**71 files / 266 cases 全绿**
- `npm run build`：生产构建成功，`@antv/g6` 单独分包（`node_modules__antv_g6_esm_index_*.async.js`
  222KB gzip），确认 `LineageGraph.tsx` 被正确按路由代码分割进产物，不是只在 dev 模式下能跑

### 9.2 后端 —— §7.3 三个遗留失败类，根因与修复

`LineageGenerationReaderTest` 和 `LineageObservabilityTest` 的根因和之前判断一致（stub 未跟着
`queryForObject`→`queryForList` 的签名切换），修法也一样，唯一新增内容是 `LineageObservabilityTest`
的 H2 fixture DDL 需要同步 B1 的三个 schema 变化（`t_ddh_lineage_generation` 改按 `cluster_id`
建主键、`t_ddh_lineage_node` 加 `cluster_id`、新增最小 `t_ddh_data_job` 供边查询 JOIN）。

`LineageV2ControllerTest` 的根因**在批 1 结束时没有确认**，本轮定位清楚——**不是** Application
Context 加载失败，是 3 处 `ReflectionTestUtils.setField` 遗留自 L1 时代的写法，字段类型/名称都
已被批 1 的分片改造淘汰：

| 位置 | 旧写法 | 问题 |
|---|---|---|
| `coordinatorErrorParticipatesInFreshnessAndImpactFailsClosed` | `setField(coordinator, "lastRebuildError", ex)` | `LineageRebuildCoordinator` 早已把单值 `lastRebuildError` 换成 `Map<Long, Throwable> lastRebuildErrors`（B6），字段名对不上 |
| `snapshotAgeIsTheThirdIndependentStalenessBranch` | `setField(snapshotHolder, "published", SNAPSHOT.get())` | `published` 已从单值 `LineageGraphSnapshot` 换成 `ConcurrentMap<Long, LineageGraphSnapshot>`（B5），类型不匹配 |
| `missingSnapshotReturnsServiceUnavailableInsteadOfEmptyGraph` | `setField(snapshotHolder, "published", null)` | 把整个分片 Map **永久置空**，而 `@WebMvcTest` 的 Spring 上下文在同一测试类内跨方法缓存（无 `@DirtiesContext`），这个 null 会残留到后面每一个测试的 `@BeforeEach`，导致 `resetMockService()` 里 `publishedSnapshots().clear()` 空指针——**这就是"13/15 失败、耗时均 0.001s"的真正原因**：不是 context 起不来，是某个测试把共享单例 bean 的内部状态永久破坏了，后续测试的 `@BeforeEach` 提前炸掉 |

修法统一为通过既有的 `publishedSnapshots()` / `rebuildErrors()` 辅助方法按 `CLUSTER_ID`
操作分片容器本身（`.put()` / `.remove()`），不再触达容器的物理字段。副作用之一：定位过程中
还额外揪出一个真实断言错误——`graphReturnsBoundedDataAndGenerationBasedStaleness` 断言
`sourceFreshness.status` 应为 `UNKNOWN`，但 stub 按 `resetMockService()` 设定的
"30 秒前收到事件"实际应判定为 `OK`（30s 远小于默认 1800s 滞后阈值），这是断言文本本身写错，
不是产线代码缺陷，已改成 `OK` 并加注释说明"快照 stale 与采集侧新鲜度是两件独立的事"。

### 9.3 第一轮合并验证结果（历史记录，已被 §9.4 取代为最终数字）

```
Lineage*Test 分组：Tests run: 52, Failures: 0, Errors: 0, Skipped: 0
```

批 1 遗留的 3 个失败测试类修复后的首次绿灯，当时 B2/B3/B5/B8/B9 的新增验收用例、
真实 MySQL 组的 `cluster_id` 迁移、浏览器联调都还没做，见 §9.4 的下一轮延续。

### 9.4 下一轮：补齐 B2/B3/B5/B8/B9 验收用例 + 真实 MySQL 组迁移（2026-08-01 同日下一轮）

用户说"继续下一轮"后，按 §9.3 末尾列的缺口逐条补齐。写 B8 用例时先发现了 §9.0 的
`service.ts` 信封 bug 并修复；随后依次补齐：

- **B3**：新增 `DwLayerInferrerTest`（6 用例）+ `LineageGraphQueryTest` 新增 1 个回归用例
  （层级相同度数打平会验证不出问题，特意构造"度数相同、层级不同"的两个分支，证明
  `layerDistance()` 真的在起作用而不是退化成度数排序）
- **B5**：新增 `LineageGraphSnapshotHolderTest`（4 用例，纯内存单测不需要 MySQL）
- **B9**：`LineageGraphQueryTest` 新增 5 个 `list()` 用例（过滤组合/空结果/排序分页边界/
  size 截断用 250 节点真实验证截到 200/非法参数拒绝）
- **B8**：`LineageV2ControllerTest` 新增 3 个用例覆盖 `/tables`（分页排序过滤 + 400）与
  `/job/{id}`（200/404/跨集群越权 404）
- **B2 + 真实 MySQL 组迁移**：这一项范围比原计划大得多。检查 `LineageMysqlTestSupport`
  （所有 5 个 `@Tag("mysql")` 测试类的共享基类）时发现 `clearLineageTables()` 的
  `@BeforeEach` 还在对 `t_ddh_lineage_generation` 执行 `UPDATE ... WHERE id = 1`——B1 已经
  把这张表的主键从 `id` 改成 `cluster_id` 并删掉了种子行，这条 SQL 对新 schema 会直接报错
  "Unknown column 'id'"。**这意味着批 1 的所有真实 MySQL 测试自 B1 合并后就没有真正跑通过**
  ——之前的验证记录全部止步于默认分组，从未跑过 `-DexcludedGroups=` 解锁 `mysql` 分组。
  逐个排查后，5 个测试类 + 1 个手工基准工具（`LineageRebuildBenchmark`/
  `LineageBenchmarkDataGenerator`，非自动化 `@Test`，顺手一并修了但未跑）全部有同类漂移：
  `t_ddh_lineage_node` 的 `INSERT` 缺 `cluster_id`（该列 `NOT NULL`，直接插入失败）、
  按 `canonical_name` 回查 node id 没带 `cluster_id`（P1 同款隐患）、`t_ddh_lineage_generation`
  的读写全部还在用已不存在的 `id=1`。本机用 Docker 起了一个 `mysql:8.0`（root/localmysql，
  端口 3306，容器名 `lineage-test-mysql`）把这些改动跑通验证，不是纸面审查。

**最终结果**（`clean test` 全量重跑，含真实 MySQL）：

```
Lineage*Test 分组：Tests run: 77, Failures: 0, Errors: 0, Skipped: 0
```

新增：`DwLayerInferrerTest` 6、`LineageGraphSnapshotHolderTest` 4、`LineageGraphQueryTest`
从 9→15（`list()` 5 个 + layerDistance 回归 1 个）、`LineageV2ControllerTest` 从 15→18、
`LineageIngestMysqlTest` 从 6→7（跨集群同名表用例）。5 个真实 MySQL 测试类全部跑通：
`LineageMasterLeaseMysqlTest` 1、`LineageDeadlockRetryMysqlTest` 1、`LineageQueryMysqlTest` 1、
`LineageIngestMysqlTest` 7、`LineageSnapshotIsolationMysqlTest` 2（参数化两个隔离级别）。
`spotless:apply` 干净。

**仍未做**：浏览器实机联调（需要真正跑起来 datasophon-api + datasophon-ui-v2 两个进程，
本轮验证到"后端对着真实 MySQL 全绿"为止，没有再往上搭前端联调）；与本轮无关模块的全量回归
（只跑了 `-Dtest=Lineage*Test`，未跑整个 `datasophon-api` 默认分组）；本机起的 MySQL 容器是
临时验证用途，未做持久化配置，重启会丢数据（这是预期行为，不是遗留问题）。
