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

package com.datasophon.api.doris;

import com.datasophon.api.observability.ExternalOtelDatasourceProvider;
import com.datasophon.api.observability.OtelCredentialService;
import com.datasophon.api.observability.OtelCredentials;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.ClusterVariableService;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.entity.ClusterVariable;
import com.datasophon.dao.enums.ServiceRoleState;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.zaxxer.hikari.HikariDataSource;

import jakarta.annotation.PreDestroy;

/** Creates the separate Doris connection used by the active-task read path. */
@Component
public class DorisAdminReaderFactory {

    public static final String ROOT_USER = "root";
    public static final String READER_USER = "otel_reader";
    public static final int DEFAULT_QUERY_PORT = 9030;
    public static final String ROOT_FALLBACK_REASON = "root 连接不可用，已回落到只读账号";

    private static final int CONNECTION_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;
    private static final String DORIS_FE_ROLE = "DorisFE";

    private static final Logger log = LoggerFactory.getLogger(DorisAdminReaderFactory.class);

    private final ClusterServiceRoleInstanceService roleService;
    private final ClusterVariableService variableService;
    private final OtelCredentialService credentialService;
    private final ExternalOtelDatasourceProvider externalDatasourceProvider;
    private final ConnectionVerifier connectionVerifier;
    private final Map<Integer, PoolEntry> pools = new ConcurrentHashMap<>();

    @Autowired
    public DorisAdminReaderFactory(ClusterServiceRoleInstanceService roleService,
                                   ClusterVariableService variableService,
                                   OtelCredentialService credentialService,
                                   ExternalOtelDatasourceProvider externalDatasourceProvider) {
        this(roleService, variableService, credentialService, externalDatasourceProvider,
                DorisAdminReaderFactory::verifyConnection);
    }

    DorisAdminReaderFactory(ClusterServiceRoleInstanceService roleService,
                            ClusterVariableService variableService,
                            OtelCredentialService credentialService,
                            ExternalOtelDatasourceProvider externalDatasourceProvider,
                            ConnectionVerifier connectionVerifier) {
        this.roleService = roleService;
        this.variableService = variableService;
        this.credentialService = credentialService;
        this.externalDatasourceProvider = externalDatasourceProvider;
        this.connectionVerifier = connectionVerifier;
    }

    /**
     * Resolves a read connection and reports the endpoint actually used.
     *
     * <p>Physical clusters try the stored root password first. Imported clusters have no platform
     * root password source and use the already provisioned otel_reader account directly.
     */
    public DorisAdminConnection create(Integer clusterId) {
        Optional<ExternalOtelDatasourceProvider.ExternalDatasource> external =
                externalDatasourceProvider.find(clusterId);
        if (external.isPresent()) {
            ExternalOtelDatasourceProvider.ExternalDatasource datasource = external.get();
            int port = parsePort(datasource.port());
            return createReaderConnection(clusterId, datasource.host(), port, false, null);
        }

        Endpoint endpoint = physicalEndpoint(clusterId);
        String rootPassword = variableValue(clusterId, "root_password");
        if (rootPassword != null && !rootPassword.isBlank()) {
            try {
                return createConnection(clusterId, endpoint, ROOT_USER, rootPassword, false, null);
            } catch (DorisConnectionException e) {
                log.warn("Doris root connection unavailable for cluster {} at {}, using reader account",
                        clusterId, endpoint.hostPort());
            }
        }
        return createReaderConnection(clusterId, endpoint.host(), endpoint.port(), true, ROOT_FALLBACK_REASON);
    }

    private DorisAdminConnection createReaderConnection(Integer clusterId, String host, int port,
                                                        boolean degraded, String degradedReason) {
        OtelCredentials credentials = credentialService.getOrCreate(clusterId);
        return createConnection(clusterId, new Endpoint(host, port), READER_USER,
                credentials.readerPassword(), degraded, degradedReason);
    }

    private DorisAdminConnection createConnection(Integer clusterId, Endpoint endpoint, String username,
                                                  String password, boolean degraded, String degradedReason) {
        PoolKey key = new PoolKey(endpoint.host(), endpoint.port(), username, password);
        AtomicReference<HikariDataSource> obsolete = new AtomicReference<>();
        PoolEntry entry = pools.compute(clusterId, (id, current) -> {
            if (current != null && current.key().equals(key)) {
                return current;
            }
            HikariDataSource dataSource = newDataSource(key);
            try {
                if (!connectionVerifier.verify(dataSource)) {
                    dataSource.close();
                    throw new DorisConnectionException();
                }
            } catch (SQLException | RuntimeException e) {
                dataSource.close();
                if (e instanceof DorisConnectionException connectionException) {
                    throw connectionException;
                }
                throw new DorisConnectionException();
            }
            if (current != null) {
                obsolete.set(current.dataSource());
            }
            return new PoolEntry(key, dataSource);
        });
        HikariDataSource old = obsolete.get();
        if (old != null) {
            old.close();
        }
        return new DorisAdminConnection(JdbcClient.create(entry.dataSource()), endpoint.host(), endpoint.port(),
                username, degraded, degradedReason);
    }

    private Endpoint physicalEndpoint(Integer clusterId) {
        List<ClusterServiceRoleInstanceEntity> frontends = roleService
                .getServiceRoleInstanceListByClusterIdAndRoleName(clusterId, DORIS_FE_ROLE)
                .stream()
                .filter(role -> ServiceRoleState.RUNNING.equals(role.getServiceRoleState())
                        || ServiceRoleState.EXISTS_ALARM.equals(role.getServiceRoleState()))
                .toList();
        if (frontends.isEmpty()) {
            throw new DorisConnectionException();
        }
        return new Endpoint(frontends.get(0).getHostname(),
                parsePort(variableValue(clusterId, "query_port")));
    }

    private String variableValue(Integer clusterId, String name) {
        ClusterVariable variable = variableService.getVariableByVariableName(clusterId, "DORIS", name);
        return variable == null ? null : variable.getVariableValue();
    }

    private static int parsePort(String port) {
        if (port == null || port.isBlank()) {
            return DEFAULT_QUERY_PORT;
        }
        try {
            int value = Integer.parseInt(port);
            if (value > 0 && value <= 65_535) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // The caller gets the same safe connection failure as any other invalid endpoint.
        }
        throw new DorisConnectionException();
    }

    private static HikariDataSource newDataSource(PoolKey key) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setJdbcUrl("jdbc:mysql://" + key.host() + ":" + key.port()
                + "/?useUnicode=true&characterEncoding=utf8&useSSL=false"
                + "&connectTimeout=" + CONNECTION_TIMEOUT_MS + "&socketTimeout=" + READ_TIMEOUT_MS);
        dataSource.setUsername(key.username());
        dataSource.setPassword(key.password());
        dataSource.setPoolName("doris-active-task-" + key.host() + "-" + key.port());
        dataSource.setMaximumPoolSize(8);
        dataSource.setMinimumIdle(0);
        dataSource.setConnectionTimeout(CONNECTION_TIMEOUT_MS);
        dataSource.setValidationTimeout(CONNECTION_TIMEOUT_MS);
        dataSource.setInitializationFailTimeout(-1);
        return dataSource;
    }

    private static boolean verifyConnection(HikariDataSource dataSource) throws SQLException {
        try (Connection ignored = dataSource.getConnection()) {
            return true;
        }
    }

    @PreDestroy
    public void close() {
        pools.values().forEach(entry -> entry.dataSource().close());
        pools.clear();
    }

    void invalidate(Integer clusterId) {
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

    @FunctionalInterface
    interface ConnectionVerifier {
        boolean verify(HikariDataSource dataSource) throws SQLException;
    }

    public record DorisAdminConnection(JdbcClient client, String host, int port, String username,
                                       boolean degraded, String degradedReason) {
        public String hostPort() {
            return host + ":" + port;
        }
    }

    public static final class DorisConnectionException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public DorisConnectionException() {
            super("Doris connection unavailable");
        }
    }

    private record Endpoint(String host, int port) {
        String hostPort() {
            return host + ":" + port;
        }
    }

    private record PoolKey(String host, int port, String username, String password) {
    }

    private record PoolEntry(PoolKey key, HikariDataSource dataSource) {
    }
}
