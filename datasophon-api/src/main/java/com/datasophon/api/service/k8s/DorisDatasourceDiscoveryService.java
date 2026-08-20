package com.datasophon.api.service.k8s;

import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.observability.OtelDorisReaderFactory;
import com.datasophon.api.security.K8sTakeoverAccessGuard;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.api.vo.k8s.DorisDatasourceCandidate;
import com.datasophon.common.k8s.vo.k8s.K8sNode;
import com.datasophon.common.k8s.vo.k8s.K8sService;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

/**
 * 从目标集群发现 Doris 的连接候选，并提供连通性测试。
 *
 * <p>只读：不向目标集群写入任何内容。
 */
@Service
public class DorisDatasourceDiscoveryService {

    /** Doris FE 的 MySQL 协议端口，按此识别候选 Service。 */
    private static final int MYSQL_PROTOCOL_PORT = 9030;

    private static final int CONNECT_TIMEOUT_SECONDS = 10;

    /** 与 OtelDorisReaderFactory 的 DEFAULT_READER_USER 保持一致。 */
    static final String DEFAULT_READER_USER = "otel_reader";

    static final String DEFAULT_DATABASE = "otel";

    /**
     * host 会被直接拼进 JDBC URL；与 {@code K8sTakeoverDTO.DatasourceSave.host} 的 Bean Validation
     * 校验规则保持一致，这里是第二道防线——防止未来有内部调用绕过 Controller 层校验直接调这两个方法。
     */
    private static final Pattern HOST_PATTERN = Pattern.compile("[A-Za-z0-9.:\\[\\]-]+");

    private final com.datasophon.api.service.k8s.K8sService k8sService;
    private final K8sClusterConfigService k8sClusterConfigService;
    private final DorisDatasourcePersistenceService persistenceService;
    private final OtelDorisReaderFactory readerFactory;
    private final K8sTakeoverAccessGuard accessGuard;

    public DorisDatasourceDiscoveryService(com.datasophon.api.service.k8s.K8sService k8sService,
                                           K8sClusterConfigService k8sClusterConfigService,
                                           DorisDatasourcePersistenceService persistenceService,
                                           OtelDorisReaderFactory readerFactory,
                                           K8sTakeoverAccessGuard accessGuard) {
        this.k8sService = k8sService;
        this.k8sClusterConfigService = k8sClusterConfigService;
        this.persistenceService = persistenceService;
        this.readerFactory = readerFactory;
        this.accessGuard = accessGuard;
    }

    /**
     * 扫描集群内暴露 9030 端口的 Service，给出连接候选。
     *
     * <p>注意候选**不保证可达**：实测目标集群的 LoadBalancer 型 Service 其
     * {@code status.loadBalancer.ingress} 为空（公网 IP 由集群外部的负载均衡器持有，
     * K8s API 看不到），此时只能由用户手工填写实际地址。因此提交前必须做连通性测试。
     */
    public List<DorisDatasourceCandidate> discover(Integer clusterId) {
        K8sClusterConfig config = k8sClusterConfigService.getByClusterId(clusterId);
        if (config == null) {
            throw new BusinessHintException("集群未配置 K8s 连接信息，无法发现 Doris 数据源");
        }
        return k8sService.batchExec(config, client -> {
            String nodeIp = firstNodeIp(client.getNodes().getItems());
            List<DorisDatasourceCandidate> candidates = new ArrayList<>();
            for (K8sService service : client.getServicesAllNamespaces().getItems()) {
                collect(service, nodeIp, candidates);
            }
            return candidates;
        }, "发现 Doris 数据源");
    }

    /**
     * 用给定连接信息实连一次并执行 {@code SELECT 1}。
     *
     * @return 成功返回 null，失败返回可展示给用户的原因（不含密码）
     */
    public String testConnection(String host, Integer port, String password) {
        String url = jdbcUrl(host, port == null ? MYSQL_PROTOCOL_PORT : port);
        Properties properties = new Properties();
        properties.setProperty("user", DEFAULT_READER_USER);
        properties.setProperty("password", password == null ? "" : password);
        try (
                Connection connection = DriverManager.getConnection(url, properties);
                Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT 1")) {
            return rs.next() ? null : "连接成功但查询无返回";
        } catch (Exception e) {
            // 只回传异常消息，不回显密码
            return e.getMessage();
        }
    }

    /**
     * 保存接管集群的 Doris 数据源。**先测连通性，不通则拒绝保存**（D16）。
     *
     * <p>密码不落 {@code t_ddh_k8s_cluster_config}，而是存成 {@code DORIS / otel_reader_password}
     * 变量 —— {@link com.datasophon.api.observability.OtelCredentialService} 会优先读它，
     * 从而让 {@code OtelDorisReaderFactory} 用上用户录入的密码而非随机生成。
     */
    public void saveDatasource(Integer clusterId, String host, Integer port, String password) {
        accessGuard.requireImportedCluster(clusterId);
        K8sClusterConfig config = k8sClusterConfigService.getByClusterId(clusterId);
        if (config == null) {
            throw new BusinessHintException("集群未配置 K8s 连接信息");
        }
        int actualPort = port == null ? MYSQL_PROTOCOL_PORT : port;
        String failure = testConnection(host, actualPort, password);
        if (failure != null) {
            throw new BusinessHintException("Doris 连通性测试失败，未保存：" + failure);
        }

        config.setDorisHost(host);
        config.setDorisPort(actualPort);
        config.setDorisDatabase(DEFAULT_DATABASE);
        persistenceService.save(config, password);
        readerFactory.invalidate(clusterId);
    }

    static String jdbcUrl(String host, int port) {
        if (host == null || !HOST_PATTERN.matcher(host).matches()) {
            // Bean Validation 已在 Controller 层拦过一次；这里再拒绝一次，防止内部调用绕过校验
            // 把 ? & # 之类的字符带进 JDBC 连接串（可注入 allowLoadLocalInfile 等危险参数）。
            throw new BusinessHintException("Doris 主机格式不合法");
        }
        return String.format("jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&useSSL=false"
                + "&connectTimeout=%d&socketTimeout=%d",
                host, port, DEFAULT_DATABASE,
                CONNECT_TIMEOUT_SECONDS * 1000, CONNECT_TIMEOUT_SECONDS * 1000);
    }

    private void collect(K8sService service, String nodeIp, List<DorisDatasourceCandidate> candidates) {
        if (service.getSpec() == null || service.getSpec().getPorts() == null) {
            return;
        }
        for (K8sService.ServicePort port : service.getSpec().getPorts()) {
            if (port.getPort() != MYSQL_PROTOCOL_PORT) {
                continue;
            }
            candidates.add(toCandidate(service, port, nodeIp));
        }
    }

    private DorisDatasourceCandidate toCandidate(K8sService service, K8sService.ServicePort port, String nodeIp) {
        String name = service.getMetadata() == null ? null : service.getMetadata().getName();
        String namespace = service.getMetadata() == null ? null : service.getMetadata().getNamespace();
        String type = service.getSpec().getType();

        String ingress = loadBalancerAddress(service);
        if (ingress != null) {
            return new DorisDatasourceCandidate(name, namespace, type, ingress, port.getPort(),
                    DorisDatasourceCandidate.SOURCE_LOAD_BALANCER, true, null);
        }
        if (port.getNodePort() != null && nodeIp != null) {
            return new DorisDatasourceCandidate(name, namespace, type, nodeIp, port.getNodePort(),
                    DorisDatasourceCandidate.SOURCE_NODE_PORT, true,
                    "NodePort 走节点内网地址，若平台与集群节点不在同一网络请改填对外地址");
        }
        String hint = "LoadBalancer".equals(type)
                ? "该 Service 为 LoadBalancer 但未分配外部地址，请手工填写实际对外地址"
                : "仅集群内地址（ClusterIP），平台通常无法直连，请手工填写对外暴露的地址";
        return new DorisDatasourceCandidate(name, namespace, type,
                service.getSpec().getClusterIP(), port.getPort(),
                DorisDatasourceCandidate.SOURCE_CLUSTER_IP, false, hint);
    }

    private String loadBalancerAddress(K8sService service) {
        if (service.getStatus() == null || service.getStatus().getLoadBalancer() == null
                || service.getStatus().getLoadBalancer().getIngress() == null) {
            return null;
        }
        for (K8sService.LoadBalancerIngress ingress : service.getStatus().getLoadBalancer().getIngress()) {
            if (ingress.getIp() != null && !ingress.getIp().isBlank()) {
                return ingress.getIp();
            }
            if (ingress.getHostname() != null && !ingress.getHostname().isBlank()) {
                return ingress.getHostname();
            }
        }
        return null;
    }

    private String firstNodeIp(List<K8sNode> nodes) {
        if (nodes == null) {
            return null;
        }
        for (K8sNode node : nodes) {
            if (node.getStatus() == null || node.getStatus().getAddresses() == null) {
                continue;
            }
            for (K8sNode.NodeAddress address : node.getStatus().getAddresses()) {
                if ("InternalIP".equals(address.getType())) {
                    return address.getAddress();
                }
            }
        }
        return null;
    }
}
