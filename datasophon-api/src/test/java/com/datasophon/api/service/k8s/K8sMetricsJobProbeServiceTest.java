package com.datasophon.api.service.k8s;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datasophon.api.observability.OtelDorisReaderFactory;
import com.datasophon.common.function.ThrowableMapper;
import com.datasophon.common.k8s.client.KubectlClient;
import com.datasophon.common.k8s.vo.k8s.K8sResourceList;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;

import java.util.Arrays;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * job 探测算法测试。
 *
 * <p>fixture 全部取自目标集群 2026-08-17 实测：Service 名来自
 * {@code kubectl get svc -l app.kubernetes.io/instance=<release>}，
 * job 名来自 Doris {@code SELECT DISTINCT service_name}。
 */
class K8sMetricsJobProbeServiceTest {

    /** Doris 中近期有数据的 job（P0-4 实测，去掉与本测试无关项）。 */
    private static final Set<String> ACTIVE_JOBS = Set.of(
            "apisix-prometheus-metrics",
            "dolphinscheduler-api",
            "dolphinscheduler-master-headless",
            "dolphinscheduler-worker-headless",
            "kyuubi-headless",
            "spark/kyuubi",
            "zookeeper-metrics",
            "node");

    @Test
    @DisplayName("一个服务对应多个 job 时全部登记，逗号分隔")
    void probesAllJobsOfMultiComponentService() {
        String jobs = probe("dolphinscheduler", "prod",
                "dolphinscheduler-api", "dolphinscheduler-master-headless", "dolphinscheduler-worker-headless");

        assertThat(jobs).isEqualTo(
                "dolphinscheduler-api,dolphinscheduler-master-headless,dolphinscheduler-worker-headless");
    }

    @Test
    @DisplayName("只取真正被采集的 Service，普通 Service 与 headless 被交集滤掉")
    void keepsOnlyScrapedService() {
        String jobs = probe("zookeeper", "prod", "zookeeper", "zookeeper-headless", "zookeeper-metrics");

        assertThat(jobs).isEqualTo("zookeeper-metrics");
    }

    @Test
    @DisplayName("Kyuubi 被两个 SM 重复采集时只取 Service 名那个，避免数据翻倍")
    void avoidsDuplicateScrapeJob() {
        // spark/kyuubi 也在 ACTIVE_JOBS 里，但它不是 Service 名，交集天然排除
        String jobs = probe("kyuubi", "spark", "kyuubi-headless", "kyuubi-rest", "kyuubi-thrift-binary");

        assertThat(jobs).isEqualTo("kyuubi-headless");
        assertThat(jobs).doesNotContain("spark/kyuubi");
    }

    @Test
    @DisplayName("未接入采集的服务返回 null，供上层标记为「未接入采集」")
    void returnsNullWhenServiceNotScraped() {
        // Redis 实测无 exporter、无 ServiceMonitor
        String jobs = probe("redis-cluster", "prod", "redis-cluster", "redis-cluster-headless");

        assertThat(jobs).isNull();
    }

    @Test
    @DisplayName("release 查不到任何 Service 时返回 null，不抛异常")
    void returnsNullWhenNoServiceFound() {
        assertThat(probe("ustream", "prod")).isNull();
    }

    private String probe(String releaseName, String namespace, String... serviceNames) {
        KubectlClient client = mock(KubectlClient.class);
        try {
            K8sResourceList<com.datasophon.common.k8s.vo.k8s.K8sService> list = new K8sResourceList<>();
            list.setItems(Arrays.stream(serviceNames).map(K8sMetricsJobProbeServiceTest::service).toList());
            when(client.getServices(eq(namespace), anyString())).thenReturn(list);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        com.datasophon.api.service.k8s.K8sService k8sService =
                mock(com.datasophon.api.service.k8s.K8sService.class);
        when(k8sService.batchExec(any(), any(), anyString())).thenAnswer(invocation -> {
            ThrowableMapper<KubectlClient, Object> mapper = invocation.getArgument(1);
            return mapper.accept(client);
        });

        K8sMetricsJobProbeService probeService =
                new K8sMetricsJobProbeService(k8sService, mock(OtelDorisReaderFactory.class));
        return probeService.probe(new K8sClusterConfig(), releaseName, namespace, ACTIVE_JOBS);
    }

    private static com.datasophon.common.k8s.vo.k8s.K8sService service(String name) {
        com.datasophon.common.k8s.vo.k8s.K8sService svc = new com.datasophon.common.k8s.vo.k8s.K8sService();
        com.datasophon.common.k8s.vo.k8s.K8sService.Metadata metadata =
                new com.datasophon.common.k8s.vo.k8s.K8sService.Metadata();
        metadata.setName(name);
        svc.setMetadata(metadata);
        return svc;
    }
}
