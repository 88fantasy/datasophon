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

import com.datasophon.api.service.ServiceInstancePortResolver.RolePort;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.ServiceRoleState;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;

/** Resolves a unique usable service role to its configured host and port. */
@Component
public class ServiceRoleEndpointResolver {

    private final ClusterServiceRoleInstanceService roleService;
    private final ClusterHostService hostService;
    private final ServiceInstancePortResolver portResolver;

    public ServiceRoleEndpointResolver(ClusterServiceRoleInstanceService roleService,
                                       ClusterHostService hostService,
                                       ServiceInstancePortResolver portResolver) {
        this.roleService = roleService;
        this.hostService = hostService;
        this.portResolver = portResolver;
    }

    public HostPort resolve(int clusterId,
                            String roleName,
                            String portParamName,
                            Set<ServiceRoleState> acceptedStates) {
        List<ClusterServiceRoleInstanceEntity> installed =
                roleService.getServiceRoleInstanceListByClusterIdAndRoleName(clusterId, roleName);
        List<ClusterServiceRoleInstanceEntity> usable = installed == null
                ? List.of()
                : installed.stream()
                        .filter(Objects::nonNull)
                        .filter(role -> acceptedStates.contains(role.getServiceRoleState()))
                        .toList();
        if (usable.isEmpty()) {
            throw new EndpointResolutionException(Failure.NO_USABLE_ROLE);
        }
        if (usable.size() > 1) {
            throw new EndpointResolutionException(Failure.MULTIPLE_USABLE_ROLES);
        }

        ClusterServiceRoleInstanceEntity role = usable.get(0);
        ClusterHostDO host = hostService.getClusterHostByHostname(role.getHostname());
        if (host == null
                || !Objects.equals(clusterId, host.getClusterId())
                || StringUtils.isBlank(host.getIp())) {
            throw new EndpointResolutionException(Failure.HOST_UNAVAILABLE);
        }
        List<RolePort> ports = portResolver.portsOf(role).stream()
                .filter(port -> portParamName.equals(port.paramName()))
                .toList();
        if (ports.size() != 1) {
            throw new EndpointResolutionException(Failure.PORT_UNAVAILABLE);
        }
        return new HostPort(host.getIp(), ports.get(0).port());
    }

    public record HostPort(String host, int port) {
    }

    public enum Failure {
        NO_USABLE_ROLE,
        MULTIPLE_USABLE_ROLES,
        HOST_UNAVAILABLE,
        PORT_UNAVAILABLE
    }

    public static final class EndpointResolutionException extends RuntimeException {

        private final Failure failure;

        private EndpointResolutionException(Failure failure) {
            super(failure.name());
            this.failure = failure;
        }

        public Failure failure() {
            return failure;
        }
    }
}
