-- Flink 实时链路验证专用库，独立于任何生产/其他验证库
-- 命名沿用沙箱既有约定（对照 gravitino_lineage_1 / lineage_probe）
CREATE DATABASE IF NOT EXISTS `lineage_flink_verify` DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_general_ci;

-- CDC 连接账号：本沙箱不新建独立账号。
-- 实测（2026-08-06）：Gravitino entity store 专用账号 `gravitino`@`%` 已拥有全局
-- SELECT + REPLICATION SLAVE + REPLICATION CLIENT（见 gravitino.conf 的 jdbcUser 配置），
-- 恰好满足 flink-cdc mysql-cdc source 的全部权限要求，T6 直接复用该账号，不再新建专用用户。
-- 曾尝试新建 flink_cdc_user 并 GRANT，但 gravitino 账号本身无 GRANT OPTION（"Access denied ... to
-- database" 已实测证实），GRANT 会失败；空壳账号已清理（DROP USER），不留孤儿账号。
