-- otel 可观测存储:库 + 独立资源组 + 最小权限账号 (schema v1)
-- source: datasophon observability Phase A2, Task 1
-- vendoring: dorisexporter v0.156.0

-- 1. 建库
CREATE DATABASE IF NOT EXISTS otel;

-- 2. 独立 Workload Group，让可观测负载与业务负载相互隔离，配额按部署调整
--    cpu_share=10 是 CPU 竞争时的相对权重（非硬上限；默认 512，越小越让路）
--    memory_limit=20% 是软上限，配合 enable_memory_overcommit=true 空闲时可超占
--    注：仅 CREATE 资源组不生效——账号必须 GRANT USAGE_PRIV 且设 default_workload_group 才会进入本组（见 5/6）
CREATE WORKLOAD GROUP IF NOT EXISTS otel_wg
PROPERTIES (
  "cpu_share" = "10",
  "memory_limit" = "20%",
  "enable_memory_overcommit" = "true"
);

-- 3. 采集账号：仅 Stream Load 所需 LOAD 权限（无 CREATE/DROP/DELETE）
--    口令由部署阶段 A3 下发时改为实际值，请务必替换 CHANGE_ME_AT_A3
CREATE USER IF NOT EXISTS 'otel_collector' IDENTIFIED BY 'CHANGE_ME_AT_A3_COLLECTOR';
ALTER USER 'otel_collector' IDENTIFIED BY 'CHANGE_ME_AT_A3_COLLECTOR';
GRANT LOAD_PRIV ON otel.* TO 'otel_collector';

-- 4. 看板读账号：仅 SELECT（与采集写账号分离，最小权限）
--    口令由部署阶段 A3 下发时改为实际值，请务必替换 CHANGE_ME_AT_A3
CREATE USER IF NOT EXISTS 'otel_reader' IDENTIFIED BY 'CHANGE_ME_AT_A3_READER';
ALTER USER 'otel_reader' IDENTIFIED BY 'CHANGE_ME_AT_A3_READER';
GRANT SELECT_PRIV ON otel.* TO 'otel_reader';

-- 5. 把两个账号绑定到 otel_wg（USAGE_PRIV 是使用资源组的必要条件）
GRANT USAGE_PRIV ON WORKLOAD GROUP 'otel_wg' TO 'otel_collector';
GRANT USAGE_PRIV ON WORKLOAD GROUP 'otel_wg' TO 'otel_reader';

-- 6. 设为各账号默认资源组，使其连接默认进入 otel_wg（否则仍走 normal 组）
SET PROPERTY FOR 'otel_collector' 'default_workload_group' = 'otel_wg';
SET PROPERTY FOR 'otel_reader' 'default_workload_group' = 'otel_wg';
