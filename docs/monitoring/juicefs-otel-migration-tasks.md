# JuiceFS 监控看板迁移任务清单(Prometheus → OTel + Doris)

> 执行者:Codex。产出后由 Claude 检查/审查。
> 本文件自包含:含背景、已核实事实、逐任务改动点、验收命令与判据。

## 背景

可观测重构 epic(`refactor/observability-otel`)已把 metrics 采集换成 OTel Collector 直写 Doris。
`datasophon-ui-v2` 的 JuiceFSMonitor 仍走 `PrometheusProxyV2Controller` 透传 Prometheus,过渡期无数据。
本任务把它迁到"查 Doris `otel_metrics_*` 表"取数(与 RustFS/Doris/Nexus 看板对齐),恢复数据,视觉布局不变。

**分支**:从 `main` 新建 `feat/juicefs-monitor-otel-doris`。已创建并切换到该分支。

## 已拍板决策

- **延迟面板 J09/J10/J11 用分位数 p50/p99**(复用现有 `buildRangeHistogramSql`,不做平均值)。
- **验证门禁 = 推导 + 交付 SQL**(无实时 JuiceFS 可核对,按 prometheus receiver 语义实现,产出 verification 文档待真实环境回填)。

## 已核实关键事实(实现前必读,避免踩坑)

1. **采集侧零改动**:`JuicefsMount` 角色(`package/raw/meta/datacluster-physical/JUICEFS/service_ddl.json`)
   已声明 `jmxPortParam: "juicefsJmxPort"`(9567,路径默认 `/metrics`),`OtelScrapeConfigBuilder` 自动纳入抓取。
   **不改 `OtelScrapeConfigBuilder`,不加 `PATH_OVERRIDES`。**
2. **落表/label**:`service_name='JuicefsMount'`(顶层列);`instance` → `resource_attributes['service']['instance']['id']`;
   `vol_name`/`mp`/`method` → 普通 `attributes` VARIANT。
3. **histogram 合并**:经典 Prometheus histogram 被 receiver 合并成 `otel_metrics_histogram` 单行
   (metric_name 去 `_bucket/_sum/_count` 后缀,`count BIGINT`/`sum DOUBLE`/`bucket_counts`/`explicit_bounds` 成列)。
   **确切 metric_name 以 Task 0 验证 SQL 为准。**
4. **后端白名单缺口**:`OtelMetricsQueryService.java` L88–96 的 `ALLOWED_ATTR_FILTER_KEYS` 与
   `INSTANT_SERIES_ATTR_KEYS` **不含 `vol_name`/`mp`/`method`**,必须两处都补。
5. **后端 histogram 能力缺口**:`buildRangeHistogramSql` 只做分位数,不暴露 count-rate、不读 `sum` 列。
   J07(count-rate 请求速率)、J08(sum-rate 字节吞吐)、J12(count-rate by method)三个**吞吐/速率**面板
   必须新增"histogram 表 count/sum 列 counter-rate"能力——**此项与"延迟改分位数"正交,无法回避**。
6. **共享层两个历史 bug 已在 main 修复**,放心复用:`buildRangeRateSql` 已按 `series_key` 分区不串线;
   `_shared/charts/promql.ts` 的 `mergeNamedSeries` 已拼接各 series label 不丢维度。

---

## 任务清单

### Task 0 — 验证文档(不阻塞后续)

- [x] 新建 `docs/monitoring/juicefs-otel-verification.md`,仿 `docs/monitoring/rustfs-otel-verification.md`:
  - [x] Step1:`SELECT COUNT(*) FROM otel.otel_metrics_gauge WHERE service_name='JuicefsMount'`(及 sum/histogram 表)。
  - [x] Step2:枚举 `DISTINCT metric_name`,回填真实指标名(尤其确认 histogram 是否去后缀)。
  - [x] Step3:抽查 `attributes` 含 `vol_name`/`mp`/`method`,`instance` 在 `resource_attributes`。
  - [x] Step4:对照 Task 2 面板表逐指标核对存在性。含结论回填占位。
- **判据**:文档产出;真实环境结论标注为"待回填"。

### Task 1 — 后端(datasophon-api)

改文件:`observability/OtelMetricsQueryService.java`、`controller/v2/OtelMetricsQueryController.java`、
`observability/OtelMetricsQueryServiceTest.java`。

- [x] **1.1 白名单**:`ALLOWED_ATTR_FILTER_KEYS` 与 `INSTANT_SERIES_ATTR_KEYS` **两处**各加 `"vol_name","mp","method"`。
- [x] **1.2 histogram field-rate builder**:新增 `buildRangeHistogramFieldRateSql(String field, ...)`,`field ∈ {"count","sum"}`。
  - **复制 `buildRangeRateSql` 的 CTE 结构**,`FROM otel.otel_metrics_histogram`,值列用 `count AS value` 或 `sum AS value`。
  - 保留 `series_key`(`CAST(attributes AS STRING)`)分区 + reset 守卫(reset 判据统一用 `count` 列单调性,即使 field=sum)。
  - 语义 = Prometheus `sum(rate(metric_count))` / `sum(rate(metric_sum))`。
- [x] **1.3 接入 `queryRange` 分发**(L198 附近):`table='histogram'` 时读新 `field` 参数——
  `field='count'|'sum'` → 新 builder;`field` 缺省或 `'quantile'` → 现有 `buildRangeHistogramSql`(J09/J10/J11 用)。
- [x] **1.4(小)J04 会话数**:`buildInstantAggSql` 的 `fn` 加 `"count"` 支持(`COUNT(value)` over 去重 series)。
  (或改前端对 no-agg 向量计数;推荐后端加 `count` 保持前端 scalar 路径。)
- [x] **1.5 Controller**:`queryRange` 加 `@RequestParam(required=false) String field`,透传 service。
- [x] **1.6 测试**:
  - [x] 加 `rangeHistogramFieldRate_countAndSum_useHistogramTableAndSeriesKeyPartition`(断言含 `otel_metrics_histogram`+`series_key`+对应列)。
  - [x] 更新 `allowedAttrFilterKeys_*` 断言纳入 `vol_name/mp/method`。
  - [x] 若加 `count` agg,补 instant count 断言。
- **验收**:`JAVA_HOME=$JH21 ./mvnw -pl datasophon-api -am test -Dtest="OtelMetricsQueryServiceTest,OtelMetricsQueryControllerTest" -s ~/.m2/setting.xml` 全绿;`./mvnw -pl datasophon-api spotless:check` 通过。
  - 实际执行:`./mvnw -pl datasophon-common,datasophon-grpc-api install -DskipTests=true -Dspotless.check.skip=true`(用于刷新本地 snapshot 依赖)。
  - 实际执行:`./mvnw -pl datasophon-api test -Dtest="OtelMetricsQueryServiceTest,OtelMetricsQueryControllerTest" -Dspotless.check.skip=true` 通过,13 tests。
  - 实际执行:`./mvnw -pl datasophon-api spotless:check` 通过。

### Task 2 — 前端(datasophon-ui-v2)

目录 `datasophon-ui-v2/src/pages/monitor/JuiceFSMonitor/`,**照 `RustfsMonitor/` 结构改写**
(descriptor + 共享 hook,取数走 `_shared/dorisService.ts` + `_shared/useDorisDashboardData.ts`)。

- [x] **2.1 `_shared/dorisService.ts`**:`DorisRangeQuery` 加可选 `field?: 'quantile'|'count'|'sum'`;`queryDorisRange` 拼进请求参数。
- [x] **2.2 `panelQueries.ts`** 重写(删 `replaceJuiceFSVars` 与 promql 字符串,改 `Record<string, DorisPanelDescriptor>`,
  `import type { DorisPanelDescriptor } from '../_shared/dorisService'`)。17 面板(全部 `filters.vol_name` 由 hook 注入):

  | ID | 面板 | 类型 | metric / 关键字段 |
  |---|---|---|---|
  | J01 | Uptime | instant max | `juicefs_uptime`(gauge) |
  | J02 | Data Size | instant max | `juicefs_used_space`(原 avg→max) |
  | J03 | Files | instant max | `juicefs_used_inodes` |
  | J04 | Client Sessions | instant count | `juicefs_uptime`(见 1.4) |
  | J05 | Block Cache Hit % | 客户端合成 stat | hits-rate+miss-rate → `h/(h+m)*100` 取末点 |
  | J06 | Staging Blocks | instant sum | `juicefs_staging_blocks` |
  | J07 | Operations | histogram field-rate | fuse ops histogram, field=count |
  | J08 | IO Throughput | multi-range histogram sum-rate | written/read `_size_bytes`, field=sum(Write/Read) |
  | J09 | IO Latency | multi-range histogram quantile | fuse ops histogram, p50+p99(可选 groupBy mp) |
  | J10 | Transaction Latency | multi-range histogram quantile | transaction histogram, p50+p99 |
  | J11 | Objects Latency | multi-range histogram quantile | object request histogram, p50+p99 |
  | J12 | Objects Requests | histogram field-rate groupBy method | object request histogram, field=count, groupBy `['method']` |
  | J13 | Object Errors & Tx Restarts | multi-range plain rate | `juicefs_object_request_errors`+`juicefs_transaction_restart`(sum 表) |
  | J14 | Block Cache Size | range gauge groupBy mp | `juicefs_blockcache_bytes` |
  | J15 | Block Cache Hit Ratio | 客户端合成 | by count(hits/miss)+by bytes(hit_bytes/miss_bytes) |
  | J16 | Objects Throughput | multi-range rate filter method | `juicefs_object_request_data_bytes`(sum 表), method=PUT/GET |
  | J17 | Client CPU & Memory | multi-range | CPU=`juicefs_cpu_usage` rate*100(sum,scale100)+Memory=`juicefs_memory`(gauge), groupBy mp |

  > histogram metric_name 以 Task 0 验证结果为准(去后缀基名)。

- [x] **2.3 `hooks/useJuiceFSDashboard.ts`** 重写:
  - [x] `useDashboardData` → `useDorisDashboardData`(单次拉全部 17 面板,无 segment)。
  - [x] 卷下拉:`fetchDorisLabels('juicefs_uptime')` 派生 `vol_name` 列表。
  - [x] hit-ratio 合成(J05/J15):加 `combineHitRatio(hitsMatrix, missMatrix)` 逐点 `h/(h+m)*100`,`Number.isNaN`/除零守卫(参照 Valkey V04)。J05 取末点为 scalar,J15 输出全序列。
  - [x] 把 `selectedVolume` 注入所有 descriptor 的 `filters.vol_name`。
- [x] **2.4 `index.tsx`**:布局不动;确认单变量 `vol_name` 单选、meta 行、卷列表变化自动切首个卷;生产不引用旧 mock。
- [x] **2.5 `panelQueries.test.ts`** 改写:断言 17 panelId 全定义、字段正确(histogram field/quantile、J12 groupBy method、J13 指标名、命名空间 `/^juicefs_/`)。
- [x] **2.6 locale**(补历史缺口):
  - [x] 新建 `src/locales/{zh-CN,en-US}/juicefsMonitor.ts`(title+17 panel key),仿 `rustfsMonitor.ts`。
  - [x] 在 `src/locales/zh-CN.ts` / `en-US.ts` 各 import + spread。
  - [x] `index.tsx`/toolbar 面板标题改用 `t('pages.juicefsMonitor.panel.*')`;修掉 toolbar 里 `pages.juiceFSMonitor.toolbar.volume` 大小写不一致(统一 `juicefsMonitor`)。
  - [x] 新建 `juicefsMonitor.test.ts`(zh+en)断言必需 key 齐全非空,仿 `rustfsMonitor.test.ts`。
- **验收**:`cd datasophon-ui-v2 && pnpm test run && pnpm lint && pnpm tsc --noEmit`(或 `pnpm build`)全绿。
  - 实际执行:`PATH=/Users/pro/.nvm/versions/node/v22.22.2/bin:$PATH pnpm test` 通过,35 files / 146 tests。
  - 实际执行:`PATH=/Users/pro/.nvm/versions/node/v22.22.2/bin:$PATH pnpm lint` 通过(包含 `tsc --noEmit`)。
  - 实际执行:`PATH=/Users/pro/.nvm/versions/node/v22.22.2/bin:$PATH pnpm tsc --noEmit` 通过。

### Task 3 — 端到端验证(有环境时)

- [ ] 后端起 datasophon-api(连观测沙箱 Doris),前端 `pnpm dev`,开 `/monitor/juicefs`。
- [ ] 逐面板确认有数据;无真实 JuiceFS 时至少确认请求打到 `/api/v2/observability/otel/metrics/query_range`、带正确 `metric/field/filters` 参数、无报错。
- [ ] 回填 Task 0 验证文档结论。

> 状态:未执行。当前环境没有真实 JuiceFS + Doris 观测沙箱可用于端到端数据核对。

---

## 复用清单(不要重写)

- 后端:`OtelMetricsQueryService`/`OtelMetricsQueryController`(仅加白名单键 + histogram field-rate)。
- 前端取数:`_shared/dorisService.ts`、`_shared/useDorisDashboardData.ts`(含 `denominatorMetric` 比值、并发限流)。
- 图表/布局:`_shared/panels/`、`MonitorDashboardLayout`、`mergeNamedSeries`。
- 模板参考:`RustfsMonitor/`(最新 Doris 看板)、`DorisMonitor`(ratio 合成)、`ValkeyMonitor` V04(hit-ratio NaN 守卫)。

## 已知局限(不在本任务修)

- 多挂载点共用 `juicefsJmxPort` → 一节点多挂载端口冲突,仅单挂载点可靠(采集侧预置问题,验证文档记录即可)。
- histogram 精确 metric_name/attributes 键真实值需真实 JuiceFS 环境最终确认(Task 0 SQL 待回填)。

## Claude 审查要点(实现后)

1. 白名单两处集合都改了(漏一处则 filter/groupBy 被静默丢弃)。
2. histogram field-rate builder 的 `series_key` 分区 + reset 守卫是否保留(否则多卷/多挂载跨 series 串线,单卷沙箱照不出)。
3. J05/J15 hit-ratio 合成的除零/NaN 守卫。
4. locale 大小写统一为 `juicefsMonitor`,17 panel key 齐全。
5. 后端 java 测试 + 前端 vitest/lint/tsc 全绿;spotless 通过。
