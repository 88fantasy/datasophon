package com.datasophon.api.service.k8s.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.datasophon.api.exceptions.BusinessException;
import com.datasophon.api.service.k8s.K8sService;
import com.datasophon.api.vo.k8s.K8sClusterStatus;
import com.datasophon.api.vo.k8s.K8sConnectionResult;
import com.datasophon.api.vo.k8s.K8sNamespace;
import com.datasophon.common.function.ThrowableMapper;
import com.datasophon.common.k8s.client.K8sClientFactory;
import com.datasophon.common.k8s.config.ClientOptions;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.Node;
import io.fabric8.kubernetes.api.model.NodeCondition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.VersionInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author zhanghuangbin
 */
@Service("k8sService")
@Slf4j
public class K8sServiceImpl implements K8sService {


    @Override
    public K8sClusterStatus getState(K8sClusterConfig config) {
        return exec(newOptions(config), client -> {
            K8sClusterStatus state = new K8sClusterStatus();
            VersionInfo version = client.getKubernetesVersion();
            state.setK8sVersion(version.getMajor() + "." + version.getMinor());

            // 2. 节点列表及状态
            List<Node> nodes = client.nodes().list().getItems();
            nodes.forEach(node -> {
                String name = node.getMetadata().getName();
                K8sClusterStatus.NodeInfo info = new K8sClusterStatus.NodeInfo();
                info.setName(name);

                String status = node.getStatus().getConditions().stream()
                        .filter(c -> READY.equals(c.getType()))
                        .map(NodeCondition::getStatus)
                        .findFirst()
                        .orElse("Unknown");
                info.setStatus(status);
                state.getNodes().add(info);
            });

            // 3. 命名空间列表
            List<Namespace> namespaces = client.namespaces().list().getItems();
            state.setNamespace(namespaces.stream().map(ns->ns.getMetadata().getName()).collect(Collectors.toList()));

            return state;
        }, null);
    }

    @Override
    public K8sConnectionResult testConnection(K8sClusterConfig config) {
        K8sConnectionResult result = new K8sConnectionResult();
        try {
            ClientOptions options = newOptions(config);
            options.setFastFail(true);
            try (KubernetesClient client = K8sClientFactory.newClient(options)){
                client.getKubernetesVersion();
            }
            result.setSuccess(true);
            result.setInfo("connect success");
        } catch (Exception e) {
            if (e instanceof KubernetesClientException) {
                String message = e.getMessage();
                if (e.getCause() != null) {
                    message = message +  e.getCause().getMessage();
                }
                log.warn("test k8s connection fail, {}", message);
            } else {
                log.error(e.getMessage(), e);
            }

            result.setSuccess(false);
            String message = e.getMessage();
            if (e instanceof NullPointerException) {
                message = "空指针";
            } else if(e instanceof KubernetesClientException) {
                if (e.getCause() != null) {
                    message = message +  e.getCause().getMessage();
                }
            }
            result.setInfo(String.format("测试连接性失败，原因： %s", message));
        }
        return result;
    }


    @Override
    public List<K8sNamespace> listNamespaces(K8sClusterConfig config) {
        return exec(newOptions(config), client -> {
            List<Namespace> namespaces = client.namespaces().list().getItems();
            return namespaces.stream()
                    .map(ns-> {
                        K8sNamespace namespace = new K8sNamespace();
                        namespace.setName(ns.getMetadata().getName());
                        namespace.setStatus("Active".equals(ns.getStatus().getPhase()) ? "active" : "inactive");
                        return namespace;
                    })
                    .collect(Collectors.toList());
        }, "获取 K8s 命名空间列表及状态");
    }


    private ClientOptions newOptions(K8sClusterConfig config) {
        ClientOptions options = BeanUtil.toBean(config, ClientOptions.class);
        options.setServerName(config.getServerHost());
        return options;
    }

    private <T> T exec(ClientOptions options, ThrowableMapper<KubernetesClient, T> consumer, String actionHint) {
        try (KubernetesClient client = K8sClientFactory.newClient(options)) {
            return consumer.accept(client);
        } catch (Exception e) {
            throw new BusinessException(String.format("%s失败，%s", StrUtil.isBlank(actionHint) ? "请求K8S集群接口" : actionHint, e.getMessage()), e);
        }
    }
}
