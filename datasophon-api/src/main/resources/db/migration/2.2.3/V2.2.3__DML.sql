-- DS 告警角色 ddl 里的名称已从 UAlertServer 改回正确命名 AlertServer（与 OtelScrapeConfigBuilder.PATH_OVERRIDES
-- 映射键、t_ddh_cluster_alert_quota 种子数据的 serviceRoleName 保持一致）。ddl 重新加载只会新增/更新
-- AlertServer 这一条角色定义，不会重命名已安装集群里持久化的旧角色实例，导致老角色组 metadata 查找失败
-- （OTel 抓取配置、jmxPortParam 均按 serviceRoleName 查找），需要显式迁移已有数据。
UPDATE t_ddh_cluster_service_role_instance SET service_role_name = 'AlertServer' WHERE service_role_name = 'UAlertServer';
UPDATE t_ddh_frame_service_role SET service_role_name = 'AlertServer' WHERE service_role_name = 'UAlertServer';
