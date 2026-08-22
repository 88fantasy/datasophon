-- 接管现有 K8s 集群：集群管理模式
ALTER TABLE t_ddh_cluster_info
    ADD COLUMN manage_mode varchar(16) NOT NULL DEFAULT 'MANAGED' COMMENT '管理模式 MANAGED=平台安装 IMPORTED=接管现有集群';

-- 接管集群的外部 OTel Doris 数据源（密码不落此表，走 OtelCredentialService）
-- 不存库名：OTel 数据由离线安装包里的 collector 写入，库名恒为 otel，查询侧同样按 otel 硬编码。
ALTER TABLE t_ddh_k8s_cluster_config
    MODIFY COLUMN kube_config text NULL COMMENT '配置文件内容，type=config_file 有效',
    ADD COLUMN doris_host varchar(128) DEFAULT NULL COMMENT '接管集群 OTel Doris FE 主机',
    ADD COLUMN doris_port int(11) DEFAULT NULL COMMENT 'Doris MySQL 协议端口，缺省 9030';

-- 服务实例来源与采集标识
-- metrics_job 为逗号分隔的多值：一个服务可能对应多个 Prometheus job，
-- 例如 DolphinScheduler 有 dolphinscheduler-api / -master-headless / -worker-headless 三个。
ALTER TABLE t_ddh_k8s_service_instance
    ADD COLUMN source varchar(16) NOT NULL DEFAULT 'INSTALLED' COMMENT '来源 INSTALLED=平台安装 IMPORTED=扫描接管',
    ADD COLUMN release_name varchar(128) DEFAULT NULL COMMENT '接管实例对应的 Helm release 名',
    ADD COLUMN metrics_job varchar(512) DEFAULT NULL COMMENT 'OTel service_name(job) 列表，逗号分隔',
    ADD COLUMN source_kind varchar(16) NOT NULL DEFAULT 'HELM' COMMENT '来源类型 HELM=Helm release CR=Operator 自定义资源',
    ADD COLUMN monitor_profile varchar(1024) DEFAULT NULL COMMENT '看板画像 JSON';

ALTER TABLE t_ddh_frame_k8s_service
    MODIFY COLUMN support_artifacts varchar(64) DEFAULT NULL COMMENT '支持的部署方式，逗号分隔，取值 helm/yaml/operator';

ALTER TABLE t_ddh_k8s_service_instance
    MODIFY COLUMN release_name varchar(253) DEFAULT NULL COMMENT '接管实例对应的 Helm release 或 CR 实例名',
    ADD UNIQUE KEY uk_k8s_imported_unit
        (cluster_id, namespace_id, source_kind, release_name);
