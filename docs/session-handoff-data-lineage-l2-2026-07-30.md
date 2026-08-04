# Session Handoff — Datasophon 数据血缘 epic（2026-07-30）

> 给下一个 Claude Code session 的交接文档。目标：不用重新读完整段对话就能接着干。
> 本文只写**这次 session 做了什么、现在什么状态、下一步做什么**——不复述已经写进仓库的
> 决策内容，全部用路径引用。

## 仓库与分支

- 仓库：`/Users/pro/IdeaProjects/datasophon`
- 分支：`feat/data-lineage-l1`（基于 `main`），**全部改动未 push**
- 工作区里 `datasophon-ui-v2/config/proxy.ts` 有一处**本机联调改动**（与本 epic 无关），
  **不要提交它**——这次 session 里已经反复确认过，`git add` 时手动排除

## 这次 session 做了什么（按时间顺序）

1. **L1 第 4 批（T7 测试审计 + T8 可观测埋点）**：
   - commit `1dcacf2c` — 开工决策文档（§7.0 T7 审计结论 + §8.0 T8 的 F1-F6 决策）
   - commit `4674cdf4` — T8 实现：`IngestMetrics`/`RebuildMetrics` 从 `NOOP` 接上真实
     Micrometer，`/actuator/prometheus` 拉模式导出；派 Codex 实现，Claude 复跑发现并
     修复 3 处 Codex 自己沙箱里没编译过就交上来的缺陷
2. **L2 开工（Spark 经 Gravitino 转发血缘事件）**：
   - commit `c9733783` — GRAVITINO/SPARK3 两份 `service_ddl.json` 配置改动 +
     `LineageV2Controller` 补共享 token 鉴权 + 两个 DDL 结构测试

**默认测试组从 47 → 54，全绿**（跑法见下方命令）。

## 现在的状态（权威来源，别猜，直接读）

- **一句话现状 + 环境坑 + 已知缺口**：`docs/data-lineage-L1-交接-2026-07-30.md`
  （尤其是 §8b，是这次 L2 工作的完整记录；§5.3 是 T8 的记录）
- **架构决策**（含这次改动的 D1 最终结论）：
  `docs/data-lineage-平台级血缘架构-2026-07-29.md`（搜 "2026-07-30 L2 开工时最终决定"）
- **任务清单 / 开工决策明细**（T7/T8 的 F1-F6）：`docs/data-lineage-L1-实施任务清单.md`
- 长期记忆（跨 session）：Claude 的 memory 文件
  `project_data_lineage_platform.md`（已同步到最新状态，包含这次的 D1 反复决策过程）

## 明确未完成 / 未验证的部分（下一步大概率从这里开始）

1. **L2 的真实验收标准完全没达成**：`gravitino.conf` 的 `sinks=http` + `authType=apiKey`
   配置组合从未在真实 Gravitino 部署里跑通过（这次只是逐层读 Gravitino 1.3.0 源码
   `/Users/pro/IdeaProjects/gravitino` 核对出配置 key 是对的，没有实机验证）。
2. **四种 Spark catalog 的 `canonical_name` 全部未采样**：Hive / Iceberg / Paimon /
   Doris 专用连接器（`DorisTableCatalog`，注意不是标准 JDBC，是不同代码路径）。
   之前只采样过标准 JDBC 一种格式。这是 L2"提交真实 Spark 作业 → 边正确入库"这条
   验收的卡点，也是 L0 现场核查剩下的阻塞项。
   **2026-07-30 同日追加**：Gravitino 侧的准备工作已完成——在五节点沙箱 ddh-02 的
   Gravitino 上建好了 `paimon_fs`/`paimon_s3`/`doris_catalog` 三个 catalog（均建
   schema+建表验证到底），见 `deploy/deployment-standalone-doris.md` §7.14 与
   `docs/data-lineage-L1-交接-2026-07-30.md` §8c。**但真正的采样还没做**——这三个
   catalog 目前只验证了"能经 Gravitino Java API 建表"，没有一条真实 Spark 写路径
   经过 `openlineage-spark` 监听器。下一步是照抄 L0 JDBC 那次的做法（本机下载
   Spark 3.5.3 scp 到 ddh-02，接 Gravitino Spark connector + openlineage-spark
   console transport）对这三个 catalog 各跑一次真实读写。Hive catalog 仍然完全
   没有环境（阶段 B 依赖的 Hive metastore 未搭，也未部署）。
3. **一个已知的潜在 flaky 点**：`LineageDeadlockRetryMysqlTest`（`@Tag("mysql")`）的
   死锁计数断言在 L1 T8 那批被收紧为 `isEqualTo(1)`（原来是 `>= 1`），这次两批工作都
   没有真实 MySQL 环境去复跑这个 tag，需要下次有真实 MySQL 沙箱时验证是否偶发 >1 次。

## 关键环境命令（已验证在本机可用）

```bash
export JH21=/Users/pro/Library/Java/JavaVirtualMachines/graalvm-jdk-21.0.7/Contents/Home

# 只跑血缘相关测试（含新增的 DDL 结构测试），约 20-30 秒
JAVA_HOME=$JH21 ./mvnw -pl datasophon-api -am test \
  -Dtest='Lineage*,GravitinoDdlLoadTest,Spark3DdlLoadTest' \
  -DfailIfNoSpecifiedTests=false -s ~/.m2/setting.xml

# 改过 Java/pom.xml/docs 后必须跑，否则 spotless check 会拦下构建
JAVA_HOME=$JH21 ./mvnw spotless:apply -s ~/.m2/setting.xml
```

**已知坑**（这次 session 踩过，别重踩）：
- `datasophon-api` 的 `-pl ... -am` 会把 `datasophon-ui-v2` 前端也拉进构建
（因为依赖关系），在本机能跑通（~12 秒），但**在 Codex 的沙箱环境里会卡死/触发网络
断连**——凡是派给 Codex 的验证任务，必须显式告诉它只跑 `-Dtest='Lineage*'` 这种精确
过滤，不要让它自己决定要不要跑全量 `package`/`verify`。
- Codex 这次两批工作都是"写完代码但自己从没编译通过过"就报告完成/失败——**Codex 侧的
"已完成"声明不可信，必须 Claude 侧亲自跑一遍测试**才算数。这次修复的具体编译错误（供
参考，别重复踩）：
- `org.springframework.transaction.TransactionCallback`/`TransactionOperations`
实际包名是 `org.springframework.transaction.support.*`
- `SimpleMeterRegistry` 在当前 Micrometer 版本下不是 `AutoCloseable`，不能用在
`try (...)` 里
- 手写 `JdbcTemplate` 测试桩时，`update(String sql)`（无参）和
`update(String sql, Object... args)` 是两个不同的重载，必须分别覆盖

## 协作方式（这次 session 确认过的既定分工）

用户偏好：**Claude 出计划 + 验收标准 → Codex 实现 → Claude 检查验证**。用
`codex:codex-rescue` 子代理（会自动转发到 Codex CLI 跑后台任务），用
`node "<script>" status <task-id>` / `result <task-id>` 轮询（脚本路径见对话历史，
或直接问 codex:codex-cli-runtime skill）。**不要**在 Codex 报告"完成"后就直接相信，
一定要自己跑一遍测试命令。

## 建议下个 session 调用的 skill

- 如果继续这个 epic 的下一步（找真实环境验证 L2 + 补 L0 采样）：这是基础设施 / 现场核查
  工作，不是纯代码任务，大概率不需要 TDD 类 skill；如果需要委托 Codex 做具体实现，用
  **`codex:rescue`**（沿用这次 session 的分工模式）。
- 如果发现需要先弄清楚 Gravitino/OpenLineage/Spark 某个具体 API 行为且没有实机可测，
  用 **`mattpocock-skills:research`** 把结论落成文档，而不是在对话里口头结论就算了
  （这次 session 就是靠读 Gravitino 本机源码 checkout 才避免了瞎猜配置 key，下次遇到
  类似"没有实机、只能读源码/文档确认"的情况，这个 skill 的方法论适用）。
- 项目本身的 `CLAUDE.local.md` 已经规定了标准工作流（superpowers 管流程、CodeGraph 管
  代码检索），新 session 打开项目会自动加载，不需要额外提醒。
- 如果下一步涉及大量并行探索（比如同时排查 Hive/Iceberg/Paimon/Doris 四种 catalog 各自
  的问题），且用户明确要求并行/多代理，再考虑 Workflow 工具；不要在没被要求的情况下主动
  升级到多代理编排。

