package com.datasophon.lineage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DatasetResolverTest {

  @Test
  void resolvesMysqlCdcAndPaimonDatasetsFromSingleSinkPlan() {
    String plan =
        """
        {"nodes":[
          {"id":1,"scanTableSource":{"table":{"identifier":"`paimon_s3`.`lineage_flink_verify`.`mysql_pat_surgery`"}}},
          {"id":2,"dynamicTableSink":{"table":{"identifier":"`paimon_s3`.`lineage_flink_verify`.`ods_smxt_lancet_aims_pat_surgery_full_daily`"}}}
        ],
        "edges":[{"source":1,"target":2}]}
        """;
    SqlScript.TempTableInfo source =
        new SqlScript.TempTableInfo(
            "mysql_pat_surgery",
            Map.of(
                "connector", "mysql-cdc",
                "hostname", "192.168.10.131",
                "port", "3306",
                "database-name", "lineage_flink_verify",
                "table-name", "pat_surgery"));

    DatasetResolver.Resolution resolution = DatasetResolver.resolve(plan, Map.of(source.name(), source));

    assertEquals(1, resolution.pipelines().size());
    DatasetResolver.Pipeline pipeline = resolution.pipelines().get(0);
    assertEquals(
        new DatasetIdentity(
            "paimon://paimon_s3/lineage_flink_verify",
            "ods_smxt_lancet_aims_pat_surgery_full_daily"),
        pipeline.output());
    assertEquals(1, pipeline.inputs().size());
    assertEquals(
        new DatasetIdentity("mysql-cdc://192.168.10.131:3306", "lineage_flink_verify.pat_surgery"),
        pipeline.inputs().iterator().next());
  }

  @Test
  void resolvesLookupJoinTemporalTableAsInput() {
    // stream-exec-lookup-join nodes don't have a scanTableSource — the temporal table they read
    // is nested under temporalTable.lookupTableSource (confirmed against a real T9 CompiledPlan).
    String plan =
        """
        {"nodes":[
          {"id":1,"scanTableSource":{"table":{"identifier":"`paimon_s3`.`lineage_flink_verify`.`ods_smxt_lancet_aims_pat_surgery_full_daily`"}}},
          {"id":2,"temporalTable":{"lookupTableSource":{"table":{"identifier":"`paimon_s3`.`lineage_flink_verify`.`ods_smxt_lancet_aims_sys_dept_full_daily`"}}}},
          {"id":3,"dynamicTableSink":{"table":{"identifier":"`paimon_s3`.`lineage_flink_verify`.`dwd_sink`"}}}
        ],
        "edges":[{"source":1,"target":2},{"source":2,"target":3}]}
        """;

    DatasetResolver.Resolution resolution = DatasetResolver.resolve(plan, Map.of());

    assertEquals(1, resolution.pipelines().size());
    DatasetResolver.Pipeline pipeline = resolution.pipelines().get(0);
    assertEquals(2, pipeline.inputs().size());
    assertEquals(
        Set.of(
            new DatasetIdentity(
                "paimon://paimon_s3/lineage_flink_verify",
                "ods_smxt_lancet_aims_pat_surgery_full_daily"),
            new DatasetIdentity(
                "paimon://paimon_s3/lineage_flink_verify", "ods_smxt_lancet_aims_sys_dept_full_daily")),
        pipeline.inputs());
  }

  @Test
  void pairsEachSinkWithOnlyItsOwnInputsInAMultiInsertStatementSet() {
    // T15 finding: a STATEMENT SET compiles into disjoint connected components, one per INSERT.
    // Flattening across the whole plan (the pre-T15 design) would report all inputs as feeding
    // all outputs — a real bug caught via a real T6 CompiledPlan (4 inputs x 4 outputs = 16
    // edges stored in Gravitino, 12 of them factually wrong). This plan mirrors that shape with
    // two independent two-node pipelines that must not cross-contaminate each other's inputs.
    String plan =
        """
        {"nodes":[
          {"id":1,"scanTableSource":{"table":{"identifier":"`paimon_s3`.`lineage_flink_verify`.`mysql_pat_surgery`"}}},
          {"id":2,"dynamicTableSink":{"table":{"identifier":"`paimon_s3`.`lineage_flink_verify`.`ods_pat_surgery`"}}},
          {"id":3,"scanTableSource":{"table":{"identifier":"`paimon_s3`.`lineage_flink_verify`.`mysql_sys_dept`"}}},
          {"id":4,"dynamicTableSink":{"table":{"identifier":"`paimon_s3`.`lineage_flink_verify`.`ods_sys_dept`"}}}
        ],
        "edges":[{"source":1,"target":2},{"source":3,"target":4}]}
        """;
    SqlScript.TempTableInfo patSurgery =
        new SqlScript.TempTableInfo(
            "mysql_pat_surgery",
            Map.of(
                "connector", "mysql-cdc",
                "hostname", "192.168.10.131",
                "port", "3306",
                "database-name", "lineage_flink_verify",
                "table-name", "pat_surgery"));
    SqlScript.TempTableInfo sysDept =
        new SqlScript.TempTableInfo(
            "mysql_sys_dept",
            Map.of(
                "connector", "mysql-cdc",
                "hostname", "192.168.10.131",
                "port", "3306",
                "database-name", "lineage_flink_verify",
                "table-name", "sys_dept"));

    DatasetResolver.Resolution resolution =
        DatasetResolver.resolve(
            plan, Map.of(patSurgery.name(), patSurgery, sysDept.name(), sysDept));

    assertEquals(2, resolution.pipelines().size());
    for (DatasetResolver.Pipeline pipeline : resolution.pipelines()) {
      assertEquals(1, pipeline.inputs().size(), "each sink must have exactly its own single input");
      String inputTable = pipeline.inputs().iterator().next().name();
      if (pipeline.output().name().equals("ods_pat_surgery")) {
        assertEquals("lineage_flink_verify.pat_surgery", inputTable);
      } else {
        assertEquals("lineage_flink_verify.sys_dept", inputTable);
      }
    }
  }
}
