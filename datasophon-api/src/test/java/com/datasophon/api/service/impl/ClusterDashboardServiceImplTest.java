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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.datasophon.api.dto.v2.ClusterDashboardResponse;
import com.datasophon.dao.entity.ClusterServiceInstanceEntity;
import com.datasophon.dao.enums.AlertLevel;
import com.datasophon.dao.enums.ServiceRoleState;
import com.datasophon.dao.enums.ServiceState;
import com.datasophon.dao.mapper.ClusterAlertHistoryMapper;
import com.datasophon.dao.mapper.ClusterHostMapper;
import com.datasophon.dao.mapper.ClusterInfoMapper;
import com.datasophon.dao.mapper.ClusterServiceInstanceMapper;
import com.datasophon.dao.mapper.ClusterServiceRoleInstanceMapper;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 覆盖 {@link ClusterDashboardServiceImpl} 的四个聚合步骤（{@code build*} 方法为包私有，
 * 便于逐个 mock 单一 mapper 而不用为整个 {@code getDashboard()} 按源码调用顺序堆叠桩）。
 */
@ExtendWith(MockitoExtension.class)
class ClusterDashboardServiceImplTest {

    @Mock
    private ClusterHostMapper hostMapper;

    @Mock
    private ClusterServiceInstanceMapper serviceInstanceMapper;

    @Mock
    private ClusterServiceRoleInstanceMapper roleInstanceMapper;

    @Mock
    private ClusterAlertHistoryMapper alertHistoryMapper;

    @Mock
    private ClusterInfoMapper clusterInfoMapper;

    @InjectMocks
    private ClusterDashboardServiceImpl service;

    @Test
    void buildStats_computesTotalsAndAllowsNegativeDelta() {
        Date todayStart = new Date();
        Date yesterdayStart = new Date(todayStart.getTime() - 86_400_000L);

        // 调用顺序见 buildStats 源码：hostTotal, todayHosts, yesterdayHosts
        when(hostMapper.selectCount(any())).thenReturn(5L, 2L, 5L);
        // serviceTotal, todayServices, yesterdayServices
        when(serviceInstanceMapper.selectCount(any())).thenReturn(10L, 4L, 1L);
        // alertTotal, criticalAlertTotal, todayAlerts, yesterdayAlerts, todayCritical, yesterdayCritical
        when(alertHistoryMapper.selectCount(any())).thenReturn(8L, 3L, 4L, 9L, 1L, 2L);

        ClusterDashboardResponse.Stats stats = service.buildStats(1, todayStart, yesterdayStart);

        assertThat(stats.getHostTotal()).isEqualTo(5);
        assertThat(stats.getHostDelta()).isEqualTo(-3);
        assertThat(stats.getServiceTotal()).isEqualTo(10);
        assertThat(stats.getServiceDelta()).isEqualTo(3);
        assertThat(stats.getAlertTotal()).isEqualTo(8);
        assertThat(stats.getCriticalAlertTotal()).isEqualTo(3);
        // todayAlerts(4) - yesterdayAlerts(9)，参考图允许出现 -5 这种负增量
        assertThat(stats.getAlertDelta()).isEqualTo(-5);
        assertThat(stats.getCriticalAlertDelta()).isEqualTo(-1);
    }

    @Test
    void buildAlertTrend_zeroFillsMissingDaysAndSplitsByLevel() {
        LocalDate today = LocalDate.of(2026, 7, 25);
        Map<String, Object> warnRow = Map.<String, Object>of(
                "day", "2026-07-25", "lvl", AlertLevel.WARN.getValue(), "cnt", 3L);
        Map<String, Object> exceptionRow = Map.<String, Object>of(
                "day", "2026-07-25", "lvl", AlertLevel.EXCEPTION.getValue(), "cnt", 2L);
        when(alertHistoryMapper.selectMaps(any())).thenReturn(List.of(warnRow, exceptionRow));

        List<ClusterDashboardResponse.AlertTrendPoint> trend =
                service.buildAlertTrend(1, new Date(), today);

        assertThat(trend).hasSize(7);
        assertThat(trend.get(0).getDay()).isEqualTo("2026-07-19");
        assertThat(trend.subList(0, 6)).allSatisfy(point -> {
            assertThat(point.getWarning()).isZero();
            assertThat(point.getException()).isZero();
        });
        ClusterDashboardResponse.AlertTrendPoint today0725 = trend.get(6);
        assertThat(today0725.getDay()).isEqualTo("2026-07-25");
        assertThat(today0725.getWarning()).isEqualTo(3);
        assertThat(today0725.getException()).isEqualTo(2);
    }

    @Test
    void buildServiceHealth_ranksProblemServicesFirstAndKeepsNullPercentLast() {
        ClusterServiceInstanceEntity healthy = new ClusterServiceInstanceEntity();
        healthy.setId(1);
        healthy.setServiceName("HDFS");
        healthy.setServiceState(ServiceState.RUNNING);
        ClusterServiceInstanceEntity degraded = new ClusterServiceInstanceEntity();
        degraded.setId(2);
        degraded.setServiceName("KAFKA");
        degraded.setServiceState(ServiceState.EXISTS_ALARM);
        ClusterServiceInstanceEntity notInstalled = new ClusterServiceInstanceEntity();
        notInstalled.setId(3);
        notInstalled.setServiceName("HIVE");
        notInstalled.setServiceState(ServiceState.WAIT_INSTALL);
        when(serviceInstanceMapper.selectList(any()))
                .thenReturn(List.of(healthy, degraded, notInstalled));

        // service 1：2 个角色全 RUNNING；service 2：1 RUNNING + 1 STOP；service 3：无角色行
        when(roleInstanceMapper.selectMaps(any())).thenReturn(List.of(
                Map.<String, Object>of("service_id", 1, "state", ServiceRoleState.RUNNING.getValue(), "cnt", 2L),
                Map.<String, Object>of("service_id", 2, "state", ServiceRoleState.RUNNING.getValue(), "cnt", 1L),
                Map.<String, Object>of("service_id", 2, "state", ServiceRoleState.STOP.getValue(), "cnt", 1L)));
        when(alertHistoryMapper.selectMaps(any())).thenReturn(
                List.of(Map.<String, Object>of("service_instance_id", 2, "cnt", 3L)));

        List<ClusterDashboardResponse.ServiceHealth> health = service.buildServiceHealth(1);

        assertThat(health).hasSize(3);
        assertThat(health.get(0).getServiceName()).isEqualTo("KAFKA");
        assertThat(health.get(0).getHealthPercent()).isEqualTo(50.0);
        assertThat(health.get(0).getAlertNum()).isEqualTo(3);
        assertThat(health.get(0).getRunningRoles()).isEqualTo(1);
        assertThat(health.get(0).getAbnormalRoles()).isEqualTo(1);
        assertThat(health.get(1).getServiceName()).isEqualTo("HDFS");
        assertThat(health.get(1).getHealthPercent()).isEqualTo(100.0);
        // 未装角色的服务健康度为 null（前端应显示为 "-"），排在最后而非最先
        assertThat(health.get(2).getServiceName()).isEqualTo("HIVE");
        assertThat(health.get(2).getHealthPercent()).isNull();
        assertThat(health.get(2).getTotalRoles()).isZero();
    }

    @Test
    void buildServiceHealth_returnsEmptyAndSkipsFurtherQueriesWhenClusterHasNoServices() {
        when(serviceInstanceMapper.selectList(any())).thenReturn(List.of());

        assertThat(service.buildServiceHealth(1)).isEmpty();
        verifyNoInteractions(roleInstanceMapper, alertHistoryMapper);
    }

    @Test
    void buildProfile_aggregatesHostsAndFallsBackWhenClusterInfoMissing() {
        when(clusterInfoMapper.selectById(eq(1))).thenReturn(null);
        when(hostMapper.selectMaps(any())).thenReturn(List.of(Map.<String, Object>of(
                "node_count", 5L, "total_cores", 80L, "total_mem", 150L, "total_disk", 2660L)));
        when(hostMapper.selectObjs(any())).thenReturn(List.of("x86_64", "x86_64", "aarch64"));

        ClusterDashboardResponse.ClusterProfile profile = service.buildProfile(1);

        assertThat(profile.getClusterName()).isNull();
        assertThat(profile.getClusterState()).isNull();
        assertThat(profile.getNodeCount()).isEqualTo(5);
        assertThat(profile.getTotalCores()).isEqualTo(80);
        assertThat(profile.getTotalMemGb()).isEqualTo(150);
        assertThat(profile.getTotalDiskGb()).isEqualTo(2660);
        assertThat(profile.getCpuArchitectures()).containsExactly("x86_64", "x86_64", "aarch64");
        // 本期无数据源的字段必须恒为 null，不能杜撰假值
        assertThat(profile.getOsName()).isNull();
        assertThat(profile.getKernelVersion()).isNull();
        assertThat(profile.getUptimeSeconds()).isNull();
    }
}
