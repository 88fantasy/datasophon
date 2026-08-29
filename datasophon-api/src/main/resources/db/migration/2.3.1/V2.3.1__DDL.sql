CREATE TABLE `t_ddh_ds_stream_metric_job` (
    `cluster_id` int NOT NULL COMMENT 'Datasophon cluster id',
    `job_id` varchar(64) NOT NULL COMMENT 'Flink job id',
    `job_name` varchar(255) NOT NULL COMMENT 'Flink job name',
    `since_time` datetime(3) NOT NULL COMMENT 'First observed metric time',
    `cursor_time` datetime(3) NOT NULL COMMENT 'Exclusive aggregation cursor',
    `processed_approx` bigint NOT NULL DEFAULT 0 COMMENT 'Observed delta sum, approximate',
    `update_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`cluster_id`, `job_id`),
    KEY `idx_ds_stream_metric_cursor` (`cursor_time`),
    KEY `idx_ds_stream_metric_pending` (`update_time`, `cursor_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DS streaming job observed totals';

CREATE TABLE `t_ddh_ds_stream_metric_period` (
    `cluster_id` int NOT NULL COMMENT 'Datasophon cluster id',
    `job_id` varchar(64) NOT NULL COMMENT 'Flink job id',
    `period_start` datetime(3) NOT NULL COMMENT 'Inclusive period start',
    `period_end` datetime(3) NOT NULL COMMENT 'Exclusive period end',
    `delta_value` bigint NOT NULL COMMENT 'Observed delta sum in this period',
    `create_time` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`cluster_id`, `job_id`, `period_start`),
    KEY `idx_ds_stream_metric_period_end` (`period_end`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DS streaming metric idempotency ledger';
