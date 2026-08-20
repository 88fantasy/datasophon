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

package com.datasophon.api.observability;

import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.ClusterVariableService;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.enums.ManageMode;
import com.datasophon.dao.enums.ServiceRoleState;
import com.datasophon.dao.mapper.ClusterInfoMapper;
import com.datasophon.dao.mapper.cluster.K8sClusterConfigMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zaxxer.hikari.HikariDataSource;

import jakarta.annotation.PreDestroy;

@Component
public class OtelDorisReaderFactory {
    private static final String DEFAULT_READER_USER = "otel_reader";

    private static final Logger log = LoggerFactory.getLogger(OtelDorisReaderFactory.class);

    /** 接管集群缺省的 Doris MySQL 协议端口。 */
    private static final String DEFAULT_DORIS_PORT = "9030";

    private final ClusterServiceRoleInstanceService roleService;
    private final ClusterVariableService variableService;
    private final OtelCredentialService credentialService;
    private final ClusterInfoMapper clusterInfoMapper;
    /**
     * 直接依赖 Mapper 而非 K8sClusterConfigService：后者依赖链很深且已含自循环，
     * 本类是构造器注入（Spring 不容忍构造器循环依赖）。
     */
    private final K8sClusterConfigMapper k8sClusterConfigMapper;
    private final Map<Integer, PoolEntry> pools = new ConcurrentHashMap<>();

    /** 开发/测试直连兜底：配置后跳过集群注册表查询，直连指定 Doris FE 主机。生产环境留空。 */
    @Value("${datasophon.otel.doris.fallback-host:}")
    private String fallbackHost;

    @Value("${datasophon.otel.doris.fallback-port:9030}")
    private String fallbackPort;

    @Value("${datasophon.otel.doris.fallback-user:otel_reader}")
    private String fallbackUser = DEFAULT_READER_USER;

    @Value("${datasophon.otel.doris.fallback-password:}")
    private String fallbackPassword;

    public OtelDorisReaderFactory(ClusterServiceRoleInstanceService roleService,
                                  ClusterVariableService variableService,
                                  OtelCredentialService credentialService,
                                  ClusterInfoMapper clusterInfoMapper,
                                  K8sClusterConfigMapper k8sClusterConfigMapper) {
        this.roleService = roleService;
        this.variableService = variableService;
        this.credentialService = credentialService;
        this.clusterInfoMapper = clusterInfoMapper;
        this.k8sClusterConfigMapper = k8sClusterConfigMapper;
    }

    /** 用 otel_reader 账号（SELECT-only，满足 F1 凭据隔离）创建 JdbcClient。 */
    public JdbcClient create(Integer clusterId) {
        // 开发直连兜底：配置 datasophon.otel.doris.fallback-host 后跳过集群注册表
        if (fallbackHost != null && !fallbackHost.isBlank()) {
            log.debug("Using Doris fallback connection {}:{}", fallbackHost, fallbackPort);
            return buildJdbcClient(clusterId, fallbackHost, fallbackPort, fallbackReaderUser(), fallbackPassword);
        }

        // 接管集群：Doris 不由本平台安装，角色实例表里查不到，改用接管时登记的外部数据源
        JdbcClient external = createExternal(clusterId);
        if (external != null) {
            return external;
        }

        List<ClusterServiceRoleInstanceEntity> fes = roleService
                .getServiceRoleInstanceListByClusterIdAndRoleName(clusterId, "DorisFE")
                .stream()
                // EXISTS_ALARM 仍是可查询的活跃 FE（只是挂了一条告警规则），只有 STOP/DECOMMISSIONING/
                // DECOMMISSIONED 才代表进程真的不可用；此前只放行 RUNNING 导致任意一次告警（哪怕是
                // Doris 查询错误率刚好越过阈值的边缘毛刺）就会让全平台 OTel-Doris 查询链路全部 500。
                .filter(r -> ServiceRoleState.RUNNING.equals(r.getServiceRoleState())
                        || ServiceRoleState.EXISTS_ALARM.equals(r.getServiceRoleState()))
                .toList();
        if (fes.isEmpty()) {
            throw new IllegalStateException("No running DorisFE for cluster " + clusterId);
        }
        String port = variableValue(clusterId, "query_port", "9030");
        String password = credentialService.getOrCreate(clusterId).readerPassword();
        return buildJdbcClient(clusterId, fes.get(0).getHostname(), port, DEFAULT_READER_USER, password);
    }

    /**
     * 接管集群的外部 Doris 数据源。未登记 {@code doris_host} 时返回 null，由调用方回落到角色实例查询。
     *
     * <p>密码同样走 {@link OtelCredentialService}：接管时用户录入的密码被存为
     * {@code DORIS / otel_reader_password} 变量，该服务会优先返回它而不是随机生成。
     */
    private JdbcClient createExternal(Integer clusterId) {
        ClusterInfoEntity cluster = clusterInfoMapper.selectById(clusterId);
        if (cluster == null || cluster.getManageMode() != ManageMode.IMPORTED) {
            return null;
        }
        K8sClusterConfig config = k8sClusterConfigMapper.selectOne(
                new LambdaQueryWrapper<K8sClusterConfig>()
                        .eq(K8sClusterConfig::getClusterId, clusterId)
                        .last("limit 1"));
        if (config == null || config.getDorisHost() == null || config.getDorisHost().isBlank()) {
            return null;
        }
        String port = config.getDorisPort() == null ? DEFAULT_DORIS_PORT : String.valueOf(config.getDorisPort());
        String password = credentialService.getOrCreate(clusterId).readerPassword();
        log.debug("Using external Doris datasource {}:{} for imported cluster {}",
                config.getDorisHost(), port, clusterId);
        return buildJdbcClient(clusterId, config.getDorisHost(), port, DEFAULT_READER_USER, password);
    }

    private String fallbackReaderUser() {
        return fallbackUser == null || fallbackUser.isBlank() ? DEFAULT_READER_USER : fallbackUser;
    }

    private JdbcClient buildJdbcClient(Integer clusterId, String host, String port, String user, String password) {
        PoolKey key = new PoolKey(host, port, user, password);
        PoolEntry entry = pools.compute(clusterId, (id, current) -> {
            if (current != null && current.key().equals(key)) {
                return current;
            }
            HikariDataSource replacement = newDataSource(key);
            if (current != null) {
                current.dataSource().close();
            }
            return new PoolEntry(key, replacement);
        });
        return JdbcClient.create(entry.dataSource());
    }

    private static HikariDataSource newDataSource(PoolKey key) {
        HikariDataSource ds = new HikariDataSource();
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setJdbcUrl("jdbc:mysql://" + key.host() + ":" + key.port()
                + "/otel?useUnicode=true&characterEncoding=utf8&useSSL=false");
        ds.setUsername(key.user());
        ds.setPassword(key.password());
        ds.setPoolName("otel-doris-reader-" + key.host() + "-" + key.port() + "-" + key.user());
        ds.setMaximumPoolSize(8);
        ds.setMinimumIdle(0);
        ds.setConnectionTimeout(5000);
        ds.setIdleTimeout(300000);
        ds.setMaxLifetime(1800000);
        ds.setInitializationFailTimeout(-1);
        return ds;
    }

    @PreDestroy
    public void close() {
        pools.values().forEach(entry -> entry.dataSource().close());
        pools.clear();
    }

    /** 外部数据源配置变更后立即释放旧连接池。 */
    public void invalidate(Integer clusterId) {
        PoolEntry removed = pools.remove(clusterId);
        if (removed != null) {
            removed.dataSource().close();
        }
    }

    int poolSizeForTest() {
        return pools.size();
    }

    HikariDataSource dataSourceForTest(Integer clusterId) {
        PoolEntry entry = pools.get(clusterId);
        return entry == null ? null : entry.dataSource();
    }

    private String variableValue(Integer clusterId, String name, String defaultValue) {
        var v = variableService.getVariableByVariableName(clusterId, "DORIS", name);
        return v == null ? defaultValue : v.getVariableValue();
    }

    private record PoolKey(String host, String port, String user, String password) {
    }

    private record PoolEntry(PoolKey key, HikariDataSource dataSource) {
    }
}
