# 血缘 L1 交接（2026-07-30）

> **给下一个 session 的状态快照。** 本文只记「现在在哪、下一步做什么、有哪些坑」，
> **不复述**架构与任务内容 —— 那两份文档是权威，本文过期时以它们为准。

---

## 1. 一句话现状

L1 **第 1 批（T0/T1/T2）与第 2 批（R1-R5 返工 + T3 写路径 + T6 租约）均已实现、验证、提交**：
默认组 23 个单测 + 真实 MySQL 组 10 个单测全绿，四条纪律机械验证通过，验收 8b 做过证伪实验。
下一步是**第 3 批（T4 查询 API + T5 分层 BFS）**。L0 现场核查**仍未执行**，
T3 里被它阻塞的三处目前是骨架 + `TODO L0-#N`。

## 2. 分支与提交

分支 `feat/data-lineage-l1`（基于 `main` 的 `0b80a856`），**未 push**。

|   commit   |                                内容                                 |
|------------|-------------------------------------------------------------------|
| `b658b447` | `docs(lineage)` 架构文档 + L1 任务清单 + Phase G 标记被取代                    |
| `f4c06739` | `feat(lineage)` 第 1 批：7 个主代码 + DDL/DML + 6 个测试                    |
| `83ebefa9` | `docs(lineage)` SnapshotLoader 事务契约（R5 + 纪律 ④）                    |
| `dab36d1c` | `docs(lineage)` 第 2 批开工决策 §3.0 D1-D15 + T6 定稿 §6.1                |
| `e4b1bbba` | `fix(lineage)` R1-R5 五项返工                                         |
| `e9944d39` | `feat(lineage)` 第 2 批：T3 写路径 + T6 租约 + `MysqlSnapshotLoader` + 测试 |

> ⚠️ 工作区里 `datasophon-ui-v2/config/proxy.ts` 有一处**本机联调改动**
> （`localhost:8080` → `192.168.10.131:8080`），与血缘无关，**不要提交**。

## 3. 三份文档的分工

|                  文档                  |                  什么时候读                  |
|--------------------------------------|-----------------------------------------|
| `data-lineage-平台级血缘架构-2026-07-29.md` | 想知道**为什么这么设计**、四个决策的论证、L1 的 23 条验收      |
| `data-lineage-L1-实施任务清单.md`          | 想知道**接下来写什么代码** —— T0-T8、四批交付、返工项 §2.1b |
| 本文                                   | 想知道**现在到哪了**                            |

## 4. 进度

|  批次   |               任务               |    状态    |
|-------|--------------------------------|----------|
| 第 1 批 | T0 基准脚手架 · T1 DDL · T2 内存图+协调器 | ✅ 已提交并验证 |
| 第 2 批 | R1-R5 返工 · T3 写路径 · T6 单实例租约   | ✅ 已提交并验证 |
| 第 3 批 | T4 查询 API · T5 分层 BFS          | ⬜ 未开始    |
| 第 4 批 | T7 测试 · T8 埋点                  | ⬜ 未开始    |

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

## 6. 阻塞项：L0 现场核查（9 项，未执行）

其中**三项直接阻塞 T3**，任务清单 §0.2 要求**只写骨架 + TODO，不要臆造实现**：

|         被阻塞项          |                                                          依赖                                                          |
|-----------------------|----------------------------------------------------------------------------------------------------------------------|
| `canonical_name` 转换函数 | L0 #2 —— Gravitino 转换后 dataset 的 namespace/name **确切拼写必须实机采样**。写错会让 Spark 与 Flink/DS 的节点对不上、图断成两半。**这是整个 epic 的生死点** |
| `watermark` 取值来源      | L0 #8 —— 上游能否提供可靠单调序号                                                                                                |
| structural hash 归一规则  | L0 #7 —— 动态表名 / 临时表 / 日期分区的实际形态                                                                                      |

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

|                                坑                                 |                                                                                                   说明                                                                                                    |
|------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **`datasophon-api` 全量测试跑不起来**                                    | 它对 `datasophon-ui-v2` 的 jar 是**硬依赖**，不先构建前端就 `Could not resolve dependencies`。`-pl datasophon-common,datasophon-grpc-api,datasophon-api` 同样失败                                                           |
| **`-o` 离线模式失败**                                                  | `${revision}` 解析不了                                                                                                                                                                                      |
| **Spotless 管 `docs/**/*.md`**                                    | 会重排表格对齐与引用块缩进。改完 md 直接 `spotless:apply`，不要手工对齐                                                                                                                                                          |
| **新增 `@SpringBootTest` 必须加 `@DirtiesContext`**                   | 否则两个上下文抢 gRPC 18081，全量测试必挂，**且报错表象伪装成 MySQL 连接失败**                                                                                                                                                      |
| **`DatabaseMigration` 同时读 DDL 与 DML**                            | `DatabaseMigration.java:158` 无条件取两个 Resource，所以每个版本目录都要有 DML 文件（可为空）                                                                                                                                    |
| **`LineageRebuildCoordinator` 没有 `@Component`**                  | 第 2 批已由 `LineageConfiguration` 注册为 `@Bean`（注入 `TransactionTemplate` + `MysqlSnapshotLoader`）                                                                                                            |
| **`GeneratedKeyHolder.getKey()` 不能用于 `ON DUPLICATE KEY UPDATE`** | MySQL 在**更新分支且值真的变化**时报 `affected_rows=2`，Connector/J 产生两个 generated key，`getKey()` 直接抛 `InvalidDataAccessApiUsageException`。值不变（`id = LAST_INSERT_ID(id)`）时 `affected_rows=0` 反而不炸。正确写法：INSERT 后按唯一键回查 |
| **spotless 配了 `<ratchetFrom>`**                                  | 文件没被改动就不检查，**一旦改动整个文件必须符合当前格式**（含删尾随空格）。所以改老文件时出现满屏空白 diff 是正常的，还原它反而会让 `spotless:check` 拦下构建。改完直接 `spotless:apply`                                                                                     |
| **surefire `<excludedGroups>` 写死时命令行覆盖不了**                       | 插件 `configuration` 里的硬编码值优先级高于 `-DexcludedGroups=`，必须写成 `${excludedGroups}` + POM property。否则 mysql 组永远「0 个测试且是绿的」                                                                                      |
| **Codex 沙箱禁止 TCP socket**                                        | 它跑不了任何连 `127.0.0.1:3306` 的测试。凡真实 MySQL 验收必须由验证方复跑，**不能采信「已覆盖」的声明**                                                                                                                                      |

## 9. 审查历史

|     轮次      |                      来源                      |                                      结果                                       |
|-------------|----------------------------------------------|-------------------------------------------------------------------------------|
| 一轮          | Codex `019fae1c-edff-7002-82c6-bf005bd70074` | 写侧依赖陈旧快照、重建未串行化等                                                              |
| 二轮          | Codex `019fae34-3112-7c40-8c05-b971d22258fb` | 晚到旧 run 回滚结构、A→B→A 撞唯一键、`stale` 漏报、重建占用请求线程                                   |
| 三轮          | 自审（对第 1 批代码逐行核对）                             | F1 作业身份未声明唯一 / F2 `hasCycle` 恒真 / F3 诱饵字段 / F4 测试断言全局最新版本 / F5 事务契约只在 Javadoc |
| `/simplify` | 4 个 agent（复用·简化·效率·层次）                       | 修 9 处、跳过 6 处，见 commit `f4c06739`                                              |
| 四轮          | 第 2 批交付验证（真实 MySQL 实跑）                       | 1 个必现生产缺陷（`getKey()` 与 `ON DUPLICATE KEY UPDATE` 不兼容）+ 1 个偶然通过的假绿并发测试，均已修     |

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
> **问一句"这个测试在缺陷存在时会不会失败"，比多写三条断言有用。**

