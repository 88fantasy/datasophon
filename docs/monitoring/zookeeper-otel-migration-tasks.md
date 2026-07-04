# ZooKeeper 监控看板迁移任务清单(Prometheus → OTel + Doris)

> 执行者:Codex。产出后由 Claude 检查/审查。
> 本文件自包含:含背景、已核实事实、逐任务改动点、验收命令与判据。

## 背景

可观测重构 epic(`refactor/observability-otel`)已把 metrics 采集换成 OTel Collector 直写 Doris。
`datasophon-ui-v2` 的 ZooKeeperMonitor 仍走 `PrometheusProxyV2Controller` 透传 Prometheus,过渡期无数据。
本任务把它迁到"查 Doris `otel_metrics_*` 表"取数(与 RustFS/JuiceFS/Nexus 看板对齐),恢复数据,**视觉布局不变**。

**分支**:从 `main` 新建 `feat/zookeeper-monitor-otel-doris`。已创建并切换到该分支。

## ⚠️ 2026-07-04 真实沙箱验证结果(已用 docker compose 搭建,详见 `docs/monitoring/zookeeper-otel-verification.md`)

以下"已拍板决策"1-4 中有两处推断被真实数据证伪,代码已按验证结果修正,不要再按最初的决策 2/3 实现:

- **决策 1(Z22 summary count-rate)基本正确**,唯一偏差是 metric_name 多写了 `_count` 后缀——真实
  metric_name 是 `jvm_gc_collection_seconds`(不带后缀),已修正。
- **决策 2 完全错误**:`election_time_sum`/`fsynctime_sum`/`snapshottime_sum`/`jvm_pause_time_ms_sum`
  实测 `/metrics` 端点均为 `# TYPE ... summary`(真 Prometheus Summary,非独立 Counter),真实 metric_name
  不带 `_sum` 后缀。已改为 `table=summary,field=sum`,metric 去掉 `_sum` 后缀。
- **决策 3 部分错误**:`packets_received`/`packets_sent` 实测为 `# TYPE ... gauge`(不是 counter),
  已去掉 Z13 的 `table:'sum'`,恢复默认 gauge。其余 4 个(connection_rejected 等)+ 3 个(commit/snap/
  proposal_count)确认是真 counter,决策 3 对这 7 个是对的。
- 额外发现一个**采集侧真实缺陷**(超出本任务范围,未修复):`otel_metrics_summary` 表的 Stream Load 会被
  同批次任意一个 NaN 分位数值整体拖垮(ZK 大量低频 processor 指标零观测时输出 NaN),生产
  `otelcol.ftl` 同样没有 NaN 处理,详见验证文档"⚠️ 采集侧发现的真实缺陷"节。

新增沙箱资产(已提交,可复用):`deploy/compose/docker-compose.observability.yml` 的 `obs-zookeeper` 服务 +
`deploy/compose/conf/otelcol-juicefs.yaml` 的 `prometheus/zookeeper` 抓取任务(含验证专用白名单 relabel)。

## 已拍板决策(本次由 Claude 按现状推断拍板,Codex 实现时按此执行;如与真实环境冲突以 Task 0 验证结果为准并回填此文档)

> **以下 1-4 条为实现前的初始推断,已被上方"真实沙箱验证结果"部分修正/证实,请以验证结果为准。**

1. **Z22(GC Collection Rate)需要新的"summary 表 count-rate"能力**:`jvm_gc_collection_seconds_count`
   来自 Java simpleclient `hotspot.GarbageCollectorExports`,以 **Prometheus `Summary`** 类型注册
   (即使不配置分位数,仍会带 `# TYPE jvm_gc_collection_seconds summary`),OTel prometheusreceiver 会把它
   落进 `otel_metrics_summary` 表而非 `otel_metrics_sum`。该表已有 `count BIGINT`/`sum DOUBLE` 列(与
   `otel_metrics_histogram` 结构一致),但现有 `buildRangeSummarySql` 只读 `quantile_values`(此指标未配置
   分位数,该数组为空),读不到 `count`。**需仿照 `buildRangeHistogramFieldRateSql` 新增等价的 summary
   count-rate 查询路径**(见 Task 1.2)。
2. **其余 `_sum` 后缀指标按 ZK 自有 Counter 处理,非 Prometheus Summary**:`election_time_sum` /
   `fsynctime_sum` / `snapshottime_sum` / `jvm_pause_time_ms_sum` 是 ZooKeeper 自身 `SummarySet`/
   `JvmPauseMonitor` 指标体系导出的**独立 Counter**(与 `avg_latency`/`min_latency`/`max_latency` 是各自
   独立指标同理),**不是** Prometheus Summary 类型的自动 `_sum` 后缀,因此落 `otel_metrics_sum` 表,直接用
   现有 `table=sum` + `rateWindow` 能力(`buildRangeRateSql`),**不需要新代码**。
3. **`packets_received`/`packets_sent`/`connection_rejected`/`connection_drop_count`/
   `unrecoverable_error_count`/`digest_mismatches_count`/`commit_count`/`snap_count`/`proposal_count`
   是普通 Counter**,落 `otel_metrics_sum` 表。原 Prometheus 面板(Z13/Z15/Z18)直接读原始累计值(不做
   rate),迁移后用 `table=sum` **不传 `rateWindow`**(即走 `buildRangeGaugeSql` 对 `otel_metrics_sum` 表原样
   取值,语义与原面板一致,详见 §9.4 联调注释"cumulative")。
4. **JVM 基础指标(threads/deadlocked/memory pool/open fd)走 hotspot `DefaultExports`/
   `MemoryPoolsExports`/`ThreadExports`,均为真 Gauge**,落 `otel_metrics_gauge`,`table` 用默认值,无需改动。
5. **`fetchDorisLabels` 枚举 instance/job 用 `jvm_threads_current` 作基准指标**(而非 `leader_uptime` 或
   `quorum_size`):后两者语义上只应由 leader/健康法定节点上报,枚举出的 instance 列表可能不全;
   `jvm_threads_current` 来自 JVM 进程级 hotspot 指标,每个存活节点必上报,更适合做标签枚举基准
   (同 RustFS 用 `rustfs_process_uptime_seconds`、Nexus 用 `jvm_vm_uptime` 的先例)。
6. **验证门禁 = 推导 + 交付 SQL**(当前无运行中的观测沙箱 + ZooKeeper 可核对,§已确认无 docker 容器在跑)。
   按上述推断实现,产出验证文档待真实环境回填结论。

## 已核实关键事实(实现前必读,避免踩坑)

1. **采集侧零改动**:`ZOOKEEPER/ZkServer` 角色(`package/raw/meta/datacluster-physical/ZOOKEEPER/service_ddl.json`
   L15)已声明 `jmxPortParam: "metricsProvider.httpPort"`(默认 `7000`),`OtelScrapeConfigBuilder` 自动纳入
   抓取,`job_name` = 角色名 `ZkServer`。抓取路径用 `DEFAULT_METRICS_PATH = "/metrics"`
   (`OtelScrapeConfigBuilder.java` 无 ZooKeeper 的 `PATH_OVERRIDES` 条目,与 ZK 原生
   `PrometheusMetricsProvider` 默认路径一致)。**不改 `OtelScrapeConfigBuilder`。**
2. **落表/label**:`service_name='ZkServer'`(顶层列,抓取型 job 名);`instance` →
   `resource_attributes['service']['instance']['id']`(标准 prometheusreceiver 语义,与 JuiceFS/Nexus 一致);
   `pool`(JVM 内存池名)、`gc`(GC 名)→ 普通 `attributes` VARIANT。
3. **后端白名单缺口**:`OtelMetricsQueryService.java` L88–96 的 `ALLOWED_ATTR_FILTER_KEYS` 与
   `INSTANT_SERIES_ATTR_KEYS` **不含 `pool`/`gc`**,必须两处都补(否则 Z21/Z22 的 `groupBy` 被
   `toValidGroupBy` 静默过滤掉,退化成单一序列)。
4. **本次唯一新增查询能力 = summary 表 count-rate**(Z22 专用,见"已拍板决策"1)。**没有 histogram 相关工作**
   ——ZooKeeper 面板中没有任何真正的 Prometheus Histogram 指标,不涉及 `otel_metrics_histogram`。
5. **`ZKDashboardToolbar.tsx` 是跨看板共享组件,禁止删除/改签名/搬迁**:被 `ApisixMonitor`、`MySQLMonitor`、
   `NexusMonitor`、`RustfsMonitor` 的 `index.tsx` 直接 `import ... from '../ZooKeeperMonitor/toolbar/ZKDashboardToolbar'`
   引用。其 props(`instances`/`selectedInstances`/`onInstancesChange`/`jobs`/`selectedJobs`/`onJobsChange`
   等)与数据源无关,Doris 迁移**不需要改这个文件**,只需保证 `useZKDashboard` 仍然吐出同形状的
   `instances: string[]` / `jobs: string[]`。
6. **`replaceZKVars` 整体删除**:Doris 描述符走结构化 `filters`/`groupBy` 参数,不再拼接 PromQL 字符串,
   该函数与 PromQL 字符串替换机制一起废弃(仿 JuiceFS/RustFS 迁移,不保留 `promql` 字段)。
7. **共享层能力已就绪**,直接复用:`_shared/dorisService.ts`(`queryDorisInstant`/`queryDorisRange`/
   `fetchDorisLabels`)、`_shared/useDorisDashboardData.ts`(`instant`/`multi-range` 两种描述符,含并发限流、
   `mergeNamedSeries` 正确拼接 groupBy 维度到 series 标签、`series_key` 分区已修复历史串线 bug)。

---

## 任务清单

### Task 0 — 验证文档(不阻塞后续)

- [x] 新建 `docs/monitoring/zookeeper-otel-verification.md`,用 docker compose 真实沙箱跑通(非"仿 RustFS 待回填"):
  - [x] Step1:三张表 COUNT(*) 已确认(gauge/sum 表数千行;summary 表需绕开采集侧 NaN 缺陷才有数据,详见验证文档)。
  - [x] Step2:枚举三张表 `DISTINCT metric_name` 完成,**发现两处推断错误**(见上方"真实沙箱验证结果")。
  - [x] Step3:`jvm_memory_pool_bytes_used` 含 `pool`、`jvm_gc_collection_seconds` 含 `gc` 均已确认。
  - [x] Step4:面板指标存在性逐一核对(quorum_size/leader_uptime/learners/synced_observers 因单节点沙箱
    无法验证,标注为已知局限,非代码缺陷)。
- **判据**:文档产出且结论均为真实回填(非占位)。✅达成。

### Task 1 — 后端(datasophon-api)

改文件:`observability/OtelMetricsQueryService.java`、`controller/v2/OtelMetricsQueryController.java`、
`observability/OtelMetricsQueryServiceTest.java`。

- [x] **1.1 白名单**:`ALLOWED_ATTR_FILTER_KEYS` 与 `INSTANT_SERIES_ATTR_KEYS` **两处**各加 `"pool", "gc"`。
  真实沙箱确认 `pool`/`gc` 属性键名与实测一致。
- [x] **1.2 summary 表 count-rate 能力**(Task 0 验证确认"已拍板决策 1"成立,已实现):
  - 现有 `buildRangeHistogramFieldRateSql(String field, ...)`(L570)硬编码 `FROM otel.otel_metrics_histogram`。
    **重构为接受表名参数**(如加一个 `otelTable` 形参,仿 `buildRangeGaugeSql`/`buildRangeRateSql` 已有的
    表参数化写法),使同一份 CTE(`series_key` 分区 + reset 守卫)可同时服务 histogram 和 summary 两张表——
    两表列结构完全一致(`count BIGINT`/`sum DOUBLE`/`attributes`/`metric_name`/`timestamp`)。
  - Z22 只需要 `field="count"` 分支,不需要 `field="sum"`,但保持函数签名通用(不为 Z22 单独砍能力)。
  - 命名建议:重命名为 `buildRangeFieldRateSql(String field, ..., String otelTable)`,调用处传
    `"otel_metrics_histogram"` 或 `"otel_metrics_summary"`。
- [x] **1.3 接入 `queryRange` 分发**:`summary`+field-rate 分支已加在 `histogram` 分支之前,`summary`
  纯分位数分支保持不变。已用真实 Doris 数据手工执行生成的 SQL 验证 rate 计算正确(见验证文档 Step4)。
- [x] **1.4 Controller**:`field` 参数透传到 `table=summary` 场景无需改动,dispatch 顺序已核对正确。
- [x] **1.5 测试**:
  - [x] `rangeSummaryFieldRate_count_useSummaryTableAndSeriesKeyPartition` 已加(顶层+嵌套类各一份)。
  - [x] `allowedAttrFilterKeys_includeZooKeeperJvmDimensions` 已加,纳入 `pool`/`gc`。
- **验收**:`JAVA_HOME=$JH21 ./mvnw -pl datasophon-api -am test "-Dtest=OtelMetricsQueryServiceTest,OtelMetricsQueryServiceTest\$*,OtelMetricsQueryControllerTest" -s ~/.m2/setting.xml` 64+8=72 个测试全绿(**注意**:`-Dtest=OtelMetricsQueryServiceTest` 不带 `$*` 通配符时 Surefire 默认不跑 3 个 `@Nested` 内部类,只会显示 7/64,是本项目已知的 Surefire 过滤器坑,不是测试变少);`spotless:check` 通过。

### Task 2 — 前端(datasophon-ui-v2)

目录 `datasophon-ui-v2/src/pages/monitor/ZooKeeperMonitor/`,**照 `RustfsMonitor/` 结构改写**
(descriptor + 共享 hook,取数走 `_shared/dorisService.ts` + `_shared/useDorisDashboardData.ts`)。
**`index.tsx` 的 JSX 布局、`toolbar/ZKDashboardToolbar.tsx`、颜色/阈值/formatter 常量一律不动**——
只改数据来源。

- [x] **2.1 `panelQueries.ts` 重写**(删 `replaceZKVars` 与所有 `promql` 字符串字段,改
  `Record<string, DorisPanelDescriptor>`,`import type { DorisPanelDescriptor } from '../_shared/dorisService'`)。
  23 个面板:

  | ID  |            面板             |       类型       |                                                      metric / 关键字段                                                       |
  |-----|---------------------------|----------------|--------------------------------------------------------------------------------------------------------------------------|
  | Z01 | Quorum Size               | instant        | `quorum_size`,agg=max                                                                                                    |
  | Z02 | Leader Uptime             | instant        | `leader_uptime`,agg=max                                                                                                  |
  | Z03 | JVM Threads Current       | instant        | `jvm_threads_current`,agg=max                                                                                            |
  | Z04 | JVM Threads Deadlocked    | instant        | `jvm_threads_deadlocked`,agg=max                                                                                         |
  | Z05 | Alive Connections         | instant        | `num_alive_connections`,agg=sum                                                                                          |
  | Z06 | Open File Descriptors     | instant        | `open_file_descriptor_count`,agg=max                                                                                     |
  | Z07 | Outstanding Requests      | multi-range(1) | `outstanding_requests`(gauge,by instance 天然多序列)                                                                          |
  | Z08 | Latency (max/avg/min)     | multi-range(3) | `max_latency`/`avg_latency`/`min_latency`(gauge)                                                                         |
  | Z09 | Sessions (global/local)   | multi-range(2) | `global_sessions`/`local_sessions`(gauge)                                                                                |
  | Z10 | Znodes                    | multi-range(2) | `znode_count`/`ephemerals_count`(gauge)                                                                                  |
  | Z11 | Approximate Data Size     | multi-range(1) | `approximate_data_size`(gauge)                                                                                           |
  | Z12 | Watch Count               | multi-range(1) | `watch_count`(gauge,by instance)                                                                                         |
  | Z13 | Packets (Recv/Sent)       | multi-range(2) | `packets_received`/`packets_sent`,**默认 gauge**(实测 `# TYPE ... gauge`,非 counter,不带 table/rate)                            |
  | Z14 | Alive Connections (Trend) | multi-range(1) | `num_alive_connections`(gauge,by instance)                                                                               |
  | Z15 | Connection & Data Errors  | multi-range(4) | `connection_rejected`/`connection_drop_count`/`unrecoverable_error_count`/`digest_mismatches_count`,table=sum,**无 rate** |
  | Z16 | Election Time             | multi-range(1) | `election_time`,table=summary,field=sum,rate=1m(**实测为 Summary,非 `_sum` 后缀 Counter**)                                     |
  | Z17 | Learners/Synced Observers | multi-range(2) | `learners`/`synced_observers`(gauge)                                                                                     |
  | Z18 | Quorum Counts             | multi-range(3) | `commit_count`/`snap_count`/`proposal_count`,table=sum,**无 rate**                                                        |
  | Z19 | Fsync Time                | multi-range(1) | `fsynctime`,table=summary,field=sum,rate=1m,by instance(**实测为 Summary**)                                                 |
  | Z20 | Snapshot Time             | multi-range(1) | `snapshottime`,table=summary,field=sum,rate=5m,by instance(**实测为 Summary**)                                              |
  | Z21 | JVM Memory Pool Usage     | multi-range(1) | `jvm_memory_pool_bytes_used`(gauge),groupBy=['pool']                                                                     |
  | Z22 | GC Collection Rate        | multi-range(1) | `jvm_gc_collection_seconds`,table=summary,field=count,rate=5m,groupBy=['gc']                                             |
  | Z23 | JVM GC Pause Time         | multi-range(1) | `jvm_pause_time_ms`,table=summary,field=sum,rate=5m,by instance(**实测为 Summary**)                                         |

  > "由 instance 天然多序列"的面板(Z07/Z12/Z14/Z19/Z20/Z23)**不需要**显式 `groupBy`——后端 SELECT 已含
  > `instance`/`job` 列,多节点自然产生多条 series,与 RustFS R13/R14/R17 同理。
  > Z16/`election_time` 在单节点沙箱下 count 恒为 0(standalone 模式不发生选举),真实多节点集群才有非零值,
  > 且受"采集侧真实缺陷"影响(NaN 拖垮整批 summary 写入),详见验证文档。

- [x] **2.2 `hooks/useZKDashboard.ts` 重写**:
  - [x] `useDashboardData` → `useDorisDashboardData`(单次拉全部 23 面板)。
  - [x] instance/job 下拉:`fetchDorisLabels('jvm_threads_current')` 派生(见"已拍板决策 5")。
  - [x] 保持返回形状不变,`index.tsx` 无需改动解构逻辑。
- [x] **2.3 `index.tsx`**:确认无需改动,已用 `git diff` 核实未被触碰。
- [x] **2.4 `panelQueries.test.ts`** 改写:23 个 panelId 全定义;Z15/Z18 `table:'sum'` 不带 rate;
  Z13 确认默认 gauge(不带 table/rate);Z16/Z19/Z20/Z23 `table:'summary'`+`field:'sum'`+对应 rate;
  Z21/Z22 `groupBy` 正确,Z22 `table:'summary'`+`field:'count'`。删除 `replaceZKVars` 测试。
- [x] **2.5 locale**:`src/locales/{zh-CN,en-US}/zookeeperMonitor.ts` 确认未改动,面板标题/数量不变。
- **验收**:`npm run test`(vitest)149/149 全绿;`npm run lint`(biome+tsc)0 错误。真实结果,非自报。

### Task 3 — 端到端验证(有环境时)

- [x] 用 `deploy/compose/docker-compose.observability.yml`(新增 `obs-zookeeper` + otelcol
  `prometheus/zookeeper` 抓取任务)搭建真实沙箱,`zkCli.sh` 打真实读写流量。
- [x] 逐面板确认 Doris 有数据:gauge 表 7000+ 行覆盖 Z01-Z14/Z17/Z21 全部相关指标(quorum_size/
  leader_uptime/learners/synced_observers 因单节点无 quorum 缺失,标为已知局限);sum 表覆盖 Z15/Z18;
  summary 表(绕开采集侧 NaN 缺陷后)覆盖 fsynctime/snapshottime/jvm_gc_collection_seconds。
- [x] Z22 落表推断(otel_metrics_summary)**确认成立**,唯一偏差是 metric_name 多写了 `_count` 后缀,已修正。
  额外发现 Z16/Z19/Z20/Z23 的"独立 Counter"假设是错的,已按真实类型改为 `table=summary,field=sum`;
  Z13 的"counter"假设也是错的,已改回默认 gauge。
- [x] 手工执行 `buildRangeFieldRateSql` 生成的 SQL 模板(summary 表 + groupBy=['gc'])直接对 Doris 跑,
  验证 rate 计算与 `gc` 维度拆分均正确(见验证文档 Step4)。
- [x] 回填 Task 0 验证文档结论——已用真实数据全部回填,无"待回填"占位。

> 状态:已完成(2026-07-04)。额外发现并记录一个采集侧真实缺陷(NaN 拖垮 otel_metrics_summary 整批写入),
> 超出本任务范围,已在验证文档单独标注,未修复,需团队后续决策。

---

## 复用清单(不要重写)

- 后端:`OtelMetricsQueryService`/`OtelMetricsQueryController`(仅加白名单键 + summary field-rate 能力)。
- 前端取数:`_shared/dorisService.ts`、`_shared/useDorisDashboardData.ts`。
- 前端 UI:`ZooKeeperMonitor/index.tsx`(布局/颜色/阈值/formatter 全部保留)、
  `ZooKeeperMonitor/toolbar/ZKDashboardToolbar.tsx`(**禁止修改**,见"已核实关键事实 5")。
- locale:`src/locales/{zh-CN,en-US}/zookeeperMonitor.ts`(预计免改)。
- 模板参考:`RustfsMonitor/`(最新纯 Doris 看板,instant+multi-range 组合的最佳范例)。

## 已知局限(不在本任务修)

- Z22 的 summary count-rate 是本次唯一新增后端能力,若真实环境指标落表与推断不符需按 Task 3 回退。
- 单节点沙箱验证不到多节点场景下 `by instance` 多序列是否正确(与历史 RustFS/JuiceFS 迁移同样局限)。

## Claude 审查要点(实现后)

1. 白名单两处集合都改了(`pool`/`gc` 漏一处则 Z21/Z22 的 `groupBy` 被静默丢弃退化成单一序列)。
2. Z13/Z15/Z18 **确认没有**误加 `rateWindow`(它们是"显示累计值"语义,不是 rate)。
3. Z22 的新 summary field-rate 查询是否正确复用了 histogram field-rate 的 `series_key` 分区 + reset 守卫
   (否则多 GC 收集器名称跨 series 串线)。
4. Task 0 验证文档的 Step2① 结论是否与"已拍板决策 1"一致;若不一致,确认 Task 3 的回退是否已执行。
5. `index.tsx`/`ZKDashboardToolbar.tsx` 确实未被改动(除非类型报错才做最小修补)。
6. 后端 java 测试 + 前端 vitest/lint/tsc 全绿;spotless 通过。

