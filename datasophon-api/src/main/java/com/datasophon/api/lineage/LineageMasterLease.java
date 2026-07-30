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
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the MySQL session lock that fences lineage writes to one Master.
 *
 * <p>The connection is created directly through {@link DriverManager}; it must never come from a
 * connection pool because returning or recycling the physical session releases the advisory lock.
 */
public final class LineageMasterLease implements AutoCloseable {

    static final String LOCK_NAME = "datasophon:lineage:master";
    private static final Duration DEFAULT_HEARTBEAT_INTERVAL = Duration.ofSeconds(30);
    private static final Logger log = LoggerFactory.getLogger(LineageMasterLease.class);

    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final boolean enabled;
    private final Duration heartbeatInterval;
    private final ScheduledExecutorService scheduler;

    private Connection connection;
    private volatile boolean owner;
    private boolean started;
    private boolean closed;

    public LineageMasterLease(String jdbcUrl, String username, String password, boolean enabled) {
        this(jdbcUrl, username, password, enabled, DEFAULT_HEARTBEAT_INTERVAL);
    }

    LineageMasterLease(String jdbcUrl, String username, String password, boolean enabled,
                       Duration heartbeatInterval) {
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl");
        this.username = Objects.requireNonNull(username, "username");
        this.password = Objects.requireNonNull(password, "password");
        this.enabled = enabled;
        this.heartbeatInterval = Objects.requireNonNull(heartbeatInterval, "heartbeatInterval");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "lineage-master-lease");
            thread.setDaemon(true);
            return thread;
        });
    }

    public synchronized void start() {
        if (started || closed) {
            return;
        }
        started = true;
        if (!enabled) {
            owner = true;
            return;
        }

        heartbeat();
        scheduler.scheduleWithFixedDelay(
                this::heartbeatSafely,
                heartbeatInterval.toMillis(),
                heartbeatInterval.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    public boolean isOwner() {
        return !enabled || owner;
    }

    synchronized void heartbeat() {
        if (!enabled || closed) {
            return;
        }

        try {
            ensureConnection();
            if (owner) {
                if (holdsLock()) {
                    return;
                }
                owner = false;
            }

            owner = tryAcquire();
            if (!owner) {
                log.error("Lineage Master lease is not held; lineage endpoints are unavailable");
            }
        } catch (SQLException e) {
            owner = false;
            closeConnection();
            log.error("Lineage Master lease heartbeat failed; lineage endpoints are unavailable", e);
        }
    }

    private void heartbeatSafely() {
        try {
            heartbeat();
        } catch (RuntimeException e) {
            owner = false;
            log.error("Unexpected Lineage Master lease heartbeat failure", e);
        }
    }

    private void ensureConnection() throws SQLException {
        if (connection == null || connection.isClosed() || !connection.isValid(2)) {
            owner = false;
            closeConnection();
            connection = DriverManager.getConnection(jdbcUrl, username, password);
        }
    }

    private boolean holdsLock() throws SQLException {
        try (
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT CONNECTION_ID(), IS_USED_LOCK(?)")) {
            statement.setString(1, LOCK_NAME);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return false;
                }
                long connectionId = resultSet.getLong(1);
                long lockOwnerId = resultSet.getLong(2);
                return !resultSet.wasNull() && connectionId == lockOwnerId;
            }
        }
    }

    private boolean tryAcquire() throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, 0)")) {
            statement.setString(1, LOCK_NAME);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) == 1 && !resultSet.wasNull();
            }
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        owner = false;
        scheduler.shutdownNow();

        if (enabled && connection != null) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                statement.setString(1, LOCK_NAME);
                statement.executeQuery();
            } catch (SQLException e) {
                log.warn("Failed to release Lineage Master lease explicitly; closing its connection", e);
            }
        }
        closeConnection();
    }

    private void closeConnection() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException e) {
            log.warn("Failed to close Lineage Master lease connection", e);
        } finally {
            connection = null;
        }
    }
}
