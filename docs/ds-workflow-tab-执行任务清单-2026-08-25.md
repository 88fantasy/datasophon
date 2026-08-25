# DS 工作流可视化 · 执行任务清单（交 Codex 实现）

> 出清单：Claude，2026-08-25。实现：Codex。验收：Claude。
> **设计依据**：`docs/ds-workflow-tab-调度作业可视化-架构设计-2026-08-24.md`（架构）
> 与 `docs/ds-workflow-tab-DS工作流可视化-实施方案-2026-08-25.md`（接口契约与前端规范）。
> 本清单只讲**怎么做、怎么验、怎么记**，设计问题一律回上面两份文档，不在此重开。

---

## 0. 使用规则（**先读完再动手**）

### 0.1 状态回写：每个任务做完立刻改本文件

状态列取值：`⬜ 未开始` / `🔄 进行中` / `✅ 完成` / `⛔ 阻塞` / `⏭ 跳过`。

**每个任务完成后，必须在同一次提交里更新本文件的三处**：

1. 该任务行的**状态**；
2. 该任务行的**证据**列——填**可复现的东西**（测试命令 + 通过数、截图文件名、实测返回值），
   不写「已完成」「OK」这类无信息量的词；
3. 若发现与设计文档不符的事实，在 §6「实施中发现的偏差」追加一行。

**每个 Wave 全部完成后**，在 §5 的 Wave 门禁表里记录门禁验证结果。

> **为什么这条是硬性的**：会话随时可能中断。清单是唯一的交接面——
> 状态没回写，接手的人只能把整个 Wave 重跑一遍。
> **判断「上一轮做到哪」的唯一依据是本文件，不是 git log，也不是记忆。**

### 0.2 中断恢复流程

```
1. 读本文件 §5 门禁表 → 确定最后一个通过门禁的 Wave
2. 读该 Wave 之后所有任务的状态列
3. 状态为 🔄 的任务：先跑它的验证判据，判断是「真没做完」还是「做完了没回写」
4. 从第一个未通过验证的任务继续
```

### 0.3 验证的硬性要求

- **每个任务都有可判真假的验证判据**，不写「代码写完即可」；
- **后端任务**：必须有自动化测试，且在证据列写明命令与通过数；
- **前端任务**：**必须经浏览器实机走查**，不接受「单测通过」代替。
  用 `ego-browser` 技能操作，截图存 `.scratch/ds-workflow-tab/shots/`（该目录已 gitignore），
  证据列写截图文件名 + 一句话观察结果；
- **验证失败不许改判据**：判据不合理时在 §6 记录并说明，不能悄悄放宽。

### 0.4 环境与红线

| 项 | 要求 |
|---|---|
| 后端构建 | `JAVA_HOME` 指向 JDK 21；单类测试用显式模块链 + 跳过前端（见根 `CLAUDE.md` §3.2） |
| 前端 | Node ≥ 22，**npm**（禁 pnpm/yarn），lint 用 **Biome**，提交前 `npm run lint` 必须过 |
| 沙箱验证数据 | **一律合成数据**。不得使用任何真实业务数据 |
| 提交物 | **不得包含**任何口令、令牌、密钥、内网 IP、真实业务表名 |
| `proxy.ts` | `datasophon-ui-v2/config/proxy.ts` 是本机联调改动，**不要提交** |

---

## 1. 并行分组总览

```
Wave 0（平台前置缺陷，2 个任务，可完全并行）
   └─ 门禁 G0 ─┐
                │
Wave 1（后端骨架 + 配置 + 前端骨架，5 个任务，三条线并行）
   └─ 门禁 G1 ─┐
                │
Wave 2（指标绑定 + 前端主体，5 个任务，两条线并行）
   └─ 门禁 G2 ─┐
                │
Wave 3（累计聚合 + 端到端验收，3 个任务）
   └─ 门禁 G3 = 交付
```

**并行原则**：同一 Wave 内的任务**不改同一个文件**。跨 Wave 的依赖已在每个任务的「依赖」列写明。
`service_ddl.json`、`locales/*`、`ServiceInstance/index.tsx` 这类共享文件**只允许一个任务改**，
已在分组时错开。

---

## 2. Wave 0 —— 平台前置缺陷（**阻断一切，先做**）

> 依据架构文档 §2.5。这两条不修，DS 连工作流都建不出来，后面全部无从验证。

| ID | 任务 | 依赖 | 产出 | 验证判据 | 状态 | 证据 |
|---|---|---|---|---|---|---|
| **W0-1** | 修复 DS 分发包缺任务插件 | — | 分发包中补入官方任务插件（至少 shell / spark / flink / flink-stream / sql），三类角色目录（api / master / worker）均需具备 | **全新安装**一套 DS 后，通过 Open API 成功创建一条 SHELL 任务的工作流定义（`code=0`）；服务端日志无 `Cannot find TaskChannel` | ✅ 完成 | `JAVA_HOME=... ./mvnw -s ~/.m2/setting.xml -pl datasophon-common,datasophon-grpc-api,datasophon-ui-v2,datasophon-api -Dskip.installnodenpm -Dskip.npm -Dtest=DsDdlLoadTest,DdlMetaServiceImplTest,ServiceConfigUtilsTest -DfailIfNoTests=false clean test`：7/7；隔离全新安装 `g0-result.json`：create/release/start 均 `code=0`，SHELL=SUCCESS；api/master/worker 各 5 个插件，禁用日志命中 0 |
| **W0-2** | 修复 DS 的对象存储凭据漂移 | — | 从 `DS/service_ddl.json` 侧取值，消除与对象存储服务各存一份的结构 | DS 的 **api / master / worker 逐一重启**后均能正常启动，日志无 `signature does not match`；重新下发配置后凭据仍正确 | ✅ 完成 | 同上 7/7；隔离环境先用历史 Worker 配置复现 `SignatureDoesNotMatch`，统一重新下发 ROOT 投影后 api/master/worker/alert 逐一重启 PASS，`forbiddenLogMatches=0`；临时角色/目录/数据库已清理，共享 DS 健康 200 |

**门禁 G0**：在一套**全新安装**的 DS 上，能创建工作流定义、能上线、能执行到 SUCCESS，
且四类角色都重启过一遍仍正常。

---

## 3. Wave 1 —— 后端骨架 · 配置 · 前端骨架（三条线并行）

### 线 A：后端取数层

| ID | 任务 | 依赖 | 产出 | 验证判据 | 状态 | 证据 |
|---|---|---|---|---|---|---|
| **W1-A1** | `DsApiClient`：端点解析 + token + 响应校验 | G0 | 按 `clusterId` 解析 `ApiServer` 端点、注入 token、统一错误映射 | 单测覆盖四种响应：正常 JSON、**401 且响应体为空**、**200 + HTML**、连接超时。**四种都不得抛出解析异常**，须映射为可读错误 | ✅ 完成 | JDK 21 显式模块链 `clean test`（`DsEndpointResolverTest,DsApiClientTest,DsConfigServiceTest,DsWorkflowServiceTest,DsDdlLoadTest,ServiceInstallServiceImplTest`）：19/19；含四响应与 `EXISTS_ALARM` 端点用例 |
| **W1-A2** | 端点 1/2/3 + VO | W1-A1 | `/v2/ds/projects`、`/workflows`、`/workflows/{code}/instances` | 单测断言：分页信封字段齐全；VO 中**不出现** DS 原始字段名（`totalList`/`dagData` 等）；时间与时长已转换 | ✅ 完成 | 同上 19/19；`DsWorkflowServiceTest` 断言分页信封、稳定 VO、ISO 时间及秒级时长；合成 mock curl：projects=2、workflows=1、instances=1 |
| **W1-A3** | 端点 4：DAG 组装（**暂不含指标**） | W1-A1 | `/v2/ds/instances/{id}/dag`，节点 + 边 + 状态 | 单测断言：**`preTaskCode == 0` 的边被丢弃**；节点状态由 `taskCode` 正确关联；`locations` 为 null 时不报错 | ✅ 完成 | 同上 19/19；`DsWorkflowServiceTest` 覆盖哑元边过滤、taskCode 状态关联及 null locations；合成 mock curl：dagNodes=1 |

> ⚠️ **端点解析不得使用「状态严格等于 RUNNING」的过滤**（架构 §5.1）。
> 单测须包含一个「角色带告警但进程健康」的用例，断言端点仍被判为可用。

### 线 B：服务配置

| ID | 任务 | 依赖 | 产出 | 验证判据 | 状态 | 证据 |
|---|---|---|---|---|---|---|
| **W1-B1** | `DS/service_ddl.json` 新增 `apiToken` | G0 | 参数定义（非必填、默认空） | **新装**集群的配置页出现该参数并可保存 | ✅ 完成 | `jq empty package/raw/meta/datacluster-physical/DS/service_ddl.json`；上述 19/19 中 `DsDdlLoadTest` 3/3，断言默认空、可见且不写入节点配置 |
| **W1-B2** | 现存实例的参数兜底合并 | W1-B1 | 照既有网关配置服务的做法补合并逻辑 | **已安装**的 DS 实例（升级场景）在配置页也能看到该参数——这是最容易漏的一条，须单独验 | ✅ 完成 | 上述 19/19；`DsConfigServiceTest` 验证旧实例 DDL 兜底，`ServiceInstallServiceImplTest` 验证保存平台参数不误标重启、运行配置仍标重启 |

> W1-B1/B2 都改 `service_ddl.json` 与配置服务，**必须串行**，不要与线 A/C 抢同一文件。

### 线 C：前端骨架

| ID | 任务 | 依赖 | 产出 | 验证判据 | 状态 | 证据 |
|---|---|---|---|---|---|---|
| **W1-C1** | Tab 注册 + 项目下拉 + i18n 命名空间 | — | `ServiceInstance/index.tsx` 加 `isDS` 分支；新建 `DsWorkflow/` 目录与 `locales/{zh-CN,en-US}/dsWorkflow.ts` | **浏览器验证**：DS 组件详情页出现「工作流」Tab，非 DS 服务不出现；项目下拉能加载并切换。`npm run lint` 通过 | ✅ 完成 | Vitest 定向 3 文件 19/19；`npm run lint`、`npm run build`、DS 目录 `npx antd lint` 均通过；浏览器截图 `g1-ds-workflow-project-switch.png`（DS Tab 可见且切至合成流项目）、`g1-non-ds-no-workflow-tab.png`（非 DS 无该 Tab） |

**门禁 G1**：三条线各自的验证通过；后端三个端点能被前端 mock 或 curl 调通；
Tab 在浏览器里可见且项目下拉可用。

---

## 4. Wave 2 —— 指标绑定 · 前端主体（两条线并行）

### 线 D：指标绑定（后端）

| ID | 任务 | 依赖 | 产出 | 验证判据 | 状态 | 证据 |
|---|---|---|---|---|---|---|
| **W2-D1** | 批指标绑定 | G1 | 按 `ds-<clusterId>-<taskInstanceId>` 查血缘，填充节点 `metrics` | 沙箱跑一条 Spark 批任务（行数**预先可算**），节点返回的各输出表行数与真实写入**逐值一致** | ✅ 完成 | JDK 21 显式模块链定向测试 22/22；沙箱合成 SPARK 实例 6 SUCCESS，`ds-1-6` 聚合 7 个 run，两个输出逐值为 700/234 行且容量为 6346/2733 B |
| **W2-D2** | 流指标绑定 | G1 | 按作业名前缀匹配取 `job_id` 并计算速率 | 沙箱跑一条 Flink 流作业，节点返回的 `jobId` 与引擎 REST 的作业 id **一致**，速率非零且 `approximate=true` | ✅ 完成 | 同上 22/22；沙箱合成实例 9（SHELL + `taskExecuteType=STREAM`）命名 `ds-1-9-codex-w2-stream`，Doris `job_id` 与 Flink REST 精确一致，完整分钟桶速率 153/350 row/s，返回 `approximate=true` |
| **W2-D3** | 并发 / 超时 / 单点失败隔离 | W2-D1, W2-D2 | 有界并发、三档超时、单节点失败不影响整图 | 单测：让**一个**节点的指标查询抛异常，断言其余节点数据完整且该节点 `metricsError` 有值 | ✅ 完成 | `DsWorkflowServiceTest` 覆盖单节点异常隔离：批节点保留完整指标、失败流节点为 `LOOKUP_FAILED`；专用 8 线程/64 队列与单节点 3s 超时；定向测试 22/22 |

> **批流判定用 `taskType`，禁止使用 `flowType`**（恒为固定字面量的假字段）。
> **`appLink` 一律不读**（实测恒 null）。

### 线 E：前端主体

| ID | 任务 | 依赖 | 产出 | 验证判据 | 状态 | 证据 |
|---|---|---|---|---|---|---|
| **W2-E1** | 树表：主行 + 子行懒加载 | G1 | ProTable + expandable | **浏览器验证**：未展开时**不发**子行请求（看 Network）；展开后加载该定义的最近实例；主行显示上线状态标记 | ✅ 完成 | ego-browser Network：展开前实例请求 0，展开后仅发 `/workflows/800001/instances?...limit=10`；主行显示「已上线」，项目切换刷新定义；前端定向 5 文件 23/23 |
| **W2-E2** | Drawer + G6 DAG + 15 秒轮询 | W2-E1 | 全屏 Drawer、G6 v5 图、轮询启停 | **浏览器验证**：仅**实例行**可点开；图能正常渲染且**无幽灵起点节点**；打开后每 15 秒有一次请求、**关闭后请求停止**（Network 面板确认） | ✅ 完成 | ego-browser：主行点击不打开、实例行打开 Drawer；16s 内 DAG 请求共 2 次，关闭后再等 16s 仍为 2；画布节点 id 不含 `0`；`DsDagDrawer` fake timer 测试通过 |
| **W2-E3** | 节点视觉 + 格式化 | W2-E2, W2-D1, W2-D2 | 密度卡节点、批按表分列、流显速率 | **浏览器验证 + 截图**：批节点按输出表分列（超过 2 张折叠）、流节点显示速率**并标注约值**、无指标节点显示 `—`。**`0 row/s` 与 `—` 必须视觉可区分** | ✅ 完成 | `g2-batch-dag.png`：700/234 两输出 + `+1`、未绑定 `—`；`g2-stream-dag.png`：约 22.8 row/s；单测断言 `0.0 row/s` 与 `—` 分离。前端 23/23、lint、antd lint、build 通过 |

**门禁 G2**：在浏览器里从 Tab 一路点到 DAG，批节点显示正确行数、流节点显示速率，
关掉 Drawer 后轮询停止。截图存档。

---

## 5. Wave 3 —— 累计聚合 · 端到端验收

| ID | 任务 | 依赖 | 产出 | 验证判据 | 状态 | 证据 |
|---|---|---|---|---|---|---|
| **W3-1** | 流作业累计量后台聚合 | G2 | 累计表 + 定时任务（幂等键 = 作业 id + 周期） | 单测：**同一周期重复执行不重复累加**；**重启后从游标继续**。浏览器验证：流节点出现「已处理约 N 条」并标注约值 | ✅ 完成 | 新增游标累计表 + 周期去重账本（迁移 2.3.1），事务内周期去重与游标 CAS；JDK 21 定向后端测试 29/29（含 H2 真实 SQL 的重复周期/重启续跑）；`g3-stream-processed-approx.png` 显示「已处理约 1,234,567 条」 |
| **W3-2** | 端到端验收：批场景 | G2 | 按实施方案 §7.1 走查 | 逐表核对行数；**再跑一次后，旧实例仍显示它自己那一次的量** | ✅ 完成 | 合成 SPARK 工作流二次运行：新任务实例 10 SUCCESS；`ds-1-6` 与 `ds-1-10` 分别独立绑定 7 个 run，旧/新实例输出均逐表保持 700/234 行与 6346/2733 B；两次运行落在不同 Worker |
| **W3-3** | 端到端验收：流场景 + 降级 | G2 | 按实施方案 §7.2 / §7.3 走查 | 流速率刷新正常；**四类降级**（未配置令牌 / 令牌失效 / DS 不可达 / 未按约定接入）各自的提示可区分，且**都不是 500** | ⛔ 阻塞 | 前端定向测试 5 文件 10/10；`g3-degrade-{token-missing,token-invalid,ds-unavailable,not-bound}.png` 四类提示均可区分、带重试/接入引导且无 500。实机 Collector 仍因 `json: unsupported value: NaN` 持续丢批，流指标最新样本滞后约 24 分钟，无法诚实完成 15 秒实时刷新复验 |

**门禁 G3（交付）**：架构文档 §8 的八条验收要点逐条通过，其中第 8 条（浏览器实机走查）
须有截图存档。

---

## 5.1 Wave 门禁记录表（**每过一个门禁填一行**）

| 门禁 | 日期 | 验证人 | 结果 | 备注 |
|---|---|---|---|---|
| G0 | 2026-08-25 | Codex | ✅ | 合成 SHELL 数据；隔离全新安装创建→上线→运行 SUCCESS，四角色重启通过；`.scratch/ds-workflow-tab/g0-{result,role-restart}.json` |
| G1 | 2026-08-25 | Codex | ✅ | 全部使用合成数据；后端 19/19、前端 19/19、lint/build 通过；mock curl 四端点通过；DS/非 DS 浏览器走查与项目切换截图已存档 |
| G2 | 2026-08-25 | Codex | ✅ | 合成数据；后端 22/22、前端 23/23、lint/build 通过；批 700/234 与流 JobID/非零速率实机核对；懒加载、15s 轮询启停及批/流 DAG 截图存档 |
| G3 | 2026-08-25 | Codex | ⛔ | 累计幂等/续跑、批任务二次运行历史隔离、不同 Worker 与四类降级浏览器走查均通过；共享 Collector exporter 持续丢弃数据，流速率 15 秒实机刷新未通过，门禁不放行 |

---

## 6. 实施中发现的偏差（**发现即追加，不要攒**）

> 与设计文档不符的事实、被否证的假设、判据不合理之处，都记在这里。
> 格式：`日期 · 任务 ID · 现象 · 影响 · 处置`。

| 日期 | 任务 | 现象 | 影响 | 处置 |
|---|---|---|---|---|
| 2026-08-25 | W2-D2 | DS 3.4.1 原生 `FLINK_STREAM` 插件不会替换 SQL `rawScript` 内的 `${system.task.instance.id}`，作业名保留字面量 | 原生任务无法按约定前缀绑定；已产生的错误命名合成 Job 已精确取消 | 沙箱改用 Shell→SQL Client 且任务定义标记 `taskExecuteType=STREAM`；后端优先据此判流，`FLINK*` taskType 仍作为兼容路径 |
| 2026-08-25 | W2-D2 | 实测期间 OTel Collector 在 15:11 后持续出现 exporter failure，Doris 新鲜指标停止推进，但本次合成 Job 的 15:09–15:11 样本已完整落库 | 后续实时刷新验收会受共享环境故障影响；不能通过扩大查询窗口伪装为健康 | 不重启共享 Collector；W2 用已落库的同一 JobID 样本核对两个完整分钟桶，W3 实时刷新待环境恢复后复验 |
| 2026-08-25 | W3-1 | DS `appLink` 恒空且 YARN/Standalone 的 JobManager REST 端点没有可从任务实例稳定反查的契约 | 无法按设计直接读取 Flink REST `start-time`；累计量本身仍只能从首个可用 delta 开始重建 | `since` 与初始游标改取 Doris 中该 JobID 的首个 OTel 样本并明确为 first observed；不伪造引擎启动时间，后续若补齐 JobManager 端点契约再切回 REST |
| 2026-08-25 | W3-3 | Collector exporter 的实际根因为 `json: unsupported value: NaN`，持续重试后 `Dropping data`；指标最新样本实测滞后约 24 分钟 | 实时流速率与累计游标都无法获得新鲜 delta，15 秒轮询只能重复旧值 | 遵守共享环境红线，不重启/改配置；四类降级已完成，W3-3 与 G3 保持阻塞，待 Collector 恢复后只需复验流刷新 |

---

## 7. 已知地雷速查（动手前扫一眼）

实施方案 §8 有完整的 20 条，这里只挑**最容易在本清单各任务上踩到**的：

| 会踩在哪 | 地雷 |
|---|---|
| W1-A1 | DS 的 401 **响应体是空的**，直接解析会把 401 变成 500；改名前的旧路径返回 **200 + HTML** 而非 404 |
| W1-A1 | 端点解析**不得**用严格 `RUNNING` 过滤——ApiServer 长期带告警是常态，既有实现曾因此造成全平台查询失败 |
| W1-A3 | `preTaskCode == 0` 是哑元边，不丢会多出幽灵节点 |
| W1-B2 | DDL 新增参数**现存实例默认看不到**，必须补兜底合并 |
| W2-D1 | 一次任务对应**多个** run，统计只落在写入语句那几个上，而应用级 run 的事件序号最大——**不能「取最新一个」** |
| W2-D1/D2 | 标识必须带 `ds-<clusterId>-` 前缀，裸数字跨集群会撞 |
| W2-D2 | 流作业的 source/sink 被算子链合并时速率**恒为 0**，这与「未绑定」是两回事 |
| W2-E2 | Drawer 关闭必须清定时器，否则后台持续轮询 |
| W2-E3 | 速率是**近似值**，必须标注；行数千分位、容量走既有格式化工具 |
| 任何前端任务 | 前端 mock 路由的 `pathname` 必须含 `/ddh`，否则 basename 类缺陷会全绿逃逸 |
| 任何后端集成测试 | 新增 `@SpringBootTest` 须加 `@DirtiesContext`，否则与其它上下文抢 gRPC 端口，**报错表象常被误判为数据库连接问题** |
| 沙箱操作 | `ssh A 'cat 密钥' \| ssh B 'bash -s' <<'EOF'` 是**坏的**——`bash -s` 的 stdin 被 heredoc 占用，管道值被丢弃，`read` 读到脚本自己的下一行。先落成远端文件再读 |
