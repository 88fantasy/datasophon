package com.datasophon.lineage;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.flink.api.common.RuntimeExecutionMode;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.table.api.CompiledPlan;
import org.apache.flink.table.api.StatementSet;
import org.apache.flink.table.api.TableResult;
import org.apache.flink.table.api.bridge.java.StreamTableEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LineageSqlRunner {

  private static final Logger LOG = LoggerFactory.getLogger(LineageSqlRunner.class);
  private static final Pattern SET_OPTION =
      Pattern.compile("^SET\\s+'([^']+)'\\s*=\\s*'([^']*)'$", Pattern.CASE_INSENSITIVE);
  private static final Pattern RUNTIME_MODE =
      Pattern.compile(
          "^SET\\s+'execution\\.runtime-mode'\\s*=\\s*'(batch|streaming)'$",
          Pattern.CASE_INSENSITIVE);
  private static final int TERMINAL_EMIT_MAX_ATTEMPTS = 3;
  private static final long TERMINAL_EMIT_INITIAL_RETRY_DELAY_MILLIS = 250;

  private LineageSqlRunner() {}

  public static void main(String[] args) throws Exception {
    Map<String, String> options = parseArgs(args);
    boolean compileOnly = options.containsKey("compile-only");
    String sqlPath = require(options, "sql");
    SqlSecretRenderer.Renderer renderer = secretRenderer(options);
    String rawSql = render(readSql(sqlPath), renderer);
    SqlScript script = SqlScriptParser.parse(rawSql);
    if (compileOnly) {
      validateCompileOnly(script);
    } else if (isContinuous(script) && !options.containsKey("allow-continuous-job")) {
      throw new IllegalArgumentException(
          "Continuous SQL requires explicit --allow-continuous-job confirmation");
    }
    LOG.info(
        "[lineage] parsed {}: {} DDL/USE statements, {} INSERTs, {} CREATE TEMPORARY TABLE(s)",
        sqlPath,
        script.executeImmediately().size(),
        script.insertStatements().size(),
        script.tempTables().size());

    StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
    if (isBatch(script)) {
      env.setRuntimeMode(RuntimeExecutionMode.BATCH);
    }
    StreamTableEnvironment tEnv = StreamTableEnvironment.create(env);
    String bootstrapPath = options.get("bootstrap-sql");
    String bootstrapSql = bootstrapPath == null ? null : render(readSql(bootstrapPath), renderer);
    if (compileOnly && bootstrapSql != null) {
      validateCompileOnly(SqlScriptParser.parse(bootstrapSql));
    }
    if (bootstrapSql != null) {
      executeSetup(tEnv, bootstrapSql);
    }
    for (String statement : script.executeImmediately()) {
      executeSetupStatement(tEnv, statement);
    }
    renderer.validateAllUsed();

    StatementSet statementSet = tEnv.createStatementSet();
    for (String insert : script.insertStatements()) {
      statementSet.addInsertSql(insert);
    }
    CompiledPlan compiledPlan = statementSet.compilePlan();
    DatasetResolver.Resolution resolution =
        DatasetResolver.resolve(compiledPlan.asJsonString(), script.tempTables());
    LOG.info(
        "[lineage] resolved {} pipeline(s): {}",
        resolution.pipelines().size(),
        resolution.pipelines().stream()
            .map(p -> p.inputs().size() + " input(s) -> " + p.output().name())
            .toList());
    if (compileOnly) {
      return;
    }

    String jobName = require(options, "job-name");
    String jobNamespace = options.getOrDefault("job-namespace", "flink");
    String gravitinoUrl = require(options, "gravitino-url");
    try (GravitinoLineageEmitter emitter =
        new GravitinoLineageEmitter(
            gravitinoUrl, readToken(Path.of(require(options, "auth-token-file"))), jobNamespace, jobName)) {
      GravitinoLineageJobListener listener =
          new GravitinoLineageJobListener(emitter, resolution.pipelines());
      env.registerJobListener(listener);
      TableResult result = compiledPlan.execute();
      try {
        result.await();
        emitTerminalWithRetry(
            listener::emitCompleteAfterAwait,
            TERMINAL_EMIT_MAX_ATTEMPTS,
            TERMINAL_EMIT_INITIAL_RETRY_DELAY_MILLIS);
      } catch (ExecutionException e) {
        emitFailureAndRethrow(
            listener::emitFailAfterAwait,
            e,
            TERMINAL_EMIT_MAX_ATTEMPTS,
            TERMINAL_EMIT_INITIAL_RETRY_DELAY_MILLIS);
      }
    }
  }

  static void emitTerminalWithRetry(
      Runnable terminalEmission, int maxAttempts, long initialRetryDelayMillis) {
    if (maxAttempts < 1 || initialRetryDelayMillis < 0) {
      throw new IllegalArgumentException("Invalid terminal emission retry configuration");
    }
    RuntimeException firstFailure = null;
    long retryDelayMillis = initialRetryDelayMillis;
    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        terminalEmission.run();
        return;
      } catch (RuntimeException failure) {
        if (firstFailure == null) {
          firstFailure = failure;
        } else if (failure != firstFailure) {
          firstFailure.addSuppressed(failure);
        }
        if (attempt == maxAttempts) {
          break;
        }
        LOG.warn(
            "[lineage] terminal emission attempt {}/{} failed; retrying in {} ms",
            attempt,
            maxAttempts,
            retryDelayMillis,
            failure);
        try {
          Thread.sleep(retryDelayMillis);
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          firstFailure.addSuppressed(interrupted);
          throw firstFailure;
        }
        retryDelayMillis *= 2;
      }
    }
    throw firstFailure;
  }

  static void emitFailureAndRethrow(
      Runnable failureEmission,
      ExecutionException jobFailure,
      int maxAttempts,
      long initialRetryDelayMillis)
      throws ExecutionException {
    try {
      emitTerminalWithRetry(failureEmission, maxAttempts, initialRetryDelayMillis);
    } catch (RuntimeException terminalFailure) {
      jobFailure.addSuppressed(terminalFailure);
    }
    throw jobFailure;
  }

  static void validateCompileOnly(SqlScript script) {
    for (String statement : script.executeImmediately()) {
      String normalized = statement.stripLeading().toUpperCase(java.util.Locale.ROOT);
      if (!(normalized.startsWith("CREATE CATALOG ")
          || normalized.startsWith("USE ")
          || normalized.startsWith("CREATE TEMPORARY TABLE ")
          || normalized.startsWith("CREATE TEMPORARY VIEW ")
          || isBatchRuntimeMode(statement))) {
        throw new IllegalArgumentException(
            "--compile-only only allows catalog setup, USE, temporary objects, and batch runtime-mode configuration");
      }
    }
  }

  static boolean isBatch(SqlScript script) {
    String runtimeMode = null;
    for (String statement : script.executeImmediately()) {
      Matcher matcher = RUNTIME_MODE.matcher(statement.trim());
      if (matcher.matches()) {
        runtimeMode = matcher.group(1);
      }
    }
    return "batch".equalsIgnoreCase(runtimeMode);
  }

  static boolean isContinuous(SqlScript script) {
    boolean hasMySqlCdcSource =
        script.tempTables().values().stream()
            .anyMatch(table -> "mysql-cdc".equalsIgnoreCase(table.options().get("connector")));
    return !isBatch(script) || hasMySqlCdcSource;
  }

  private static boolean isBatchRuntimeMode(String statement) {
    Matcher matcher = RUNTIME_MODE.matcher(statement.trim());
    return matcher.matches() && "batch".equalsIgnoreCase(matcher.group(1));
  }

  private static SqlSecretRenderer.Renderer secretRenderer(Map<String, String> options)
      throws Exception {
    String secretsFile = options.get("secrets-file");
    return secretsFile == null
        ? SqlSecretRenderer.empty()
        : SqlSecretRenderer.from(Path.of(secretsFile));
  }

  private static String render(String sql, SqlSecretRenderer.Renderer renderer) {
    return renderer.render(sql);
  }

  private static String readSql(String path) throws Exception {
    return Files.readString(Path.of(path), StandardCharsets.UTF_8);
  }

  private static void executeSetup(StreamTableEnvironment tEnv, String rawSql) {
    SqlScript setup = SqlScriptParser.parse(rawSql);
    if (!setup.insertStatements().isEmpty()) {
      throw new IllegalArgumentException("Bootstrap SQL must not contain INSERT statements");
    }
    for (String statement : setup.executeImmediately()) {
      executeSetupStatement(tEnv, statement);
    }
  }

  private static void executeSetupStatement(StreamTableEnvironment tEnv, String statement) {
    Matcher setOption = SET_OPTION.matcher(statement.trim());
    if (setOption.matches()) {
      if ("execution.runtime-mode".equalsIgnoreCase(setOption.group(1))) {
        return;
      }
      tEnv.getConfig().getConfiguration().setString(setOption.group(1), setOption.group(2));
      return;
    }
    tEnv.executeSql(statement);
  }

  private static String readToken(Path tokenFile) throws Exception {
    if (!Files.isRegularFile(tokenFile, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalArgumentException("Auth token file must be a regular file");
    }
    SqlSecretRenderer.validateOwnerOnlyPermissions(tokenFile);
    String token = Files.readString(tokenFile, StandardCharsets.UTF_8).strip();
    if (token.isEmpty() || token.indexOf('\0') >= 0 || token.contains("\n") || token.contains("\r")) {
      throw new IllegalArgumentException("Auth token file contains an invalid token");
    }
    return token;
  }

  static Map<String, String> parseArgs(String[] args) {
    Map<String, String> options = new HashMap<>();
    for (int i = 0; i < args.length; i++) {
      String arg = args[i];
      if ("--compile-only".equals(arg) || "--allow-continuous-job".equals(arg)) {
        options.put(arg.substring(2), "true");
        continue;
      }
      if (!arg.startsWith("--") || i + 1 >= args.length) {
        throw new IllegalArgumentException("Unexpected argument: " + arg);
      }
      options.put(arg.substring(2), args[++i]);
    }
    return options;
  }

  private static String require(Map<String, String> options, String key) {
    String value = options.get(key);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing required --" + key);
    }
    return value;
  }
}
