package com.datasophon.api.service.extrepo.utils;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IORuntimeException;
import cn.hutool.crypto.SmUtil;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.datasophon.api.exceptions.BusinessException;
import com.datasophon.api.service.extrepo.ctx.MetaParseOption;
import com.datasophon.api.service.extrepo.ctx.SrvParseCtx;
import com.datasophon.common.utils.PathUtils;
import com.datasophon.dao.model.extrepo.DeploymentModel;
import com.datasophon.dao.model.extrepo.ExtRepoMetaFsModel;
import com.datasophon.dao.model.extrepo.FrameworkMeta;
import com.datasophon.dao.model.extrepo.ServiceMeta;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.PropertyUtils;

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
            "config/common.properties",
            "config/cluster-sample.yml",
            "config/datasophon.conf",
            "config/meta/**/service_ddl.json"
    );

    public static final String SAMPLE = "cluster-sample.yml";
    public static final String SERVICE_DDL = "service_ddl.json";
    private static final Logger log = LoggerFactory.getLogger(MetaUtils.class);

    public static final String PACKAGES = "packages";
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
                    String relative = PathUtils.unixStyle(PathUtils.relative(path.toString(), dir));
                    if (matcher.isMatch(relative)) {
                        String plainText = MetaUtils.decodeFile(path.toFile(), cipherKey);
                        FileUtil.writeString(plainText, path.toFile(), StandardCharsets.UTF_8);
                    }
                } catch (IORuntimeException ex) {
                    log.error("handle encoded file:{} fail, {}", path, ex.getMessage());
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
    public static String decodeFile(File file, String cipherKey) {
        String cipherText = FileUtil.readString(file, StandardCharsets.UTF_8);
        log.info("decode file: {}", file.getAbsolutePath());
        String plainText = SmUtil.sm4(Base64.decode(cipherKey)).decryptStr(Base64.decode(cipherText), StandardCharsets.UTF_8);
        return plainText;

//        return cipherText;
    }


    /**
     * 解析meta文件的信息
     *
     * @return
     */
    public static ExtRepoMetaFsModel parseRepoMeta(MetaParseOption option) {
        String root = option.getRoot();

        ExtRepoMetaFsModel vo = new ExtRepoMetaFsModel();

        Path base = getConfPath(root);
        if (!base.toFile().exists()) {
            throw new BusinessException("meta文件未包含config目录");
        }

        List<String> errors = new ArrayList<>();
        File sample = base.resolve(SAMPLE).toFile();
        if (sample.exists()) {
            vo.setSample(PathUtils.relative(sample, root));
        }

        File tpl = base.resolve("template").toFile();
        if (tpl.exists() && tpl.isDirectory()) {
            vo.setTemplate(PathUtils.relative(tpl, root));
        }


        File metaDir = base.resolve("meta").toFile();
        if (metaDir.exists()) {
            vo.setMeta(PathUtils.relative(metaDir, root));
            File[] frameDirs = metaDir.listFiles();
            if (frameDirs != null) {
                for (File frameDir : frameDirs) {
                    if (!frameDir.isDirectory()) {
                        continue;
                    }
                    FrameworkMeta framework = parseFrameMeta(option, frameDir, errors);
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

    private static FrameworkMeta parseFrameMeta(MetaParseOption option, File frameDir, List<String> errors) {
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
            List<ServiceMeta> serviceMeta = parseServiceMeta(new SrvParseCtx(option, meta.getFrameCode(), errors), serviceDir);
            meta.getServices().addAll(serviceMeta);
        }
        return meta;
    }


    private static List<ServiceMeta> parseServiceMeta(SrvParseCtx ctx, File serviceDir) {
        String root = ctx.getOption().getRoot();
        Path currentPath = getSrvPath(ctx, serviceDir.getName());

        File ddl = currentPath.resolve(SERVICE_DDL).toFile();
        if (!ddl.exists()) {
            return Collections.emptyList();
        }

        ServiceMeta meta = new ServiceMeta();
        meta.setFrameCode(ctx.getFramework());
        meta.setName(serviceDir.getName());
        meta.setDdl(PathUtils.relative(ddl, root));



        File script = currentPath.resolve("script").toFile();
        if (script.exists() && script.isDirectory()) {
            meta.setScript(PathUtils.relative(script, root));
        }


        String content = FileUtil.readString(ddl, StandardCharsets.UTF_8);
        JSONObject ddlInfo = JSONObject.parseObject(content);
        meta.setPackageName(ddlInfo.getString("packageName"));
        meta.setVersion(ddlInfo.getString("version"));

        if (!StringUtils.equals(meta.getName(), ddlInfo.getString("name"))) {
            ctx.addError(String.format("框架%s服务%s ddl文件放置有误，name不一致", serviceDir.getParentFile().getName(), meta.getName()));
        }
        meta.setDependencies(ddlInfo.getObject("dependencies", new TypeReference<List<String>>() {
        }));
        return Collections.singletonList(meta);
    }

    private static Path getSrvPath(SrvParseCtx ctx, String srv) {
        return PathUtils.join(getConfPath(ctx.getOption().getRoot()), "meta", ctx.getFramework(), srv);
    }

    public static Path getConfPath(String root) {
        return Paths.get(root, "config");
    }


    public static Path getPkgPath(String root) {
        return Paths.get(root, "packages", "raw");
    }

    public static Path getFileRelativePath(ServiceMeta meta) {
        return PathUtils.join("packages", "raw", meta.getPackageName());
    }

    public static Path getMd5FileRelativePath(ServiceMeta meta) {
        return PathUtils.join("packages", "raw", getMd5FileName(meta.getPackageName()));
    }


    public static String getMd5FileName(String pkgName) {
        return pkgName.endsWith(".md5") ? pkgName : pkgName + ".md5";
    }


    public static DeploymentModel parseDeploymentFile(String content) {
        Constructor constructor = new Constructor(DeploymentModel.class);
        PropertyUtils propertyUtils = new PropertyUtils();
        propertyUtils.setSkipMissingProperties(true);
        constructor.setPropertyUtils(propertyUtils);
        return new Yaml(constructor).loadAs(content, DeploymentModel.class);
    }

}
