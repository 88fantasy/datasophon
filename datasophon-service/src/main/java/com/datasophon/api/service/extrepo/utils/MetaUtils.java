package com.datasophon.api.service.extrepo.utils;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IORuntimeException;
import cn.hutool.crypto.SmUtil;
import com.alibaba.fastjson.JSONObject;
import com.datasophon.api.exceptions.BusinessException;
import com.datasophon.dao.model.extrepo.ExtRepoMetaModel;
import com.datasophon.dao.model.extrepo.FrameworkMeta;
import com.datasophon.dao.model.extrepo.ServiceMeta;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
     */
    private static final List<String> ENCRYPT_FILES = Arrays.asList(
            "conf/common.properties",
            "conf/cluster-sample.yml",
            "conf/datasophon.conf",
            "meta/**/service_ddl.json"
    );

    public static final String SAMPLE = "cluster-sample.yml";
    public static final String SERVICE_DDL = "service_ddl.json";
    private static final Logger log = LoggerFactory.getLogger(MetaUtils.class);

    /**
     * 对需要解压的文件，进行文件内容解压
     *
     * @param dir
     * @param cipherKey
     * @throws IOException
     */
    public static void decodeMatchedFiles(String dir, String cipherKey) throws IOException {
        PathMatcher matcher = new PathMatcher(dir, ENCRYPT_FILES);
//        解密文件内容
        Files.walkFileTree(Paths.get(dir), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                try {
                    if (matcher.isMatch(path)) {
                        MetaUtils.decodeFile(path.toFile(), cipherKey);
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
     * 解密文件内容，并写回原文件
     *
     * @param file
     * @param cipherKey
     */
    public static void decodeFile(File file, String cipherKey) {
        String cipherText = FileUtil.readString(file, StandardCharsets.UTF_8);
        String plainText = SmUtil.sm4(Base64.decode(cipherKey)).decryptStr(Base64.decode(cipherText), StandardCharsets.UTF_8);
        FileUtil.writeString(plainText, file, StandardCharsets.UTF_8);
        log.info("decode file: {}", file.getName());
    }




    public static ExtRepoMetaModel parseRepoMeta(String root) {
        ExtRepoMetaModel vo = new ExtRepoMetaModel();
        List<String> errors = new ArrayList<>();

        Path base = getConfPath(root);
        if (!base.toFile().exists()) {
            throw new BusinessException("meta文件未包含config目录");
        }
        File sample = base.resolve(SAMPLE).toFile();
        if (sample.exists()) {
//          TODO
        }


        File metaDir = base.resolve( "meta").toFile();
        if (metaDir.exists()) {
            File[] frameDirs = metaDir.listFiles();
            if (frameDirs != null) {
                for (File frameDir : frameDirs) {
                    if (!frameDir.isDirectory()) {
                        continue;
                    }
                    FrameworkMeta framework = parseFrameMeta(root, frameDir, errors);
                    vo.getFrameworks().add(framework);
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new BusinessException(StringUtils.joinWith(";", errors));
        }

        log.debug("解析到meta文件信息：{}", JSONObject.toJSONString(vo, true));
        return vo;
    }

    private static FrameworkMeta parseFrameMeta(String root, File frameDir, List<String> errors) {
        FrameworkMeta meta = new FrameworkMeta();
        meta.setFrameCode(frameDir.getName());

        File[] services = frameDir.listFiles();
        if (services == null) {
            return meta;
        }
        for (File serviceDir : services) {
            if (!serviceDir.isDirectory()) {
                continue;
            }
            List<ServiceMeta> serviceMeta = parseServiceMeta(new SrvParseCtx(root, meta.getFrameCode()), serviceDir, errors);
            meta.getServices().addAll(serviceMeta);
        }
        return meta;
    }


    private static List<ServiceMeta> parseServiceMeta(SrvParseCtx  ctx, File serviceDir, List<String> errors) {
        String root = ctx.getRoot();
        Path currentPath = getSrvPath(ctx, serviceDir.getName());

        File ddl = currentPath.resolve(SERVICE_DDL).toFile();
        if (!ddl.exists()) {
            return Collections.emptyList();
        }

        ServiceMeta meta = new ServiceMeta();
        meta.setFrameCode(ctx.getFramework());
        meta.setName(serviceDir.getName());
        meta.setDdl(PathUtils.relative(ddl.getAbsolutePath(), root));

        File tpl = currentPath.resolve( "template").toFile();
        if (tpl.exists() && tpl.isDirectory()) {
            meta.setTemplate(PathUtils.relative(tpl.getAbsolutePath(), root));
        }

        File script =  Paths.get(ctx.getRoot(), serviceDir.getName(), "script").toFile();
        if (script.exists() && script.isDirectory()) {
            meta.setScript(PathUtils.relative(script.getAbsolutePath(), root));
        }

        String content = FileUtil.readString(ddl, StandardCharsets.UTF_8);
        JSONObject ddlInfo = JSONObject.parseObject(content);
        meta.setPackageName(ddlInfo.getString("packageName"));
        meta.setVersion(ddlInfo.getString("version"));

        if (!StringUtils.equals(meta.getName(), ddlInfo.getString("name"))) {
            errors.add(String.format("框架%s服务%s ddl文件放置有误，name不一致", serviceDir.getParentFile().getName(), meta.getName()));
        }
        return Collections.singletonList(meta);
    }


    public static Path getConfPath(String root) {
        return Paths.get(root, "config");
    }


    public static Path getSrvPath(SrvParseCtx ctx, String srv) {
       return PathUtils.join(getConfPath(ctx.getRoot()), "meta", ctx.getFramework(), srv);
    }

    public static Path getPkgPath(String root) {
        return Paths.get(root, "packages", "raw");
    }

    public static String getMd5FileName(String pkgName) {
        return pkgName.endsWith(".md5") ? pkgName : pkgName + ".md5";
    }

}
