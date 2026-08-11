-- Flink 原生 Paimon catalog 初始化。凭据只经 LineageSqlRunner 的 --secrets-file 在内存注入。
CREATE CATALOG paimon_s3 WITH (
  'type' = 'paimon',
  'warehouse' = 's3://lineage-paimon-warehouse/',
  's3.endpoint' = 'http://192.168.10.131:9040',
  's3.access-key' = '__S3_ACCESS_KEY__',
  's3.secret-key' = '__S3_SECRET_KEY__',
  's3.path.style.access' = 'true'
);
USE CATALOG paimon_s3;
USE lineage_flink_verify;
