package com.datasophon.api.service.k8s;

import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/** 将 K8s 指标 DaemonSet 幂等应用到已初始化的受管集群。 */
@Service
public class K8sDashboardCollectorService {
    private final K8sClusterConfigService configService;
    private final K8sService k8sService;

    public K8sDashboardCollectorService(K8sClusterConfigService configService, K8sService k8sService) {
        this.configService = configService;
        this.k8sService = k8sService;
    }

    @Async("masterExecutor")
    public void install(Integer clusterId) {
        K8sClusterConfig config = configService.getInitConfig(clusterId);
        k8sService.applyYaml(config, manifest(clusterId));
    }

    private String manifest(Integer clusterId) {
        try (var input = new ClassPathResource("k8s/datasophon-k8s-metrics-collector.yaml").getInputStream()) {
            String yaml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return yaml.replace("value: \"local\"", "value: \"" + clusterId + "\"");
        } catch (IOException e) {
            throw new IllegalStateException("读取 K8s 指标采集器清单失败", e);
        }
    }
}
