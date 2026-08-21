package com.datasophon.api.service.k8s;

import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.api.service.frame.FrameK8sServiceService;
import com.datasophon.api.service.instance.K8sServiceInstanceService;
import com.datasophon.api.vo.k8s.K8sTakeoverScanResult;
import com.datasophon.common.k8s.vo.helm.HelmReleaseListItemVO;
import com.datasophon.common.model.k8s.K8sArtifact;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.entity.frame.FrameK8sServiceEntity;
import com.datasophon.dao.enums.k8s.InstanceSource;
import com.datasophon.dao.enums.k8s.InstanceSourceKind;
import com.datasophon.dao.vo.instance.K8sServiceInstanceVO;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSONObject;

/**
 * 扫描目标集群已存在的 Helm release，并与框架服务定义做匹配。
 *
 * <p>只读：本类不向目标集群写入任何内容。
 */
@Service
public class K8sTakeoverScanService {

    private final HelmReleaseReader helmReleaseReader;
    private final K8sCrScanner k8sCrScanner;
    private final FrameK8sServiceService frameK8sServiceService;
    private final K8sClusterConfigService k8sClusterConfigService;
    private final K8sServiceInstanceService k8sServiceInstanceService;
    private final K8sTakeoverReconcileService reconcileService;

    public K8sTakeoverScanService(HelmReleaseReader helmReleaseReader,
                                  K8sCrScanner k8sCrScanner,
                                  FrameK8sServiceService frameK8sServiceService,
                                  K8sClusterConfigService k8sClusterConfigService,
                                  K8sServiceInstanceService k8sServiceInstanceService,
                                  K8sTakeoverReconcileService reconcileService) {
        this.helmReleaseReader = helmReleaseReader;
        this.k8sCrScanner = k8sCrScanner;
        this.frameK8sServiceService = frameK8sServiceService;
        this.k8sClusterConfigService = k8sClusterConfigService;
        this.k8sServiceInstanceService = k8sServiceInstanceService;
        this.reconcileService = reconcileService;
    }

    /**
     * 扫描集群内 deployed 状态的 release，按 chart 名匹配框架服务定义。
     *
     * @param clusterId 集群 ID
     * @return 匹配结果，未匹配的进入 pending 待人工绑定
     */
    public K8sTakeoverScanResult scan(Integer clusterId) {
        K8sClusterConfig config = k8sClusterConfigService.getByClusterId(clusterId);
        if (config == null) {
            throw new BusinessHintException("集群未配置 K8s 连接信息，无法扫描");
        }
        List<FrameK8sServiceEntity> definitions = frameK8sServiceService.listNewest(clusterId);
        List<HelmReleaseListItemVO> releases = helmReleaseReader.listDeployed(config);
        K8sCrScanner.CrScanResult crScanResult = k8sCrScanner.scan(config, definitions);
        List<K8sCrScanner.ScannedCr> crs = crScanResult.crs();

        // 已登记的接管实例，用来判定「已接管」与「失联」两个方向
        List<K8sServiceInstanceVO> imported = k8sServiceInstanceService.queryInstanceList(clusterId).stream()
                .filter(instance -> InstanceSource.IMPORTED.equals(instance.getSource()))
                .toList();
        Set<String> registeredKeys = imported.stream()
                .map(instance -> releaseKey(instance.getSourceKind(),
                        instance.getNamespace(), instance.getReleaseName()))
                .collect(Collectors.toSet());
        Set<String> deployedKeys = new HashSet<>();
        releases.forEach(release -> deployedKeys.add(
                releaseKey(InstanceSourceKind.HELM, release.getNamespace(), release.getName())));
        crs.forEach(cr -> deployedKeys.add(releaseKey(InstanceSourceKind.CR, cr.namespace(), cr.name())));

        List<K8sTakeoverScanResult.ScannedRelease> matched = new ArrayList<>();
        List<K8sTakeoverScanResult.ScannedRelease> pending = new ArrayList<>();
        ChartIndex chartIndex = ChartIndex.of(definitions);
        for (HelmReleaseListItemVO release : releases) {
            FrameK8sServiceEntity definition = chartIndex.match(release);
            boolean registered = registeredKeys.contains(
                    releaseKey(InstanceSourceKind.HELM, release.getNamespace(), release.getName()));
            if (definition == null) {
                pending.add(toScanned(release, null, registered));
            } else {
                matched.add(toScanned(release, definition, registered));
            }
        }
        for (K8sCrScanner.ScannedCr cr : crs) {
            String key = releaseKey(InstanceSourceKind.CR, cr.namespace(), cr.name());
            boolean registered = registeredKeys.contains(key);
            matched.add(toScanned(cr, registered));
        }

        // CR 扫描不完整（有 CRD 失败）时跳过 CR 类目的 missing 判定：本次没扫到不等于确认不存在，
        // 一次 API server 抖动 / RBAC 变更就会让全部已登记 CR 实例被误判为失联。Helm 类目走独立的
        // deployedKeys 来源（helm list 失败直接抛异常向上传播，不会静默产出空结果），不受影响。
        boolean skipCrMissing = !crScanResult.complete();
        List<K8sTakeoverScanResult.MissingInstance> missing = imported.stream()
                .filter(instance -> !(skipCrMissing && InstanceSourceKind.CR.equals(instance.getSourceKind())))
                .filter(instance -> !deployedKeys.contains(
                        releaseKey(instance.getSourceKind(),
                                instance.getNamespace(), instance.getReleaseName())))
                .map(instance -> new K8sTakeoverScanResult.MissingInstance(
                        instance.getId(), instance.getReleaseName(),
                        instance.getNamespace(), instance.getServiceName()))
                .toList();

        // 刚拿到最新的集群状态，让轻对账缓存立刻跟上，避免重扫完侧边栏还挂着旧标记
        reconcileService.evict(clusterId);
        return new K8sTakeoverScanResult(matched, pending, missing, crScanResult.failedCrds());
    }

    /**
     * chart 名 → 框架服务定义的预建索引。
     *
     * <p>按 release 逐个线性扫 definitions 时，每次比对都要重新 {@code parseObject} 一遍
     * 同一份 artifact JSON（release 数 × 定义数 次解析）。这里改为进循环前一次性建索引，
     * 每份 artifact 只解析一次，匹配退化为两次 O(1) 查表。
     *
     * <p>两张表分开存而不是合并成一张，是为了保持 {@link #match} 原有的优先级语义：
     * 服务名匹配整体优先于 artifact chart 名匹配。同名冲突时用 {@code putIfAbsent}
     * 保留先出现的定义，与原先「返回第一个命中」一致。
     */
    private record ChartIndex(Map<String, FrameK8sServiceEntity> byServiceName,
                             Map<String, FrameK8sServiceEntity> byArtifactChartName) {

        static ChartIndex of(List<FrameK8sServiceEntity> definitions) {
            Map<String, FrameK8sServiceEntity> byServiceName = new HashMap<>();
            Map<String, FrameK8sServiceEntity> byArtifactChartName = new HashMap<>();
            for (FrameK8sServiceEntity definition : definitions) {
                if (definition.getServiceName() != null) {
                    byServiceName.putIfAbsent(definition.getServiceName(), definition);
                }
                String fromArtifact = chartNameOfArtifact(definition);
                if (fromArtifact != null) {
                    byArtifactChartName.putIfAbsent(fromArtifact, definition);
                }
            }
            return new ChartIndex(byServiceName, byArtifactChartName);
        }

        /**
         * 匹配规则，按优先级：
         * <ol>
         *   <li>框架服务名 == chart 名</li>
         *   <li>{@code artifact.helm}（Nexus 上的 chart 包名，如 {@code apisix-2.12.5.tgz}）去掉版本与后缀后 == chart 名</li>
         * </ol>
         *
         * <p>不用 release 名匹配：实测 release 名与 chart 名可以不同，
         * 如 release {@code fdb-cluster} 对应 chart {@code fdb-operator}。
         */
        FrameK8sServiceEntity match(HelmReleaseListItemVO release) {
            String chartName = release.chartName();
            if (chartName == null) {
                return null;
            }
            FrameK8sServiceEntity byName = byServiceName.get(chartName);
            return byName != null ? byName : byArtifactChartName.get(chartName);
        }
    }

    /** 从 {@code artifact.helm} 的包名里截出 chart 名，如 {@code apisix-2.12.5.tgz} → {@code apisix}。 */
    private static String chartNameOfArtifact(FrameK8sServiceEntity definition) {
        if (definition.getArtifact() == null || definition.getArtifact().isBlank()) {
            return null;
        }
        K8sArtifact artifact = JSONObject.parseObject(definition.getArtifact(), K8sArtifact.class);
        if (artifact == null || artifact.getHelm() == null || artifact.getHelm().isBlank()) {
            return null;
        }
        String helm = artifact.getHelm();
        int suffix = helm.lastIndexOf(".tgz");
        if (suffix > 0) {
            helm = helm.substring(0, suffix);
        }
        int separator = helm.lastIndexOf('-');
        return separator < 0 ? helm : helm.substring(0, separator);
    }

    private String releaseKey(InstanceSourceKind sourceKind, String namespace, String releaseName) {
        if (sourceKind == null) {
            throw new BusinessHintException("接管实例缺少来源类型");
        }
        return sourceKind.name() + ":" + namespace + "/" + releaseName;
    }

    private K8sTakeoverScanResult.ScannedRelease toScanned(HelmReleaseListItemVO release,
                                                           FrameK8sServiceEntity definition,
                                                           boolean registered) {
        return new K8sTakeoverScanResult.ScannedRelease(
                release.getName(),
                release.getNamespace(),
                release.getChart(),
                release.chartName(),
                release.chartVersion(),
                definition == null ? null : definition.getId(),
                definition == null ? null : definition.getServiceName(),
                definition == null ? null : definition.getType(),
                registered,
                InstanceSourceKind.HELM);
    }

    /** CR 结果全部来自按已知 definition 主动扫描（见 {@link K8sCrScanner}），不存在"未匹配"的情况。 */
    private K8sTakeoverScanResult.ScannedRelease toScanned(K8sCrScanner.ScannedCr cr, boolean registered) {
        FrameK8sServiceEntity definition = cr.definition();
        return new K8sTakeoverScanResult.ScannedRelease(
                cr.name(),
                cr.namespace(),
                null,
                null,
                null,
                definition.getId(),
                definition.getServiceName(),
                definition.getType(),
                registered,
                InstanceSourceKind.CR);
    }
}
