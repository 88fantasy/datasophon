# DolphinScheduler 工作流可视化 Tab —— 实施方案

> 出方案：Claude，2026-08-25。实现：Codex。验收：Claude。
> 方案里的每个 DS 接口参数、每个字段取值、每条绑定链，都在五节点沙箱上**实测过**，不是推测。
> 需求推导过程与全部证据留在本地 `.scratch/ds-workflow-tab/`（已 gitignore），本文是其收敛结果。

---

## 1. 背景与范围

### 1.1 要做什么

在 **DolphinScheduler 组件详情页**（物理侧 `DS` 服务）新增一个**只读**的「工作流」Tab：

- 顶部项目下拉切换 DS project；
- 树表**主行** = 该项目下的工作流定义（带上线状态标记），**子行** = 该定义最近 10 条工作流实例；
- **点击工作流实例行** → 全屏 Drawer 渲染**该条实例**的 G6 DAG；
- DAG 节点显示任务状态；**批任务终态**显示总行数 + 写入容量，**流作业**显示速度（row/s）。

### 1.2 领域词汇（全文固定用法）

| 词 | 含义 | 避免 |
|---|---|---|
| **工作流定义** | DS 里可被调度的编排模板，**本身没有运行状态** | 「工作流」「流程」「DAG」 |
| **工作流实例** | 一次具体运行，是**状态、行数、速度的唯一载体** | 「工作流运行」「执行记录」 |
| **任务节点** | DAG 上的一个点，对应一条**任务实例** | — |
| **绑定链** | 任务实例 → 应用标识 → 指标。断在任何一环，节点上就没有数字可显示 | — |

### 1.3 范围内

- 仅**物理侧 `DS` 服务**；
- **纯只读**，所有写操作（上线/下线、手动触发、停止实例）一律跳转 DS 原生 Web UI；
- 项目维度切换，**不做跨项目汇总**。

### 1.4 明确不做（Out of scope）

- K8s 侧 `dolphinscheduler-helm` 的同款 Tab（后端接口按同一契约设计，本期不实现、不验证）；
- 任何写操作；
- Spark 4.x 纳管（需换 Scala 2.13 构建线与 OpenLineage 版本，够一个独立 epic）；
- `datasophon-flink-metrics-otel` 的平台受管分发。

---

## 2. 架构

```
┌────────────────────┐   DS Open API (token)   ┌──────────────────┐
│ datasophon-ui-v2   │ ───────────────────────▶│ datasophon-api   │
│  工作流 Tab         │                          │  /v2/ds/*        │
│  树表 + G6 Drawer   │◀─────────────────────── │  DsWorkflowService│
└────────────────────┘   平台自有 VO             └────────┬─────────┘
                                                          │
                          ┌───────────────────────────────┼──────────────────────────┐
                          ▼                               ▼                          ▼
                 ┌────────────────┐            ┌────────────────────┐     ┌────────────────────┐
                 │ DS ApiServer   │            │ Gravitino /api/    │     │ Doris otel.        │
                 │ :12345         │            │ lineage/run/       │     │ otel_metrics_sum   │
                 │ /dolphinscheduler│          │ by-external-key/{k}│     │ (job_name/job_id)  │
                 └────────────────┘            └────────────────────┘     └────────────────────┘
                     列表 / DAG / 状态                批任务行数与容量            流作业 row/s
```

### 2.1 绑定链（本需求的技术命门）

DS **不提供**可用的应用标识——`app_link` 字段**恒为 `null`**（3.3/3.4 执行器重构引入的上游回归，
`AbstractYarnTask` 覆写 `handle()` 后不再回写 `taskExecutionContext`）。沙箱实测三个任务实例
（含 DS 原生 SPARK 任务）**全部为 null**。因此绑定必须由平台**主动注入**，分两条链：

**批（Spark）**

```
DS 任务实例 id
  └─ 用户在任务里注入：--conf spark.datasophon.dsTaskInstanceId=ds-<clusterId>-${system.task.instance.id}
       └─ OpenLineage Spark 集成把它放进 run facet `spark_properties.properties`
            └─ Gravitino 按 externalRunKeys 解析，落 lineage_run.external_run_key
                 └─ GET /api/lineage/run/by-external-key/{key}
                      └─ outputs[]：每个输出数据集的 rowCount / size
```

**流（Flink）**

```
DS 任务实例 id
  └─ 用户在 SQL 里设：SET 'pipeline.name' = 'ds-<clusterId>-${system.task.instance.id}-<自定义后缀>'
       └─ Flink metrics-otel reporter 把 job_name 写进 OTLP attributes
            └─ Doris otel.otel_metrics_sum 按 job_name LIKE 'ds-<clusterId>-<taskInstanceId>-%' 命中
                 └─ 取 attributes["job_id"] → 按 numRecordsOut 的 delta 算 row/s
```

> **key 必须带 `ds-<clusterId>-` 前缀。** DS 内置变量 `${system.task.instance.id}` 给的是**裸数字**
> （沙箱实测 key 就是 `"3"`、`"4"`），而 Gravitino 的 `external_run_key` 是**全局键、无命名空间**，
> 跨 DS 实例或跨集群必然相撞。

### 2.2 为什么批走血缘、流走 OTel

| | 批（Spark） | 流（Flink） |
|---|---|---|
| 通道 | OpenLineage → Gravitino | OTLP push → Doris |
| 拿到的量 | **终值**总行数 + 容量（`outputStatistics` facet） | **速率**（delta 求和） |
| 为什么不是反过来 | 官方 `openlineage-flink` **不实现 `outputStatistics`**（OpenLineage 仓库里 `integration/spark` 命中 10 处、`integration/flink` 0 处），Flink 的 FLIP-314 血缘只给拓扑不给统计 | OTLP push 路径只有 delta，没有可读累计值 |

---

## 3. 接口契约

统一前缀 `/v2/ds`（沿用 `LineageV2Controller` 的 `/v2/*` 风格）。**每个端点必带 `clusterId`**
——DS 地址由 `ApiServer` 角色推导、`apiToken` 按 clusterId 实时读库（见 §4）。

| # | 端点 | 用途 | 打给 DS 的调用 |
|---|---|---|---|
| 1 | `GET /v2/ds/projects?clusterId=` | 项目下拉 | `GET /projects?pageNo=1&pageSize=200` |
| 2 | `GET /v2/ds/workflows?clusterId=&projectCode=&pageNo=&pageSize=&searchVal=` | 树表主行 | `GET /projects/{c}/workflow-definition`（**透传分页**） |
| 3 | `GET /v2/ds/workflows/{workflowCode}/instances?clusterId=&projectCode=&limit=10` | 树表子行（**懒加载**） | `GET /projects/{c}/workflow-instances?workflowDefinitionCode=…&pageNo=1&pageSize=limit` |
| 4 | `GET /v2/ds/instances/{instanceId}/dag?clusterId=&projectCode=` | Drawer 的 DAG（**含指标**） | `GET /workflow-instances/{id}` + `GET /workflow-instances/{id}/tasks` + 指标查询 |

> DS 3.4.1 自带 **OpenAPI 3**：`GET /dolphinscheduler/v3/api-docs`（需 token，约 240 KB）。
> 上表每个参数名都据此核对过。**DS 3.3 起 `process` 全线改名 `workflow`**，网上 3.1/3.2 教程的路径全部作废。

### 3.1 为什么子行懒加载

DS **没有**「批量查 N 个定义的最近实例」的接口。随主行一起返回 = 对每页 20 个定义扇出 20 次调用。
**展开时才查，一次一个定义。**

`GET /workflow-instances` 支持 `workflowDefinitionCode` 过滤，底层就是 `order by start_time desc`，
`pageSize=10` 直接满足「最近 10 条」。⚠️ 其 SQL 硬编码 `is_sub_workflow=0`，**子工作流实例不可见**。

### 3.2 DAG 响应：指标内联

端点 4 一次返回节点 + 边 + 状态 + 指标，前端**不再**单独调 `/v2/lineage/job-metrics`。
理由：指标是节点的一部分；15 秒轮询打一个端点，比打两个再对齐时间戳简单得多。

```jsonc
{
  "instance": {
    "id": 3, "name": "wf_batch_spark_shell-20260825102001",
    "state": "SUCCESS", "startTime": "2026-08-25T10:20:01",
    "endTime": "2026-08-25T10:20:17", "durationSeconds": 16,
    "host": "…:1234", "commandType": "START_PROCESS", "dryRun": false
  },
  "nodes": [{
    "taskCode": 182469955171360,
    "name": "spark_batch_shell",
    "taskType": "SHELL",              // DS 原值
    "taskExecuteType": "BATCH",       // DS 原值
    "flowType": "BATCH",              // 平台判定（§3.3），不是 DS 的假字段
    "taskInstanceId": 3,              // 没跑过则 null
    "state": "SUCCESS",
    "startTime": "…", "endTime": "…", "durationSeconds": 16,
    "host": "…:1234", "retryTimes": 0,
    "metrics": {
      "kind": "BATCH",
      "runCount": 7,
      "outputs": [
        {"namespace": "file", "name": "…/ds_batch_src", "rowCount": 700, "size": 7096, "jobName": "…"},
        {"namespace": "file", "name": "…/ds_batch_dst", "rowCount": 234, "size": 3450, "jobName": "…"}
      ]
    },
    "metricsError": null              // null | "NOT_BOUND" | "LOOKUP_FAILED"
  }],
  "edges": [{"from": 182469955171360, "to": 182470883015712}]
}
```

流节点的 `metrics`：

```jsonc
{"kind": "STREAM", "jobId": "e838c39e…", "jobName": "ds-1-5-stream-verify",
 "rowsPerSecond": 22.8, "approximate": true,
 "processedApprox": 1234567, "since": "2026-08-25T11:00:36"}
```

**两条硬性要求**：

1. **边要过滤 `preTaskCode == 0` 的哑元**。DS 的起点边就是 `pre=0 → post=<code>`，
   不丢弃会在图上多出一个幽灵节点。
2. **`locations` 可能为 null**，前端回退 dagre 自动布局。

DAG 数据在 `GET /workflow-instances/{id}` 的 `dagData` 里：
`taskDefinitionList`（节点）+ `workflowTaskRelationList`（边）+ `workflowDefinition`；
**节点的运行状态不在这里**，在 `GET /workflow-instances/{id}/tasks` 的 `taskList`，
两者用 `taskCode` 关联。实例 DAG 是**版本钉住的**（按 `workflowDefinitionVersion` 回落 `_log` 表），
正是我们要的语义。

### 3.3 批流判定与 key 拼装（**核心，别抄错**）

- 批/流由平台按 **`taskType`** 判定（实测取值 `SHELL` / `SPARK`）。
  ⚠️ **绝不能用 DS 的 `flowType`**——它是硬编码字面量 `"TABLE"` 的假字段，三处写死。
- **批**：`key = "ds-" + clusterId + "-" + taskInstanceId` →
  `GravitinoLineageClient.getRunByExternalKey(clusterId, key)`（已实现并验收）。
- **流**：Doris 按 `job_name LIKE 'ds-<clusterId>-<taskInstanceId>-%'` 命中 →
  取 `attributes["job_id"]` → 按 `flink.taskmanager.job.task.operator.numRecordsOut` 的 delta 算速率。
- **`appLink` 一律不用**（恒 null）。

### 3.4 聚合、并发与超时

- 端点 4 内部：并发拿 `/workflow-instances/{id}` 与 `/{id}/tasks`（互不依赖），
  再对**有 `taskInstanceId` 的节点**并发查指标；**没跑过的节点不查**。
- 单节点指标失败 → 该节点 `metricsError="LOOKUP_FAILED"`，**不影响整图**。
- 超时：DS 调用 **5s**、单次指标查询 **3s**、端点整体 **10s**；并发上限 **8**。
- 端点 1/2/3 都是单次 DS 调用，不扇出。
- **轮询**：15 秒，**只打端点 4，且仅 Drawer 打开时**。列表页不轮询。

### 3.5 缓存：不缓存

`apiToken` 实时读库（§4）；DS 列表接口本身很快。缓存会带来「上线了却看不到」的解释成本。
若将来单项目定义数上千再议。

### 3.6 错误语义

**沿用仓库现有 v2 范式**：`V2ApiExceptionHandler`（`@RestControllerAdvice(basePackages="…controller.v2")`）
+ `ApiResponse` 信封 + `V2ResponseBodyAdvice`。

> ⚠️ `.claude/rules/springboot.md` 要求 `ProblemDetail`，此处**有意不遵循**：
> `datasophon-api` 的 v2 已有统一约定，为一个 Tab 引入第二套错误范式会让前端同时处理两种错误形状。

| 情况 | 处理 | 前端表现 |
|---|---|---|
| `apiToken` 未配置 | `BusinessHintException` → `fail(400, …)` | Tab **仍出现**，展示配置引导 |
| DS token 失效 | DS 返回**裸 401、响应体为空** → 转 `ResponseStatusException(401, "DS apiToken 已失效")` | 同上，文案不同 |
| DS 不可达 / 超时 | `ResponseStatusException(502, …)` | Tab 内错误占位 + 重试按钮（**不隐藏 Tab**） |
| 指标查不到 | **不报错**，节点 `metricsError` | 节点指标区显示 `—` |

**必须先判状态码再解析**：DS 的 401 响应体是空的，直接 `readTree` 会把 401 变成 500。
同理 **老路径 `/process-definition` 返回 200 + 一整页 HTML**（被 SPA 路由兜住），
只按状态码判活会全绿逃逸 —— 解析前校验 `Content-Type` 或解析结果。

### 3.7 DTO 与命名

- **不透传 DS 的原始结构**（`totalList` / `dagData` / `workflowTaskRelationList` 不外露），
  平台侧定 `DsProjectVO` / `DsWorkflowDefinitionVO` / `DsWorkflowInstanceVO` / `DsDagVO` / `DsDagNodeVO`。
- 时间统一 `yyyy-MM-dd'T'HH:mm:ss`（DS 给的是 `yyyy-MM-dd HH:mm:ss`）；
  时长统一 `durationSeconds`（DS 给的是 `"16s"` 这类字符串）。
- 分页信封 `{ list, total, pageNo, pageSize }`。

---

## 4. 配置

### 4.1 DDL 只加一个参数

`package/raw/meta/datacluster-physical/DS/service_ddl.json` 新增：

| 字段 | 值 |
|---|---|
| `name` | `apiToken` |
| `label` | DS 开放接口令牌 |
| `configType` | `map`，`type: input` |
| `required` | `false`（未配置时 Tab 仍出现并引导） |
| `defaultValue` | 空 |

**地址不配**：由 `ApiServer` 角色实例 + `apiServerPort` 推导。
⚠️ **不得照抄 `GravitinoLineageEndpointResolver` 的严格 `RUNNING` 过滤** ——
`OtelDorisReaderFactory` 已因此在角色带 `EXISTS_ALARM`（进程实际健康）时把端点判成不可用，
造成全平台查询 500，而 **DS ApiServer 长期带告警是常态**。

### 4.2 明文存储 + 只读账号

平台没有敏感字段机制（Gravitino 的几个密码参数都是明文 `input`），本参数同样明文存库。
**用权限收敛替代加密**：文档要求用户**为平台单独建一个只读 DS 用户并签发 token**，
而不是用 admin 的 token。token **不下发到节点、不触发 DS 重启**。

### 4.3 ⚠️ 现存实例看不到新参数（必须处理）

`getServiceConfigOption` 对**已安装实例**只回放已持久化的 `configJson`，
**DDL 新增的参数现存实例永远看不到**。必须照 `ApisixGatewayConfigService`(:170-173) 补兜底合并，
否则老集群升级后这个参数在界面上根本不出现。

### 4.4 用户接入约定（要写进用户文档）

| 引擎 | 用户要做的事 |
|---|---|
| **Spark（批）** | 任务参数加 `--conf spark.datasophon.dsTaskInstanceId=ds-<clusterId>-${system.task.instance.id}`；并确保 `spark.openlineage.capturedProperties` 含 `spark.datasophon.dsTaskInstanceId` |
| **Flink（流）** | SQL 里显式 `SET 'pipeline.name' = 'ds-<clusterId>-${system.task.instance.id}-<后缀>'`。**不能依赖默认作业名**——实测 SQL 自动生成的名字是 `insert-into_<表>_sink`，多 sink 还逗号拼接，无法回指 DS 任务 |

Gravitino 侧需配 `gravitino.lineage.storage.externalRunKeys = spark.datasophon.dsTaskInstanceId`
（已在 `GRAVITINO/service_ddl.json` 中加好）。

---

## 5. 前端

### 5.1 Tab 注册

`datasophon-ui-v2/src/pages/Cluster/ServiceInstance/index.tsx` 里已有
`const isDS = serviceInfo?.serviceName === 'DS';`（约 276 行），照 `isApisix` 的写法追加：

```tsx
if (isDS) {
  items.push({
    key: 'dsWorkflow',
    label: '工作流',
    children: <DsWorkflowPanel clusterId={numericClusterId} instanceId={numericInstanceId} />,
  });
}
```

新目录 `src/pages/Cluster/ServiceInstance/DsWorkflow/`。

### 5.2 树表

`ProTable` + `expandable`：

- 顶部 `ProFormSelect` 切 project（端点 1），选中值进 `params`；
- 主行 = 工作流定义，列：名称 / **上线状态徽标**（`releaseState`）/ 版本 / 负责人 / 更新时间 / 操作（跳 DS 原生页）；
- **`request` 必须返回 `{ data, success, total }`**，`total` 透传 DS 的值；
- 子行懒加载：展开时调端点 3，列：实例名 / 状态 / 开始时间 / 时长 / 执行主机；
- **仅实例行可点**（打开 Drawer），定义主行不可点。

> 主行**不做「只看已上线」的服务端过滤**——DS 的分页接口没有 `releaseState` 参数，
> 无分页的 `simple-list` 又不返回它。前端给一个客户端筛选器即可。

### 5.3 Drawer + G6 DAG

- 容器：antd `Drawer`，`width="90%"`，非独立路由；
- 图：复用 `pages/Cluster/Lineage/LineageGraph.tsx` 的 G6 v5 封装与
  `flowingLineageEdge.ts` 的流动边（**流作业节点的出边用流动边，批任务用静态边**）；
- 打开时启动 **15 秒轮询**，关闭时**必须清掉定时器**；
- `locations` 为 null 时用 dagre 布局。

### 5.4 节点视觉规范（变体 A「密度卡」）

```
┌─┬──────────────────────────────┐
│▌│ spark_batch_shell            │   ← 左侧 4px 状态色条
│▌│ SPARK · 成功                  │   ← 类型 · 状态
│▌│ ─────────────────────────    │
│▌│ ds_batch_src   700 行 / 6.9K │   ← 批：按输出表分列
│▌│ ds_batch_dst   234 行 / 3.4K │
│▌│ +2 张表                       │   ← 超过 2 张折叠
└─┴──────────────────────────────┘
```

- **批任务**：按输出表分列，**不求和**（一个 Spark 应用会产出多条血缘 job，各对应一张输出表）；
  前 2 张 + `+N`；
- **流作业**：大号 `22.8 row/s` + 次级「已处理约 1,234,567 条」；
- **无指标**：显示 `—`，不显示 0（0 是有意义的值，`—` 表示没绑定上）；
- 状态色条取值与 DS 状态一一对应（SUCCESS / RUNNING / FAILURE / KILL / PAUSE / STOP / SUBMITTED_SUCCESS）。

### 5.5 i18n

新建 `src/locales/zh-CN/dsWorkflow.ts` 与 `en-US/dsWorkflow.ts` **一个命名空间**
（与 `apisixGateway.ts`、`dolphinSchedulerMonitor.ts` 同级）。
键前缀 `dsWorkflow.`，覆盖：表头、状态枚举、指标单位、四类错误文案、配置引导文案。

### 5.6 格式化

- 行数：千分位（`1,234,567`）；
- 容量：`formatBytes`（复用 `lineageFormatters.ts`）；
- 速率：保留 1 位小数 + `row/s`，**并在旁边标注「约」**；
- 时间：`dayjs.utc().local()`（平台既有约定，勿直接 `dayjs(...)`）。

---

## 6. 任务清单

### 6.1 进度跟踪表（**每个 Phase 验证完更新对应行，随代码进仓库**）

| Phase | 任务 | 完成判据 | 状态 | 备注 |
|---|---|---|---|---|
| **P0-1** | 修复 DS 分发包缺任务插件 | 全新安装的 DS 能创建 SHELL/SPARK/FLINK 任务定义 | ⬜ | 见 `docs/ds-平台缺陷-任务插件缺失与S3凭据漂移-2026-08-25.md` |
| **P0-2** | 修复 DS 的 S3 凭据漂移 | DS 任一角色重启后能正常启动 | ⬜ | 同上，须从 `DS/service_ddl.json` 侧修 |
| **P1-1** | `DsApiClient`（地址推导 + token + 错误映射） | 单测覆盖 200 / **401 空体** / **200+HTML** / 超时四种响应 | ⬜ | 地址推导**不得**用严格 RUNNING 过滤 |
| **P1-2** | 端点 1/2/3 + VO | 前端可照 §3 写 mock；分页信封字段一致 | ⬜ | |
| **P1-3** | 端点 4（DAG 组装，不含指标） | 边已过滤 `preTaskCode==0`；节点状态由 `taskCode` 正确关联 | ⬜ | |
| **P2-1** | 批指标绑定 | 给定跑过的 Spark 任务实例，节点上出现 `outputs[]` 且行数与实际写入一致 | ⬜ | 复用 `getRunByExternalKey` |
| **P2-2** | 流指标绑定 | 给定跑着的 Flink 作业，节点上出现 row/s 且 `jobId` 与 Flink REST 一致 | ⬜ | Doris `job_name` 前缀匹配 |
| **P2-3** | 并发 + 超时 + 单点失败隔离 | 单个节点指标查询抛异常时，其余节点仍正常返回 | ⬜ | |
| **P3-1** | `DS/service_ddl.json` 加 `apiToken` | 新装集群能在界面上看到并保存该参数 | ⬜ | |
| **P3-2** | 现存实例兜底合并 | **已安装**的 DS 实例升级后也能看到该参数 | ⬜ | 照 `ApisixGatewayConfigService` |
| **P4-1** | Tab 注册 + 项目下拉 | 只在 `serviceName === 'DS'` 时出现；切项目会刷新列表 | ⬜ | |
| **P4-2** | 树表主行 + 子行懒加载 | 展开才发请求；`total` 正确 | ⬜ | |
| **P4-3** | Drawer + G6 DAG + 15s 轮询 | 关闭 Drawer 后定时器停止（用 fake timer 断言） | ⬜ | |
| **P4-4** | 节点视觉（变体 A） | 批按表分列、流显速率、无指标显示 `—` | ⬜ | |
| **P4-5** | i18n + 格式化 | `npm run lint` 通过；中英两份键齐全 | ⬜ | |
| **P5-1** | 流作业累计量落表（D20） | 见 §6.2 | ⬜ | **本期唯一新增持久化状态** |
| **P6-1** | 场景 7.1 批端到端验收 | 见 §7 | ⬜ | |
| **P6-2** | 场景 7.2 流端到端验收 | 见 §7 | ⬜ | |

### 6.2 P5：流作业「已处理数量」的后台累加（单列一节）

OTLP 只有 delta，累计值必须**平台自己算**。**不实时算**——沙箱那个流作业已跑 9.9 天，
按 6.5k–7.2k 点/作业/小时的密度，实时聚合会把 Doris 打爆。

- 新增一张累计表（作业维度：`job_id` + 窗口游标 + 累计值 + 更新时间）；
- 新增一个 `@Scheduled` 定时任务，按周期对 delta 求和后**幂等**写入（重复执行不重复累加，重启可恢复）；
- 起点取 Flink REST 的 `start-time`；
- **显示为约值并标注口径**。

### 6.3 依赖顺序

```
P0 ──▶ P1 ──▶ P2 ──▶ P6
        │       │
        └──▶ P4 ┘        P3 与 P1 并行；P5 依赖 P2-2
```

---

## 7. 验收

### 7.1 场景一：批任务（Spark）

1. 在 DS 里建一条 Spark 任务（原生 SPARK 任务或 Shell 调 `spark-sql` 均可），
   按 §4.4 注入 `ds-<clusterId>-${system.task.instance.id}`；
2. 任务写入两张表，行数**预先可算**（例如 `range(0,700)` 与 `id%3=0` → 700 / 234）；
3. 运行至终态；
4. 在工作流 Tab 里展开该定义 → 点击这条实例 → Drawer 出图；
5. **判据**：节点显示两张输出表，行数分别为 **700** 与 **234**，容量非空；
6. **再跑一次**（新的任务实例 id），旧实例的节点仍显示**它自己那次**的行数（不被新运行覆盖）。

### 7.2 场景二：流作业（Flink）

1. 在 DS 里建一条 Flink 任务，SQL 里按 §4.4 设 `pipeline.name`；
2. **数据一律用合成数据**（如 datagen），不得使用任何真实业务数据；
3. 作业进入 RUNNING；
4. 打开该实例的 DAG；
5. **判据**：节点显示 row/s（非零）、`jobId` 与 Flink REST `/jobs/overview` 的 `jid` 一致，
   且速率标注为**约值**；Drawer 打开期间数值每 15 秒刷新。

> **7.2 的表结构可以写进文档，真实数据、口令、内网 IP 一律不得出现。**

### 7.3 降级验收

| 操作 | 期望 |
|---|---|
| 清空 `apiToken` | Tab 仍出现，展示配置引导 |
| 填一个失效 token | Tab 仍出现，提示「apiToken 已失效」（**不是 500**） |
| 停掉 DS ApiServer | Tab 内错误占位 + 重试按钮 |
| 任务没按 §4.4 注入 | 节点指标区显示 `—`，**不报错、不猜测** |

---

## 8. 已知地雷清单

按踩到的代价从高到低排：

1. **DS 分发包缺全部任务插件** —— 任何 Shell/Spark/Flink/SQL 任务**在创建阶段**就被拒，
   而报错文案是 `request parameter {0} is not valid`，真因藏在 api-server 日志的
   `Cannot find TaskChannel for : SHELL`。见平台缺陷文档。
2. **DS 的 S3 凭据与对象存储不符，重启才暴露** —— S3 客户端只在启动时初始化一次。
   任何「一直好好的」服务，其配置正确性只有在重启那一刻才被验证。
3. **`app_link` 恒为 null** —— 3.3/3.4 执行器重构的上游回归，别指望它。
4. **`flowType` 是假字段** —— 硬编码 `"TABLE"`，三处写死；批流判定必须用 `taskType`。
5. **external key 用裸任务实例 id 会跨集群相撞** —— 必须带 `ds-<clusterId>-` 前缀。
6. **端点解析不得用严格 `RUNNING` 过滤角色实例** —— DS ApiServer 长期带 `EXISTS_ALARM` 是常态，
   严格过滤会把健康端点判成不可用（`OtelDorisReaderFactory` 已经踩过，造成全平台查询 500）。
7. **DDL 新增参数现存实例看不到** —— 必须补兜底合并。
8. **DS 的 401 响应体为空** —— 先判状态码再解析，否则 401 变 500。
9. **老路径返回 200 + HTML** —— `/process-definition` 被 SPA 路由兜住，按状态码判活会全绿逃逸。
10. **`preTaskCode == 0` 是哑元边** —— 不丢弃会多出幽灵节点。
11. **`GET /task-instances` 的 `taskExecuteType` 默认 `BATCH`** —— 不传 `STREAM`，流作业节点会凭空消失。
12. **子工作流实例不可见** —— 实例列表 SQL 硬编码 `is_sub_workflow=0`。
13. **算子被 chain 时 `numRecordsOut` 恒为 0** —— 「显示 0 row/s」不等于「作业没在跑」，
    前端不能把这两种状态画成一样。
14. **row/s 是近似值** —— `SUM(delta)/(MAX-MIN)` 少算首个采集周期，实测偏高一倍多；
    口径要写死并标注约值。
15. **Flink 有三条 reporter 路径不是两条** —— 1.20 backport 与 2.x 原生是同一份代码
    （OTLP delta Sum → `otel_metrics_sum`）；1.20 现役的 Prometheus scrape 是第三条
    （Counter 误标 gauge → `otel_metrics_gauge`）。**同时配两个 reporter 会双重计数。**
16. **Doris 动态分区可能没跟上当天日期** —— 查不到数据时先看分区，别急着怀疑采集。
17. **前端 mock 路由的 `pathname` 必须含 `/ddh`** —— 否则 basename 类缺陷全绿逃逸。
18. **新增 `@SpringBootTest` 必须加 `@DirtiesContext`** —— 否则与其它上下文抢 gRPC 18081，
    报错表象常被误判为 MySQL 连接问题。
19. **`dolphinscheduler-daemon.sh start` 会用 `bin/env/dolphinscheduler_env.sh` 覆盖各服务 `conf/` 下的同名文件**
    —— 给任务配环境变量必须改 `bin/env/` 那一份。
20. **复制 Flink 目录必须排除 `savepoints/` 与 `checkpoints/`** —— 那是运行中作业的状态数据，
    可能含业务数据，属于合规问题而不只是体积问题。
