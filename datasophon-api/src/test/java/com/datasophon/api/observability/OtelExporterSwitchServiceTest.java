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
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.ClusterVariableService;
import com.datasophon.api.service.ServiceInstallService;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.entity.ClusterVariable;
import com.datasophon.dao.enums.ServiceRoleState;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OtelExporterSwitchServiceTest {

    private final OtelCollectorConfigService configService = mock(OtelCollectorConfigService.class);
    private final ClusterServiceRoleInstanceService roleService = mock(ClusterServiceRoleInstanceService.class);
    private final ClusterVariableService variableService = mock(ClusterVariableService.class);
    private final ServiceInstallService installService = mock(ServiceInstallService.class);
    private final OtelCredentialService credentialService = mock(OtelCredentialService.class);
    private final OtelExporterSwitchService service = new OtelExporterSwitchService(
            configService, roleService, variableService, installService, credentialService);

    @Test
    void switchesNodeWithCompleteDorisParameters() {
        runningDoris();
        when(installService.getServiceConfigOption(7, "OTELCOLLECTOR"))
                .thenReturn(List.of(config("batchSize", "8192")));
        when(variableService.getVariables(7, "DORIS"))
                .thenReturn(List.of(variable("http_port", "8030")));
        when(credentialService.getOrCreate(7))
                .thenReturn(new OtelCredentials("collector-secret", "reader-secret"));
        when(configService.pushNodeConfig(org.mockito.ArgumentMatchers.eq(7),
                org.mockito.ArgumentMatchers.eq("worker-1"), anyMap())).thenReturn(ExecResult.success());

        ExecResult result = service.switchNode(
                7, "worker-1", ExporterMode.DORIS, Map.of("batchSize", "4096"));

        assertThat(result.getExecResult()).isTrue();
        ArgumentCaptor<Map<String, String>> params = ArgumentCaptor.forClass(Map.class);
        verify(configService).pushNodeConfig(org.mockito.ArgumentMatchers.eq(7),
                org.mockito.ArgumentMatchers.eq("worker-1"), params.capture());
        assertThat(params.getValue())
                .containsEntry("batchSize", "4096")
                .containsEntry("exporterMode", "doris")
                .containsEntry("dorisEndpoint", "http://doris-fe:8030")
                .containsEntry("dorisDatabase", "otel")
                .containsEntry("dorisUser", "otel_collector")
                .containsEntry("dorisPassword", "collector-secret");
    }

    @Test
    void rejectsDorisSwitchUntilFeAndBeAreRunning() {
        when(roleService.getServiceRoleInstanceListByClusterIdAndRoleName(7, "DorisFE"))
                .thenReturn(List.of(role("doris-fe", ServiceRoleState.RUNNING)));
        when(roleService.getServiceRoleInstanceListByClusterIdAndRoleName(7, "DorisBE"))
                .thenReturn(List.of(role("doris-be", ServiceRoleState.STOP)));

        ExecResult result = service.switchNode(7, "worker-1", ExporterMode.DORIS);

        assertThat(result.getExecResult()).isFalse();
        verify(configService, never()).pushNodeConfig(org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(), anyMap());
    }

    private void runningDoris() {
        when(roleService.getServiceRoleInstanceListByClusterIdAndRoleName(7, "DorisFE"))
                .thenReturn(List.of(role("doris-fe", ServiceRoleState.RUNNING)));
        when(roleService.getServiceRoleInstanceListByClusterIdAndRoleName(7, "DorisBE"))
                .thenReturn(List.of(role("doris-be", ServiceRoleState.RUNNING)));
    }

    private static ClusterServiceRoleInstanceEntity role(String hostname, ServiceRoleState state) {
        ClusterServiceRoleInstanceEntity role = new ClusterServiceRoleInstanceEntity();
        role.setHostname(hostname);
        role.setServiceRoleState(state);
        return role;
    }

    private static ServiceConfig config(String name, String value) {
        ServiceConfig config = new ServiceConfig();
        config.setName(name);
        config.setValue(value);
        return config;
    }

    private static ClusterVariable variable(String name, String value) {
        ClusterVariable variable = new ClusterVariable();
        variable.setVariableName("${DORIS." + name + "}");
        variable.setVariableValue(value);
        return variable;
    }
}
