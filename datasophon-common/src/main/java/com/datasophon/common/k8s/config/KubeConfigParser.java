package com.datasophon.common.k8s.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.List;
import java.util.Map;

/**
 * @author zhanghuangbin
 */
public class KubeConfigParser {

    /**
     * 解析 kubeConfig 内容，提取当前生效的 cluster 的 server 和证书信息
     *
     * @param kubeConfigContent kubeConfig YAML 内容
     * @return 包含 serverName 和 serverCert 的 ClientOptions 对象
     */
    public ClientOptions parse(String kubeConfigContent) {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Map<String, Object> config = yaml.load(kubeConfigContent);

        ClientOptions options = new ClientOptions();

        // 获取当前上下文名称
        String currentContext = (String) config.get("current-context");

        // 获取 contexts 列表，找到当前上下文对应的 cluster 名称
        String currentClusterName = findClusterNameByContext(config, currentContext);

        // 获取 clusters 列表，找到对应 cluster 的 server 和证书
        extractClusterInfo(config, currentClusterName, options);

        return options;
    }

    /**
     * 根据上下文名称找到对应的 cluster 名称
     */
    private String findClusterNameByContext(Map<String, Object> config, String currentContext) {
        List<Map<String, Object>> contexts = (List<Map<String, Object>>) config.get("contexts");
        if (contexts == null || contexts.isEmpty()) {
            return null;
        }

        for (Map<String, Object> context : contexts) {
            Map<String, Object> contextMap = (Map<String, Object>) context.get("context");
            if (contextMap == null) {
                continue;
            }
            String contextName = (String) context.get("name");
            if (currentContext != null && currentContext.equals(contextName)) {
                return (String) contextMap.get("cluster");
            }
        }

        // 如果没有指定 current-context，返回第一个 context 的 cluster
        if (!contexts.isEmpty()) {
            Map<String, Object> firstContext = (Map<String, Object>) contexts.get(0).get("context");
            if (firstContext != null) {
                return (String) firstContext.get("cluster");
            }
        }

        return null;
    }

    /**
     * 从 clusters 中提取 server 和证书信息
     */
    private void extractClusterInfo(Map<String, Object> config, String clusterName, ClientOptions options) {
        List<Map<String, Object>> clusters = (List<Map<String, Object>>) config.get("clusters");
        if (clusters == null || clusters.isEmpty()) {
            return;
        }

        for (Map<String, Object> cluster : clusters) {
            String name = (String) cluster.get("name");
            if (clusterName != null && clusterName.equals(name)) {
                Map<String, Object> clusterConfig = (Map<String, Object>) cluster.get("cluster");
                if (clusterConfig != null) {
                    // 设置 serverName
                    Object server = clusterConfig.get("server");
                    if (server != null) {
                        options.setServerName(server.toString());
                    }

                    // 设置 serverCert (certificate-authority-data 或 certificate-authority)
                    Object certData = clusterConfig.get("certificate-authority-data");
                    if (certData != null) {
                        options.setServerCert(certData.toString());
                    }
                }
                break;
            }
        }
    }
}
