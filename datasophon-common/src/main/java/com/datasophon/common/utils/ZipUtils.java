package com.datasophon.common.utils;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import org.apache.commons.lang3.SystemUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author zhanghuangbin
 * @date 2025/11/7
 */
public class ZipUtils {


    /**
     * 解压带密码的ZIP文件
     *
     * @param zipFilePath   ZIP文件路径
     * @param destDirectory 解压目标目录
     * @param password      解压密码 (可为空，用于无密码ZIP)
     * @throws IOException 解压过程中可能出现的IO异常
     */
    public static void unzip(String zipFilePath, String destDirectory, String password) throws IOException {
        try (ZipFile zipFile = new ZipFile(zipFilePath, password == null ? null : password.toCharArray())){
            zipFile.setCharset(StandardCharsets.UTF_8);
            zipFile.extractAll(destDirectory);
        }
    }

    public static String unzipToTemp(String zipFilePath, String password) throws IOException {
        String dest = Paths.get(SystemUtils.getJavaIoTmpDir().getAbsolutePath(), "ddp_unzip", RandomUtil.randomNumbers(12)).toString();
        unzip(zipFilePath, dest, password);
        return dest;
    }


    public static List<String> getZipEntry(String zipFilePath, String password) throws Exception {
        try (ZipFile zipFile = new ZipFile(zipFilePath)){
            if (StrUtil.isNotBlank(password)) {
                zipFile.setPassword(password.toCharArray());
            }
            zipFile.setCharset(StandardCharsets.UTF_8);
            if (!zipFile.isValidZipFile()) {
                throw new IllegalStateException("ZIP文件不合法或已损坏");
            }
            List<FileHeader> fileHeaders = zipFile.getFileHeaders();
            return fileHeaders.stream().map(FileHeader::getFileName).collect(Collectors.toList());
        }
    }


}
