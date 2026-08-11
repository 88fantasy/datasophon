package com.datasophon.lineage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.Test;

class SqlSecretRendererTest {

  @Test
  void rendersOnlyExactSqlStringLiteralPlaceholders() throws Exception {
    Path secrets = Files.createTempFile("lineage-secrets", ".json");
    Files.setPosixFilePermissions(secrets, PosixFilePermissions.fromString("rw-------"));
    Files.writeString(secrets, "{\"MYSQL_PWD\":\"pa'ss\"}");

    SqlSecretRenderer.Renderer renderer = SqlSecretRenderer.from(secrets);
    assertEquals("'pa''ss'", renderer.render("'__MYSQL_PWD__'"));
    renderer.validateAllUsed();
  }

  @Test
  void rejectsUnusedSecretsWithoutExposingValues() throws Exception {
    Path secrets = Files.createTempFile("lineage-secrets", ".json");
    Files.setPosixFilePermissions(secrets, PosixFilePermissions.fromString("rw-------"));
    Files.writeString(secrets, "{\"MYSQL_PWD\":\"private-value\",\"EXTRA\":\"unused\"}");

    SqlSecretRenderer.Renderer renderer = SqlSecretRenderer.from(secrets);
    renderer.render("'__MYSQL_PWD__'");
    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, renderer::validateAllUsed);
    assertEquals("Secrets file contains unused entries", exception.getMessage());
  }

  @Test
  void rejectsPlaceholdersOutsideSqlStringLiterals() throws Exception {
    Path secrets = Files.createTempFile("lineage-secrets", ".json");
    Files.setPosixFilePermissions(secrets, PosixFilePermissions.fromString("rw-------"));
    Files.writeString(secrets, "{\"MYSQL_PWD\":\"private-value\"}");

    SqlSecretRenderer.Renderer renderer = SqlSecretRenderer.from(secrets);
    assertThrows(IllegalArgumentException.class, () -> renderer.render("__MYSQL_PWD__"));
  }

  @Test
  void apostropheInACommentDoesNotFlipLiteralDetection() throws Exception {
    // 注释里一个英文缩写的撇号就足以翻转字面量状态。翻错的方向决定后果：判成"不在字面量内"
    // 只是误拒合法脚本；判成"在字面量内"会让密钥被拼到字面量之外，那是注入。
    Path secrets = Files.createTempFile("lineage-secrets", ".json");
    Files.setPosixFilePermissions(secrets, PosixFilePermissions.fromString("rw-------"));
    Files.writeString(secrets, "{\"MYSQL_PWD\":\"private-value\"}");

    SqlSecretRenderer.Renderer renderer = SqlSecretRenderer.from(secrets);

    assertEquals(
        "-- don't touch this\n'private-value'",
        renderer.render("-- don't touch this\n'__MYSQL_PWD__'"));
  }

  @Test
  void apostropheInABlockCommentDoesNotFlipLiteralDetection() throws Exception {
    Path secrets = Files.createTempFile("lineage-secrets", ".json");
    Files.setPosixFilePermissions(secrets, PosixFilePermissions.fromString("rw-------"));
    Files.writeString(secrets, "{\"MYSQL_PWD\":\"private-value\"}");

    SqlSecretRenderer.Renderer renderer = SqlSecretRenderer.from(secrets);

    assertEquals(
        "/* it's fine */ 'private-value'",
        renderer.render("/* it's fine */ '__MYSQL_PWD__'"));
  }

  @Test
  void rejectsPlaceholderThatOnlyAppearsInsideAComment() throws Exception {
    Path secrets = Files.createTempFile("lineage-secrets", ".json");
    Files.setPosixFilePermissions(secrets, PosixFilePermissions.fromString("rw-------"));
    Files.writeString(secrets, "{\"MYSQL_PWD\":\"private-value\"}");

    SqlSecretRenderer.Renderer renderer = SqlSecretRenderer.from(secrets);

    assertThrows(
        IllegalArgumentException.class, () -> renderer.render("-- password is __MYSQL_PWD__\n"));
  }

  @Test
  void rejectsSecretValuesContainingABackslash() throws Exception {
    // render() 只做 SQL-92 的 '' 转义；这些 SQL 最终会到 MySQL，那里反斜杠默认仍是转义符，
    // 值里带 \ 时转义不完整。直接在入口拒绝，而不是做一套依赖目标方言的转义。
    Path secrets = Files.createTempFile("lineage-secrets", ".json");
    Files.setPosixFilePermissions(secrets, PosixFilePermissions.fromString("rw-------"));
    Files.writeString(secrets, "{\"MYSQL_PWD\":\"pa\\\\ss\"}");

    IllegalArgumentException exception =
        assertThrows(IllegalArgumentException.class, () -> SqlSecretRenderer.from(secrets));
    assertEquals("Secrets file contains an invalid entry", exception.getMessage());
  }
}
