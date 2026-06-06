package com.datasophon.common.utils;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.RandomUtil;

/**
 * @author zhanghuangbin
 */
public class TarUtils {
    
    /**
     * 解压tar文件(支持tar,tag.gz)
     *
     * @param tarFilePath   tar文件路径
     * @param destDirectory 解压目标目录
     * @throws IOException 解压过程中可能出现的IO异常
     */
    public static void decompress(String tarFilePath, String destDirectory) throws IOException {
        Path tarPath = Paths.get(tarFilePath);
        Path outputPath = Paths.get(destDirectory);
        if (!Files.exists(outputPath)) {
            Files.createDirectories(outputPath);
        }
        
        String fileName = tarPath.getFileName().toString().toLowerCase();
        try (
                InputStream fileInputStream = Files.newInputStream(tarPath);
                InputStream decompressedInputStream = getDecompressedStream(fileInputStream, fileName);
                TarArchiveInputStream tarInputStream = new TarArchiveInputStream(decompressedInputStream)) {
            
            TarArchiveEntry entry;
            while ((entry = (TarArchiveEntry) tarInputStream.getNextEntry()) != null) {
                Path outputFilePath = outputPath.resolve(entry.getName());
                if (!outputFilePath.startsWith(outputPath)) {
                    throw new SecurityException("路径遍历攻击检测: " + entry.getName());
                }
                
                if (entry.isDirectory()) {
                    Files.createDirectories(outputFilePath);
                } else {
                    Path parentDir = outputFilePath.getParent();
                    if (parentDir != null && !Files.exists(parentDir)) {
                        Files.createDirectories(parentDir);
                    }
                    
                    try (OutputStream outputFileStream = Files.newOutputStream(outputFilePath)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = tarInputStream.read(buffer)) != -1) {
                            outputFileStream.write(buffer, 0, bytesRead);
                        }
                    }
                }
            }
        }
    }
    
    private static InputStream getDecompressedStream(InputStream inputStream, String fileName) throws IOException {
        if (fileName.endsWith(".tar.gz") || fileName.endsWith(".tgz") || fileName.endsWith(".taz")) {
            return new GzipCompressorInputStream(inputStream);
        } else if (fileName.endsWith(".tar.bz2") || fileName.endsWith(".tbz2") || fileName.endsWith(".tbz")) {
            return new BZip2CompressorInputStream(inputStream);
        } else if (fileName.endsWith(".tar")) {
            return inputStream;
        } else {
            throw new IllegalArgumentException("不支持的文件格式: " + fileName);
        }
    }
    
    public static String decompressToTemp(String zipFilePath) throws IOException {
        String dest = Paths.get(PathUtils.getTmpDir(), "ddp_unzip", RandomUtil.randomNumbers(12)).toString();
        decompress(zipFilePath, dest);
        return dest;
    }
    
    public static List<String> getEntry(String tarFilePath) throws IOException {
        Path tarPath = Paths.get(tarFilePath);
        String fileName = tarPath.getFileName().toString().toLowerCase();
        
        List<String> paths = new ArrayList<>();
        
        try (
                InputStream fileInputStream = Files.newInputStream(tarPath);
                InputStream decompressedInputStream = getDecompressedStream(fileInputStream, fileName);
                TarArchiveInputStream tarInputStream = new TarArchiveInputStream(decompressedInputStream)) {
            ArchiveEntry entry;
            while ((entry = tarInputStream.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    paths.add(entry.getName());
                }
            }
        }
        return paths;
    }
    
    public static String readEntryContent(String tarFilePath, String target) throws IOException {
        try (
                FileInputStream fis = new FileInputStream(tarFilePath);
                TarArchiveInputStream tarInputStream = new TarArchiveInputStream(fis)) {
            
            TarArchiveEntry entry;
            while ((entry = tarInputStream.getNextTarEntry()) != null) {
                // 跳过目录
                if (entry.isDirectory()) {
                    continue;
                }
                
                // 匹配目标文件（注意：路径分隔符可能与操作系统有关，TAR 内部通常使用 "/")
                if (entry.getName().equals(target)) {
                    return IoUtil.read(tarInputStream, StandardCharsets.UTF_8);
                }
            }
        }
        
        throw new FileNotFoundException("File not found in TAR: " + target);
    }
    
}
