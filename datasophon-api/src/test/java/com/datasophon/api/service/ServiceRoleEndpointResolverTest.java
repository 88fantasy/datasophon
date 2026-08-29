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

import com.datasophon.api.service.ServiceInstancePortResolver.RolePort;
import com.datasophon.api.service.ServiceRoleEndpointResolver.HostPort;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.ServiceRoleState;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ServiceRoleEndpointResolverTest {

    @Test
    void resolvesTheUniqueRoleMatchingTheAcceptedStatesAndPortName() {
        ClusterServiceRoleInstanceService roleService = mock(ClusterServiceRoleInstanceService.class);
        ClusterHostService hostService = mock(ClusterHostService.class);
        ServiceInstancePortResolver portResolver = mock(ServiceInstancePortResolver.class);
        ServiceRoleEndpointResolver resolver =
                new ServiceRoleEndpointResolver(roleService, hostService, portResolver);

        ClusterServiceRoleInstanceEntity running = role(7, "api-host", ServiceRoleState.RUNNING);
        ClusterServiceRoleInstanceEntity stopped = role(7, "old-host", ServiceRoleState.STOP);
        when(roleService.getServiceRoleInstanceListByClusterIdAndRoleName(7, "ApiServer"))
                .thenReturn(List.of(running, stopped));
        ClusterHostDO host = new ClusterHostDO();
        host.setClusterId(7);
        host.setIp("127.0.0.1");
        when(hostService.getClusterHostByHostname("api-host")).thenReturn(host);
        when(portResolver.portsOf(running)).thenReturn(List.of(
                new RolePort("rpcPort", "RPC", 1234),
                new RolePort("apiServerPort", "API", 12345)));

        HostPort result = resolver.resolve(
                7, "ApiServer", "apiServerPort", Set.of(ServiceRoleState.RUNNING));

        assertThat(result).isEqualTo(new HostPort("127.0.0.1", 12345));
    }

    private static ClusterServiceRoleInstanceEntity role(int clusterId, String hostname, ServiceRoleState state) {
        ClusterServiceRoleInstanceEntity role = new ClusterServiceRoleInstanceEntity();
        role.setClusterId(clusterId);
        role.setHostname(hostname);
        role.setServiceRoleState(state);
        return role;
    }
}
