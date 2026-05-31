-- Standalone 拓扑模拟环境种子数据：插入一条集群记录供 Worker 注册用
-- mw-worker(hostname=mw1) / app1-worker(hostname=app1) / app2-worker(hostname=app2) 均注册到此集群
INSERT IGNORE INTO t_ddh_cluster_info
  (id, cluster_name, cluster_code, cluster_frame, frame_version, cluster_state, arch_type, create_time)
VALUES
  (1, 'standalone-topology', 'standalone-topology', 'DDP-1.0.0', '3.6.0', 1, 'physical', NOW());
