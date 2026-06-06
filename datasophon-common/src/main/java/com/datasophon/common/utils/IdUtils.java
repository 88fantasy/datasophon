package com.datasophon.common.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import cn.hutool.core.util.StrUtil;

/**
 * @author zhanghuangbin
 */
public class IdUtils {
    
    public static List<Integer> toIdList(String ids) {
        if (StrUtil.isBlank(ids)) {
            return new ArrayList<>();
        }
        return Arrays.stream(ids.split(","))
                .filter(StrUtil::isNotBlank)
                .map(Integer::parseInt)
                .collect(Collectors.toList());
    }
}
