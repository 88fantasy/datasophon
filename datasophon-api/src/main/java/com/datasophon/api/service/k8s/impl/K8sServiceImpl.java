package com.datasophon.api.service.k8s.impl;

import com.datasophon.api.dto.instance.K8sServiceInstanceQueryDTO;
import com.datasophon.api.dto.log.K8sRuntimeEventQueryDTO;
import com.datasophon.api.dto.log.K8sRuntimeLogQueryDTO;
import com.datasophon.api.exceptions.BusinessException;
import com.datasophon.api.service.instance.K8sServiceInstanceService;
import com.datasophon.api.service.k8s.K8sService;
import com.datasophon.api.vo.k8s.K8sClusterStatus;
import com.datasophon.api.vo.k8s.K8sConfigMapInfo;
import com.datasophon.api.vo.k8s.K8sConnectionResult;
import com.datasophon.api.vo.k8s.K8sDeploymentInfo;
import com.datasophon.api.vo.k8s.K8sEventInfo;
import com.datasophon.api.vo.k8s.K8sIngressInfo;
import com.datasophon.api.vo.k8s.K8sNamespace;
import com.datasophon.api.vo.k8s.K8sPodInfo;
import com.datasophon.api.vo.k8s.K8sServiceInfo;
import com.datasophon.common.function.ThrowableMapper;
import com.datasophon.common.k8s.client.HelmClient;
import com.datasophon.common.k8s.client.KubectlClient;
import com.datasophon.common.k8s.config.ClientOptions;
import com.datasophon.common.k8s.config.DockerRegistryOptions;
import com.datasophon.common.k8s.exception.KubectlException;
import com.datasophon.common.k8s.spec.helm.HelmUtils;
import com.datasophon.common.k8s.vo.k8s.K8sConfigMap;
import com.datasophon.common.k8s.vo.k8s.K8sDeployment;
import com.datasophon.common.k8s.vo.k8s.K8sEvent;
import com.datasophon.common.k8s.vo.k8s.K8sIngress;
import com.datasophon.common.k8s.vo.k8s.K8sPod;
import com.datasophon.common.k8s.vo.k8s.K8sResourceList;
import com.datasophon.common.k8s.vo.k8s.K8sSecret;
import com.datasophon.common.storage.impl.NexusImageStorage;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.vo.instance.K8sServiceInstanceVO;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;

/**
 * @author zhanghuangbin
 */
@Service("k8sService")
@Slf4j
public class K8sServiceImpl implements K8sService {
    
    @Autowired
    private K8sServiceInstanceService k8sServiceInstanceService;
    
    /** 拉取私有镜像仓库镜像所用的 docker-registry Secret 名称（与 K8s 集群内约定一致）。 */
    private static final String NEXUS_REGISTRY_SECRET_NAME = "nexus-registry-secret";
    
    @Override
    public K8sClusterStatus getState(K8sClusterConfig config) {
        return doGetState(newOptions(config));
    }
    
    private K8sClusterStatus doGetState(ClientOptions options) {
        return exec(options, client -> {
            K8sClusterStatus state = new K8sClusterStatus();
            
            String version = client.getVersion();
            state.setK8sVersion(version);
            K8sResourceList<com.datasophon.common.k8s.vo.k8s.K8sNode> nodesResult = client.getNodes();
            for (com.datasophon.common.k8s.vo.k8s.K8sNode node : nodesResult.getItems()) {
                String name = node.getMetadata() != null ? node.getMetadata().getName() : null;
                K8sClusterStatus.NodeInfo info = new K8sClusterStatus.NodeInfo();
                info.setName(name);
                
                String status = "Unknown";
                if (node.getStatus() != null && node.getStatus().getConditions() != null) {
                    for (com.datasophon.common.k8s.vo.k8s.K8sNode.NodeCondition condition : node.getStatus().getConditions()) {
                        if (READY.equals(condition.getType())) {
                            status = condition.getStatus();
                            break;
                        }
                    }
                }
                info.setStatus(status);
                state.getNodes().add(info);
            }
            
            K8sResourceList<com.datasophon.common.k8s.vo.k8s.K8sNamespace> nsResult = client.getNamespaces();
            for (com.datasophon.common.k8s.vo.k8s.K8sNamespace ns : nsResult.getItems()) {
                state.getNamespace().add(ns.getMetadata() != null ? ns.getMetadata().getName() : null);
            }
            
            return state;
        }, "获取 K8s 集群状态");
    }
    
    @Override
    public K8sConnectionResult testConnection(K8sClusterConfig config) {
        K8sConnectionResult result = new K8sConnectionResult();
        try {
            ClientOptions options = newOptions(config);
            doGetState(options);
            result.setSuccess(true);
            result.setInfo("connect success");
        } catch (Exception e) {
            result.setSuccess(false);
            
            String message = e.getMessage();
            if (e instanceof NullPointerException) {
                message = "空指针";
            }
            
            Throwable cause = e.getCause();
            if (cause instanceof KubectlException) {
                StringBuilder sb = new StringBuilder(message);
                while (cause != null) {
                    sb.append("\n底层原因：").append(cause.getMessage());
                    cause = cause.getCause();
                }
                message = sb.toString();
                log.warn("test k8s connection fail, {}", message);
            } else {
                log.error(e.getMessage(), e);
            }
            result.setInfo(String.format("测试连接性失败，原因：%s", message));
        }
        return result;
    }
    
    @Override
    public List<K8sNamespace> listNamespaces(K8sClusterConfig config) {
        return exec(newOptions(config), this::doListNamespaces, "获取 K8s 集群命名空间列表及状态");
    }
    
    private List<K8sNamespace> doListNamespaces(KubectlClient client) {
        K8sResourceList<com.datasophon.common.k8s.vo.k8s.K8sNamespace> nsResult = client.getNamespaces();
        List<K8sNamespace> result = new ArrayList<>();
        for (com.datasophon.common.k8s.vo.k8s.K8sNamespace ns : nsResult.getItems()) {
            K8sNamespace namespace = new K8sNamespace();
            namespace.setName(ns.getMetadata() != null ? ns.getMetadata().getName() : null);
            String phase = ns.getStatus() != null ? ns.getStatus().getPhase() : "";
            namespace.setStatus("Active".equals(phase) ? "active" : "inactive");
            result.add(namespace);
        }
        return result;
    }
    
    @Override
    public List<String> getResourceTypes(K8sClusterConfig config, K8sServiceInstanceQueryDTO query) {
        return exec(newOptions(config), client -> {
            String namespace = getNamespaceByInstanceId(query.getInstanceId());
            
            String labelSelector = buildLabelSelector(query.getInstanceId());
            
            List<String> resourceTypes = new ArrayList<>();
            
            // 检查 Pods
            if (client.hasResources(namespace, "pods", labelSelector)) {
                resourceTypes.add(POD_TYPE);
            }
            
            // 检查 Deployments
            if (client.hasResources(namespace, "deployments", labelSelector)) {
                resourceTypes.add(DEPLOYMENT_TYPE);
            }
            
            // 检查 Services
            if (client.hasResources(namespace, "services", labelSelector)) {
                resourceTypes.add(SERVICE_TYPE);
            }
            
            // 检查 Ingresses
            if (client.hasResources(namespace, "ingresses", labelSelector)) {
                resourceTypes.add(INGRESS_TYPE);
            }
            
            // 检查 ConfigMaps
            if (client.hasResources(namespace, "configmaps", labelSelector)) {
                resourceTypes.add(CONFIGMAP_TYPE);
            }
            
            return resourceTypes;
        }, "获取指定命名空间下的 K8s 资源类型");
    }
    
    @Override
    public List<K8sDeploymentInfo> listDeployments(K8sClusterConfig config, K8sServiceInstanceQueryDTO query) {
        return exec(newOptions(config), client -> {
            String namespace = getNamespaceByInstanceId(query.getInstanceId());
            String labelSelector = buildLabelSelector(query.getInstanceId());
            
            K8sResourceList<K8sDeployment> deploymentsResult = client.getDeployments(namespace, labelSelector);
            List<K8sDeploymentInfo> result = new ArrayList<>();
            for (K8sDeployment deployment : deploymentsResult.getItems()) {
                result.add(deploymentToInfo(deployment));
            }
            return result;
        }, "获取 Deployment 资源列表");
    }
    
    private String buildLabelSelector(Integer instanceId) {
        String serviceName = k8sServiceInstanceService.getServiceName(instanceId)
                .orElseThrow(() -> new BusinessException(String.format("K8s 服务实例 %s 不存在", instanceId)));
        return buildLabelSelector(serviceName);
    }
    
    private String buildLabelSelector(String serviceName) {
        return String.format("%s=%s,%s=%s", MANGED_BY_LABEL, MANGED_BY_LABEL_VALUE, SRV_INST_ID_LABEL, HelmUtils.createReleaseName(serviceName));
    }
    
    /**
     * 通过服务实例 ID 获取 Kubernetes 命名空间
     *
     * @param instanceId 服务实例 ID
     * @return Kubernetes 命名空间名称
     */
    private String getNamespaceByInstanceId(Integer instanceId) {
        K8sServiceInstanceVO instance = k8sServiceInstanceService.getVoById(instanceId)
                .orElseThrow(() -> new BusinessException(String.format("K8s 服务实例 %s 不存在", instanceId)));
        return instance.getNamespace();
    }
    
    private K8sDeploymentInfo deploymentToInfo(K8sDeployment deployment) {
        K8sDeploymentInfo info = new K8sDeploymentInfo();
        info.setName(deployment.getMetadata() != null ? deployment.getMetadata().getName() : null);
        info.setNamespace(deployment.getMetadata() != null ? deployment.getMetadata().getNamespace() : null);
        info.setAge(calculateAge(deployment.getMetadata() != null ? deployment.getMetadata().getCreationTimestamp() : null));
        info.setLabels(deployment.getMetadata() != null ? deployment.getMetadata().getLabels() : null);
        
        K8sDeployment.DeploymentStatus status = deployment.getStatus();
        if (status != null) {
            info.setReadyReplicas(status.getReadyReplicas());
            info.setReplicas(status.getReplicas());
            info.setAvailableReplicas(status.getAvailableReplicas());
            info.setUnavailableReplicas(status.getUnavailableReplicas());
            
            int ready = info.getReadyReplicas() != null ? info.getReadyReplicas() : 0;
            int total = info.getReplicas() != null ? info.getReplicas() : 0;
            info.setStatus(ready == total ? "Ready" : "Progressing");
        }
        
        // 镜像列表
        if (deployment.getSpec() != null && deployment.getSpec().getTemplate() != null) {
            K8sDeployment.PodSpec podSpec = deployment.getSpec().getTemplate().getSpec();
            if (podSpec != null && podSpec.getContainers() != null) {
                List<String> images = podSpec.getContainers().stream()
                        .map(c -> c.getImage() != null ? c.getImage() : "")
                        .collect(Collectors.toList());
                info.setImages(images);
            }
        }
        
        // 选择器
        if (deployment.getSpec() != null && deployment.getSpec().getSelector() != null) {
            K8sDeployment.LabelSelector selector = deployment.getSpec().getSelector();
            Map<String, String> matchLabels = selector.getMatchLabels();
            info.setSelector(matchLabels != null ? matchLabels.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(",")) : null);
        }
        
        // 更新策略
        if (deployment.getSpec() != null && deployment.getSpec().getStrategy() != null) {
            info.setStrategy(deployment.getSpec().getStrategy().getType());
        }
        
        return info;
    }
    
    @Override
    public List<K8sPodInfo> listPods(K8sClusterConfig config, K8sServiceInstanceQueryDTO query) {
        return exec(newOptions(config), client -> {
            String namespace = getNamespaceByInstanceId(query.getInstanceId());
            String labelSelector = buildLabelSelector(query.getInstanceId());
            
            K8sResourceList<K8sPod> podsResult = client.getPods(namespace, labelSelector);
            List<K8sPodInfo> result = new ArrayList<>();
            for (K8sPod pod : podsResult.getItems()) {
                result.add(podToInfo(pod));
            }
            return result;
        }, "获取 Pod 资源列表");
    }
    
    private K8sPodInfo podToInfo(K8sPod pod) {
        K8sPodInfo info = new K8sPodInfo();
        info.setName(pod.getMetadata() != null ? pod.getMetadata().getName() : null);
        info.setNamespace(pod.getMetadata() != null ? pod.getMetadata().getNamespace() : null);
        info.setStatus(pod.getStatus() != null ? pod.getStatus().getPhase() : "");
        info.setAge(calculateAge(pod.getMetadata() != null ? pod.getMetadata().getCreationTimestamp() : null));
        info.setPodIP(pod.getStatus() != null ? pod.getStatus().getPodIP() : null);
        info.setNodeName(pod.getSpec() != null ? pod.getSpec().getNodeName() : null);
        info.setLabels(pod.getMetadata() != null ? pod.getMetadata().getLabels() : null);
        
        // 计算重启次数
        int restartCount = 0;
        List<String> containers = new ArrayList<>();
        if (pod.getStatus() != null && pod.getStatus().getContainerStatuses() != null) {
            for (K8sPod.ContainerStatus cs : pod.getStatus().getContainerStatuses()) {
                restartCount += cs.getRestartCount();
                containers.add(cs.getName());
            }
        }
        info.setRestartCount(restartCount);
        info.setContainerNames(containers);
        
        // 设置就绪状态
        int readyContainers = 0;
        int totalContainers = 0;
        if (pod.getSpec() != null && pod.getSpec().getContainers() != null) {
            totalContainers = pod.getSpec().getContainers().size();
        }
        if (pod.getStatus() != null && pod.getStatus().getContainerStatuses() != null) {
            for (K8sPod.ContainerStatus cs : pod.getStatus().getContainerStatuses()) {
                if (cs.getReady()) {
                    readyContainers++;
                }
            }
        }
        info.setReady(readyContainers + "/" + totalContainers);
        
        return info;
    }
    
    @Override
    public List<K8sServiceInfo> listServices(K8sClusterConfig config, K8sServiceInstanceQueryDTO query) {
        return exec(newOptions(config), client -> {
            String namespace = getNamespaceByInstanceId(query.getInstanceId());
            String labelSelector = buildLabelSelector(query.getInstanceId());
            
            K8sResourceList<com.datasophon.common.k8s.vo.k8s.K8sService> servicesResult = client.getServices(namespace, labelSelector);
            List<K8sServiceInfo> result = new ArrayList<>();
            for (com.datasophon.common.k8s.vo.k8s.K8sService service : servicesResult.getItems()) {
                result.add(serviceToInfo(service));
            }
            return result;
        }, "获取 Service 资源列表");
    }
    
    private K8sServiceInfo serviceToInfo(com.datasophon.common.k8s.vo.k8s.K8sService service) {
        K8sServiceInfo info = new K8sServiceInfo();
        info.setName(service.getMetadata() != null ? service.getMetadata().getName() : null);
        info.setNamespace(service.getMetadata() != null ? service.getMetadata().getNamespace() : null);
        info.setAge(calculateAge(service.getMetadata() != null ? service.getMetadata().getCreationTimestamp() : null));
        info.setLabels(service.getMetadata() != null ? service.getMetadata().getLabels() : null);
        
        com.datasophon.common.k8s.vo.k8s.K8sService.ServiceSpec spec = service.getSpec();
        if (spec != null) {
            info.setType(spec.getType());
            info.setClusterIP(spec.getClusterIP());
            
            // 外部 IP
            if (spec.getExternalIPs() != null && !spec.getExternalIPs().isEmpty()) {
                info.setExternalIP(String.join(",", spec.getExternalIPs()));
            }
            
            // LoadBalancer IP
            if (service.getStatus() != null && service.getStatus().getLoadBalancer() != null
                    && service.getStatus().getLoadBalancer().getIngress() != null
                    && !service.getStatus().getLoadBalancer().getIngress().isEmpty()) {
                info.setLoadBalancerIP(service.getStatus().getLoadBalancer().getIngress().get(0).getIp());
            }
            
            // 端口列表
            List<K8sServiceInfo.PortInfo> portInfos = new ArrayList<>();
            if (spec.getPorts() != null) {
                for (com.datasophon.common.k8s.vo.k8s.K8sService.ServicePort port : spec.getPorts()) {
                    K8sServiceInfo.PortInfo portInfo = new K8sServiceInfo.PortInfo();
                    portInfo.setName(port.getName());
                    portInfo.setPort(port.getPort());
                    portInfo.setTargetPort(port.getTargetPort());
                    portInfo.setNodePort(port.getNodePort() != null ? port.getNodePort() : null);
                    portInfo.setProtocol(port.getProtocol());
                    portInfos.add(portInfo);
                }
            }
            info.setPorts(portInfos);
            
            // 选择器
            if (spec.getSelector() != null) {
                info.setSelector(spec.getSelector().entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .collect(Collectors.joining(",")));
            }
            
            info.setSessionAffinity(spec.getSessionAffinity());
        }
        info.setStatus("Active");
        
        return info;
    }
    
    @Override
    public List<K8sIngressInfo> listIngresses(K8sClusterConfig config, K8sServiceInstanceQueryDTO query) {
        return exec(newOptions(config), client -> {
            String namespace = getNamespaceByInstanceId(query.getInstanceId());
            String labelSelector = buildLabelSelector(query.getInstanceId());
            
            K8sResourceList<K8sIngress> ingressesResult = client.getIngresses(namespace, labelSelector);
            List<K8sIngressInfo> result = new ArrayList<>();
            for (K8sIngress ingress : ingressesResult.getItems()) {
                result.add(ingressToInfo(ingress));
            }
            return result;
        }, "获取 Ingress 资源列表");
    }
    
    private K8sIngressInfo ingressToInfo(K8sIngress ingress) {
        K8sIngressInfo info = new K8sIngressInfo();
        info.setName(ingress.getMetadata() != null ? ingress.getMetadata().getName() : null);
        info.setNamespace(ingress.getMetadata() != null ? ingress.getMetadata().getNamespace() : null);
        info.setAge(calculateAge(ingress.getMetadata() != null ? ingress.getMetadata().getCreationTimestamp() : null));
        info.setLabels(ingress.getMetadata() != null ? ingress.getMetadata().getLabels() : null);
        
        K8sIngress.IngressSpec spec = ingress.getSpec();
        if (spec != null) {
            if (spec.getIngressClassName() != null) {
                info.setIngressClass(spec.getIngressClassName());
            }
            
            // 主机和路径
            List<String> hosts = new ArrayList<>();
            if (spec.getRules() != null) {
                for (K8sIngress.IngressRule rule : spec.getRules()) {
                    if (rule.getHost() != null) {
                        hosts.add(rule.getHost());
                    }
                }
            }
            info.setHosts(hosts);
        }
        
        // 负载均衡地址
        if (ingress.getStatus() != null && ingress.getStatus().getLoadBalancer() != null
                && ingress.getStatus().getLoadBalancer().getIngress() != null
                && !ingress.getStatus().getLoadBalancer().getIngress().isEmpty()) {
            K8sIngress.LoadBalancerIngress first =
                    ingress.getStatus().getLoadBalancer().getIngress().get(0);
            String lbAddress = first.getIp();
            if (StrUtil.isBlank(lbAddress)) {
                lbAddress = first.getHostname();
            }
            info.setLoadBalancerAddress(lbAddress);
        }
        
        info.setStatus("Active");
        
        return info;
    }
    
    @Override
    public List<K8sConfigMapInfo> listConfigMaps(K8sClusterConfig config, K8sServiceInstanceQueryDTO query) {
        return exec(newOptions(config), client -> {
            String namespace = getNamespaceByInstanceId(query.getInstanceId());
            String labelSelector = buildLabelSelector(query.getInstanceId());
            
            K8sResourceList<K8sConfigMap> configMapsResult = client.getConfigMaps(namespace, labelSelector);
            List<K8sConfigMapInfo> result = new ArrayList<>();
            for (K8sConfigMap configMap : configMapsResult.getItems()) {
                result.add(configMapToInfo(configMap));
            }
            return result;
        }, "获取 ConfigMap 资源列表");
    }
    
    private K8sConfigMapInfo configMapToInfo(K8sConfigMap configMap) {
        K8sConfigMapInfo info = new K8sConfigMapInfo();
        info.setName(configMap.getMetadata() != null ? configMap.getMetadata().getName() : null);
        info.setNamespace(configMap.getMetadata() != null ? configMap.getMetadata().getNamespace() : null);
        info.setAge(calculateAge(configMap.getMetadata() != null ? configMap.getMetadata().getCreationTimestamp() : null));
        info.setLabels(configMap.getMetadata() != null ? configMap.getMetadata().getLabels() : null);
        
        return info;
    }
    
    private String calculateAge(String creationTimestamp) {
        if (creationTimestamp == null || creationTimestamp.isEmpty()) {
            return "";
        }
        try {
            LocalDateTime creationTime = LocalDateTime.parse(creationTimestamp, DateTimeFormatter.ISO_DATE_TIME);
            LocalDateTime now = LocalDateTime.now();
            long seconds = java.time.Duration.between(creationTime, now).getSeconds();
            if (seconds < 60) {
                return seconds + "s";
            } else if (seconds < 3600) {
                return (seconds / 60) + "m";
            } else if (seconds < 86400) {
                return (seconds / 3600) + "h";
            } else {
                return (seconds / 86400) + "d";
            }
        } catch (Exception e) {
            return "";
        }
    }
    
    private ClientOptions newOptions(K8sClusterConfig config) {
        ClientOptions options = BeanUtil.toBean(config, ClientOptions.class);
        options.setServerName(config.getServerHost());
        return options;
    }
    
    @Override
    public boolean createIfAbsent(K8sClusterConfig config, String namespaceName) {
        return exec(newOptions(config), client -> {
            List<K8sNamespace> namespaces = doListNamespaces(client);
            K8sNamespace exist = null;
            for (K8sNamespace ns : namespaces) {
                if (namespaceName.equals(ns.getName())) {
                    exist = ns;
                    break;
                }
            }
            if (exist == null) {
                // 2. namespace 不存在，创建它
                client.createNamespace(namespaceName);
                log.info("K8s namespace '{}' created in cluster {}", namespaceName, config.getClusterId());
            }
            
            // 3. 创建 nexus-registry-secret
            K8sSecret sSecret = client.getSecret(namespaceName, NEXUS_REGISTRY_SECRET_NAME);
            if (sSecret == null) {
                DockerRegistryOptions options = NexusImageStorage.newOptions();
                String dockerServer = String.format("%s:%s", options.getHost(), options.getPort());
                client.createDockerRegistrySecret(namespaceName, NEXUS_REGISTRY_SECRET_NAME, dockerServer, options.getUsername(), options.getPassword());
                log.info("nexus-registry-secret created in namespace {}", namespaceName);
                
                // 4. 将凭据附加到 service_account 中
                String serviceAccountName = "default";
                client.attachSecretToServiceAccount(namespaceName, NEXUS_REGISTRY_SECRET_NAME, serviceAccountName);
                log.info("nexus-registry-secret attached to serviceaccount {} in namespace {}", serviceAccountName, namespaceName);
            }
            
            // 5. 返回新创建的 namespace 信息
            return exist == null;
        }, "确保 K8s namespace 存在");
    }
    
    @Override
    public void restartDeployment(K8sClusterConfig config, List<K8sDeploymentInfo> deployments) {
        exec(newOptions(config), client -> {
            for (K8sDeploymentInfo deployment : deployments) {
                try {
                    client.restartDeployment(deployment.getNamespace(), deployment.getName());
                } catch (Exception e) {
                    throw new KubectlException(String.format("重启 deployment/%s 失败，%s", deployment.getName(), e.getMessage()), e);
                }
            }
            return null;
        }, "重启 Deployment");
    }
    
    @Override
    public void scaleDeployments(K8sClusterConfig config, List<K8sDeploymentInfo> deployments, int replicas) {
        exec(newOptions(config), client -> {
            for (K8sDeploymentInfo deployment : deployments) {
                try {
                    client.scaleDeployment(deployment.getNamespace(), deployment.getName(), replicas);
                } catch (Exception e) {
                    throw new KubectlException(String.format("缩放 deployment/%s 到%d 副本失败，%s", deployment.getName(), replicas, e.getMessage()), e);
                }
            }
            return null;
        }, "缩放 Deployment 副本数");
    }
    
    @Override
    public void deleteSecrets(K8sClusterConfig config, String namespace, List<String> secrets) {
        exec(newOptions(config), client -> {
            // 批量删除 secrets
            if (!secrets.isEmpty()) {
                client.deleteSecrets(namespace, secrets);
                log.info("成功删除 {} 个 pending secrets", secrets.size());
            }
            return null;
        }, "删除secrets");
    }
    
    @Override
    public <T> T batchExec(K8sClusterConfig config, ThrowableMapper<KubectlClient, T> consumer, String actionHint) {
        return exec(newOptions(config), consumer, actionHint);
    }
    
    @Override
    public String getPodLog(K8sClusterConfig config, K8sRuntimeLogQueryDTO dto) {
        return exec(newOptions(config), client -> {
            String namespace = getNamespaceByInstanceId(dto.getInstanceId());
            String podName = dto.getPodName();
            String containerName = dto.getContainerName();
            boolean previous = dto.isPrevious();
            
            return client.getPodLog(namespace, podName, containerName, previous, dto.getLines());
        }, "获取 K8s Pod 日志");
    }
    
    @Override
    public List<K8sEventInfo> listK8sServiceInstanceEvents(K8sClusterConfig config, K8sRuntimeEventQueryDTO query) {
        return exec(newOptions(config), client -> {
            K8sServiceInstanceVO instance = k8sServiceInstanceService.getVoById(query.getInstanceId())
                    .orElseThrow(() -> new BusinessException(String.format("K8s 服务实例 %s 不存在", query.getInstanceId())));
            String namespace = instance.getNamespace();
            String labelSelector = buildLabelSelector(query.getInstanceId());
            K8sResourceList<K8sPod> podsResult = client.getPods(namespace, labelSelector);
            List<K8sEvent> allEvents = new ArrayList<>();
            
            for (K8sPod pod : podsResult.getItems()) {
                List<K8sEvent> podEvents = client.getEventOf(namespace, "pod/" + pod.getMetadata().getName());
                allEvents.addAll(podEvents);
            }
            // 4. 转换为 K8sEventInfo
            List<K8sEventInfo> eventInfos = new ArrayList<>();
            for (K8sEvent event : allEvents) {
                eventInfos.add(eventToInfo(event));
            }
            
            // 5. 按最后发生时间逆序排序（从新到旧）
            eventInfos.sort((e1, e2) -> {
                String t1 = e1.getLastTimestamp() != null ? e1.getLastTimestamp() : e1.getFirstTimestamp();
                String t2 = e2.getLastTimestamp() != null ? e2.getLastTimestamp() : e2.getFirstTimestamp();
                return t2 != null && t1 != null ? t2.compareTo(t1) : 0;
            });
            
            return eventInfos;
        }, "获取 Deployment 及其关联 Pod 的事件");
    }
    
    /**
     * 将 K8sEvent 转换为 K8sEventInfo
     */
    private K8sEventInfo eventToInfo(K8sEvent event) {
        K8sEventInfo info = new K8sEventInfo();
        info.setName(event.getMetadata() != null ? event.getMetadata().getName() : null);
        info.setNamespace(event.getMetadata() != null ? event.getMetadata().getNamespace() : null);
        info.setAge(calculateAge(event.getMetadata() != null ? event.getMetadata().getCreationTimestamp() : null));
        info.setType(event.getType());
        info.setReason(event.getReason());
        info.setMessage(event.getMessage());
        info.setFirstTimestamp(event.getFirstTimestamp());
        info.setLastTimestamp(event.getLastTimestamp());
        info.setCount(event.getCount());
        info.setReportingComponent(event.getReportingComponent());
        
        // 关联对象信息
        if (event.getInvolvedObject() != null) {
            K8sEvent.InvolvedObject involvedObject = event.getInvolvedObject();
            info.setInvolvedObjectKind(involvedObject.getKind());
            info.setInvolvedObjectName(involvedObject.getName());
            info.setInvolvedObjectNamespace(involvedObject.getNamespace());
        }
        
        // 事件来源
        if (event.getSource() != null) {
            K8sEvent.EventSource source = event.getSource();
            info.setSourceComponent(source.getComponent());
            info.setSourceHost(source.getHost());
        }
        
        return info;
    }
    
    @Override
    public void uninstallRelease(K8sClusterConfig config, Integer instanceId) {
        try (HelmClient client = new HelmClient(newOptions(config))) {
            K8sServiceInstanceVO instance = k8sServiceInstanceService.getVoById(instanceId)
                    .orElseThrow(() -> new BusinessException(String.format("K8s 服务实例 %s 不存在", instanceId)));
            String namespace = instance.getNamespace();
            String releaseName = HelmUtils.createReleaseName(instance.getServiceName());
            log.info("卸载helm release {}", releaseName);
            client.uninstall(namespace, releaseName);
        }
    }
    
    private <T> T exec(ClientOptions options, ThrowableMapper<KubectlClient, T> consumer, String actionHint) {
        try (KubectlClient client = new KubectlClient(options)) {
            return consumer.accept(client);
        } catch (Exception e) {
            throw new BusinessException(String.format("%s 失败，%s", StrUtil.isBlank(actionHint) ? "请求 K8S 集群接口" : actionHint, e.getMessage()), e);
        }
    }
    
}
