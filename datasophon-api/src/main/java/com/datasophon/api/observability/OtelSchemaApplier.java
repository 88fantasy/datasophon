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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;

import cn.hutool.core.io.IoUtil;

/** 把自管 otel schema 幂等应用到 Doris（MySQL 协议）。运行期需真实 Doris 连接。 */
public final class OtelSchemaApplier {
    
    private static final Logger log = LoggerFactory.getLogger(OtelSchemaApplier.class);
    
    private OtelSchemaApplier() {
    }
    
    /** 按 DDL_RESOURCES 顺序逐语句执行；DDL 均为 IF NOT EXISTS，可重复应用。 */
    public static void apply(JdbcClient doris) {
        for (String res : OtelSchema.DDL_RESOURCES) {
            String sql = readResource(res);
            for (String stmt : splitStatements(sql)) {
                doris.sql(stmt).update();
            }
            log.info("otel schema applied: {}", res);
        }
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
