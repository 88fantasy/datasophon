package com.datasophon.api.service.k8s;

import com.datasophon.api.observability.OtelDorisReaderFactory;
import com.datasophon.common.k8s.vo.k8s.K8sService;
import com.datasophon.common.model.k8s.K8sOperatorArtifact;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 探测接管服务在 OTel 数据中对应的 Prometheus job（Doris 中的 {@code service_name} 列）。
 *
 * <p><b>关联依据</b>：TargetAllocator 抓取 ServiceMonitor 时，job 名取自被抓取的
 * K8s Service 名。实测目标集群完全吻合 —— release {@code dolphinscheduler} 的三个 Service
 * {@code dolphinscheduler-api} / {@code -master-headless} / {@code -worker-headless}
 * 与 Doris 中的三个 {@code service_name} 逐一对应。
 *
 * <p>因此探测 = 「该 release 拥有的 Service 名」∩「Doris 中近期出现过的 service_name」，
 * 而不是按名字做模糊匹配。副作用是 Kyuubi 那种被两个 ServiceMonitor 重复抓取、
 * 其中一个 job 名为 {@code spark/kyuubi}（非 Service 名）的情况会只取到一个，
 * 正好避免了数据翻倍。
 */
@Service
public class K8sMetricsJobProbeService {

    private static final Logger log = LoggerFactory.getLogger(K8sMetricsJobProbeService.class);

    /** Helm 为 release 下所有资源打的标准标签。 */
    private static final String RELEASE_LABEL = "app.kubernetes.io/instance";

    /** 探测时回看的时间窗口，覆盖 TargetAllocator 的抓取间隔即可。 */
    private static final int LOOKBACK_HOURS = 1;

    private final com.datasophon.api.service.k8s.K8sService k8sService;
    private final OtelDorisReaderFactory readerFactory;

    public K8sMetricsJobProbeService(com.datasophon.api.service.k8s.K8sService k8sService,
                                     OtelDorisReaderFactory readerFactory) {
        this.k8sService = k8sService;
        this.readerFactory = readerFactory;
    }

    /**
     * 探测指定 release 对应的 job 列表。
     *
     * @return 逗号分隔的 job 名；该服务未接入采集时返回 null
     */
    public String probe(K8sClusterConfig config, Integer clusterId, String releaseName, String namespace) {
        Set<String> serviceNames = serviceNamesOf(config, releaseName, namespace);
        if (serviceNames.isEmpty()) {
            return null;
        }
        Set<String> activeJobs = activeJobs(clusterId);
        serviceNames.retainAll(activeJobs);
        return serviceNames.isEmpty() ? null : String.join(",", serviceNames);
    }

    /** 查询 Doris 中近期有数据的全部 job，供批量探测复用，避免每个服务查一次。 */
    public Set<String> activeJobs(Integer clusterId) {
        String sql = "SELECT DISTINCT service_name FROM otel.otel_metrics_gauge "
                + "WHERE timestamp > NOW() - INTERVAL " + LOOKBACK_HOURS + " HOUR "
                + "UNION SELECT DISTINCT service_name FROM otel.otel_metrics_sum "
                + "WHERE timestamp > NOW() - INTERVAL " + LOOKBACK_HOURS + " HOUR";
        try {
            return new LinkedHashSet<>(readerFactory.create(clusterId).sql(sql).query(String.class).list());
        } catch (Exception e) {
            // 数据源不可用不应阻断接管登记，只是探测不到 job
            log.warn("探测集群 {} 的 OTel job 失败：{}", clusterId, e.getMessage());
            return Set.of();
        }
    }

    /** 用已取到的 activeJobs 做探测，批量登记时用这个重载。 */
    public String probe(K8sClusterConfig config, String releaseName, String namespace, Set<String> activeJobs) {
        Set<String> serviceNames = serviceNamesOf(config, releaseName, namespace);
        serviceNames.retainAll(activeJobs);
        return serviceNames.isEmpty() ? null : String.join(",", serviceNames);
    }

    /** 探测结果：{@code metricsJob} 沿用既有逗号分隔格式；{@code roleJobs} 是角色名到其 job 列表的映射。 */
    public record ProbeResult(String metricsJob, Map<String, List<String>> roleJobs) {
    }

    /**
     * CR 来源服务的探测重载：{@code operatorArtifact} 非空时，Service 定位改用 name-prefix 启发式
     * （operator 管理的资源没有 Helm 标准标签），并按 {@code operatorArtifact.roles} 的正则把命中的
     * job 分类到角色桶里（如 fe/compute），供前端按角色分流查询。
     */
    public ProbeResult probe(K8sClusterConfig config, String releaseName, String namespace,
                             Set<String> activeJobs, K8sOperatorArtifact operatorArtifact) {
        Set<String> serviceNames = operatorArtifact == null
                ? serviceNamesOf(config, releaseName, namespace)
                : serviceNamesByNamePrefix(config, releaseName, namespace);
        serviceNames.retainAll(activeJobs);
        if (serviceNames.isEmpty()) {
            return new ProbeResult(null, Map.of());
        }
        String metricsJob = String.join(",", serviceNames);
        Map<String, List<String>> roleJobs = new LinkedHashMap<>();
        if (operatorArtifact != null && operatorArtifact.getRoles() != null) {
            for (K8sOperatorArtifact.Role role : operatorArtifact.getRoles()) {
                if (role.getName() == null || role.getJobPattern() == null) {
                    continue;
                }
                Pattern pattern = Pattern.compile(role.getJobPattern());
                List<String> jobs = serviceNames.stream()
                        .filter(job -> pattern.matcher(job).find())
                        .sorted()
                        .toList();
                if (!jobs.isEmpty()) {
                    roleJobs.put(role.getName(), jobs);
                }
            }
        }
        return new ProbeResult(metricsJob, roleJobs);
    }

    /**
     * name-prefix 启发式：operator 管理的资源没有 {@code app.kubernetes.io/instance} 这类 Helm
     * 标准标签，改按"该 namespace 下 Service 名以 CR 实例名为前缀"定位（Doris/Nacos 两案例实测验证）。
     * 用 {@code prefix + "-"} 而非裸 {@code startsWith(prefix)}，避免 {@code nacos} 误吃到
     * {@code nacosxyz} 这类同前缀不同实体的 Service。
     */
    private Set<String> serviceNamesByNamePrefix(K8sClusterConfig config, String namePrefix, String namespace) {
        try {
            List<K8sService> services = k8sService.batchExec(config,
                    client -> client.getServices(namespace, null).getItems(),
                    "按前缀查询命名空间 Service");
            Set<String> names = new LinkedHashSet<>();
            for (K8sService service : services) {
                if (service.getMetadata() == null || service.getMetadata().getName() == null) {
                    continue;
                }
                String name = service.getMetadata().getName();
                if (name.equals(namePrefix) || name.startsWith(namePrefix + "-")) {
                    names.add(name);
                }
            }
            return names;
        } catch (Exception e) {
            log.warn("按前缀 {} 查询命名空间 {} 的 Service 失败：{}", namePrefix, namespace, e.getMessage());
            return new LinkedHashSet<>();
        }
    }

    private Set<String> serviceNamesOf(K8sClusterConfig config, String releaseName, String namespace) {
        try {
            List<K8sService> services = k8sService.batchExec(config,
                    client -> client.getServices(namespace, RELEASE_LABEL + "=" + releaseName).getItems(),
                    "查询 release 的 Service");
            Set<String> names = new LinkedHashSet<>();
            for (K8sService service : services) {
                if (service.getMetadata() != null && service.getMetadata().getName() != null) {
                    names.add(service.getMetadata().getName());
                }
            }
            return names;
        } catch (Exception e) {
            log.warn("查询 release {} 的 Service 失败：{}", releaseName, e.getMessage());
            return new LinkedHashSet<>();
        }
    }
}
