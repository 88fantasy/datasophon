# 组件监控看板调研与选型 实施计划(Phase 1)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 22 个平台组件并行调研「Prometheus 数据源(原生 `/metrics` 优先)+ 社区候选看板 + 加权选型」,产出调研报告、选型清单与可供 Phase 2(claude design)使用的看板源 JSON 与 panel-catalog。

**Architecture:** 先生成 22 行执行清单(manifest)→ 用 Workflow 工具并行调起「每组件 1 个可重试子代理(结构化 schema)」→ Workflow 内合并为报告 → 落盘报告供人工选型 → 按选型清单下载看板源 JSON 并用 node 脚本抽取 panel-catalog。**本期只产出文档与数据物料,不写任何业务代码。**

**Tech Stack:** Workflow 工具(parallel + schema 自动重试)、WebSearch/WebFetch、grafana.com dashboards API、node v22(ESM 抽取脚本)、jq(验证)。

**Spec:** `docs/monitoring/dashboard-survey-design.md`

---

## 文件结构

| 文件 | 职责 | 由谁产出 |
|---|---|---|
| `docs/monitoring/dashboard-survey-manifest.json` | 22 组件执行清单(component/version/group/docHint),Workflow 的 `args` 输入 | Task 1 |
| `docs/monitoring/workflow/survey.workflow.js` | 调研 Workflow 脚本(并行子代理 + 重试 + 合并) | Task 2 |
| `docs/monitoring/dashboard-survey-results.json` | Workflow 返回的 22 份结构化结果(报告数据源) | Task 3 |
| `docs/monitoring/dashboard-survey.md` | 调研报告(总览表 + 每组件详情打分) | Task 3 |
| `docs/monitoring/dashboard-selection.md` | 选型清单(**人工卡点**锁定) | Task 4 |
| `docs/monitoring/scripts/extract-panel-catalog.mjs` | 从上游看板 JSON 抽取 panel-catalog 的脚本 | Task 5 |
| `docs/monitoring/dashboards-reference/<COMPONENT>/<id>.json` | 选定看板的源 JSON | Task 6 |
| `docs/monitoring/panel-catalog/<COMPONENT>.json` | 规范化面板目录(交给 Phase 2) | Task 6 |

## 当前产出物核验状态(2026-06-14 更新)

| 产出物 | 状态 | 核验结论 |
|---|---|---|
| `docs/monitoring/dashboard-survey-manifest.json` | ✅已完成 | `jq 'length'` = 22,不含已移除组件,包含 Valkey 与 DATART Spring Boot Actuator/Micrometer 提示 |
| `docs/monitoring/workflow/survey.workflow.js` | ✅已完成 | 已包含 22 组件范围,含 DATART/Valkey 特殊提示 |
| `docs/monitoring/dashboard-survey-results.json` | ✅已完成 | `jq 'length'` = 22;Valkey 替代 Redis;DATART 走 Spring Boot Actuator/Micrometer;⚠️ Doris/Kyuubi 候选数 < 2(需补足或豁免) |
| `docs/monitoring/dashboard-survey.md` | ✅已完成 | 报告 136 行表格;总览和详情包含 Valkey/DATART 新口径 |
| `docs/monitoring/scripts/extract-panel-catalog.mjs` | ✅已完成 | 已用最小上游看板 JSON 样例验证:输出 2 个面板,能展开 row 嵌套并过滤 text 面板 |
| `docs/monitoring/dashboard-selection.md` | ⏳未完成 | 文件尚未存在,处于人工选型卡点前 |
| `docs/monitoring/dashboards-reference/<COMPONENT>/<id>.json` | ⏳未完成 | 目录尚未存在,需等待选型清单锁定后下载 |
| `docs/monitoring/panel-catalog/<COMPONENT>.json` | ⏳未完成 | 目录尚未存在,需等待源 JSON 下载后批量抽取 |

补充说明:
- 新范围不再调研已移除的 3 个组件。
- Redis 替换为 Valkey,调研时优先确认 Valkey 原生/兼容 Redis exporter 指标与可复用看板。
- DATART 不再按"缺数据源"处理,改采用 Spring Boot Actuator/Micrometer 体系看板,优先检索 Spring Boot / JVM / Micrometer / Tomcat / HikariCP 相关 Prometheus 看板。
- **⚠️ Doris、Kyuubi 候选数 < 2**:已触发 Task 3 Step 4 校验失败。处理方案:人工审核时若判定这两个组件确实无第二个合适候选,在 results.json 中为其加 `"candidatesNote": "确认无现成社区看板,需自建"` 后豁免;否则用 Workflow 补跑。
- 多个 grafana.com 候选的 `rating` 为 `null`,但候选均有 `scores.heat` 与 `total`。验收口径已调整为"downloads/rating 以 API 实际返回为准"。
- **前端图表库已改用 AntV G2**(原 ECharts),design.md 与本文件均已同步更新。Phase 3 实现时使用 `@antv/g2` 或其 React 封装;panel-catalog 的 chartType / unit / thresholds 字段语义不变。

---

## Task 1: 生成执行清单 manifest(22 行)

**Files:**
- Create: `docs/monitoring/dashboard-survey-manifest.json`

- [ ] **Step 1: 写入 manifest(22 组件)**

```json
[
  { "component": "MySQL",            "version": "8.0.28",  "group": "中间件",       "status": "pending", "docHint": "https://dev.mysql.com/doc/" },
  { "component": "Nexus",            "version": "3.85.0",  "group": "中间件",       "status": "pending", "docHint": "https://help.sonatype.com/en/sonatype-nexus-repository.html" },
  { "component": "Rustfs",           "version": "1.0.0",   "group": "中间件",       "status": "pending", "docHint": "https://docs.rustfs.com/features/logging/" },
  { "component": "HDFS",             "version": "3.5.0",   "group": "存储/数据库",  "status": "pending", "docHint": "https://hadoop.apache.org/docs/r3.5.0/" },
  { "component": "YARN",             "version": "3.5.0",   "group": "存储/数据库",  "status": "pending", "docHint": "https://hadoop.apache.org/docs/r3.5.0/" },
  { "component": "Hive",             "version": "4.2.0",   "group": "存储/数据库",  "status": "pending", "docHint": "https://hive.apache.org/docs/" },
  { "component": "Elasticsearch",    "version": "9.4.2",   "group": "存储/数据库",  "status": "pending", "docHint": "https://www.elastic.co/guide/en/elasticsearch/reference/current/index.html" },
  { "component": "Valkey",           "version": "8.6",     "group": "存储/数据库",  "status": "pending", "docHint": "https://valkey.io/docs/" },
  { "component": "JuiceFS",          "version": "1.3.1",   "group": "存储/数据库",  "status": "pending", "docHint": "https://juicefs.com/docs/community/administration/monitoring/" },
  { "component": "Doris",            "version": "4.0.5",   "group": "存储/数据库",  "status": "pending", "docHint": "https://doris.apache.org/docs/admin-manual/maint-monitor/monitor-metrics/metrics" },
  { "component": "Kyuubi",           "version": "1.11.1",  "group": "计算/查询引擎","status": "pending", "docHint": "https://kyuubi.readthedocs.io/en/master/monitor/metrics.html" },
  { "component": "Kafka",            "version": "4.3.0",   "group": "消息/协调",    "status": "pending", "docHint": "https://kafka.apache.org/documentation/#monitoring" },
  { "component": "ZooKeeper",        "version": "3.8.6",   "group": "消息/协调",    "status": "pending", "docHint": "https://zookeeper.apache.org/doc/r3.8.6/zookeeperMonitor.html" },
  { "component": "DolphinScheduler", "version": "3.4.1",   "group": "调度",         "status": "pending", "docHint": "https://dolphinscheduler.apache.org/en-us/docs/3.4.1" },
  { "component": "Prometheus",       "version": "3.12.0",  "group": "可观测性",     "status": "pending", "docHint": "https://prometheus.io/docs/prometheus/latest/getting_started/" },
  { "component": "Alertmanager",     "version": "0.32.1",  "group": "可观测性",     "status": "pending", "docHint": "https://prometheus.io/docs/alerting/latest/alertmanager/" },
  { "component": "Loki",             "version": "3.7.2",   "group": "可观测性",     "status": "pending", "docHint": "https://grafana.com/docs/loki/latest/operations/observability/" },
  { "component": "Promtail",         "version": "2.8.11",  "group": "可观测性",     "status": "pending", "docHint": "https://grafana.com/docs/loki/latest/send-data/promtail/" },
  { "component": "APISIX",           "version": "3.16.0",  "group": "网关/注册中心","status": "pending", "docHint": "https://apisix.apache.org/docs/apisix/plugins/prometheus/" },
  { "component": "Nacos",            "version": "3.2.2",   "group": "网关/注册中心","status": "pending", "docHint": "https://nacos.io/en-us/docs/v2/guide/admin/monitor-guide.html" },
  { "component": "Nginx",            "version": "1.30.2",  "group": "网关/注册中心","status": "pending", "docHint": "https://github.com/nginxinc/nginx-prometheus-exporter" },
  { "component": "DATART",           "version": "3.6.1",   "group": "内部组件",     "status": "pending", "docHint": "https://docs.spring.io/spring-boot/reference/actuator/metrics.html", "dashboardHint": "采用 Spring Boot Actuator/Micrometer 看板,优先覆盖 JVM、HTTP、Tomcat、HikariCP、进程资源指标" }
]
```

- [ ] **Step 2: 验证为 22 行且字段齐全**

Run:
```bash
jq 'length' docs/monitoring/dashboard-survey-manifest.json
jq '[.[] | select(.component and .version and .group and .docHint)] | length' docs/monitoring/dashboard-survey-manifest.json
```
Expected: 两条都输出 `22`。

- [ ] **Step 3: Commit**

```bash
git add docs/monitoring/dashboard-survey-manifest.json
git commit -m "docs(monitoring): 添加 22 组件调研执行清单"
```

---

## Task 2: 编写调研 Workflow 脚本

**Files:**
- Create: `docs/monitoring/workflow/survey.workflow.js`

- [ ] **Step 1: 写入 Workflow 脚本(完整)**

> 关键约束:Workflow 脚本**无文件系统访问**,所以组件清单通过 `args` 注入(Task 3 传入),结果通过返回值回传(Task 3 落盘)。每个组件是一个独立可重试单元:首轮 `parallel` 失败(返回 null)的组件再单独重跑一次。

```javascript
export const meta = {
  name: 'component-dashboard-survey',
  description: '并行调研 22 个组件的 Prometheus 数据源与社区看板并合并为选型报告',
  phases: [
    { title: 'Survey', detail: '每组件一个 agent:查官网确认原生 /metrics + grafana.com 候选看板 + 加权打分' },
    { title: 'Synthesize', detail: '合并 22 份结果为总览表 + 详情报告' },
  ],
}

const COMPONENTS = args
if (!Array.isArray(COMPONENTS) || COMPONENTS.length === 0) {
  throw new Error('args 必须是 manifest 组件数组(非空)')
}

const COMPONENT_SCHEMA = {
  type: 'object',
  additionalProperties: false,
  required: ['component', 'version', 'group', 'nativePrometheus', 'exporterFallback', 'dataSource', 'candidates', 'recommendation', 'tier'],
  properties: {
    component: { type: 'string' },
    version: { type: 'string' },
    group: { type: 'string' },
    nativePrometheus: {
      type: 'object',
      additionalProperties: false,
      required: ['supported', 'endpoint', 'docUrl'],
      properties: {
        supported: { type: 'boolean' },
        sinceVersion: { type: 'string' },
        endpoint: { type: 'string' },
        port: { type: ['string', 'integer'] },
        docUrl: { type: 'string' },
        notes: { type: 'string' },
      },
    },
    exporterFallback: {
      type: 'object',
      additionalProperties: false,
      required: ['needed'],
      properties: {
        needed: { type: 'boolean' },
        name: { type: ['string', 'null'] },
        repoUrl: { type: ['string', 'null'] },
      },
    },
    dataSource: { type: 'string', enum: ['native', 'exporter', 'none'] },
    candidates: {
      type: 'array',
      items: {
        type: 'object',
        additionalProperties: false,
        required: ['name', 'source', 'url', 'scores', 'total'],
        properties: {
          name: { type: 'string' },
          source: { type: 'string' },
          url: { type: 'string' },
          grafanaId: { type: ['integer', 'string', 'null'] },
          downloads: { type: ['integer', 'null'] },
          rating: { type: ['number', 'null'] },
          datasource: { type: ['string', 'null'] },
          lastUpdate: { type: ['string', 'null'] },
          scores: {
            type: 'object',
            additionalProperties: false,
            required: ['heat', 'datasourceMatch', 'promqlPortability', 'goldenSignals'],
            properties: {
              heat: { type: 'number' },
              datasourceMatch: { type: 'number' },
              promqlPortability: { type: 'number' },
              goldenSignals: { type: 'number' },
            },
          },
          total: { type: 'number' },
        },
      },
    },
    recommendation: {
      type: 'object',
      additionalProperties: false,
      required: ['pick', 'reason'],
      properties: { pick: { type: 'string' }, reason: { type: 'string' } },
    },
    tier: { type: 'string', enum: ['🟢可直接做', '🟡需自建', '🔴缺数据源'] },
  },
}

function buildPrompt(c) {
  return [
    `你是监控调研专家。调研组件 ${c.component}(版本 ${c.version},分组 ${c.group})。`,
    `官网线索:${c.docHint || '(自行检索官网)'}`,
    '',
    '第一步 数据源探测(原生优先):',
    `用 WebSearch/WebFetch 查 ${c.component} ${c.version} 官方文档,确认该版本是否原生暴露 Prometheus /metrics 端点。`,
    c.component === 'DATART' ? 'DATART 特殊要求:按 Spring Boot Actuator/Micrometer 应用看板处理,重点调研 JVM、HTTP server、Tomcat、HikariCP、process/filesystem 等 Micrometer 指标,不要按"缺数据源"直接降级。' : '',
    c.component === 'Valkey' ? 'Valkey 特殊要求:优先确认 Valkey 版本对 Redis exporter/Valkey exporter 指标的兼容性,候选看板可复用 Redis/Valkey 兼容指标,但必须在 datasourceMatch 中说明匹配依据。' : '',
    '记录 nativePrometheus.supported(布尔)、sinceVersion、endpoint(路径)、port、docUrl(证据链接)、notes。',
    '仅当原生不支持时,才找成熟 exporter(exporterFallback.needed=true + name + repoUrl);否则 needed=false、name/repoUrl 置 null。',
    'dataSource 取 native(用原生端点)/ exporter(需 exporter)/ none(都没有)。',
    '',
    '第二步 看板检索:',
    '主源用 grafana.com 官方 API(WebFetch):',
    `https://grafana.com/api/dashboards?search=${encodeURIComponent(c.component)}&orderBy=downloads&direction=desc`,
    '取 2-3 个候选,记录 name、source(grafana.com|github|official)、url、grafanaId、downloads、rating、datasource、lastUpdate。',
    '辅源:GitHub、组件官方仓库自带 dashboard。无候选则 candidates 为空数组。',
    '',
    '第三步 加权打分(每个候选,每项 = 归一分(0~1) × 权重,即已是加权贡献):',
    'scores.heat(权重0.3)= 按 grafana.com 下载量/评分归一后 ×0.3;',
    'scores.datasourceMatch(0.3)= 看板指标名是否对应第一步确定的数据源(走原生端点的组件,看板须基于原生指标名;基于旧 exporter 指标名则低分)归一后 ×0.3;',
    'scores.promqlPortability(0.2)= PromQL 是否干净易移植 归一后 ×0.2;',
    'scores.goldenSignals(0.2)= 是否覆盖延迟/流量/错误/饱和度 归一后 ×0.2;',
    'total = 四项之和(范围 0~1)。',
    '',
    '第四步 分级与推荐:',
    'tier:🟢可直接做(有数据源 + 有优质看板) / 🟡需自建(有数据源但无匹配看板) / 🔴缺数据源。',
    'recommendation.pick = 推荐看板名(🟡 填"需自建",🔴 填"缺数据源");reason = 理由(引用分项得分)。',
    '',
    `component/version/group 必须原样回填为 "${c.component}" / "${c.version}" / "${c.group}"。`,
    '严格按结构化 schema 返回,所有 URL 必须真实可访问。',
  ].join('\n')
}

phase('Survey')
const first = await parallel(COMPONENTS.map((c) => () =>
  agent(buildPrompt(c), { label: `survey:${c.component}`, phase: 'Survey', schema: COMPONENT_SCHEMA })
))

// 单组件重试:对失败(null)的再跑一次,不连累已成功的
const finalResults = await Promise.all(first.map(async (r, i) => {
  if (r) return r
  log(`重试组件:${COMPONENTS[i].component}`)
  return await agent(buildPrompt(COMPONENTS[i]), { label: `retry:${COMPONENTS[i].component}`, phase: 'Survey', schema: COMPONENT_SCHEMA })
}))

const good = finalResults.filter(Boolean)
const failed = COMPONENTS.filter((c, i) => !finalResults[i]).map((c) => c.component)

phase('Synthesize')
const report = await agent([
  '把下列组件监控调研结果合并为一份 Markdown 报告。',
  '',
  '报告结构:',
  `1) 标题「组件监控看板调研报告」+ 一行生成说明(共 ${good.length} 个组件)。`,
  '2) 总览表,列:组件 | 版本 | 数据源方式 | 候选数 | 分级 | 推荐看板 | 总分。',
  '   数据源方式:native 显示"原生 {endpoint}:{port}";exporter 显示"exporter {name}";none 显示"无"。',
  '   总分:取 recommendation 对应候选看板的 total;🟡/🔴 留空。',
  '3) 每组件详情(按分组归类):',
  '   - 数据源结论(supported / endpoint / port / sinceVersion / docUrl 证据链接);',
  '   - 候选看板列表(名称 / 链接 / 看板 ID / 下载量 / 数据源 / 更新时间);',
  '   - 分项打分表(heat / datasourceMatch / promqlPortability / goldenSignals / total);',
  '   - 推荐结论与理由。',
  '',
  '只输出 Markdown 正文,不要任何额外解释或代码块包裹。',
  '数据如下(JSON):',
  JSON.stringify(good),
].join('\n'), { label: 'synthesize', phase: 'Synthesize' })

return { results: good, report, failed }
```

- [ ] **Step 2: 校验脚本以 meta 字面量开头**

Run:
```bash
head -1 docs/monitoring/workflow/survey.workflow.js
```
Expected: 输出 `export const meta = {`(Workflow 要求脚本首行为 meta 字面量;脚本用了 `agent/parallel/phase/log/args` 等运行时全局,无法用 `node --check` 校验,以人工核对为准)。

- [ ] **Step 3: Commit**

```bash
git add docs/monitoring/workflow/survey.workflow.js
git commit -m "docs(monitoring): 添加调研 Workflow 脚本(并行子代理+重试+合并)"
```

---

## Task 3: 运行调研 Workflow 并落盘报告

**Files:**
- Create: `docs/monitoring/dashboard-survey-results.json`
- Create: `docs/monitoring/dashboard-survey.md`

> 本任务需要调用 **Workflow 工具**(非纯 shell)。Workflow 后台运行,完成后通过 `<task-notification>` 返回 `{ results, report, failed }`。

- [ ] **Step 1: 读取 manifest 作为 args 启动 Workflow**

读取 `docs/monitoring/dashboard-survey-manifest.json` 的完整数组,作为 `args` 传入(必须是真正的 JSON 数组值,不是字符串)。调用:

```
Workflow({
  scriptPath: "docs/monitoring/workflow/survey.workflow.js",
  args: <粘贴 manifest 数组的 JSON 值>
})
```

等待 `<task-notification>` 完成通知。

- [ ] **Step 2: 把返回的 results 落盘**

把 Workflow 结果中的 `results` 字段写入 `docs/monitoring/dashboard-survey-results.json`(格式化 JSON)。把 `failed` 字段(若非空)记录到本步骤说明里,供下一步处理。

- [ ] **Step 3: 校验 22 组件全覆盖、无失败遗漏**

Run:
```bash
jq 'length' docs/monitoring/dashboard-survey-results.json
jq '[.[].component] | sort' docs/monitoring/dashboard-survey-results.json
```
Expected: `length` 为 `22`;组件名列表与 manifest 一致,且不包含已移除组件,包含 Valkey。
若 `length < 22`(有组件二次重试仍失败):用 Workflow 单独重跑缺失组件(`args` 只传缺失项),把结果并入 results.json,直到达到 22。

- [ ] **Step 4: 校验 🟢/🟡 类每个有 ≥2 候选**

Run:
```bash
jq '[.[] | select(.tier=="🟢可直接做" or .tier=="🟡需自建") | select((.candidates|length) < 2) | .component]' docs/monitoring/dashboard-survey-results.json
```
Expected: 输出 `[]`(空数组)。若非空:对列出的组件用 Workflow 重跑补足候选。

> 说明:🟡需自建本意是"无现成看板",理论上候选可能 <2。若该组件确为无候选,在 results.json 里给其加 `"candidatesNote": "确认无现成社区看板,需自建"` 字段后,本校验对它豁免(改用下方命令复核)。
> `jq '[.[] | select(.tier=="🟢可直接做") | select((.candidates|length) < 2) | .component]' docs/monitoring/dashboard-survey-results.json` —— 🟢 类必须为 `[]`。

- [ ] **Step 5: 落盘报告并校验**

把 Workflow 结果的 `report` 字段写入 `docs/monitoring/dashboard-survey.md`。

Run:
```bash
test -s docs/monitoring/dashboard-survey.md && echo "报告非空 OK"
grep -c "|" docs/monitoring/dashboard-survey.md
```
Expected: 输出"报告非空 OK";总览表存在(`|` 计数明显 > 22,说明有表格)。

- [ ] **Step 6: Commit**

```bash
git add docs/monitoring/dashboard-survey-results.json docs/monitoring/dashboard-survey.md
git commit -m "docs(monitoring): 22 组件调研结果与调研报告"
```

---

## Task 4: 人工选型卡点 → 选型清单

**Files:**
- Create: `docs/monitoring/dashboard-selection.md`

> ⛔ **这是人工卡点**:报告交付后,由人决策每个组件最终用哪个看板。本任务先用报告里的 `recommendation.pick` 预填一版,再交人工锁定。

- [ ] **Step 1: 用 results 预填选型清单**

Run(生成预填清单):
```bash
jq -r '"# 监控看板选型清单\n\n> 预填来自调研报告的推荐项,**待人工审核锁定**。每行确认后把 状态 改为 ✅锁定。\n\n| 组件 | 分级 | 推荐看板 | 看板 ID | 状态 | 备注 |\n|---|---|---|---|---|---|",
( .[] | "| \(.component) | \(.tier) | \(.recommendation.pick) | \((.candidates[0].grafanaId // "-")) | ⏳待审核 | \(.recommendation.reason) |")' \
docs/monitoring/dashboard-survey-results.json > docs/monitoring/dashboard-selection.md
cat docs/monitoring/dashboard-selection.md | head -5
```
Expected: 生成含 22 行的清单,首行为标题。

- [ ] **Step 2: 人工审核锁定(交付给用户)**

提示用户逐行 review `docs/monitoring/dashboard-selection.md`,把确认的行「状态」列改为 `✅锁定`,需要换看板的改「推荐看板/看板 ID」。**等待用户完成。**

- [ ] **Step 3: 校验全部已决策**

Run:
```bash
grep -c "⏳待审核" docs/monitoring/dashboard-selection.md
```
Expected: 输出 `0`(无遗留待审核项)。

- [ ] **Step 4: Commit**

```bash
git add docs/monitoring/dashboard-selection.md
git commit -m "docs(monitoring): 锁定监控看板选型清单"
```

---

## Task 5: 编写 panel-catalog 抽取脚本

**Files:**
- Create: `docs/monitoring/scripts/extract-panel-catalog.mjs`

- [x] **Step 1: 写入抽取脚本(完整)**

```javascript
// 用法: node docs/monitoring/scripts/extract-panel-catalog.mjs <input-dashboard.json> <COMPONENT>
// 产出: docs/monitoring/panel-catalog/<COMPONENT>.json
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { dirname } from 'node:path'

const [, , inputPath, component] = process.argv
if (!inputPath || !component) {
  console.error('用法: node extract-panel-catalog.mjs <input-dashboard.json> <COMPONENT>')
  process.exit(1)
}

const dash = JSON.parse(readFileSync(inputPath, 'utf8'))

// 上游看板的 panels 可能嵌套在 row 内,递归展开
function flattenPanels(panels) {
  const out = []
  for (const p of panels || []) {
    if (p.type === 'row' && Array.isArray(p.panels)) {
      out.push(...flattenPanels(p.panels))
    } else {
      out.push(p)
    }
  }
  return out
}

const catalog = flattenPanels(dash.panels)
  .filter((p) => p.type !== 'row' && p.type !== 'text')
  .map((p) => {
    const promql = (p.targets || []).map((t) => t.expr).filter(Boolean)
    // 兼容新版 fieldConfig 与旧版 yaxes / 阈值结构
    const unit = p.fieldConfig?.defaults?.unit ?? p.yaxes?.[0]?.format ?? null
    const thresholds = p.fieldConfig?.defaults?.thresholds?.steps ?? p.thresholds ?? null
    const legend = p.options?.legend ?? p.legend ?? null
    return {
      title: p.title || '(untitled)',
      promql,
      unit,
      chartType: p.type || null,
      thresholds,
      legend,
    }
  })
  .filter((p) => p.promql.length > 0)

const outPath = `docs/monitoring/panel-catalog/${component}.json`
mkdirSync(dirname(outPath), { recursive: true })
writeFileSync(outPath, JSON.stringify(catalog, null, 2), 'utf8')
console.log(`✓ ${component}: ${catalog.length} 个面板 → ${outPath}`)
```

- [x] **Step 2: 用一份临时看板 JSON 验证脚本可运行**

Run(造一个最小看板 JSON 验证解析,含 row 嵌套):
```bash
mkdir -p /tmp/pc-test
cat > /tmp/pc-test/d.json <<'EOF'
{ "panels": [
  { "type": "row", "panels": [
    { "type": "timeseries", "title": "QPS", "targets": [{"expr": "rate(http_requests_total[5m])"}], "fieldConfig": {"defaults": {"unit": "reqps"}} }
  ]},
  { "type": "stat", "title": "Up", "targets": [{"expr": "up"}] },
  { "type": "text", "title": "ignore" }
]}
EOF
node docs/monitoring/scripts/extract-panel-catalog.mjs /tmp/pc-test/d.json __TEST__
jq 'length' docs/monitoring/panel-catalog/__TEST__.json
jq '.[0].promql' docs/monitoring/panel-catalog/__TEST__.json
```
Expected: 打印 `✓ __TEST__: 2 个面板`;`length` 为 `2`;首面板 promql 为 `["rate(http_requests_total[5m])"]`(证明 row 嵌套被展开、text 面板被滤除)。

- [x] **Step 3: 清理测试产物**

Run:
```bash
rm -f docs/monitoring/panel-catalog/__TEST__.json && rm -rf /tmp/pc-test
```

- [x] **Step 4: Commit**

```bash
git add docs/monitoring/scripts/extract-panel-catalog.mjs
git commit -m "docs(monitoring): 添加 panel-catalog 抽取脚本"
```

---

## Task 6: 按选型清单下载看板源 JSON + 抽取 panel-catalog

**Files:**
- Create: `docs/monitoring/dashboards-reference/<COMPONENT>/<id>.json`(每个锁定且来自 grafana.com 的看板)
- Create: `docs/monitoring/panel-catalog/<COMPONENT>.json`(每个有源 JSON 的组件)

> 仅处理选型清单中「状态=✅锁定」且有看板 ID 的组件。🟡需自建 / 🔴缺数据源 的组件跳过下载(它们无现成看板),在最终说明里列出。

- [ ] **Step 1: 逐组件下载源 JSON(以单个组件为例,对每个锁定组件重复)**

对清单里某个锁定组件(示例 `ZooKeeper`,看板 ID 假设 `10465`):

Run:
```bash
COMPONENT=ZooKeeper; ID=10465
mkdir -p "docs/monitoring/dashboards-reference/${COMPONENT}"
REV=$(curl -fsSL "https://grafana.com/api/dashboards/${ID}" | jq -r '.revision')
curl -fsSL "https://grafana.com/api/dashboards/${ID}/revisions/${REV}/download" \
  -o "docs/monitoring/dashboards-reference/${COMPONENT}/${ID}.json"
jq '.panels | length' "docs/monitoring/dashboards-reference/${COMPONENT}/${ID}.json"
```
Expected: 文件写入成功,`.panels | length` > 0(确认是有效看板 JSON)。

- [ ] **Step 2: 对该看板抽取 panel-catalog**

Run:
```bash
node docs/monitoring/scripts/extract-panel-catalog.mjs \
  "docs/monitoring/dashboards-reference/${COMPONENT}/${ID}.json" "${COMPONENT}"
jq 'length' "docs/monitoring/panel-catalog/${COMPONENT}.json"
```
Expected: 打印 `✓ ${COMPONENT}: N 个面板`,`N` > 0。

- [ ] **Step 3: 对所有锁定组件重复 Step 1-2,然后校验覆盖率**

Run(校验:每个锁定且有 ID 的组件都有源 JSON 与 panel-catalog):
```bash
# 锁定且有看板 ID 的组件清单
jq -r 'length as $n | .' docs/monitoring/dashboard-survey-results.json >/dev/null   # sanity
ls docs/monitoring/dashboards-reference/
ls docs/monitoring/panel-catalog/
```
Expected: 两个目录下的组件集合一致,且与选型清单中「✅锁定 + 有 ID」的组件集合一致(逐一肉眼核对组件名)。

- [ ] **Step 4: Commit**

```bash
git add docs/monitoring/dashboards-reference docs/monitoring/panel-catalog
git commit -m "docs(monitoring): 归档选定看板源 JSON 与 panel-catalog(Phase 1 完成)"
```

---

## 验收标准(对照 spec 第 7 节)

- [ ] 22 个组件全部有分级结论(`dashboard-survey-results.json` length=22),且不包含已移除组件,包含 Valkey。
- [ ] 🟢类组件每个有 ≥2 候选 + 打分 + 推荐理由(Task 3 Step 4 校验通过)。
- [ ] 数据源探测每个组件有官网文档证据链接(`nativePrometheus.docUrl` 非空);DATART 按 Spring Boot Actuator/Micrometer 证据链验收。
- [ ] "热度"分基于 grafana.com API 实测数据(候选 `downloads`/`rating` 非空,除确无看板者)。当前 `scores.heat` 已存在,但多个候选 `rating` 为 `null`,需复核 API 返回或调整验收口径。
- [ ] 选型清单锁定后无 `⏳待审核`(Task 4 Step 3 校验通过)。
- [ ] 每个选定看板都有源 JSON + panel-catalog(Task 6 Step 3 校验通过)。

## 不做(留待后续 spec)

后端 PromQL 代理端点、前端 AntV G2 图表、exporter 部署自动化、`ClusterServiceDashboard` 表改造、现有外部看板显示层移除 —— 全部属于 Phase 3。
