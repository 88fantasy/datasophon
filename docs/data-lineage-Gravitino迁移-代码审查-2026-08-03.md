# 数据血缘后端迁移 Gravitino —— 代码审查报告

> 2026-08-03。范围：Codex 依照 `~/Downloads/PLAN.md`（"将 Datasophon 血缘后端迁移至 Gravitino"）完成的实现，
> 审查两侧已提交代码是否可简化 / 抽象提取。**本报告只读，未修改任何代码。**

## 审查对象

| 仓库 | 分支 | commit | 净改动 |
|---|---|---|---|
| datasophon | `feat/data-lineage-l1` | `239c28dc` feat + `794555cd` fix | **-6927 行**（删本地 ingest/lease/快照/MySQL 查询实现，新增 ~350 行代理） |
| gravitino | `branch-1.3`（本地未推送） | `7ad3a7f8c` feat + `cce3493c4` fix | **+3957 行**（新增 `lineage/storage` 包、`LineageQuery` 契约、8 个原生 REST 接口） |

## 结论先行

方向和分层是对的：Datasophon 从血缘权威端退化为无状态查询代理，删掉的代码远多于新增的，
代理层本身很干净（未发现 lease / readiness / ingest-token 的残留引用，前端 `service.ts`、
契约测试 `contract.test.ts`、`docs/open-api/*.yaml` 三者一致）。

问题集中在两处：

1. Gravitino 新增的 `lineage/storage` 包内部——同一组数据在三层各排了一种参数顺序、
   两个类里各定义了一遍相同的私有 record、分层词表两套互不同步。
2. Datasophon 代理有一处 fail-closed 校验放错了生命周期阶段，产生了非预期的"全进程"级爆炸半径。

以下按 **P0（越界问题，需要修）→ P1（值得做的抽象提取）→ P2（小清理）→ 明确判定不改的项 →
待人工决策项** 排列。

---

## P0 —— 越界问题（不只是重构，需要修）

### P0-1 `auth-token` 校验放在构造器，默认配置下整个 datasophon-api 起不来

**位置**：`datasophon-api/src/main/java/com/datasophon/api/lineage/proxy/GravitinoLineageClient.java:69-74`

```java
if (StringUtils.isBlank(authToken)) {
    throw new IllegalArgumentException(
            "datasophon.lineage.proxy.auth-token must be set; ...");
}
```

而 `datasophon-api/src/main/resources/application.yml:83` 的默认值是：

```yaml
auth-token: ${DDH_LINEAGE_PROXY_AUTH_TOKEN:}
```

**影响**：`GravitinoLineageClient` 是 `@Component`，Spring 启动期急切实例化其构造器。只要没设
`DDH_LINEAGE_PROXY_AUTH_TOKEN` 环境变量，**整个 Master 进程启动失败**——主机管理、服务编排等
与血缘完全无关的功能一并不可用。旁证：为了让测试上下文能加载，两处测试配置被迫塞入占位 token
（`application-test.yml` +4 行、`application-integration.yml` +5 行），这本身就是"校验放错层"
的信号。

**建议**：把校验从构造器移到请求入口（`exchange()`），空 token 时直接返回
`ResponseStatusException(SERVICE_UNAVAILABLE, "lineage proxy auth token is not configured")`。
fail-closed 的安全语义完全保留（没配置就查不了血缘），但爆炸半径从"全进程无法启动"收缩到
"血缘接口不可用"。副带收益：两处测试配置里的占位 token 可以删掉。

**验证**：`LineageV2ControllerTest`、`GravitinoLineageClientTest` 现有用例覆盖正常路径；
需补一条"空 token → 503"的新用例。

### P0-2 分层词表两套互不同步，`TMP` 层能标注但不参与排序

**产出侧**：`gravitino/lineage/src/main/java/org/apache/gravitino/lineage/storage/LineageDatasetParser.java:126-137`

```java
rules.put("cdc_", "CDC"); rules.put("ods_", "ODS"); rules.put("dwd_", "DWD");
rules.put("dws_", "DWS"); rules.put("dim_", "DIM"); rules.put("ads_", "ADS");
rules.put("tmp_", "TMP"); rules.put("temp_", "TMP");   // 8 个前缀
```

**消费侧**：`gravitino/lineage/src/main/java/org/apache/gravitino/lineage/storage/LineageGraphQuery.java:55-58`

```java
private static final List<String> STANDARD_LAYERS =
    List.of("CDC", "ODS", "DWD", "DIM", "DWS", "ADS");        // 6 个，没有 TMP
private static final Map<String, Integer> LAYER_RANK =
    Map.of("CDC", 0, "ODS", 1, "DWD", 2, "DIM", 2, "DWS", 3, "ADS", 4);  // 同样没有 TMP
```

**影响**：`tmp_*` / `temp_*` 表会被正确打上 `dwLayer=TMP`，但 `layerDistance()`
（`LineageGraphQuery.java:353-359`）查不到它的 rank，返回 `Integer.MAX_VALUE`——BFS 按
"层距离→度数→ID"分配节点预算时，TMP 表永远排最后、最容易被折叠成 `CollapsedNode`；
`overview()`（:126-153）里 TMP 也会和真正的 `UNKNOWN` 落进同一档排序。即"能标注、
不参与设计好的排序/预算逻辑"，与两层各自的字面意图都不符。

**建议**：提取单一真相（enum 或常量类，例如 `DwLayer`），同时携带「匹配前缀 + rank +
是否计入 overview 标准层」三项属性，`LineageDatasetParser` 与 `LineageGraphQuery` 都从它读，
不再各自维护一份表。**TMP 应该排在哪一档需要人来定**——建议给它一个 rank（不再是
`Integer.MAX_VALUE`），但保持不计入 `STANDARD_LAYERS` 的固定初始化列表（即只有真实存在
TMP 表时才出现在 overview 里，不占位一个恒为 0 的空档）。

**验证**：`TestLineageGraphCache` 里的
`testDatasetParserPreservesRawIdentityAndDerivesCanonicalName` 需扩一条 `tmp_`/`temp_` 前缀
用例；`overview()` 的排序断言需要同步更新预期值。

---

## P1 —— 值得做的抽象提取 / 去重

### P1-1 同一组 6 个查询参数，三层三种参数顺序（本轮最值得修的一条）

| 层 | 位置 | 签名 |
|---|---|---|
| 接口 | `LineageQuery.java:86-92` | `listTables(page, size, keyword, layer, connector, database)` |
| 缓存 | `LineageGraphCache.java:128-133` | `listTables(page, keyword, layer, connector, database, size)` |
| 查询 | `LineageGraphQuery.java:89-96` | `list(snapshot, keyword, layer, connector, database, page, size)` |

`LineageService.java:124` 负责在接口层和缓存层之间做一次位置重排。三个 `int`/`String` 位置参数
全靠肉眼对齐，顺序传反不会有任何编译错误，只会在运行时静默返回错误的分页结果——这类缺陷
用测试也不好抓，必须专门写一条参数顺序断言才能防住。

**建议**：让 `LineageGraphCache implements LineageQuery`，把签名统一到接口那一份权威顺序上；
`LineageGraphQuery.list` 也对齐。`LineageService.java:117-158` 的 7 个纯转发方法会随之退化为
1:1 委托，可以直接省掉这一层（或保留但方法体只剩一行 `return graphCache.xxx(...)`），估算减少
~50 行样板代码。

**附带**：`rebuild()` 目前拼在 `LineageService.java:154-158`（`cache.requestRefresh()` +
`cache.currentGeneration()` 两步手工组合），要让 `LineageGraphCache` 完整实现
`LineageQuery` 接口，这个组合逻辑需要下沉成 `LineageGraphCache.rebuild()` 方法本身。

### P1-2 `EdgeKey` 在同一个包内重复定义两次

- `gravitino/.../storage/JdbcLineageStorage.java:747`：`private record EdgeKey(long source, long target) {}`
- `gravitino/.../storage/LineageGraphQuery.java:385`：完全相同的一份

两个类同属 `org.apache.gravitino.lineage.storage` 包，提成包级 `EdgeKey`（去掉 `private`）
即可，无需改变任何调用方式。

### P1-3 `Direction` 的字符串映射有两份真相

`LineageQuery.java:29-51` 的 enum 常量本身已经带了双字段：

```java
UPSTREAM("upstream", "up"), DOWNSTREAM("downstream", "down"), BOTH("both", "both");
```

但 `fromRequest()`（:53-68）和 `fromToken()`（:71-82）又把同样的字面量各写了一遍 `switch`。
数据已经在对象里了，解析理应反向查表，而不是重述一遍映射关系——这是典型的"两份真相"，
将来新增方向时很容易漏改其中一处。

**建议**：改为遍历 `values()` 建两张静态 `Map<String, Direction>`（一张按 `requestValue`、
一张按 `tokenValue`），`fromRequest`/`fromToken` 各自查表 + 抛错。两个方法各减约 12 行。

### P1-4 `JdbcLineageStorage` 751 行 / 五个职责（方案，本次不落地）

单个类当前同时承担：

1. 配置与 schema 校验（:276-318）
2. 事件写入事务（:320-527，含内部类 `EventData`、私有 record `DatasetIdentity`）
3. 快照加载（:551-598）
4. job 详情查询（:227-265）
5. `DataSource` 生命周期（构造器 + `close()`）

**建议方向**：拆出 `LineageSchemaValidator`（承担①，约 50 行）与 `LineageEventWriter`
（承担②，约 200 行，含两个内部类），主类只保留 ③④⑤ 与连接管理。

**风险提示（拆分前必读）**：`resolveDatasets()`（:401-462）里用 `TreeMap` 保证数据集按
固定顺序处理，这是防止并发事务锁顺序死锁的关键机制——代码里已有明确注释说明
（:410-413："TreeMap iteration is sorted by DatasetIdentity ... keeps concurrent
transactions acquiring lineage_dataset row locks in a consistent global order"）。
拆分时这段排序语义必须原样搬到新类，`TestJdbcLineageStorageIT` 里的并发用例是验证这一点
唯一的防线，拆分后必须重跑。

### P1-5 `LineageGraphCache` 混了三件不同粒度的事

341 行的类里同时有：

- 刷新调度的 single-flight/coalescing 逻辑（:119-258）
- 六个查询方法的转发（:127-178）
- 新鲜度计算——`context()`（:273-315，单方法 40 余行）里揉合了 generation 缓存读取、
  快照新鲜度（`stale` 判定）、事件源新鲜度（`lastEventReceivedAt` 判定）三套独立逻辑

**建议**：把 `context()`、`currentGenerationCached()`、`GENERATION_CACHE_TTL_MILLIS` 一起
抽成 `LineageFreshnessEvaluator`。优先级低于 P1-1/P1-4——做完 P1-1 之后 `LineageGraphCache`
的方法列表会变短，届时这块的职责混杂会更显眼，届时再动更省力。

---

## P2 —— 小清理

- **`LineageEdgeValue`**（整个文件，`gravitino/.../storage/LineageEdgeValue.java`）：手写的
  `final class` 只包一个 `ImmutableList<GraphJob>` 字段外加一个 getter，直接改成 `record` 可省
  约 20 行样板。
  **反例说明（不要连带改掉）**：`LineageGraphSnapshot` 结构类似但**不要**改成 record——
  它的 `graph()`/`nodes()` 访问器是包级可见（package-private），改成 record 会把
  `ImmutableValueGraph` 强制变成 `public` 访问器，等于把内部实现细节泄漏成公开 API。
- **`JdbcLineageStorage.firstText(root, fallback, pointer)`**（:600）：方法名是 "first"
  （暗示会尝试多个 pointer、取第一个命中的），但签名只收单个 `pointer` 参数，与调用处
  （:254、:255、:257）的用法（各传一个固定 JSON Pointer）不符。建议改 varargs 支持真正的
  "多选一"语义，或者干脆改名为 `textAt`，消除名实不符。
- **`GravitinoLineageClient.getJob()`**（datasophon，:90-96）：为了把 `clusterId` 注入到
  响应根节点，单独复制了一份 `exchange()` 调用 + 注入逻辑，绕开了已有的 `NodeInjection`
  枚举机制。建议给 `NodeInjection` 加一个 `ROOT` 枚举值（`injectNode(response.at("/data"), ...)`
  的变体，直接作用于响应根而非 `/data` 子节点），把 `getJob()` 并回通用的 `get()`，
  少一个公开方法、少一条专门的测试分支。
- **`LineageV2ControllerTest`**：文件顶部已经 `static import` 了 `any`/`anyMap`，但内部
  20+ 处 `org.mockito.ArgumentMatchers.eq`/`anyLong`/`anyString`/`argThat` 全部使用完全限定名。
  补齐 static import 即可，纯格式噪音，不影响行为。

---

## 明确判定「不改」的项

审查中发现但**判定不值得动**的相似逻辑，一并记录、避免重复评估：

- **`GravitinoLineageEndpointResolver.resolve()`**（datasophon，:67-79：按 clusterId + 角色名
  取唯一 RUNNING 实例）与 **`OtelDorisReaderFactory.create()`**（:77 起，同样按 clusterId +
  `"DorisFE"` 角色名取唯一 RUNNING 实例）结构同构。但目前全仓只有这 2 处，且两者后续用途
  已经分叉（一个组 URI 给 HTTP 客户端用，一个建 Hikari 连接池）。按仓库
  `CLAUDE.md` §2「不为单点用法抽象」的原则，**留作观察项**，出现第 3 处同构逻辑时再提取
  `RunningRoleLocator`。
- **`LineageV2Controller.tables()/graph()`**（:61-67、:77-81）手工拼 `LinkedHashMap` 而不用
  `Map.of(...)`——因为 `Map.of` 不接受 `null` 值，而这里的可选查询参数（`keyword`/`layer`/
  `connector`/`database`/`expand`）经常是 `null`。现写法是必要的，不动。
- **`LineageConfig` 的 10 个 `ConfigEntry` 声明**（~140 行样板）——是 Gravitino 全仓统一的
  配置声明范式（`ConfigBuilder().doc().version().xxxConf().createWithDefault()`），不应该为
  血缘模块单独发明一套简写，会破坏和其他模块配置类的一致性。
- **`LineageOperations.execute()`** 的异常→HTTP 状态码集中映射（:167-185：
  `StaleLineageTokenException→409`、`NoSuchLineageDatasetException→404`、
  `LineageUnavailableException→503`、`IllegalArgumentException→400`）——已经是所有 7 个
  查询接口共用的单一实现，写法正确，不需要改动。

---

## 待人工决策事项（非重构问题，单独列出）

`JdbcLineageStorage.java:552-560` 有一条 Codex 在实现过程中自己留下的注释：

> `NOTE(C3, code review 2026-08-03)`：`loadCurrentEdges` 目前 union 了
> START/RUNNING/COMPLETE 各类事件的边。收紧到只认 run 的权威 COMPLETE 事件，会漏掉
> "增量 facet 上报"型 producer 的真实边（这类 producer 常见做法是让 COMPLETE 事件本身
> inputs/outputs 为空，依赖 START 阶段已经声明过）；但不收紧，则可能在 COMPLETE 从未
> 确认过的情况下展示幻影边。

这是一个**产品/数据语义决策**，不是代码质量问题，本报告不代为拍板。列在此处是为了确保它被
显式看到，而不是停留在源码内部注释里等下一个读到这段代码的人重新发现。建议：结合沙箱环境
实际接入的 OpenLineage producer（Spark + openlineage-spark）的真实上报行为，决定收紧或维持
现状，再回填 `docs/lineage/gravitino-server-lineage.md` 说明。

---

## 涉及文件汇总

**Gravitino**（`lineage/src/main/java/org/apache/gravitino/lineage/`）：
- `LineageQuery.java`（P1-1、P1-3）
- `LineageService.java`（P1-1）
- `storage/LineageGraphCache.java`（P1-1、P1-5）
- `storage/LineageGraphQuery.java`（P0-2、P1-1、P1-2）
- `storage/JdbcLineageStorage.java`（P1-2、P1-4、P2、待决策事项）
- `storage/LineageDatasetParser.java`（P0-2）
- `storage/LineageEdgeValue.java`（P2）

**Datasophon**（`datasophon-api/src/main/java/com/datasophon/api/lineage/proxy/`、
`controller/v2/`）：
- `proxy/GravitinoLineageClient.java`（P0-1、P2）
- `proxy/GravitinoLineageEndpointResolver.java`（不改，见"明确判定"）
- `controller/v2/LineageV2Controller.java`（不改，见"明确判定"）
- `src/test/.../controller/v2/LineageV2ControllerTest.java`（P2）

## 后续建议

本报告只交付问题清单，不实施修改。建议按仓库既有工作流（记于用户 memory
`feedback_claude_plan_codex_implement`：Claude 出计划、Codex 实现、Claude 验证）处理：

1. P0-1、P0-2 影响范围小、风险低，可优先转给 Codex 实施。
2. P1-1 改动面稍大（跨 `LineageQuery`/`LineageService`/`LineageGraphCache`/`LineageGraphQuery`
   四个文件），建议单独一个 PR，实施后跑 `./gradlew :lineage:test` + `spotlessCheck`。
3. P1-4（拆分 `JdbcLineageStorage`）建议放到最后，且必须先跑通
   `TestJdbcLineageStorageIT` 的并发用例作为改动前基线。
4. 待决策事项需要人先拍板，再落到代码和文档。
