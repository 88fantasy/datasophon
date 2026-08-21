-- K8sClusterConfig.kubeConfig 在 2.3.x 的接管凭据整改中加了 updateStrategy=IGNORED，
-- 目的是切换 type（config_file -> token/password）时把旧的 kubeConfig 真正清空落库，
-- 避免旧凭据残留被 SecureKubeConfigWriter 误用。但该列自 2.1.0 建表起一直是 NOT NULL
-- 且无默认值，导致 UPDATE 置 null 直接报 SQLIntegrityConstraintViolationException，
-- 首次以 token/password 方式新建配置（INSERT 阶段 kubeConfig 本就是 null）更是直接失败
-- （Field 'kube_config' doesn't have a default value）。放开为可空，与同表 token/username/
-- password 三列的既有约束保持一致。
ALTER TABLE t_ddh_k8s_cluster_config
    MODIFY COLUMN kube_config text NULL COMMENT '配置文件内容，type=config_file 有效';
