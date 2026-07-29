# 血缘 L1 实施任务清单（交付 Codex）

> **来源**：`docs/data-lineage-平台级血缘架构-2026-07-29.md`（架构定稿，经 Codex 两轮对抗性审查，4×P0 + 8×P1 + 4×P2 已回写）
> **范围**：仅 L1（接收端 + 存储 + 内存图 + 查询 API）。L2-L7 不在本清单内
> **日期**：2026-07-29
> **分支建议**：`feat/data-lineage-l1`

---

## 0. 开工前必读

### 0.1 三条不可违反的纪律

| # |                                纪律                                |            违反后果            |               验证方式                |
|---|------------------------------------------------------------------|----------------------------|-----------------------------------|
| ① | **写侧不修改内存图**                                                     | 内存出现 DB 中不存在的边             | 代码中不存在 `snapshot.addEdge()` 类 API |
| ② | **写侧不读取内存图** —— 所有 `snapshotHolder.get*()` 必须在 `@GetMapping` 链路内 | 陈旧缓存参与权威写入 → 重复版本、旧事件回滚新结构 | 静态检查调用点（T7-3）                     |
| ③ | **禁用 `Graphs.transitiveClosure()`**                              | O(V·E) + 超级节点上结果集爆炸 → 进程挂死 | 代码检索确认零调用                         |

### 0.2 被 L0 阻塞的部分（**不要猜，等实测**）

L0 现场核查尚未执行。以下三处**只写骨架 + 留 TODO，不要臆造实现**：

|         被阻塞项          |    依赖     |                         说明                         |
|-----------------------|-----------|----------------------------------------------------|
| `canonical_name` 转换函数 | **L0 #2** | Gravitino 转换后 dataset 的 namespace/name 确切拼写，必须实机采样 |
| `watermark` 取值来源      | **L0 #8** | 上游能否提供可靠单调序号；拿不到则降级为 `received_at` 并记录妥协后果         |
| structural hash 的归一规则 | **L0 #7** | 动态表名 / 临时表 / 日期分区的实际形态                             |

**处理方式**：定义接口 + 默认实现 + `// TODO(L0-#N)` 注释，用可配置策略类隔离，L0 出结论后只改实现不改调用方。

### 0.3 环境

```bash
export JH21=/Users/pro/Library/Java/JavaVirtualMachines/graalvm-jdk-21.0.7/Contents/Home
JAVA_HOME=$JH21 ./mvnw -pl datasophon-api -am test -s ~/.m2/setting.xml
JAVA_HOME=$JH21 ./mvnw spotless:apply -s ~/.m2/setting.xml
```

**新增 `@SpringBootTest` 必须加 `@DirtiesContext`** —— 否则两个上下文抢 gRPC 18081，全量测试必挂，且报错表象伪装成 MySQL 连接失败。优先用 `@WebMvcTest` + mock。

---

## 1. 任务依赖图

```text
T1 (DDL) ──┬─► T2 (内存图+Coordinator) ──┬─► T4 (查询 API) ──► T5 (BFS+折叠)
           │                             │
           └─► T3 (写路径) ──────────────┘
T0 (基准脚手架) ── 独立，但结果影响 T2 的参数
T6 (单例租约) ──── 独立
T7 (测试) ─────── 贯穿，随各任务交付
T8 (埋点) ─────── 贯穿
```

**建议交付批次**：

|  批次   |      任务      |                                       理由                                        |
|-------|--------------|---------------------------------------------------------------------------------|
| 第 1 批 | T1 · T2 · T0 | 纯结构 + 纯内存逻辑，不依赖 L0，可完整单测。**已交付，但三轮自审提出返工项**：T1 见「唯一键的坑 ①」，T2 见 §2.1b 的 R1/R2/R3 |
| 第 2 批 | T3 · T6      | 写路径（L0 阻塞项留 TODO）+ 租约。**T3 须配合 F1 改用 `ON DUPLICATE KEY UPDATE` 抢占身份行**          |
| 第 3 批 | T4 · T5      | 查询 API + BFS                                                                    |
| 第 4 批 | T7 · T8      | 补齐验收与埋点                                                                         |

---

## T0 — 基准验证脚手架

> **架构文档 §3.4.8。这是 L1 的第一件事，先于功能代码。** §3.4.2 / §3.4.3 的所有性能数字都是粗估，未经验证不得作为容量承诺。

### 产出

- `datasophon-api/src/test/java/com/datasophon/api/lineage/bench/LineageRebuildBenchmark.java`
- 造数脚本：生成 15000 节点 + 2 万边的真实形态数据（`canonical_name` 用真实长度，含并行边、自环、超级节点）

### 要求

|  项  |                              要求                              |
|-----|--------------------------------------------------------------|
| 环境  | JDK 21、生产堆配置、**远程 MySQL**（不能用本机同进程）                          |
| 指标  | 重建耗时 **p50 / p95 / p99**，不是单次采样                              |
| 分段  | DB read · 结果映射 · 建图 · `copyOf` · `hasCycle` · publish 六段独立计时 |
| 内存  | JOL 测 retained heap；**JFR 测 allocation 与 promotion**         |
| 锁   | `lock_wait` p95/p99 · deadlock count · `history_list_length` |
| SQL | 对真实 SELECT 跑 `EXPLAIN ANALYZE`，**据此反推索引列**                   |

### 验收

产出一份基准报告（`docs/monitoring/data-lineage-benchmark.md`），包含上述全部实测数字。**若 p99 > 2s，需回到架构文档 §3.4.8 走"回头触发点"流程，而不是直接继续开发。**

> **特别注意**：架构文档 §3.4.3 明确修正过一个错误结论 —— 快照存活 3 分钟必然跨越多次 young GC 并晋升 old gen。基准必须用 JFR 验证实际 promotion 量，不要复述"对象都死在 young gen"。

---

## T1 — DDL 迁移 2.2.5

> **架构文档 §3.1**

### 产出

`datasophon-api/src/main/resources/db/migration/2.2.5/V2.2.5__DDL.sql`（照抄 `2.2.4/` 的文件命名与格式；`DatabaseMigration` 扫目录发现版本，**无需注册**）

### 表清单（7 张）

|              表              |                                                    要点                                                    |
|-----------------------------|----------------------------------------------------------------------------------------------------------|
| `t_ddh_data_job`            | + `current_structural_hash CHAR(64)`、`current_watermark BIGINT`；**唯一键 `(cluster_id, engine, job_name)`** |
| `t_ddh_data_job_definition` | **唯一键 `(job_id, version)`** —— ⚠️ **不能**用 `(job_id, content_hash)`                                       |
| `t_ddh_lineage_node`        | `canonical_name` 唯一键                                                                                     |
| `t_ddh_lineage_edge`        | + `is_current TINYINT NOT NULL DEFAULT 1`                                                                |
| `t_ddh_lineage_parse_log`   | 解析旁路，失败不阻断                                                                                               |
| `t_ddh_lineage_event`       | **唯一键 `(producer, run_id, event_type)`** —— 投递幂等                                                         |
| `t_ddh_lineage_generation`  | 单行计数器，结构写事务内 +1                                                                                          |

### ⚠️ 唯一键的坑 ①：作业身份必须 UNIQUE（三轮自审 F1，**T1 返工项**）

`t_ddh_data_job` 的 `(cluster_id, engine, job_name)` **必须是 `UNIQUE KEY`，不是普通 `KEY`**。首版清单只列了字段未声明唯一性，实现照字面建成了普通索引。

后果在**写侧**：T3 靠 `SELECT ... FOR UPDATE` 锁 job 行做条件写入，而**行不存在时 `FOR UPDATE` 锁不住任何东西**。两个并发首次事件各自 `INSERT`，同一逻辑作业得到两个 `job_id`，两条 `is_current` 边链并存。

**T3 写路径必须配合改成**：先 `INSERT ... ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)` 抢占身份行，拿到 `job_id` 后再 `FOR UPDATE`。

### ⚠️ 唯一键的坑 ②：定义历史用 version 不用 hash（Codex 二轮 P0-1）

`t_ddh_data_job_definition` 的唯一键**必须**是 `(job_id, version)`。用 `(job_id, content_hash)` 会让 **A→B→A 的合法发布回滚直接撞唯一键** —— 结构改到 B 再改回 A 是日常操作，不是异常。投递幂等由 `t_ddh_lineage_event` 承担，**不要把两件事压在一个约束上**。

### 索引

```sql
KEY idx_edge_current (is_current, src_node_id, dst_node_id)   -- 起点，非结论
```

此索引**不覆盖**建图查询的全部所需列（还要 `job_id`、`definition_version`、edge 主键）。**不要在注释里称它为覆盖索引**，最终列组合由 T0 的 `EXPLAIN ANALYZE` 决定。

### 验收

迁移可重复执行不报错；`DatabaseMigration` 能识别 2.2.5；7 张表结构与 §3.1 一致。

---

## T2 — 内存图与重建协调器

> **架构文档 §3.4.2 / §3.4.3 / §3.4.5。本任务是 L1 的核心，纯内存逻辑，可完整单测。**

### 产出

```text
datasophon-api/src/main/java/com/datasophon/api/lineage/
├── LineageGraphSnapshot.java          // 不可变快照
├── LineageGraphSnapshotHolder.java    // volatile 引用持有者
├── LineageRebuildCoordinator.java     // single-flight + 代际单调发布
├── EdgeValue.java / JobRef.java / NodeMeta.java
└── LineageSnapshotMeta.java           // generation/builtAt/stale/...
```

### 2.1 图结构

```java
ValueGraph<Long, EdgeValue>   // 节点 = t_ddh_lineage_node.id（Long，不是 canonical_name）
```

|                          要求                          |                                 原因                                  |
|------------------------------------------------------|---------------------------------------------------------------------|
| `ValueGraphBuilder.directed().allowsSelfLoops(true)` | `INSERT OVERWRITE t SELECT FROM t` 是真实自环，默认 builder 抛异常             |
| `EdgeValue` 持 `List<JobRef>`                         | **逻辑边模型**：同一 `(src,dst)` 可由多个作业产生，图上仍是一条边                           |
| 节点用 `Long` 不用 `String`                               | `canonical_name` 是长串，做 HashMap key 每次全串比较                           |
| `nodeMeta` 放 `ImmutableMap<Long, NodeMeta>` 侧表       | BFS 结束后回填名称                                                         |
| 环检测**拆两个指标**（见下）                                     | 直接用 `Graphs.hasCycle()` 会因自环恒为 `true`，告警失效；记 `parse_log` 但**不阻断发布** |
| **禁用** `Graphs.transitiveClosure()`                  | 见纪律 ③                                                               |
| **不要用** `Traverser`                                  | 不带深度信息，深度截断与折叠都要在 BFS 循环内控制                                         |
| **不要塞进 `CacheUtils`**                                | 那是 Hutool `newLRUCache(4096)` 全局共享单例，快照会被业务键挤掉                      |

### 2.1b T2 返工项（三轮自审，**第 1 批已交付代码需修改**）

#### R1 — 环检测拆两个指标（F2）

实测（GraalVM 21.0.7 + guava 31.1-jre，本项目版本）：`directed + allowsSelfLoops` 的图只要有一条 `1→1`，`Graphs.hasCycle` 即为 `true`；纯 DAG 为 `false`。

而本清单上面刚强制要求 `allowsSelfLoops(true)`，理由是"`INSERT OVERWRITE t SELECT FROM t` 真实存在"。**合起来就是任何真实集群 `hasCycle` 恒为 `true`** —— 告警永远亮着，运维学会无视，真正危险的跨作业环（A→B→A）就此淹没。

|          指标          |               算法                |           用途           |
|----------------------|---------------------------------|------------------------|
| `selfLoopCount`      | `edges()` 中 `nodeU == nodeV` 计数 | **信息量**，不告警            |
| `hasNonTrivialCycle` | **剥掉自环后**再 `Graphs.hasCycle()`  | **告警条件**，正常集群恒 `false` |

```java
MutableGraph<Long> stripped = GraphBuilder.directed().allowsSelfLoops(false).build();
graph.nodes().forEach(stripped::addNode);
graph.edges().stream().filter(e -> !e.nodeU().equals(e.nodeV()))
     .forEach(e -> stripped.putEdge(e.nodeU(), e.nodeV()));
boolean hasNonTrivialCycle = Graphs.hasCycle(stripped);
```

`LineageGraphSnapshot.copyOf()` 与 `LineageSnapshotMeta` 的 `hasCycle` 字段按此拆分。BFS 侧不受影响（靠 `visited` 终止）。

#### R2 — 删掉 `LineageSnapshotMeta` 的三个恒空字段（F3）

`stale` / `degraded` / `lastRebuildError` 的唯一构造入口 `fresh()` 恒传 `false / false / null`，Coordinator 也从不构造别的取值。

更危险的是：重建失败时 `drainPending` 只更新 Coordinator 自己的 `lastRebuildError`，**已发布快照的 `meta.stale()` 仍是 `false`** —— T4 若读它，会拿到"自称新鲜"的旧图，直接违反两层新鲜度契约。

- 三个字段**从 record 中删除**
- `stale` 由 T4 查询侧**每次现算**：快照 `generation` / `builtAt` + `coordinator.lastRebuildError()` + 当次 `observedDbGeneration`
- `targetGeneration` **保留**（是事实记录，非派生量）

> 一般规律：**只要一个字段在所有现有写入路径下都是同一个常量，它就不是字段，是注释。** 注释不会被误当成判据。

#### R3 — `publishIfNewer` 更名为 `publishIfNotOlder`

实现语义是 `next.generation() < current` 才拒绝（相等接受），**这是正确的**：3 分钟定时重建在无变更期读到的 generation 不变，若严格 `<` 就永远刷不新 `builtAt`，快照会被 age 判据误判为陈旧。只是方法名与行为不符，改名即可，**不要改逻辑**。

#### R4 — `LineageDdlContractTest` 不得断言"全仓库最新版本"（三轮自审 F4）

```java
// 现状（datasophon-api/src/test/java/com/datasophon/api/lineage/LineageDdlContractTest.java:47-48）
Migration latest = migrations.last();
assertThat(latest.getVersion()).isEqualTo("2.2.5");   // ← 断言的是"全仓库最新迁移 = 2.2.5"
```

**任何人后续新增 `2.2.6/` 迁移，这个血缘测试就会变红**，而排查的人完全想不到跟血缘有关。血缘功能不应该给无关模块埋下这种绊子。

改成按版本号**查找**而非取 `last()`，断言强度不变、耦合消失：

```java
Migration target = migrations.stream()
        .filter(m -> "2.2.5".equals(m.getVersion()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("migration 2.2.5 not discovered"));
assertThat(target.getUpgradeDDLFile().getFilename()).isEqualTo("V2.2.5__DDL.sql");
assertThat(target.getUpgradeDMLFile().getFilename()).isEqualTo("V2.2.5__DML.sql");
```

> 这条与 F1 是**同一个模式的两个方向**：F1 是"规格没说的地方，实现和测试一起默认了同一件事"；F4 是"测试断言了一个它其实不关心的全局事实"。两者都表现为**测试与它真正要保护的东西之间存在多余耦合**。
>
> 一般规律：**一个测试应当只在它保护的行为被破坏时失败。** 若它还会因无关变更而失败，那多出来的失败条件就是负债——它训练团队忽略红灯。

### 2.2 Coordinator（**最容易写错的部分**）

```java
/** 独立单线程执行器 —— 重建绝不占用 Tomcat 线程或 @Scheduled 线程 */
private final ExecutorService rebuildExecutor =
        Executors.newSingleThreadExecutor(r -> new Thread(r, "lineage-rebuild"));

/** 三个触发源的唯一入口：只置脏 + 投递，立即返回 */
public void requestRebuild(Trigger trigger) {
    pending.set(true);
    if (inFlight.compareAndSet(false, true)) {
        rebuildExecutor.execute(this::drainPending);
    }
}

private void drainPending() {
    try {
        int rounds = 0;
        long deadline = clock.millis() + MAX_DRAIN_MILLIS;
        while (pending.compareAndSet(true, false)) {
            try {
                doRebuild();
            } catch (Exception e) {
                lastRebuildError = e;          // 不吞异常、不丢 pending
                metrics.rebuildFailed(e);
                break;
            }
            if (++rounds >= MAX_DRAIN_ROUNDS || clock.millis() > deadline) {
                pending.set(true);             // 让出线程，重新投递
                break;
            }
        }
    } finally {
        inFlight.set(false);
        if (pending.get() && inFlight.compareAndSet(false, true)) {
            rebuildExecutor.execute(this::drainPending);   // 无丢唤醒交接
        }
    }
}
```

**三个触发源全部经由 `requestRebuild()`，不得各自调用 `doRebuild()`**：

- `@Scheduled(fixedDelay = 3 * 60 * 1000)`
- `@TransactionalEventListener(AFTER_COMMIT)`（T3 交付）
- `POST /v2/lineage/rebuild`（T4 交付）

### 2.3 读一致性与单调发布

```java
private void doRebuild() {
    // ① 整次读取（节点 + 边 + 全部分页）必须在同一个只读 REPEATABLE READ 事务、同一连接内
    //    否则分页跨越 is_current 翻转 → 快照含某作业的新旧两版边，或一条都没有
    Snapshot next = txTemplate.execute(readOnlyRepeatableRead, tx -> {
        long generation = readDbGeneration();
        return buildFromDb(generation);
    });
    publishIfNewer(next);
}

/** 代际单调：只有不低于已发布代际才允许覆盖，杜绝慢重建覆盖新快照 */
private synchronized void publishIfNewer(Snapshot next) {
    if (next.generation() < publishedGeneration.get()) {
        metrics.staleRebuildDiscarded(next.generation(), publishedGeneration.get());
        return;
    }
    published = next;
    publishedGeneration.set(next.generation());
}
```

> **读接口只读取一次 `published` 引用，并从该对象内取 generation** —— 不要分别读 `published` 和 `publishedGeneration` 两个字段，否则会产生瞬时错配。

### 2.4 启动加载

照抄 `LoadServiceMeta implements ApplicationRunner`（`LoadServiceMeta.java:55`）的模式复用同一个 `rebuild()`，但**四点相反**：

|           问题            |                                                          处理                                                           |
|-------------------------|-----------------------------------------------------------------------------------------------------------------------|
| 加载失败**不能**阻断 Master 启动  | `run()` 整体 try-catch，标记 `degraded` 并告警 —— **别照抄 `LoadServiceMeta` 的 `@Transactional(rollbackFor = Exception.class)`** |
| 未就绪时 API 返回 **503**     | 返回空图会让用户以为血缘丢了，比报错更糟                                                                                                  |
| 大表分批读                   | 按 `id` 范围分页（每批 1 万）或 MyBatis 流式游标                                                                                     |
| **不要 `@PostConstruct`** | DataSource 未必就绪且拖慢启动                                                                                                  |

### 验收（对应架构文档 L1 验收 10-15）

- coordinator 并发度**恒为 1**；多触发源请求被合并；最终发布最新 generation
- `publishIfNewer` 单测：注入 generation 11 再注入 10，确认 10 被丢弃且 `staleRebuildDiscarded` +1
- 持续 pending 不饥饿：达到轮数/墙钟预算后让出线程并重新投递
- 重建失败可恢复：注入异常，`lastRebuildError` 被记录、pending 不丢、下轮恢复
- 读一致性：分页期间并发翻转 `is_current`，快照不得含同一作业两个版本，也不得一条边都没有

> ⚠️ **不要写"慢重建与快重建并发、慢的被丢弃"这种测试** —— single-flight 下两次重建不可能并发，该测试永远无法通过。并发控制测 coordinator，代际保护测 `publishIfNewer` 单元方法。

---

## T3 — 写路径（ingest）

> **架构文档 §3.4.4。本任务含 L0 阻塞项，见 §0.2。**

### 产出

```text
datasophon-api/src/main/java/com/datasophon/api/lineage/
├── LineageIngestService.java
├── StructuralHashCalculator.java      // ← L0 #7 阻塞：归一规则
├── CanonicalNameResolver.java         // ← L0 #2 阻塞：Gravitino 拼写
├── WatermarkExtractor.java            // ← L0 #8 阻塞：单调序号来源
└── event/                             // OpenLineage 类型仅存在于此包内
```

**包边界纪律**：OpenLineage / 解析器类型**绝不外泄**到 service / controller 层，对外只暴露自定义 POJO。将来换 provider 只换实现。

### 3.1 三种语义分开处理（**核心，Codex 二轮 P0-1**）

`SELECT ... FOR UPDATE` 只保证同一作业的事务串行，**不能判断"结构不同"是新结构还是晚到的旧运行**。三件事三种机制：

```text
① INSERT IGNORE INTO t_ddh_lineage_event (producer, run_id, event_type, ...)
   └─ 影响行数 = 0 → 重复投递，直接返回，不进入后续任何步骤

② 事务内按固定顺序加锁：
   SELECT current_structural_hash, current_watermark FROM t_ddh_data_job WHERE id = ? FOR UPDATE
     ├─ watermark <= current_watermark → 【晚到的旧 run】只记 parse_log，**绝不改 current**
     ├─ hash 相同                       → 只更新 last_seen + 推进 watermark，不写版本
     └─ hash 不同 且 watermark 更新       → is_current 翻转 + 写 node/edge/definition(version+1)
                                          + 更新 hash/watermark + generation +1

③ AFTER_COMMIT → coordinator.requestRebuild(Trigger.EVENT)
```

### 3.2 固定加锁顺序（写进代码注释）

```text
t_ddh_data_job(按 job_id)
  → t_ddh_lineage_node(按 canonical_name 或 node_id 排序)
  → t_ddh_lineage_edge / t_ddh_data_job_definition
  → t_ddh_lineage_generation
```

死锁异常按**整个事务**有限重试。`last_seen` 若改异步合并写，必须用 `GREATEST(last_seen, ?)` 防止乱序导致时间回退。

### 3.3 事件监听器

```java
/**
 * 结构变更落库后提前触发一次全量重建 —— 仅为降低延迟，不承担正确性。
 * 事件丢失/重复/乱序均无害：最坏结果是等到下一个 3 分钟窗口。
 * 注意：这里是"触发重建"，不是"修改内存图"（违反纪律 ①）。
 *
 * 与本仓库现有的普通 @EventListener（WorkerCommandClient.java:262 监听
 * WorkerOfflineEvent）语义不同 —— 必须是 @TransactionalEventListener，
 * 否则事务回滚后仍会触发。请勿"顺手统一"。
 */
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
void onLineageChanged(LineageStructureChangedEvent e) {
    coordinator.requestRebuild(Trigger.EVENT);
}
```

### 3.4 必须埋的四个计数器

`event_total` · `structure_change_total` · `edge_rows_written_total` · `last_seen_rows_updated_total`

**架构文档声称"结构变化仅几十次/天"目前没有证据**，这四个计数器是唯一的证伪手段。

### 验收（对应架构文档 L1 验收 1-9）

1. 真实事件样本正确入库
2. **投递幂等**：同一 `(producer, runId, eventType)` 重复 100 次，event 表 1 行，边表不变
3. **结构未变不写版本**：不同 run 相同结构连灌 100 次，definition 版本数不变
4. **晚到的旧 run 不改 current**：先灌 v2（watermark 大），再灌 v1 COMPLETE（watermark 小），current 仍是 v2 且 `parse_log` 有记录
5. **重叠 run**：START₁ START₂ COMPLETE₂ COMPLETE₁ 交错到达，current 由 watermark 最大者决定
6. **A→B→A 合法回退**：必须成功产生 version 3 且不撞唯一键
7. **写路径不读快照**：快照置空/置过期时行为完全不变
8. **并发写不产生多个 current**：20 线程并发，`COUNT(DISTINCT definition_version) = 1` 且与 job 当前版本一致

   > ⚠️ **必须同时断言** `SELECT COUNT(*) FROM t_ddh_data_job WHERE cluster_id=? AND engine=? AND job_name=?` **= 1**（三轮自审 F1）。
   >
   > 缺这一条时，"并发 INSERT 出两个 job_id"的场景下本条**依然全绿** —— 每个 job_id 各自只有一个 current。验收条件是从同一份规格推导的，因而继承了规格的盲区。

8b. **并发首次事件只建一个作业**（三轮自审 F1）：对**从未出现过**的 `(cluster_id, engine, job_name)` 用 20 线程并发灌首个事件，`t_ddh_data_job` 只增 1 行。**去掉 `uk_data_job_identity` 后此测试必须失败** —— 用于证明约束真正生效，而非碰巧没并发

9. **死锁可恢复**：反序更新共享节点，死锁后整事务重试成功

---

## T4 — 查询 API

> **架构文档 §3.4.5（陈旧性契约）/ §3.4.6（禁全图）**

### 产出

`datasophon-api/src/main/java/com/datasophon/api/controller/v2/LineageV2Controller.java`（照抄同目录现有 `*V2Controller` 的风格）

|              端点               |                              契约                              |
|-------------------------------|--------------------------------------------------------------|
| `POST /v2/lineage`            | OpenLineage 兼容接收端（T3）                                        |
| `GET  /v2/lineage/graph`      | **`rootNodeId` 必填**、`depth` 默认 2 上限 5、`direction`、硬上限 300 节点 |
| `GET  /v2/lineage/overview`   | 按 `dw_layer` 聚合的 5 个块 + 层间边计数                                |
| `GET  /v2/lineage/table/{id}` | 单表详情                                                         |
| **`GET  /v2/lineage/impact`** | **严格接口**，`stale=true` 时返回 **503 + 告警**                       |
| `POST /v2/lineage/rebuild`    | **返回 202 + generation**，不同步执行重建                              |

### 4.1 两层新鲜度（Codex 二轮 P0-2）

```text
snapshotStale = publishedGeneration < observedDbGeneration     // 已知落后
             || rebuildFailedAfterTargetGeneration            // 追赶失败
             || ageSeconds > threshold                        // 兜底（默认 600s）
```

**`stale` 绝不能只按年龄算** —— 快照刚建 10 秒、DB generation 已推进、重建失败，此时系统明知落后却报告新鲜，fail closed 不会启动。

所有血缘 GET 响应必须带：

```jsonc
{
  "data": { },
  "snapshot": { "generation": 4471, "targetGeneration": 4472, "builtAt": "...",
                "ageSeconds": 47, "stale": true, "lastRebuildError": "..." },
  "sourceFreshness": { "lastEventReceivedAt": "...", "status": "OK|LAGGING|UNKNOWN" }
}
```

> `sourceFreshness` 与 `snapshot` **是两个维度**：`builtAt` 只能证明"何时读过 DB"，不能证明上游 OpenLineage 没有积压。拿不到上游状态就老实报 `UNKNOWN`，**不要用"快照新鲜"冒充"源数据完整"**。

### 4.2 严格接口：fail closed（**已决策：正确性优先**）

重建持续失败时，`/impact` **保持不可用并告警**。**不实现"绕过快照直查 DB"的兜底路径** —— 那会让纯投影模型出现例外分支，而例外分支是这类设计腐化的起点。

### 4.3 逻辑边响应格式

```jsonc
{ "src": 101, "dst": 205,
  "jobs": [ {"jobId": 7,  "edgeId": 3301, "flowType": "BATCH"},
            {"jobId": 12, "edgeId": 4102, "flowType": "STREAM"} ] }
```

### 验收（对应架构文档 L1 验收 17-18、21）

- 逻辑边口径：`Σ(jobRefs.size())` == DB current edge 行数；逻辑边数 == `COUNT(DISTINCT src,dst)`
- **两层新鲜度**：构造"年龄仅 10s 但 generation 落后"场景，`stale` 必须为 `true`；`/impact` 返回 503 并告警，`/graph` 正常返回并带标记
- 快照未就绪时 GET 返回 503 而非空图

---

## T5 — 分层 BFS 与度数折叠

> **架构文档 §3.4.6**

### 产出

`datasophon-api/src/main/java/com/datasophon/api/lineage/LineageGraphQuery.java`

### 5.1 商余分配（**不是** `max(1, ...)`）

```text
每层开始前：
  1. 收集 frontier，按 (dw_layer 距离, 度数升序, node_id) 稳定排序   ← 确定性
  2. q = 剩余预算 / frontier 节点数
     r = 剩余预算 % frontier 节点数
     前 r 个节点得 q+1，其余得 q                                  ← 预算为 0 是合法结果
  3. 预算为 0 的分支直接返回 collapsed token，不展开
  4. 逐节点展开，实际用量少于预算时把余额还给后续节点
```

> **不能用 `max(1, 剩余/frontier数)`**：剩余 2、frontier 5 个节点时每个仍分到 1，总需求 5 > 2，最终还是靠 300 硬上限粗暴截断 —— "按 frontier 分摊"名存实亡。

### 5.2 折叠节点契约

```jsonc
{"type": "collapsed", "token": "n:1042:down:g4471", "hiddenCount": 247, "direction": "downstream"}
```

`token` **内含 generation**；二次展开时若 generation 不匹配返回 **409**，让前端整图重载 —— 否则会把两个代际的图拼接成现实中不存在的结构。

### 5.3 确定性是硬要求

Guava 邻接集合的迭代顺序**不是稳定契约**。不排序会导致同一查询两次返回不同截断结果，用户刷新一下图就变了，会被当成 bug。排序键末位加 `node_id` 保证全序。

### 验收（对应架构文档 L1 验收 19-20）

- 同一查询连续 10 次结果**完全相同**
- 覆盖：环、菱形（多路径到同一节点）、多超级节点同层、不同插入顺序
- 边界：`remaining = 0` / `1` / `frontierCount - 1`
- **环检测分级**（三轮自审 F2）：纯 DAG → `selfLoopCount=0 / hasNonTrivialCycle=false`；`INSERT OVERWRITE t SELECT FROM t` → `selfLoopCount=1 / hasNonTrivialCycle=`**`false`**（关键行，直接用 `Graphs.hasCycle()` 会得 `true` 而失败）；A→B→A 跨作业环 → `hasNonTrivialCycle=`**`true`** 且告警。三组均**不阻断发布**，BFS 均不死循环

---

## T6 — 单 Master 租约

> **架构文档 §3.4.5 部署约束段。Codex 二轮 P1-5：打印日志不是约束检测。**

### 背景（已核实）

仓库当前确实是单 Master：K8s 场景 API 仍部署在唯一 `mw1`、不进 Deployment（`deploy/deployment-k8s.md:45`）；standalone 同样只有一个 `mw1` API（`deploy/deployment-standalone.md:44`）。

但**启动日志拦不住**另一台主机启第二个 API，固定端口只能防同机重复进程。

### 产出

```text
启动 → 独立连接获取 MySQL advisory lock（GET_LOCK）
       或写租约表 { owner, heartbeat, expiresAt, fencing_token }
  ├─ 成功 → 正常提供血缘功能，后台心跳续租
  └─ 失败 → readiness DOWN + 拒绝血缘端点 + 告警（**不是打 WARN 继续跑**）
```

**要点**：租约必须用**独立连接**持有（连接归还池即释放锁，不能复用业务连接）。部署文档需补充升级交接流程与租约超时时长，避免蓝绿发布期间新实例长时间不可用。

### 验收（对应架构文档 L1 验收 16）

启动第二个实例时：获取租约失败 → readiness DOWN → 血缘端点拒绝服务。

---

## T7 — 测试

架构文档 L1 共 **23 条验收**，已分配到各任务（T2:10-15 / T3:1-9 / T4:17-18,21 / T5:19-20 / T6:16 / T8:22）。第 23 条：

> `@WebMvcTest` + mock service 绕开 gRPC 18081 端口冲突。**新增 `@SpringBootTest` 必须加 `@DirtiesContext`**。

---

## T8 — 可观测埋点

|     类别     |                                                  指标                                                   |
|------------|-------------------------------------------------------------------------------------------------------|
| 重建**分段**耗时 | DB read · 映射 · 建图 · `copyOf` · `hasCycle` · publish                                                   |
| 重建结果       | `staleRebuildDiscarded` · `rebuildFailed` · `lastRebuildError`                                        |
| 写路径        | `event_total` · `structure_change_total` · `edge_rows_written_total` · `last_seen_rows_updated_total` |
| 锁          | `lock_wait` p95/p99 · deadlock count · `history_list_length`                                          |

**没有分段打点，架构文档 §3.4.8 的三条回头触发点全是摆设** —— 半年后没人知道重建已从 500ms 涨到 3s。

---

## 附：交付前自查

```bash
JAVA_HOME=$JH21 ./mvnw spotless:apply -s ~/.m2/setting.xml
JAVA_HOME=$JH21 ./mvnw -pl datasophon-api -am test -s ~/.m2/setting.xml
```

|                        自查项                         | 对应纪律 |
|----------------------------------------------------|------|
| 代码中不存在修改快照内容的 API                                  | ①    |
| 所有 `snapshotHolder.get*()` 调用点都在 `@GetMapping` 链路内 | ②    |
| `Graphs.transitiveClosure()` 零调用                   | ③    |
| L0 阻塞项均为 `// TODO(L0-#N)` 而非臆造实现                   | §0.2 |
| 新增 `@SpringBootTest` 都带 `@DirtiesContext`          | §0.3 |

