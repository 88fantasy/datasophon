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

import java.util.List;
import java.util.Set;

/** otel Doris schema 的版本与期望对象集合(单一真相,契约测试与应用器共用)。 */
public final class OtelSchema {

    private OtelSchema() {
    }

    public static final String VERSION = "v1";

    /**
     * dorisexporter v0.156.0 Stream Load 目标基表;缺一张对应信号写不进。
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
