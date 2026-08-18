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
import com.datasophon.common.model.k8s.K8sOperatorArtifact;
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

    @Test
    @DisplayName("operatorArtifact 非空时按 name-prefix 定位 Service，并按角色分桶")
    void probesByNamePrefixAndBucketsByRole() {
        // 沙箱实测样例：Doris CR 名 doris-disaggregated-cluster，Service 前缀命中 -fe/-cg1/-cg2，
        // -ms 因不产指标不在 ACTIVE_JOBS 里被交集自然排除
        Set<String> activeJobs = Set.of(
                "doris-disaggregated-cluster-fe",
                "doris-disaggregated-cluster-cg1",
                "doris-disaggregated-cluster-cg2");
        K8sOperatorArtifact operator = new K8sOperatorArtifact();
        K8sOperatorArtifact.Role fe = new K8sOperatorArtifact.Role();
        fe.setName("fe");
        fe.setJobPattern("-fe$");
        K8sOperatorArtifact.Role compute = new K8sOperatorArtifact.Role();
        compute.setName("compute");
        compute.setJobPattern("-cg\\d+$");
        operator.setRoles(java.util.List.of(fe, compute));

        K8sMetricsJobProbeService.ProbeResult result = probeByPrefix(
                "doris-disaggregated-cluster", "doris", activeJobs, operator,
                "doris-disaggregated-cluster-fe", "doris-disaggregated-cluster-cg1",
                "doris-disaggregated-cluster-cg2", "doris-disaggregated-cluster-ms");

        // metricsJob 按 kubectl 返回的 Service 遍历顺序拼接（LinkedHashSet，与既有 Helm 分支一致）
        assertThat(result.metricsJob()).isEqualTo(
                "doris-disaggregated-cluster-fe,doris-disaggregated-cluster-cg1,doris-disaggregated-cluster-cg2");
        assertThat(result.roleJobs()).containsEntry("fe", java.util.List.of("doris-disaggregated-cluster-fe"));
        assertThat(result.roleJobs()).containsEntry("compute", java.util.List.of(
                "doris-disaggregated-cluster-cg1", "doris-disaggregated-cluster-cg2"));
    }

    @Test
    @DisplayName("name-prefix 用前缀+连字符匹配，不会误吃同前缀不同实体的 Service（nacos 不吃 nacosxyz）")
    void namePrefixDoesNotMatchUnrelatedServiceWithSamePrefix() {
        Set<String> activeJobs = Set.of("nacos", "nacosxyz");
        K8sMetricsJobProbeService.ProbeResult result = probeByPrefix(
                "nacos", "prod", activeJobs, new K8sOperatorArtifact(), "nacos", "nacosxyz");

        assertThat(result.metricsJob()).isEqualTo("nacos");
        assertThat(result.metricsJob()).doesNotContain("nacosxyz");
    }

    @Test
    @DisplayName("operatorArtifact 为 null 时回退走原 Helm 标签分支")
    void fallsBackToHelmLabelBranchWhenOperatorArtifactIsNull() {
        KubectlClient client = mock(KubectlClient.class);
        try {
            K8sResourceList<com.datasophon.common.k8s.vo.k8s.K8sService> list = new K8sResourceList<>();
            list.setItems(Arrays.stream(new String[]{"zookeeper-metrics"})
                    .map(K8sMetricsJobProbeServiceTest::service).toList());
            when(client.getServices(eq("prod"), anyString())).thenReturn(list);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        com.datasophon.api.service.k8s.K8sService k8sService =
                mock(com.datasophon.api.service.k8s.K8sService.class);
        when(k8sService.batchExec(any(), any(), anyString())).thenAnswer(invocation -> {
            ThrowableMapper<KubectlClient, Object> mapper = invocation.getArgument(1);
            return mapper.accept(client);
        });

        K8sMetricsJobProbeService.ProbeResult result = new K8sMetricsJobProbeService(
                k8sService, mock(OtelDorisReaderFactory.class))
                .probe(new K8sClusterConfig(), "zookeeper", "prod", ACTIVE_JOBS, null);

        assertThat(result.metricsJob()).isEqualTo("zookeeper-metrics");
        assertThat(result.roleJobs()).isEmpty();
        // 走的是 -l 标签查询分支，而不是 name-prefix 的 null selector 分支
        try {
            org.mockito.Mockito.verify(client).getServices(eq("prod"), anyString());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private K8sMetricsJobProbeService.ProbeResult probeByPrefix(String releaseName, String namespace,
                                                                Set<String> activeJobs,
                                                                K8sOperatorArtifact operator,
                                                                String... serviceNames) {
        KubectlClient client = mock(KubectlClient.class);
        try {
            K8sResourceList<com.datasophon.common.k8s.vo.k8s.K8sService> list = new K8sResourceList<>();
            list.setItems(Arrays.stream(serviceNames).map(K8sMetricsJobProbeServiceTest::service).toList());
            when(client.getServices(eq(namespace), org.mockito.ArgumentMatchers.isNull())).thenReturn(list);
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
        return probeService.probe(new K8sClusterConfig(), releaseName, namespace, activeJobs, operator);
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
