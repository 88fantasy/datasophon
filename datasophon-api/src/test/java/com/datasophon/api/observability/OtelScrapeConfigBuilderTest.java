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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.api.load.ServiceInfoMap;
import com.datasophon.api.load.ServiceRoleMap;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterServiceRoleGroupConfigService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceInfo;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceRoleGroupConfig;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.NeedRestart;
import com.datasophon.dao.enums.ServiceRoleState;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * jmxPort 已从 ddl 撤销，端口一律来自 jmxPortParam 指向的业务参数：优先读角色组 configJson 里的实时值，
 * 读不到时退回该参数在 ddl 里的 defaultValue（ServiceInfoMap，内存态，无需查库）。
 */
class OtelScrapeConfigBuilderTest {

    private final ClusterServiceRoleInstanceService roleService = mock(ClusterServiceRoleInstanceService.class);
    private final ClusterInfoService clusterInfoService = mock(ClusterInfoService.class);
    private final ClusterServiceRoleGroupConfigService roleGroupConfigService =
            mock(ClusterServiceRoleGroupConfigService.class);
    private final OtelScrapeConfigBuilder builder =
            new OtelScrapeConfigBuilder(roleService, clusterInfoService, roleGroupConfigService);

    @Test
    void buildsLocalScrapeJobsForRunningRoles() {
        givenClusterFrame("FRAME_A");
        givenRoleMeta("FRAME_A_HDFS_NameNode", "namenodeJmxPort");
        givenRoleMeta("FRAME_A_HDFS_DataNode", "datanodeJmxPort");
        givenServiceDefaults("FRAME_A_HDFS",
                param("namenodeJmxPort", "9101"), param("datanodeJmxPort", "9102"));
        when(roleService.getServiceRoleListByHostnameAndClusterId("worker-1", 7))
                .thenReturn(List.of(
                        role("HDFS", "NameNode", ServiceRoleState.RUNNING),
                        role("HDFS", "DataNode", ServiceRoleState.RUNNING)));

        String yaml = builder.build(7, "worker-1");

        assertThat(yaml).contains("        - job_name: 'NameNode'");
        assertThat(yaml).contains("          metrics_path: '/metrics'");
        assertThat(yaml).contains("            - targets: ['127.0.0.1:9101']");
        assertThat(yaml).contains("              labels: {job: 'NameNode', instance: 'worker-1:9101'}");
        assertThat(yaml).contains("        - job_name: 'DataNode'");
        assertThat(yaml).contains("            - targets: ['127.0.0.1:9102']");
    }

    @Test
    void skipsStoppedRolesAndRolesWithoutRoleMetadata() {
        givenClusterFrame("FRAME_B");
        givenRoleMeta("FRAME_B_HDFS_NameNode", "namenodeJmxPort");
        givenServiceDefaults("FRAME_B_HDFS", param("namenodeJmxPort", "9201"));
        // DataNode 故意不注册 ServiceRoleMap 元数据，模拟"从未声明过监控端口"的角色。
        when(roleService.getServiceRoleListByHostnameAndClusterId("worker-2", 8))
                .thenReturn(List.of(
                        role("HDFS", "NameNode", ServiceRoleState.STOP),
                        role("HDFS", "DataNode", ServiceRoleState.RUNNING)));

        String yaml = builder.build(8, "worker-2");

        assertThat(yaml).doesNotContain("NameNode");
        assertThat(yaml).doesNotContain("DataNode");
    }

    @Test
    void appliesMetricsPathOverridesAndDorisGroupLabels() {
        givenClusterFrame("FRAME_C");
        givenRoleMeta("FRAME_C_DOLPHINSCHEDULER_ApiServer", "apiServerPort");
        givenRoleMeta("FRAME_C_NACOS_NacosServer", "nacosServerPort");
        givenRoleMeta("FRAME_C_APISIX_Apisix", "apisixPrometheusPort");
        givenRoleMeta("FRAME_C_MINIO_Minio", "minioMetricsPort");
        givenRoleMeta("FRAME_C_DORIS_DorisFE", "http_port");
        givenRoleMeta("FRAME_C_DORIS_DorisBE", "webserver_port");
        givenServiceDefaults("FRAME_C_DOLPHINSCHEDULER", param("apiServerPort", "12345"));
        givenServiceDefaults("FRAME_C_NACOS", param("nacosServerPort", "8848"));
        givenServiceDefaults("FRAME_C_APISIX", param("apisixPrometheusPort", "9091"));
        givenServiceDefaults("FRAME_C_MINIO", param("minioMetricsPort", "9000"));
        givenServiceDefaults("FRAME_C_DORIS",
                param("http_port", "8030"), param("webserver_port", "8040"));
        when(roleService.getServiceRoleListByHostnameAndClusterId("worker-3", 9))
                .thenReturn(List.of(
                        role("DOLPHINSCHEDULER", "ApiServer", ServiceRoleState.RUNNING),
                        role("NACOS", "NacosServer", ServiceRoleState.RUNNING),
                        role("APISIX", "Apisix", ServiceRoleState.RUNNING),
                        role("MINIO", "Minio", ServiceRoleState.RUNNING),
                        role("DORIS", "DorisFE", ServiceRoleState.RUNNING),
                        role("DORIS", "DorisBE", ServiceRoleState.RUNNING)));

        String yaml = builder.build(9, "worker-3");

        assertThat(yaml).contains("job_name: 'ApiServer'");
        assertThat(yaml).contains("metrics_path: '/dolphinscheduler/actuator/prometheus'");
        assertThat(yaml).contains("job_name: 'NacosServer'");
        assertThat(yaml).contains("metrics_path: '/nacos/actuator/prometheus'");
        assertThat(yaml).contains("job_name: 'Apisix'");
        assertThat(yaml).contains("metrics_path: '/apisix/prometheus/metrics'");
        assertThat(yaml).contains("targets: ['worker-3:9091']");
        assertThat(yaml).contains("job_name: 'Minio'");
        assertThat(yaml).contains("metrics_path: '/minio/v2/metrics/cluster'");
        assertThat(yaml).contains("targets: ['127.0.0.1:9000']");
        assertThat(yaml).contains("labels: {job: 'DorisFE', instance: 'worker-3:8030', group: 'fe'}");
        assertThat(yaml).contains("labels: {job: 'DorisBE', instance: 'worker-3:8040', group: 'be'}");
    }

    @Test
    void emptyRoleListProducesEmptyYaml() {
        givenClusterFrame("FRAME_D");
        when(roleService.getServiceRoleListByHostnameAndClusterId("worker-4", 10))
                .thenReturn(List.of());

        String yaml = builder.build(10, "worker-4");

        assertThat(yaml).isEmpty();
    }

    @Test
    void livePortOverridesDdlDefaultWhenConfiguredAndRestarted() {
        givenClusterFrame("FRAME_E");
        givenRoleMeta("FRAME_E_DORIS_DorisFE", "http_port");
        givenRoleMeta("FRAME_E_NACOS_NacosServer", "nacosServerPort");
        givenServiceDefaults("FRAME_E_DORIS", param("http_port", "18030"));
        givenServiceDefaults("FRAME_E_NACOS", param("nacosServerPort", "8848"));
        when(roleGroupConfigService.getConfigByRoleGroupId(101))
                .thenReturn(groupConfig("[{\"name\":\"http_port\",\"value\":\"28030\"}]"));
        when(roleGroupConfigService.getConfigByRoleGroupId(102))
                .thenReturn(groupConfig("[{\"name\":\"nacosServerPort\",\"value\":\"18848\"}]"));
        when(roleService.getServiceRoleListByHostnameAndClusterId("worker-5", 11))
                .thenReturn(List.of(
                        role("DORIS", "DorisFE", ServiceRoleState.RUNNING, 101, NeedRestart.NO),
                        role("NACOS", "NacosServer", ServiceRoleState.RUNNING, 102, NeedRestart.NO)));

        String yaml = builder.build(11, "worker-5");

        assertThat(yaml).contains("            - targets: ['127.0.0.1:28030']");
        assertThat(yaml).contains("              labels: {job: 'DorisFE', instance: 'worker-5:28030', group: 'fe'}");
        assertThat(yaml).contains("            - targets: ['127.0.0.1:18848']");
        assertThat(yaml).contains("              labels: {job: 'NacosServer', instance: 'worker-5:18848'}");
    }

    @Test
    void livePortIgnoredWhilePendingRestart() {
        givenClusterFrame("FRAME_F");
        givenRoleMeta("FRAME_F_DORIS_DorisFE", "http_port");
        givenServiceDefaults("FRAME_F_DORIS", param("http_port", "18030"));
        when(roleGroupConfigService.getConfigByRoleGroupId(103))
                .thenReturn(groupConfig("[{\"name\":\"http_port\",\"value\":\"28030\"}]"));
        when(roleService.getServiceRoleListByHostnameAndClusterId("worker-6", 12))
                .thenReturn(List.of(role("DORIS", "DorisFE", ServiceRoleState.RUNNING, 103, NeedRestart.YES)));

        String yaml = builder.build(12, "worker-6");

        // 没有上一版本可退（configVersion 缺省为 null），只能退回 ddl defaultValue。
        assertThat(yaml).contains("            - targets: ['127.0.0.1:18030']");
    }

    @Test
    void livePortFallsBackToPreviousVersionWhenPendingRestartIsForAnUnrelatedParam() {
        givenClusterFrame("FRAME_K");
        givenRoleMeta("FRAME_K_DORIS_DorisFE", "http_port");
        givenServiceDefaults("FRAME_K_DORIS", param("http_port", "18030"));
        // 最新一版（configVersion=3）待重启生效，可能改的是别的参数；上一版（2）是角色目前实际仍在跑的配置。
        ClusterServiceRoleGroupConfig latest = groupConfig(
                "[{\"name\":\"someOtherParam\",\"value\":\"x\"},{\"name\":\"http_port\",\"value\":\"28030\"}]");
        latest.setConfigVersion(3);
        when(roleGroupConfigService.getConfigByRoleGroupId(108)).thenReturn(latest);
        ClusterServiceRoleGroupConfig previous = groupConfig("[{\"name\":\"http_port\",\"value\":\"38030\"}]");
        when(roleGroupConfigService.getConfigByRoleGroupIdAndVersion(108, 2)).thenReturn(previous);
        when(roleService.getServiceRoleListByHostnameAndClusterId("worker-11", 17))
                .thenReturn(List.of(role("DORIS", "DorisFE", ServiceRoleState.RUNNING, 108, NeedRestart.YES)));

        String yaml = builder.build(17, "worker-11");

        // 退回上一版本里的端口（38030），既不是待生效的新值（28030），也不是 ddl 默认值（18030）。
        assertThat(yaml).contains("            - targets: ['127.0.0.1:38030']");
    }

    @Test
    void livePortFallsBackToDdlDefaultWhenNeverConfigured() {
        givenClusterFrame("FRAME_G");
        givenRoleMeta("FRAME_G_DORIS_DorisFE", "http_port");
        givenServiceDefaults("FRAME_G_DORIS", param("http_port", "18030"));
        when(roleGroupConfigService.getConfigByRoleGroupId(104)).thenReturn(null);
        when(roleService.getServiceRoleListByHostnameAndClusterId("worker-7", 13))
                .thenReturn(List.of(role("DORIS", "DorisFE", ServiceRoleState.RUNNING, 104, NeedRestart.NO)));

        String yaml = builder.build(13, "worker-7");

        assertThat(yaml).contains("            - targets: ['127.0.0.1:18030']");
    }

    @Test
    void livePortHandlesNumericConfigValue() {
        givenClusterFrame("FRAME_H");
        givenRoleMeta("FRAME_H_DORIS_DorisFE", "http_port");
        givenServiceDefaults("FRAME_H_DORIS", param("http_port", "18030"));
        when(roleGroupConfigService.getConfigByRoleGroupId(105))
                .thenReturn(groupConfig("[{\"name\":\"http_port\",\"value\":28030}]"));
        when(roleService.getServiceRoleListByHostnameAndClusterId("worker-8", 14))
                .thenReturn(List.of(role("DORIS", "DorisFE", ServiceRoleState.RUNNING, 105, NeedRestart.NO)));

        String yaml = builder.build(14, "worker-8");

        assertThat(yaml).contains("            - targets: ['127.0.0.1:28030']");
    }

    @Test
    void rolesWithoutRoleMetadataNeverQueryLiveConfigOrProduceAJob() {
        givenClusterFrame("FRAME_I");
        // 故意不注册 ServiceRoleMap，等价于该角色 ddl 里从未声明 jmxPortParam（如 HttpFs）。
        when(roleService.getServiceRoleListByHostnameAndClusterId("worker-9", 15))
                .thenReturn(List.of(role("HDFS", "NameNode", ServiceRoleState.RUNNING, 106, NeedRestart.NO)));

        String yaml = builder.build(15, "worker-9");

        assertThat(yaml).isEmpty();
        verify(roleGroupConfigService, never()).getConfigByRoleGroupId(any());
    }

    @Test
    void rolesWithRoleMetadataButNoDefaultValueProduceNoJob() {
        givenClusterFrame("FRAME_J");
        givenRoleMeta("FRAME_J_DORIS_DorisFE", "http_port");
        // 该角色声明了 jmxPortParam，但 ServiceInfoMap 里没有对应 service，无 live config，也无默认值兜底。
        when(roleGroupConfigService.getConfigByRoleGroupId(107)).thenReturn(null);
        when(roleService.getServiceRoleListByHostnameAndClusterId("worker-10", 16))
                .thenReturn(List.of(role("DORIS", "DorisFE", ServiceRoleState.RUNNING, 107, NeedRestart.NO)));

        String yaml = builder.build(16, "worker-10");

        assertThat(yaml).isEmpty();
    }

    private static void givenRoleMeta(String key, String jmxPortParam) {
        ServiceRoleInfo meta = new ServiceRoleInfo();
        meta.setJmxPortParam(jmxPortParam);
        ServiceRoleMap.put(key, meta);
    }

    private static ServiceConfig param(String name, String defaultValue) {
        ServiceConfig config = new ServiceConfig();
        config.setName(name);
        config.setDefaultValue(defaultValue);
        return config;
    }

    private static void givenServiceDefaults(String key, ServiceConfig... params) {
        ServiceInfo info = new ServiceInfo();
        info.setParameters(new ArrayList<>(List.of(params)));
        ServiceInfoMap.put(key, info);
    }

    private static ClusterServiceRoleGroupConfig groupConfig(String configJson) {
        ClusterServiceRoleGroupConfig config = new ClusterServiceRoleGroupConfig();
        config.setConfigJson(configJson);
        return config;
    }

    private void givenClusterFrame(String frame) {
        ClusterInfoEntity cluster = new ClusterInfoEntity();
        cluster.setClusterFrame(frame);
        when(clusterInfoService.getById(org.mockito.ArgumentMatchers.anyInt())).thenReturn(cluster);
    }

    private static ClusterServiceRoleInstanceEntity role(String serviceName, String roleName, ServiceRoleState state) {
        ClusterServiceRoleInstanceEntity role = new ClusterServiceRoleInstanceEntity();
        role.setServiceName(serviceName);
        role.setServiceRoleName(roleName);
        role.setServiceRoleState(state);
        return role;
    }

    private static ClusterServiceRoleInstanceEntity role(String serviceName, String roleName, ServiceRoleState state,
                                                         Integer roleGroupId, NeedRestart needRestart) {
        ClusterServiceRoleInstanceEntity role = role(serviceName, roleName, state);
        role.setRoleGroupId(roleGroupId);
        role.setNeedRestart(needRestart);
        return role;
    }
}
