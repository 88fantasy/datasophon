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
}
