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

import com.datasophon.api.dto.ApiResponse;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.api.service.k8s.K8sService;
import com.datasophon.api.vo.k8s.K8sConnectionResult;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClusterK8sConnectionConfigV2ControllerTest {

    private K8sService k8sService;
    private K8sClusterConfigService k8sClusterConfigService;
    private ClusterK8sConnectionConfigV2Controller controller;

    @BeforeEach
    void setUp() {
        k8sService = proxy(K8sService.class, (p, method, args) -> null);
        k8sClusterConfigService = proxy(K8sClusterConfigService.class, (p, method, args) -> null);
        controller = new ClusterK8sConnectionConfigV2Controller(k8sService, k8sClusterConfigService);
    }

    @Test
    void getConfigByClusterId_returnsExistingConfig() {
        K8sClusterConfig config = new K8sClusterConfig();
        config.setClusterId(1);
        config.setServerHost("https://k8s.example:6443");
        k8sClusterConfigService = proxy(K8sClusterConfigService.class, (p, method, args) -> {
            if ("getByClusterId".equals(method.getName())) {
                return config;
            }
            return null;
        });
        controller = new ClusterK8sConnectionConfigV2Controller(k8sService, k8sClusterConfigService);

        ApiResponse<K8sClusterConfig> response = controller.getConfigByClusterId(1);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isSameAs(config);
    }

    @Test
    void testConnection_delegatesToK8sService() {
        K8sClusterConfig config = new K8sClusterConfig();
        K8sConnectionResult result = new K8sConnectionResult();
        result.setSuccess(true);
        k8sService = proxy(K8sService.class, (p, method, args) -> {
            if ("testConnection".equals(method.getName())) {
                assertThat(args[0]).isSameAs(config);
                return result;
            }
            return null;
        });
        controller = new ClusterK8sConnectionConfigV2Controller(k8sService, k8sClusterConfigService);

        ApiResponse<K8sConnectionResult> response = controller.testConnection(config);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isSameAs(result);
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
        controller = new ClusterK8sConnectionConfigV2Controller(k8sService, k8sClusterConfigService);

        ApiResponse<K8sClusterConfig> response = controller.saveOrUpdateConfig(config);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isSameAs(config);
        assertThat(saved.get()).isSameAs(config);
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
