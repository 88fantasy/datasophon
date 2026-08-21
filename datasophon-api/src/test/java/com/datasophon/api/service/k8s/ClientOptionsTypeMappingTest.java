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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.common.k8s.config.ClientOptions;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.enums.ManageMode;
import com.datasophon.dao.enums.k8s.K8sAuthType;

import org.junit.jupiter.api.Test;

/**
 * 锁定 {@link K8sClusterConfig} → {@link ClientOptions} 的凭据与只读模式传递契约。
 *
 * <p>公共模块只依赖 {@code K8sClientConfig}，而 API 侧工厂负责从集群管理模式计算
 * {@code readOnly}，避免公共模块反向依赖 DAO 实体。
 */
class ClientOptionsTypeMappingTest {

    @Test
    void fromCarriesAuthTypeIntoClientOptionsAsEnumName() {
        for (K8sAuthType type : K8sAuthType.values()) {
            K8sClusterConfig config = new K8sClusterConfig();
            config.setType(type);

            ClientOptions options = ClientOptions.from(config, false);

            assertEquals(type.name(), options.getType(),
                    String.format("K8sAuthType.%s 未能传递到 ClientOptions.type，SecureKubeConfigWriter 会退回按非空猜测凭据", type));
        }
    }

    /**
     * {@code SecureKubeConfigWriter} 里的 TYPE_* 常量是私有的字面量副本，
     * 与本枚举没有编译期绑定，这里正面锁住取值。
     */
    @Test
    void authTypeNamesMatchSecureKubeConfigWriterConstants() {
        assertEquals("config_file", K8sAuthType.config_file.name());
        assertEquals("token", K8sAuthType.token.name());
        assertEquals("password", K8sAuthType.password.name());
    }

    @Test
    void importedClusterConfigMapsToReadOnlyClientOptions() {
        ClusterInfoService clusterInfoService = mock(ClusterInfoService.class);
        ClusterInfoEntity cluster = new ClusterInfoEntity();
        cluster.setManageMode(ManageMode.IMPORTED);
        when(clusterInfoService.getById(7)).thenReturn(cluster);
        K8sClusterConfig config = config(7);

        ClientOptions options = new K8sClientOptionsFactory(clusterInfoService).from(config);

        assertTrue(options.isReadOnly());
        assertEquals("https://k8s.example:6443", options.getServerName());
        assertEquals(K8sAuthType.token.name(), options.getType());
    }

    @Test
    void managedClusterConfigMapsToWritableClientOptions() {
        ClusterInfoService clusterInfoService = mock(ClusterInfoService.class);
        ClusterInfoEntity cluster = new ClusterInfoEntity();
        cluster.setManageMode(ManageMode.MANAGED);
        when(clusterInfoService.getById(7)).thenReturn(cluster);

        assertFalse(new K8sClientOptionsFactory(clusterInfoService).from(config(7)).isReadOnly());
    }

    /**
     * default-deny 的核心用例：集群记录查不到时必须按只读处理。
     *
     * <p>若这里退回可写，平台就可能在集群数据异常时向一个来历不明的目标集群下发
     * helm upgrade/uninstall——这是接管功能唯一不能破的红线。
     */
    @Test
    void missingClusterRecordFallsBackToReadOnly() {
        ClusterInfoService clusterInfoService = mock(ClusterInfoService.class);
        when(clusterInfoService.getById(7)).thenReturn(null);

        assertTrue(new K8sClientOptionsFactory(clusterInfoService).from(config(7)).isReadOnly(),
                "集群记录查不到时必须只读，否则接管集群会因查询失败被误判为可写");
    }

    @Test
    void nullClusterIdFallsBackToReadOnly() {
        ClusterInfoService clusterInfoService = mock(ClusterInfoService.class);

        assertTrue(new K8sClientOptionsFactory(clusterInfoService).from(config(null)).isReadOnly(),
                "clusterId 为空时无从判定归属，必须只读");
    }

    @Test
    void nullManageModeFallsBackToReadOnly() {
        ClusterInfoService clusterInfoService = mock(ClusterInfoService.class);
        ClusterInfoEntity cluster = new ClusterInfoEntity();
        cluster.setManageMode(null);
        when(clusterInfoService.getById(7)).thenReturn(cluster);

        assertTrue(new K8sClientOptionsFactory(clusterInfoService).from(config(7)).isReadOnly(),
                "manageMode 缺失时必须只读——只有确认为 MANAGED 才放行写操作");
    }

    private static K8sClusterConfig config(Integer clusterId) {
        K8sClusterConfig config = new K8sClusterConfig();
        config.setClusterId(clusterId);
        config.setType(K8sAuthType.token);
        config.setServerHost("https://k8s.example:6443");
        config.setToken("token");
        return config;
    }
}
