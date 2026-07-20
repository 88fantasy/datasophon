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
import static org.mockito.Mockito.when;

import com.datasophon.api.load.ServiceInfoMap;
import com.datasophon.api.service.ServiceInstancePortResolver.RolePort;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceInfo;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceRoleGroupConfig;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.NeedRestart;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 端口来源分两层：① 角色组实时 configJson 里 isPort==true 的参数；② 读不到时退回 ddl 声明的
 * defaultValue（内存态 ServiceInfoMap）。反查（resolveServiceType）先按集群实例表精确匹配，
 * 未命中再查静态知名端口表。
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
        when(roleGroupConfigService.getConfigByRoleGroupId(201)).thenReturn(groupConfig(
                "[{\"name\":\"http_port\",\"value\":\"8030\",\"port\":true},"
                        + "{\"name\":\"query_port\",\"value\":\"9030\",\"port\":true},"
                        + "{\"name\":\"fe_heap_size\",\"value\":\"4096\"}]"));
        ClusterServiceRoleInstanceEntity role = role(1, "DORIS", "DorisFE", 201, NeedRestart.NO);

        List<RolePort> ports = resolver.portsOf(role);

        assertThat(ports).extracting(RolePort::port).containsExactlyInAnyOrder(8030, 9030);
    }

    @Test
    void portsOf_fallsBackToDdlDefaultsWhenRoleGroupIdMissing() {
        givenClusterFrame(2, "FRAME_B");
        givenServiceDefaults("FRAME_B_DORIS",
                portParam("http_port", "8030"), portParam("query_port", "9030"), nonPortParam("fe_heap_size", "4096"));
        ClusterServiceRoleInstanceEntity role = role(2, "DORIS", "DorisFE", null, null);

        List<RolePort> ports = resolver.portsOf(role);

        assertThat(ports).extracting(RolePort::port).containsExactlyInAnyOrder(8030, 9030);
    }

    @Test
    void portsOf_fallsBackToDdlDefaultsWhenConfigJsonHasNoPortValues() {
        givenClusterFrame(3, "FRAME_C");
        givenServiceDefaults("FRAME_C_NACOS", portParam("nacosServerPort", "8848"));
        when(roleGroupConfigService.getConfigByRoleGroupId(202))
                .thenReturn(groupConfig("[{\"name\":\"otherParam\",\"value\":\"x\"}]"));
        ClusterServiceRoleInstanceEntity role = role(3, "NACOS", "NacosServer", 202, NeedRestart.NO);

        List<RolePort> ports = resolver.portsOf(role);

        assertThat(ports).extracting(RolePort::port).containsExactly(8848);
    }

    @Test
    void resolveServiceType_matchesByIpAgainstClusterInstance() {
        givenClusterFrame(7, "FRAME_D");
        when(hostService.getClusterHostByIp("192.168.10.131")).thenReturn(host("ddh-01", "192.168.10.131"));
        when(roleGroupConfigService.getConfigByRoleGroupId(301)).thenReturn(
                groupConfig("[{\"name\":\"query_port\",\"value\":\"9030\",\"port\":true}]"));
        when(roleService.getServiceRoleListByHostnameAndClusterId("ddh-01", 7))
                .thenReturn(List.of(role(7, "DORIS", "DorisFE", 301, NeedRestart.NO)));

        String type = resolver.resolveServiceType(7, "192.168.10.131", "9030");

        // 关键场景:9030 曾被 dbSystem 三级兜底误标成 mysql(Doris 走 MySQL 协议),这里应精确反查出 doris。
        assertThat(type).isEqualTo("doris");
    }

    @Test
    void resolveServiceType_fallsBackToHostnameLookupWhenIpMissesOnClusterHost() {
        givenClusterFrame(8, "FRAME_E");
        when(hostService.getClusterHostByIp("ddh-03")).thenReturn(null);
        when(hostService.getClusterHostByHostname("ddh-03")).thenReturn(host("ddh-03", "192.168.10.133"));
        when(roleGroupConfigService.getConfigByRoleGroupId(302)).thenReturn(
                groupConfig("[{\"name\":\"workerServerPort\",\"value\":\"18082\",\"port\":true}]"));
        when(roleService.getServiceRoleListByHostnameAndClusterId("ddh-03", 8))
                .thenReturn(List.of(role(8, "DS", "WorkerServer", 302, NeedRestart.NO)));

        String type = resolver.resolveServiceType(8, "ddh-03", "18082");

        assertThat(type).isEqualTo("ds");
    }

    @Test
    void resolveServiceType_fallsBackToWellKnownPortTableWhenNoInstanceMatches() {
        // 平台自身进程端口(datasophon-worker 的 gRPC 18082)不由任何 service_ddl.json 声明,
        // 集群实例表反查不到时应退回静态知名端口表。
        when(hostService.getClusterHostByIp("192.168.10.133")).thenReturn(null);
        when(hostService.getClusterHostByHostname("192.168.10.133")).thenReturn(null);

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

    private static ClusterHostDO host(String hostname, String ip) {
        ClusterHostDO host = new ClusterHostDO();
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

    private static void givenServiceDefaults(String key, ServiceConfig... params) {
        ServiceInfo info = new ServiceInfo();
        info.setParameters(new ArrayList<>(List.of(params)));
        ServiceInfoMap.put(key, info);
    }
}
