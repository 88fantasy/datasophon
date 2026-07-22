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

package com.datasophon.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.api.load.ServiceInfoMap;
import com.datasophon.api.service.ServiceInstancePortResolver.RolePort;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceInfo;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceRoleGroupConfig;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.NeedRestart;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 端口参数归属由角色的 portParams 声明；端口值优先读角色组实时 configJson，缺失时退回 ddl
 * defaultValue。反查只在当前集群主机范围内执行。
 */
class ServiceInstancePortResolverTest {

    private final ClusterHostService hostService = mock(ClusterHostService.class);
    private final ClusterInfoService clusterInfoService = mock(ClusterInfoService.class);
    private final ClusterServiceRoleInstanceService roleService = mock(ClusterServiceRoleInstanceService.class);
    private final ClusterServiceRoleGroupConfigService roleGroupConfigService =
            mock(ClusterServiceRoleGroupConfigService.class);
    private final ServiceInstancePortResolver resolver = new ServiceInstancePortResolver(
            hostService, clusterInfoService, roleService, roleGroupConfigService);

    @Test
    void portsOf_readsMultiplePortsFromLiveConfig() {
        givenClusterFrame(1, "FRAME_A");
        givenService("FRAME_A_DORIS", List.of(roleInfo("DorisFE", "http_port", "query_port")),
                portParam("http_port", "8030"), portParam("query_port", "9030"));
        when(roleGroupConfigService.getConfigByRoleGroupId(201)).thenReturn(groupConfig(
                "[{\"name\":\"http_port\",\"value\":\"18030\"},"
                        + "{\"name\":\"query_port\",\"value\":\"19030\"},"
                        + "{\"name\":\"fe_heap_size\",\"value\":\"4096\"}]"));
        ClusterServiceRoleInstanceEntity role = role(1, "DORIS", "DorisFE", 201, NeedRestart.NO);

        List<RolePort> ports = resolver.portsOf(role);

        // 旧集群 configJson 没有 port=true，也必须按 ddl 的参数名读取用户已生效的自定义值。
        assertThat(ports).extracting(RolePort::port).containsExactly(18030, 19030);
    }

    @Test
    void portsOf_fallsBackToDdlDefaultsWhenRoleGroupIdMissing() {
        givenClusterFrame(2, "FRAME_B");
        givenService("FRAME_B_DORIS", List.of(roleInfo("DorisFE", "http_port", "query_port")),
                portParam("http_port", "8030"), portParam("query_port", "9030"), nonPortParam("fe_heap_size", "4096"));
        ClusterServiceRoleInstanceEntity role = role(2, "DORIS", "DorisFE", null, null);

        List<RolePort> ports = resolver.portsOf(role);

        assertThat(ports).extracting(RolePort::port).containsExactlyInAnyOrder(8030, 9030);
    }

    @Test
    void portsOf_fallsBackToDdlDefaultsWhenConfigJsonHasNoPortValues() {
        givenClusterFrame(3, "FRAME_C");
        givenService("FRAME_C_NACOS", List.of(roleInfo("NacosServer", "nacosServerPort")),
                portParam("nacosServerPort", "8848"));
        when(roleGroupConfigService.getConfigByRoleGroupId(202))
                .thenReturn(groupConfig("[{\"name\":\"otherParam\",\"value\":\"x\"}]"));
        ClusterServiceRoleInstanceEntity role = role(3, "NACOS", "NacosServer", 202, NeedRestart.NO);

        List<RolePort> ports = resolver.portsOf(role);

        assertThat(ports).extracting(RolePort::port).containsExactly(8848);
    }

    @Test
    void portsOf_onlyReturnsPortsDeclaredForCurrentRole() {
        givenClusterFrame(4, "FRAME_DORIS");
        givenService("FRAME_DORIS_DORIS", List.of(
                roleInfo("DorisFE", "http_port", "query_port"),
                roleInfo("DorisBE", "be_port", "webserver_port")),
                portParam("http_port", "8030"), portParam("query_port", "9030"),
                portParam("be_port", "9060"), portParam("webserver_port", "8040"));
        when(roleGroupConfigService.getConfigByRoleGroupId(204)).thenReturn(groupConfig(
                "[{\"name\":\"http_port\",\"value\":\"8030\"},"
                        + "{\"name\":\"query_port\",\"value\":\"9030\"},"
                        + "{\"name\":\"be_port\",\"value\":\"9060\"},"
                        + "{\"name\":\"webserver_port\",\"value\":\"8040\"}]"));

        List<RolePort> ports = resolver.portsOf(role(4, "DORIS", "DorisBE", 204, NeedRestart.NO));

        assertThat(ports).extracting(RolePort::paramName).containsExactly("be_port", "webserver_port");
        assertThat(ports).extracting(RolePort::port).containsExactly(9060, 8040);
    }

    @Test
    void portsOf_batch_cachesClusterFrameLookupPerCluster() {
        givenClusterFrame(5, "FRAME_BATCH");
        givenService("FRAME_BATCH_NACOS", List.of(roleInfo("NacosServer", "nacosServerPort")),
                portParam("nacosServerPort", "8848"));
        ClusterServiceRoleInstanceEntity roleA = role(5, "NACOS", "NacosServer", null, null);
        ClusterServiceRoleInstanceEntity roleB = role(5, "NACOS", "NacosServer", null, null);
        ClusterServiceRoleInstanceEntity roleC = role(5, "NACOS", "NacosServer", null, null);

        List<List<RolePort>> result = resolver.portsOf(List.of(roleA, roleB, roleC));

        // 三行都属于同一 clusterId=5,批量重载应只查一次 getById,而不是按行各查一次。
        assertThat(result).hasSize(3);
        result.forEach(ports -> assertThat(ports).extracting(RolePort::port).containsExactly(8848));
        verify(clusterInfoService, times(1)).getById(5);
    }

    @Test
    void resolveServiceType_matchesByIpAgainstClusterInstance() {
        givenClusterFrame(7, "FRAME_D");
        givenService("FRAME_D_DORIS", List.of(roleInfo("DorisFE", "query_port")),
                portParam("query_port", "9030"));
        when(hostService.getClusterHostByIp("192.168.10.131")).thenReturn(host(7, "ddh-01", "192.168.10.131"));
        when(roleGroupConfigService.getConfigByRoleGroupId(301)).thenReturn(
                groupConfig("[{\"name\":\"query_port\",\"value\":\"9030\"}]"));
        when(roleService.getServiceRoleListByHostnameAndClusterId("ddh-01", 7))
                .thenReturn(List.of(role(7, "DORIS", "DorisFE", 301, NeedRestart.NO)));

        String type = resolver.resolveServiceType(7, "192.168.10.131", "9030");

        // 关键场景:9030 曾被 dbSystem 三级兜底误标成 mysql(Doris 走 MySQL 协议),这里应精确反查出 doris。
        assertThat(type).isEqualTo("doris");
    }

    @Test
    void resolveServiceType_fallsBackToHostnameLookupWhenIpMissesOnClusterHost() {
        givenClusterFrame(8, "FRAME_E");
        givenService("FRAME_E_DS", List.of(roleInfo("WorkerServer", "workerServerPort")),
                portParam("workerServerPort", "18082"));
        when(hostService.getClusterHostByIp("ddh-03")).thenReturn(null);
        when(hostService.getClusterHostByHostname("ddh-03")).thenReturn(host(8, "ddh-03", "192.168.10.133"));
        when(roleGroupConfigService.getConfigByRoleGroupId(302)).thenReturn(
                groupConfig("[{\"name\":\"workerServerPort\",\"value\":\"18082\"}]"));
        when(roleService.getServiceRoleListByHostnameAndClusterId("ddh-03", 8))
                .thenReturn(List.of(role(8, "DS", "WorkerServer", 302, NeedRestart.NO)));

        String type = resolver.resolveServiceType(8, "ddh-03", "18082");

        assertThat(type).isEqualTo("ds");
    }

    @Test
    void resolveServiceType_fallsBackToWellKnownPortTableWhenNoInstanceMatches() {
        // 平台自身进程端口(datasophon-worker 的 gRPC 18082)不由任何 service_ddl.json 声明,
        // 集群实例表反查不到时应退回静态知名端口表。
        when(hostService.getClusterHostByIp("192.168.10.133")).thenReturn(host(9, "ddh-03", "192.168.10.133"));

        String type = resolver.resolveServiceType(9, "192.168.10.133", "18082");

        assertThat(type).isEqualTo("datasophon-worker");
    }

    @Test
    void resolveServiceType_returnsNullWhenNothingMatches() {
        when(hostService.getClusterHostByIp("10.0.0.9")).thenReturn(null);
        when(hostService.getClusterHostByHostname("10.0.0.9")).thenReturn(null);

        assertThat(resolver.resolveServiceType(9, "10.0.0.9", "54321")).isNull();
    }

    @Test
    void resolveServiceType_doesNotClassifyWellKnownPortOnExternalHost() {
        when(hostService.getClusterHostByIp("10.0.0.9")).thenReturn(null);
        when(hostService.getClusterHostByHostname("10.0.0.9")).thenReturn(null);

        assertThat(resolver.resolveServiceType(9, "10.0.0.9", "8080")).isNull();
    }

    @Test
    void resolveServiceType_doesNotClassifyHostFromAnotherCluster() {
        when(hostService.getClusterHostByIp("192.168.10.131"))
                .thenReturn(host(7, "ddh-01", "192.168.10.131"));

        assertThat(resolver.resolveServiceType(9, "192.168.10.131", "8080")).isNull();
    }

    @Test
    void resolveServiceType_returnsNullOnBlankAddrOrInvalidPort() {
        assertThat(resolver.resolveServiceType(9, "", "18082")).isNull();
        assertThat(resolver.resolveServiceType(9, "192.168.10.133", "not-a-port")).isNull();
        assertThat(resolver.resolveServiceType(9, "192.168.10.133", null)).isNull();
    }

    private void givenClusterFrame(int clusterId, String frame) {
        ClusterInfoEntity cluster = new ClusterInfoEntity();
        cluster.setClusterFrame(frame);
        when(clusterInfoService.getById(clusterId)).thenReturn(cluster);
    }

    private static ClusterHostDO host(Integer clusterId, String hostname, String ip) {
        ClusterHostDO host = new ClusterHostDO();
        host.setClusterId(clusterId);
        host.setHostname(hostname);
        host.setIp(ip);
        return host;
    }

    private static ClusterServiceRoleInstanceEntity role(Integer clusterId, String serviceName, String roleName,
                                                         Integer roleGroupId, NeedRestart needRestart) {
        ClusterServiceRoleInstanceEntity role = new ClusterServiceRoleInstanceEntity();
        role.setClusterId(clusterId);
        role.setServiceName(serviceName);
        role.setServiceRoleName(roleName);
        role.setRoleGroupId(roleGroupId);
        role.setNeedRestart(needRestart);
        return role;
    }

    private static ClusterServiceRoleGroupConfig groupConfig(String configJson) {
        ClusterServiceRoleGroupConfig config = new ClusterServiceRoleGroupConfig();
        config.setConfigJson(configJson);
        return config;
    }

    private static ServiceConfig portParam(String name, String defaultValue) {
        ServiceConfig config = new ServiceConfig();
        config.setName(name);
        config.setDefaultValue(defaultValue);
        config.setPort(true);
        return config;
    }

    private static ServiceConfig nonPortParam(String name, String defaultValue) {
        ServiceConfig config = new ServiceConfig();
        config.setName(name);
        config.setDefaultValue(defaultValue);
        return config;
    }

    private static ServiceRoleInfo roleInfo(String name, String... portParams) {
        ServiceRoleInfo role = new ServiceRoleInfo();
        role.setName(name);
        role.setPortParams(List.of(portParams));
        return role;
    }

    private static void givenService(String key, List<ServiceRoleInfo> roles, ServiceConfig... params) {
        ServiceInfo info = new ServiceInfo();
        info.setRoles(roles);
        info.setParameters(new ArrayList<>(List.of(params)));
        ServiceInfoMap.put(key, info);
    }
}
