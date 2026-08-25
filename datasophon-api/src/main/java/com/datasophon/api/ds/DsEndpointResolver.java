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

import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.ServiceInstancePortResolver;
import com.datasophon.api.service.ServiceInstancePortResolver.RolePort;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.ServiceRoleState;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Resolves the usable DolphinScheduler ApiServer endpoint for one cluster. */
@Component
public class DsEndpointResolver {

    static final String ROLE_NAME = "ApiServer";
    static final String HTTP_PORT_PARAM = "apiServerPort";

    private final ClusterServiceRoleInstanceService roleService;
    private final ClusterHostService hostService;
    private final ServiceInstancePortResolver portResolver;

    public DsEndpointResolver(ClusterServiceRoleInstanceService roleService,
                              ClusterHostService hostService,
                              ServiceInstancePortResolver portResolver) {
        this.roleService = roleService;
        this.hostService = hostService;
        this.portResolver = portResolver;
    }

    public URI resolve(Integer clusterId) {
        if (clusterId == null || clusterId <= 0) {
            throw unavailable("clusterId 无效");
        }
        List<ClusterServiceRoleInstanceEntity> installed =
                roleService.getServiceRoleInstanceListByClusterIdAndRoleName(clusterId, ROLE_NAME);
        List<ClusterServiceRoleInstanceEntity> usable = installed == null
                ? List.of()
                : installed.stream()
                        .filter(Objects::nonNull)
                        .filter(DsEndpointResolver::isProcessHealthy)
                        .toList();
        if (usable.size() != 1) {
            throw unavailable(usable.isEmpty() ? "没有可用的 DS ApiServer" : "存在多个可用的 DS ApiServer");
        }

        ClusterServiceRoleInstanceEntity role = usable.get(0);
        ClusterHostDO host = hostService.getClusterHostByHostname(role.getHostname());
        if (host == null || !Objects.equals(clusterId, host.getClusterId()) || StringUtils.isBlank(host.getIp())) {
            throw unavailable("DS ApiServer 主机不可用");
        }
        List<RolePort> ports = portResolver.portsOf(role).stream()
                .filter(port -> HTTP_PORT_PARAM.equals(port.paramName()))
                .toList();
        if (ports.size() != 1) {
            throw unavailable("DS ApiServer 端口不可用");
        }
        try {
            return new URI("http", null, host.getIp(), ports.get(0).port(), "/dolphinscheduler/", null, null);
        } catch (URISyntaxException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "DS ApiServer 地址无效", e);
        }
    }

    private static boolean isProcessHealthy(ClusterServiceRoleInstanceEntity role) {
        return role.getServiceRoleState() == ServiceRoleState.RUNNING
                || role.getServiceRoleState() == ServiceRoleState.EXISTS_ALARM;
    }

    private static ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, message);
    }
}
