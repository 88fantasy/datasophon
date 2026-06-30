# otel schema 真实 Doris 应用验收清单

> 供具备 Doris 实例的环境执行。开发机无 Doris,以下为 ⏳ 待真实环境;schema 文件本身的契约/解析/版本已由 `OtelSchemaContractTest` 在开发机验证(✅)。

schema 版本:`v1`(`OtelSchema.VERSION`);DDL 真相之源见同目录 `SCHEMA.md`(pin dorisexporter v0.154.0)。

## 1. 应用 DDL

方式 A —— 代码:`OtelSchemaApplier.apply(jdbcClient)`(jdbcClient 走 MySQL 协议连 Doris FE 9030)。
方式 B —— 手工:
```bash
for f in V1__otel_database.sql V1__otel_tables.sql V1__otel_views.sql; do
  mysql -h<FE> -P9030 -uroot < datasophon-api/src/main/resources/observability/doris/$f
done
mysql -h<FE> -P9030 -uroot -e "SHOW TABLES FROM otel;"
```
期望:列出全部 8 张基表 `otel_logs / otel_metrics_{gauge,sum,histogram,exponential_histogram,summary} / otel_traces / otel_traces_graph`。

## 2. exporter 写入预建表(create_schema=false)

otelcol 以 `dorisexporter`(`create_schema: false`,database `otel`)接收合成数据后:
```sql
SELECT count(*) FROM otel.otel_logs;
SELECT count(*) FROM otel.otel_metrics_gauge;
SELECT count(*) FROM otel.otel_traces;
```
期望:对应信号灌入后各 > 0(证明 exporter 能写进我们预建的表,字段对齐)。

## 3. 最小权限(F1)

```sql
-- 用 otel_collector 账号连接
DROP TABLE otel.otel_logs;      -- 期望:被拒(无 DROP_PRIV)
SELECT * FROM otel.otel_logs;   -- 期望:被拒(仅 LOAD,无 SELECT)
-- Stream Load 写入 otel_logs   -- 期望:成功(LOAD_PRIV)
```

## 4. 资源组与绑定(CHECK 4)

```sql
SHOW WORKLOAD GROUPS;  -- 期望:含 otel_wg
-- 用 otel_collector / otel_reader 账号分别连接,验证 USAGE_PRIV 生效(能看到该组):
SHOW WORKLOAD GROUPS;  -- 期望:该账号可见 otel_wg(无 USAGE_PRIV 则不可见)
SHOW PROPERTY FOR 'otel_collector';  -- 期望:default_workload_group = otel_wg
SHOW PROPERTY FOR 'otel_reader';     -- 期望:default_workload_group = otel_wg
```
> 仅 CREATE WORKLOAD GROUP 不够:未 GRANT USAGE_PRIV + 未设 default_workload_group 时账号仍走 normal 组。

## 5. traces_graph_job 幂等(A3 待办,CHECK 2-1)

Phase D 仅完成 api/worker OTel Java Agent 接入;`otel_traces_graph_job` 的真机幂等验证继续 defer,不纳入本批验收范围。

`V1__otel_views.sql` 的 `CREATE JOB \`otel:otel_traces_graph_job\`` 无幂等语法
(Doris `CREATE JOB` 不支持 `IF NOT EXISTS`、`DROP JOB` 不支持 `IF EXISTS`)。
- 单次 apply:JOB 创建成功。
- 重复 apply:在该语句失败(job 已存在)。

A3 接真实 Doris 时实现幂等容错并在此实测:
1. 首次 apply 建 JOB(`SELECT * FROM jobs("type"="insert") WHERE Name='otel:otel_traces_graph_job'` 期望 1 行)。
2. 二次 apply 不报错(幂等)。
3. 候选实现:执行前 `DROP JOB where jobName='otel:otel_traces_graph_job'` 并按 **DROP 不存在 job 的真实错误码**容错——该错误行为须在真实 Doris 实测确定,不可在开发机臆测。

实现后同步更新 `OtelSchemaContractTest.traces_graph_job_is_the_single_known_non_idempotent_statement`(其 assertFalse 会主动失败提示)。

## 验收状态(2026-06-19)

| 项 | 状态 |
|---|---|
| schema 文件解析/资源可加载 | ✅ 开发机(OtelSchemaContractTest) |
| DDL 覆盖 exporter 8 表(契约) | ✅ 开发机 |
| 账号权限白名单精确(collector={LOAD_PRIV}/reader={SELECT_PRIV},含自证) | ✅ 开发机 |
| 资源组绑定契约(USAGE_PRIV + default_workload_group) | ✅ 开发机 |
| JOB 幂等已知边界守卫(恰 1 个 CREATE JOB 无 IF NOT EXISTS) | ✅ 开发机 |
| schema 版本 pin v1 | ✅ 开发机 |
| 应用器实跑建表(§1) | ⏳ 待真实 Doris |
| exporter 写入预建表(§2) | ⏳ 待真实 Doris(并依赖 A3 切 dorisexporter) |
| 最小权限拒绝(§3) | ⏳ 待真实 Doris |
| 资源组绑定生效(§4) | ⏳ 待真实 Doris |
| traces_graph_job 幂等(§5,CHECK 2-1) | ⏳ 待真实 Doris(A3 实现+验证) |

> 注:Workload Group 属性名、物化视图 `DROP IF EXISTS` 与 CREATE JOB 幂等写法需按部署 Doris 版本(4.0.5)实测校正(见 §5 与 Task 1 报告 concerns)。
