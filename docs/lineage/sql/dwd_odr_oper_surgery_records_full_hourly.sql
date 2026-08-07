-- T8：Doris 方言清洗 SQL → Flink SQL 改写（源 = docs/lineage/dwd层建表语句清洗语句.md 第 2 节原始 SQL）
-- 逐条改写对照见 docs/lineage/sql/T8-改写对照清单.md，此文件是可执行的最终结果。
--
-- 执行上下文：flink-cluster-dwd（2.0.2），Paimon 主流 + Lookup Join，写 Doris DWD。
-- 凭据占位符由 LineageSqlRunner 的 --secrets-file 在内存中注入，不生成含明文凭据的 SQL 文件。
-- 主流需要 proc_time 做 Lookup Join 的时间点，Paimon 建表时没加这个计算列（T2 已建表，不想改），
-- 用 VIEW 包一层补上，不动物理表。

CREATE TEMPORARY VIEW ps_with_proctime AS
SELECT *, PROCTIME() AS proc_time
FROM ods_smxt_lancet_aims_pat_surgery_full_daily;

CREATE TEMPORARY TABLE IF NOT EXISTS dwd_odr_oper_surgery_records_full_hourly_sink (
  `surgery_id` STRING,
  `zyh` STRING,
  `xm` STRING,
  `xb` STRING,
  `nl` STRING,
  `szks` STRING,
  `sqkk` STRING,
  `ch` STRING,
  `surgery_type` STRING,
  `ssmc` STRING,
  `sqzd` STRING,
  `yblx` STRING,
  `sqsj` STRING,
  `ssrq` STRING,
  `rssj` STRING,
  `cssj` STRING,
  `sskssj` STRING,
  `ssjssj` STRING,
  `mzkssj` STRING,
  `mzjssj` STRING,
  `sszt` STRING,
  `mzfs` STRING,
  `sssmc` STRING,
  `zdks_id` STRING,
  `zdks_mc` STRING,
  `ssj` STRING,
  `tc` STRING,
  `surgery_doctor_name` STRING,
  `surgery_doctor_id` STRING,
  `circuit_nurse1_name` STRING,
  `equipment_nurse1_name` STRING,
  `anes_doctor1_name` STRING,
  `doctor_sync_id` STRING,
  `mzzs` STRING,
  `visit_id` STRING,
  `sync_id` STRING,
  `hzid` STRING,
  `surgery_level` STRING,
  `jxks` STRING,
  `jxks_sync_id` STRING,
  `etl_time` STRING,
  `jxks1` STRING,
  `modify_time` STRING
) WITH (
  'connector' = 'doris',
  'fenodes' = '192.168.10.131:8030',
  'table.identifier' = 'lineage_flink_verify.dwd_odr_oper_surgery_records_full_hourly',
  'username' = 'root',
  'password' = '__DORIS_PWD__',
  'sink.label-prefix' = 'flink_t9_dwd'
);

INSERT INTO dwd_odr_oper_surgery_records_full_hourly_sink
SELECT
  ps.ID AS surgery_id,
  IFNULL(ps.ADMISSION_NUMBER, '') AS zyh,
  IFNULL(ps.NAME, '') AS xm,
  IFNULL(ps.GENDER, '') AS xb,
  IFNULL(ps.AGE, '') AS nl,
  IFNULL(ps.DEPT_NAME, '') AS szks,
  IFNULL(ad.NAME, '') AS sqkk,
  IFNULL(ps.BED_NUMBER, '') AS ch,
  CASE ps.TYPE
    WHEN '1' THEN '择期手术'
    WHEN '2' THEN '急诊手术'
    WHEN '3' THEN '门诊手术'
    WHEN '4' THEN '技诊手术'
    WHEN '5' THEN '择期手术'
    WHEN '6' THEN '科外业务'
    WHEN '7' THEN '急诊手术'
    WHEN '8' THEN '体检手术'
    WHEN '9' THEN '预留手术'
    WHEN '10' THEN '加台手术'
    ELSE ''
  END AS surgery_type,
  IFNULL(ps.SURGERY_NAME, '') AS ssmc,
  IFNULL(ps.DIAGNOSIS_CONTENT, '') AS sqzd,
  CAST(NULL AS STRING) AS yblx,
  ps.APPLY_TIME AS sqsj,
  ps.SURGERY_DATE AS ssrq,
  ps.SURGERY_ENTRY_TIME AS rssj,
  ps.SURGERY_LEAVE_TIME AS cssj,
  ps.SURGERY_BEGIN_TIME AS sskssj,
  ps.SURGERY_END_TIME AS ssjssj,
  ps.ANES_BEGIN_TIME AS mzkssj,
  ps.ANES_END_TIME AS mzjssj,
  IFNULL(ps.STATE, '') AS sszt,
  IFNULL(ps.ANES_TYPE_NAME, '') AS mzfs,
  CASE
    WHEN ps.OR_DEPT_NAME = '一期中心手术室(南区)' THEN '中心手术室(南区一期)'
    WHEN ps.OR_DEPT_NAME = '中心手术室（南区二期）' THEN '中心手术室(南区二期)'
    ELSE IFNULL(ps.OR_DEPT_NAME, '')
  END AS sssmc,
  IFNULL(ps.DOCTOR_DEPT_ID, '') AS zdks_id,
  IFNULL(ps.DOCTOR_DEPT_NAME, '') AS zdks_mc,
  IFNULL(ps.ROOM_NAME, '') AS ssj,
  IFNULL(ps.SURGERY_SERIAL, '') AS tc,
  IFNULL(ps.SURGERY_DOCTOR_NAME, '') AS surgery_doctor_name,
  IFNULL(ps.SURGERY_DOCTOR_ID, '') AS surgery_doctor_id,
  IFNULL(CONCAT_WS('、', ps.CIRCUIT_NURSE1_NAME, ps.CIRCUIT_NURSE2_NAME), '') AS circuit_nurse1_name,
  IFNULL(CONCAT_WS('、', ps.EQUIPMENT_NURSE1_NAME, ps.EQUIPMENT_NURSE2_NAME), '') AS equipment_nurse1_name,
  IFNULL(CONCAT_WS('、', ps.ANES_DOCTOR1_NAME, ps.ANES_DOCTOR2_NAME), '') AS anes_doctor1_name,
  su.SYNC_ID AS doctor_sync_id,
  CASE
    WHEN ps.ANES_ASSISTANT IS NOT NULL
      AND ps.ANES_ASSISTANT NOT IN ('', 'null')
      AND ps.ANES_ASSISTANT IS JSON ARRAY
    THEN IFNULL(JSON_VALUE(ps.ANES_ASSISTANT, '$[0].name'), '')
    ELSE ''
  END AS mzzs,
  CAST(NULL AS STRING) AS visit_id,
  CAST(NULL AS STRING) AS sync_id,
  CAST(NULL AS STRING) AS hzid,
  CASE
    WHEN POSITION('Ⅳ级' IN IFNULL(ps.SURGERY_LEVEL, '')) > 0 THEN 'Ⅳ级'
    WHEN POSITION('Ⅲ级' IN IFNULL(ps.SURGERY_LEVEL, '')) > 0 THEN 'Ⅲ级'
    WHEN POSITION('Ⅱ级' IN IFNULL(ps.SURGERY_LEVEL, '')) > 0 THEN 'Ⅱ级'
    WHEN POSITION('Ⅰ级' IN IFNULL(ps.SURGERY_LEVEL, '')) > 0 THEN 'Ⅰ级'
    ELSE '未知'
  END AS surgery_level,
  bb.jxks AS jxks,
  ad.SYNC_ID AS jxks_sync_id,
  CAST(NOW() AS STRING) AS etl_time,
  COALESCE(ee.jxksmc, dd.jxksmc) AS jxks1,
  ps.MODIFY_TIME AS modify_time
FROM ps_with_proctime AS ps
LEFT JOIN ods_smxt_lancet_aims_pat_surgery_notice_full_daily
  FOR SYSTEM_TIME AS OF ps.proc_time AS psn
  ON psn.PATIENT_ID = ps.PATIENT_ID
LEFT JOIN ods_smxt_lancet_aims_sys_dept_full_daily
  FOR SYSTEM_TIME AS OF ps.proc_time AS ad
  ON ad.ID = psn.APPLY_DEPT_ID
LEFT JOIN ods_xy_jxkh_cwc_hsjxdyb_full_daily
  FOR SYSTEM_TIME AS OF ps.proc_time AS bb
  ON ad.SYNC_ID = bb.ksbm
LEFT JOIN ods_smxt_lancet_aims_sys_user_full_daily
  FOR SYSTEM_TIME AS OF ps.proc_time AS su
  ON ps.DOCTOR_DEPT_ID = su.DEPT_ID AND ps.SURGERY_DOCTOR_ID = su.ID
LEFT JOIN ods_xy_jxkh_v_ryb_full_daily
  FOR SYSTEM_TIME AS OF ps.proc_time AS cc
  ON su.SYNC_ID = cc.ygbh
LEFT JOIN ods_xy_jxkh_xzksjxdyb_full_daily
  FOR SYSTEM_TIME AS OF ps.proc_time AS dd
  ON cc.xzksbh = dd.xzksbh
LEFT JOIN ods_xy_jxkh_txryjxksb_full_daily
  FOR SYSTEM_TIME AS OF ps.proc_time AS ee
  ON ee.ygbh = su.SYNC_ID
WHERE ps.VALID = '1'
  AND ps.IS_TRANSFER_ROOM = '0';
