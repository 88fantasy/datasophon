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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class DefaultDorisSqlOperationsTest {

    private final DefaultDorisSqlOperations operations = new DefaultDorisSqlOperations();

    @Test
    void canConnectReturnsTrueWhenDriverManagerSucceeds() {
        Connection connection = mock(Connection.class);
        try (MockedStatic<DriverManager> driverManager = mockStatic(DriverManager.class)) {
            driverManager
                    .when(() -> DriverManager.getConnection(
                            "jdbc:mysql://fe-1:9030/?useUnicode=true&characterEncoding=utf8&useSSL=false",
                            "root", "target-password"))
                    .thenReturn(connection);

            assertTrue(operations.canConnect("fe-1", 9030, "target-password"));
        }
    }

    @Test
    void canConnectReturnsFalseWithoutThrowingWhenDriverManagerFails() {
        try (MockedStatic<DriverManager> driverManager = mockStatic(DriverManager.class)) {
            driverManager
                    .when(() -> DriverManager.getConnection(
                            "jdbc:mysql://fe-1:9030/?useUnicode=true&characterEncoding=utf8&useSSL=false",
                            "root", "wrong-password"))
                    .thenAnswer(invocation -> {
                        throw new SQLException("Access denied");
                    });

            assertFalse(operations.canConnect("fe-1", 9030, "wrong-password"));
        }
    }

    @Test
    void resetRootAndAdminPasswordBindsPasswordAsParameterNotSqlText() throws Exception {
        Connection connection = mock(Connection.class);
        PreparedStatement rootStatement = mock(PreparedStatement.class);
        PreparedStatement adminStatement = mock(PreparedStatement.class);
        when(connection.prepareStatement("SET PASSWORD FOR 'root'@'%' = PASSWORD(?)")).thenReturn(rootStatement);
        when(connection.prepareStatement("SET PASSWORD FOR 'admin'@'%' = PASSWORD(?)")).thenReturn(adminStatement);

        try (MockedStatic<DriverManager> driverManager = mockStatic(DriverManager.class)) {
            driverManager
                    .when(() -> DriverManager.getConnection(
                            "jdbc:mysql://fe-1:9030/?useUnicode=true&characterEncoding=utf8&useSSL=false",
                            "root", ""))
                    .thenReturn(connection);

            operations.resetRootAndAdminPassword("fe-1", 9030, "", "it's\\a'password");
        }

        verify(rootStatement).setString(eq(1), eq("it's\\a'password"));
        verify(rootStatement).execute();
        verify(adminStatement).setString(eq(1), eq("it's\\a'password"));
        verify(adminStatement).execute();
        verify(connection, never()).createStatement();
    }
}
