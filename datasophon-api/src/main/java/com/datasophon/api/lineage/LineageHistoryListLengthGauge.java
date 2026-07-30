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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Best-effort InnoDB history list length gauge.
 *
 * <p>The monitor is enabled and probed once at startup. Any SQL failure permanently disables the
 * gauge, emits one warning, and never blocks application startup.</p>
 */
public final class LineageHistoryListLengthGauge implements ApplicationRunner, Ordered {

    static final String HISTORY_LIST_LENGTH = "lineage.ingest.history.list.length";

    private static final Logger logger = LoggerFactory.getLogger(LineageHistoryListLengthGauge.class);
    private static final String ENABLE_SQL =
            "SET GLOBAL innodb_monitor_enable = 'trx_rseg_history_len'";
    private static final String SELECT_SQL =
            "SELECT count FROM information_schema.INNODB_METRICS WHERE name = 'trx_rseg_history_len'";

    private final DataSource dataSource;
    private final Consumer<SQLException> warning;
    private final AtomicBoolean initialized = new AtomicBoolean();
    private final AtomicBoolean available = new AtomicBoolean();
    private final AtomicBoolean warned = new AtomicBoolean();

    public LineageHistoryListLengthGauge(DataSource dataSource, MeterRegistry registry) {
        this(dataSource, registry, error -> logger.warn(
                "Lineage history_list_length gauge is permanently unavailable; "
                        + "innodb_monitor_enable may require SYSTEM_VARIABLES_ADMIN: {}",
                error.getMessage()));
    }

    LineageHistoryListLengthGauge(DataSource dataSource, MeterRegistry registry, Consumer<SQLException> warning) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.warning = Objects.requireNonNull(warning, "warning");
        Gauge.builder(HISTORY_LIST_LENGTH, this, LineageHistoryListLengthGauge::value)
                .description("InnoDB transaction rollback segment history list length")
                .register(Objects.requireNonNull(registry, "registry"));
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!initialized.compareAndSet(false, true)) {
            return;
        }
        try {
            enableAndRead();
            available.set(true);
        } catch (SQLException e) {
            disable(e);
        }
    }

    @Override
    public int getOrder() {
        return LineageRebuildCoordinator.STARTUP_ORDER + 1;
    }

    boolean isAvailable() {
        return available.get();
    }

    private double value() {
        if (!available.get()) {
            return Double.NaN;
        }
        try {
            return read();
        } catch (SQLException e) {
            disable(e);
            return Double.NaN;
        }
    }

    private void enableAndRead() throws SQLException {
        try (
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(ENABLE_SQL);
            read(statement);
        }
    }

    private double read() throws SQLException {
        try (
                Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            return read(statement);
        }
    }

    private static double read(Statement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery(SELECT_SQL)) {
            if (!resultSet.next()) {
                throw new SQLException("trx_rseg_history_len is not available");
            }
            return resultSet.getDouble(1);
        }
    }

    private void disable(SQLException error) {
        available.set(false);
        if (warned.compareAndSet(false, true)) {
            warning.accept(error);
        }
    }
}
