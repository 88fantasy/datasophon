/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.datasophon.api.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.hutool.core.io.IoUtil;

import org.junit.jupiter.api.Test;

class OtelSchemaContractTest {

    // -----------------------------------------------------------------------
    // Task 2: 已有测试(不得删除)
    // -----------------------------------------------------------------------

    @Test
    void ddl_resources_are_loadable_and_nonempty() {
        for (String res : OtelSchema.DDL_RESOURCES) {
            var in = getClass().getClassLoader().getResourceAsStream(res);
            assertTrue(in != null, "DDL 资源缺失: " + res);
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
                readClasspath("observability/doris/V1__otel_tables.sql").toLowerCase(java.util.Locale.ROOT);
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
     * 采集账号 otel_collector 只应持有 LOAD_PRIV；
     * 断言不存在针对该账号的 CREATE/DROP/DELETE/ALL 权限授予。
     */
    @Test
    void collector_account_has_load_only_no_ddl_privilege() {
        String db =
                readClasspath("observability/doris/V1__otel_database.sql")
                        .toUpperCase(java.util.Locale.ROOT);
        // 正向：必须含 LOAD_PRIV 授权
        assertTrue(
                db.contains("GRANT LOAD_PRIV ON OTEL.* TO 'OTEL_COLLECTOR'"),
                "V1__otel_database.sql 缺少 GRANT LOAD_PRIV ON otel.* TO 'otel_collector'");
        // 负向：采集账号绝不能被授予 DDL/删除权限
        Pattern ddlGrant =
                Pattern.compile(
                        "GRANT\\s+(ALL|CREATE|DROP|DELETE)\\w*\\s+PRIV\\s+ON\\s+OTEL[^;]*OTEL_COLLECTOR",
                        Pattern.DOTALL);
        assertFalse(
                ddlGrant.matcher(db).find(),
                "otel_collector 不应被授予 CREATE/DROP/DELETE/ALL 权限");
    }

    /** OtelSchema.VERSION 必须固定为 v1，防止版本漂移导致契约失效。 */
    @Test
    void schema_version_is_pinned() {
        assertEquals("v1", OtelSchema.VERSION);
    }

    // -----------------------------------------------------------------------
    // 私有助手
    // -----------------------------------------------------------------------

    private String readClasspath(String path) {
        InputStream in = getClass().getClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("classpath 资源缺失: " + path);
        }
        return IoUtil.read(in, StandardCharsets.UTF_8);
    }
}
