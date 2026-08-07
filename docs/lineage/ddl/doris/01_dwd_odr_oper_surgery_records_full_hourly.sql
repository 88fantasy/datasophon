-- DWD 层建表，直接基于 docs/lineage/dwd层建表语句清洗语句.md 的原始建表语句，
-- 仅补沙箱参数：库名 lineage_flink_verify（新建，独立于既有 lineage_probe 验证库）、
-- replication_num=3（实测 3 BE Alive，见 T3 自检）
CREATE DATABASE IF NOT EXISTS `lineage_flink_verify`;

DROP TABLE IF EXISTS `lineage_flink_verify`.`dwd_odr_oper_surgery_records_full_hourly`;

CREATE TABLE `lineage_flink_verify`.`dwd_odr_oper_surgery_records_full_hourly` (
`surgery_id` VARCHAR(255) NOT NULL COMMENT '手术id' ,
`zyh` VARCHAR(100) NULL COMMENT '住院号' ,
`xm` VARCHAR(255) NULL COMMENT '姓名' ,
`xb` VARCHAR(255) NULL COMMENT '性别' ,
`nl` VARCHAR(255) NULL COMMENT '年龄' ,
`szks` VARCHAR(255) NULL COMMENT '患者所在科室' ,
`sqkk` VARCHAR(255) NULL COMMENT '医生所在科室' ,
`ch` VARCHAR(255) NULL COMMENT '床号' ,
`surgery_type` VARCHAR(255) NULL COMMENT '手术类型' ,
`ssmc` VARCHAR(500) NULL COMMENT '手术名称' ,
`sqzd` VARCHAR(255) NULL COMMENT '术前诊断' ,
`yblx` VARCHAR(255) NULL COMMENT '医保类型' ,
`sqsj` VARCHAR(255) NULL COMMENT '手术申请时间' ,
`ssrq` VARCHAR(255) NULL COMMENT '手术日期' ,
`rssj` VARCHAR(255) NULL COMMENT '入手术间时间' ,
`cssj` VARCHAR(255) NULL COMMENT '出手术间时间' ,
`sskssj` VARCHAR(255) NULL COMMENT '手术开始时间' ,
`ssjssj` VARCHAR(255) NULL COMMENT '手术结束时间' ,
`mzkssj` VARCHAR(255) NULL COMMENT '麻醉开始时间' ,
`mzjssj` VARCHAR(255) NULL COMMENT '麻醉结束时间' ,
`sszt` VARCHAR(255) NULL COMMENT '手术状态' ,
`mzfs` VARCHAR(255) NULL COMMENT '麻醉方法' ,
`sssmc` VARCHAR(255) NULL COMMENT '手术室名称',
`zdks_id` VARCHAR(255) NULL COMMENT '主刀科室id',
`zdks_mc` VARCHAR(255) NULL COMMENT '主刀科室名称',
`ssj` VARCHAR(255) NULL COMMENT '手术间' ,
`tc` VARCHAR(255) NULL COMMENT '台次' ,
`surgery_doctor_name` VARCHAR(255) NULL COMMENT '主刀医生名称' ,
`surgery_doctor_id` VARCHAR(255) NULL COMMENT '主刀医生id' ,
`circuit_nurse1_name` VARCHAR(255) NULL COMMENT '巡回护士姓名' ,
`equipment_nurse1_name` VARCHAR(255) NULL COMMENT '器械护士姓名' ,
`anes_doctor1_name` VARCHAR(255) NULL COMMENT '麻醉医生姓名' ,
`doctor_sync_id` VARCHAR(255) NULL COMMENT '麻醉医生id' ,
`mzzs` VARCHAR(255) NULL COMMENT '麻醉助手' ,
`visit_id` VARCHAR(255) NULL COMMENT '' ,
`sync_id` VARCHAR(255) NULL COMMENT '' ,
`hzid` VARCHAR(255) NULL COMMENT '' ,
`surgery_level` VARCHAR(255) NULL COMMENT '手术级别' ,
`jxks` VARCHAR(255) NULL COMMENT '绩效科室' ,
`jxks_sync_id` VARCHAR(255) NULL COMMENT '绩效科室id' ,
`etl_time` VARCHAR(255) NULL COMMENT '',
`jxks1` VARCHAR(255) NULL COMMENT '绩效科室id',
`modify_time` VARCHAR(255) NULL COMMENT '修改时间'
)
ENGINE=olap
UNIQUE KEY(surgery_id)
COMMENT '手术麻醉大屏表'
DISTRIBUTED BY HASH(surgery_id) BUCKETS 3
PROPERTIES (
  "replication_num" = "3"
);
