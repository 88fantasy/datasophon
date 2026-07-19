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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class OtelSchemaContractTest {

    // -----------------------------------------------------------------------
    // Task 2: 已有测试(不得删除)
    // -----------------------------------------------------------------------

    @Test
    void ddl_resources_are_loadable_and_nonempty() {
        for (String res : OtelSchema.DDL_RESOURCES) {
            assertTrue(Files.exists(ddlSqlPath(res)), "DDL 资源缺失: " + res);
        }
    }

    @Test
    void applier_splits_statements() {
        String sql = "CREATE DATABASE IF NOT EXISTS otel;\nCREATE TABLE otel.a(x INT);\n";
        var stmts = OtelSchemaApplier.splitStatements(sql);
        assertFalse(stmts.isEmpty());
        assertTrue(stmts.size() >= 2);
    }

    // -----------------------------------------------------------------------
    // Task 3: 契约测试(守 F4/§5.8 漂移)
    // -----------------------------------------------------------------------

    /**
     * 从 V1__otel_tables.sql 正则提取所有 CREATE TABLE [IF NOT EXISTS] otel.&lt;name&gt;，
     * 断言 OtelSchema.EXPECTED_TABLES 中每一张表都被 DDL 覆盖。漂移即失败。
     */
    @Test
    void tables_sql_creates_exactly_the_expected_exporter_tables() {
        String sql =
                readDdlSql("sql/V1__otel_tables.sql").toLowerCase(java.util.Locale.ROOT);
        Matcher m =
                Pattern.compile(
                        "create\\s+table\\s+(if\\s+not\\s+exists\\s+)?otel\\.([a-z0-9_]+)")
                        .matcher(sql);
        Set<String> declared = new HashSet<>();
        while (m.find()) {
            declared.add(m.group(2));
        }
        // 期望表集必须被 DDL 全覆盖(漂移即失败)
        for (String t : OtelSchema.EXPECTED_TABLES) {
            assertTrue(declared.contains(t), "DDL 缺少 exporter 目标表: " + t);
        }
    }

    /**
     * 采集账号 otel_collector 数据权限必须精确等于 {LOAD_PRIV}，看板账号 otel_reader 精确等于 {SELECT_PRIV}。
     *
     * <p>白名单精确相等优于黑名单排除：黑名单只能枚举已知危险词，会漏过 legacy `GRANT ALL ON otel.*`、
     * 组合授权 `GRANT LOAD_PRIV, DROP_PRIV ...` 以及 ALTER_PRIV/ADMIN_PRIV/GRANT_PRIV 等越权写法；
     * 精确相等一次性拒绝任何非预期权限。(Codex 对抗审查 CHECK 3 整改)
     */
    @Test
    void accounts_hold_exactly_the_least_privilege() {
        String db =
                readDdlSql("sql/V1__otel_database.sql")
                        .toUpperCase(java.util.Locale.ROOT);

        // 自证：解析器能看见越权写法（否则下面的精确相等是空洞的）
        assertEquals(
                Set.of("ALL"),
                dataPrivilegesFor("GRANT ALL ON OTEL.* TO 'OTEL_COLLECTOR';", "OTEL_COLLECTOR"),
                "解析器必须能看见 legacy GRANT ALL（自证）");
        assertEquals(
                Set.of("LOAD_PRIV", "DROP_PRIV"),
                dataPrivilegesFor(
                        "GRANT LOAD_PRIV, DROP_PRIV ON OTEL.* TO 'OTEL_COLLECTOR';", "OTEL_COLLECTOR"),
                "解析器必须能看见组合授权里的越权项（自证）");

        // 真实 schema：数据权限精确白名单
        assertEquals(
                Set.of("LOAD_PRIV"),
                dataPrivilegesFor(db, "OTEL_COLLECTOR"),
                "采集账号 otel.* 数据权限必须精确为 {LOAD_PRIV}");
        assertEquals(
                Set.of("SELECT_PRIV"),
                dataPrivilegesFor(db, "OTEL_READER"),
                "看板账号 otel.* 数据权限必须精确为 {SELECT_PRIV}");
    }

    /**
     * 独立资源组 otel_wg 必须真正绑定到两个账号，否则资源隔离形同虚设。
     *
     * <p>仅 CREATE WORKLOAD GROUP 不够：用户不获 USAGE_PRIV 且不设 default_workload_group 时仍走 normal 组。
     * (Codex 对抗审查 CHECK 4 整改；语法据 Doris 官方文档：
     * GRANT USAGE_PRIV ON WORKLOAD GROUP / SET PROPERTY FOR ... 'default_workload_group')
     */
    @Test
    void accounts_are_bound_to_isolated_workload_group() {
        String db =
                readDdlSql("sql/V1__otel_database.sql")
                        .toUpperCase(java.util.Locale.ROOT);
        assertTrue(
                db.contains("CREATE WORKLOAD GROUP IF NOT EXISTS OTEL_WG"),
                "缺少独立资源组 otel_wg");
        for (String user : new String[]{"OTEL_COLLECTOR", "OTEL_READER"}) {
            assertTrue(
                    Pattern.compile(
                            "GRANT\\s+USAGE_PRIV\\s+ON\\s+WORKLOAD\\s+GROUP\\s+'OTEL_WG'\\s+TO\\s+'"
                                    + user + "'")
                            .matcher(db)
                            .find(),
                    user + " 未被授予 otel_wg 的 USAGE_PRIV");
            assertTrue(
                    Pattern.compile(
                            "SET\\s+PROPERTY\\s+FOR\\s+'" + user
                                    + "'\\s+'DEFAULT_WORKLOAD_GROUP'\\s*=\\s*'OTEL_WG'")
                            .matcher(db)
                            .find(),
                    user + " 未设 default_workload_group=otel_wg");
        }
    }

    /** 提取针对 user 在 otel.* 上授予的全部数据权限（合并所有 GRANT 的逗号分隔项，全大写）。 */
    private static Set<String> dataPrivilegesFor(String dbSql, String user) {
        Set<String> privs = new HashSet<>();
        Matcher m =
                Pattern.compile(
                        "GRANT\\s+(.+?)\\s+ON\\s+OTEL\\.\\*\\s+TO\\s+'" + user + "'",
                        Pattern.CASE_INSENSITIVE)
                        .matcher(dbSql);
        while (m.find()) {
            for (String p : m.group(1).split(",")) {
                String t = p.trim().toUpperCase(java.util.Locale.ROOT);
                if (!t.isEmpty()) {
                    privs.add(t);
                }
            }
        }
        return privs;
    }

    /** OtelSchema.VERSION 必须固定为 v1，防止版本漂移导致契约失效。 */
    @Test
    void schema_version_is_pinned() {
        assertEquals("v1", OtelSchema.VERSION);
    }

    /**
     * 锁定已知边界（Codex 对抗审查 CHECK 2-1）：views.sql 恰含 1 个 CREATE JOB（traces_graph_job）
     * 且当前无幂等语法——Doris 的 CREATE JOB 不支持 IF NOT EXISTS，重复 apply 会在该语句失败。
     *
     * <p>幂等容错需真实 Doris 错误行为验证，留待 A3（见 apply-verify.md）。A3 实现幂等后，
     * 本测试的 assertFalse 会主动失败，提示同步更新——把口头 TODO 变成 CI 守卫的待办。
     */
    @Test
    void traces_graph_job_is_the_single_known_non_idempotent_statement() {
        String views =
                readDdlSql("sql/V1__otel_views.sql")
                        .toUpperCase(java.util.Locale.ROOT);
        Matcher m = Pattern.compile("CREATE\\s+JOB").matcher(views);
        int count = 0;
        while (m.find()) {
            count++;
        }
        assertEquals(1, count, "views.sql 应恰含 1 个 CREATE JOB(traces_graph_job)");
        assertFalse(
                Pattern.compile("CREATE\\s+JOB\\s+IF\\s+NOT\\s+EXISTS").matcher(views).find(),
                "A2 已知边界:CREATE JOB 无 IF NOT EXISTS(Doris 不支持);A3 实现幂等后同步更新本测试");
    }

    // -----------------------------------------------------------------------
    // 私有助手
    // -----------------------------------------------------------------------

    /** {@code OtelSchema.DDL_RESOURCES} 中的相对路径对应本地 {@code package/raw/meta/datacluster-physical/DORIS/} 下的文件。 */
    private Path ddlSqlPath(String res) {
        return Path.of("..", "package", "raw", "meta", "datacluster-physical", OtelSchema.SERVICE_NAME, res);
    }

    private String readDdlSql(String res) {
        try {
            return Files.readString(ddlSqlPath(res));
        } catch (IOException e) {
            throw new IllegalStateException("DDL 资源缺失: " + res, e);
        }
    }
}
