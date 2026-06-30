-- otel 可观测存储：物化视图 + Graph Job (schema v1)
-- 视图定义逐字源自 dorisexporter v0.154.0，仅做机械替换：
--   1. 视图名加库前缀 otel.<view>，补 IF NOT EXISTS 保证幂等
--   2. 占位符 %s（第1处=视图名前缀，第2处=基表名）替换为具体值
-- 注：Doris MATERIALIZED VIEW 不支持 IF NOT EXISTS，用 DROP IF EXISTS 前置保证幂等。
-- 注：所有表/视图名均使用 otel. 全限定前缀，不依赖 USE 会话上下文（applier 每条语句独立连接）。
-- traces_graph_job: JOB 名称格式为 `database:table_graph_job`，此处为 `otel:otel_traces_graph_job`

-- ===========================================================================
-- 1. otel_logs_services（物化视图）
-- source: dorisexporter v0.154.0 sql/logs_view.sql
-- ===========================================================================
DROP MATERIALIZED VIEW IF EXISTS otel.otel_logs_services;
CREATE MATERIALIZED VIEW otel.otel_logs_services AS
SELECT service_name, service_instance_id
FROM otel.otel_logs
GROUP BY service_name, service_instance_id;

-- ===========================================================================
-- 2. otel_metrics_services（物化视图）
-- source: dorisexporter v0.154.0 sql/metrics_view.sql
-- 注：metrics_view 使用 otel_metrics_gauge 作为基表（即 %s_gauge 的 %s = otel_metrics）
-- ===========================================================================
DROP MATERIALIZED VIEW IF EXISTS otel.otel_metrics_services;
CREATE MATERIALIZED VIEW otel.otel_metrics_services AS
SELECT service_name, service_instance_id
FROM otel.otel_metrics_gauge
GROUP BY service_name, service_instance_id;

-- ===========================================================================
-- 3. otel_traces_services（物化视图）
-- source: dorisexporter v0.154.0 sql/traces_view.sql
-- ===========================================================================
DROP MATERIALIZED VIEW IF EXISTS otel.otel_traces_services;
CREATE MATERIALIZED VIEW otel.otel_traces_services AS
SELECT service_name, service_instance_id, span_name
FROM otel.otel_traces
GROUP BY service_name, service_instance_id, span_name;

-- ===========================================================================
-- 4. otel_traces_graph_job（Doris JOB，每 10 分钟聚合一次调用图数据）
-- source: dorisexporter v0.154.0 sql/traces_graph_job.sql
-- JOB 名格式：`database:table_graph_job` => `otel:otel_traces_graph_job`
-- ===========================================================================
CREATE JOB `otel:otel_traces_graph_job`
ON SCHEDULE EVERY 10 MINUTE
DO
INSERT INTO otel.otel_traces_graph
SELECT
    date_trunc(t2.timestamp, 'MINUTE') as timestamp,
    t1.service_name AS caller_service_name,
    t1.service_instance_id AS caller_service_instance_id,
    t2.service_name AS callee_service_name,
    t2.service_instance_id AS callee_service_instance_id,
    count(*) as count,
    sum(if(t2.status_code = 'STATUS_CODE_ERROR', 1, 0)) as error_count
FROM otel.otel_traces t1
JOIN otel.otel_traces t2
ON t1.trace_id = t2.trace_id
AND t1.span_id != ''
AND t1.service_name != t2.service_name
AND t1.span_id = t2.parent_span_id
AND t2.timestamp >= minutes_sub(date_trunc(now(), 'MINUTE'), 10)
GROUP BY timestamp, caller_service_name, caller_service_instance_id, callee_service_name, callee_service_instance_id;
