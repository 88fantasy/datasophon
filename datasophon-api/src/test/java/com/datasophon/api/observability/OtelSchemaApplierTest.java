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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
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
    void ignoresAlreadyExistingCreateJobOnly() {
        JdbcClient jdbc = mock(JdbcClient.class);
        JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class);
        when(jdbc.sql("CREATE JOB test")).thenReturn(statement);
        when(statement.update()).thenThrow(new DataAccessResourceFailureException("job already exists"));

        assertThatCode(() -> OtelSchemaApplier.executeStatement(jdbc, "CREATE JOB test"))
                .doesNotThrowAnyException();
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
