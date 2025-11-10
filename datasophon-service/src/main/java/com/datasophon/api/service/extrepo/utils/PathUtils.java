package com.datasophon.api.service.extrepo.utils;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author zhanghuangbin
 * @date 2025/11/10
 */
public class PathUtils {


    public static Path join(String root, String...others) {
        return Paths.get(root, others);
    }

    public static Path join (Path root, String...other) {
        return Paths.get(root.toString(), other);
    }

    public static String relative(String current, String parent) {
        return Paths.get(parent).relativize(Paths.get(current)).toString();
    }
}
