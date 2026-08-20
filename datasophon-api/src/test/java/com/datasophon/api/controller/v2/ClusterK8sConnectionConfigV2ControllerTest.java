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

package com.datasophon.api.controller.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.datasophon.api.dto.ApiResponse;
import com.datasophon.api.security.ClusterAccessGuard;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.api.vo.k8s.K8sClusterConfigVO;
import com.datasophon.api.vo.k8s.K8sConnectionResult;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.enums.k8s.K8sAuthType;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class ClusterK8sConnectionConfigV2ControllerTest {

    private K8sClusterConfigService k8sClusterConfigService;
    private ClusterAccessGuard clusterAccessGuard;
    private ClusterK8sConnectionConfigV2Controller controller;

    @BeforeEach
    void setUp() {
        k8sClusterConfigService = proxy(K8sClusterConfigService.class, (p, method, args) -> null);
        clusterAccessGuard = mock(ClusterAccessGuard.class);
        controller = new ClusterK8sConnectionConfigV2Controller(k8sClusterConfigService, clusterAccessGuard);
    }

    @Test
    void getConfigByClusterId_returnsExistingConfigWithoutSecrets() throws Exception {
        K8sClusterConfig config = new K8sClusterConfig();
        config.setClusterId(1);
        config.setType(K8sAuthType.token);
        config.setServerHost("https://k8s.example:6443");
        config.setToken("secret-token");
        k8sClusterConfigService = proxy(K8sClusterConfigService.class, (p, method, args) -> {
            if ("getByClusterId".equals(method.getName())) {
                return config;
            }
            return null;
        });
        controller = new ClusterK8sConnectionConfigV2Controller(k8sClusterConfigService, clusterAccessGuard);

        ApiResponse<K8sClusterConfigVO> response = controller.getConfigByClusterId(1);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getServerHost()).isEqualTo("https://k8s.example:6443");
        assertThat(response.getData().isCredentialConfigured()).isTrue();
        JsonNode json = new ObjectMapper().valueToTree(response.getData());
        assertThat(json.has("token")).isFalse();
        assertThat(json.has("password")).isFalse();
        assertThat(json.has("kubeConfig")).isFalse();
        verify(clusterAccessGuard).requireAccess(1);
    }

    @Test
    void testConnection_delegatesToK8sService() {
        K8sClusterConfig config = new K8sClusterConfig();
        K8sConnectionResult result = new K8sConnectionResult();
        result.setSuccess(true);
        k8sClusterConfigService = proxy(K8sClusterConfigService.class, (p, method, args) -> {
            if ("testConnection".equals(method.getName())) {
                assertThat(args[0]).isSameAs(config);
                return result;
            }
            return null;
        });
        controller = new ClusterK8sConnectionConfigV2Controller(k8sClusterConfigService, clusterAccessGuard);

        ApiResponse<K8sConnectionResult> response = controller.testConnection(config);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isSameAs(result);
        verify(clusterAccessGuard).requireAccess(config.getClusterId());
    }

    @Test
    void saveOrUpdateConfig_delegatesToConfigService() {
        K8sClusterConfig config = new K8sClusterConfig();
        config.setClusterId(1);
        AtomicReference<K8sClusterConfig> saved = new AtomicReference<>();
        k8sClusterConfigService = proxy(K8sClusterConfigService.class, (p, method, args) -> {
            if ("saveOrUpdateConfig".equals(method.getName())) {
                saved.set((K8sClusterConfig) args[0]);
                return config;
            }
            return null;
        });
        controller = new ClusterK8sConnectionConfigV2Controller(k8sClusterConfigService, clusterAccessGuard);

        ApiResponse<K8sClusterConfigVO> response = controller.saveOrUpdateConfig(config);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getClusterId()).isEqualTo(1);
        assertThat(saved.get()).isSameAs(config);
        verify(clusterAccessGuard).requireAccess(1);
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> type.getSimpleName() + "Proxy";
                    case "hashCode" -> System.identityHashCode(p);
                    case "equals" -> p == args[0];
                    default -> null;
                };
            }
            return handler.invoke(p, method, args);
        });
        return type.cast(proxy);
    }
}
