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

import com.datasophon.api.observability.ExternalOtelDatasourceProvider;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.enums.ManageMode;
import com.datasophon.dao.mapper.ClusterInfoMapper;
import com.datasophon.dao.mapper.cluster.K8sClusterConfigMapper;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

/**
 * 接管（{@link ManageMode#IMPORTED}）集群在接管向导里登记的外部 Doris 地址，
 * 由 {@link DorisDatasourceDiscoveryService} 写入 {@code t_ddh_k8s_cluster_config}。
 *
 * <p><b>为什么直连 Mapper 而不是 {@code K8sClusterConfigService}</b>：后者的依赖图会绕回本接口的
 * 消费方，形成 {@code OtelDorisReaderFactory → 本类 → K8sClusterConfigService → ClusterInfoService
 * → ClusterDeleteService → OtelDorisReaderFactory} 的构造环。本类只需要一次主键读，
 * 用 Mapper（叶子 Bean）既避免了这个环，也不必为省一次查询给全链路挂 {@code @Lazy}。
 * 若将来要给读路径加缓存/校验，加在本类里，不要改成注入 Service。
 */
@Service
public class ImportedClusterOtelDatasourceProvider implements ExternalOtelDatasourceProvider {

    /** 接管集群缺省的 Doris MySQL 协议端口。 */
    private static final String DEFAULT_DORIS_PORT = "9030";

    private final ClusterInfoMapper clusterInfoMapper;
    private final K8sClusterConfigMapper k8sClusterConfigMapper;

    public ImportedClusterOtelDatasourceProvider(ClusterInfoMapper clusterInfoMapper,
                                                 K8sClusterConfigMapper k8sClusterConfigMapper) {
        this.clusterInfoMapper = clusterInfoMapper;
        this.k8sClusterConfigMapper = k8sClusterConfigMapper;
    }

    @Override
    public Optional<ExternalDatasource> find(Integer clusterId) {
        ClusterInfoEntity cluster = clusterInfoMapper.selectById(clusterId);
        if (cluster == null || cluster.getManageMode() != ManageMode.IMPORTED) {
            return Optional.empty();
        }
        K8sClusterConfig config = k8sClusterConfigMapper.selectOne(
                new LambdaQueryWrapper<K8sClusterConfig>()
                        .eq(K8sClusterConfig::getClusterId, clusterId)
                        .last("limit 1"));
        if (config == null || config.getDorisHost() == null || config.getDorisHost().isBlank()) {
            return Optional.empty();
        }
        String port = config.getDorisPort() == null ? DEFAULT_DORIS_PORT : String.valueOf(config.getDorisPort());
        return Optional.of(new ExternalDatasource(config.getDorisHost(), port));
    }
}
