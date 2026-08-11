package com.datasophon.lineage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LineageSqlRunnerTest {

  @Test
  void parsesBooleanSafetyFlagsWithoutValues() {
    Map<String, String> options =
        LineageSqlRunner.parseArgs(
            new String[] {
              "--compile-only", "--allow-continuous-job", "--sql", "job.sql"
            });

    assertEquals("true", options.get("compile-only"));
    assertEquals("true", options.get("allow-continuous-job"));
    assertEquals("job.sql", options.get("sql"));
  }

  @Test
  void compileOnlyRejectsPersistentOrDestructiveStatements() {
    SqlScript script =
        SqlScriptParser.parse(
            "CREATE TEMPORARY TABLE source (id STRING) WITH ('connector' = 'datagen');\n"
                + "CREATE TABLE target (id STRING) WITH ('connector' = 'blackhole');\n"
                + "DROP TABLE target;");

    assertThrows(IllegalArgumentException.class, () -> LineageSqlRunner.validateCompileOnly(script));
  }

  @Test
  void compileOnlyAllowsOnlySessionScopedStatements() {
    SqlScript script =
        SqlScriptParser.parse(
            "SET 'execution.runtime-mode' = 'batch';\n"
                + "CREATE CATALOG paimon_s3 WITH ('type' = 'paimon');\n"
                + "USE CATALOG paimon_s3;\n"
                + "CREATE TEMPORARY TABLE source (id STRING) WITH ('connector' = 'datagen');\n"
                + "CREATE TEMPORARY VIEW source_view AS SELECT * FROM source;");

    assertDoesNotThrow(() -> LineageSqlRunner.validateCompileOnly(script));
  }

  @Test
  void recognizesBatchRuntimeModeForEnvironmentInitialization() {
    SqlScript script =
        SqlScriptParser.parse(
            "SET 'execution.runtime-mode' = 'batch';\n"
                + "CREATE TEMPORARY TABLE source (id STRING) WITH ('connector' = 'datagen');\n"
                + "INSERT INTO sink SELECT * FROM source;");

    assertEquals(true, LineageSqlRunner.isBatch(script));
  }

  @Test
  void requiresConfirmationWhenLastRuntimeModeIsStreaming() {
    SqlScript script =
        SqlScriptParser.parse(
            "SET 'execution.runtime-mode' = 'batch';\n"
                + "SET 'execution.runtime-mode' = 'streaming';\n"
                + "CREATE TEMPORARY TABLE source (id STRING) WITH ('connector' = 'datagen');\n"
                + "INSERT INTO sink SELECT * FROM source;");

    assertEquals(true, LineageSqlRunner.isContinuous(script));
  }

  @Test
  void ignoresBatchMarkerInsideCommentsWhenCheckingContinuousSql() {
    SqlScript script =
        SqlScriptParser.parse(
            "-- SET 'execution.runtime-mode' = 'batch';\n"
                + "CREATE TEMPORARY TABLE source (id STRING) WITH ('connector' = 'datagen');\n"
                + "INSERT INTO sink SELECT * FROM source;");

    assertEquals(true, LineageSqlRunner.isContinuous(script));
  }

  @Test
  void retriesTerminalEmissionUntilItSucceeds() {
    AtomicInteger attempts = new AtomicInteger();

    LineageSqlRunner.emitTerminalWithRetry(
        () -> {
          if (attempts.incrementAndGet() < 3) {
            throw new IllegalStateException("temporary Gravitino failure");
          }
        },
        3,
        0);

    assertEquals(3, attempts.get());
  }

  @Test
  void preservesJobFailureWhenFailEmissionExhaustsRetries() {
    ExecutionException jobFailure = new ExecutionException("Flink job failed", new RuntimeException());
    AtomicInteger attempts = new AtomicInteger();

    ExecutionException thrown =
        assertThrows(
            ExecutionException.class,
            () ->
                LineageSqlRunner.emitFailureAndRethrow(
                    () -> {
                      attempts.incrementAndGet();
                      throw new IllegalStateException("Gravitino unavailable");
                    },
                    jobFailure,
                    3,
                    0));

    assertSame(jobFailure, thrown);
    assertEquals(3, attempts.get());
    assertEquals(1, thrown.getSuppressed().length);
  }
}
