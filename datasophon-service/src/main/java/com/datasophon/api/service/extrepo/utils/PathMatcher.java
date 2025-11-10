package com.datasophon.api.service.extrepo.utils;

import cn.hutool.core.text.AntPathMatcher;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * @author zhanghuangbin
 * @date 2025/11/7
 */
public class PathMatcher {


    private final Path base;

    private final List<String> patterns;

    public PathMatcher(String bath, List<String> patterns) {
        this.base = Paths.get(bath);
        this.patterns = patterns;
    }


    public boolean isMatch(Path path) {
        String relative = path.relativize(base).toString();
        if (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        AntPathMatcher matcher = new AntPathMatcher();
        for (String pattern : patterns) {
            if (matcher.matchStart(pattern, relative)) {
                return true;
            }
        }
        return false;
    }
}
