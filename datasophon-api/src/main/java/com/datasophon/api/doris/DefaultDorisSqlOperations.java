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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DefaultDorisSqlOperations implements DorisSqlOperations {

    @Override
    public boolean canConnect(String feHost, int port, String password) {
        try (Connection ignored = DriverManager.getConnection(url(feHost, port), "root", password)) {
            return true;
        } catch (Exception e) {
            log.debug("Doris connect probe failed feHost={} port={}: {}", feHost, port, e.getMessage());
            return false;
        }
    }

    @Override
    public void resetRootAndAdminPassword(String feHost, int port, String currentPassword,
                                          String newPassword) throws Exception {
        try (Connection connection = DriverManager.getConnection(url(feHost, port), "root", currentPassword)) {
            try (
                    PreparedStatement statement =
                            connection.prepareStatement("SET PASSWORD FOR 'root'@'%' = PASSWORD(?)")) {
                statement.setString(1, newPassword);
                statement.execute();
            }
            try (
                    PreparedStatement statement =
                            connection.prepareStatement("SET PASSWORD FOR 'admin'@'%' = PASSWORD(?)")) {
                statement.setString(1, newPassword);
                statement.execute();
            }
        }
    }

    private String url(String feHost, int port) {
        return "jdbc:mysql://" + feHost + ":" + port
                + "/?useUnicode=true&characterEncoding=utf8&useSSL=false";
    }
}
