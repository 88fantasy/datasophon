
package com.datasophon.worker.hook.db;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public final class JdbcConnectorUtils {
    
    public static Connection getConnection(String driver, String url, String username, String password) {
        try {
            Class.forName(driver);
            return DriverManager.getConnection(url, username, password);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(String.format("不支持的jdbc驱动：%s", driver));
        } catch (SQLException e) {
            throw new IllegalStateException(String.format("获取数据连接失败，数据库错误码%d，%s", e.getErrorCode(), e.getMessage()), e);
        }
    }
    
    public static int executeUpdate(final Connection connection, final String sql, Object... params) throws SQLException {
        try (final PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                if (params[i] instanceof Date) {
                    ps.setDate(i + 1, (Date) params[i]);
                } else if (params[i] instanceof java.util.Date) {
                    java.util.Date date = (java.util.Date) params[i];
                    ps.setTimestamp(i + 1, new Timestamp(date.getTime()));
                } else if (params[i] instanceof Integer) {
                    ps.setInt(i + 1, (Integer) params[i]);
                } else {
                    ps.setString(i + 1, params[i].toString());
                }
            }
            return ps.executeUpdate();
        }
    }
    
    public static <T> List<T> queryList(
                                        final Connection connection, String sql, ResultSetHandler<T> mapper, String... params) throws SQLException {
        try (final PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            List<T> result = new ArrayList<>();
            try (ResultSet resultSet = ps.executeQuery()) {
                while (resultSet.next()) {
                    result.add(mapper.map(resultSet));
                }
            }
            return result;
        }
    }
    
    public static <T> T query(
                              final Connection connection, String sql, ResultSetHandler<T> mapper, String... params) throws SQLException {
        try (final PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            try (ResultSet resultSet = ps.executeQuery()) {
                if (resultSet.next()) {
                    return mapper.map(resultSet);
                }
            }
            return null;
        }
    }
    
    public interface ResultSetHandler<T> {
        
        T map(ResultSet resultSet) throws SQLException;
    }
    
}
