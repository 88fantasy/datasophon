package com.datasophon.api.vo.k8s;

/**
 * 接管扫描发现的 Doris 连接候选地址。
 *
 * @param serviceName K8s Service 名
 * @param namespace   所在命名空间
 * @param serviceType Service 类型 LoadBalancer / NodePort / ClusterIP
 * @param host        候选主机地址；ClusterIP 类型时为集群内地址
 * @param port        MySQL 协议端口
 * @param source      地址来源 LOAD_BALANCER / NODE_PORT / CLUSTER_IP
 * @param reachable   平台是否**可能**直连。ClusterIP 恒为 false；
 *                    LoadBalancer 未分配 ingress 时也为 false
 * @param hint        不可达时给用户的处置提示
 */
public record DorisDatasourceCandidate(String serviceName,
                                       String namespace,
                                       String serviceType,
                                       String host,
                                       Integer port,
                                       String source,
                                       boolean reachable,
                                       String hint) {

    public static final String SOURCE_LOAD_BALANCER = "LOAD_BALANCER";
    public static final String SOURCE_NODE_PORT = "NODE_PORT";
    public static final String SOURCE_CLUSTER_IP = "CLUSTER_IP";
}
