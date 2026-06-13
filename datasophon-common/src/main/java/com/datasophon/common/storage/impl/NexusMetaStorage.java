package com.datasophon.common.storage.impl;

import com.datasophon.common.Constants;
import com.datasophon.common.k8s.spec.helm.HelmUtils;
import com.datasophon.common.storage.MetaStorage;
import com.datasophon.common.storage.vo.ServiceMetaItem;
import com.datasophon.common.utils.NexusFileUtils;
import com.datasophon.common.utils.PathUtils;
import com.datasophon.common.utils.nexus.NexusFacade;
import com.datasophon.common.utils.nexus.vo.Component;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import lombok.extern.slf4j.Slf4j;
import cn.hutool.core.io.FileUtil;

/**
 * @author zhanghuangbin
 */
@Slf4j
public class NexusMetaStorage extends NexusStorageSupport implements MetaStorage {
    
    public static final String REPO = "raw";
    
    @Override
    public List<ServiceMetaItem> listService(String type) {
        validType(type);
        try {
            String ddlName = PHYSICAL.equals(type) ? Constants.SERVICE_DDL : Constants.MANIFEST_DDL;
            List<Component> components = NexusFacade.getCommonClient().listMatchedItem(REPO, String.format("/meta/*/*/%s", ddlName));
            List<ServiceMetaItem> items = new ArrayList<>();
            for (Component component : components) {
                ServiceMetaItem item = new ServiceMetaItem();
                item.setType(type);
                try {
                    String name = component.getName();
                    if (name.startsWith("/")) {
                        name = name.substring(1);
                    }
                    String[] parts = name.split("/");
                    item.setFramework(parts[1]);
                    item.setServiceName(parts[2]);
                } catch (ArrayIndexOutOfBoundsException exception) {
                    log.warn("ddl file: {} matched query pattern, but can not be parsed, ignore it", component.getName());
                    continue;
                }
                items.add(item);
            }
            return items;
        } catch (IOException e) {
            throw new IllegalStateException(String.format("IO异常，%s", e.getMessage()), e);
        }
    }
    
    @Override
    @SuppressWarnings("deprecated")
    public void saveServiceDdl(ServiceMetaItem item, String content) throws IOException {
        String path = String.format("/meta/%s/%s", item.getFramework(), item.getServiceName());
        NexusFileUtils.uploadFileToRawRepo(path, Constants.SERVICE_DDL, content);
    }
    
    @Override
    public String getHelmValuesYaml(ServiceMetaItem item, String chartName) throws IOException {
        File tmp = null;
        String extractDir = null;
        try {
            tmp = PathUtils.createTmpFile("helm", "_" + chartName);
            try (OutputStream out = Files.newOutputStream(tmp.toPath())) {
                downResource(item, chartName, () -> out);
            }
            extractDir = HelmUtils.unzip(tmp);
            File valueFile = HelmUtils.getValueFile(extractDir);
            if (!valueFile.exists()) {
                throw new IllegalStateException("chart 中未找到 values.yaml 文件");
            }
            if (valueFile.length() == 0) {
                return null;
            }
            return FileUtil.readString(valueFile, StandardCharsets.UTF_8);
        } finally {
            FileUtil.del(tmp);
            FileUtil.del(extractDir);
        }
    }
    
    @Override
    public void downResource(ServiceMetaItem item, String relativePath, OutputStreamSupplier supplier) throws IOException {
        String downloadUrl = String.format("/meta/%s/%s/%s", item.getFramework(), item.getServiceName(), relativePath);
        downloadUrl = NexusFacade.getRawRepoClient().getNexusRawObjectUrl(downloadUrl);
        NexusFacade.getCommonClient().download(downloadUrl, supplier.get());
    }
    
    @Override
    @SuppressWarnings("deprecated")
    public void moveToStorage(File dir, Function<String, String> relativePathHandler) throws IOException {
        ensureDirValid(dir);
        ensureNexusEnable();
        Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                String relative = PathUtils.relative(path.getParent().toFile(), dir.getAbsolutePath());
                relative = relativePathHandler.apply(relative);
                relative = PathUtils.unixStyle(relative);
                
                log.info("upload {} to raw repo, path: {}", path, relative);
                NexusFileUtils.uploadFileToRawRepo(relative, path.toFile());
                return FileVisitResult.CONTINUE;
            }
            
            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                throw exc;
            }
        });
    }
    
    @Override
    @SuppressWarnings("deprecated")
    public void removeMeta(String frameCode, String serviceName, String type) {
        NexusFileUtils.removeFolderFromRawRepo(String.format("/meta/%s/%s", frameCode, serviceName));
    }
    
    private void validType(String type) {
        if (!Arrays.asList(PHYSICAL, K8S).contains(type)) {
            throw new IllegalArgumentException(String.format("type %s is allowed", type));
        }
    }
    
}
