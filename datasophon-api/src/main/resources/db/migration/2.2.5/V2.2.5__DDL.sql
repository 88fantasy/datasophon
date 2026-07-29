CREATE TABLE IF NOT EXISTS `t_ddh_data_job` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `cluster_id` INT NOT NULL,
    `job_name` VARCHAR(255) NOT NULL,
    `engine` VARCHAR(32) NOT NULL,
    `otel_service_name` VARCHAR(255) DEFAULT NULL,
    `current_structural_hash` CHAR(64) DEFAULT NULL,
    `current_watermark` BIGINT DEFAULT NULL,
    `job_type` VARCHAR(32) NOT NULL,
    `dw_layer` VARCHAR(32) DEFAULT NULL,
    `owner` VARCHAR(128) DEFAULT NULL,
    `external_url` VARCHAR(1024) DEFAULT NULL,
    `state` VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_data_job_identity` (`cluster_id`, `engine`, `job_name`),
    KEY `idx_data_job_otel_service` (`otel_service_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据作业台账';

CREATE TABLE IF NOT EXISTS `t_ddh_data_job_definition` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `job_id` BIGINT NOT NULL,
    `version` INT NOT NULL,
    `definition_text` LONGTEXT NOT NULL,
    `content_hash` CHAR(64) NOT NULL,
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_job_version` (`job_id`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据作业定义历史';

CREATE TABLE IF NOT EXISTS `t_ddh_lineage_node` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `connector` VARCHAR(64) NOT NULL,
    `catalog_name` VARCHAR(255) NOT NULL,
    `database_name` VARCHAR(255) NOT NULL,
    `table_name` VARCHAR(255) NOT NULL,
    `canonical_name` VARCHAR(512) NOT NULL,
    `dw_layer` VARCHAR(32) DEFAULT NULL,
    `first_seen` DATETIME(3) NOT NULL,
    `last_seen` DATETIME(3) NOT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_lineage_node_canonical_name` (`canonical_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='血缘节点';

CREATE TABLE IF NOT EXISTS `t_ddh_lineage_edge` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `job_id` BIGINT NOT NULL,
    `definition_version` INT NOT NULL,
    `src_node_id` BIGINT NOT NULL,
    `dst_node_id` BIGINT NOT NULL,
    `flow_type` VARCHAR(16) NOT NULL,
    `is_current` TINYINT NOT NULL DEFAULT 1,
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_lineage_edge_version` (`job_id`, `definition_version`, `src_node_id`, `dst_node_id`),
    KEY `idx_edge_current` (`is_current`, `src_node_id`, `dst_node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='血缘边历史';

CREATE TABLE IF NOT EXISTS `t_ddh_lineage_parse_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `job_id` BIGINT DEFAULT NULL,
    `definition_version` INT DEFAULT NULL,
    `status` VARCHAR(16) NOT NULL,
    `message` TEXT DEFAULT NULL,
    `parsed_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_lineage_parse_log_job` (`job_id`, `definition_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='血缘解析旁路日志';

CREATE TABLE IF NOT EXISTS `t_ddh_lineage_event` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `producer` VARCHAR(255) NOT NULL,
    `run_id` VARCHAR(64) NOT NULL,
    `event_type` VARCHAR(16) NOT NULL,
    `job_id` BIGINT DEFAULT NULL,
    `run_started_at` DATETIME(3) DEFAULT NULL,
    `received_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `status` VARCHAR(16) DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_event` (`producer`, `run_id`, `event_type`),
    KEY `idx_lineage_event_job` (`job_id`, `received_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='血缘事件投递幂等记录';

CREATE TABLE IF NOT EXISTS `t_ddh_lineage_generation` (
    `id` TINYINT NOT NULL,
    `generation` BIGINT NOT NULL DEFAULT 0,
    `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    CONSTRAINT `chk_lineage_generation_singleton` CHECK (`id` = 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='血缘结构单行代际计数器';

INSERT IGNORE INTO `t_ddh_lineage_generation` (`id`, `generation`) VALUES (1, 0);
