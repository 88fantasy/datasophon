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

package com.datasophon.api.lineage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class LineageObservabilityTest {

    @Test
    void rebuildSegmentsAndResultsReachMeterRegistry() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        {
            MicrometerRebuildMetrics rebuildMetrics = new MicrometerRebuildMetrics(registry);
            DriverManagerDataSource dataSource = lineageDataSource();
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            createSnapshotFixture(jdbcTemplate);

            MysqlSnapshotLoader loader = new MysqlSnapshotLoader(jdbcTemplate, rebuildMetrics);
            LineageGraphSnapshot snapshot = loader.load(1L);
            TransactionTemplate transaction =
                    new TransactionTemplate(new DataSourceTransactionManager(dataSource));
            try (
                    LineageRebuildCoordinator coordinator = new LineageRebuildCoordinator(
                            new LineageGraphSnapshotHolder(), loader, transaction, rebuildMetrics)) {
                assertThat(coordinator.publishIfNotOlder(1L, snapshot)).isTrue();
                assertThat(coordinator.publishIfNotOlder(1L, LineageGraphSnapshot.copyOf(
                        snapshot.graph(), snapshot.nodeMeta(), snapshot.generation() - 1, Instant.now()))).isFalse();
            }

            assertTimerCount(registry, MicrometerRebuildMetrics.DB_READ, 1);
            assertTimerCount(registry, MicrometerRebuildMetrics.MAPPING, 1);
            assertTimerCount(registry, MicrometerRebuildMetrics.GRAPH_BUILD, 1);
            assertTimerCount(registry, MicrometerRebuildMetrics.SNAPSHOT_COPY, 1);
            assertTimerCount(registry, MicrometerRebuildMetrics.CYCLE_CHECK, 1);
            assertTimerCount(registry, MicrometerRebuildMetrics.PUBLISH, 2);
            assertThat(registry.get(MicrometerRebuildMetrics.STALE_DISCARDED).counter().count()).isEqualTo(1);

            rebuildMetrics.rebuildFailed(new IllegalStateException("injected"));
            assertThat(registry.get(MicrometerRebuildMetrics.FAILED).counter().count()).isEqualTo(1);
            assertThat(registry.get(MicrometerRebuildMetrics.LAST_ERROR).gauge().value()).isEqualTo(1);
            rebuildMetrics.rebuildSucceeded();
            assertThat(registry.get(MicrometerRebuildMetrics.LAST_ERROR).gauge().value()).isZero();
        }
    }

    @Test
    void changedIngestRecordsWriteCountersAndJobRowLockWait() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        {
            MicrometerIngestMetrics metrics = new MicrometerIngestMetrics(registry);
            DecodedLineageEvent event = new DecodedLineageEvent(
                    "producer",
                    "run-1",
                    "COMPLETE",
                    "daily-orders",
                    "spark",
                    "BATCH",
                    "2026-07-30T00:00:00Z",
                    "2026-07-30T00:00:01Z",
                    "insert into dwd.orders select * from ods.orders",
                    List.of(new DatasetIdentity("paimon://prod/ods", "orders")),
                    List.of(new DatasetIdentity("paimon://prod/dwd", "orders")));
            LineageIngestService service = new LineageIngestService(
                    new IngestJdbcTemplate(),
                    directTransaction(),
                    payload -> event,
                    new CanonicalNameResolver.Default(),
                    new StructuralHashCalculator(new ObjectMapper()),
                    new WatermarkExtractor.Default(),
                    ignored -> {
                    },
                    metrics,
                    Clock.fixed(Instant.parse("2026-07-30T00:00:02Z"), ZoneOffset.UTC),
                    ignored -> {
                    });

            assertThat(service.ingest(1, new ObjectMapper().createObjectNode()).status())
                    .isEqualTo(LineageIngestService.Status.CHANGED);

            assertThat(registry.get(MicrometerIngestMetrics.EVENT).counter().count()).isEqualTo(1);
            assertThat(registry.get(MicrometerIngestMetrics.STRUCTURE_CHANGE).counter().count()).isEqualTo(1);
            assertThat(registry.get(MicrometerIngestMetrics.EDGE_ROWS_WRITTEN).counter().count()).isEqualTo(1);
            assertThat(registry.get(MicrometerIngestMetrics.LAST_SEEN_ROWS_UPDATED).counter().count()).isEqualTo(2);
            assertTimerCount(registry, MicrometerIngestMetrics.LOCK_WAIT, 1);
            assertThat(registry.get(MicrometerIngestMetrics.LOCK_WAIT).timer().takeSnapshot().percentileValues())
                    .extracting(value -> value.percentile())
                    .containsExactly(0.95, 0.99);
        }
    }

    @Test
    void historyListLengthPermissionFailureDisablesGaugeWithoutRetryingOrBlockingStartup() {
        AtomicInteger connectionAttempts = new AtomicInteger();
        AtomicInteger warnings = new AtomicInteger();
        AbstractDataSource permissionDenied = new AbstractDataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                connectionAttempts.incrementAndGet();
                throw new SQLException("access denied", "42000", 1227);
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                return getConnection();
            }
        };

        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        {
            LineageHistoryListLengthGauge gauge =
                    new LineageHistoryListLengthGauge(permissionDenied, registry, error -> warnings.incrementAndGet());

            assertThatCode(() -> {
                gauge.run(null);
                gauge.run(null);
            }).doesNotThrowAnyException();

            assertThat(gauge.isAvailable()).isFalse();
            assertThat(registry.get(LineageHistoryListLengthGauge.HISTORY_LIST_LENGTH).gauge().value()).isNaN();
            assertThat(connectionAttempts).hasValue(1);
            assertThat(warnings).hasValue(1);
        }
    }

    private static DriverManagerDataSource lineageDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:lineage-observability;MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private static void createSnapshotFixture(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("DROP ALL OBJECTS");
        jdbcTemplate.execute("""
                CREATE TABLE t_ddh_lineage_generation (
                    id BIGINT PRIMARY KEY,
                    generation BIGINT NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_ddh_lineage_node (
                    id BIGINT PRIMARY KEY,
                    connector VARCHAR(32),
                    catalog_name VARCHAR(255),
                    database_name VARCHAR(255),
                    table_name VARCHAR(255),
                    canonical_name VARCHAR(1024),
                    dw_layer VARCHAR(32)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_ddh_lineage_edge (
                    id BIGINT PRIMARY KEY,
                    job_id BIGINT NOT NULL,
                    definition_version INT NOT NULL,
                    src_node_id BIGINT NOT NULL,
                    dst_node_id BIGINT NOT NULL,
                    flow_type VARCHAR(32),
                    is_current TINYINT NOT NULL
                )
                """);
        jdbcTemplate.update("INSERT INTO t_ddh_lineage_generation (id, generation) VALUES (1, 7)");
        jdbcTemplate.update("""
                INSERT INTO t_ddh_lineage_node
                    (id, connector, catalog_name, database_name, table_name, canonical_name, dw_layer)
                VALUES
                    (1, 'paimon', 'prod', 'ods', 'orders', 'paimon://prod/ods/orders', 'ODS'),
                    (2, 'paimon', 'prod', 'dwd', 'orders', 'paimon://prod/dwd/orders', 'DWD')
                """);
        jdbcTemplate.update("""
                INSERT INTO t_ddh_lineage_edge
                    (id, job_id, definition_version, src_node_id, dst_node_id, flow_type, is_current)
                VALUES (1, 10, 1, 1, 2, 'BATCH', 1)
                """);
    }

    private static void assertTimerCount(SimpleMeterRegistry registry, String name, long expected) {
        assertThat(registry.get(name).timer().count()).isEqualTo(expected);
    }

    private static TransactionOperations directTransaction() {
        return new TransactionOperations() {
            @Override
            public <T> T execute(TransactionCallback<T> action) {
                return action.doInTransaction(new SimpleTransactionStatus());
            }
        };
    }

    private static final class IngestJdbcTemplate extends JdbcTemplate {

        @Override
        public int update(String sql) {
            return 1;
        }

        @Override
        public int update(String sql, Object... args) {
            return 1;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            Object result;
            if (requiredType == Integer.class) {
                result = 0;
            } else if (sql.contains("FROM t_ddh_data_job WHERE cluster_id")) {
                result = 10L;
            } else if (sql.contains("FROM t_ddh_lineage_node")) {
                result = String.valueOf(args[0]).contains("/ods/") ? 11L : 12L;
            } else {
                throw new AssertionError("Unexpected query: " + sql);
            }
            return requiredType.cast(result);
        }

        @Override
        public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) {
            try {
                return rowMapper.mapRow(emptyJobState(), 0);
            } catch (SQLException e) {
                throw new AssertionError(e);
            }
        }

        private static ResultSet emptyJobState() {
            return (ResultSet) Proxy.newProxyInstance(
                    ResultSet.class.getClassLoader(),
                    new Class<?>[]{ResultSet.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "getLong" -> 0L;
                        case "getString" -> null;
                        case "wasNull" -> true;
                        case "isWrapperFor" -> false;
                        case "unwrap" -> throw new SQLException("not a wrapper");
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }
}
