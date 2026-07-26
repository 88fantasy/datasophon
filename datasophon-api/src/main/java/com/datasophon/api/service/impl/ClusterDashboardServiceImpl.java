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

package com.datasophon.api.service.impl;

import com.datasophon.api.dto.v2.ClusterDashboardResponse;
import com.datasophon.api.service.ClusterDashboardService;
import com.datasophon.common.Constants;
import com.datasophon.dao.entity.ClusterAlertHistory;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceInstanceEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.AlertLevel;
import com.datasophon.dao.enums.ServiceRoleState;
import com.datasophon.dao.mapper.ClusterAlertHistoryMapper;
import com.datasophon.dao.mapper.ClusterHostMapper;
import com.datasophon.dao.mapper.ClusterInfoMapper;
import com.datasophon.dao.mapper.ClusterServiceInstanceMapper;
import com.datasophon.dao.mapper.ClusterServiceRoleInstanceMapper;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

/**
 * 集群总览看板聚合查询实现。
 *
 * <p>全部走 MyBatis-Plus {@code QueryWrapper}，不新增 XML。服务健康度与告警趋势按
 * {@code GROUP BY} 一次查询取全量分组结果，在内存里按集群下的服务/日期归并，
 * 避免对每个服务/每一天单独发一次查询（参见 {@code ClusterServiceInstanceServiceImpl}
 * 现有的逐服务 {@code selectCount} 写法，本类不沿用该模式）。
 */
@Service
public class ClusterDashboardServiceImpl implements ClusterDashboardService {

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    @Autowired
    private ClusterHostMapper hostMapper;

    @Autowired
    private ClusterServiceInstanceMapper serviceInstanceMapper;

    @Autowired
    private ClusterServiceRoleInstanceMapper roleInstanceMapper;

    @Autowired
    private ClusterAlertHistoryMapper alertHistoryMapper;

    @Autowired
    private ClusterInfoMapper clusterInfoMapper;

    @Override
    public ClusterDashboardResponse getDashboard(Integer clusterId) {
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now();
        Date todayStart = Date.from(today.atStartOfDay(zone).toInstant());
        Date yesterdayStart = Date.from(today.minusDays(1).atStartOfDay(zone).toInstant());
        Date sevenDaysAgoStart = Date.from(today.minusDays(6).atStartOfDay(zone).toInstant());

        return ClusterDashboardResponse.builder()
                .stats(buildStats(clusterId, todayStart, yesterdayStart))
                .alertTrend(buildAlertTrend(clusterId, sevenDaysAgoStart, today))
                .serviceHealth(buildServiceHealth(clusterId))
                .profile(buildProfile(clusterId))
                .build();
    }

    // 包私有：便于单测直接调用单个聚合步骤，不用为整个 getDashboard() 按调用顺序堆叠 mock 桩。
    ClusterDashboardResponse.Stats buildStats(Integer clusterId, Date todayStart, Date yesterdayStart) {
        long hostTotal = hostMapper.selectCount(new QueryWrapper<ClusterHostDO>()
                .eq(Constants.CLUSTER_ID, clusterId));
        long hostDelta = hostMapper.selectCount(new QueryWrapper<ClusterHostDO>()
                .eq(Constants.CLUSTER_ID, clusterId)
                .ge(Constants.CREATE_TIME, todayStart));

        long serviceTotal = serviceInstanceMapper.selectCount(new QueryWrapper<ClusterServiceInstanceEntity>()
                .eq(Constants.CLUSTER_ID, clusterId));
        long serviceDelta = serviceInstanceMapper.selectCount(new QueryWrapper<ClusterServiceInstanceEntity>()
                .eq(Constants.CLUSTER_ID, clusterId)
                .ge(Constants.CREATE_TIME, todayStart));

        long alertTotal = alertHistoryMapper.selectCount(new QueryWrapper<ClusterAlertHistory>()
                .eq(Constants.CLUSTER_ID, clusterId)
                .eq(Constants.IS_ENABLED, 1));
        long criticalAlertTotal = alertHistoryMapper.selectCount(new QueryWrapper<ClusterAlertHistory>()
                .eq(Constants.CLUSTER_ID, clusterId)
                .eq(Constants.IS_ENABLED, 1)
                .eq("alert_level", AlertLevel.EXCEPTION));

        long todayAlerts = alertHistoryMapper.selectCount(new QueryWrapper<ClusterAlertHistory>()
                .eq(Constants.CLUSTER_ID, clusterId)
                .ge(Constants.CREATE_TIME, todayStart));
        long yesterdayAlerts = alertHistoryMapper.selectCount(new QueryWrapper<ClusterAlertHistory>()
                .eq(Constants.CLUSTER_ID, clusterId)
                .ge(Constants.CREATE_TIME, yesterdayStart)
                .lt(Constants.CREATE_TIME, todayStart));
        long todayCritical = alertHistoryMapper.selectCount(new QueryWrapper<ClusterAlertHistory>()
                .eq(Constants.CLUSTER_ID, clusterId)
                .eq("alert_level", AlertLevel.EXCEPTION)
                .ge(Constants.CREATE_TIME, todayStart));
        long yesterdayCritical = alertHistoryMapper.selectCount(new QueryWrapper<ClusterAlertHistory>()
                .eq(Constants.CLUSTER_ID, clusterId)
                .eq("alert_level", AlertLevel.EXCEPTION)
                .ge(Constants.CREATE_TIME, yesterdayStart)
                .lt(Constants.CREATE_TIME, todayStart));

        return ClusterDashboardResponse.Stats.builder()
                .hostTotal(hostTotal).hostDelta(hostDelta)
                .serviceTotal(serviceTotal).serviceDelta(serviceDelta)
                .alertTotal(alertTotal).alertDelta(todayAlerts - yesterdayAlerts)
                .criticalAlertTotal(criticalAlertTotal).criticalAlertDelta(todayCritical - yesterdayCritical)
                .build();
    }

    List<ClusterDashboardResponse.AlertTrendPoint> buildAlertTrend(Integer clusterId,
                                                                   Date sevenDaysAgoStart,
                                                                   LocalDate today) {
        List<Map<String, Object>> rows = alertHistoryMapper.selectMaps(new QueryWrapper<ClusterAlertHistory>()
                .select("DATE(create_time) AS day", "alert_level AS lvl", "COUNT(1) AS cnt")
                .eq(Constants.CLUSTER_ID, clusterId)
                .ge(Constants.CREATE_TIME, sevenDaysAgoStart)
                .groupBy("DATE(create_time)", "alert_level"));

        // [0]=warning [1]=exception
        Map<String, long[]> countsByDay = new HashMap<>();
        for (Map<String, Object> row : rows) {
            String day = String.valueOf(row.get("day"));
            int level = ((Number) row.get("lvl")).intValue();
            long cnt = ((Number) row.get("cnt")).longValue();
            long[] counts = countsByDay.computeIfAbsent(day, key -> new long[2]);
            if (level == AlertLevel.EXCEPTION.getValue()) {
                counts[1] += cnt;
            } else {
                counts[0] += cnt;
            }
        }

        List<ClusterDashboardResponse.AlertTrendPoint> trend = new ArrayList<>();
        LocalDate cursor = today.minusDays(6);
        for (int i = 0; i < 7; i++) {
            String day = cursor.format(DAY_FORMAT);
            long[] counts = countsByDay.getOrDefault(day, new long[2]);
            trend.add(ClusterDashboardResponse.AlertTrendPoint.builder()
                    .day(day).warning(counts[0]).exception(counts[1]).build());
            cursor = cursor.plusDays(1);
        }
        return trend;
    }

    List<ClusterDashboardResponse.ServiceHealth> buildServiceHealth(Integer clusterId) {
        List<ClusterServiceInstanceEntity> services = serviceInstanceMapper.selectList(
                new QueryWrapper<ClusterServiceInstanceEntity>().eq(Constants.CLUSTER_ID, clusterId));
        if (services.isEmpty()) {
            return List.of();
        }

        List<Map<String, Object>> roleRows = roleInstanceMapper.selectMaps(
                new QueryWrapper<ClusterServiceRoleInstanceEntity>()
                        .select("service_id", "service_role_state AS state", "COUNT(1) AS cnt")
                        .eq(Constants.CLUSTER_ID, clusterId)
                        .groupBy("service_id", "service_role_state"));
        Map<Integer, Map<Integer, Long>> roleCountByService = new HashMap<>();
        for (Map<String, Object> row : roleRows) {
            int serviceId = ((Number) row.get("service_id")).intValue();
            int state = ((Number) row.get("state")).intValue();
            long cnt = ((Number) row.get("cnt")).longValue();
            roleCountByService.computeIfAbsent(serviceId, key -> new HashMap<>()).put(state, cnt);
        }

        List<Map<String, Object>> alertRows = alertHistoryMapper.selectMaps(new QueryWrapper<ClusterAlertHistory>()
                .select(Constants.SERVICE_INSTANCE_ID, "COUNT(1) AS cnt")
                .eq(Constants.CLUSTER_ID, clusterId)
                .eq(Constants.IS_ENABLED, 1)
                .groupBy(Constants.SERVICE_INSTANCE_ID));
        Map<Integer, Long> alertCountByService = new HashMap<>();
        for (Map<String, Object> row : alertRows) {
            Object serviceInstanceId = row.get(Constants.SERVICE_INSTANCE_ID);
            if (serviceInstanceId != null) {
                alertCountByService.put(((Number) serviceInstanceId).intValue(),
                        ((Number) row.get("cnt")).longValue());
            }
        }

        List<ClusterDashboardResponse.ServiceHealth> health = new ArrayList<>();
        for (ClusterServiceInstanceEntity service : services) {
            Map<Integer, Long> stateCounts = roleCountByService.getOrDefault(service.getId(), Map.of());
            long totalRoles = stateCounts.values().stream().mapToLong(Long::longValue).sum();
            long runningRoles = stateCounts.getOrDefault(ServiceRoleState.RUNNING.getValue(), 0L);
            Double healthPercent = totalRoles == 0 ? null : runningRoles * 100.0 / totalRoles;
            health.add(ClusterDashboardResponse.ServiceHealth.builder()
                    .serviceName(service.getServiceName())
                    .label(service.getLabel())
                    .healthPercent(healthPercent)
                    .runningRoles((int) runningRoles)
                    .abnormalRoles((int) (totalRoles - runningRoles))
                    .totalRoles((int) totalRoles)
                    .alertNum(alertCountByService.getOrDefault(service.getId(), 0L).intValue())
                    .serviceState(service.getServiceState())
                    .build());
        }

        health.sort(Comparator.comparing(ClusterDashboardResponse.ServiceHealth::getHealthPercent,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return health.size() > 5 ? health.subList(0, 5) : health;
    }

    ClusterDashboardResponse.ClusterProfile buildProfile(Integer clusterId) {
        ClusterInfoEntity info = clusterInfoMapper.selectById(clusterId);

        List<Map<String, Object>> rows = hostMapper.selectMaps(new QueryWrapper<ClusterHostDO>()
                .select("COUNT(1) AS node_count", "SUM(core_num) AS total_cores",
                        "SUM(total_mem) AS total_mem", "SUM(total_disk) AS total_disk")
                .eq(Constants.CLUSTER_ID, clusterId));
        Map<String, Object> agg = rows.isEmpty() ? Map.of() : rows.get(0);

        List<Object> architectures = hostMapper.selectObjs(new QueryWrapper<ClusterHostDO>()
                .select("DISTINCT cpu_architecture")
                .eq(Constants.CLUSTER_ID, clusterId)
                .isNotNull("cpu_architecture"));

        return ClusterDashboardResponse.ClusterProfile.builder()
                .clusterName(info == null ? null : info.getClusterName())
                .clusterFrame(info == null ? null : info.getClusterFrame())
                .frameVersion(info == null ? null : info.getFrameVersion())
                .clusterState(info == null ? null : info.getClusterState())
                .createTime(info == null ? null : info.getCreateTime())
                .nodeCount((int) longValue(agg.get("node_count")))
                .totalCores(longValue(agg.get("total_cores")))
                .totalMemGb(longValue(agg.get("total_mem")))
                .totalDiskGb(longValue(agg.get("total_disk")))
                .cpuArchitectures(architectures.stream().map(String::valueOf).toList())
                .build();
    }

    private static long longValue(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }
}
