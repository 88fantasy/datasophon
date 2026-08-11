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

  @Test
  void keepsOptionValuesIntactWhenTheyContainEscapedSingleQuotes() {
    // SqlSecretRenderer 用 '' 转义密钥里的单引号，所以渲染后的 SQL 真的会长这样。
    // 不认这个转义的话，password 会被截断成 "pa"，后续 option 的解析也可能错位。
    String sql =
        "CREATE TEMPORARY TABLE src (\n"
            + "  id INT\n"
            + ") WITH (\n"
            + "  'connector' = 'mysql-cdc',\n"
            + "  'password' = 'pa''ss',\n"
            + "  'hostname' = 'db-1'\n"
            + ");";

    SqlScript script = SqlScriptParser.parse(sql);

    var options = script.tempTables().get("src").options();
    assertEquals("mysql-cdc", options.get("connector"));
    assertEquals("pa'ss", options.get("password"));
    assertEquals("db-1", options.get("hostname"));
  }
}
