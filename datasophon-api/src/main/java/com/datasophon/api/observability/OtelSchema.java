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

import java.util.List;
import java.util.Set;

/** otel Doris schema 的版本与期望对象集合(单一真相,契约测试与应用器共用)。 */
public final class OtelSchema {
    
    private OtelSchema() {
    }
    
    public static final String VERSION = "v1";
    
    /**
     * dorisexporter v0.154.0 Stream Load 目标基表;缺一张对应信号写不进。
     *
     * <p>表名以 V1__otel_tables.sql 中 CREATE TABLE otel.&lt;name&gt; 为准(8 张)。
     */
    public static final Set<String> EXPECTED_TABLES =
            Set.of(
                    "otel_logs",
                    "otel_metrics_gauge",
                    "otel_metrics_sum",
                    "otel_metrics_histogram",
                    "otel_metrics_exponential_histogram",
                    "otel_metrics_summary",
                    "otel_traces",
                    "otel_traces_graph");
    
    /**
     * 按依赖顺序的 DDL 资源(database → tables → views)。
     *
     * <p>契约:这些资源由 {@code OtelSchemaApplier.splitStatements} 按分号切分逐条执行,因此分号只能作语句分隔符
     * —— 字面量(列 DEFAULT、注释、PROPERTIES value)内不得含分号,否则会被截断成残缺 SQL。需要含分号字面量时,
     * 先把分割逻辑升级为引号感知再加资源。
     */
    public static final List<String> DDL_RESOURCES =
            List.of(
                    "observability/doris/V1__otel_database.sql",
                    "observability/doris/V1__otel_tables.sql",
                    "observability/doris/V1__otel_views.sql");
}
