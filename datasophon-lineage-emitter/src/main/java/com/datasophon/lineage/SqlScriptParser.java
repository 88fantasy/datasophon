package com.datasophon.lineage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.datasophon.lineage.SqlScript.TempTableInfo;

/**
 * Splits one SQL file into statements Flink's Table API can consume one at a time. Handles the
 * two shapes actually used by this epic's jobs (see docs/lineage/sql/*.sql):
 *
 * <ul>
 *   <li>job2 (T9): a flat sequence of CREATE ...; statements followed by one INSERT ...;
 *   <li>job1 (T6): the same CREATE prefix, then {@code EXECUTE STATEMENT SET BEGIN INSERT ...;
 *       INSERT ...; ... END;} wrapping multiple sink INSERTs in one physical Flink job.
 * </ul>
 *
 * Not a general-purpose SQL parser — deliberately scoped to what this epic's generated SQL files
 * contain (see docs/lineage/sql/T8-改写对照清单.md and t6_mysql_cdc_to_paimon.sql).
 */
final class SqlScriptParser {

  private static final Pattern STATEMENT_SET_PREFIX =
      Pattern.compile(
          "^\\s*EXECUTE\\s+STATEMENT\\s+SET\\s+BEGIN\\s+", Pattern.CASE_INSENSITIVE);
  private static final Pattern CREATE_TEMP_TABLE =
      Pattern.compile(
          "^\\s*CREATE\\s+TEMPORARY\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?([A-Za-z_][A-Za-z0-9_]*)`?",
          Pattern.CASE_INSENSITIVE);
  private static final Pattern WITH_CLAUSE = Pattern.compile("\\bWITH\\s*\\(", Pattern.CASE_INSENSITIVE);
  private static final Pattern OPTION_ENTRY =
      Pattern.compile("'((?:[^'\\\\]|\\\\.)*)'\\s*=\\s*'((?:[^'\\\\]|\\\\.)*)'");

  private SqlScriptParser() {}

  static SqlScript parse(String rawSql) {
    List<String> statements = splitTopLevelStatements(rawSql);
    List<String> executeImmediately = new ArrayList<>();
    List<String> insertStatements = new ArrayList<>();
    Map<String, TempTableInfo> tempTables = new LinkedHashMap<>();

    for (String statement : statements) {
      String trimmed = statement.trim();
      if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("END")) {
        continue; // STATEMENT SET terminator, nothing to run
      }
      Matcher setPrefix = STATEMENT_SET_PREFIX.matcher(trimmed);
      if (setPrefix.find()) {
        trimmed = trimmed.substring(setPrefix.end()).trim();
      }
      if (trimmed.regionMatches(true, 0, "INSERT", 0, "INSERT".length())) {
        insertStatements.add(trimmed);
        continue;
      }
      executeImmediately.add(trimmed);
      Matcher tempTable = CREATE_TEMP_TABLE.matcher(trimmed);
      if (tempTable.find()) {
        String name = tempTable.group(1);
        tempTables.put(name, new TempTableInfo(name, parseWithOptions(trimmed)));
      }
    }
    return new SqlScript(executeImmediately, insertStatements, tempTables);
  }

  /** Splits on top-level `;` (outside single-quoted strings) and drops `--` line comments. */
  private static List<String> splitTopLevelStatements(String rawSql) {
    List<String> result = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean inSingleQuote = false;
    int i = 0;
    int len = rawSql.length();
    while (i < len) {
      char c = rawSql.charAt(i);
      if (!inSingleQuote && c == '-' && i + 1 < len && rawSql.charAt(i + 1) == '-') {
        int newline = rawSql.indexOf('\n', i);
        i = newline < 0 ? len : newline + 1;
        continue;
      }
      if (c == '\'') {
        inSingleQuote = !inSingleQuote;
        current.append(c);
      } else if (!inSingleQuote && c == ';') {
        result.add(current.toString());
        current.setLength(0);
      } else {
        current.append(c);
      }
      i++;
    }
    if (!current.toString().trim().isEmpty()) {
      result.add(current.toString());
    }
    return result;
  }

  /** Extracts the `'key' = 'value'` entries of a CREATE TEMPORARY TABLE's trailing WITH (...) clause. */
  private static Map<String, String> parseWithOptions(String createTableStatement) {
    Matcher withStart = WITH_CLAUSE.matcher(createTableStatement);
    if (!withStart.find()) {
      throw new IllegalArgumentException("CREATE TEMPORARY TABLE without a WITH (...) clause");
    }
    int bodyStart = withStart.end();
    int depth = 1;
    int idx = bodyStart;
    boolean inSingleQuote = false;
    while (idx < createTableStatement.length() && depth > 0) {
      char c = createTableStatement.charAt(idx);
      if (c == '\'') {
        inSingleQuote = !inSingleQuote;
      } else if (!inSingleQuote && c == '(') {
        depth++;
      } else if (!inSingleQuote && c == ')') {
        depth--;
      }
      idx++;
    }
    String body = createTableStatement.substring(bodyStart, idx - 1);
    Map<String, String> options = new LinkedHashMap<>();
    Matcher entry = OPTION_ENTRY.matcher(body);
    while (entry.find()) {
      options.put(entry.group(1), entry.group(2));
    }
    return options;
  }
}
