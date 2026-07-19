# Doris 监控看板搬迁到服务 Tab + 打通 OTel 采集 任务清单

> 执行者:Codex。产出后由 Claude 检查/审查 + 真实沙箱验证。
> 本文件自包含:含背景、已核实事实、逐任务改动点、验收命令与判据。

## 背景

用户要求把独立的 Doris 监控看板 `/monitor/doris`(挂在"监控看板"菜单下)**移动**到 Doris 服务组件详情页的一个"监控" Tab 上,并"对接上 otel 采集 Doris 监控指标"。

**分支**:从 `main` 新建 `feat/doris-monitor-tab-otel`。

## 已核实关键事实(实现前必读,避免踩坑)

1. **后端 OTel 采集已就绪,不需改动采集侧**:`OtelScrapeConfigBuilder.java` 已为 `DorisFE`(`jmxPortParam=http_port`,默认 **8030**)/`DorisBE`(`jmxPortParam=webserver_port`,默认 **8040**)生成 scrape job,`job` 名 = 角色名 **`DorisFE`/`DorisBE`**,并附加 `group: 'fe'/'be'` label;路径用默认 `/metrics`(`PATH_OVERRIDES` 无 Doris 条目)。`otelcol.ftl` 通过 `${localScrapeJobsYaml}` 注入,无 `prometheus/doris` 写死特例。`ServiceCommandService` 在 `START_SERVICE`/`STOP_SERVICE`/`RESTART_SERVICE`/`START_WITH_CONFIG`/`RESTART_WITH_CONFIG` 成功后自动 `pushNodeConfig` 刷新抓取配置,对 Doris 无排除,**只对 RUNNING 角色生成 job**。
2. **落表/label(与旧文档不同,以当前代码为准)**:`OtelMetricsQueryService.java` 顶部 javadoc 明确标注"D2 实测确认"——`service_name`/`service_instance_id` 是**扁平列**(`INST_EXPR`/`JOB_EXPR` 常量),**不是** `resource_attributes['service']['instance']['id']` 嵌套 MAP 取法。`group`/`type`/`mode`/`path`/`device` 等业务维度落在 `attributes` MAP(`ALLOWED_ATTR_FILTER_KEYS` 白名单已含 `group`/`type`)。Doris 的 `service_name` 值 = scrape job 名 = **`DorisFE`/`DorisBE`**。
3. **⚠️ 核心"对接"缺口 —— job 命名漂移导致前端取数断链**:Phase D(2026-06-22)看板验证通过时,采集用写死的 `prometheus/doris`,FE/BE 指标的 `service_name` 都是 `doris`,前端"单一 job 过滤套用到所有面板"的模型成立。Phase E(2026-06-29/30)把采集统一切到 `OtelScrapeConfigBuilder` 后,job 变成 **`DorisFE`/`DorisBE`**(FE、BE 分属不同 `service_name`),但前端 `useDorisMonitorDashboard.ts:126` 仍是 `const job = variables.cluster || 'doris'`,把单一 job 值同时套给 cluster/fe/be 三段所有面板查询。**这是本次要修的真实断链点**,不只是"挪 UI"。
4. **前端 `clusterId` 硬编码为 1**:`DorisMonitor/index.tsx:121` 调用 `useDorisMonitorDashboard({ ..., clusterId: 1, ... })`;`useDorisMonitorDashboard.ts:80`、`dorisService.ts:247/261` 默认参数也是 `clusterId = 1`。页面无 props、不读 URL/Context。
5. **clusterId 隔离已在连接层保证,后端大概率不需要改动**:`OtelMetricsQueryService.createReader(clusterId)` → `OtelDorisReaderFactory.create(clusterId)` 按 clusterId 构建/复用独立的 `HikariDataSource` 连接池(即每个集群连自己的 Doris 实例),不是靠共享表 WHERE 子句过滤 clusterId。因此**去掉前端 job 过滤后不会引入跨集群串数据风险**——只要 clusterId 参数本身正确透传。
6. **服务详情页 Tab 差异化先例**:`src/pages/Cluster/ServiceInstance/index.tsx:181-185` 已有 `serviceInfo?.serviceName === 'YARN'` 条件渲染专属"资源配置"Tab 的写法。Doris 的 `serviceName` 字面值确认为大写 **`'DORIS'`**(`OtelSchema.SERVICE_NAME = "DORIS"`、`package/raw/meta/datacluster-physical/DORIS/service_ddl.json` 顶层 `"name": "DORIS"`),与 YARN 同构,可直接照搬加一个条件分支。
7. **Tab 上下文来源**:`ServiceInstance/index.tsx:18-23` 通过 `useParams<{ clusterId; instanceId }>()` 拿到 `numericClusterId`/`numericInstanceId`,以 props 显式传给每个子 Tab(`<InstanceTab clusterId={...} instanceId={...} />` 等模式),不用 Context。新增的"监控"Tab 应遵循同样模式,把 `numericClusterId` 以 prop 传给 `DorisDashboard`。
8. **DorisDashboardToolbar 现有 props**(`toolbar/DorisDashboardToolbar.tsx:14-31`):`cluster`/`clusters`/`onClusterChange` 是独立的一组(渲染第一个 `<Select>`,:69-78行),与 FE/BE 实例多选、rateInterval、时间范围各自独立,可以干净地条件跳过而不影响其它控件。
9. **旧独立页路由/菜单位置**:`config/routes.ts:67-72`(`path: '/monitor/doris', name: 'doris-monitor'`),挂在 `/monitor`("监控看板",`routes.ts:40-44`)分组下。菜单文案 `menu.monitor-dashboards.doris-monitor` **只存在于两个 locale 文件**:`src/locales/zh-CN/menu.ts`、`src/locales/en-US/menu.ts`(grep 确认,不是 8 语种都有)。

## 用户已拍板决策

- 旧独立页 `/monitor/doris` **删除**(路由 + 菜单 + i18n key),彻底移动,不保留旧入口。`DorisMonitor` 组件目录本身**保留**(被 Tab 复用)。
- Tab 内集群已由 URL `clusterId` 固定,**隐藏** `DorisDashboard` 自带的顶部集群下拉。

## 依赖关系

**T3 与 (T1 → T2) 完全并行,不冲突文件**。T1 必须先于 T2(T2 引用 T1 改造后的 `DorisDashboard` props)。T2 内部,路由/菜单删除子任务与 Tab 渲染子任务互不阻塞,可同时做。

---

## Task 1 — 前端数据流:clusterId 参数化 + embedded 模式 + 修 job 漂移(核心)

**目标**:`DorisDashboard` 可接收 `clusterId`、以 embedded 形态渲染(隐藏集群下拉),并让 FE/BE 各面板在 Phase E 新 job 命名下正确取数,不再依赖已失效的单一 job 过滤。

**改动文件**:
- `datasophon-ui-v2/src/pages/monitor/DorisMonitor/index.tsx`
- `datasophon-ui-v2/src/pages/monitor/DorisMonitor/hooks/useDorisMonitorDashboard.ts`
- `datasophon-ui-v2/src/pages/monitor/DorisMonitor/toolbar/DorisDashboardToolbar.tsx`
- 如实测需要:`datasophon-ui-v2/src/pages/monitor/_shared/useDorisDashboardData.ts`、`datasophon-ui-v2/src/pages/monitor/_shared/dorisService.ts`

**步骤**:

1. `DorisDashboard`(`index.tsx`)由无参组件改为接收 `props: { clusterId?: number; embedded?: boolean }`,默认 `clusterId = 1`、`embedded = false`(保持向后兼容,防止遗漏引用)。删除 `index.tsx:121` 硬编码的 `clusterId: 1`,改为透传 `props.clusterId`。

2. `embedded === true` 时,给 `DorisDashboardToolbar` 新增并传入 `hideClusterSelect` prop,内部条件跳过渲染集群 `<Select>`(:69-78 行那一块),保留 FE/BE 实例多选、rateInterval、时间范围、刷新按钮不变。`DorisDashboardToolbarProps` 增加 `hideClusterSelect?: boolean`。

3. **修 job 漂移(本任务的核心)**:`useDorisMonitorDashboard.ts:126` 的 `const job = variables.cluster || 'doris'` 不再作为过滤条件下发给 `useDorisDashboardData`——传 `job: undefined`(或直接删除这个变量,视 `useDorisDashboardData` 签名要求)。

   - 需先确认 `dorisService.ts` 的 `queryDorisInstant`/`queryDorisRange`(`job?: string` 可选参数,`...rest` 透传给 `request()`)在 `job` 为 `undefined` 时不会拼出 `job=undefined` 查询串(Umi `request` 对 `undefined` value 的 param 默认会跳过,但**必须实测确认**,不要凭经验假设)。
   - `fetchDorisLabels` 调用(枚举 FE/BE 实例下拉候选值,:91-92 行)不受影响,继续按 metric 名枚举,与 job 无关。
   - 集群相关 UI 状态(`selectedCluster`/`clusters`/`effectiveCluster`)在隐藏下拉后不再驱动任何查询过滤,可以精简掉相关 `useEffect`/`useState`(如 `index.tsx:125-131`、`useDorisMonitorDashboard.ts` 里 `clusters` state 的赋值逻辑),但这是次要清理——**底线是 job 不能再被发送**,精简范围以不引入新 bug 为界,拿不准就保守只做 job 改动。
4. 更新 `panelQueries.test.ts` / `hooks/useDorisMonitorDashboard.test.ts`:新增/改断言锁定"不再向后端发送 `job` 参数"以及"embedded 模式下工具栏隐藏集群下拉"。

**验收命令**:

```bash
cd datasophon-ui-v2
npm run lint
npm run test
```

**判据**:全绿;新增单测明确断言 job 不再被发送(而不是断言发送某个具体值,避免测试与实现耦合过紧)。

---

## Task 2 — 前端集成:ServiceInstance 新增"监控"Tab + 下线旧独立页

**目标**:Doris 服务详情页出现"监控"Tab,展示 embedded 版 DorisDashboard;`/monitor/doris` 独立入口彻底下线。

**依赖**:Tab 渲染子任务依赖 Task 1 产出的 `DorisDashboard` props 接口,建议 Task 1 完成/联调通过后再接入;路由/菜单删除子任务无依赖,可提前做。

**改动文件**:
- `datasophon-ui-v2/src/pages/Cluster/ServiceInstance/index.tsx`
- `datasophon-ui-v2/config/routes.ts`
- `datasophon-ui-v2/src/locales/zh-CN/menu.ts`
- `datasophon-ui-v2/src/locales/en-US/menu.ts`

**步骤**:

1. 【依赖 Task1】在 `ServiceInstance/index.tsx` 物理集群分支(:181-185 行,`serviceInfo?.serviceName === 'YARN'` 条件旁)新增:

   ```tsx
   {serviceInfo?.serviceName === 'DORIS' && (
     <Tabs.TabPane tab="监控" key="monitor">
       <DorisDashboard clusterId={numericClusterId} embedded />
     </Tabs.TabPane>
   )}
   ```

   顶部新增 `import DorisDashboard from '@/pages/monitor/DorisMonitor';`(或相对路径,视组件默认导出方式)。`serviceName === 'DORIS'` 已由 `OtelSchema.SERVICE_NAME`/DDL 确认为大写字面值,无需再猜测。

2. 【无依赖】删除 `config/routes.ts:67-72` 的 `/monitor/doris` 路由项(注意其 `routes` 数组内其它监控页 `apisix`/`zookeeper`/`dolphinscheduler`/`mysql` 等条目保持不变,只删 doris 这一段;也检查 `:46-48` 的 `redirect: '/monitor/apisix'` 不受影响,无需改)。

3. 【无依赖】删除 `src/locales/zh-CN/menu.ts` 和 `src/locales/en-US/menu.ts` 里的 `menu.monitor-dashboards.doris-monitor` key(grep 确认只有这两个文件含该 key,其余 locale 文件不需要改)。

4. `src/pages/monitor/DorisMonitor/` 目录与 `src/locales/*/dorisMonitor.ts` 保留不动(供 Tab 复用)。

**验收命令**:

```bash
cd datasophon-ui-v2
npm run lint
npm run test
grep -rn "monitor/doris\|doris-monitor" src config
```

**判据**:`npm run lint`/`npm run test` 全绿;最后一条 grep 命令的结果只应剩下 `src/pages/monitor/DorisMonitor/` 自身目录内的引用和 `ServiceInstance/index.tsx` 里新加的 import,不应再有 `config/routes.ts` 或 `menu.ts` 命中。

---

## Task 3 — 后端 clusterId 隔离核实(与 Task 1/2 完全并行)

**目标**:在前端不再发送单一 `job` 过滤的前提下,书面确认 Doris 指标查询不会跨集群串数据,并核实 FE/BE 实例枚举在新 job 命名下仍可用。**预期结论是"无需改代码,补一份核实说明"**,只有实测发现连接层隔离不生效才需要动代码。

**核对/改动文件**:
- 只读核对:`datasophon-api/src/main/java/com/datasophon/api/observability/OtelMetricsQueryService.java`
- 只读核对:`datasophon-api/src/main/java/com/datasophon/api/observability/OtelDorisReaderFactory.java`
- 只读核对:`datasophon-api/src/main/java/com/datasophon/api/observability/OtelScrapeConfigBuilder.java`
- 如需补测试:`datasophon-api/src/test/java/com/datasophon/api/observability/OtelMetricsQueryServiceTest.java`(若已存在同名测试类,追加用例;不存在则说明现状,不强制新建)

**步骤**:

1. 阅读 `OtelMetricsQueryService.createReader(clusterId)`(:895-896 行)→ `OtelDorisReaderFactory.create(clusterId)`,确认查询确实通过"按 clusterId 建立/复用独立连接池"实现隔离(而非共享表 + WHERE clusterId),记录连接池 key 的组成(`PoolKey` 结构)、以及 clusterId 对应不到有效 Doris 实例时的 fallback 行为(`fallbackHost`/`fallbackPort` 配置项)。

2. 确认 `fetchDorisLabels('doris_fe_query_total', clusterId)` / `fetchDorisLabels('doris_be_memory_allocated_bytes', clusterId)`(前端 `useDorisMonitorDashboard.ts:91-92`)在 Phase E 新 job 命名(`DorisFE`/`DorisBE`)下,`queryLabels` 返回的 `instances` 列表依然能正确枚举出 FE/BE 主机(不依赖已废弃的单一 job 值)。

3. 产出结论:若第 1、2 步验证通过,在本文档末尾"核实结论"一节(见下)写明"clusterId 隔离由连接层保证,已验证,无需改代码";若发现反例(如 fallback 模式下多集群共用一个连接、labels 接口枚举漏掉某类实例),补代码修复并同步更新单测。

**验收命令**:

```bash
JAVA_HOME=$JH21 ./mvnw -pl datasophon-api -am test -Dtest=OtelMetricsQueryServiceTest -s ~/.m2/setting.xml
JAVA_HOME=$JH21 ./mvnw -pl datasophon-api spotless:check -s ~/.m2/setting.xml
```

**判据**:相关测试全绿(若为纯核实无代码改动,可跳过测试命令,但需在文档"核实结论"一节写明依据的具体代码行);spotless 通过(仅当有代码改动时适用)。

---

## 核实结论(Codex 实现后回填)

> 按 ZooKeeper/RustFS 先例:若真实沙箱数据推翻本文档任何推断(如 `service_name` 实际不是 `DorisFE`/`DorisBE`、`attributes` 位置不同),在此处记录并说明代码已如何据此修正,不要静默覆盖原描述。

- Task 3 核实结论:已静态核实，常规运行时 `OtelMetricsQueryService.createReader(clusterId)` 直接委托 `OtelDorisReaderFactory.create(clusterId)`；后者按该 clusterId 查询 RUNNING `DorisFE`、读取该集群的 `DORIS.query_port` 与 reader 凭据，再按 `(host, port, user, password)` 复用连接池。因此不同集群不会通过共享 OTel 表串数据，`queryLabels(metric, clusterId)` 同样经该 reader 查询 gauge/sum 两表，且未传 job 时不加 `service_name` 条件，能同时枚举 `DorisFE`、`DorisBE` 的实例。无需改后端代码。
- 其它偏差记录: `datasophon.otel.doris.fallback-host` 是明确标注为开发/测试直连的全局兜底；配置后会跳过 clusterId 注册表查询，多个 clusterId 都会连到同一 fallback Doris，不能作为多集群隔离模式使用。该模式不适用于生产多集群，本次未改变其既有语义。静态核实还确认 `OtelScrapeConfigBuilder` 仅为 RUNNING 角色生成 job，job_name 为 `DorisFE`/`DorisBE`，并分别写入 `group: fe`/`group: be` 和默认 `/metrics` 路径。
- **Claude 真实沙箱验证结论(2026-07-16,已完成,非静态推断)**:把 `deploy/compose/conf/otelcol-juicefs.yaml` 的 `prometheus/doris` receiver 由 Phase D 遗留的单一 `job_name: doris` 改为镜像生产 `OtelScrapeConfigBuilder` 的 `job_name: DorisFE`/`job_name: DorisBE`(各自 `group: fe`/`group: be`),起 `docker-compose.observability.yml` 沙箱验证。**结果:真实数据 100% 印证本文档假设**——`otel_metrics_sum`/`otel_metrics_gauge` 新写入行的 `service_name` 确为 `DorisFE`/`DorisBE`(非旧 `doris`),`attributes['group']` 为 `fe`/`be`。用真实数据对照查询:`WHERE metric_name='doris_be_memory_allocated_bytes' AND attributes['group']='be' AND service_name REGEXP 'doris'`(模拟修复前的单一 job 过滤)命中 **0 行**;去掉 `service_name` 过滤(修复后行为)命中 **20 行**——完整复现了任务背景所述断链,并证明修复生效。另确认 `panelQueries.ts` 全部 32 个面板本来就已带 `filters.group` 或用 `roleName` 天然区分角色(`jvm_heap_size_bytes` 也确认只有 `DorisFE` 上报),故去掉 job 过滤不引入 FE/BE 串数据风险。**沙箱运维提示(与代码无关)**:该 Doris 卷含 8 天前的历史 schema,`dynamic_partition.end=1` 的动态分区在容器长时间停机期间未跟上,需要 FE 重启后等一次 `dynamic_partition_check_interval_seconds`(默认 600s,验证时临时调至 5s 加速,验证完已恢复默认)才会补建当天分区,否则新数据会 Stream Load 失败(`no partition for this tuple`)。

---

## Claude 负责的验证(实现完成后,不在本清单范围内执行,仅记录以便交接)

1. 静态审查三个 Task 的 diff。
2. 用 `deploy/compose/docker-compose.observability.yml` 观测沙箱 + 真实 Doris FE/BE 容器,`mysql -h127.0.0.1 -P9030 -uroot` 核对 `otel_metrics_*` 表 `service_name` 确为 `DorisFE`/`DorisBE`,且各面板指标(`doris_be_disks_local_used_capacity`、`doris_fe_query_total`、`jvm_heap_size_bytes` 等)在去掉 job 过滤后仍能查到数据。
3. 浏览器端到端:Doris 服务详情页 →"监控"Tab → cluster/fe/be 三段面板均有数据(尤其验证 BE 段,这是 job 漂移最先暴露断链的地方);确认 `/monitor/doris` 已无路由/菜单。

全部证据齐备后，方可标记该监控迁移已完成。
