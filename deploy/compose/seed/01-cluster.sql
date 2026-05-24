-- 测试环境种子数据：插入一条集群记录供 Worker 注册用
-- cluster_state=1 (NEED_CONFIG), arch_type='physical'
INSERT IGNORE INTO t_ddh_cluster_info
  (id, cluster_name, cluster_code, cluster_frame, frame_version, cluster_state, arch_type, create_time)
VALUES
  (1, 'compose-test', 'compose-test', 'DDP-1.0.0', '3.6.0', 1, 'physical', NOW());
