-- 2.3.0/2.3.1 曾按 service_id 判重，同一部署单元重新绑定到其他服务定义时可能留下多行。
-- 保留最大 id（最后一次登记），并把仅有的两类引用迁到保留行，避免唯一索引升级失败。
CREATE TEMPORARY TABLE tmp_k8s_imported_unit_keep AS
SELECT cluster_id, namespace_id, source_kind, release_name, MAX(id) AS keep_id
FROM t_ddh_k8s_service_instance
WHERE release_name IS NOT NULL
GROUP BY cluster_id, namespace_id, source_kind, release_name
HAVING COUNT(*) > 1;

CREATE TEMPORARY TABLE tmp_k8s_imported_unit_duplicate AS
SELECT i.id AS duplicate_id, k.keep_id
FROM t_ddh_k8s_service_instance i
JOIN tmp_k8s_imported_unit_keep k
  ON i.cluster_id <=> k.cluster_id
 AND i.namespace_id <=> k.namespace_id
 AND i.source_kind = k.source_kind
 AND i.release_name = k.release_name
WHERE i.id <> k.keep_id;

UPDATE t_ddh_k8s_service_instance_values v
JOIN tmp_k8s_imported_unit_duplicate d
  ON v.instance_id = d.duplicate_id
SET v.instance_id = d.keep_id;

UPDATE t_ddh_cluster_k8s_service_command c
JOIN tmp_k8s_imported_unit_duplicate d
  ON c.service_instance_id = d.duplicate_id
SET c.service_instance_id = d.keep_id;

DELETE i
FROM t_ddh_k8s_service_instance i
JOIN tmp_k8s_imported_unit_duplicate d
  ON i.id = d.duplicate_id;

DROP TEMPORARY TABLE tmp_k8s_imported_unit_duplicate;
DROP TEMPORARY TABLE tmp_k8s_imported_unit_keep;

-- 接管实例以部署单元为唯一身份，允许同一 namespace 下存在同 chart 的多个 release。
-- release_name 对平台安装实例为 NULL；MySQL 唯一索引允许多行 NULL，不影响原安装模型。
ALTER TABLE t_ddh_k8s_service_instance
    MODIFY COLUMN release_name varchar(253) DEFAULT NULL COMMENT '接管实例对应的 Helm release 或 CR 实例名',
    ADD UNIQUE KEY uk_k8s_imported_unit
        (cluster_id, namespace_id, source_kind, release_name);
