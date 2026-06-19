-- otel 可观测存储:库 + 独立资源组 + 最小权限账号 (schema v1)
-- source: datasophon observability Phase A2, Task 1
-- vendoring: dorisexporter v0.154.0

-- 1. 建库
CREATE DATABASE IF NOT EXISTS otel;

-- 2. 独立 Workload Group，限制可观测负载占用（防拖垮业务），配额按部署调整
--    cpu_share=10 表示与其他 group 竞争时的权重比（相对值，默认 512）
--    memory_limit=20% 表示该 group 最多使用节点内存的 20%
--    enable_memory_overcommit=true 表示允许在内存空闲时超占
CREATE WORKLOAD GROUP IF NOT EXISTS otel_wg
PROPERTIES (
  "cpu_share" = "10",
  "memory_limit" = "20%",
  "enable_memory_overcommit" = "true"
);

-- 3. 采集账号：仅 Stream Load 所需 LOAD 权限（无 CREATE/DROP/DELETE）
--    口令由部署阶段 A3 下发时改为实际值，请务必替换 CHANGE_ME_AT_A3
CREATE USER IF NOT EXISTS 'otel_collector' IDENTIFIED BY 'CHANGE_ME_AT_A3';
GRANT LOAD_PRIV ON otel.* TO 'otel_collector';

-- 4. 看板读账号：仅 SELECT（与采集写账号分离，最小权限）
--    口令由部署阶段 A3 下发时改为实际值，请务必替换 CHANGE_ME_AT_A3
CREATE USER IF NOT EXISTS 'otel_reader' IDENTIFIED BY 'CHANGE_ME_AT_A3';
GRANT SELECT_PRIV ON otel.* TO 'otel_reader';
