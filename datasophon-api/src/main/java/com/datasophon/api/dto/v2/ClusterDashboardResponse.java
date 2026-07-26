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

package com.datasophon.api.dto.v2;

import com.datasophon.dao.enums.ClusterState;
import com.datasophon.dao.enums.ServiceState;

import java.util.Date;
import java.util.List;

import lombok.Builder;
import lombok.Data;

/**
 * 集群总览看板聚合响应，对应集群菜单首位的「集群看板」页面。
 *
 * <p>时序图表（CPU/内存/磁盘/网络）不走本接口，由前端直接调用
 * {@code /v2/observability/otel/metrics/query_range} 取数；本接口只承载
 * DB 侧聚合的统计数字、告警趋势、服务健康度与集群概要。
 */
@Data
@Builder
public class ClusterDashboardResponse {

    private Stats stats;
    private List<AlertTrendPoint> alertTrend;
    private List<ServiceHealth> serviceHealth;
    private ClusterProfile profile;

    @Data
    @Builder
    public static class Stats {
        private long hostTotal;
        /** 今日新增主机数 − 昨日新增主机数，可为负。 */
        private long hostDelta;
        private long serviceTotal;
        /** 今日新增服务数 − 昨日新增服务数，可为负。 */
        private long serviceDelta;
        /** 未处理告警总数（{@code is_enabled=1}）。 */
        private long alertTotal;
        /** 今日新增告警数 − 昨日新增告警数，可为负。 */
        private long alertDelta;
        /** 未处理的严重告警（{@code AlertLevel.EXCEPTION}）总数。 */
        private long criticalAlertTotal;
        private long criticalAlertDelta;
    }

    /** 近 7 天告警趋势的一天；无告警的日期补零，不缺天。 */
    @Data
    @Builder
    public static class AlertTrendPoint {
        /** {@code yyyy-MM-dd}。 */
        private String day;
        private long warning;
        private long exception;
    }

    /** 按健康度升序排列的问题服务 TOP5（角色实例运行占比最低者优先）。 */
    @Data
    @Builder
    public static class ServiceHealth {
        private String serviceName;
        private String label;
        /** 运行角色数 / 总角色数 * 100；总角色数为 0（未装角色）时为 {@code null}。 */
        private Double healthPercent;
        private int runningRoles;
        private int abnormalRoles;
        private int totalRoles;
        private int alertNum;
        private ServiceState serviceState;
    }

    /** 集群概要：替代参考图「系统信息」面板（OS/内核/CPU 型号/运行时间本项目无数据源）。 */
    @Data
    @Builder
    public static class ClusterProfile {
        private String clusterName;
        private String clusterFrame;
        private String frameVersion;
        private ClusterState clusterState;
        private Date createTime;
        private int nodeCount;
        private long totalCores;
        /** 汇总自 {@code t_ddh_cluster_host.total_mem}（GiB，`HostCheckService` 落库时已换算）。 */
        private long totalMemGb;
        /** 汇总自 {@code t_ddh_cluster_host.total_disk}（GiB）。 */
        private long totalDiskGb;
        private List<String> cpuArchitectures;

        /** 本期无数据源，恒为 {@code null}，预留给后续主机系统信息采集。 */
        private String osName;
        private String kernelVersion;
        private Long uptimeSeconds;
    }
}
