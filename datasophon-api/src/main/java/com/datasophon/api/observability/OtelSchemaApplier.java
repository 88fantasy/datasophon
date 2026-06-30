/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.datasophon.api.observability;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;

import cn.hutool.core.io.IoUtil;

/** 把自管 otel schema 幂等应用到 Doris（MySQL 协议）。运行期需真实 Doris 连接。 */
public final class OtelSchemaApplier {
    
    private static final Logger log = LoggerFactory.getLogger(OtelSchemaApplier.class);
    
    private OtelSchemaApplier() {
    }
    
    /**
     * 按 DDL_RESOURCES 顺序逐语句执行。
     *
     * <p>库/表/视图幂等（IF NOT EXISTS / DROP+CREATE）；唯 traces_graph_job 的 CREATE JOB 无幂等语法
     * （Doris 不支持 IF NOT EXISTS、DROP JOB 不支持 IF EXISTS），重复 apply 时忽略该作业的
     * already-exists 错误；其他异常仍向上抛出。
     */
    public static void apply(JdbcClient doris, OtelCredentials credentials) {
        for (String res : OtelSchema.DDL_RESOURCES) {
            String sql = renderSql(readResource(res), credentials);
            for (String stmt : splitStatements(sql)) {
                executeStatement(doris, stmt);
            }
            log.info("otel schema applied: {}", res);
        }
    }
    
    static String renderSql(String sql, OtelCredentials credentials) {
        return sql.replace("CHANGE_ME_AT_A3_COLLECTOR", credentials.collectorPassword())
                .replace("CHANGE_ME_AT_A3_READER", credentials.readerPassword());
    }
    
    static void executeStatement(JdbcClient doris, String statement) {
        try {
            doris.sql(statement).update();
        } catch (DataAccessException e) {
            if (statement.stripLeading().startsWith("CREATE JOB") && isAlreadyExists(e)) {
                log.info("otel traces graph job already exists, skip create");
                return;
            }
            throw e;
        }
    }
    
    private static boolean isAlreadyExists(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(java.util.Locale.ROOT);
                if (normalized.contains("already exist") || normalized.contains("has been created")) {
                    return true;
                }
            }
        }
        return false;
    }
    
    static List<String> splitStatements(String sql) {
        List<String> out = new ArrayList<>();
        for (String raw : sql.split(";")) {
            String s = stripComments(raw).trim();
            if (!s.isEmpty()) {
                out.add(s);
            }
        }
        return out;
    }
    
    private static String stripComments(String s) {
        StringBuilder sb = new StringBuilder();
        for (String line : s.split("\n")) {
            String t = line.stripLeading();
            if (!t.startsWith("--")) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }
    
    private static String readResource(String res) {
        var in = OtelSchemaApplier.class.getClassLoader().getResourceAsStream(res);
        if (in == null) {
            throw new IllegalStateException("DDL 资源缺失: " + res);
        }
        return IoUtil.read(in, StandardCharsets.UTF_8);
    }
}
