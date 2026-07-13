-- otel 可观测存储：基表定义 (schema v1)
-- 表定义逐字源自 dorisexporter v0.156.0，仅做以下机械替换：
--   1. 表名加库前缀 otel.<table>，并补 IF NOT EXISTS 保证幂等
--   2. 占位符 %s（第1处=表名，第2处=PROPERTIES块）替换为具体值
-- 默认 PROPERTIES 取自 factory.go 默认值：
--   replication_num=1, compaction_policy=time_series（非唯一键表）/ size_based（UNIQUE KEY 表）
--   dynamic_partition.start=-2147483648（defaultStart=IntMin，即不限历史分区起始）
--   dynamic_partition.history_partition_num=0（不预建历史分区）
-- 列/键/分区/索引定义不得手改，否则 Stream Load 字段不匹配。

USE otel;

-- ===========================================================================
-- 1. otel_logs
-- source: dorisexporter v0.156.0 sql/logs_ddl.sql
-- ===========================================================================
CREATE TABLE IF NOT EXISTS otel.otel_logs
(
    timestamp             DATETIME(6),
    service_name          VARCHAR(200),
    service_instance_id   VARCHAR(200),
    trace_id              VARCHAR(200),
    span_id               STRING,
    severity_number       INT,
    severity_text         STRING,
    body                  STRING,
    resource_attributes   VARIANT,
    log_attributes        VARIANT,
    scope_name            STRING,
    scope_version         STRING,
    INDEX idx_service_name(service_name) USING INVERTED,
    INDEX idx_timestamp(timestamp) USING INVERTED,
    INDEX idx_service_instance_id(service_instance_id) USING INVERTED,
    INDEX idx_trace_id(trace_id) USING INVERTED,
    INDEX idx_span_id(span_id) USING INVERTED,
    INDEX idx_severity_number(severity_number) USING INVERTED,
    INDEX idx_body(body) USING INVERTED PROPERTIES("parser"="unicode", "support_phrase"="true"),
    INDEX idx_severity_text(severity_text) USING INVERTED,
    INDEX idx_resource_attributes(resource_attributes) USING INVERTED,
    INDEX idx_log_attributes(log_attributes) USING INVERTED,
    INDEX idx_scope_name(scope_name) USING INVERTED,
    INDEX idx_scope_version(scope_version) USING INVERTED
)
ENGINE = OLAP
DUPLICATE KEY(timestamp, service_name)
PARTITION BY RANGE(timestamp) ()
DISTRIBUTED BY RANDOM BUCKETS AUTO
PROPERTIES (
"replication_num" = "1",
"compaction_policy" = "time_series",
"dynamic_partition.enable" = "true",
"dynamic_partition.create_history_partition" = "true",
"dynamic_partition.time_unit" = "DAY",
"dynamic_partition.start" = "-2147483648",
"dynamic_partition.history_partition_num" = "0",
"dynamic_partition.end" = "1",
"dynamic_partition.prefix" = "p",
"compression" = "zstd",
"inverted_index_storage_format" = "V2"
);

-- ===========================================================================
-- 2. otel_metrics_gauge
-- source: dorisexporter v0.156.0 sql/metrics_gauge_ddl.sql
-- ===========================================================================
CREATE TABLE IF NOT EXISTS otel.otel_metrics_gauge
(
    service_name          VARCHAR(200),
    timestamp             DATETIME(6),
    service_instance_id   VARCHAR(200),
    metric_name           VARCHAR(200),
    metric_description    STRING,
    metric_unit           STRING,
    attributes            VARIANT,
    start_time            DATETIME(6),
    value                 DOUBLE,
    exemplars             ARRAY<STRUCT<filtered_attributes:MAP<STRING,STRING>, timestamp:DATETIME(6), value:DOUBLE, span_id:STRING, trace_id:STRING>>,
    resource_attributes   VARIANT,
    scope_name            STRING,
    scope_version         STRING,
    INDEX idx_service_name(service_name) USING INVERTED,
    INDEX idx_timestamp(timestamp) USING INVERTED,
    INDEX idx_service_instance_id(service_instance_id) USING INVERTED,
    INDEX idx_metric_name(metric_name) USING INVERTED,
    INDEX idx_metric_description(metric_description) USING INVERTED,
    INDEX idx_metric_unit(metric_unit) USING INVERTED,
    INDEX idx_attributes(attributes) USING INVERTED,
    INDEX idx_start_time(start_time) USING INVERTED,
    INDEX idx_resource_attributes(resource_attributes) USING INVERTED,
    INDEX idx_scope_name(scope_name) USING INVERTED,
    INDEX idx_scope_version(scope_version) USING INVERTED
)
ENGINE = OLAP
DUPLICATE KEY(service_name, timestamp)
PARTITION BY RANGE(timestamp) ()
DISTRIBUTED BY RANDOM BUCKETS AUTO
PROPERTIES (
"replication_num" = "1",
"compaction_policy" = "time_series",
"dynamic_partition.enable" = "true",
"dynamic_partition.create_history_partition" = "true",
"dynamic_partition.time_unit" = "DAY",
"dynamic_partition.start" = "-2147483648",
"dynamic_partition.history_partition_num" = "0",
"dynamic_partition.end" = "1",
"dynamic_partition.prefix" = "p",
"compression" = "zstd",
"inverted_index_storage_format" = "V2"
);

-- ===========================================================================
-- 3. otel_metrics_sum
-- source: dorisexporter v0.156.0 sql/metrics_sum_ddl.sql
-- ===========================================================================
CREATE TABLE IF NOT EXISTS otel.otel_metrics_sum
(
    service_name            VARCHAR(200),
    timestamp               DATETIME(6),
    service_instance_id     VARCHAR(200),
    metric_name             VARCHAR(200),
    metric_description      STRING,
    metric_unit             STRING,
    attributes              VARIANT,
    start_time              DATETIME(6),
    value                   DOUBLE,
    exemplars               ARRAY<STRUCT<filtered_attributes:MAP<STRING,STRING>, timestamp:DATETIME(6), value:DOUBLE, span_id:STRING, trace_id:STRING>>,
    aggregation_temporality STRING,
    is_monotonic            BOOLEAN,
    resource_attributes     VARIANT,
    scope_name              STRING,
    scope_version           STRING,
    INDEX idx_service_name(service_name) USING INVERTED,
    INDEX idx_timestamp(timestamp) USING INVERTED,
    INDEX idx_service_instance_id(service_instance_id) USING INVERTED,
    INDEX idx_metric_name(metric_name) USING INVERTED,
    INDEX idx_metric_description(metric_description) USING INVERTED,
    INDEX idx_metric_unit(metric_unit) USING INVERTED,
    INDEX idx_attributes(attributes) USING INVERTED,
    INDEX idx_start_time(start_time) USING INVERTED,
    INDEX idx_aggregation_temporality(aggregation_temporality) USING INVERTED,
    INDEX idx_resource_attributes(resource_attributes) USING INVERTED,
    INDEX idx_scope_name(scope_name) USING INVERTED,
    INDEX idx_scope_version(scope_version) USING INVERTED
)
ENGINE = OLAP
DUPLICATE KEY(service_name, timestamp)
PARTITION BY RANGE(timestamp) ()
DISTRIBUTED BY RANDOM BUCKETS AUTO
PROPERTIES (
"replication_num" = "1",
"compaction_policy" = "time_series",
"dynamic_partition.enable" = "true",
"dynamic_partition.create_history_partition" = "true",
"dynamic_partition.time_unit" = "DAY",
"dynamic_partition.start" = "-2147483648",
"dynamic_partition.history_partition_num" = "0",
"dynamic_partition.end" = "1",
"dynamic_partition.prefix" = "p",
"compression" = "zstd",
"inverted_index_storage_format" = "V2"
);

-- ===========================================================================
-- 4. otel_metrics_histogram
-- source: dorisexporter v0.156.0 sql/metrics_histogram_ddl.sql
-- ===========================================================================
CREATE TABLE IF NOT EXISTS otel.otel_metrics_histogram
(
    service_name            VARCHAR(200),
    timestamp               DATETIME(6),
    service_instance_id     VARCHAR(200),
    metric_name             VARCHAR(200),
    metric_description      STRING,
    metric_unit             STRING,
    attributes              VARIANT,
    start_time              DATETIME(6),
    count                   BIGINT,
    sum                     DOUBLE,
    bucket_counts           ARRAY<BIGINT>,
    explicit_bounds         ARRAY<DOUBLE>,
    exemplars               ARRAY<STRUCT<filtered_attributes:MAP<STRING,STRING>, timestamp:DATETIME(6), value:DOUBLE, span_id:STRING, trace_id:STRING>>,
    min                     DOUBLE,
    max                     DOUBLE,
    aggregation_temporality STRING,
    resource_attributes     VARIANT,
    scope_name              STRING,
    scope_version           STRING,
    INDEX idx_service_name(service_name) USING INVERTED,
    INDEX idx_timestamp(timestamp) USING INVERTED,
    INDEX idx_service_instance_id(service_instance_id) USING INVERTED,
    INDEX idx_metric_name(metric_name) USING INVERTED,
    INDEX idx_metric_description(metric_description) USING INVERTED,
    INDEX idx_metric_unit(metric_unit) USING INVERTED,
    INDEX idx_attributes(attributes) USING INVERTED,
    INDEX idx_start_time(start_time) USING INVERTED,
    INDEX idx_count(count) USING INVERTED,
    INDEX idx_aggregation_temporality(aggregation_temporality) USING INVERTED,
    INDEX idx_resource_attributes(resource_attributes) USING INVERTED,
    INDEX idx_scope_name(scope_name) USING INVERTED,
    INDEX idx_scope_version(scope_version) USING INVERTED
)
ENGINE = OLAP
DUPLICATE KEY(service_name, timestamp)
PARTITION BY RANGE(timestamp) ()
DISTRIBUTED BY RANDOM BUCKETS AUTO
PROPERTIES (
"replication_num" = "1",
"compaction_policy" = "time_series",
"dynamic_partition.enable" = "true",
"dynamic_partition.create_history_partition" = "true",
"dynamic_partition.time_unit" = "DAY",
"dynamic_partition.start" = "-2147483648",
"dynamic_partition.history_partition_num" = "0",
"dynamic_partition.end" = "1",
"dynamic_partition.prefix" = "p",
"compression" = "zstd",
"inverted_index_storage_format" = "V2"
);

-- ===========================================================================
-- 5. otel_metrics_exponential_histogram
-- source: dorisexporter v0.156.0 sql/metrics_exponential_histogram_ddl.sql
-- ===========================================================================
CREATE TABLE IF NOT EXISTS otel.otel_metrics_exponential_histogram
(
    service_name            VARCHAR(200),
    timestamp               DATETIME(6),
    service_instance_id     VARCHAR(200),
    metric_name             VARCHAR(200),
    metric_description      STRING,
    metric_unit             STRING,
    attributes              VARIANT,
    start_time              DATETIME(6),
    count                   BIGINT,
    sum                     DOUBLE,
    scale                   INT,
    zero_count              BIGINT,
    positive_offset         INT,
    positive_bucket_counts  ARRAY<BIGINT>,
    negative_offset         INT,
    negative_bucket_counts  ARRAY<BIGINT>,
    exemplars               ARRAY<STRUCT<filtered_attributes:MAP<STRING,STRING>, timestamp:DATETIME(6), value:DOUBLE, span_id:STRING, trace_id:STRING>>,
    min                     DOUBLE,
    max                     DOUBLE,
    zero_threshold          DOUBLE,
    aggregation_temporality STRING,
    resource_attributes     VARIANT,
    scope_name              STRING,
    scope_version           STRING,
    INDEX idx_service_name(service_name) USING INVERTED,
    INDEX idx_timestamp(timestamp) USING INVERTED,
    INDEX idx_service_instance_id(service_instance_id) USING INVERTED,
    INDEX idx_metric_name(metric_name) USING INVERTED,
    INDEX idx_metric_description(metric_description) USING INVERTED,
    INDEX idx_metric_unit(metric_unit) USING INVERTED,
    INDEX idx_attributes(attributes) USING INVERTED,
    INDEX idx_start_time(start_time) USING INVERTED,
    INDEX idx_count(count) USING INVERTED,
    INDEX idx_scale(scale) USING INVERTED,
    INDEX idx_zero_count(zero_count) USING INVERTED,
    INDEX idx_positive_offset(positive_offset) USING INVERTED,
    INDEX idx_negative_offset(negative_offset) USING INVERTED,
    INDEX idx_aggregation_temporality(aggregation_temporality) USING INVERTED,
    INDEX idx_resource_attributes(resource_attributes) USING INVERTED,
    INDEX idx_scope_name(scope_name) USING INVERTED,
    INDEX idx_scope_version(scope_version) USING INVERTED
)
ENGINE = OLAP
DUPLICATE KEY(service_name, timestamp)
PARTITION BY RANGE(timestamp) ()
DISTRIBUTED BY RANDOM BUCKETS AUTO
PROPERTIES (
"replication_num" = "1",
"compaction_policy" = "time_series",
"dynamic_partition.enable" = "true",
"dynamic_partition.create_history_partition" = "true",
"dynamic_partition.time_unit" = "DAY",
"dynamic_partition.start" = "-2147483648",
"dynamic_partition.history_partition_num" = "0",
"dynamic_partition.end" = "1",
"dynamic_partition.prefix" = "p",
"compression" = "zstd",
"inverted_index_storage_format" = "V2"
);

-- ===========================================================================
-- 6. otel_metrics_summary
-- source: dorisexporter v0.156.0 sql/metrics_summary_ddl.sql
-- ===========================================================================
CREATE TABLE IF NOT EXISTS otel.otel_metrics_summary
(
    service_name          VARCHAR(200),
    timestamp             DATETIME(6),
    service_instance_id   VARCHAR(200),
    metric_name           VARCHAR(200),
    metric_description    STRING,
    metric_unit           STRING,
    attributes            VARIANT,
    start_time            DATETIME(6),
    count                 BIGINT,
    sum                   DOUBLE,
    quantile_values       ARRAY<STRUCT<quantile:DOUBLE, value:DOUBLE>>,
    resource_attributes   VARIANT,
    scope_name            STRING,
    scope_version         STRING,
    INDEX idx_service_name(service_name) USING INVERTED,
    INDEX idx_timestamp(timestamp) USING INVERTED,
    INDEX idx_service_instance_id(service_instance_id) USING INVERTED,
    INDEX idx_metric_name(metric_name) USING INVERTED,
    INDEX idx_metric_description(metric_description) USING INVERTED,
    INDEX idx_metric_unit(metric_unit) USING INVERTED,
    INDEX idx_attributes(attributes) USING INVERTED,
    INDEX idx_start_time(start_time) USING INVERTED,
    INDEX idx_count(count) USING INVERTED,
    INDEX idx_resource_attributes(resource_attributes) USING INVERTED,
    INDEX idx_scope_name(scope_name) USING INVERTED,
    INDEX idx_scope_version(scope_version) USING INVERTED
)
ENGINE = OLAP
DUPLICATE KEY(service_name, timestamp)
PARTITION BY RANGE(timestamp) ()
DISTRIBUTED BY RANDOM BUCKETS AUTO
PROPERTIES (
"replication_num" = "1",
"compaction_policy" = "time_series",
"dynamic_partition.enable" = "true",
"dynamic_partition.create_history_partition" = "true",
"dynamic_partition.time_unit" = "DAY",
"dynamic_partition.start" = "-2147483648",
"dynamic_partition.history_partition_num" = "0",
"dynamic_partition.end" = "1",
"dynamic_partition.prefix" = "p",
"compression" = "zstd",
"inverted_index_storage_format" = "V2"
);

-- ===========================================================================
-- 7. otel_traces
-- source: dorisexporter v0.156.0 sql/traces_ddl.sql
-- ===========================================================================
CREATE TABLE IF NOT EXISTS otel.otel_traces
(
    service_name          VARCHAR(200),
    timestamp             DATETIME(6),
    service_instance_id   VARCHAR(200),
    trace_id              VARCHAR(200),
    span_id               STRING,
    trace_state           STRING,
    parent_span_id        STRING,
    span_name             STRING,
    span_kind             STRING,
    end_time              DATETIME(6),
    duration              BIGINT,
    span_attributes       VARIANT,
    events                ARRAY<STRUCT<timestamp:DATETIME(6), name:STRING, attributes:MAP<STRING, STRING>>>,
    links                 ARRAY<STRUCT<trace_id:STRING, span_id:STRING, trace_state:STRING, attributes:MAP<STRING, STRING>>>,
    status_message        STRING,
    status_code           STRING,
    resource_attributes   VARIANT,
    scope_name            STRING,
    scope_version         STRING,
    INDEX idx_service_name(service_name) USING INVERTED,
    INDEX idx_timestamp(timestamp) USING INVERTED,
    INDEX idx_service_instance_id(service_instance_id) USING INVERTED,
    INDEX idx_trace_id(trace_id) USING INVERTED,
    INDEX idx_span_id(span_id) USING INVERTED,
    INDEX idx_trace_state(trace_state) USING INVERTED,
    INDEX idx_parent_span_id(parent_span_id) USING INVERTED,
    INDEX idx_span_name(span_name) USING INVERTED,
    INDEX idx_span_kind(span_kind) USING INVERTED,
    INDEX idx_end_time(end_time) USING INVERTED,
    INDEX idx_duration(duration) USING INVERTED,
    INDEX idx_span_attributes(span_attributes) USING INVERTED,
    INDEX idx_status_message(status_message) USING INVERTED,
    INDEX idx_status_code(status_code) USING INVERTED,
    INDEX idx_resource_attributes(resource_attributes) USING INVERTED,
    INDEX idx_scope_name(scope_name) USING INVERTED,
    INDEX idx_scope_version(scope_version) USING INVERTED
)
ENGINE = OLAP
DUPLICATE KEY(service_name, timestamp)
PARTITION BY RANGE(timestamp) ()
DISTRIBUTED BY RANDOM BUCKETS AUTO
PROPERTIES (
"replication_num" = "1",
"compaction_policy" = "time_series",
"dynamic_partition.enable" = "true",
"dynamic_partition.create_history_partition" = "true",
"dynamic_partition.time_unit" = "DAY",
"dynamic_partition.start" = "-2147483648",
"dynamic_partition.history_partition_num" = "0",
"dynamic_partition.end" = "1",
"dynamic_partition.prefix" = "p",
"compression" = "zstd",
"inverted_index_storage_format" = "V2"
);

-- ===========================================================================
-- 8. otel_traces_graph
-- source: dorisexporter v0.156.0 sql/traces_graph_ddl.sql
-- 注：此表为 UNIQUE KEY，propertiesStr 使用 size_based compaction_policy
-- ===========================================================================
CREATE TABLE IF NOT EXISTS otel.otel_traces_graph
(
    timestamp                   DATETIME(6),
    caller_service_name         VARCHAR(200),
    caller_service_instance_id  VARCHAR(200),
    callee_service_name         VARCHAR(200),
    callee_service_instance_id  VARCHAR(200),
    count                       BIGINT,
    error_count                 BIGINT,
    INDEX idx_timestamp(timestamp) USING INVERTED,
    INDEX idx_caller_service_name(caller_service_name) USING INVERTED,
    INDEX idx_caller_service_instance_id(caller_service_instance_id) USING INVERTED,
    INDEX idx_callee_service_name(callee_service_name) USING INVERTED,
    INDEX idx_callee_service_instance_id(callee_service_instance_id) USING INVERTED,
    INDEX count(count) USING INVERTED,
    INDEX error_count(error_count) USING INVERTED
)
UNIQUE KEY(timestamp, caller_service_name, caller_service_instance_id, callee_service_name, callee_service_instance_id)
PARTITION BY RANGE(timestamp) ()
DISTRIBUTED BY HASH(caller_service_name) BUCKETS AUTO
PROPERTIES (
"replication_num" = "1",
"compaction_policy" = "size_based",
"dynamic_partition.enable" = "true",
"dynamic_partition.create_history_partition" = "true",
"dynamic_partition.time_unit" = "DAY",
"dynamic_partition.start" = "-2147483648",
"dynamic_partition.history_partition_num" = "0",
"dynamic_partition.end" = "1",
"dynamic_partition.prefix" = "p",
"compression" = "zstd",
"inverted_index_storage_format" = "V2"
);
