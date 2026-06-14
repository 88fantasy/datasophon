# 组件监控看板调研与选型设计(Phase 1)

> 日期:2026-06-14
> 状态:已通过 brainstorming 评审,待 writing-plans 转化为实施计划
> 范围边界:**本 spec 仅覆盖 Phase 1(调研 → 人工选型 → 物料归档),不写任何业务代码**

---

## 1. 背景与最终目标

### 1.1 现状(代码实锤)

- **监控入口**:`datasophon-ui/src/pages/ServiceManage/Instance/Overview/index.tsx` 当前实现为单行 `<iframe src={obj.dashboardUrl}>`,即每个服务实例的"概览"页内嵌一个 Grafana 看板。
- **dashboardUrl 来源**:数据库表 `ClusterServiceDashboard`(实体 `com.datasophon.dao.entity.ClusterServiceDashboard` + 控制器 `ClusterServiceDashboardController#/getDashboardUrl`),URL 带占位符(host/port/clusterId),后端 `PlaceholderUtils.replacePlaceholders` 运行时替换。
- **数据真相**:Grafana 看板自身不产生数据,它查询的是 **Prometheus**(系统已内置 Prometheus 3.12.0 + Alertmanager 0.32.1)。因此"替换 Grafana"替换的是**渲染层**,Prometheus 这个数据源在任何方案下都保留。

### 1.2 最终目标(已锁定的终态架构)

```
React(ECharts)  ──HTTP──>  datasophon-api(新增 PromQL 代理/聚合端点)  ──PromQL──>  Prometheus
                                  ^ 复用现有 PlaceholderUtils / 鉴权 / 集群上下文
Grafana:可作为显示层移除
下载的 Grafana 看板 = 参考资料,产物为 PromQL + 面板元信息,不是整份 JSON 拿去导入
```

关键决策(来自 brainstorming):

1. **目标形态**:原生图表,但**由后端代理 Prometheus**,前端不直连(避开 CORS、不暴露 Prometheus 端点给浏览器、复用后端占位符/鉴权/集群上下文)。
2. **数据源探测原生优先**:现代组件普遍内置 `/metrics` 原生 Prometheus 端点,不必额外部署 exporter;调研第一步是**查官网文档确认「版本参考表里那个具体版本」是否原生暴露 `/metrics`**,仅当原生不支持时才退而找 exporter。
3. **选型按加权评分**:热度 0.3 + 数据源匹配度 0.3 + PromQL 可移植 0.2 + 黄金信号覆盖 0.2。

### 1.3 分期

| 阶段 | 内容 | spec |
|---|---|---|
| **Phase 1(本 spec)** | 调研全量组件 → 人工选型 → 物料归档(源 JSON + panel-catalog) | 本文件 |
| Phase 2(后续) | 用 claude design 基于 panel-catalog 做看板原型 | 独立 spec |
| Phase 3(后续) | 后端 PromQL 代理端点 + 前端 ECharts 图表组件 + `ClusterServiceDashboard` 表改造 | 独立 spec |

---

## 2. 范围:版本参考表全部 25 个组件

来源:`deploy/deployment-standalone.md` 第十节「组件版本参考」。版本号取自该表"当前配置版本"列,作为调研时锁定的目标版本(原生端点支持情况依版本而异)。

> 说明:brainstorming 过程中口头称"27 个",版本表原列 26 个;**本次移除 MinIO(对象存储职责由 Rustfs 替代,不再需要 MinIO)**,最终范围 **25 个**;以本表为准。

| 分组 | 组件(版本) |
|---|---|
| 中间件 | MySQL(8.0.28)、Nexus Repository 3(3.85.0)、Rustfs(1.0.0) |
| 存储/数据库 | HDFS(3.5.0)、YARN(3.5.0)、Hive(4.2.0)、Elasticsearch(9.4.2)、Redis(8.6)、JuiceFS(1.3.1)、Doris(4.0.5) |
| 计算/查询引擎 | Spark3(3.5.8)、Flink(2.2.1)、Kyuubi(1.11.1) |
| 消息/协调 | Kafka(4.3.0)、ZooKeeper(3.8.6) |
| 调度 | DolphinScheduler(3.4.1) |
| 可观测性 | Prometheus(3.12.0)、Alertmanager(0.32.1)、Grafana(13.0.1)、Loki(3.7.2)、Promtail(2.8.11) |
| 网关/注册中心 | APISIX(3.16.0)、Nacos(3.2.2)、Nginx(1.30.2) |
| 内部组件 | DATART(3.6.1) |

> Promtail 2.8.11 已 EOL,官方建议迁移 Grafana Alloy;调研时如实标注,优先级低。

---

## 3. 调研方法(每个组件四步)

### 3.1 数据源探测(原生优先)

联网检索**组件官网文档**,确认在第 2 节锁定的版本上:

- 是否原生暴露 Prometheus `/metrics` 端点?
- 起始支持版本、端点路径、默认端口、是否需开关配置?
- 官方文档链接(可复现证据)。

仅当原生不支持时,才检索是否有成熟 exporter(记录 exporter 名称与仓库)。

### 3.2 看板检索

- **主源**:grafana.com 官方 dashboards API(`https://grafana.com/api/dashboards`)。直接返回**下载量、评分、修订时间、数据源、所绑 exporter/指标体系**,使"热度"与"数据源匹配度"两个打分项客观可取、可复现。
- **辅源**:GitHub、组件官方仓库自带 dashboard。
- 每组件取 **2-3 个候选**。

### 3.3 三级分类

```
🟢 可直接做  : 有数据源(原生端点优先) + 有优质现成看板
🟡 需自建面板: 有数据源,但无匹配的现成看板
🔴 缺数据源  : 既无原生端点、也无合适 exporter(预计极少)
```

每个组件除三级标签外,**始终记录数据源方式**(原生端点 path/port,或 exporter 名,或无)。

### 3.4 加权打分(每个候选看板)

| 维度 | 权重 | 含义 |
|---|---|---|
| 热度 | 0.3 | grafana.com 下载量 / 评分 |
| 数据源匹配度 | 0.3 | 看板指标名是否对应我们实际使用的数据源(走原生端点的组件,看板必须基于**原生指标名**;若候选看板基于旧 exporter 指标名则匹配度低) |
| PromQL 可移植 | 0.2 | 查询语句是否干净、易于移植到后端代理 + 前端图表 |
| 黄金信号覆盖 | 0.2 | 是否覆盖延迟 / 流量 / 错误 / 饱和度 |

加权总分排序,推荐结论附分项得分。

---

## 4. 产出物(三件)

### 4.1 调研报告 `docs/monitoring/dashboard-survey.md`

- **总览表**(25 行):`组件 │ 版本 │ 数据源方式 │ 候选数 │ 分级 │ 推荐看板 │ 总分`
- **每组件详情**:候选看板(名称 / 链接 / Grafana ID / 下载量 / 数据源 / 更新时间)+ 分项打分表 + 推荐结论与理由 + 官方文档证据链接。

### 4.2 选型清单 `docs/monitoring/dashboard-selection.md`

**← 人工审核后锁定**,每组件最终用哪个看板(或标注"需自建 / 缺数据源")。

### 4.3 物料归档(按选型清单)

- 选定看板的源 JSON → `docs/monitoring/dashboards-reference/<COMPONENT>/<grafanaId>.json`
- 规范化**面板目录** → `docs/monitoring/panel-catalog/<COMPONENT>.json`,每个面板记录:

  ```json
  {
    "title": "...",
    "promql": "...",
    "unit": "...",
    "chartType": "timeseries|gauge|stat|...",
    "thresholds": [...],
    "legend": "..."
  }
  ```

  这是交给 Phase 2(claude design)的结构化输入。

---

## 5. 执行编排:清单 → 并行 → 重试 → 合并

> 用户明确要求"分组派子代理并行调研,先生成执行清单,确保每个并行代理出错后能进行重试,当所有代理完成后再合并结果"。该形态对应 **Workflow 工具**(parallel + 结构化 schema 自动重试 + 合并)。

```
1. 生成执行清单 docs/monitoring/dashboard-survey-manifest.json
   ── 25 行:component / version / group / 官网文档线索 / status(pending|done|failed)

2. 每个组件 = 1 个独立可重试单元
   ── 用结构化 schema 输出(schema 不达标自动重试)
   ── 按 8 个分组并行调度,并发上限由 Workflow 自动管控
   ── 可重试粒度 = 单组件(MySQL 失败只重跑 MySQL,不连累同组 Nexus/Rustfs)
   ── "分组"仅用于进度展示与语义

3. 全部完成后合并 → 调研报告(总览三级分类表 + 每组件详情打分)
   ── 失败组件单独重跑,不连累已成功的
```

### 5.1 每个调研 agent 的结构化输出 schema

既是 agent 返回格式,也是报告行格式:

```json
{
  "component": "Kafka",
  "version": "4.3.0",
  "group": "消息/协调",
  "nativePrometheus": {
    "supported": true,
    "sinceVersion": "...",
    "endpoint": "/metrics",
    "port": 9092,
    "docUrl": "https://...",
    "notes": "Kafka 4.x KRaft 自带..."
  },
  "exporterFallback": { "needed": false, "name": null, "repoUrl": null },
  "dataSource": "native",
  "candidates": [
    {
      "name": "...",
      "source": "grafana.com|github|official",
      "url": "https://...",
      "grafanaId": 12345,
      "downloads": 50000,
      "rating": 4.8,
      "datasource": "prometheus",
      "lastUpdate": "2026-xx-xx",
      "scores": { "热度": 0.3, "数据源匹配": 0.3, "promql可移植": 0.18, "黄金信号": 0.16 },
      "total": 0.94
    }
  ],
  "recommendation": { "pick": "...", "reason": "..." },
  "tier": "🟢可直接做|🟡需自建|🔴缺数据源"
}
```

### 5.2 执行边界

真正运行该 Workflow 属于 **Phase 1 的执行动作**;本次 brainstorming 仅产出本 spec。落盘后由 writing-plans 转化为实施计划,执行计划中才实际调起 Workflow 跑 25 个组件。

---

## 6. 明确不做(YAGNI)

以下全部留给 Phase 3 spec,本期一行业务代码都不写:

- 后端 PromQL 代理 / 聚合端点实现
- 前端 ECharts 图表组件
- exporter 部署自动化
- `ClusterServiceDashboard` 表结构改造
- Grafana 进程的移除

---

## 7. 验收标准

- 25 个组件全部有分级结论,无遗漏。
- 🟢/🟡 类组件每个有 ≥2 候选 + 打分 + 推荐理由。
- 数据源探测有官网文档证据链接(可复现),非拍脑袋。
- 报告"热度"分基于 grafana.com API 实测数据(可复现)。
- 选型清单锁定后,每个选定看板都有源 JSON + panel-catalog 落盘。

---

## 8. 关键文件参考

| 用途 | 文件 |
|---|---|
| 现状监控入口(iframe Grafana) | `datasophon-ui/src/pages/ServiceManage/Instance/Overview/index.tsx` |
| dashboardUrl 实体 | `datasophon-api/.../dao/entity/ClusterServiceDashboard.java` |
| dashboardUrl 取值逻辑 | `datasophon-api/.../service/impl/ClusterServiceDashboardServiceImpl.java` |
| 占位符替换 | `PlaceholderUtils.replacePlaceholders` |
| 组件版本参考表(范围来源) | `deploy/deployment-standalone.md` 第十节 |
