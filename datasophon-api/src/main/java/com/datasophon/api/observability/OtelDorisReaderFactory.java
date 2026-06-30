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
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.ServiceRoleState;

import jakarta.annotation.PreDestroy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.zaxxer.hikari.HikariDataSource;

@Component
public class OtelDorisReaderFactory {
    
    private static final Logger log = LoggerFactory.getLogger(OtelDorisReaderFactory.class);
    
    private final ClusterServiceRoleInstanceService roleService;
    private final ClusterVariableService variableService;
    private final OtelCredentialService credentialService;
    private final Map<PoolKey, HikariDataSource> pools = new ConcurrentHashMap<>();
    
    /** 开发/测试直连兜底：配置后跳过集群注册表查询，直连指定 Doris FE 主机。生产环境留空。 */
    @Value("${datasophon.otel.doris.fallback-host:}")
    private String fallbackHost;
    
    @Value("${datasophon.otel.doris.fallback-port:9030}")
    private String fallbackPort;
    
    @Value("${datasophon.otel.doris.fallback-password:}")
    private String fallbackPassword;
    
    public OtelDorisReaderFactory(ClusterServiceRoleInstanceService roleService,
                                  ClusterVariableService variableService,
                                  OtelCredentialService credentialService) {
        this.roleService = roleService;
        this.variableService = variableService;
        this.credentialService = credentialService;
    }
    
    /** 用 otel_reader 账号（SELECT-only，满足 F1 凭据隔离）创建 JdbcClient。 */
    public JdbcClient create(Integer clusterId) {
        // 开发直连兜底：配置 datasophon.otel.doris.fallback-host 后跳过集群注册表
        if (fallbackHost != null && !fallbackHost.isBlank()) {
            log.debug("Using Doris fallback connection {}:{}", fallbackHost, fallbackPort);
            return buildJdbcClient(fallbackHost, fallbackPort, "root", fallbackPassword);
        }
        
        List<ClusterServiceRoleInstanceEntity> fes = roleService
                .getServiceRoleInstanceListByClusterIdAndRoleName(clusterId, "DorisFE")
                .stream()
                .filter(r -> ServiceRoleState.RUNNING.equals(r.getServiceRoleState()))
                .toList();
        if (fes.isEmpty()) {
            throw new IllegalStateException("No running DorisFE for cluster " + clusterId);
        }
        String port = variableValue(clusterId, "query_port", "9030");
        String password = credentialService.getOrCreate(clusterId).readerPassword();
        return buildJdbcClient(fes.get(0).getHostname(), port, "otel_reader", password);
    }
    
    private JdbcClient buildJdbcClient(String host, String port, String user, String password) {
        PoolKey key = new PoolKey(host, port, user, password);
        return JdbcClient.create(pools.computeIfAbsent(key, OtelDorisReaderFactory::newDataSource));
    }
    
    private static HikariDataSource newDataSource(PoolKey key) {
        HikariDataSource ds = new HikariDataSource();
        ds.setDriverClassName("com.mysql.cj.jdbc.Driver");
        ds.setJdbcUrl("jdbc:mysql://" + key.host() + ":" + key.port()
                + "/?useUnicode=true&characterEncoding=utf8&useSSL=false");
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
        pools.values().forEach(HikariDataSource::close);
        pools.clear();
    }
    
    int poolSizeForTest() {
        return pools.size();
    }
    
    private String variableValue(Integer clusterId, String name, String defaultValue) {
        var v = variableService.getVariableByVariableName(clusterId, "DORIS", name);
        return v == null ? defaultValue : v.getVariableValue();
    }
    
    private record PoolKey(String host, String port, String user, String password) {
    }
}
