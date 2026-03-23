package com.datasophon.common.utils;

import cn.hutool.core.util.StrUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * @author zhanghuangbin
 */
public class ConverterUtils {


    public static <T> List<T> convertIds(String id, Function<String, T> mapper) {
        if (StrUtil.isBlank(id)) {
            return new ArrayList<>(0);
        }
        List<T> result = new ArrayList<>();
        for(String idPart : id.split(",")) {
            if (StrUtil.isNotBlank(idPart)) {
                result.add(mapper.apply(idPart));
            }
        }
        return result;
    }
}
