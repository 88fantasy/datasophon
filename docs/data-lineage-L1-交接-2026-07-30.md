# 血缘 L1 交接（2026-07-30）

> **给下一个 session 的状态快照。** 本文只记「现在在哪、下一步做什么、有哪些坑」，
> **不复述**架构与任务内容 —— 那两份文档是权威，本文过期时以它们为准。

---

## 1. 一句话现状

**L1 全部四批（T0-T8）均已实现、验证、提交**。第 1-3 批（T0-T6）默认组 47 个单测 +
真实 MySQL 组 11 个单测全绿，四条纪律机械验证通过，验收 8b 做过证伪实验。第 4 批
（T7 审计 + T8 埋点，§7.0/§8.0 F1-F6）已于 2026-07-30 交付：T7 审计确认验收 1-21、23
在前 3 批已全部覆盖；T8 把 `IngestMetrics`/`RebuildMetrics` 从 `NOOP` 接上真实
Micrometer（`/actuator/prometheus` 拉模式），补了重建六段计时、`lock_wait`、
`history_list_length` 权限降级路径，默认组测试从 47 涨到 **50 个全绿**（详见 §5.3，
含 Codex 两轮交付中 Claude 复跑时发现并修复的 3 处编译期缺陷）。L0 现场核查**部分完成**
（`canonical_name` 的 JDBC 子项已实机采样并修复，见 §6）；Hive/Paimon/Iceberg 三类仍是
骨架 + `TODO L0-#N`（阻塞于沙箱无 Spark/Hive metastore/Paimon warehouse），**这是 L1
之外唯一还悬着的事**，L1 本身范围内的工作已经做完。

## 2. 分支与提交

分支 `feat/data-lineage-l1`（基于 `main` 的 `0b80a856`），**未 push**。

|        commit         |                                内容                                 |
|-----------------------|-------------------------------------------------------------------|
| `b658b447`            | `docs(lineage)` 架构文档 + L1 任务清单 + Phase G 标记被取代                    |
| `f4c06739`            | `feat(lineage)` 第 1 批：7 个主代码 + DDL/DML + 6 个测试                    |
| `83ebefa9`            | `docs(lineage)` SnapshotLoader 事务契约（R5 + 纪律 ④）                    |
| `dab36d1c`            | `docs(lineage)` 第 2 批开工决策 §3.0 D1-D15 + T6 定稿 §6.1                |
| `e4b1bbba`            | `fix(lineage)` R1-R5 五项返工                                         |
| `e9944d39`            | `feat(lineage)` 第 2 批：T3 写路径 + T6 租约 + `MysqlSnapshotLoader` + 测试 |
| `83ebefa9`~`490b8535` | `docs(lineage)` 交接文档更新 + L0 现场核查报告（Gravitino 不做规范化 + JDBC 实机采样）   |
| `aea2948f`            | `fix(lineage)` `CanonicalNameResolver` 拒绝 100% 真实 JDBC 事件的缺陷      |
| `86856178`            | `fix(v2-api)` 通用异常 handler 吞掉 `ResponseStatusException` 真实状态码     |
| `b96bd140`            | `docs(lineage)` 第 3 批开工决策 §4.0（T4）+ §5.0（T5）                      |
| `673d28bd`            | `feat(lineage)` 第 3 批：查询 API + 分层 BFS 度数折叠                        |

> ⚠️ 工作区里 `datasophon-ui-v2/config/proxy.ts` 有一处**本机联调改动**
> （`localhost:8080` → `192.168.10.131:8080`），与血缘无关，**不要提交**。

## 3. 三份文档的分工

|                  文档                  |                  什么时候读                  |
|--------------------------------------|-----------------------------------------|
| `data-lineage-平台级血缘架构-2026-07-29.md` | 想知道**为什么这么设计**、四个决策的论证、L1 的 23 条验收      |
| `data-lineage-L1-实施任务清单.md`          | 想知道**接下来写什么代码** —— T0-T8、四批交付、返工项 §2.1b |
| 本文                                   | 想知道**现在到哪了**                            |

## 4. 进度

|  批次   |               任务               |          状态          |
|-------|--------------------------------|----------------------|
| 第 1 批 | T0 基准脚手架 · T1 DDL · T2 内存图+协调器 | ✅ 已提交并验证             |
| 第 2 批 | R1-R5 返工 · T3 写路径 · T6 单实例租约   | ✅ 已提交并验证             |
| 第 3 批 | T4 查询 API · T5 分层 BFS          | ✅ 已提交并验证             |
| 第 4 批 | T7 测试 · T8 埋点                  | ✅ 已提交并验证（默认组，见 §5.3） |

## 5. 第 2 批的验证结论（已完成，勿重做）

|     项      |                怎么验的                 |                       结果                       |
|------------|-------------------------------------|------------------------------------------------|
| R1-R5 返工   | 逐个 diff 核对                          | 全部到位                                           |
| 纪律 ②       | grep 全部 `snapshotHolder.get*()` 调用点 | ingest 链路零命中                                   |
| 纪律 ③       | 全模块检索 `transitiveClosure`           | 仅注释，零调用                                        |
| 纪律 ④       | 查 `MysqlSnapshotLoader`             | 无事务注解、不自取连接                                    |
| R5 / 验收 15 | 隔离级别参数化跑真实 MySQL                    | `REPEATABLE_READ` 一致，`READ_COMMITTED` **确实撕裂** |
| 验收 8b      | 删掉 `uk_data_job_identity` 后复跑并发用例   | **确实失败**（20 线程插出 5 行 job），索引已恢复                |
| 验收 16      | 真实 MySQL 双租约实例                      | 第二实例 503 且未进业务，首个 close 后接管                    |
| L0 阻塞三处    | 逐个读实现                               | 接口 + 单一直白实现 + `TODO L0-#N`，无臆造                 |

**修掉的一个必现生产缺陷**：`GeneratedKeyHolder.getKey()` 不能用于
`ON DUPLICATE KEY UPDATE` —— 见下方坑表。

## 5.1 L0 现场核查：`canonical_name` 的 JDBC 子项已完成（含一个推翻架构假设的发现）

借用 `deploy/deployment-standalone-doris.md` 五节点沙箱做的实机采样（不是推断），
完整报告在 `docs/monitoring/data-lineage-verification.md`：

- **Gravitino 不做 identifier 规范化**——服务端只有 `NoopProcessor`，`GravitinoSparkPlugin`
  是 catalog 插件跟 OpenLineage 事件生成无关。架构文档 §2.1/§3.3/D1 的相关表述已回写
- **JDBC 真实格式**：`namespace = scheme://host:port`（不含 database），
  `name = database.table`（点号分隔）——与架构文档假设的 `mysql-cdc://host:port/db/table`
  两处都不符；Doris 走标准 JDBC 时 scheme 是 `mysql` 不是 `doris`
- 用真实数据核对后确认第 2 批的 `CanonicalNameResolver.Default` 会拒绝 100% 真实 JDBC
  事件，**已修复**（commit `aea2948f`）：按 `namespace` 是否含斜杠分流两个分支
- Hive/Paimon/Iceberg 仍未采样（阶段 B 未部署，沙箱无 Hive metastore/Paimon warehouse），
  JDBC 的采样结果不能外推到它们

## 5.2 第 3 批的验证结论（已完成，勿重做）

|      项       |                   怎么验的                    |                                 结果                                  |
|--------------|-------------------------------------------|---------------------------------------------------------------------|
| E1（生产缺陷预检）   | 真实 Spring 上下文（非 `@WebMvcTest` 阉割版）        | 确认修复前 503/400 全部退化成 200，已修复（commit `86856178`）                      |
| E2-E4（两层新鲜度） | 逐行核对 `LineageV2Controller.queryContext()` | `observedDbGeneration` 独立查询、`stale` 三分支、`targetGeneration` 绑定源，逐条对上 |
| E5-E9（端点契约）  | 读代码 + 跑 `LineageV2ControllerTest`（14 个）   | 404/400/409/503 全部覆盖，日志里能看到真实状态码                                    |
| E10-E12（BFS） | 读 `LineageGraphQuery` + 手算一个真实用例验证        | `remaining=0/1/frontierCount-1` 边界、全折叠不做部分展开、根节点占预算，均与手算结果一致        |
| 全量回归         | `datasophon-api` 全量测试（462 个）              | 2 个失败与本次无关（`OtelMetricsQueryServiceTest` 既有缺陷，已用 `git stash` 复现证实）  |

**顺带修掉的一个独立生产缺陷**：`V2ApiExceptionHandler` 的兜底 handler 没有
`@ResponseStatus`，会把 `ResponseStatusException`（503/404/409）吞成 200——
T4 的整个 fail-closed 设计建立在状态码能正确透传之上，已在开工 T4 前用真实 Spring
上下文实测坐实并修复（commit `86856178`），详见下方坑表。

## 5.3 第 4 批（T8）的交付与验证（2026-07-30）

Codex 两轮（第一轮网络断连 failed，`codex resume` 续接后 completed）落地了 §8.0 F1-F6：
新增 `MicrometerIngestMetrics`/`MicrometerRebuildMetrics`/`LineageHistoryListLengthGauge`
三个类 + `LineageObservabilityTest`，改动 `pom.xml`/`application.yml`/
`LineageConfiguration`/`IngestMetrics`/`LineageGraphSnapshot`/`LineageIngestService`/
`LineageRebuildCoordinator`/`MysqlSnapshotLoader`/`LineageDeadlockRetryMysqlTest`。

**Codex 两轮都未能真正编译通过**（沙箱里 `-am` 会拉 `datasophon-ui-v2` 一起构建，第一轮
卡在前端构建无输出触发网络断连；第二轮前端构建本身过了，但从没跑到 Java 测试编译）——
按老规矩，Codex 沙箱跑不出来的验证由 Claude 侧复跑，这次多复现了两处编译期缺陷：

1. `LineageObservabilityTest` 误 `import org.springframework.transaction.TransactionCallback`/
   `TransactionOperations`（实际在 `org.springframework.transaction.support` 包下）
2. `SimpleMeterRegistry` 在当前 Micrometer 版本下不是 `AutoCloseable`，`try (SimpleMeterRegistry
   registry = ...)` 编译不过，改成普通声明 + 裸块
3. 手写的 `IngestJdbcTemplate extends JdbcTemplate` 测试桩只覆盖了 `update(String, Object...)`，
   漏了 `persist()` 里那条无参 `update(String sql)`（`generation = generation + 1`），
   补上这个重载后才真正跑通

修完后默认组 **50 个单测全绿**（含新增 `LineageObservabilityTest` 3 个），spotless 无残留改动。

**一个未验证的遗留点**：`LineageDeadlockRetryMysqlTest` 的断言从 `retries >= 1` 改成了
`registry.get("lineage.ingest.deadlock").counter().count() == 1`——该文件 `@Tag("mysql")`，
本次默认组验证覆盖不到它，真实死锁场景下是否总是精确重试 1 次，需要下次有真实 MySQL 沙箱时
复核，若偶发 >1 次会导致该用例变 flaky。

## 6. 阻塞项：L0 现场核查（9 项，`canonical_name` 的 JDBC 子项已完成，其余未执行）

其中**三项直接阻塞 T3**，任务清单 §0.2 要求**只写骨架 + TODO，不要臆造实现**：

|         被阻塞项          |                                                                            依赖                                                                             |
|-----------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `canonical_name` 转换函数 | L0 #2 —— **JDBC 已完成实机采样并修复**（§5.1）。Hive/Paimon/Iceberg **仍需实机采样**，阻塞于沙箱无 Hive metastore/Paimon warehouse。写错会让不同 provider 的节点对不上、图断成两半，**这仍是整个 epic 的生死点** |
| `watermark` 取值来源      | L0 #8 —— 上游能否提供可靠单调序号                                                                                                                                     |
| structural hash 归一规则  | L0 #7 —— 动态表名 / 临时表 / 日期分区的实际形态                                                                                                                           |

## 7. 无未决问题

原先悬而未决的 `SnapshotLoader` 事务契约已于 2026-07-30 记入任务清单
（**§2.1b R5** + **§0.1 纪律 ④**），并强化了 T2 的读一致性验收 ——
真实用例必须能在隔离级别降到 `READ COMMITTED` 时失败，否则它没有在测隔离级别。

## 8. 环境与操作坑（均已实测）

```bash
export JH21=/Users/pro/Library/Java/JavaVirtualMachines/graalvm-jdk-21.0.7/Contents/Home

# 只跑血缘测试 —— 可用，约 1 分钟
JAVA_HOME=$JH21 ./mvnw -pl datasophon-api -am test -Dtest='Lineage*' \
  -DfailIfNoSpecifiedTests=false -s ~/.m2/setting.xml

# 改过 docs/**.md 后必须跑，否则 test 阶段会被 spotless 拦下
JAVA_HOME=$JH21 ./mvnw spotless:apply -s ~/.m2/setting.xml
```

|                                    坑                                     |                                                                                                                                                                            说明                                                                                                                                                                             |
|--------------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **`datasophon-api` 全量测试跑不起来**                                            | 它对 `datasophon-ui-v2` 的 jar 是**硬依赖**，不先构建前端就 `Could not resolve dependencies`。`-pl datasophon-common,datasophon-grpc-api,datasophon-api` 同样失败                                                                                                                                                                                                             |
| **`-o` 离线模式失败**                                                          | `${revision}` 解析不了                                                                                                                                                                                                                                                                                                                                        |
| **Spotless 管 `docs/**/*.md`**                                            | 会重排表格对齐与引用块缩进。改完 md 直接 `spotless:apply`，不要手工对齐                                                                                                                                                                                                                                                                                                            |
| **新增 `@SpringBootTest` 必须加 `@DirtiesContext`**                           | 否则两个上下文抢 gRPC 18081，全量测试必挂，**且报错表象伪装成 MySQL 连接失败**                                                                                                                                                                                                                                                                                                        |
| **`DatabaseMigration` 同时读 DDL 与 DML**                                    | `DatabaseMigration.java:158` 无条件取两个 Resource，所以每个版本目录都要有 DML 文件（可为空）                                                                                                                                                                                                                                                                                      |
| **`LineageRebuildCoordinator` 没有 `@Component`**                          | 第 2 批已由 `LineageConfiguration` 注册为 `@Bean`（注入 `TransactionTemplate` + `MysqlSnapshotLoader`）                                                                                                                                                                                                                                                              |
| **`GeneratedKeyHolder.getKey()` 不能用于 `ON DUPLICATE KEY UPDATE`**         | MySQL 在**更新分支且值真的变化**时报 `affected_rows=2`，Connector/J 产生两个 generated key，`getKey()` 直接抛 `InvalidDataAccessApiUsageException`。值不变（`id = LAST_INSERT_ID(id)`）时 `affected_rows=0` 反而不炸。正确写法：INSERT 后按唯一键回查                                                                                                                                                   |
| **spotless 配了 `<ratchetFrom>`**                                          | 文件没被改动就不检查，**一旦改动整个文件必须符合当前格式**（含删尾随空格）。所以改老文件时出现满屏空白 diff 是正常的，还原它反而会让 `spotless:check` 拦下构建。改完直接 `spotless:apply`                                                                                                                                                                                                                                       |
| **surefire `<excludedGroups>` 写死时命令行覆盖不了**                               | 插件 `configuration` 里的硬编码值优先级高于 `-DexcludedGroups=`，必须写成 `${excludedGroups}` + POM property。否则 mysql 组永远「0 个测试且是绿的」                                                                                                                                                                                                                                        |
| **Codex 沙箱禁止 TCP socket**                                                | 它跑不了任何连 `127.0.0.1:3306` 的测试。凡真实 MySQL 验收必须由验证方复跑，**不能采信「已覆盖」的声明**                                                                                                                                                                                                                                                                                        |
| **`V2ApiExceptionHandler` 的兜底 handler 吞掉 `ResponseStatusException` 状态码** | 没有 `@ResponseStatus` 的 `@ExceptionHandler(Exception.class)` 会先于 Spring 默认的 `ResponseStatusExceptionResolver` 捕获异常，503/404/409 全部退化成 200。`ResponseStatusException` 与 Spring 内建绑定异常（如缺 `@RequestParam`）分别继承不同基类但都实现 `ErrorResponse` 接口——按 `instanceof` 判断，不枚举具体类型。**`@WebMvcTest` 测试若没 `@Import` 生产会真实加载的 exception handler，测试保护的是假想实现**，这是第五次踩到同一模式（F5 同类） |
| **架构文档里同一个字段名指了两个不同的东西**                                                 | §3.4.5 的 JSON 示例 `targetGeneration` 是"实时查到的 DB 代际"，但 T2 交付的 `LineageSnapshotMeta.targetGeneration()` 语义是"重建成功时读到的代际"，因构造时机恒等于 `generation`，永远不可能不同。T4 响应体的 `targetGeneration` 字段必须绑定前者（新增的 `LineageGenerationReader`），不是后者                                                                                                                                |

## 8b. L2（Spark provider）已开工，配置与鉴权部分已交付（2026-07-30 同日追加）

**决策**：D1 重新评估后**维持经 Gravitino 转发**（用户明确决策），未采用直连
`/v2/lineage` 方案。详见架构文档 D1 小节 2026-07-30 追加段落。

**已交付**：
- `GRAVITINO/service_ddl.json` 新增 8 个 `gravitino.lineage.*` 参数（`source`/
`processorClass`/`sinks`/`sinkQueueCapacity`/`http.sinkClass`/`http.url`/
`http.authType`/`http.apiKey`），全部从 Gravitino 1.3.0 源码
（`/Users/pro/IdeaProjects/gravitino` 本机源码checkout）逐层核对得出真实 key 名，
不是猜的
- `SPARK3/service_ddl.json` 的 `custom.spark.defaults.conf` 追加
`spark.extraListeners`/`spark.openlineage.transport.type`/`.url`（指向
`${ROOT.GRAVITINO.__hostIp__}:${ROOT.GRAVITINO.__port__}/api/lineage`，
沿用 `${ROOT.Rustfs.__hostIp__}` 同款跨服务占位符约定，非新发明语法）
- `LineageV2Controller` 回填了第 1 批就留的 `TODO L2: 接 Gravitino 时补共享 token 校验`：
新增 `datasophon.lineage.ingest-token`（环境变量 `DDH_LINEAGE_INGEST_TOKEN`），
校验 `Authorization: Bearer <token>`（常数时间比较），token 未配置时 fail closed，
必须与 Gravitino 侧 `gravitino.lineage.http.apiKey` 配成同一个值
- 新增 `GravitinoDdlLoadTest`/`Spark3DdlLoadTest`（照抄 `OtelCollectorDdlLoadTest`
的静态 JSON 结构核对模式）+ `LineageV2ControllerTest` 新增鉴权失败测试 +
修复两处因新鉴权检查产生的既有测试回归（`ingestRequiresClusterIdAndReturnsAdviceWrappedPojo`
等需要补 `Authorization` 头）。默认组 **54 个单测全绿**

**明确未交付、需要真实环境才能验证的部分**（不要误认为已完成）：
- `gravitino.conf` 里 `sinks=http` + `authType=apiKey` 这组配置**从未在真实
Gravitino 部署里跑通过**——L0 #4（HTTP sink 重试/超时行为）当时就没执行，之前
沙箱只测过 `sinks` 默认值 `log`。源码读对不代表配置组合已验证
- `spark.sql.gravitino.uri`/`.metalake`（架构文档原 L2 描述里提到的 catalog
联邦配置）**本次特意跳过未加**：SPARK3 现有 `custom.spark.defaults.conf` 已经
直接接了 Hive/Iceberg/Paimon/Doris 四种 catalog，完全不经过 Gravitino；这次
只做血缘转发这一件事，不引入新的 catalog 联邦机制，避免和已跑通的现状冲突
- 四种 catalog（Hive/Iceberg/Paimon、以及 Doris 专用连接器 `DorisTableCatalog`）
的 `canonical_name` **全部未采样**，此前只验证过标准 JDBC 一种格式，Doris 这里
用的专用连接器与标准 JDBC 是不同代码路径，也不能外推。L2 的验收标准"提交真实
Spark 作业 → 边正确入库"需要真实 SPARK3 + Hive metastore + Paimon warehouse
部署才能跑，本次会话没有这个环境
- L2 验收原文"`gravitino_lineage.log` 与 MySQL 内容一致"完全未验证

## 8c. Gravitino 三个 catalog 已建好，为 canonical_name 采样清障（2026-07-30 同日追加）

在五节点沙箱（`deploy/deployment-standalone-doris.md`）已装好的 Gravitino（ddh-02）
上，经 REST API 在既有 metalake `datasophon_verify` 下创建了 `paimon_fs`
（filesystem backend，本地磁盘）、`paimon_s3`（filesystem backend，warehouse 指向
RustFS S3，bucket `lineage-paimon-warehouse`）、`doris_catalog`（`jdbc-doris`
provider，指向 ddh-01 的 Doris FE）三个 catalog，全部建 schema + 建表验证到底
（非仅创建成功）。**不涉及部署 SPARK3/HDFS/Hive metastore（仍属阶段 B，未启动）**，
纯粹是 Gravitino 侧准备。详细操作记录、踩坑（两个内置 catalog provider 缺运行时
jar；Paimon S3 catalog 的 `s3.*` 标准 key 建 schema 时才报"缺失"，须同时用
`gravitino.bypass.*` 透传重复一遍）见 `deploy/deployment-standalone-doris.md` §7.14。

**这不等于 L0/L2 采样已完成**：上面三个 catalog 目前只验证了"能通过 Gravitino
Java API 建表"，还没有一条真实 Spark 作业经过 `openlineage-spark` 监听器写入这三个
catalog——真正要采样的 `namespace`/`name` 格式，只有让 Spark 对着这三个 catalog 跑
一次真实读写才能拿到。Hive catalog 仍然完全没有环境（阶段 B 依赖的 Hive metastore
未搭）。下一步是本机下载 Spark scp 到 ddh-02，配上 openlineage-spark console
transport，对 `paimon_fs`/`paimon_s3`/`doris_catalog` 各跑一次真实读写。

## 8d. Spark 真实读写采样结果：Paimon/Doris 在 openlineage-spark 里根本采不到（2026-07-30 同日追加，重大负面结论）

**结论先行**：用 Nexus 上现成的 `spark-3.5.8-bin-hadoop3.tgz`（`package/manifest.json`
声明的官方版本，与生产 SPARK3 DDL 一致）在 ddh-02 跑了三组真实读写（`CREATE TABLE`
+ `INSERT` + `SELECT`），配置**完全照抄生产 `SPARK3/service_ddl.json` 的
`custom.spark.defaults.conf`**（`org.apache.paimon.spark.SparkCatalog`、
`org.apache.doris.spark.catalog.DorisTableCatalog`，不经过 Gravitino Spark
connector——见下方"弯路"一节说明为什么绕过它），`openlineage-spark` 全部三个
catalog 都打印了 START/COMPLETE 事件，但 **`inputs`/`outputs` 字段永远是空数组
`[]`，日志里明确报 `WARN PlanUtils3: Catalog <类名> is unsupported: Cannot
extract dataset for catalog=<类名>`**（Paimon 是 `org.apache.paimon.spark.
SparkCatalog`，Doris 是 `org.apache.doris.spark.catalog.DorisTableCatalog`）。
这不是配置错、不是版本没对上、也不是 Gravitino 引入的问题——**`openlineage-spark`
从 1.29.0（本次生产用的版本）到 Maven Central 当前最新 1.52.0，反编译两个版本的
jar 确认都不存在任何 `Paimon`/`Doris` 相关的 class 或 handler**：对比之下
`Iceberg`/`Delta`（`IcebergHandler`/`DeltaHandler`/`DatabricksDeltaHandler`
等一整套 handler 类）是有官方支持的，Paimon 和 Doris 的 DSv2 catalog 连一个字符
串"paimon"/"doris"都搜不到。之前 L0 能采到 JDBC 格式（`aea2948f` 那次），是因为
那次走的是 `spark.read.jdbc()` 标准 JDBC 数据源代码路径，跟这次的 DSv2
TableCatalog（`org.apache.paimon.spark.SparkCatalog`/`DorisTableCatalog`）是
openlineage-spark 内部完全不同的两套抽取逻辑——JDBC 那条路径有支持，DSv2
catalog 这条路径对 Paimon/Doris 没有支持。

**对 CanonicalNameResolver 的影响：不需要改代码**——`CanonicalNameResolver` 只在
`namespace`/`name` 真的出现在事件里时才会被调用；Paimon/Doris 这两类事件的
`inputs`/`outputs` 从源头就是空的，没有字符串可解析，属于"上游根本不产出数据"，
不是"产出了但解析错了"，两者是完全不同性质的问题，本次不涉及 T3/CanonicalNameResolver
代码改动。

**走过的弯路，供下次直接跳过**：
1. 一开始按之前的既定思路让 Spark 经 **Gravitino Spark connector**
（`org.apache.gravitino.spark.connector.plugin.GravitinoSparkPlugin`）接
`paimon_fs`/`paimon_s3`/`doris_catalog` 三个 Gravitino catalog——`CREATE
TABLE`/`INSERT`/`SELECT` 全部真实成功（Gravitino 把 Paimon 包了一层
`GravitinoPaimonCatalogSpark35`），但 `openlineage-spark` 同样报
`PlanUtils3: Catalog org.apache.gravitino.spark.connector.paimon.
GravitinoPaimonCatalogSpark35 is unsupported`——Gravitino 包了一层之后
**类名变了**，openlineage-spark 更加不可能认得。这提示：**Gravitino Spark
connector 这条路径天生就比生产现用的"Spark 直连各 catalog"更不利于血缘采集**，
与架构文档 D1 小节"生产 `custom.spark.defaults.conf` 不经过 Gravitino 做 catalog
联邦"这个决策方向一致，不需要再重新评估走 Gravitino 这条路。
2. `spark-sql` CLI 默认把日志压到 `WARN`（`conf/log4j2.properties.template`
里 `logger.thriftserver.level = warn` 会在运行时覆盖 `rootLogger.level=info`），
`openlineage-spark` 的 `ConsoleTransport` 是按 `INFO` 打的，不改这一行看起来会
是"完全没有任何事件"的假象，容易误判为监听器没生效。
3. JDK17 下 `openlineage-spark` 的反射式 provider 加载（`SparkOpenLineage
ExtensionVisitorWrapper`）会因为 `--add-opens` 缺失和 `--jars` 参数传入的 jar
和通过 `spark.extraListeners` 反射实例化的 jar **classloader 不一致**而报
`InaccessibleObjectException`——把所有相关 jar（`openlineage-spark`/
`paimon-spark`/`paimon-s3`/`spark-doris-connector`/`mysql-connector-j`）直接
放进 `$SPARK_HOME/jars/`（而不是 `--jars` 参数）+ 加
`--add-opens=java.base/java.security=ALL-UNNAMED` 等三个 flag 解决了这个警告，
但**不影响本节的核心负面结论**（解决这个警告之后 `PlanUtils3 unsupported` 依然
存在，两者是独立问题）。

**对 L2 验收原文"提交真实 Spark 作业 → 边正确入库"的影响**：Hive 尚未验证（无
metastore 环境，但 Hive 是 Spark 自带的 v1 catalog，openlineage-spark 历史最早
支持的类型之一，架构上大概率没问题，只是没有实机验证过）；**Paimon 和 Doris 两类
作业，只要还在用 `openlineage-spark` 做血缘采集，无论用不用 Gravitino，都拿不到
`namespace`/`name`——这是 L2 架构层面需要用户决策的新问题**，不是能靠继续调环境
解决的。可能的方向（未与用户讨论，仅供参考）：a) 接受 Paimon/Doris 暂时没有
列级/表级血缘，只覆盖 Hive/Iceberg/JDBC；b) 自己写一个实现
`OpenLineageExtensionProvider` SPI 的小扩展类随 SPARK3 包分发（`openlineage-spark`
有这个 SPI 但没人为 Paimon/Doris 实现，`paimon-spark`/`spark-doris-connector`
两个 jar 都没有 `META-INF/services/io.openlineage.spark.api.
OpenLineageExtensionProvider` 条目，反编译确认过）；c) 给 openlineage 上游提
issue/PR。

## 8e. Iceberg catalog 验证：openlineage-spark 真的能采到，正面结论（2026-07-30 同日追加）

按用户要求专门验证 Iceberg（`docs` §8d 提到官方有 `IcebergHandler`，值得优先确认）。
用官方 `org.apache.iceberg:iceberg-spark-runtime-3.5_2.12:1.9.2`（生产 DDL 里锁的是
`iceberg-spark-runtime-3.4_2.12-1.3.1.jar`，本次为规避跨小版本兼容风险改用与
Spark 3.5.8 精确匹配的 3.5 线最新版，不影响"是否支持"这个结论本身——支持与否由
`openlineage-spark` 的 `IcebergHandler` 按类名 `org.apache.iceberg.spark.
SparkCatalog` 匹配决定，跟 Iceberg 具体小版本无关），建一个 Hadoop 类型的独立
catalog（`type=hadoop`，`warehouse=file:///data/iceberg-warehouse-fs`，不依赖
Hive metastore，隔离测试）。

**结果**：`CREATE TABLE ... USING iceberg` 的 `COMPLETE` 事件、`INSERT`（`append_data`
job）的 `START`/`RUNNING`/`COMPLETE`、`SELECT`（`columnar_to_row` job）的输入端，
**全部正确带上了 `outputs`/`inputs`**，零 `PlanUtils3 unsupported` 警告：

```
namespace = "file"
name      = "/data/iceberg-warehouse-fs/lineage_probe/ol_native"
```

即 Iceberg 走的是"表物理存储位置"命名法：`namespace` 是文件系统 scheme（本例是
本地磁盘的 `file`，生产上 S3/HDFS 场景预期会是 `s3a`/`hdfs` 等），`name` 是表在
该文件系统下的绝对路径——**跟 JDBC 那次"`namespace=scheme://host:port`、
`name=database.table`"是完全不同的命名法**，`CanonicalNameResolver` 现有的两个
分支（catalog-style 两段 namespace / JDBC-style 一段 namespace）都不认识这种
"路径当 name"的格式，**如果 Iceberg 血缘要接入生产，`CanonicalNameResolver`
需要新增第三个分支**，具体规则和是否需要下次找到真实 S3/HDFS 环境验证前缀之后
再定，本次只确认了"能采到"这个前提。

**未验证**：本次只测了 `type=hadoop` 独立 catalog；生产 DDL 实际配置是 Iceberg
表建在默认 `spark_catalog`（Hive metastore 支撑）下，`namespace`/`name` 的具体值
在 Hive metastore 场景可能不同（`IcebergHandler` 内部按 catalog 类型分支，
`HiveCatalog` 场景大概率仍是路径命名法，但没有 Hive metastore 环境实测过，不能
断言完全一致）；S3/HDFS 后端的 `namespace` scheme 具体值（`s3a` 还是别的）也没
实测，本地磁盘测试无法覆盖。

## 9. 审查历史

|     轮次      |                       来源                        |                                                               结果                                                               |
|-------------|-------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------|
| 一轮          | Codex `019fae1c-edff-7002-82c6-bf005bd70074`    | 写侧依赖陈旧快照、重建未串行化等                                                                                                               |
| 二轮          | Codex `019fae34-3112-7c40-8c05-b971d22258fb`    | 晚到旧 run 回滚结构、A→B→A 撞唯一键、`stale` 漏报、重建占用请求线程                                                                                    |
| 三轮          | 自审（对第 1 批代码逐行核对）                                | F1 作业身份未声明唯一 / F2 `hasCycle` 恒真 / F3 诱饵字段 / F4 测试断言全局最新版本 / F5 事务契约只在 Javadoc                                                  |
| `/simplify` | 4 个 agent（复用·简化·效率·层次）                          | 修 9 处、跳过 6 处，见 commit `f4c06739`                                                                                               |
| 四轮          | 第 2 批交付验证（真实 MySQL 实跑）                          | 1 个必现生产缺陷（`getKey()` 与 `ON DUPLICATE KEY UPDATE` 不兼容）+ 1 个偶然通过的假绿并发测试，均已修                                                      |
| 五轮          | 第 3 批开工前预检 + L0 现场核查（真实 Spring 上下文 + 五节点沙箱实机采样） | 1 个独立生产缺陷（`V2ApiExceptionHandler` 吞掉 `ResponseStatusException` 状态码）+ 1 个已交付的 `CanonicalNameResolver` 缺陷（架构前提被推翻后用真实数据核出来的），均已修 |

> 三轮自审的 F1/F2/F3/F5 **全部源自文档的「沉默」而非「错误」** —— 规格没说的地方（唯一性、
> 环的定义、字段谁来填、事务谁来开），实现方按字面照做，双方都不觉得自己错。
> 下次审查时，值得问的不是"这条实现对不对"，而是
> **"如果规格根本没说，实现者会默认什么，而我的测试会不会跟着一起默认"**。
>
> 四轮补充了一个新变种：**测试的绿灯可能来自巧合，而不是来自它验证的性质**。
> 第 2 批的并发用例通过了，但只是因为 20 个线程落在同一毫秒内，`DATETIME(3)`
> 精度下 `GREATEST(last_seen, ...)` 结果不变，恰好绕开了当时存在的缺陷 ——
> 缺陷存在时它照样全绿。加固方式是把「必须走到的路径」变成前置条件
> （预置 `last_seen = EPOCH`），而不是加断言。
>
> 五轮再补充一个变种：**`@WebMvcTest` 的切片上下文可以合法地漏掉生产会真实加载的组件**，
> 而测试照样全绿——`LineageV2ControllerTest` 断言 503 时用的是 Spring 默认的
> `ResponseStatusExceptionResolver`，不是生产环境真正在跑的 `V2ApiExceptionHandler`。
> 切片测试的速度优势与"漏掉一个 `@RestControllerAdvice`"是同一件事的两面，
>
>> 排查时值得反过来问一句：**这个切片上下文里，缺席的东西会不会正是生产环境的默认行为**。
>> **问一句"这个测试在缺陷存在时会不会失败"，比多写三条断言有用。**

