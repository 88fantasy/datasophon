-- T10 独立 bounded probe：只验证 JobListener 的 START/COMPLETE 和 Paimon input/output dataset。
SET 'execution.runtime-mode' = 'batch';

CREATE TABLE IF NOT EXISTS `lineage_emitter_probe_output` (
  `ID` STRING,
  PRIMARY KEY (`ID`) NOT ENFORCED
) WITH (
  'bucket' = '1'
);

INSERT INTO `lineage_emitter_probe_output`
SELECT `ID`
FROM `ods_smxt_lancet_aims_pat_surgery_full_daily`
LIMIT 1;
