-- 接管现有 K8s 集群：集群管理模式
ALTER TABLE t_ddh_cluster_info
    ADD COLUMN manage_mode varchar(16) NOT NULL DEFAULT 'MANAGED' COMMENT '管理模式 MANAGED=平台安装 IMPORTED=接管现有集群';

-- 接管集群的外部 OTel Doris 数据源（密码不落此表，走 OtelCredentialService）
ALTER TABLE t_ddh_k8s_cluster_config
    ADD COLUMN doris_host varchar(128) DEFAULT NULL COMMENT '接管集群 OTel Doris FE 主机',
    ADD COLUMN doris_port int(11) DEFAULT NULL COMMENT 'Doris MySQL 协议端口，缺省 9030',
    ADD COLUMN doris_database varchar(64) DEFAULT NULL COMMENT 'OTel 数据库名，缺省 otel';

-- 服务实例来源与采集标识
-- metrics_job 为逗号分隔的多值：一个服务可能对应多个 Prometheus job，
-- 例如 DolphinScheduler 有 dolphinscheduler-api / -master-headless / -worker-headless 三个。
ALTER TABLE t_ddh_k8s_service_instance
    ADD COLUMN source varchar(16) NOT NULL DEFAULT 'INSTALLED' COMMENT '来源 INSTALLED=平台安装 IMPORTED=扫描接管',
    ADD COLUMN release_name varchar(128) DEFAULT NULL COMMENT '接管实例对应的 Helm release 名',
    ADD COLUMN metrics_job varchar(512) DEFAULT NULL COMMENT 'OTel service_name(job) 列表，逗号分隔';
