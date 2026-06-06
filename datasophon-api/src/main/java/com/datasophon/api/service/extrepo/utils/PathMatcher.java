package com.datasophon.api.service.extrepo.utils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import cn.hutool.core.text.AntPathMatcher;

/**
 * @author zhanghuangbin
 */
public class PathMatcher {
    
    private final Path base;
    
    private final List<String> patterns;
    
    public PathMatcher(String bath, List<String> patterns) {
        this.base = Paths.get(bath);
        this.patterns = patterns;
    }
    
    public boolean isMatch(String relative) {
        if (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        AntPathMatcher matcher = new AntPathMatcher();
        for (String pattern : patterns) {
            if (matcher.match(pattern, relative)) {
                return true;
            }
        }
        return false;
    }
}
