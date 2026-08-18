-- 接管扫描识别 operator CR：实例来源类型与看板画像
-- source_kind 与既有 source（INSTALLED/IMPORTED，2.3.0 引入）正交：
-- source 表达"平台安装 vs 扫描接管"，source_kind 表达"该实例是 Helm release 还是 operator CR"。
-- CR 场景复用既有 release_name 列存 CR 实例名（语义等价："这条实例对应的部署单元标识"）。
ALTER TABLE t_ddh_k8s_service_instance
    ADD COLUMN source_kind varchar(16) NOT NULL DEFAULT 'HELM' COMMENT '来源类型 HELM=Helm release CR=Operator 自定义资源',
    ADD COLUMN monitor_profile varchar(1024) DEFAULT NULL COMMENT '看板画像 JSON，如 {"profile":"doris-disaggregated","roles":{"fe":[...],"compute":[...]}}';

-- 原 varchar(10) 只够放 "helm,yaml"（9 字符）这类组合；新增 kind=operator 后
-- "yaml,operator" 已达 13 字符，本地实测 DdlMetaServiceImpl.loadServiceK8sDdl 因此截断报错
-- （Data too long for column 'support_artifacts'）。扩到 64 覆盖 helm+yaml+operator 全组合并留余量。
ALTER TABLE t_ddh_frame_k8s_service
    MODIFY COLUMN support_artifacts varchar(64) DEFAULT NULL COMMENT '支持的部署方式，逗号分隔，取值 helm/yaml/operator';
