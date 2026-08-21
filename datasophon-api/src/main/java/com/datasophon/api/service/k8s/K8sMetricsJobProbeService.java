package com.datasophon.api.service.k8s;

import com.datasophon.api.observability.OtelDorisReaderFactory;
import com.datasophon.common.k8s.vo.k8s.K8sResourceList;
import com.datasophon.common.k8s.vo.k8s.K8sService;
import com.datasophon.common.model.k8s.K8sOperatorArtifact;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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

    /** 探测时回看的时间窗口，覆盖 TargetAllocator 的抓取间隔即可。 */
    private static final int LOOKBACK_HOURS = 1;

    private final com.datasophon.api.service.k8s.K8sService k8sService;
    private final OtelDorisReaderFactory readerFactory;

    public K8sMetricsJobProbeService(com.datasophon.api.service.k8s.K8sService k8sService,
                                     OtelDorisReaderFactory readerFactory) {
        this.k8sService = k8sService;
        this.readerFactory = readerFactory;
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

    /** 探测结果：{@code metricsJob} 沿用既有逗号分隔格式；{@code roleJobs} 是角色名到其 job 列表的映射。 */
    public record ProbeResult(String metricsJob, Map<String, List<String>> roleJobs) {
    }

    /**
     * 一次读取全集 Service，供同一批接管登记的多个 binding 复用。
     *
     * <p>查询失败时返回空集，保持登记流程「采集诊断失败不阻断本地登记」的既有语义。
     */
    public List<K8sService> allServices(K8sClusterConfig config) {
        try {
            K8sResourceList<K8sService> result = k8sService.batchExec(config,
                    client -> client.getServicesAllNamespaces(), "查询集群全部 Service");
            return result == null || result.getItems() == null ? List.of() : result.getItems();
        } catch (Exception e) {
            log.warn("查询集群全部 Service 失败：{}", e.getMessage());
            return List.of();
        }
    }

    /**
     * CR 来源服务的探测重载：{@code operatorArtifact} 非空时，Service 定位改用 name-prefix 启发式
     * （operator 管理的资源没有 Helm 标准标签），并按 {@code operatorArtifact.roles} 的正则把命中的
     * job 分类到角色桶里（如 fe/compute），供前端按角色分流查询。
     */
    public ProbeResult probe(K8sClusterConfig config, String releaseName, String namespace,
                             Set<String> activeJobs, K8sOperatorArtifact operatorArtifact) {
        String labelSelector = operatorArtifact == null
                ? com.datasophon.api.service.k8s.K8sService.SRV_INST_ID_LABEL + "=" + releaseName
                : null;
        Predicate<String> nameFilter = operatorArtifact == null ? name -> true : namePrefixFilter(releaseName);
        Set<String> serviceNames = serviceNames(config, namespace, labelSelector, nameFilter,
                operatorArtifact == null ? "查询 release 的 Service" : "按前缀查询命名空间 Service");
        return probe(activeJobs, operatorArtifact, serviceNames);
    }

    /**
     * 使用批量读取的 Service 快照探测 job，避免每个 binding 重建 kubectl 客户端。
     */
    public ProbeResult probe(String releaseName, String namespace, Set<String> activeJobs,
                             K8sOperatorArtifact operatorArtifact, List<K8sService> services) {
        String labelSelector = operatorArtifact == null
                ? com.datasophon.api.service.k8s.K8sService.SRV_INST_ID_LABEL + "=" + releaseName
                : null;
        Predicate<String> nameFilter = operatorArtifact == null ? name -> true : namePrefixFilter(releaseName);
        return probe(activeJobs, operatorArtifact, serviceNames(services, namespace, labelSelector, nameFilter));
    }

    private ProbeResult probe(Set<String> activeJobs, K8sOperatorArtifact operatorArtifact, Set<String> serviceNames) {
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
                Pattern pattern;
                try {
                    pattern = Pattern.compile(role.getJobPattern());
                } catch (PatternSyntaxException e) {
                    // manifest.yaml 里的 jobPattern 写错正则不应该拖垮整个接管登记事务，
                    // 只跳过这一个角色，其它角色照常探测。
                    log.warn("角色 {} 的 jobPattern 不是合法正则：{}，跳过该角色", role.getName(), role.getJobPattern());
                    continue;
                }
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
    private Set<String> serviceNames(K8sClusterConfig config, String namespace, String labelSelector,
                                     Predicate<String> nameFilter, String actionHint) {
        try {
            List<K8sService> services = k8sService.batchExec(config,
                    client -> client.getServices(namespace, labelSelector).getItems(), actionHint);
            // kubectl 已按 namespace 过滤，避免要求测试/调用方再在 Metadata 上重复携带 namespace。
            return serviceNames(services, null, null, nameFilter);
        } catch (Exception e) {
            log.warn("查询命名空间 {} 的 Service 失败：{}", namespace, e.getMessage());
            return new LinkedHashSet<>();
        }
    }

    private Set<String> serviceNames(List<K8sService> services, String namespace, String labelSelector,
                                     Predicate<String> nameFilter) {
        Set<String> names = new LinkedHashSet<>();
        if (services == null) {
            return names;
        }
        for (K8sService service : services) {
            if (service.getMetadata() == null || service.getMetadata().getName() == null
                    || (namespace != null && !namespace.equals(service.getMetadata().getNamespace()))
                    || !matchesLabel(service, labelSelector)) {
                continue;
            }
            String name = service.getMetadata().getName();
            if (nameFilter.test(name)) {
                names.add(name);
            }
        }
        return names;
    }

    private static Predicate<String> namePrefixFilter(String prefix) {
        return name -> name.equals(prefix) || name.startsWith(prefix + "-");
    }

    private static boolean matchesLabel(K8sService service, String labelSelector) {
        if (labelSelector == null) {
            return true;
        }
        int equalsAt = labelSelector.indexOf('=');
        if (equalsAt < 1 || service.getMetadata().getLabels() == null) {
            return false;
        }
        return labelSelector.substring(equalsAt + 1)
                .equals(service.getMetadata().getLabels().get(labelSelector.substring(0, equalsAt)));
    }
}
