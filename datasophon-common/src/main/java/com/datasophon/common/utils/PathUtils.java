package com.datasophon.common.utils;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import org.apache.commons.lang3.SystemUtils;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author zhanghuangbin
 * @date 2025/11/10
 */
public class PathUtils {


    public static String getTmpDir() {
        String tmpDir = PropertyUtils.getString("temp.dir");
        if (StrUtil.isBlank(tmpDir)) {
            tmpDir = SystemUtils.getJavaIoTmpDir().getAbsolutePath();
        }
        return tmpDir;
    }

    public static File getTmpDir(String subDir) {
        String tmpDir = getTmpDir();
        File file = Paths.get(tmpDir, subDir).toFile();
        if (!file.exists()) {
            FileUtil.mkdir(file);
        }
        return file;
    }

    public static Path join(String root, String... others) {
        return Paths.get(root, others);
    }

    public static Path join(Path root, String... other) {
        return Paths.get(root.toString(), other);
    }

    public static String relative(String current, String parent) {
        return Paths.get(parent).relativize(Paths.get(current)).toString();
    }


    public static String relative(File current, String parent) {
        return relative(current.getAbsolutePath(), parent);
    }

    public static String unixStyle(String path) {
        return path.replaceAll("\\\\", "/");
    }


}
