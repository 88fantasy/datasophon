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
import com.datasophon.api.dto.v2.HostPageResponse;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.api.service.k8s.K8sService;
import com.datasophon.common.k8s.vo.k8s.K8sNode;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.enums.ClusterArchType;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

class ClusterHostV2ControllerTest {

    @Test
    void list_k8sCluster_returnsK8sNodes() {
        AtomicBoolean physicalHostQueried = new AtomicBoolean(false);
        ClusterHostService clusterHostService = proxy(ClusterHostService.class, (p, method, args) -> {
            if ("listByPage".equals(method.getName())) {
                physicalHostQueried.set(true);
            }
            return null;
        });
        ClusterInfoService clusterInfoService = proxy(ClusterInfoService.class, (p, method, args) -> {
            if ("getById".equals(method.getName())) {
                ClusterInfoEntity cluster = new ClusterInfoEntity();
                cluster.setId(7);
                cluster.setArchType(ClusterArchType.k8s);
                return cluster;
            }
            return null;
        });
        K8sClusterConfig config = new K8sClusterConfig();
        config.setClusterId(7);
        K8sClusterConfigService configService = proxy(K8sClusterConfigService.class, (p, method, args) -> config);
        K8sService k8sService = proxy(K8sService.class, (p, method, args) -> {
            if ("listNodes".equals(method.getName())) {
                return List.of(k8sNode("k8s-node-1", "10.0.0.7"));
            }
            return null;
        });
        ClusterHostV2Controller controller =
                new ClusterHostV2Controller(clusterHostService, clusterInfoService, configService, k8sService);

        ApiResponse<HostPageResponse> response =
                controller.list(7, 1, 20, null, null, null, null, null, null);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getTotal()).isEqualTo(1);
        assertThat(response.getData().getRecords()).hasSize(1);
        assertThat(response.getData().getRecords().get(0).getHostname()).isEqualTo("k8s-node-1");
        assertThat(physicalHostQueried).isFalse();
    }

    private static K8sNode k8sNode(String name, String ip) {
        K8sNode node = new K8sNode();
        K8sNode.Metadata metadata = new K8sNode.Metadata();
        metadata.setName(name);
        node.setMetadata(metadata);
        K8sNode.NodeStatus status = new K8sNode.NodeStatus();
        K8sNode.NodeAddress address = new K8sNode.NodeAddress();
        address.setType("InternalIP");
        address.setAddress(ip);
        status.setAddresses(List.of(address));
        K8sNode.NodeCondition ready = new K8sNode.NodeCondition();
        ready.setType("Ready");
        ready.setStatus("True");
        status.setConditions(List.of(ready));
        node.setStatus(status);
        return node;
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
