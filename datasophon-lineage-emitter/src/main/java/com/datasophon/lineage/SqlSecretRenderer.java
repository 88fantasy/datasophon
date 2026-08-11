package com.datasophon.lineage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SqlSecretRenderer {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Pattern PLACEHOLDER = Pattern.compile("__([A-Z][A-Z0-9_]*)__");

  private SqlSecretRenderer() {}

  static Renderer from(Path secretsFile) throws IOException {
    return new Renderer(readSecrets(secretsFile));
  }

  static Renderer empty() {
    return new Renderer(Map.of());
  }

  static final class Renderer {
    private final Map<String, String> secrets;
    private final Map<String, String> used = new LinkedHashMap<>();

    private Renderer(Map<String, String> secrets) {
      this.secrets = secrets;
    }

    String render(String sql) {
      Matcher placeholders = PLACEHOLDER.matcher(sql);
      StringBuilder rendered = new StringBuilder();
      int offset = 0;
      while (placeholders.find()) {
        String name = placeholders.group(1);
        String value = secrets.get(name);
        if (value == null) {
          throw new IllegalArgumentException("Missing secret for placeholder " + name);
        }
        if (!isInsideSingleQuotedLiteral(sql, placeholders.start())) {
          throw new IllegalArgumentException("Secret placeholder " + name + " must be inside a SQL string literal");
        }
        used.put(name, value);
        rendered.append(sql, offset, placeholders.start());
        rendered.append(value.replace("'", "''"));
        offset = placeholders.end();
      }
      rendered.append(sql, offset, sql.length());
      return rendered.toString();
    }

    void validateAllUsed() {
      if (used.size() != secrets.size()) {
        throw new IllegalArgumentException("Secrets file contains unused entries");
      }
    }
  }

  private static Map<String, String> readSecrets(Path secretsFile) throws IOException {
    if (!Files.isRegularFile(secretsFile, LinkOption.NOFOLLOW_LINKS)) {
      throw new IllegalArgumentException("Secrets file must be a regular file");
    }
    validatePermissions(secretsFile);
    Map<String, String> values =
        MAPPER.readValue(Files.readString(secretsFile), new TypeReference<Map<String, String>>() {});
    if (values == null || values.isEmpty()) {
      throw new IllegalArgumentException("Secrets file must contain a non-empty JSON object");
    }
    for (Map.Entry<String, String> entry : values.entrySet()) {
      // 反斜杠被排除：render() 只做 SQL-92 的 '' 转义，而这些 SQL 里的 connector 参数最终会
      // 到 MySQL，MySQL 默认没开 NO_BACKSLASH_ESCAPES，反斜杠在那边仍是转义符——值里带 \
      // 时两端语义不一致，转义就不完整。与其做一套依赖目标方言的转义，不如直接拒绝。
      if (!entry.getKey().matches("[A-Z][A-Z0-9_]*")
          || entry.getValue() == null
          || entry.getValue().isEmpty()
          || entry.getValue().indexOf('\0') >= 0
          || entry.getValue().indexOf('\\') >= 0
          || entry.getValue().contains("\n")
          || entry.getValue().contains("\r")) {
        throw new IllegalArgumentException("Secrets file contains an invalid entry");
      }
    }
    return values;
  }

  static void validateOwnerOnlyPermissions(Path file) throws IOException {
    try {
      Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
      Set<PosixFilePermission> forbidden = PosixFilePermissions.fromString("---rwxrwx");
      for (PosixFilePermission permission : forbidden) {
        if (permissions.contains(permission)) {
          throw new IllegalArgumentException("Secret file must not be readable by group or other users");
        }
      }
    } catch (UnsupportedOperationException ignored) {
    }
  }

  private static void validatePermissions(Path secretsFile) throws IOException {
    validateOwnerOnlyPermissions(secretsFile);
  }

  /**
   * 判断 {@code index} 处是否位于单引号字符串字面量内部。
   *
   * <p>必须跳过 SQL 注释：注释里出现奇数个撇号（英文缩写 "don't" 就够了）会翻转字面量状态，
   * 让后面真正的占位符被判反。判成"不在字面量内"只是误拒合法脚本，判成"在字面量内"则会把
   * 密钥拼到字面量之外——那是注入。
   */
  private static boolean isInsideSingleQuotedLiteral(String sql, int index) {
    boolean inLiteral = false;
    for (int i = 0; i < index; i++) {
      char ch = sql.charAt(i);
      if (!inLiteral && ch == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
        int lineEnd = sql.indexOf('\n', i);
        if (lineEnd < 0 || lineEnd >= index) {
          return false; // 占位符本身落在行注释里
        }
        i = lineEnd;
        continue;
      }
      if (!inLiteral && ch == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
        int blockEnd = sql.indexOf("*/", i + 2);
        if (blockEnd < 0 || blockEnd >= index) {
          return false; // 占位符本身落在块注释里
        }
        i = blockEnd + 1;
        continue;
      }
      if (ch != '\'') {
        continue;
      }
      if (inLiteral && i + 1 < index && sql.charAt(i + 1) == '\'') {
        i++;
      } else {
        inLiteral = !inLiteral;
      }
    }
    return inLiteral;
  }
}
