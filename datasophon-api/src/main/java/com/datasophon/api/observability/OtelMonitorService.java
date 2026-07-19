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

import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.ServiceRoleState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

@Service
public class OtelMonitorService {

    private static final String ROLE_NAME = "OtelCollector";

    private final ClusterServiceRoleInstanceService roleInstanceService;
    private final ClusterHostService clusterHostService;
    private final OtelSelfMetricsClient metricsClient;

    public OtelMonitorService(ClusterServiceRoleInstanceService roleInstanceService,
                              ClusterHostService clusterHostService,
                              OtelSelfMetricsClient metricsClient) {
        this.roleInstanceService = roleInstanceService;
        this.clusterHostService = clusterHostService;
        this.metricsClient = metricsClient;
    }

    public List<NodeOtelMetrics> collectAll(Integer clusterId) {
        List<NodeOtelMetrics> result = new ArrayList<>();
        Map<String, String> hostIps = clusterHostService.getHostListByClusterId(clusterId).stream()
                .collect(Collectors.toMap(ClusterHostDO::getHostname, ClusterHostDO::getIp, (left, right) -> left));
        for (ClusterServiceRoleInstanceEntity role : roleInstanceService
                .getServiceRoleInstanceListByClusterIdAndRoleName(clusterId, ROLE_NAME)) {
            if (!ServiceRoleState.RUNNING.equals(role.getServiceRoleState())) {
                continue;
            }
            try {
                String ip = hostIps.get(role.getHostname());
                if (ip == null || ip.isBlank()) {
                    throw new IllegalStateException("host ip not found");
                }
                result.add(new NodeOtelMetrics(role.getHostname(), true, null,
                        metricsClient.fetch(ip)));
            } catch (RuntimeException e) {
                result.add(new NodeOtelMetrics(role.getHostname(), false, errorMessage(e), null));
            }
        }
        return result;
    }

    private static String errorMessage(RuntimeException e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
