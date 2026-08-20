package com.datasophon.api.service.k8s;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.api.observability.OtelDorisReaderFactory;
import com.datasophon.api.security.K8sTakeoverAccessGuard;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.api.vo.k8s.DorisDatasourceCandidate;
import com.datasophon.common.function.ThrowableMapper;
import com.datasophon.common.k8s.client.KubectlClient;
import com.datasophon.common.k8s.vo.k8s.K8sNode;
import com.datasophon.common.k8s.vo.k8s.K8sResourceList;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Doris 数据源发现测试。
 *
 * <p>fixture 对齐目标集群 {@code doris} 命名空间的真实 Service 形态（2026-08-17 实测）：
 * {@code fe} 是 ClusterIP，{@code fe-outside} 是 LoadBalancer 但 ingress 为空。
 */
class DorisDatasourceDiscoveryServiceTest {

    @Test
    @DisplayName("LoadBalancer 已分配 ingress 时直接可用")
    void usesLoadBalancerIngressWhenAssigned() {
        com.datasophon.common.k8s.vo.k8s.K8sService svc =
                service("doris-fe-outside", "doris", "LoadBalancer", 9030, 30302);
        svc.setStatus(loadBalancerStatus("203.0.113.10"));

        List<DorisDatasourceCandidate> candidates = discover(List.of(svc));

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).host()).isEqualTo("203.0.113.10");
        assertThat(candidates.get(0).port()).isEqualTo(9030);
        assertThat(candidates.get(0).source()).isEqualTo(DorisDatasourceCandidate.SOURCE_LOAD_BALANCER);
        assertThat(candidates.get(0).reachable()).isTrue();
    }

    @Test
    @DisplayName("LoadBalancer 未分配 ingress 但有 NodePort 时，回落到节点地址并给出提示")
    void fallsBackToNodePortWhenIngressMissing() {
        // 目标集群实测形态：类型是 LoadBalancer，status.loadBalancer.ingress 为空
        com.datasophon.common.k8s.vo.k8s.K8sService svc =
                service("doris-fe-outside", "doris", "LoadBalancer", 9030, 30302);

        List<DorisDatasourceCandidate> candidates = discover(List.of(svc));

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).host()).isEqualTo("192.168.201.7");
        assertThat(candidates.get(0).port()).isEqualTo(30302);
        assertThat(candidates.get(0).source()).isEqualTo(DorisDatasourceCandidate.SOURCE_NODE_PORT);
        assertThat(candidates.get(0).hint()).contains("对外地址");
    }

    @Test
    @DisplayName("纯 ClusterIP 标记为不可达，提示手工填写")
    void marksClusterIpUnreachable() {
        com.datasophon.common.k8s.vo.k8s.K8sService svc =
                service("doris-fe", "doris", "ClusterIP", 9030, null);

        List<DorisDatasourceCandidate> candidates = discover(List.of(svc));

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).reachable()).isFalse();
        assertThat(candidates.get(0).source()).isEqualTo(DorisDatasourceCandidate.SOURCE_CLUSTER_IP);
        assertThat(candidates.get(0).hint()).contains("手工填写");
    }

    @Test
    @DisplayName("不暴露 9030 的 Service 不作为候选")
    void ignoresServiceWithoutMysqlPort() {
        List<DorisDatasourceCandidate> candidates = discover(List.of(
                service("doris-fe", "doris", "ClusterIP", 8030, null),
                service("apisix", "apisix", "ClusterIP", 80, null)));

        assertThat(candidates).isEmpty();
    }

    @Test
    @DisplayName("Doris JDBC URL 固定连接 otel 数据库")
    void jdbcUrlUsesOtelDatabase() {
        assertThat(DorisDatasourceDiscoveryService.jdbcUrl("doris.example", 9030))
                .startsWith("jdbc:mysql://doris.example:9030/otel?");
    }

    @Test
    @DisplayName("保存数据源时固定 otel 数据库并保存 otel_reader 密码")
    void savesFixedReaderContract() {
        K8sClusterConfigService configService = mock(K8sClusterConfigService.class);
        DorisDatasourcePersistenceService persistenceService = mock(DorisDatasourcePersistenceService.class);
        OtelDorisReaderFactory readerFactory = mock(OtelDorisReaderFactory.class);
        K8sClusterConfig config = new K8sClusterConfig();
        config.setClusterId(7);
        when(configService.getByClusterId(7)).thenReturn(config);
        DorisDatasourceDiscoveryService service = spy(new DorisDatasourceDiscoveryService(
                mock(com.datasophon.api.service.k8s.K8sService.class), configService, persistenceService,
                readerFactory, mock(K8sTakeoverAccessGuard.class)));
        doReturn(null).when(service).testConnection("doris.example", 9030, "reader-secret");

        service.saveDatasource(7, "doris.example", 9030, "reader-secret");

        assertThat(config.getDorisHost()).isEqualTo("doris.example");
        assertThat(config.getDorisPort()).isEqualTo(9030);
        assertThat(config.getDorisDatabase()).isEqualTo("otel");
        verify(persistenceService).save(config, "reader-secret");
        verify(readerFactory).invalidate(7);
    }

    @SuppressWarnings("unchecked")
    private List<DorisDatasourceCandidate> discover(
                                                    List<com.datasophon.common.k8s.vo.k8s.K8sService> services) {
        K8sClusterConfigService configService = mock(K8sClusterConfigService.class);
        when(configService.getByClusterId(7)).thenReturn(new K8sClusterConfig());

        KubectlClient client = mock(KubectlClient.class);
        try {
            K8sResourceList<com.datasophon.common.k8s.vo.k8s.K8sService> svcList = new K8sResourceList<>();
            svcList.setItems(services);
            when(client.getServicesAllNamespaces()).thenReturn(svcList);
            K8sResourceList<K8sNode> nodeList = new K8sResourceList<>();
            nodeList.setItems(List.of(node("192.168.201.7")));
            when(client.getNodes()).thenReturn(nodeList);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        com.datasophon.api.service.k8s.K8sService k8sService =
                mock(com.datasophon.api.service.k8s.K8sService.class);
        when(k8sService.batchExec(any(), any(), anyString())).thenAnswer(invocation -> {
            ThrowableMapper<KubectlClient, Object> mapper = invocation.getArgument(1);
            return mapper.accept(client);
        });

        return new DorisDatasourceDiscoveryService(
                k8sService, configService, mock(DorisDatasourcePersistenceService.class),
                mock(OtelDorisReaderFactory.class), mock(K8sTakeoverAccessGuard.class)).discover(7);
    }

    private static com.datasophon.common.k8s.vo.k8s.K8sService service(
                                                                       String name, String namespace, String type,
                                                                       int port, Integer nodePort) {
        com.datasophon.common.k8s.vo.k8s.K8sService svc = new com.datasophon.common.k8s.vo.k8s.K8sService();
        com.datasophon.common.k8s.vo.k8s.K8sService.Metadata metadata =
                new com.datasophon.common.k8s.vo.k8s.K8sService.Metadata();
        metadata.setName(name);
        metadata.setNamespace(namespace);
        svc.setMetadata(metadata);

        com.datasophon.common.k8s.vo.k8s.K8sService.ServiceSpec spec =
                new com.datasophon.common.k8s.vo.k8s.K8sService.ServiceSpec();
        spec.setType(type);
        spec.setClusterIP("192.168.203.136");
        com.datasophon.common.k8s.vo.k8s.K8sService.ServicePort servicePort =
                new com.datasophon.common.k8s.vo.k8s.K8sService.ServicePort();
        servicePort.setPort(port);
        servicePort.setNodePort(nodePort);
        spec.setPorts(List.of(servicePort));
        svc.setSpec(spec);
        return svc;
    }

    private static com.datasophon.common.k8s.vo.k8s.K8sService.ServiceStatus loadBalancerStatus(String ip) {
        com.datasophon.common.k8s.vo.k8s.K8sService.LoadBalancerIngress ingress =
                new com.datasophon.common.k8s.vo.k8s.K8sService.LoadBalancerIngress();
        ingress.setIp(ip);
        com.datasophon.common.k8s.vo.k8s.K8sService.LoadBalancerStatus lb =
                new com.datasophon.common.k8s.vo.k8s.K8sService.LoadBalancerStatus();
        lb.setIngress(List.of(ingress));
        com.datasophon.common.k8s.vo.k8s.K8sService.ServiceStatus status =
                new com.datasophon.common.k8s.vo.k8s.K8sService.ServiceStatus();
        status.setLoadBalancer(lb);
        return status;
    }

    private static K8sNode node(String internalIp) {
        K8sNode node = new K8sNode();
        K8sNode.NodeStatus status = new K8sNode.NodeStatus();
        K8sNode.NodeAddress address = new K8sNode.NodeAddress();
        address.setType("InternalIP");
        address.setAddress(internalIp);
        status.setAddresses(List.of(address));
        node.setStatus(status);
        return node;
    }
}
