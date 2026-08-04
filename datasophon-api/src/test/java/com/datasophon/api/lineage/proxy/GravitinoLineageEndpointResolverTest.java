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

package com.datasophon.api.lineage.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.ServiceInstancePortResolver;
import com.datasophon.api.service.ServiceInstancePortResolver.RolePort;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.ServiceRoleState;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class GravitinoLineageEndpointResolverTest {

    private final ClusterServiceRoleInstanceService roleService =
            mock(ClusterServiceRoleInstanceService.class);
    private final ClusterHostService hostService = mock(ClusterHostService.class);
    private final ServiceInstancePortResolver portResolver = mock(ServiceInstancePortResolver.class);
    private final GravitinoLineageEndpointResolver resolver =
            new GravitinoLineageEndpointResolver(roleService, hostService, portResolver);
    private ClusterServiceRoleInstanceEntity role;

    @BeforeEach
    void setUp() {
        role = new ClusterServiceRoleInstanceEntity();
        role.setClusterId(7);
        role.setHostname("ddh-02");
        role.setServiceRoleState(ServiceRoleState.RUNNING);
        when(roleService.getServiceRoleInstanceListByClusterIdAndRoleName(
                7, GravitinoLineageEndpointResolver.ROLE_NAME)).thenReturn(List.of(role));

        ClusterHostDO host = new ClusterHostDO();
        host.setClusterId(7);
        host.setHostname("ddh-02");
        host.setIp("192.168.10.132");
        when(hostService.getClusterHostByHostname("ddh-02")).thenReturn(host);
        when(portResolver.portsOf(role)).thenReturn(List.of(
                new RolePort(GravitinoLineageEndpointResolver.HTTP_PORT_PARAM, "HTTP", 8090)));
    }

    @Test
    void resolvesUniqueRunningInstanceWithLivePort() {
        assertThat(resolver.resolve(7)).isEqualTo(URI.create("http://192.168.10.132:8090/api/"));
    }

    @Test
    void rejectsMissingStoppedAndMultipleRunningInstances() {
        when(roleService.getServiceRoleInstanceListByClusterIdAndRoleName(
                7, GravitinoLineageEndpointResolver.ROLE_NAME)).thenReturn(List.of());
        assertUnavailable();

        role.setServiceRoleState(ServiceRoleState.STOP);
        when(roleService.getServiceRoleInstanceListByClusterIdAndRoleName(
                7, GravitinoLineageEndpointResolver.ROLE_NAME)).thenReturn(List.of(role));
        assertUnavailable();

        role.setServiceRoleState(ServiceRoleState.RUNNING);
        ClusterServiceRoleInstanceEntity second = new ClusterServiceRoleInstanceEntity();
        second.setServiceRoleState(ServiceRoleState.RUNNING);
        when(roleService.getServiceRoleInstanceListByClusterIdAndRoleName(
                7, GravitinoLineageEndpointResolver.ROLE_NAME)).thenReturn(List.of(role, second));
        assertUnavailable();
    }

    @Test
    void rejectsMissingHostOrConfiguredHttpPort() {
        when(hostService.getClusterHostByHostname("ddh-02")).thenReturn(null);
        assertUnavailable();

        ClusterHostDO host = new ClusterHostDO();
        host.setClusterId(7);
        host.setIp("192.168.10.132");
        when(hostService.getClusterHostByHostname("ddh-02")).thenReturn(host);
        when(portResolver.portsOf(role)).thenReturn(List.of(new RolePort("another.port", "RPC", 8091)));
        assertUnavailable();
    }

    private void assertUnavailable() {
        assertThatThrownBy(() -> resolver.resolve(7))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(error -> ((ResponseStatusException) error).getStatusCode().value())
                .isEqualTo(503);
    }
}
