/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.datasophon.api.service.k8s;

import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.enums.k8s.InstanceSource;
import com.datasophon.dao.enums.k8s.InstanceSourceKind;
import com.datasophon.dao.vo.instance.K8sServiceInstanceVO;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 接管实例的轻对账：标记 release 已从目标集群消失的登记项。
 *
 * <p>实例列表接口是前端每 3 秒轮询的（见 {@code Cluster/Layout} 的 setInterval），
 * 所以这里有两条硬约束：
 * <ol>
 *   <li>整批只发**一次** kubectl，且只用 jsonpath 取 Secret 的标签，不拉 release 内容；</li>
 *   <li>结果带 TTL 缓存，把 3 秒一次的轮询摊薄成 {@value #CACHE_TTL_SECONDS} 秒一次的实查。</li>
 * </ol>
 *
 * <p>只在返回值上打标记，**不写库**——失联判定要落库由 T-A11 的重新扫描负责，
 * 否则轮询会变成每 3 秒一次的写风暴。
 */
@Service
public class K8sTakeoverReconcileService {

    private static final Logger log = LoggerFactory.getLogger(K8sTakeoverReconcileService.class);

    /** 缓存有效期，取值只需远大于 3 秒轮询间隔，同时小到能让人工操作后较快看到变化。 */
    private static final int CACHE_TTL_SECONDS = 30;

    private final K8sClusterConfigService k8sClusterConfigService;
    private final K8sService k8sService;

    private final Map<Integer, CachedKeys> cache = new ConcurrentHashMap<>();

    public K8sTakeoverReconcileService(K8sClusterConfigService k8sClusterConfigService,
                                       K8sService k8sService) {
        this.k8sClusterConfigService = k8sClusterConfigService;
        this.k8sService = k8sService;
    }

    /**
     * 给接管实例打失联标记；平台安装的实例原样不动。
     *
     * <p>查询失败时**不标记任何实例**：把「查不到集群」误报成「服务没了」比不报更糟。
     *
     * <p>{@code sourceKind=CR} 的实例本就不跳过——但 {@link #deployedReleaseKeys} 只查 Helm release
     * 的 Secret 标签（性能优化，见类注释），CR 实例的 key 永远不在这个集合里，跳过它们的判定，
     * 避免侧边栏对 CR 来源实例持续误报「失联」。CR 的失联判定交给「重新扫描」（{@code
     * K8sTakeoverScanService}）负责。
     *
     * @param clusterId 集群 ID
     * @param instances 待标记的实例列表（就地修改）
     */
    public void markMissing(Integer clusterId, Collection<K8sServiceInstanceVO> instances) {
        if (instances == null || instances.isEmpty()) {
            return;
        }
        boolean hasImported = instances.stream()
                .anyMatch(instance -> InstanceSource.IMPORTED.name().equals(instance.getSource())
                        && !InstanceSourceKind.CR.name().equals(instance.getSourceKind()));
        if (!hasImported) {
            return;
        }
        Set<String> deployed = deployedReleaseKeys(clusterId);
        if (deployed == null) {
            return;
        }
        for (K8sServiceInstanceVO instance : instances) {
            if (!InstanceSource.IMPORTED.name().equals(instance.getSource())
                    || InstanceSourceKind.CR.name().equals(instance.getSourceKind())) {
                continue;
            }
            instance.setMissing(!deployed.contains(releaseKey(instance)));
        }
    }

    /**
     * 强制让缓存失效，供重新扫描后立刻反映最新状态。
     */
    public void evict(Integer clusterId) {
        cache.remove(clusterId);
    }

    /**
     * @return 集群内全部 release 的 {@code namespace/name}；查询失败返回 null（区别于「一个都没有」）
     */
    Set<String> deployedReleaseKeys(Integer clusterId) {
        K8sClusterConfig config = k8sClusterConfigService.getByClusterId(clusterId);
        if (config == null) {
            return null;
        }
        // 用 compute() 而不是 get()+put() 两步：TTL 到期瞬间多个请求（同一集群的多个浏览器
        // Tab 都在 3 秒轮询）会同时判定缓存过期，各自发一次 kubectl，造成穿透风暴。compute()
        // 对同一 clusterId 的并发调用天然排队——只有第一个真正跑 kubectl（30s 超时封顶，不会
        // 无界阻塞），后来者拿到它算出来的新值直接返回，不重复发请求。
        AtomicReference<Set<String>> fresh = new AtomicReference<>();
        cache.compute(clusterId, (id, current) -> {
            if (current != null && !current.isExpired()) {
                fresh.set(current.keys());
                return current;
            }
            try {
                Set<String> keys = Set.copyOf(k8sService.listHelmReleaseKeys(config));
                fresh.set(keys);
                return new CachedKeys(keys, System.nanoTime());
            } catch (Exception e) {
                log.warn("集群 {} 的 Helm release 对账查询失败，本次不标记失联：{}", clusterId, e.getMessage());
                // 保持原状（可能是 null，也可能是已过期但还没被覆盖的旧值），不缓存失败结果，
                // 下一次调用会再次尝试；fresh 留空，方法整体返回 null。
                return current;
            }
        });
        return fresh.get();
    }

    private String releaseKey(K8sServiceInstanceVO instance) {
        return instance.getNamespace() + "/" + instance.getReleaseName();
    }

    private record CachedKeys(Set<String> keys, long storedAtNanos) {

        boolean isExpired() {
            return System.nanoTime() - storedAtNanos > Duration.ofSeconds(CACHE_TTL_SECONDS).toNanos();
        }
    }
}
