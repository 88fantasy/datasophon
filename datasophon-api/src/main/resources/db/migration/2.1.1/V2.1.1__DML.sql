-- Rename REDIS service to VALKEY in Grafana dashboard configuration.
-- Redis 8.x switched to non-OSI licensing (RSALv2/SSPLv1); replaced with Valkey 8.1.7 (BSD-3).
-- The dashboard_url is unchanged as we continue using oliver006/redis_exporter (Valkey wire-compatible).
UPDATE `t_ddh_cluster_service_dashboard`
SET `service_name` = 'VALKEY'
WHERE `service_name` = 'REDIS';
