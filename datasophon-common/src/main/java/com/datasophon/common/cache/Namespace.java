package com.datasophon.common.cache;

import cn.hutool.core.util.StrUtil;
import com.datasophon.common.Constants;

import java.util.Arrays;
import java.util.List;

/**
 * @author zhanghuangbin
 */
public class Namespace {

    private final List<String> namespaces;

    private Namespace(List<String> namespace) {
        this.namespaces = namespace;
    }

    public static Namespace of(String...namespaces) {
        return new Namespace(Arrays.asList(namespaces));
    }

    @Override
    public String toString() {
        return namespaces == null ? null : StrUtil.join(Constants.UNDERLINE, namespaces);
    }
}
