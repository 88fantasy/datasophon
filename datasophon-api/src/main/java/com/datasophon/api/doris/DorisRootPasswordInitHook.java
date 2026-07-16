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

import com.datasophon.api.hook.ServiceHook;
import com.datasophon.api.hook.ServiceHookContext;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.ClusterVariableService;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.entity.ClusterVariable;
import com.datasophon.dao.enums.ServiceRoleState;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DorisRootPasswordInitHook implements ServiceHook {

    private static final String DORIS_SERVICE = "DORIS";
    private static final String DORIS_FE = "DorisFE";
    private static final int DEFAULT_QUERY_PORT = 9030;
    private static final int DEFAULT_MAX_ATTEMPTS = 24;
    private static final long DEFAULT_INTERVAL_MS = 5000L;

    private final DorisReadinessService readinessService;
    private final DorisSqlOperations dorisSqlOperations;
    private final ClusterServiceRoleInstanceService roleService;
    private final ClusterVariableService variableService;

    @Override
    public String getType() {
        return "dorisRootPasswordInit";
    }

    @Override
    public boolean isReady(ServiceHookContext context) {
        return context.getClusterId() != null && readinessService.waitUntilClusterReady(context.getClusterId(),
                maxAttempts(context), intervalMs(context));
    }

    @Override
    public void invoke(ServiceHookContext context) throws Exception {
        Integer clusterId = context.getClusterId();
        if (clusterId == null) {
            return;
        }
        String targetPassword = requireValue(variable(clusterId, "root_password"),
                "Doris root_password is not configured for cluster " + clusterId);
        List<ClusterServiceRoleInstanceEntity> frontends = roleService
                .getServiceRoleInstanceListByClusterIdAndRoleName(clusterId, DORIS_FE).stream()
                .filter(role -> ServiceRoleState.RUNNING.equals(role.getServiceRoleState()))
                .toList();
        if (frontends.isEmpty()) {
            return;
        }
        String feHost = frontends.get(0).getHostname();
        int port = queryPort(clusterId);
        if (dorisSqlOperations.canConnect(feHost, port, targetPassword)) {
            log.info("Doris root 密码已是目标值,无需重置");
            return;
        }
        if (dorisSqlOperations.canConnect(feHost, port, "")) {
            dorisSqlOperations.resetRootAndAdminPassword(feHost, port, "", targetPassword);
            log.info("Doris root/admin 密码初始化成功");
            return;
        }
        throw new IllegalStateException("Doris root 密码初始化失败: FE=" + feHost + " 目标密码与空密码均无法连接");
    }

    private int maxAttempts(ServiceHookContext context) {
        return numberParam(context.getParams(), "maxAttempts", DEFAULT_MAX_ATTEMPTS).intValue();
    }

    private long intervalMs(ServiceHookContext context) {
        return numberParam(context.getParams(), "intervalMs", DEFAULT_INTERVAL_MS).longValue();
    }

    private Number numberParam(Map<String, Object> params, String name, Number defaultValue) {
        Object value = params == null ? null : params.get(name);
        if (value instanceof Number number) {
            return number;
        }
        if (value instanceof String string && !string.isBlank()) {
            try {
                return Long.parseLong(string);
            } catch (NumberFormatException ignored) {
                log.warn("Doris hook 参数 {}={} 非法,使用默认值 {}", name, value, defaultValue);
            }
        }
        return defaultValue;
    }

    private int queryPort(Integer clusterId) {
        String value = variable(clusterId, "query_port");
        return value == null || value.isBlank() ? DEFAULT_QUERY_PORT : Integer.parseInt(value);
    }

    private String variable(Integer clusterId, String name) {
        ClusterVariable value = variableService.getVariableByVariableName(clusterId, DORIS_SERVICE, name);
        return value == null ? null : value.getVariableValue();
    }

    private String requireValue(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(message);
        }
        return value;
    }
}
