package com.datasophon.api.utils;

import org.apache.commons.lang3.SystemUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.function.Consumer;

import org.springframework.web.multipart.MultipartFile;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;

/**
 * @author zhanghuangbin
 */
public class MultipartFileUtils {
    
    public static void mapToTmpFile(MultipartFile file, Consumer<File> consumer) throws IOException {
        File dest = null;
        try {
            dest = mapToTmpFile(file);
        } finally {
            if (dest != null && dest.exists()) {
                FileUtil.del(dest);
            }
        }
    }
    
    public static File mapToTmpFile(MultipartFile file) throws IOException {
        String dest = Paths.get(SystemUtils.getJavaIoTmpDir().getAbsolutePath(), "temp", RandomUtil.randomString(12)).toString();
        File dir = new File(dest);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File target = new File(dir, file.getOriginalFilename());
        file.transferTo(target);
        return target;
    }
    
}
