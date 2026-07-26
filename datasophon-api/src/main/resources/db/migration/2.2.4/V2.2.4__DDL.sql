ALTER TABLE t_ddh_cluster_alert_history
    ADD INDEX idx_alert_history_cluster_time (cluster_id, create_time, id);
