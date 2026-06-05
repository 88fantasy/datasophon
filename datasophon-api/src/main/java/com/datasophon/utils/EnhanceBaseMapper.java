package com.datasophon.utils;

import org.apache.ibatis.reflection.property.PropertyNamer;

import java.util.Arrays;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.ReflectionKit;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import com.baomidou.mybatisplus.core.toolkit.support.LambdaMeta;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;

/**
 * @author zhanghuangbin
 */
public interface EnhanceBaseMapper<T> extends BaseMapper<T> {
    
    /**
     * 对象是否重复
     *
     * @param object    查询对象
     * @param entityCls  entity类型
     * @param functions 需要检查的属性
     */
    @SuppressWarnings("unchecked")
    default <DTO> boolean isDuplicate(DTO object, Class<T> entityCls, SFunction<DTO, ?>... functions) {
        QueryWrapper<T> wrapper = Wrappers.query();
        TableInfo tableInfo = SqlHelper.table(entityCls);
        Object idVal = ReflectionKit.getFieldValue(object, tableInfo.getKeyProperty());
        if (idVal != null) {
            if (idVal instanceof String) {
                if (StringUtils.isNotBlank((String) idVal)) {
                    wrapper.ne(tableInfo.getKeyColumn(), idVal);
                }
            } else {
                wrapper.ne(tableInfo.getKeyColumn(), idVal);
            }
        }
        
        Arrays.stream(functions)
                .forEach(func -> {
                    Object val = func.apply(object);
                    LambdaMeta lambda = LambdaUtils.extract(func);
                    String fieldName = PropertyNamer.methodToProperty(lambda.getImplMethodName());
                    
                    Map<String, ColumnCache> columnMap = LambdaUtils.getColumnMap(entityCls);
                    ColumnCache columnCache = columnMap.get(LambdaUtils.formatKey(fieldName));
                    
                    if (val == null || (val instanceof CharSequence && val.toString().trim().isEmpty())) {
                        wrapper.isNotNull(columnCache.getColumn());
                    } else {
                        wrapper.eq(columnCache.getColumn(), val);
                    }
                });
        
        return selectCount(wrapper) > 0;
    }
    
    /**
     * 对象是否重复
     *
     * @param object    查询对象
     * @param functions 需要检查的属性
     */
    @SuppressWarnings("unchecked")
    default boolean isDuplicate(T object, SFunction<T, ?>... functions) {
        return isDuplicate(object, (Class<T>) object.getClass(), functions);
    }
}
