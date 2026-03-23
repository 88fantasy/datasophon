package com.datasophon.common.utils;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * @author zhanghuangbin
 */
public class ZipUtils {

    public static void unzip(String zipFile, String targetDir) throws IOException {
        unzip(zipFile, targetDir, 0);
    }

    /**
     *
     * @param zipFile
     * @param targetDir
     * @param stripComponents 类似于tar --strip-components参数的功能
     * @throws IOException
     */
    public static void unzip(String zipFile, String targetDir, int stripComponents) throws IOException {
        if (stripComponents < 0) {
            stripComponents = 0;
        }
        InputStream in = null;
        ArchiveInputStream archiveInputStream = null;

        try {
            in = Files.newInputStream(Paths.get(zipFile));
            archiveInputStream = new ArchiveStreamFactory().createArchiveInputStream(ArchiveStreamFactory.ZIP, in);
            extractArchive(archiveInputStream, targetDir, stripComponents);
        } catch (ArchiveException e) {
            throw new IllegalStateException(String.format("%s is not an valid zip file", zipFile));
        } finally {
            IoUtil.close(archiveInputStream);
            IoUtil.close(in);
        }
    }

    private static void extractArchive(ArchiveInputStream ais, String targetDir, int stripComponents) throws IOException {
        ArchiveEntry entry;
        Path targetPath = Paths.get(targetDir);

        // 创建目标目录
        if (!Files.exists(targetPath)) {
            Files.createDirectories(targetPath);
        }
        while ((entry = ais.getNextEntry()) != null) {
            if (!ais.canReadEntryData(entry)) {
                continue;
            }
            String name = entry.getName();
            if (stripComponents > 0) {
                List<String> components = Arrays.asList(name.split("/"));
                if (components.size() <= stripComponents) {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    throw new IllegalStateException(String.format("fileName: %s only has %d components, can not strip %d components", name,  components.size(), stripComponents));
                }
                String newName = StrUtil.join("/", components.subList(stripComponents, components.size()));
                name = name.endsWith("/") ? newName + "/" : newName;
            }
            Path entryPath = targetPath.resolve(name);
            if (!entryPath.normalize().startsWith(targetPath.normalize())) {
                throw new IOException("恶意路径: " + entry.getName());
            }

            if (entry.isDirectory()) {
                // 创建目录
                Files.createDirectories(entryPath);
            } else {
                // 创建父目录
                Path parent = entryPath.getParent();
                if (parent != null && !Files.exists(parent)) {
                    Files.createDirectories(parent);
                }

                // 写入文件
                try (OutputStream os = Files.newOutputStream(entryPath)) {
                    IoUtil.copy(ais, os);
                }
            }
        }
    }

}
