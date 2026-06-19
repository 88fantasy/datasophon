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

## 4. 资源组

```sql
SHOW WORKLOAD GROUPS;  -- 期望:含 otel_wg
```

## 验收状态(2026-06-19)

| 项 | 状态 |
|---|---|
| schema 文件解析/资源可加载 | ✅ 开发机(OtelSchemaContractTest) |
| DDL 覆盖 exporter 8 表(契约) | ✅ 开发机 |
| 采集账号 LOAD-only 契约(含自证断言) | ✅ 开发机 |
| schema 版本 pin v1 | ✅ 开发机 |
| 应用器实跑建表(§1) | ⏳ 待真实 Doris |
| exporter 写入预建表(§2) | ⏳ 待真实 Doris(并依赖 A3 切 dorisexporter) |
| 最小权限拒绝(§3) | ⏳ 待真实 Doris |
| 资源组(§4) | ⏳ 待真实 Doris |

> 注:Workload Group 属性名与物化视图 `DROP IF EXISTS` 幂等写法需按部署 Doris 版本(4.0.5)实测校正(见 Task 1 报告 concerns)。
