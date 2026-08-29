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

package com.datasophon.api.ds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.ServiceInstancePortResolver;
import com.datasophon.api.service.ServiceInstancePortResolver.RolePort;
import com.datasophon.api.service.ServiceRoleEndpointResolver;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.ServiceRoleState;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

class DsEndpointResolverTest {

    @Test
    void resolvesApiServerThatIsHealthyButHasAlarm() {
        ClusterServiceRoleInstanceService roleService = mock(ClusterServiceRoleInstanceService.class);
        ClusterHostService hostService = mock(ClusterHostService.class);
        ServiceInstancePortResolver portResolver = mock(ServiceInstancePortResolver.class);
        DsEndpointResolver resolver = new DsEndpointResolver(
                new ServiceRoleEndpointResolver(roleService, hostService, portResolver));

        ClusterServiceRoleInstanceEntity role = new ClusterServiceRoleInstanceEntity();
        role.setClusterId(7);
        role.setHostname("ds-api-host");
        role.setServiceRoleState(ServiceRoleState.EXISTS_ALARM);
        when(roleService.getServiceRoleInstanceListByClusterIdAndRoleName(7, "ApiServer"))
                .thenReturn(List.of(role));
        ClusterHostDO host = new ClusterHostDO();
        host.setClusterId(7);
        host.setIp("127.0.0.1");
        when(hostService.getClusterHostByHostname("ds-api-host")).thenReturn(host);
        when(portResolver.portsOf(role)).thenReturn(List.of(new RolePort("apiServerPort", "API", 12345)));

        assertThat(resolver.resolve(7)).isEqualTo(URI.create("http://127.0.0.1:12345/dolphinscheduler/"));
    }
}
