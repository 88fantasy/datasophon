package com.datasophon.common.utils;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IORuntimeException;
import cn.hutool.crypto.SmUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;

/**
 * 安装包的解析工具。 安装包的结构如下：
 * <pre>
 * ├── config                  # 配置目录
 *     ├── cluster-sample.yml      # cli初始化配置(vos-cli导入集群规划文件)
 *     ├── common.properties       # api 基础配置
 *     ├── datasophon.conf         # api 数据源配置
 *     ├── meta                    # 软件元数据配置
 *         ├── SY-3.6.0         # 安装软件配置
 *             ├── BIGDATA
 *             ├── USCHEDULER
 *                  ├── V1.0.0           # 版本号（待定）
 *                      ├── script           # 脚本等
 *                      ├── service_ddl.json # 软件json配置
 *                  ├── V1.0.1           # 版本号（待定）
 *                      ├── template           # 模版等
 *                      ├── script           # 脚本等
 *                      ├── service_ddl.json # 软件json配置
 * </pre>
 *
 * @author zhanghuangbin
 * @date 2025/11/7
 */
public class MetaUtils {

    /**
     * 需要解密文件内容的文件
     * cluster-sample.yml不需要解密输出文件
     */
    private static final List<String> ENCRYPT_FILES = Arrays.asList(
            "**/config/common.properties",
            "**/config/application.conf",
            "**/config/datasophon.conf",
            "**/meta/**/service_ddl.json"
    );
    private static final Logger log = LoggerFactory.getLogger(MetaUtils.class);

    public static void decodeMatchedFiles(String dir, String cipherKey) throws IOException {
        commonMatchedFiles(dir, cipherKey, "decode");
    }

    public static void encodeMatchedFiles(String dir, String cipherKey) throws IOException {
        commonMatchedFiles(dir, cipherKey, "encode");
    }
    /**
     * 解压与解密方法
     *
     * @param dir
     * @param cipherKey
     * @throws IOException
     */
    public static void commonMatchedFiles(String dir, String cipherKey, String type) throws IOException {
        PathMatcher matcher = new PathMatcher(dir, ENCRYPT_FILES);
//        解密文件内容
        Files.walkFileTree(Paths.get(dir), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                try {
                    String relative = PathUtils.unixStyle(PathUtils.relative(path.toString(), dir));
                    if (matcher.isMatch(relative)) {
                        if(type.equals("decode")) {
                            decodeFile(path.toFile(), cipherKey);
                        } else {
                            encodeFile(path.toFile(), cipherKey);
                        }
                    }
                } catch (IORuntimeException ex) {
                    if (ex.causeInstanceOf(IOException.class)) {
                        throw (IOException) ex.getCause();
                    }
                    throw ex;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                throw exc;
            }
        });

    }

    /**
     * 解密文件内容,并覆盖原文件
     *
     * @param file
     * @param cipherKey
     */
    public static void decodeFile(File file, String cipherKey) {
        String base64Str = FileUtil.readString(file, StandardCharsets.UTF_8);
        byte[] encryptedBytes = Base64.decode(base64Str);
        byte[] decryptedBytes = SmUtil.sm4(Base64.decode(cipherKey)).decrypt(encryptedBytes);
        FileUtil.writeBytes(decryptedBytes, file);
        log.info("decode file: {}", file.getName());
    }

    /**
     * 解密文件内容，并返回bytes
     * @param file
     * @param cipherKey
     */
    public static byte[] decodeContext(File file, String cipherKey) {
        String context = FileUtil.readString(file, StandardCharsets.UTF_8);
        byte[] encryptedBytes = Base64.decode(context);
        return SmUtil.sm4(Base64.decode(cipherKey)).decrypt(encryptedBytes);
    }

    /**
     * 加密文件内容,并覆盖原文件
     *
     * @param file
     * @param cipherKey
     */
    public static void encodeFile(File file, String cipherKey) {
        byte[] sm4Bytes = SmUtil.sm4(Base64.decode(cipherKey)).encrypt(FileUtil.readString(file, StandardCharsets.UTF_8));
        String base64 = Base64.encode(sm4Bytes);
        FileUtil.writeString(base64, file, StandardCharsets.UTF_8);
        log.info("encode file: {}", file.getName());
    }
}
