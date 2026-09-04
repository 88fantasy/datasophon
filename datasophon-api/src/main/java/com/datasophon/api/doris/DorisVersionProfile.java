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

package com.datasophon.api.doris;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按 Doris 大版本区分的活动任务取数档位。
 *
 * <p>三张 {@code information_schema} 表的列在大版本之间既有增删、又有命名风格切换，
 * 因此一套固定 SQL 跨版本必然失败。实测结论（2026-09-03，两个环境各自 {@code DESC} 得到）：
 *
 * <table border="1">
 * <caption>列差异</caption>
 * <tr><th>表</th><th>3.x（远端 K8s 存算分离，doris-3.0.8-rc01 Cloud Mode）</th>
 *     <th>4.x（ddh-01，doris-4.1.3-rc02）</th></tr>
 * <tr><td>active_queries</td><td>10 列，<b>无 USER</b></td><td>11 列，有 USER</td></tr>
 * <tr><td>backend_active_tasks</td><td>12 列，<b>无 WORKLOAD_GROUP_ID、无 SPILL_*</b></td>
 *     <td>15 列</td></tr>
 * <tr><td>processlist</td><td>大写下划线 {@code QUERY_ID/HOST/USER}</td>
 *     <td>驼峰 {@code QueryId/Host/User}</td></tr>
 * <tr><td>workload_groups</td><td colspan="2">{@code ID/NAME} 两版一致，故不在本枚举内区分</td></tr>
 * </table>
 *
 * <p>设计要点：<b>每个档位的 SELECT 负责把列名别名归一</b>成同一套 key（见 3.x 的
 * {@code QUERY_ID AS QueryId}），因此下游合并、聚合、筛选逻辑完全不需要知道版本；
 * 缺失的列直接不出现在 SELECT 里，读取时取到 {@code null}，并由 {@link #unsupportedFields()}
 * 显式向前端声明「本版本没有这个字段」，而不是静默留空。
 */
public enum DorisVersionProfile {

    /**
     * 2.x —— 预留档位，暂不支持。
     *
     * <p>不在本枚举里给 SQL：2.x 的 {@code backend_active_tasks} 与 {@code active_queries}
     * 列集合尚未实测，凭文档猜列会重演本次「SQL 报错被兜成 502」的故障。将来支持时，
     * 按 3.x/4.x 同样的方式先 {@code DESC} 实测、再在此填入列清单并把 supported 改为 true。
     */
    V2(false, List.of(), List.of(), List.of(), List.of()),

    /** 3.x —— 远端 K8s 存算分离环境实测（doris-3.0.8-rc01 Cloud Mode）。 */
    V3(true,
            List.of("QUERY_ID", "QUERY_START_TIME", "QUERY_TIME_MS", "WORKLOAD_GROUP_ID",
                    "FRONTEND_INSTANCE", "QUEUE_START_TIME", "QUEUE_END_TIME", "QUERY_STATUS", "`SQL`"),
            List.of("QUERY_ID", "BE_ID", "FE_HOST", "QUERY_TYPE",
                    "TASK_TIME_MS", "TASK_CPU_TIME_MS", "SCAN_ROWS", "SCAN_BYTES",
                    "BE_PEAK_MEMORY_BYTES", "CURRENT_USED_MEMORY_BYTES",
                    "SHUFFLE_SEND_BYTES", "SHUFFLE_SEND_ROWS"),
            List.of("QUERY_ID AS QueryId", "HOST AS Host", "`USER` AS `User`"),
            List.of("spillBytes", "loadWorkloadGroup")),

    /** 4.x 及更高 —— ddh-01 物理集群实测（doris-4.1.3-rc02），列最全。 */
    V4(true,
            List.of("QUERY_ID", "QUERY_START_TIME", "QUERY_TIME_MS", "WORKLOAD_GROUP_ID",
                    "FRONTEND_INSTANCE", "QUEUE_START_TIME", "QUEUE_END_TIME", "QUERY_STATUS",
                    "`USER`", "`SQL`"),
            List.of("QUERY_ID", "BE_ID", "FE_HOST", "WORKLOAD_GROUP_ID", "QUERY_TYPE",
                    "TASK_TIME_MS", "TASK_CPU_TIME_MS", "SCAN_ROWS", "SCAN_BYTES",
                    "BE_PEAK_MEMORY_BYTES", "CURRENT_USED_MEMORY_BYTES",
                    "SHUFFLE_SEND_BYTES", "SHUFFLE_SEND_ROWS",
                    "SPILL_WRITE_BYTES_TO_LOCAL_STORAGE", "SPILL_READ_BYTES_FROM_LOCAL_STORAGE"),
            List.of("QueryId", "Host", "`User`"),
            List.of());

    /** 单表单次取数上限；超过即认为数据源侧已被截断。 */
    public static final int SOURCE_LIMIT = 20_000;

    /**
     * 版本号来自 {@code SELECT @@version_comment}（形如
     * {@code Doris version doris-3.0.8-rc01-09b0cc49a6 (Cloud Mode)}）。
     * <b>不能用 {@code version()}</b>——它为 MySQL 协议兼容恒返回 {@code 5.7.99}。
     */
    private static final Pattern MAJOR_VERSION =
            Pattern.compile("doris[- ](?:version[- ])?(\\d+)\\.", Pattern.CASE_INSENSITIVE);

    private final boolean supported;
    private final String activeQueriesSql;
    private final String backendActiveTasksSql;
    private final String activeQueriesByIdSql;
    private final String backendActiveTasksByIdSql;
    private final String processlistSql;
    private final List<String> unsupportedFields;

    DorisVersionProfile(boolean supported, List<String> activeQueriesColumns,
                        List<String> backendActiveTasksColumns, List<String> processlistColumns,
                        List<String> unsupportedFields) {
        this.supported = supported;
        this.activeQueriesSql = select(activeQueriesColumns, "active_queries", null);
        this.backendActiveTasksSql = select(backendActiveTasksColumns, "backend_active_tasks", null);
        // 详情路径把 taskId 下推到这两张表：它们是唯一会随负载涨到 SOURCE_LIMIT 的来源。
        // 过滤列固定用 QUERY_ID —— 3.x/4.x 两个档位都声明了它，故不引入版本分叉。
        this.activeQueriesByIdSql = select(activeQueriesColumns, "active_queries", "QUERY_ID = ?");
        this.backendActiveTasksByIdSql =
                select(backendActiveTasksColumns, "backend_active_tasks", "QUERY_ID = ?");
        this.processlistSql = select(processlistColumns, "processlist", "Command = 'Query'");
        this.unsupportedFields = List.copyOf(unsupportedFields);
    }

    /** 未识别的版本串按最新已知档位尝试，不直接判死——新版本通常是加列而非删列。 */
    public static DorisVersionProfile of(String versionComment) {
        Matcher matcher = MAJOR_VERSION.matcher(versionComment == null ? "" : versionComment);
        if (!matcher.find()) {
            return V4;
        }
        return switch (Integer.parseInt(matcher.group(1))) {
            case 2 -> V2;
            case 3 -> V3;
            default -> V4;
        };
    }

    /** false 表示本版本整体不支持活动任务查询，调用方应给出「版本不支持」而不是连接失败。 */
    public boolean supported() {
        return supported;
    }

    public String activeQueriesSql() {
        return activeQueriesSql;
    }

    public String backendActiveTasksSql() {
        return backendActiveTasksSql;
    }

    /** 详情路径用：同 {@link #activeQueriesSql()} 的列集，附 {@code QUERY_ID = ?} 占位符。 */
    public String activeQueriesByIdSql() {
        return activeQueriesByIdSql;
    }

    /** 详情路径用：同 {@link #backendActiveTasksSql()} 的列集，附 {@code QUERY_ID = ?} 占位符。 */
    public String backendActiveTasksByIdSql() {
        return backendActiveTasksByIdSql;
    }

    public String processlistSql() {
        return processlistSql;
    }

    /** 本版本确定拿不到的字段名，交前端明示「该版本不支持」，避免被当成空值缺陷。 */
    public List<String> unsupportedFields() {
        return unsupportedFields;
    }

    private static String select(List<String> columns, String table, String where) {
        if (columns.isEmpty()) {
            return null;
        }
        return "SELECT " + String.join(", ", columns)
                + " FROM information_schema." + table
                + (where == null ? "" : " WHERE " + where)
                + " LIMIT " + SOURCE_LIMIT;
    }
}
