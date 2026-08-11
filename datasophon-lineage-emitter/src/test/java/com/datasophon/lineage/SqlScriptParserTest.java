package com.datasophon.lineage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SqlScriptParserTest {

  @Test
  void parsesAllT6StatementSetInserts() throws Exception {
    String sql =
        Files.readString(
            Path.of("../docs/lineage/sql/t6_mysql_cdc_to_paimon.sql"));

    SqlScript script = SqlScriptParser.parse(sql);

    assertEquals(4, script.insertStatements().size());
    assertEquals(4, script.tempTables().size());
  }
}
