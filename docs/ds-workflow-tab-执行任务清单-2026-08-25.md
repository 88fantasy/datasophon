# DS 工作流可视化 · 执行任务清单（交 Codex 实现）

> 出清单：Claude，2026-08-25。实现：Codex。验收：Claude。
> **设计依据**：`docs/ds-workflow-tab-调度作业可视化-架构设计-2026-08-24.md`（架构）
> 与 `docs/ds-workflow-tab-DS工作流可视化-实施方案-2026-08-25.md`（接口契约与前端规范）。
> 本清单只讲**怎么做、怎么验、怎么记**，设计问题一律回上面两份文档，不在此重开。

---

## 0. 使用规则（**先读完再动手**）

### 0.1 状态回写：每个任务做完立刻改本文件

状态列取值：`⬜ 未开始` / `🔄 进行中` / `✅ 完成` / `🟡 待复验` / `⛔ 阻塞` / `⏭ 跳过`。
`🟡 待复验` 指实现与单测均已完成、判据也基本达成，仅剩需要特定环境条件才能做的实机复核；它与 `⛔ 阻塞` 的区别是**已无阻塞因素，只差一次执行**。

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
| **W1-B2** | 现存实例的参数兜底合并 | W1-B1 | 照既有网关配置服务的做法补合并逻辑 | **已安装**的 DS 实例（升级场景）在配置页也能看到该参数——这是最容易漏的一条，须单独验 | ⛔ 阻塞 | 上述 19/19；`DsConfigServiceTest` 验证旧实例 DDL 兜底，`ServiceInstallServiceImplTest` 验证保存平台参数不误标重启、运行配置仍标重启。**2026-08-25 Claude 实机验证：单测全过但真实升级场景不生效**——已装实例配置页仍只有 33 项，`apiToken` 不出现。真因是新 DDL 未上传沙箱 Nexus（详见障碍记录）。手工补传 + 重启后配置项 33→34、参数正常出现，但**代码之外缺「DDL 变更如何进入元数据存储」的交付定义，该条退回** |

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
| **W2-D2** | 流指标绑定 | G1 | 按作业名前缀匹配取 `job_id` 并计算速率 | 沙箱跑一条 Flink 流作业，节点返回的 `jobId` 与引擎 REST 的作业 id **一致**，速率非零且 `approximate=true` | ✅ 完成 | 同上 22/22；沙箱合成实例 9（SHELL + `taskExecuteType=STREAM`）命名 `ds-1-9-codex-w2-stream`，Doris `job_id` 与 Flink REST 精确一致，完整分钟桶速率 153/350 row/s，返回 `approximate=true`；复审回归覆盖双 reporter 只取 OTLP delta、完整分钟右边界排除与 FLINK 批任务不误判 |
| **W2-D3** | 并发 / 超时 / 单点失败隔离 | W2-D1, W2-D2 | 有界并发、三档超时、单节点失败不影响整图 | 单测：让**一个**节点的指标查询抛异常，断言其余节点数据完整且该节点 `metricsError` 有值 | ✅ 完成 | `DsWorkflowServiceTest` 覆盖单节点异常隔离：批节点保留完整指标、失败流节点为 `LOOKUP_FAILED`；专用 8 线程/64 队列与单节点 3s 超时；定向测试 22/22 |

> **批流判定用 `taskType`，禁止使用 `flowType`**（恒为固定字面量的假字段）。
> **`appLink` 一律不读**（实测恒 null）。

### 线 E：前端主体

| ID | 任务 | 依赖 | 产出 | 验证判据 | 状态 | 证据 |
|---|---|---|---|---|---|---|
| **W2-E1** | 树表：主行 + 子行懒加载 | G1 | ProTable + expandable | **浏览器验证**：未展开时**不发**子行请求（看 Network）；展开后加载该定义的最近实例；主行显示上线状态标记 | ✅ 完成 | ego-browser Network：展开前实例请求 0，展开后仅发 `/workflows/800001/instances?...limit=10`；主行显示「已上线」，项目切换刷新定义；复审补齐 DS 原生 Web UI 入口，截图 `review-ds-link.png`；前端定向 6 文件 29/29。**2026-08-26 Claude 真实部署走查复验通过**：钩住 `fetch`/`XHR` 计数（`performance` 缓冲区 250 条已被监控页占满，会漏记，不可用作判据）——停留在 Tab 静置不展开 = `/instances` 请求 0 次；展开 1 行 = 恰好 1 次 `/v2/ds/workflows/182469955397664/instances?clusterId=1&projectCode=…&limit=10`。主行展示上线状态徽标；列表按服务端分页结果原样展示，不做会破坏 `total` 语义的客户端筛选。截图 `w51-01-workflow-tab-list.png` |
| **W2-E2** | Drawer + G6 DAG + 15 秒轮询 | W2-E1 | 全屏 Drawer、G6 v5 图、轮询启停 | **浏览器验证**：仅**实例行**可点开；图能正常渲染且**无幽灵起点节点**；打开后每 15 秒有一次请求、**关闭后请求停止**（Network 面板确认） | ✅ 完成 | ego-browser：主行点击不打开、实例行打开 Drawer；16s 内 DAG 请求共 2 次，关闭后再等 16s 仍为 2；画布节点 id 不含 `0`；`DsDagDrawer` fake timer 测试通过。**2026-08-26 Claude 真实部署走查复验通过**：点主行 Drawer 数 0；点实例行打开 Drawer 且标题=实例名；打开期间 48 秒内 `/v2/ds/instances/10/dag` 共 3 次，相邻间隔 **15.0s / 14.9s**；关闭后 Drawer open 数归 0，再观察 48 秒 `/dag` 请求 **0 次** |
| **W2-E3** | 节点视觉 + 格式化 | W2-E2, W2-D1, W2-D2 | 密度卡节点、批按表分列、流显速率 | **浏览器验证 + 截图**：批节点按输出表分列（超过 2 张折叠）、流节点显示速率**并标注约值**、无指标节点显示 `—`。**`0 row/s` 与 `—` 必须视觉可区分** | ✅ 完成 | `g2-batch-dag.png`：700/234 两输出 + `+1`、未绑定 `—`；`g2-stream-dag.png`：约 22.8 row/s；单测断言 `0.0 row/s` 与 `—` 分离。前端 23/23、lint、antd lint、build 通过。**2026-08-26 Claude 真实部署走查**：批节点逐值复核通过——`codex_w2_src 700 行 / 6.2 KB`、`codex_w2_dst 234 行 / 2.7 KB`，与 §3.2 记录的 700/6346 B、234/2733 B 一致，无幽灵起点节点，截图 `w51-02-batch-dag-700-234.png`。⚠️ **流速率的实机部分未能复验**：沙箱所有 `ds-` 前缀 Flink 作业均已 cancel，现存流节点一律落在 `NOT_BOUND`（见偏差表新增条目），需 §5.2 起作业后才能验「速率 + 约值标注」与 `0 row/s` 对比 |

**门禁 G2**：在浏览器里从 Tab 一路点到 DAG，批节点显示正确行数、流节点显示速率，
关掉 Drawer 后轮询停止。截图存档。

---

## 5. Wave 3 —— 累计聚合 · 端到端验收

| ID | 任务 | 依赖 | 产出 | 验证判据 | 状态 | 证据 |
|---|---|---|---|---|---|---|
| **W3-1** | 流作业累计量后台聚合 | G2 | 累计表 + 定时任务（幂等键 = 作业 id + 周期） | 单测：**同一周期重复执行不重复累加**；**重启后从游标继续**。浏览器验证：流节点出现「已处理约 N 条」并标注约值 | ✅ 完成 | 新增游标累计表 + 周期去重账本（迁移 2.3.1），事务内周期去重与游标 CAS；复审后空窗口不伪造零值而是刷新调度次序让出名额，pending 按最久未处理顺序公平调度，未追平完整分钟时不暴露部分累计量；JDK 21 相关后端测试 51/51；`g3-stream-processed-approx.png` 显示「已处理约 1,234,567 条」 |
| **W3-2** | 端到端验收：批场景 | G2 | 按实施方案 §7.1 走查 | 逐表核对行数；**再跑一次后，旧实例仍显示它自己那一次的量** | ✅ 完成 | 合成 SPARK 工作流二次运行：新任务实例 10 SUCCESS；`ds-1-6` 与 `ds-1-10` 分别独立绑定 7 个 run，旧/新实例输出均逐表保持 700/234 行与 6346/2733 B；两次运行落在不同 Worker。**2026-08-26 补充（Claude，方案见 `docs/ds71-lakehouse-Spark批实现与批链路验证-实施方案-2026-08-26.md`）**：此前的合成用例只有 1 节点 0 边，多节点 DAG 渲染、`preTaskCode==0` 哑元边过滤、扇入/扇出、每节点独立绑定血缘、二次运行历史隔离等一整类判据从未被真实数据触达。用《实时湖仓技术方案》§7.1 改写的 Spark 批实现（MySQL `order_dw` → Doris `ds71`，7 节点 6 边，3→1 扇入 + 1→2 扇出）补上：DAG 渲染 7 节点、无幽灵起点节点；节点行数与手算真值逐值一致（7/7/5/7/6/3/4）；追加 3 笔订单重跑后新实例更新为 10/10/5/10/9/3/4，**旧实例复核仍精确冻结在 7/7/5/7/6/3/4**。截图 `ds71-{7node-dag-rowcounts,instance12-frozen-oldvalues,instance13-rerun-newvalues,workflow-list}-2026-08-26.png` |
| **W3-3** | 端到端验收：流场景 + 降级 | G2 | 按实施方案 §7.2 / §7.3 走查 | 流速率刷新正常；**四类降级**（未配置令牌 / 令牌失效 / DS 不可达 / 未按约定接入）各自的提示可区分，且**都不是 500** | 🟡 待复验 | 前端定向测试 5 文件 10/10；`g3-degrade-{token-missing,token-invalid,ds-unavailable,not-bound}.png` 四类提示均可区分、带重试/接入引导且无 500。实机 Collector 曾因 `json: unsupported value: NaN` 持续丢批，流指标滞后约 24 分钟，当时无法完成 15 秒实时刷新复验。**2026-08-25 16:45 Claude 复核：阻塞原因已解除**——Doris 三张表 lag 均为 0，最后一条 NaN 报错停在 15:53:51。根因见下方障碍记录，模板侧已修。**仅剩「起一条带 `ds-` 前缀的流作业跑一次 15 秒刷新」未做**，其余判据已全部达成。**2026-08-26 Claude 真实部署走查**：四类降级中「未按约定接入」已在真实环境观察到并截图 （`w51-03-stream-ended-notbound.png`）；另外三类（未配置令牌 / 令牌失效 / DS 不可达）需要改动沙箱 DS 服务配置或停服务才能触发，属破坏性操作，**未在真实环境复验**，仍以 Codex 的 mock 截图为准。错误路径抽查：不存在的实例 / 不存在的 projectCode 均为 HTTP 502 + 可读的上游原文（非 500），符合判据 |

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
| G3（复验） | 2026-08-26 | Claude | ⛔ | **真实部署走查**（非 mock、非 dev server）：W2-E1 懒加载、W2-E2 15 秒轮询启停、W2-E3 批节点 700/234 逐值、上线状态徽标、`apiToken` 配置项 **全部通过**，截图 `w51-0{1,2,3}-*.png`。**仍不放行**，两个缺口：① W3-3 的流速率 15 秒刷新未做（需起流作业，代价见 §5.2）；② 走查中新发现「已结束流作业被误报为未按约定接入」，属 W2-E3/W3-3 判据范围内的文案缺陷 |

---

## 5.2 复审修复记录

| 范围 | 修复 | 验证证据 |
|---|---|---|
| 后端分层 | Controller 改为依赖 `service/ds` 接口，实现迁入 `service/impl/ds`；批、流指标拆为独立 provider，调度服务只负责分派 | JDK 21 相关后端测试 51/51；Checkstyle 与 Spotless 通过 |
| 指标准确性 | 双 reporter 不再相加，优先使用 OTLP delta；查询只取完整分钟；仅 `taskExecuteType=STREAM` 或 `FLINK_STREAM` 判为流任务 | `DsTaskMetricsServiceTest`、`DsWorkflowServiceTest` 回归通过 |
| 累计诚实性 | 空样本周期不记零、不推进，只刷新调度次序；`LIMIT 64` 按最久未处理顺序轮转；游标未追平最新完整分钟时只返回 `since`，不返回部分 `processedApprox` | `DsStreamMetricAccumulatorTest` 4/4，`DsTaskMetricsServiceTest` 4/4 |
| 前端契约 | service/test/mock 收拢到 `DsWorkflow/`；展示发布状态徽标并增加 DS 原生 Web UI 外链；列表不做破坏服务端分页语义的客户端筛选 | Vitest 6 文件 29/29，`npm run lint`、Ant Design lint、生产构建通过；`review-ds-link.png` |

---

## 6. 实施中发现的偏差（**发现即追加，不要攒**）

> 与设计文档不符的事实、被否证的假设、判据不合理之处，都记在这里。
> 格式：`日期 · 任务 ID · 现象 · 影响 · 处置`。

| 日期 | 任务 | 现象 | 影响 | 处置 |
|---|---|---|---|---|
| 2026-08-25 | W2-D2 | DS 3.4.1 原生 `FLINK_STREAM` 插件不会替换 SQL `rawScript` 内的 `${system.task.instance.id}`，作业名保留字面量 | 原生任务无法按约定前缀绑定；已产生的错误命名合成 Job 已精确取消 | 沙箱改用 Shell→SQL Client 且任务定义标记 `taskExecuteType=STREAM`；后端仅以 `taskExecuteType=STREAM` 或明确的 `FLINK_STREAM` taskType 判流，普通 FLINK 批任务保持批路径 |
| 2026-08-25 | W2-D2 | 实测期间 OTel Collector 在 15:11 后持续出现 exporter failure，Doris 新鲜指标停止推进，但本次合成 Job 的 15:09–15:11 样本已完整落库 | 后续实时刷新验收会受共享环境故障影响；不能通过扩大查询窗口伪装为健康 | 不重启共享 Collector；W2 用已落库的同一 JobID 样本核对两个完整分钟桶，W3 实时刷新待环境恢复后复验 |
| 2026-08-25 | W3-1 | DS `appLink` 恒空且 YARN/Standalone 的 JobManager REST 端点没有可从任务实例稳定反查的契约 | 无法按设计直接读取 Flink REST `start-time`；累计量本身仍只能从首个可用 delta 开始重建 | `since` 与初始游标改取 Doris 中该 JobID 的首个 OTel 样本并明确为 first observed；不伪造引擎启动时间，后续若补齐 JobManager 端点契约再切回 REST |
| 2026-08-25 | W3-3 | Collector exporter 的实际根因为 `json: unsupported value: NaN`，持续重试后 `Dropping data`；指标最新样本实测滞后约 24 分钟 | 实时流速率与累计游标都无法获得新鲜 delta，15 秒轮询只能重复旧值 | 遵守共享环境红线，不重启/改配置；四类降级已完成，W3-3 与 G3 保持阻塞，待 Collector 恢复后只需复验流刷新 |
| 2026-08-25 | W3-3 | **阻塞原因已解除**（Claude 复核）：Doris `otel_metrics_sum` / `otel_metrics_gauge` / Flink `numRecordsIn` 三者 lag 均为 0，最后一条 NaN 报错停在 15:53:51 | W3-3 可复验；但缺陷本身是间歇性的，会复发 | 未重启任何共享组件，仅做只读核查 |
| 2026-08-25 | W3-3 | **NaN 故障源定位到 `ddh-02` 的 Collector**（非 ddh-01）：1593 次 `unsupported value: NaN` + 124 次 `Dropping data`，全部集中在 `doris` exporter 的 `metrics` 信号；今日分三波（10 时 109 / 11 时 233 / 15 时 1189） | 一个坏数据点使整批 8192 条一起被丢，表现为「所有指标同时间歇性缺样本」，极易误判为网络抖动 | 见下一行的模板级修复 |
| 2026-08-25 | W3-3 | 配置里已有两条 NaN 过滤器（`drop_empty_summary` / `drop_zk_decaying_summary`），但注释自承：Summary 被观测过之后再因滑动窗口衰减出 NaN 的情况**覆盖不到**，且 OTTL 读不了 `quantile_values`，只能逐个硬编码指标名 | 打地鼠模式：每个新服务引入的 Summary 都可能再次打挂整条 pipeline，且只在故障后才被发现 | **改为隔离爆炸半径**：`OTELCOLLECTOR/templates/otelcol.ftl` 把 Summary 拆到独立 `metrics/summary` pipeline + 独立 `doris/summary` exporter 实例（独立 sending_queue 与 consumer），NaN 只能炸掉 Summary 自身批次，炸不到 Sum/Gauge。新增 `filter/drop_summary` 与 `filter/keep_summary_only` 两条互补过滤器保证不重不漏。`OtelcolTemplateTest` 13/13 通过（含新增的隔离结构断言）。**2026-08-25 已下发沙箱并验证**：改动前后各 2 分钟窗口，summary 行数 1356→1356 完全一致，sum/gauge 各 +0.1%，全键重复行前后均为 0 —— 隔离没有丢数也没有重复。⚠️ **但归因写错了，此处更正**：提交 `7c8fff56` 的信息称 NaN 来自 Summary 滑动窗口衰减，拆分后的实测**否证了它**——126 次 NaN 全部归属主 `doris` exporter，`doris/summary` 0 次，说明 NaN 来自 **Sum 或 Gauge**，不是 Summary。修复本身有效（隔离确实在起作用，整表 lag 始终正常），但它防的不是当前这个源；真正对症的修复仍待 §5.3 定位具体指标 |
| 2026-08-25 | — | 顺带闭合一条历史遗留：`ddh-02` Collector 进程启动于 `Fri Aug 14 18:44:21`，与此前测得的 8-14 丢数窗口（17:02→**18:44**）结束时刻吻合 | 该窗口此前记为「根因未定位」 | 认定为该 Collector 不可用期。**保留不确定性**：当时测得的是 22% 部分丢失而非全丢，且 18:44 之前的日志已随进程重启被覆盖，完整机制缺证据 |
| 2026-08-25 | 部署 | **只换 `datasophon-api` jar 起不来**：`NoClassDefFoundError: com/datasophon/common/k8s/config/K8sClientConfig` | 沙箱 API 停机约 3 分钟 | **api 与 common 两个 jar 必须成对部署**。本分支自 Aug 16 起有 17 个 api 提交,含整个 K8s 接管 epic,common 侧新增了类。清单原先没有这一条 |
| 2026-08-25 | **W1-B2** | **单测通过但真实升级场景不生效**：已装 DS 实例的配置页仍只有 33 项,`apiToken` 不出现 | 批链路全链阻断——`/v2/ds/projects` 直接报「请在 DS 服务配置中填写 apiToken」 | 真因不在代码:`mergeDdlFallback` 读的是库里的 DDL(由 `LoadServiceMeta` 从 Nexus 灌入),而**新 DDL 从未上传到沙箱 Nexus**。单测里的 DDL 是构造出来的,必然含新参数,所以测不出「DDL 从哪来」这个前提。**该条应退回:缺的是「DDL 变更如何进入元数据存储」的交付定义**,否则每次新增 DDL 参数都会重演 |
| 2026-08-25 | 部署 | DDL 传上 Nexus 后参数**仍不可见** | 需多一次重启 | `getServiceConfigFromDdl` 读 `t_ddh_frame_service` 表,不直连 meta 存储;**必须重启 API 让 `LoadServiceMeta` 重新灌库**。上传 ≠ 生效 |
| 2026-08-25 | 部署 | 保存服务配置返回 **403 且响应体为空** | 误以为是 K8s 只读封锁(实际集群是 MANAGED,切面直接放行) | `CsrfTokenInterceptor` 要求 POST 回带 `X-XSRF-TOKEN` 头,值在登录时以同名 Cookie 下发。另一条出口:带 `token` 头走 API token 认证可跳过 CSRF |
| 2026-08-25 | 部署 | 前端产物直接铺进 `static/` 后**首页 200 但所有 JS 404** | 页面白屏 | 生产 `publicPath = /ddh/static/`,产物结构必须是 `static/index.html` + `static/static/<assets>`,不能把 dist 平铺。另:macOS `tar czf` 会带出 216 个 `._*` AppleDouble 垃圾文件,解压后需 `find -name '._*' -delete` |
| 2026-08-25 | **W3-2** | **批链路已用真实部署实机验证通过** | — | 实例 6 与 10 各返回 `runCount=7`、两条输出 700/234 行(6346/2733 字节),分别绑定各自的 warehouse 路径互不覆盖。`runCount=7` 直接证明按标识聚合了全部 run 而非「取最新一个」(T7 返工的核心教训) |

| 2026-08-26 | **W2-E3 / W3-3** | **已结束的流作业被误报为「未按平台约定接入指标」**。实例 9（`codex_w2_stream_shell_1787641760`，W2-D2 当初实测过 153/350 row/s）现在返回 `metrics:null, metricsError:"NOT_BOUND"`，节点渲染成「— 任务尚未按平台约定接入指标」 | 文案把「作业已结束」归因成「你没按命名约定接入」，用户会去翻规范，实际什么都不用改。批路径没有这个问题——血缘是持久化的，实例 10 关停数小时后仍正确显示 700/234 | 根因：`DsStreamMetricsProvider:75` 用 `sampledAt = clock.instant()` 做**瞬时**查询发现 job，作业一结束候选集即为空 → `NotBoundException` → `NOT_BOUND`。`metricsError` 的取值只有 `NOT_BOUND` / `LOOKUP_FAILED` 两种，无法表达「曾绑定、已结束」。**建议新增一类状态**（如 `JOB_ENDED`：回看历史窗口若曾有样本则判定为已结束），或至少把文案改成不做接入归因的中性表述 |
| 2026-08-26 | W2-E1 | 行操作列每行都渲染「打开 DS」，但 8 行的 `href` **完全相同**，均为 `http://<ds>:12345/dolphinscheduler/ui`，不带 projectCode / workflowCode | 放在「操作」列会让人以为是「打开这一行的工作流」，点进去落在 DS 首页 | `DsWorkflow/index.tsx:101` 的 `render: () => …` 直接忽略行参数。要么做成深链（带 projectCode + workflowCode），要么把它从行操作提到表格外作为单一入口。当前实现与复审记录的「DS 原生 Web UI 入口」一致，属设计取舍，但摆放位置有误导性 |
| 2026-08-26 | 走查方法 | `performance.getEntriesByType('resource')` **不能用作请求计数判据**：默认缓冲上限 250 条，被监控面板的指标请求占满后新条目被静默丢弃 | 我第一次测子行懒加载时读到「展开后 0 次 `/instances`」，**看起来像功能没发请求，实际是量具失真** | 改用钩住 `window.fetch` + `XMLHttpRequest.prototype.open` 自行记录。后续任何「数请求次数」的判据都应这样做 |
| 2026-08-26 | —（平台既有，非本分支引入） | 停留在「工作流」Tab 静置 40 秒，仍发出 186 条 observability 指标请求 + 13 条 `service/instance/list` | 切走的「监控」面板在后台持续轮询 | **对照组已排除本分支嫌疑**：APISIX 服务停在「实例」Tab 同样 40 秒发 13 条 `service/instance/list` + 30 条指标请求，两者 `service/instance/list` 完全一致，指标条数差只反映各自面板数量。根因是 antd Tabs 默认保留已渲染面板（未开 `destroyInactiveTabPane`）。**本清单不处理，另立条目** |
| 2026-08-26 | —（平台既有，非本分支引入） | 非法数字型查询参数返回 **HTTP 500** 并回显 `Failed to convert value of type 'java.lang.String' to required type 'long'` 这类内部信息 | 违反 `.claude/rules/springboot.md` 的「错误一律走 `ProblemDetail`，不要返回裸串」 | **对照组已排除本分支嫌疑**：`/v2/observability/otel/metrics/query?time=notanumber` 与 `/v2/cluster/notanumber/service/instance/list` 表现完全相同，全局 `@ControllerAdvice` 缺 `MethodArgumentTypeMismatchException` 处理。**本清单不处理，另立条目** |
| 2026-08-26 | —（平台既有，非本分支引入） | ds71 多节点验证顺带发现：DS 工作流「实例」列表里「开始时间」比沙箱节点真实系统时间**超前约 8 小时**（节点系统时钟本身经核实是准的：UTC 与 CST 换算吻合）。`ps aux` 确认 DS master/api 进程启动参数为 `-Duser.timezone=${SPRING_JACKSON_TIME_ZONE}`，该环境变量在整个 DS 安装目录（含 `bin/env/`）里从未被赋值，JVM 拿到未展开的字面量占位符，时区解析失败退回 UTC，叠加 MySQL 会话时区读写时间戳产生的换算，显示值多偏移了一个时区量级 | 「工作流」Tab 展示的实例开始时间/结束时间列不可信，容易误导用户判断作业实际触发时刻；不影响行数/DAG 判据（数据内容与显示时间戳无关） | **对照组已排除本分支嫌疑**：这是 DS 自身部署配置缺口（`SPRING_JACKSON_TIME_ZONE` 环境变量），不是 datasophon-api/ui-v2 代码问题。**本清单不处理，另立条目**（需在部署脚本/`dolphinscheduler_env.sh` 里显式设置该变量，如 `Asia/Shanghai`） |
| 2026-08-26 | W2-E3 | **用户实机复现**：DAG Drawer 打开后调整/最大化浏览器窗口，节点会叠在一起、画布填不满 Drawer（截图两张）。**排查过程**：①最初怀疑 `DsDagGraph.tsx` 缺少 `useFillViewportHeight`（对照 `LineageGraph.tsx`/`TopologyTab.tsx` 都用了这个 hook 显式调 `graph.resize()`），补上后本地测试/lint/tsc 全过，但**真实浏览器复测仍未解决**，且发现 `fitView({when:'overflow'})` 本身是"容器变大不强制填满"的预期设计，不是这个 hook 能改变的行为；②用户直接指出根因方向——**改用整页跳转，参考"数据血缘"的 `/lineage` → `/lineage/:nodeId` 模式**，不再用 Drawer（Drawer 展开动画期间容器尺寸持续变化，G6 在动画中挂载测量容易踩坑）。**最终修复**：新增路由 `/cluster/:clusterId/service/:instanceId/ds-workflow/:projectCode/:workflowInstanceId` → 新页面 `DsDagPage.tsx`（整页渲染 + "返回列表" 按钮 history.push 回服务页），`InstancesTable` 行点击从 `setSelectedInstance` 改为 `history.push` 到新路由；删除 `DsDagDrawer.tsx` 及其测试。**验证**：真实浏览器里"小窗口打开→大幅拖拽到 2200×1100"复测，7 节点整齐无重叠无异常边框；"返回列表"按钮跳转正常。`useFillViewportHeight` 改动保留（整页场景下让画布填满可用高度是合理的，不是错误修复，只是不是这次 bug 的根因） | 批链路 DAG 查看方式从 Drawer 变为整页路由，URL 变得可分享/可刷新，同时规避了 Drawer 动画期间的 G6 挂载竞态 | `datasophon-ui-v2` 新增 `DsDagPage.tsx`/`DsDagPage.test.tsx`，改动 `index.tsx`/`index.test.tsx`/`DsDagGraph.tsx`/`DsDagGraph.test.tsx`/`config/routes.ts`/两个 `dsWorkflow.ts` locale 文件；删除 `DsDagDrawer.tsx`/`DsDagDrawer.test.tsx`；已构建部署到 ddh-01 并用真实浏览器 resize 验证通过，截图 `.scratch/ds-workflow-tab/shots/ds71-dagpage-resize-fixed-2026-08-26.png`。**排查中的坑**：`gotoAndWait`+`Network.setCacheDisabled` 都无法可靠绕开浏览器缓存去验证新部署，必须用 CDP `Page.reload({ignoreCache:true})` 才能确保拿到最新代码——之前两轮"点击没反应"的假阳性全部是这个缓存问题导致，不是代码 bug，浪费了大量排查时间才定位到 |
| 2026-08-26 | W1-B2 | 复核：`apiToken` 仍在 DS 配置页（「DS开放接口令牌 apiToken」，共 36 个 label），手工补传 DDL 的效果持续 | 说明修复有效，但仍是手工态 | **W1-B2 退回结论不变**——缺的是「DDL 变更如何进入元数据存储」的交付定义，不是补一次上传 |

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
