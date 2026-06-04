package com.datasophon.dao.typehandler;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeException;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import cn.hutool.core.util.StrUtil;

/**
 * @author zhanghuangbin
 * @date 2024/9/11
 */
public class ListStringHandler extends BaseTypeHandler<List<String>> {
    
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType) throws SQLException {
        if (parameter.isEmpty()) {
            if (jdbcType == null) {
                jdbcType = JdbcType.VARCHAR;
            }
            try {
                ps.setNull(i, jdbcType.TYPE_CODE);
            } catch (SQLException e) {
                throw new TypeException("Error setting null for parameter #" + i + " with JdbcType " + jdbcType + " . "
                        + "Try setting a different JdbcType for this parameter or a different jdbcTypeForNull configuration property. "
                        + "Cause: " + e, e);
            }
        } else {
            ps.setString(i, String.join(",", parameter));
        }
    }
    
    @Override
    public List<String> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String content = rs.getString(columnName);
        return convertToList(content);
    }
    
    @Override
    public List<String> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        String content = rs.getString(columnIndex);
        return convertToList(content);
    }
    
    @Override
    public List<String> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        String content = cs.getString(columnIndex);
        return convertToList(content);
    }
    
    private List<String> convertToList(String content) {
        if (StrUtil.isBlank(content)) {
            return new ArrayList<>(0);
        } else {
            return StrUtil.split(content, ",");
        }
    }
}
