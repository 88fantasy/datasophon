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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.api.observability.ExternalOtelDatasourceProvider.ExternalDatasource;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.enums.ManageMode;
import com.datasophon.dao.mapper.ClusterInfoMapper;
import com.datasophon.dao.mapper.cluster.K8sClusterConfigMapper;

import java.util.Optional;

import org.junit.jupiter.api.Test;

class ImportedClusterOtelDatasourceProviderTest {

    @Test
    void resolvesRegisteredDorisForImportedCluster() {
        K8sClusterConfigMapper configMapper = configMapper(config("10.0.0.9", 9030));

        Optional<ExternalDatasource> found =
                new ImportedClusterOtelDatasourceProvider(clusterMapper(ManageMode.IMPORTED), configMapper).find(7);

        assertThat(found).contains(new ExternalDatasource("10.0.0.9", "9030"));
    }

    @Test
    void fallsBackToDefaultPortWhenNotRegistered() {
        K8sClusterConfigMapper configMapper = configMapper(config("10.0.0.9", null));

        Optional<ExternalDatasource> found =
                new ImportedClusterOtelDatasourceProvider(clusterMapper(ManageMode.IMPORTED), configMapper).find(7);

        assertThat(found).contains(new ExternalDatasource("10.0.0.9", "9030"));
    }

    @Test
    void ignoresRegisteredDorisForManagedCluster() {
        K8sClusterConfigMapper configMapper = configMapper(config("10.0.0.9", 9030));

        Optional<ExternalDatasource> found =
                new ImportedClusterOtelDatasourceProvider(clusterMapper(ManageMode.MANAGED), configMapper).find(7);

        assertThat(found).isEmpty();
        // 自建集群直接短路，不该白查一次 K8s 配置表
        verify(configMapper, never()).selectOne(any());
    }

    @Test
    void returnsEmptyWhenClusterMissing() {
        ClusterInfoMapper clusterMapper = mock(ClusterInfoMapper.class);
        when(clusterMapper.selectById(anyInt())).thenReturn(null);

        Optional<ExternalDatasource> found =
                new ImportedClusterOtelDatasourceProvider(clusterMapper, mock(K8sClusterConfigMapper.class)).find(7);

        assertThat(found).isEmpty();
    }

    @Test
    void returnsEmptyWhenDorisHostNotRegistered() {
        // 接管向导只填了 kubeconfig、还没做 Doris 数据源发现时的中间态
        assertThat(new ImportedClusterOtelDatasourceProvider(
                clusterMapper(ManageMode.IMPORTED), configMapper(null)).find(7)).isEmpty();
        assertThat(new ImportedClusterOtelDatasourceProvider(
                clusterMapper(ManageMode.IMPORTED), configMapper(config("  ", 9030))).find(7)).isEmpty();
    }

    private static K8sClusterConfig config(String host, Integer port) {
        K8sClusterConfig config = new K8sClusterConfig();
        config.setDorisHost(host);
        config.setDorisPort(port);
        return config;
    }

    private static K8sClusterConfigMapper configMapper(K8sClusterConfig config) {
        K8sClusterConfigMapper mapper = mock(K8sClusterConfigMapper.class);
        when(mapper.selectOne(any())).thenReturn(config);
        return mapper;
    }

    private static ClusterInfoMapper clusterMapper(ManageMode mode) {
        ClusterInfoMapper mapper = mock(ClusterInfoMapper.class);
        ClusterInfoEntity cluster = new ClusterInfoEntity();
        cluster.setManageMode(mode);
        when(mapper.selectById(anyInt())).thenReturn(cluster);
        return mapper;
    }
}
