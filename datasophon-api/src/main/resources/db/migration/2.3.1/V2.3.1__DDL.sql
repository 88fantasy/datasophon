-- 接管扫描识别 operator CR：实例来源类型与看板画像
-- source_kind 与既有 source（INSTALLED/IMPORTED，2.3.0 引入）正交：
-- source 表达"平台安装 vs 扫描接管"，source_kind 表达"该实例是 Helm release 还是 operator CR"。
-- CR 场景复用既有 release_name 列存 CR 实例名（语义等价："这条实例对应的部署单元标识"）。
ALTER TABLE t_ddh_k8s_service_instance
    ADD COLUMN source_kind varchar(16) NOT NULL DEFAULT 'HELM' COMMENT '来源类型 HELM=Helm release CR=Operator 自定义资源',
    ADD COLUMN monitor_profile varchar(1024) DEFAULT NULL COMMENT '看板画像 JSON，如 {"profile":"doris-disaggregated","roles":{"fe":[...],"compute":[...]}}';
