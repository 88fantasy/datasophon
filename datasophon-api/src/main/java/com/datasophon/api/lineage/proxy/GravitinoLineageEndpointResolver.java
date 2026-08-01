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

/** Resolves the unique running GravitinoServer endpoint for one Datasophon cluster. */
@Component
public class GravitinoLineageEndpointResolver {

    static final String ROLE_NAME = "GravitinoServer";
    static final String HTTP_PORT_PARAM = "gravitino.server.webserver.httpPort";

    private final ClusterServiceRoleInstanceService roleService;
    private final ClusterHostService hostService;
    private final ServiceInstancePortResolver portResolver;

    public GravitinoLineageEndpointResolver(ClusterServiceRoleInstanceService roleService,
                                            ClusterHostService hostService,
                                            ServiceInstancePortResolver portResolver) {
        this.roleService = roleService;
        this.hostService = hostService;
        this.portResolver = portResolver;
    }

    public URI resolve(long clusterId) {
        if (clusterId <= 0 || clusterId > Integer.MAX_VALUE) {
            throw unavailable("invalid clusterId");
        }
        List<ClusterServiceRoleInstanceEntity> installed =
                roleService.getServiceRoleInstanceListByClusterIdAndRoleName((int) clusterId, ROLE_NAME);
        List<ClusterServiceRoleInstanceEntity> running = installed == null
                ? List.of()
                : installed.stream()
                        .filter(Objects::nonNull)
                        .filter(role -> ServiceRoleState.RUNNING.equals(role.getServiceRoleState()))
                        .toList();
        if (running.size() != 1) {
            throw unavailable(running.isEmpty()
                    ? "no running GravitinoServer instance"
                    : "multiple running GravitinoServer instances");
        }

        ClusterServiceRoleInstanceEntity role = running.get(0);
        ClusterHostDO host = hostService.getClusterHostByHostname(role.getHostname());
        if (host == null
                || !Objects.equals(role.getClusterId(), host.getClusterId())
                || StringUtils.isBlank(host.getIp())) {
            throw unavailable("GravitinoServer host is unavailable");
        }

        List<RolePort> ports = portResolver.portsOf(role).stream()
                .filter(port -> HTTP_PORT_PARAM.equals(port.paramName()))
                .toList();
        if (ports.size() != 1) {
            throw unavailable("GravitinoServer HTTP port is unavailable");
        }
        try {
            return new URI("http", null, host.getIp(), ports.get(0).port(), "/api/", null, null);
        } catch (URISyntaxException e) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "invalid GravitinoServer endpoint", e);
        }
    }

    private static ResponseStatusException unavailable(String message) {
        return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
