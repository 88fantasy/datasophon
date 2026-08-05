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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.api.dto.v2.ApisixGatewayResponse;
import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.dao.entity.ClusterServiceInstanceEntity;
import com.datasophon.dao.entity.ClusterServiceInstanceRoleGroup;
import com.datasophon.dao.entity.ClusterServiceRoleGroupConfig;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.NeedRestart;
import com.datasophon.dao.enums.ServiceRoleState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.conditions.Wrapper;

class ApisixGatewayConfigServiceTest {

    private static final Integer CLUSTER_ID = 1;
    private static final Integer INSTANCE_ID = 10;
    private static final Integer ROLE_GROUP_ID = 100;

    private final ClusterInfoService clusterInfoService = mock(ClusterInfoService.class);
    private final ClusterServiceInstanceService serviceInstanceService = mock(ClusterServiceInstanceService.class);
    private final ClusterServiceInstanceRoleGroupService roleGroupService =
            mock(ClusterServiceInstanceRoleGroupService.class);
    private final ClusterServiceRoleGroupConfigService roleGroupConfigService =
            mock(ClusterServiceRoleGroupConfigService.class);
    private final ClusterServiceRoleInstanceService roleInstanceService =
            mock(ClusterServiceRoleInstanceService.class);
    private final ServiceInstallService serviceInstallService = mock(ServiceInstallService.class);
    private final Executor synchronousExecutor = Runnable::run;

    private final ApisixGatewayConfigService service = new ApisixGatewayConfigService(
            clusterInfoService, serviceInstanceService, roleGroupService, roleGroupConfigService,
            roleInstanceService, serviceInstallService, synchronousExecutor);

    private static ClusterServiceInstanceEntity apisixInstance() {
        ClusterServiceInstanceEntity entity = new ClusterServiceInstanceEntity();
        entity.setId(INSTANCE_ID);
        entity.setServiceName("APISIX");
        entity.setNeedRestart(NeedRestart.NO);
        return entity;
    }

    private static ServiceConfig config(String name, Object value) {
        ServiceConfig config = new ServiceConfig();
        config.setName(name);
        config.setValue(value);
        return config;
    }

    private void stubEffectiveConfigs(List<ServiceConfig> configs) {
        when(serviceInstallService.getServiceConfigOption(eq(CLUSTER_ID), eq("APISIX")))
                .thenReturn(new ArrayList<>(configs));
    }

    /** T4 各用例保存前的默认已持久化配置（apisixGatewayYaml 已存在，路由/上游走向导默认值）。 */
    private static List<ServiceConfig> defaultSaveConfigs() {
        return List.of(
                config("apisixGatewayYaml", ""),
                config("apisixRouteUri", "/get"),
                config("apisixUpstreamHost", "127.0.0.1"),
                config("apisixUpstreamPort", 8080));
    }

    private void stubDefaultRoleGroupAndEmptyPush() {
        ClusterServiceInstanceRoleGroup group = new ClusterServiceInstanceRoleGroup();
        group.setId(ROLE_GROUP_ID);
        when(roleGroupService.getDefaultRoleGroupByServiceInstanceId(INSTANCE_ID)).thenReturn(group);
        when(roleGroupService.getById(ROLE_GROUP_ID)).thenReturn(group);

        ClusterServiceRoleGroupConfig savedConfig = new ClusterServiceRoleGroupConfig();
        savedConfig.setConfigFileJson("[]");
        when(roleGroupConfigService.getConfigByRoleGroupId(ROLE_GROUP_ID)).thenReturn(savedConfig);

        when(roleInstanceService.getServiceRoleInstanceListByClusterIdAndRoleName(CLUSTER_ID, "Apisix"))
                .thenReturn(List.of());
    }

    // ------------------------------------------------------------------
    // T3: GET /apisix/gateway
    // ------------------------------------------------------------------

    @Test
    void getGatewayConfig_buildsWizardYaml_whenGatewayYamlParamEmpty() {
        when(serviceInstanceService.getById(INSTANCE_ID)).thenReturn(apisixInstance());
        stubEffectiveConfigs(List.of(
                config("apisixGatewayYaml", ""),
                config("apisixRouteUri", "/get"),
                config("apisixUpstreamHost", "192.168.10.135"),
                config("apisixUpstreamPort", 8080)));
        when(roleInstanceService.getServiceRoleInstanceListByClusterIdAndRoleName(CLUSTER_ID, "Apisix"))
                .thenReturn(List.of());

        ApisixGatewayResponse response = service.getGatewayConfig(CLUSTER_ID, INSTANCE_ID);

        assertTrue(response.getGatewayYaml().contains("uri: '/get'"), "应由向导参数拼出初始路由");
        assertTrue(response.getGatewayYaml().contains("'192.168.10.135:8080': 1"));
        assertTrue(response.getManagedSuffix().contains("#END"), "托管段须含 #END");
        assertFalse(response.getGatewayYaml().contains("#END"), "托管段不应混入用户可编辑段");
    }

    @Test
    void getGatewayConfig_returnsPersistedYaml_whenGatewayYamlParamNonEmpty() {
        String persisted = "upstreams:\n  - id: 1\nroutes:\n  - id: 1\n    uri: '/custom'\n";
        when(serviceInstanceService.getById(INSTANCE_ID)).thenReturn(apisixInstance());
        stubEffectiveConfigs(List.of(
                config("apisixGatewayYaml", persisted),
                config("apisixRouteUri", "/get"),
                config("apisixUpstreamHost", "127.0.0.1"),
                config("apisixUpstreamPort", 8080)));
        when(roleInstanceService.getServiceRoleInstanceListByClusterIdAndRoleName(CLUSTER_ID, "Apisix"))
                .thenReturn(List.of());

        ApisixGatewayResponse response = service.getGatewayConfig(CLUSTER_ID, INSTANCE_ID);

        assertEquals(persisted, response.getGatewayYaml(), "已保存过则原样返回，不重新拼向导 YAML");
    }

    @Test
    void getGatewayConfig_backfillsMissingParam_forLegacyInstance() {
        // 已安装实例的持久化 configJson 早于 apisixGatewayYaml 这个 DDL 参数存在
        when(serviceInstanceService.getById(INSTANCE_ID)).thenReturn(apisixInstance());
        stubEffectiveConfigs(List.of(
                config("apisixRouteUri", "/legacy"),
                config("apisixUpstreamHost", "10.0.0.1"),
                config("apisixUpstreamPort", 9999)));
        when(serviceInstallService.getServiceConfigFromDdl(eq(CLUSTER_ID), eq("APISIX")))
                .thenReturn(List.of(config("apisixGatewayYaml", "")));
        when(roleInstanceService.getServiceRoleInstanceListByClusterIdAndRoleName(CLUSTER_ID, "Apisix"))
                .thenReturn(List.of());

        ApisixGatewayResponse response = service.getGatewayConfig(CLUSTER_ID, INSTANCE_ID);

        assertTrue(response.getGatewayYaml().contains("uri: '/legacy'"));
        assertTrue(response.getGatewayYaml().contains("'10.0.0.1:9999': 1"));
    }

    @Test
    void getGatewayConfig_mapsRoleInstancesToRoles() {
        when(serviceInstanceService.getById(INSTANCE_ID)).thenReturn(apisixInstance());
        stubEffectiveConfigs(List.of(
                config("apisixGatewayYaml", "routes: []\n"),
                config("apisixRouteUri", "/get"),
                config("apisixUpstreamHost", "127.0.0.1"),
                config("apisixUpstreamPort", 8080)));
        ClusterServiceRoleInstanceEntity role = new ClusterServiceRoleInstanceEntity();
        role.setHostname("ddh-01");
        role.setServiceRoleState(ServiceRoleState.RUNNING);
        when(roleInstanceService.getServiceRoleInstanceListByClusterIdAndRoleName(CLUSTER_ID, "Apisix"))
                .thenReturn(List.of(role));

        ApisixGatewayResponse response = service.getGatewayConfig(CLUSTER_ID, INSTANCE_ID);

        assertEquals(1, response.getRoles().size());
        assertEquals("ddh-01", response.getRoles().get(0).getHostname());
    }

    // ------------------------------------------------------------------
    // T4: POST /apisix/gateway —— YAML 校验四条规则
    // ------------------------------------------------------------------

    private static final String VALID_YAML = "upstreams:\n"
            + "  - id: 1\n"
            + "    type: roundrobin\n"
            + "    nodes:\n"
            + "      '127.0.0.1:8080': 1\n"
            + "routes:\n"
            + "  - id: 1\n"
            + "    uri: '/get'\n"
            + "    upstream_id: 1\n"
            + "global_rules:\n"
            + "  - id: 1\n"
            + "    plugins:\n"
            + "      prometheus:\n"
            + "        prefer_name: true\n"
            + "  - id: 2\n"
            + "    plugins:\n"
            + "      opentelemetry:\n"
            + "        sampler:\n"
            + "          name: always_on\n";

    @Test
    void saveGatewayConfig_rejectsNonApisixInstance() {
        ClusterServiceInstanceEntity other = new ClusterServiceInstanceEntity();
        other.setId(INSTANCE_ID);
        other.setServiceName("KAFKA");
        when(serviceInstanceService.getById(INSTANCE_ID)).thenReturn(other);

        assertThrows(BusinessHintException.class,
                () -> service.saveGatewayConfig(CLUSTER_ID, INSTANCE_ID, VALID_YAML));
        verify(serviceInstallService, never()).saveServiceConfig(any(), any(), any(), any());
    }

    @Test
    void saveGatewayConfig_rejectsYamlContainingEndMarker() {
        when(serviceInstanceService.getById(INSTANCE_ID)).thenReturn(apisixInstance());

        String withEnd = VALID_YAML + "#END\n";
        assertThrows(BusinessHintException.class,
                () -> service.saveGatewayConfig(CLUSTER_ID, INSTANCE_ID, withEnd));
    }

    @Test
    void saveGatewayConfig_rejectsPluginMetadataKey() {
        when(serviceInstanceService.getById(INSTANCE_ID)).thenReturn(apisixInstance());

        String withPluginMetadata = VALID_YAML + "plugin_metadata:\n  - id: opentelemetry\n";
        assertThrows(BusinessHintException.class,
                () -> service.saveGatewayConfig(CLUSTER_ID, INSTANCE_ID, withPluginMetadata));
    }

    @Test
    void saveGatewayConfig_rejectsMissingPrometheusRule() {
        when(serviceInstanceService.getById(INSTANCE_ID)).thenReturn(apisixInstance());

        String missingPrometheus = "routes: []\n"
                + "global_rules:\n"
                + "  - id: 2\n"
                + "    plugins:\n"
                + "      opentelemetry:\n"
                + "        sampler:\n"
                + "          name: always_on\n";
        assertThrows(BusinessHintException.class,
                () -> service.saveGatewayConfig(CLUSTER_ID, INSTANCE_ID, missingPrometheus));
    }

    @Test
    void saveGatewayConfig_rejectsMissingOpentelemetryRule() {
        when(serviceInstanceService.getById(INSTANCE_ID)).thenReturn(apisixInstance());

        String missingOtel = "routes: []\n"
                + "global_rules:\n"
                + "  - id: 1\n"
                + "    plugins:\n"
                + "      prometheus:\n"
                + "        prefer_name: true\n";
        assertThrows(BusinessHintException.class,
                () -> service.saveGatewayConfig(CLUSTER_ID, INSTANCE_ID, missingOtel));
    }

    @Test
    void saveGatewayConfig_rejectsUnparsableYaml() {
        when(serviceInstanceService.getById(INSTANCE_ID)).thenReturn(apisixInstance());

        assertThrows(BusinessHintException.class,
                () -> service.saveGatewayConfig(CLUSTER_ID, INSTANCE_ID, "not: [valid"));
    }

    @Test
    void saveGatewayConfig_acceptsValidYaml_andSavesWithDefaultRoleGroupSentinel() {
        when(serviceInstanceService.getById(INSTANCE_ID)).thenReturn(apisixInstance());
        stubEffectiveConfigs(defaultSaveConfigs());
        stubDefaultRoleGroupAndEmptyPush();

        service.saveGatewayConfig(CLUSTER_ID, INSTANCE_ID, VALID_YAML);

        verify(serviceInstallService).saveServiceConfig(eq(CLUSTER_ID), eq("APISIX"), any(), eq(-1));
    }

    // ------------------------------------------------------------------
    // T4: needRestart 有条件复位
    // ------------------------------------------------------------------

    @Test
    void saveGatewayConfig_resetsNeedRestart_whenPreviouslyNo() {
        ClusterServiceInstanceEntity instance = apisixInstance();
        instance.setNeedRestart(NeedRestart.NO);
        when(serviceInstanceService.getById(INSTANCE_ID)).thenReturn(instance);
        stubEffectiveConfigs(defaultSaveConfigs());
        stubDefaultRoleGroupAndEmptyPush();
        ClusterServiceRoleInstanceEntity roleInstance = new ClusterServiceRoleInstanceEntity();
        roleInstance.setNeedRestart(NeedRestart.YES);
        when(roleInstanceService.list(any(Wrapper.class))).thenReturn(new ArrayList<>(List.of(roleInstance)));

        service.saveGatewayConfig(CLUSTER_ID, INSTANCE_ID, VALID_YAML);

        verify(serviceInstanceService).updateById(argThatNeedRestartIsNo());
        verify(roleGroupService).updateById(argThatRoleGroupNeedRestartIsNo());
        verify(roleInstanceService).updateBatchById(any());
        assertEquals(NeedRestart.NO, roleInstance.getNeedRestart(), "角色实例应被复位回 NO");
    }

    @Test
    void saveGatewayConfig_keepsNeedRestart_whenPreviouslyYes() {
        ClusterServiceInstanceEntity instance = apisixInstance();
        instance.setNeedRestart(NeedRestart.YES);
        when(serviceInstanceService.getById(INSTANCE_ID)).thenReturn(instance);
        stubEffectiveConfigs(defaultSaveConfigs());
        stubDefaultRoleGroupAndEmptyPush();

        service.saveGatewayConfig(CLUSTER_ID, INSTANCE_ID, VALID_YAML);

        // 调用前已是 YES：说明用户另改过需重启项（如端口），不能被网关保存悄悄清掉
        verify(serviceInstanceService, never()).updateById(any());
        verify(roleGroupService, never()).updateById(any());
        verify(roleInstanceService, never()).updateBatchById(any());
    }

    private static ClusterServiceInstanceEntity argThatNeedRestartIsNo() {
        return argThat(e -> e.getNeedRestart() == NeedRestart.NO);
    }

    private static ClusterServiceInstanceRoleGroup argThatRoleGroupNeedRestartIsNo() {
        return argThat(g -> g.getNeedRestart() == NeedRestart.NO);
    }
}
