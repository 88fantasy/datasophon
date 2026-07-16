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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.simple.JdbcClient;

class OtelSchemaApplierTest {

    @Test
    void injectsDistinctRuntimePasswordsWithoutLeavingPlaceholders() {
        String sql = "collector='CHANGE_ME_AT_A3_COLLECTOR'; reader='CHANGE_ME_AT_A3_READER'";

        String rendered = OtelSchemaApplier.renderSql(
                sql, new OtelCredentials("collector-secret", "reader-secret"));

        assertThat(rendered)
                .contains("collector='collector-secret'")
                .contains("reader='reader-secret'")
                .doesNotContain("CHANGE_ME_AT_A3");
    }

    @Test
    void createJobSwitchesToOtelDatabaseOnTheSameConnectionBeforeCreating() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement jdbcStatement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(jdbcStatement);

        OtelSchemaApplier.executeStatement(mock(JdbcClient.class), dataSource, "CREATE JOB test");

        InOrder order = inOrder(connection, jdbcStatement);
        order.verify(connection).setCatalog(OtelSchema.DATABASE);
        order.verify(jdbcStatement).execute("CREATE JOB test");
        verify(dataSource, times(1)).getConnection();
    }

    @Test
    void ignoresAlreadyExistingCreateJobOnly() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement jdbcStatement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(jdbcStatement);
        when(jdbcStatement.execute("CREATE JOB test")).thenThrow(new SQLException("job already exists"));

        assertThatCode(() -> OtelSchemaApplier.executeStatement(mock(JdbcClient.class), dataSource, "CREATE JOB test"))
                .doesNotThrowAnyException();
    }

    /** Doris 4.0.6 实测的真实措辞是 "job name exist"(不含 "already"),曾一度漏判导致重复 apply 报错。 */
    @Test
    void ignoresDorisActualJobNameExistWording() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement jdbcStatement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(jdbcStatement);
        when(jdbcStatement.execute("CREATE JOB test"))
                .thenThrow(new SQLException("job name exist, jobName:otel:otel_traces_graph_job"));

        assertThatCode(() -> OtelSchemaApplier.executeStatement(mock(JdbcClient.class), dataSource, "CREATE JOB test"))
                .doesNotThrowAnyException();
    }

    @Test
    void rethrowsCreateJobFailuresThatAreNotAlreadyExists() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement jdbcStatement = mock(Statement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(jdbcStatement);
        when(jdbcStatement.execute("CREATE JOB test")).thenThrow(new SQLException("Unknown database ''"));

        assertThatThrownBy(() -> OtelSchemaApplier.executeStatement(mock(JdbcClient.class), dataSource, "CREATE JOB test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CREATE JOB");
    }

    @Test
    void databaseSchemaAlignsExistingOtelUserPasswords() {
        String sql = readResource("sql/V1__otel_database.sql");
        String rendered = OtelSchemaApplier.renderSql(
                sql, new OtelCredentials("collector-secret", "reader-secret"));

        assertThat(rendered)
                .contains("ALTER USER 'otel_collector' IDENTIFIED BY 'collector-secret'")
                .contains("ALTER USER 'otel_reader' IDENTIFIED BY 'reader-secret'");
    }

    /** {@code OtelSchema.DDL_RESOURCES} 中的相对路径对应本地 {@code package/raw/meta/datacluster-physical/DORIS/} 下的文件。 */
    private static String readResource(String res) {
        Path path = Path.of("..", "package", "raw", "meta", "datacluster-physical", OtelSchema.SERVICE_NAME, res);
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new IllegalStateException("DDL 资源缺失: " + res, e);
        }
    }
}
