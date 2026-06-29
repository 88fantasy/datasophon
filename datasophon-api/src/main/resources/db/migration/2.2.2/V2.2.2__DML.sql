-- Phase E: Remove stale alert seed data for GRAFANA / ALERTMANAGER / LOKI / PROMTAIL.
-- These services are superseded by OTel Collector + OtelAlertScheduler (Phase A-D).
-- Existing cluster service instances are kept intact (operators should uninstall via UI).

-- 1. Remove alert rules referencing expressions from the four decommissioned services.
DELETE FROM t_ddh_cluster_alert_rule
WHERE expression_id IN (
    SELECT id FROM t_ddh_cluster_alert_expression
    WHERE service_category IN ('GRAFANA', 'ALERTMANAGER', 'LOKI', 'PROMTAIL')
);

-- 2. Remove alert expressions for the decommissioned services.
DELETE FROM t_ddh_cluster_alert_expression
WHERE service_category IN ('GRAFANA', 'ALERTMANAGER', 'LOKI', 'PROMTAIL');

-- 3. Remove Prometheus-query-based alert quotas for the decommissioned services.
DELETE FROM t_ddh_cluster_alert_quota
WHERE service_category IN ('GRAFANA', 'ALERTMANAGER', 'LOKI', 'PROMTAIL');

-- 4. Remove alert groups for the decommissioned services.
DELETE FROM t_ddh_alert_group
WHERE alert_group_category IN ('GRAFANA', 'ALERTMANAGER', 'LOKI', 'PROMTAIL');
