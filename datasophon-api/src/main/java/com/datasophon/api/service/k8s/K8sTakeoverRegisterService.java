package com.datasophon.api.service.k8s;

import com.datasophon.api.dto.instance.K8sNamespaceIdentityDTO;
import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.api.service.cluster.K8sClusterNamespaceService;
import com.datasophon.api.service.frame.FrameK8sServiceService;
import com.datasophon.api.service.instance.K8sServiceInstanceService;
import com.datasophon.api.vo.k8s.K8sTakeoverRegisterResult;
import com.datasophon.common.model.k8s.K8sArtifact;
import com.datasophon.common.model.k8s.K8sOperatorArtifact;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.entity.cluster.K8sClusterNamespace;
import com.datasophon.dao.entity.frame.FrameK8sServiceEntity;
import com.datasophon.dao.entity.instance.K8sServiceInstance;
import com.datasophon.dao.enums.k8s.InstanceSource;
import com.datasophon.dao.enums.k8s.InstanceSourceKind;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSONObject;

/**
 * 把接管扫描确认后的绑定关系登记为服务实例。
 *
 * <p>只写 Datasophon 自己的库，**不向目标集群写入任何内容**。
 */
@Service
public class K8sTakeoverRegisterService {

    /** namespace 记录的 active 状态，与 {@code K8sClusterNamespaceService} 既有取值一致。 */
    private static final int NAMESPACE_ACTIVE = 1;

    private final K8sClusterConfigService k8sClusterConfigService;
    private final K8sClusterNamespaceService k8sClusterNamespaceService;
    private final K8sServiceInstanceService k8sServiceInstanceService;
    private final K8sMetricsJobProbeService jobProbeService;
    private final FrameK8sServiceService frameK8sServiceService;

    public K8sTakeoverRegisterService(K8sClusterConfigService k8sClusterConfigService,
                                      K8sClusterNamespaceService k8sClusterNamespaceService,
                                      K8sServiceInstanceService k8sServiceInstanceService,
                                      K8sMetricsJobProbeService jobProbeService,
                                      FrameK8sServiceService frameK8sServiceService) {
        this.k8sClusterConfigService = k8sClusterConfigService;
        this.k8sClusterNamespaceService = k8sClusterNamespaceService;
        this.k8sServiceInstanceService = k8sServiceInstanceService;
        this.jobProbeService = jobProbeService;
        this.frameK8sServiceService = frameK8sServiceService;
    }

    /**
     * 批量登记接管服务，并为每个服务探测其 OTel job。
     *
     * @param clusterId 集群 ID
     * @param bindings  用户在扫描结果页确认后的绑定关系
     * @return 每个服务的登记结果，含采集接入诊断
     */
    @Transactional(rollbackFor = Exception.class)
    public List<K8sTakeoverRegisterResult> register(Integer clusterId, List<Binding> bindings) {
        if (bindings == null || bindings.isEmpty()) {
            throw new BusinessHintException("未选择要接管的服务");
        }
        K8sClusterConfig config = k8sClusterConfigService.getByClusterId(clusterId);
        if (config == null) {
            throw new BusinessHintException("集群未配置 K8s 连接信息，无法接管");
        }
        // 一次查出集群内全部活跃 job，避免逐个服务查 Doris
        Set<String> activeJobs = jobProbeService.activeJobs(clusterId);

        List<K8sTakeoverRegisterResult> results = new ArrayList<>();
        for (Binding binding : bindings) {
            K8sClusterNamespace namespace = k8sClusterNamespaceService.createIfAbsent(
                    new K8sNamespaceIdentityDTO(clusterId, binding.namespace()), NAMESPACE_ACTIVE);
            K8sServiceInstance instance = k8sServiceInstanceService.createIfAbsent(
                    clusterId, namespace.getId(), binding.frameServiceId());

            boolean isCr = InstanceSourceKind.CR.name().equals(binding.sourceKind());
            K8sOperatorArtifact operatorArtifact = isCr ? operatorArtifactOf(binding.frameServiceId()) : null;
            K8sMetricsJobProbeService.ProbeResult probeResult = jobProbeService.probe(
                    config, binding.releaseName(), binding.namespace(), activeJobs, operatorArtifact);

            instance.setSource(InstanceSource.IMPORTED);
            instance.setSourceKind(isCr ? InstanceSourceKind.CR : InstanceSourceKind.HELM);
            instance.setReleaseName(binding.releaseName());
            instance.setMetricsJob(probeResult.metricsJob());
            if (operatorArtifact != null && !probeResult.roleJobs().isEmpty()) {
                instance.setMonitorProfile(JSONObject.toJSONString(Map.of(
                        "profile", operatorArtifact.getMonitorProfile() == null
                                ? ""
                                : operatorArtifact.getMonitorProfile(),
                        "roles", probeResult.roleJobs())));
            }
            // 接管的服务已在运行，直接标记为成功状态
            instance.setState(1);
            k8sServiceInstanceService.updateById(instance);

            results.add(new K8sTakeoverRegisterResult(
                    instance.getId(), binding.releaseName(), binding.namespace(),
                    probeResult.metricsJob(), probeResult.metricsJob() != null, probeResult.roleJobs()));
        }
        return results;
    }

    /** 按框架服务定义 ID 解析 {@code artifact.operator}；非 operator 类型或未找到定义时返回 null。 */
    private K8sOperatorArtifact operatorArtifactOf(Integer frameServiceId) {
        FrameK8sServiceEntity definition = frameK8sServiceService.getById(frameServiceId);
        if (definition == null || definition.getArtifact() == null || definition.getArtifact().isBlank()) {
            return null;
        }
        K8sArtifact artifact = JSONObject.parseObject(definition.getArtifact(), K8sArtifact.class);
        if (artifact == null || !K8sArtifact.KIND_OPERATOR.equals(artifact.effectiveKind())) {
            return null;
        }
        return artifact.getOperator();
    }

    /**
     * 一条待登记的绑定关系。
     *
     * @param releaseName    Helm release 名（CR 来源为 CR 实例名）
     * @param namespace      所在命名空间
     * @param frameServiceId 绑定到的框架服务定义 ID
     * @param sourceKind     来源类型 HELM/CR；缺省（null）按 HELM 处理，兼容旧前端
     */
    public record Binding(String releaseName, String namespace, Integer frameServiceId, String sourceKind) {
    }
}
