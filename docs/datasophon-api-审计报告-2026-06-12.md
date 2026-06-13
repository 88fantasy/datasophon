# datasophon-api 架构审计报告

> 角色:系统架构师 | 日期:2026-06-12 | 范围:`datasophon-api`(549 个 Java 文件 / ~4.6 万行,Spring Boot 3.4 + MyBatis-Plus + gRPC)
> 性质:**只读审计,不含实施。**
> 方法:3 个并行 Explore 代理分维度取证(性能/并发/结构),关键项由架构师亲自打开文件二次核验。所有问题均定位到 `文件:行号`,仅列有把握项。
> 进度追踪:本报告供后续分批修复使用,可在每条问题前用 `[ ]` / `[x]` 勾选状态。

---

## Context(为什么做这次审查)

在清理 `api.properties`/`worker.properties` 无效配置、修正 `GlobalVariables` 读取方式后,对 `datasophon-api` 做一次系统性架构体检,识别可优化点。本报告是体检结论:按「正确性/安全 → 性能 → 结构」三层、按严重度排序,给出证据与建议方向,供后续决定是否分批整改。

---

## 0. 全局快照(grep 统计)

|                 指标                  |               数值               |                      规范出处                       |
|-------------------------------------|--------------------------------|-------------------------------------------------|
| `@Autowired` 字段注入                   | **303 处**                      | springboot.md「constructor injection everywhere」 |
| 构造器注入(`@RequiredArgsConstructor` 等) | 仅 **8 个类**                     | 同上                                              |
| `return null`(主源码)                  | **51 处**                       | java.md「never return null」                      |
| `e.printStackTrace()` 吞异常           | 3 处(2 处真违规)                    | java.md「never swallow」                          |
| `ProblemDetail` 使用                  | 0 处(用自定义 `Result` 信封)          | springboot.md RFC7807                           |
| `Vos` 历史命名残留                        | 93 处(渗入 DAO/Controller 公共 API) | —                                               |
| TODO/FIXME                          | 8 处                            | —                                               |
| Akka/Pekko 残留                       | **0**(已彻底清除,做得好)               | CLAUDE.md                                       |
| `javax.crypto.*`                    | 3 处(JDK 标准库,**无需迁移**)          | —                                               |

---

## 1. 正确性 / 并发安全(最高优先,潜在线上故障)

### 🔴 H1 — GlobalVariables 把 live map 引用泄漏进 gRPC 序列化,存在 CME + 脏读

**证据(已核验):**
- 写入:`load/GlobalVariables.java:44-50` `put()` 内 `synchronized(vars){ vars.clear(); vars.putAll(value); }`;但 `putValue:64-66` 单 key 写**不与 put 互斥**。
- 读取:`getVariables:52-54` 直接 `return` 缓存里那个 live `ConcurrentHashMap` 本体,**读取方全程不持锁**。
- 危险下发点:`master/handler/service/ServiceStartHandler.java:70`、`ServiceStatusHandler.java:67` 直接 `cmd.setVariables(GlobalVariables.getVariables(...))` → 经 gRPC 序列化遍历该 live map。
- **自相矛盾的旁证**:`utils/ProcessUtils.java:503`、`master/handler/service/ServiceUpgradeHandler.java:69` 却防御性地包了 `new HashMap<>(GlobalVariables.getVariables(...))`。同一数据,一半拷贝一半直用 —— 风险已被部分开发者意识到,但未统一。

**触发条件:** 30s `@Scheduled checkServiceRoles` / @Async DAG 线程调 `put()` 触发 `clear()` 的同时,另一线程正序列化下发命令 → `ConcurrentModificationException` 或下发残缺/半填充变量集。
**影响:** 安装/巡检期间偶发命令携带错误全局变量 → 配置生成错、状态误判;低概率 CME 中断流程。
**建议方向:** `getVariables` 返回**不可变快照**(`Map.copyOf` 或 `new HashMap<>`),一次性堵住所有不安全调用点;`put`/`putValue` 统一同步策略。
**类注释自承认:** `GlobalVariables.java:19` `fixme ... 该类无法做到线程安全`。

### 🔴 H2 — @Scheduled 巡检在 5 线程调度池里做串行阻塞 gRPC,慢 Worker 拖垮整个巡检

**证据:**
- `master/MasterScheduledService.java:90`(checkServiceRoles,30s)、`:73`(checkHosts)循环遍历**所有**角色/主机。
- 每个经 `CheckUtils.java:177` `workerCommandClient.executeCmd`(deadline 90s)或 `master/service/HostCheckService.java:115` `ping`(deadline 30s)**同步阻塞 gRPC**。
- 调度线程池仅 **5 线程**(`MasterAsyncConfig.java:66`)。
**触发条件:** 集群规模大 + 部分 Worker 慢/超时。单轮耗时 = Σ(每角色 gRPC 往返);`ping` 30s × N 主机串行可占满单线程数分钟。
**影响:** 巡检延迟累积、状态刷新滞后;调度池耗尽时 `checkHostWithDelay` 等延迟任务被饿死。
**建议方向:** 巡检循环内的 gRPC 提交到 `masterExecutor`(已有界:core10/max50/queue200)并发执行 + 收紧单调用 deadline;调度线程与业务阻塞解耦。

### 🟠 M2 — 事务回滚后 GlobalVariables 内存写不回滚,内存与 DB 不一致

**证据:** `service/impl/ServiceInstallServiceImpl.java:101` 类级 `@Transactional`;内部 `ProcessUtils.generateClusterVariable`(`ProcessUtils.java:362`)既写 DB(`variableService`)又写进程内 `GlobalVariables`。事务回滚 → DB 回滚但内存残留脏变量。
**建议方向:** 内存写移到 `TransactionSynchronization.afterCommit`;避免类级大网 `@Transactional`,按方法精细化。

### 🟠 M1 — WorkerCommandClient:gRPC Channel 仅靠离线事件回收,事件丢失即句柄泄漏

**证据:** `grpc/WorkerCommandClient.java:80` 按 hostname 缓存 Channel,仅 `onWorkerOffline:274` `channel.shutdown()` 回收;`getStub:358` 复用缓存 Channel **不校验 `isShutdown()/isTerminated()`**;`onWorkerOffline` 只 `shutdown()` 无 `shutdownNow` 兜底(对比 `destroy:287` 有)。
**触发条件:** Worker 频繁上下线/改名、或离线事件在 Master 重启窗口丢失。
**建议方向:** `getStub` 取出后校验状态按需重建;`onWorkerOffline` 补 `shutdownNow` 兜底。

### 🟠 M3 — 并发 DAG 可能并发改写共享 Generators 元数据的 outputDirectory

**证据:** `master/DAGExecutor.java:112` `execDAG` 为 `@Async`,可并发;`:399-403` 拿 `getVariables`(live)后对 `Generators` 调 `setOutputDirectory(...)`。若 `Generators` 来自 `LoadServiceMeta` 共享单例,并发 install+restart 会互相覆盖。
**建议方向:** 确认 Generators 是否每次新建;若共享,占位符替换改生成副本而非原地 set。

### 🟡 L(并发潜在) — RepoDAG 转真异步前的隐患 / JschUtils 阻塞无超时

- `dag/RepoDAG.java:36` `listeners` 普通 `ArrayList`、`:117` `forward` 递归操作非线程安全 `ArrayDeque`。当前单线程同步执行安全,**若启用 `NodeExecutionCallback` 真异步则会损坏队列** → 换 `ConcurrentLinkedQueue`/`CopyOnWriteArrayList`。
- `datasophon-common/.../JschUtils.java:97` `execForStr` `Thread.sleep(500)` 轮询直到 closed **无超时上限**,远端命令不结束则线程永久阻塞,会放大 H2。

---

## 2. 性能 / 数据访问

### 🔴 P1 — 巡检热路径 4N 次串行 Prometheus HTTP(同 H2 同源,影响最大)

`master/service/HostCheckService.java:91-110` 对每台主机串行发起 4 次 `PromInfoUtils.getSinglePrometheusMetric`(`:139/144/151/158`),且在 `@Scheduled` 路径。N 台 = 4N 次串行 HTTP。
**建议:** 4 个 PromQL 合并为单次批量查询,或主机维度并行化。

### 🔴 P2 — 高频分页列表 N+1 查角色组(已核验)

`service/impl/ClusterServiceRoleInstanceServiceImpl.java:154-160` 分页后 `for` 内逐行 `roleGroupService.getById(roleGroupId)`。pageSize 条 = pageSize 次额外 SELECT,UI 高频接口。
**建议:** 去重 `roleGroupId` 后 `listByIds` 一次查回,内存 Map 关联。

### 🔴 P3 — 循环内逐主机串行 gRPC 下发

`utils/ProcessUtils.java:375-394`(hdfsEcMethond)、`:614-628`(syncUserGroupToHosts)`for` 内逐主机串行 `operateFile`/`executeCmd`/`createUnixGroup`。
**建议:** 用 `masterExecutor` 并行 fan-out 聚合结果。

### 🟠 P4 — PhysicalProductInstallServiceImpl 三处 N+1 + 重复 JSON 解析

`service/extrepo/impl/PhysicalProductInstallServiceImpl.java`:`saveDAG:313-373`(每节点 `lambdaQuery().one()` + `getAllServiceRoleList()` + 逐角色重复 `parseObject`)、`doGenerateInstallCmd:279-297`(角色×主机次 `getOneServiceRole`)、`setHosts:745-751`(逐角色 `frameService.getById`)。
**建议:** 用方法入口已查的 `serviceList` 构建 `serviceName:version→entity` / `serviceId→roles` Map 一次性查回;serviceInfo 每节点解析一次。

### 🟠 P5 — K8s 安装命名空间/实例重复查询;逐 Pod 串行拉事件

- `service/extrepo/impl/K8SProductInstallServiceImpl.java:403-412` install→exec 各查一遍 ns/instance(重复 2 组查询/服务)。
- `service/k8s/impl/K8sServiceImpl.java:609-612` 逐 Pod `client.getEventOf(...)` 串行 kubectl。
  **建议:** ns/instance 参数透传避免重复查;事件改 `labelSelector` 一次拉取或并行。

### 🟡 P6 — 主机列表全量内存排序分页 / 元数据未缓存

- `service/impl/HostInstallServiceImpl.java:187-326/463-473` 缓存全量主机 `getListPage` 内存 substring 分页,大规模时每请求全量 stream sorted。
- `ServiceInstallServiceImpl` / `FrameServiceServiceImpl.java:91-141` 反复查 `clusterInfoService.getById` + frame 元数据 —— 这些是启动时已入 `LoadServiceMeta` 内存的低频变更数据。
  **建议:** 缓存结构改为可分页;frame/clusterInfo 加 `@Cacheable`(带失效)或复用 `load/` 内存映射。

---

## 3. 结构 / 可维护性

### 🟠 S1 — 字段注入 303 处 vs 构造器注入 8 类(最大合规缺口,但属机械快赢)

反例集中:`PhysicalProductInstallServiceImpl`(11)、`ServiceInstallServiceImpl`(11)、`HostInstallServiceImpl`、`ClusterServiceRoleInstanceServiceImpl` 等大 Impl。
**影响:** 不可变缺失、单测需 Spring 上下文/反射、隐藏循环依赖。
**建议:** 改 `@RequiredArgsConstructor` + `private final`,从 4 个大 Impl 起步滚动。低风险高收益但量大。

### 🟠 S2 — God class / 超长方法

- `utils/ProcessUtils.java`(706 行 / 27 个 static / ≥5 职责:命令实体生成、配置变量合并、服务启停、告警 CRUD、用户组同步)。`saveServiceInstallInfo:119-224` ~105 行。**全 static 无法注入/mock**。可拆 `CommandEntityFactory`/`ServiceConfigMerger`/`ServiceLifecycleExecutor`/`AlertService`。**收益最高的结构性重构。**
- `PhysicalProductInstallServiceImpl`(766 行):`deploy`/`redeploy`/`generateAndExecSrvInstCmd`/`generateAndExecSrvRoleCmd` 四条 DAG 装配流程高度雷同(见 S3)。
- `K8sServiceImpl`(683)、`ServiceInstallServiceImpl`(609):`*ToInfo` 映射 / 持久化流程方法偏长,建议抽 Mapper / 切分。

### 🟠 S3 — 重复代码:afterCommit + DAG 装配复制粘贴

`PhysicalProductInstallServiceImpl` 内 `TransactionSynchronization.afterCommit{ invokeCommands(...) }` 匿名类在 `:244/:629/:701` 三处逐行重复(连注释一起 copy);同模式还散落 `K8SProductInstallServiceImpl`/`ClusterInfoServiceImpl`/`FrameServiceServiceImpl`/`K8sClusterConfigServiceImpl`。`buildDeployDAG + getReverseDag`(STOP 反转)模式重复。
**建议:** 抽 `afterCommitInvoke(dagId, commandIds)` + `saveAndDispatchDAG(...)` 模板方法。纯抽取、行为不变,**低风险高收益**。

### 🟡 S4 — 吞异常 / 魔法值 / 返回 null / 历史命名

- **吞异常(真违规):** `utils/ServletUtils.java:140`(catch 后 `printStackTrace()` + `return null`)、`service/impl/ClusterNodeLabelServiceImpl.java:112`(catch 后 `printStackTrace()` 返回 false)。→ 改 `logger.error(msg, e)`。**快赢。**
- **魔法值:** `K8sServiceImpl.java:61` 硬编码 `"nexus-registry-secret"`;`Result.error("中文")` 14 处散落未走 i18n/Status 枚举;模块仅 1 个 Constants 类。
- **返回 null 热点:** `ServletUtils`(5)、`UniEngineServiceImpl`(4)、`UploadTempFileServiceImpl`(3)、`K8sServiceImpl`(3)、`GlobalVariables`(2)、`PhysicalProductInstallServiceImpl:605`(public 方法 commandIds 空时 return null)。
- **Vos 残留 93 处:** `dao/model/extrepo/VosDdLServiceMeta`(大小写畸形)、`controller/extrepo/ExtRepoVosInstallController`、`VosProductDeployDAGBuildContext`。IDE 重构统一改名。
- **死代码:** `strategy/ServiceHandlerAbstract.java:122` `TODO 将废弃` 未清理。
- **ProblemDetail 缺失:** 两套 `@(Rest)ControllerAdvice` + `@Order` 框架到位(设计合理),但响应体走自定义 `Result` 信封、0 处 ProblemDetail。**注:这是 ant-design-pro 既定信封设计,改造成本高,属架构选择而非纯技术债,优先级最低。**

---

## 做得好、不要误改

- **Pekko/Akka 已彻底清除**(主源码 0 处),仅测试 yml 注释残留。
- **Jakarta 迁移正确**:`servlet`/`validation` 已用 `jakarta.*`;残留的 `javax.crypto.*`(3 处)是 **JDK 标准库,Boot3 无需迁移**,勿误改。
- **统一异常处理框架**双 Advice + `@Order` 分包设计合理。
- **`MasterAsyncConfig.masterExecutor`** 有界(core10/max50/queue200)+ 优雅关闭,非默认 `SimpleAsyncTaskExecutor`;`WorkerCommandClient` `@PreDestroy` 关闭所有 Channel,lifecycle 正确。唯一并发隐患是 `taskScheduler` 仅 5 线程承载阻塞巡检(H2)。
- **`@Transactional` 未误放 controller 层**。

---

## 整改优先级总表(供决策与进度追踪)

| #  | 状态  | 严重度 |                                                                                                                                                                           问题                                                                                                                                                                           |                                        证据锚点                                        |    性质     |
|----|-----|-----|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------|-----------|
| 1  | [x] | 🔴  | GlobalVariables 返回快照(堵 CME/脏读/gRPC 下发)                                                                                                                                                                                                                                                                                                                 | `GlobalVariables.java:52`                                                          | 1 处改,全局收益 |
| 2  | [x] | 🔴  | 巡检 gRPC/Prometheus 并行化 + 收紧 deadline                                                                                                                                                                                                                                                                                                                   | `MasterScheduledService:90`、`HostCheckService:91`                                  | 中等重构      |
| 3  | [x] | 🔴  | 分页列表 N+1 改 listByIds                                                                                                                                                                                                                                                                                                                                   | `ClusterServiceRoleInstanceServiceImpl:154`                                        | 局部        |
| 4  | [x] | 🔴  | 2 处吞异常补日志                                                                                                                                                                                                                                                                                                                                              | `ServletUtils:140`、`ClusterNodeLabelServiceImpl:112`                               | 快赢        |
| 5  | [x] | 🟠  | GlobalVariables 内存写移到 afterCommit                                                                                                                                                                                                                                                                                                                      | `ProcessUtils:362`、`ServiceInstallServiceImpl:101`                                 | 局部        |
| 6  | [x] | 🟠  | 循环内 gRPC fan-out 并行                                                                                                                                                                                                                                                                                                                                    | `ProcessUtils:375/614`                                                             | 局部        |
| 7  | [x] | 🟠  | Physical/K8s Install N+1 + 重复查/解析                                                                                                                                                                                                                                                                                                                      | `PhysicalProductInstallServiceImpl:313/279/745`、`K8SProductInstallServiceImpl:403` | 中等        |
| 8  | [x] | 🟠  | WorkerCommandClient.getStub 校验 Channel 状态                                                                                                                                                                                                                                                                                                              | `WorkerCommandClient:358/274`                                                      | 局部        |
| 9  | [x] | 🟠  | 抽 afterCommit 模板方法消除复制粘贴                                                                                                                                                                                                                                                                                                                               | `PhysicalProductInstallServiceImpl:244/629/701`                                    | 快赢·纯抽取    |
| 10 | [x] | 🟠  | 字段注入 → 构造器注入(分批)                                                                                                                                                                                                                                                                                                                                       | 303 处,4 大 Impl 起步                                                                  | 机械·量大     |
| 11 | [x] | 🟠  | 拆分 ProcessUtils god class(已完成:按职责拆为 ServiceConfigUtils/ServiceCommandUtils/ServiceLifecycleUtils/ServiceAlertUtils/WorkerFanOutUtils 5 个静态工具类,getExceptionMessage 并入 CommonUtils,原类已删)                                                                                                                                                                 | `ProcessUtils.java`(已删除)                                                           | 结构重构      |
| 12 | [x] | 🟡  | 返回 null → Optional/空集合(已完成可安全子集:ServletUtils/UniEngine/UploadTempFile/ClusterUserGroup/AlertHistoryGateway/K8sServiceInstanceService;排除:PhysicalProductInstallServiceImpl.generateAndExecSrv*(REST 信封)、CommonUtils.convertRoleType、GlobalVariables.getValue*、K8sServiceImpl 3 处 Void lambda、GrafanaProxyConfiguration.getCluster、ScriptType.of、序列化器契约) | ServletUtils/UniEngine 等                                                           | 分批        |
| 13 | [x] | 🟡  | 杂项(已做:JschUtils 超时上限、nexus-registry-secret 抽常量、Vos 命名统一为 Physical/Product —— 7 类 + 2 bean 名 + 方法/字段全改,白名单保留 \${ROOT.VosManager.*}/x-vos-*/vos_ddl/NAMESPACE=vos/URL path/用户文案/VOS DDL 术语;勘误:decideEnableKerberos 仍被 8 个 strategy 引用,非死代码)                                                                                                              | 见各节                                                                                | 杂项        |
| 14 | [-] | 🟡  | ProblemDetail 化(跳过:报告已注明属架构选择、优先级最低)                                                                                                                                                                                                                                                                                                                   | 异常处理两套 Advice                                                                      | 中期·有取舍    |

---

## 如需后续执行

若决定整改,建议按上表 #1→#4 先做「正确性/安全快赢」(4-5 文件、低风险),验证方式:
- **#1 GlobalVariables**:加并发单测(多线程 put + getVariables 序列化)断言无 CME、快照隔离;`./mvnw test -pl datasophon-api`。
- **#3 N+1**:开 MyBatis SQL 日志,断言分页接口 SELECT 次数从 1+N 降为 2。
- **#4 吞异常**:静态扫描确认 0 处 `printStackTrace`。
再按需推进性能(#2/#6/#7)与结构(#9→#11)分批。
