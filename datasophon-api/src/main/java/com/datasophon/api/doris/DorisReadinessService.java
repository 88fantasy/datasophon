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

package com.datasophon.api.doris;

import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.ClusterVariableService;
import com.datasophon.common.model.ProcInfo;
import com.datasophon.common.utils.OlapUtils;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.entity.ClusterVariable;
import com.datasophon.dao.enums.ServiceRoleState;

import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DorisReadinessService {

    private static final String DORIS_SERVICE = "DORIS";
    private static final String DORIS_FE = "DorisFE";
    private static final String DORIS_BE = "DorisBE";

    private final ClusterServiceRoleInstanceService roleService;
    private final ClusterVariableService variableService;

    public boolean waitUntilClusterReady(Integer clusterId, int maxAttempts, long intervalMs) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            List<ClusterServiceRoleInstanceEntity> frontends = runningRoles(clusterId, DORIS_FE);
            List<ClusterServiceRoleInstanceEntity> backends = runningRoles(clusterId, DORIS_BE);
            if (!frontends.isEmpty() && !backends.isEmpty()) {
                try {
                    List<ProcInfo> registeredBackends = showBackends(frontends.get(0).getHostname(),
                            variable(clusterId, "root_password"));
                    if (registeredBackends.size() >= backends.size()
                            && registeredBackends.stream().allMatch(backend -> Boolean.TRUE.equals(backend.getAlive()))) {
                        return true;
                    }
                } catch (Exception e) {
                    log.info("cluster {} 尚未就绪(attempt {}/{}): {}", clusterId, attempt, maxAttempts, e.getMessage());
                }
            }
            if (attempt < maxAttempts && !sleep(intervalMs)) {
                return false;
            }
        }
        return false;
    }

    protected List<ProcInfo> showBackends(String feHost, String rootPassword) throws Exception {
        return OlapUtils.showBackends(feHost, rootPassword);
    }

    private List<ClusterServiceRoleInstanceEntity> runningRoles(Integer clusterId, String roleName) {
        return roleService.getServiceRoleInstanceListByClusterIdAndRoleName(clusterId, roleName).stream()
                .filter(role -> ServiceRoleState.RUNNING.equals(role.getServiceRoleState()))
                .toList();
    }

    private String variable(Integer clusterId, String name) {
        ClusterVariable value = variableService.getVariableByVariableName(clusterId, DORIS_SERVICE, name);
        return value == null ? null : value.getVariableValue();
    }

    private boolean sleep(long intervalMs) {
        try {
            Thread.sleep(intervalMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
