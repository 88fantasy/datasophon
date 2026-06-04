package com.datasophon.api.service.extrepo.utils;

import com.datasophon.api.exceptions.BusinessException;
import com.datasophon.api.service.extrepo.ctx.MetaParseOption;
import com.datasophon.api.service.extrepo.ctx.SrvParseCtx;
import com.datasophon.common.Constants;
import com.datasophon.common.model.ServiceInfo;
import com.datasophon.common.model.k8s.K8sServiceInfo;
import com.datasophon.common.storage.MetaStorage;
import com.datasophon.common.utils.PathUtils;
import com.datasophon.common.utils.YamlUtils;
import com.datasophon.dao.model.extrepo.ExtRepoMetaFsModel;
import com.datasophon.dao.model.extrepo.FrameworkMeta;
import com.datasophon.dao.model.extrepo.K8sDdLServiceMeta;
import com.datasophon.dao.model.extrepo.VosDdLServiceMeta;

import org.apache.commons.lang3.StringUtils;

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.TypeReference;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SmUtil;

/**
 * 安装包的解析工具。 安装包的结构如下：
 * <pre>
 *└── config
 *    ├── deploy.yaml   部署清单
 *    ├── meta
 *    |  └── datalake-3.8  框架名称
 *    |     └── REDIS      服务
 *    |        ├── script
 *    |        └── service_ddl.json
 *    └── template
 *       └── redis-master.ftl
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
            "config/meta/**/vos_ddl/**/service_ddl.json",
            "config/meta/**/k8s/**/manifest.yaml",
            "config/meta/**/k8s/**/values.yaml");
    
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
        // 解密文件内容
        Files.walkFileTree(Paths.get(dir), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                try {
                    String relative = PathUtils.unixStyle(PathUtils.relative(path.toString(), dir));
                    if (matcher.isMatch(relative)) {
                        if (path.toFile().length() > 0) {
                            String plainText = MetaUtils.decodeFile(path.toFile(), cipherKey);
                            FileUtil.writeString(plainText, path.toFile(), StandardCharsets.UTF_8);
                        }
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
        if (file.length() == 0) {
            return null;
        }
        String cipherText = FileUtil.readString(file, StandardCharsets.UTF_8);
        boolean isRaw = false;
        // 测试环境，有可能上传不加密的文件，则不需要解密，这段代码方便调试
        if (file.getName().toLowerCase().endsWith("yml") || file.getName().toLowerCase().endsWith("yaml")) {
            // 如果包含冒号，说明，不需要解密
            isRaw = cipherText.contains(":");
        } else if (file.getName().toLowerCase().endsWith(".json")) {
            // json，且以{或者[开头，说明不需要解密
            isRaw = cipherText.trim().startsWith("{") || cipherText.startsWith("[");
        }
        if (isRaw) {
            log.warn("file {} need decode, but content does not match the cipher rules", file.getAbsolutePath());
            return cipherText;
        }
        log.info("decode file: {}", file.getAbsolutePath());
        String plainText = SmUtil.sm4(Base64.decode(cipherKey)).decryptStr(Base64.decode(cipherText), StandardCharsets.UTF_8);
        return plainText;
        
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
        
        log.debug("解析到meta文件信息：{}", JSON.toJSONString(vo, JSONWriter.Feature.PrettyFormat));
        return vo;
    }
    
    private static FrameworkMeta parseFrameMeta(MetaParseOption option, File frameDir, List<String> errors) {
        FrameworkMeta meta = new FrameworkMeta();
        meta.setFrameCode(frameDir.getName());
        
        File vosDdlDir = new File(frameDir, MetaStorage.VOS_DDL);
        if (vosDdlDir.exists()) {
            File[] services = vosDdlDir.listFiles();
            if (services != null) {
                for (File serviceDir : services) {
                    if (!serviceDir.isDirectory()) {
                        continue;
                    }
                    List<VosDdLServiceMeta> vosDdLServiceMeta = parseVosDdlServiceMeta(new SrvParseCtx(option, meta.getFrameCode(), errors), serviceDir);
                    meta.getVosDdlServices().addAll(vosDdLServiceMeta);
                }
            }
        }
        File k8sDdlDir = new File(frameDir, MetaStorage.K8S);
        if (k8sDdlDir.exists()) {
            File[] services = k8sDdlDir.listFiles();
            if (services != null) {
                for (File serviceDir : services) {
                    if (!serviceDir.isDirectory()) {
                        continue;
                    }
                    List<K8sDdLServiceMeta> vosDdLServiceMeta = parseK8sDdlServiceMeta(new SrvParseCtx(option, meta.getFrameCode(), errors), serviceDir);
                    meta.getK8sDdLServices().addAll(vosDdLServiceMeta);
                }
            }
        }
        
        return meta;
    }
    
    private static List<VosDdLServiceMeta> parseVosDdlServiceMeta(SrvParseCtx ctx, File serviceDir) {
        String root = ctx.getOption().getRoot();
        Path currentPath = PathUtils.join(getConfPath(ctx.getOption().getRoot()), "meta", ctx.getFramework(), MetaStorage.VOS_DDL, serviceDir.getName());
        
        File ddl = currentPath.resolve(Constants.SERVICE_DDL).toFile();
        if (!ddl.exists()) {
            return Collections.emptyList();
        }
        
        VosDdLServiceMeta meta = new VosDdLServiceMeta();
        meta.setFrameCode(ctx.getFramework());
        meta.setName(serviceDir.getName());
        meta.setDdl(PathUtils.relative(ddl, root));
        
        String content = FileUtil.readString(ddl, StandardCharsets.UTF_8);
        ServiceInfo serviceInfo = JSONObject.parseObject(content, new TypeReference<ServiceInfo>() {
        });
        
        List<String> packageNames = new ArrayList<>();
        if (serviceInfo.getArch() != null) {
            serviceInfo.getArch().values().forEach(arch -> packageNames.add(arch.getPackageName()));
        }
        meta.setPackageNames(packageNames);
        meta.setVersion(serviceInfo.getVersion());
        
        if (!StringUtils.equals(meta.getName(), serviceInfo.getName())) {
            ctx.addError(String.format("框架%s服务%s ddl文件放置有误，name不一致", ctx.getFramework(), meta.getName()));
        }
        meta.setDependencies(serviceInfo.getDependencies());
        return Collections.singletonList(meta);
    }
    
    private static List<K8sDdLServiceMeta> parseK8sDdlServiceMeta(SrvParseCtx ctx, File serviceDir) {
        String root = ctx.getOption().getRoot();
        Path currentPath = PathUtils.join(getConfPath(ctx.getOption().getRoot()), "meta", ctx.getFramework(), MetaStorage.K8S, serviceDir.getName());
        
        File ddl = currentPath.resolve(Constants.MANIFEST_DDL).toFile();
        if (!ddl.exists()) {
            return Collections.emptyList();
        }
        
        K8sDdLServiceMeta meta = new K8sDdLServiceMeta();
        meta.setFrameCode(ctx.getFramework());
        meta.setName(serviceDir.getName());
        meta.setManifest(PathUtils.relative(ddl, root));
        
        String content = FileUtil.readString(ddl, StandardCharsets.UTF_8);
        K8sServiceInfo serviceInfo = YamlUtils.parseYaml(content, K8sServiceInfo.class);
        
        meta.setVersion(serviceInfo.getVersion());
        if (!StringUtils.equals(meta.getName(), serviceInfo.getName())) {
            ctx.addError(String.format("框架%s服务%s ddl文件放置有误，name不一致", ctx.getFramework(), meta.getName()));
        }
        
        meta.setDependencies(serviceInfo.getDependencies() == null ? new ArrayList<>(0) : serviceInfo.getDependencies());
        if (serviceInfo.getArtifact() != null && StrUtil.isNotBlank(serviceInfo.getArtifact().getHelm())) {
            meta.getCharts().add(serviceInfo.getArtifact().getHelm());
        }
        return Collections.singletonList(meta);
    }
    
    public static Path getConfPath(String root) {
        return Paths.get(root, "config");
    }
    
    public static Path getPkgPath(String root) {
        return Paths.get(root, "packages", "raw");
    }
    
    public static Path getImagePath(String root) {
        return Paths.get(root, "packages", "docker");
    }
    
    public static String getMd5FileName(String pkgName) {
        return pkgName.endsWith(".md5") ? pkgName : pkgName + ".md5";
    }
    
}
