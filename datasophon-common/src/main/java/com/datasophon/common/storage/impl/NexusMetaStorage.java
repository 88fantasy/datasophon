package com.datasophon.common.storage.impl;

import cn.hutool.core.util.StrUtil;
import com.datasophon.common.Constants;
import com.datasophon.common.function.ThrowableSupplier;
import com.datasophon.common.model.uni.NexusUri;
import com.datasophon.common.storage.MetaStorage;
import com.datasophon.common.storage.vo.ServiceMetaItem;
import com.datasophon.common.utils.NexusFileUtils;
import com.datasophon.common.utils.PathUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * @author zhanghuangbin
 */
@Slf4j
public class NexusMetaStorage implements MetaStorage {

    public final static String REPO = "raw";

    @Override
    public boolean isEnabled() {
        return getNexusUri().isEnabled();
    }

    @Override
    public List<ServiceMetaItem> listService(String type) {
        validType(type);
        try {
            List<NexusFileUtils.Component> components = NexusFileUtils.listMatchedItem(REPO, String.format("/meta/*/%s/*/service_ddl.json", type));
            List<ServiceMetaItem> items = new ArrayList<>();
            for (NexusFileUtils.Component component : components) {
                ServiceMetaItem item = new ServiceMetaItem();
                item.setType(type);
                item.setDownloadUrl(component.getDownloadUrl());
                try {
                    String name = component.getName();
                    if (name.startsWith("/")) {
                        name = name.substring(1);
                    }
                    String[] parts = name.split("/");
                    item.setFramework(parts[1]);
                    item.setServiceName(parts[3]);
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
    public void saveServiceDdl(ServiceMetaItem item, String content) throws IOException {
        String path = String.format("/meta/%s/%s/%s", item.getFramework(), item.getType(), item.getServiceName());
        NexusFileUtils.uploadFileToRawRepo(path, Constants.SERVICE_DDL, content);
    }

    @Override
    public void downResource(ServiceMetaItem item, String relativePath, ThrowableSupplier<OutputStream> supplier) throws Exception {
        String downloadUrl = item.getDownloadUrl();
        if (StrUtil.isBlank(downloadUrl)) {
            downloadUrl = String.format("/meta/%s/%s/%s/%s", item.getFramework(), item.getType(), item.getServiceName(), relativePath);
            downloadUrl = NexusFileUtils.getNexusRawObjectUrl(downloadUrl);
        }
        NexusFileUtils.downStream(downloadUrl, supplier.get());
    }


    @Override
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


    private NexusUri getNexusUri() {
        NexusUri uri = new NexusUri();
        uri.setEnabled(Constants.NEXUS_ENABLE);
        uri.setUri(String.format("http://%s:%s", Constants.NEXUS_IP, Constants.NEXUS_PORT));
        uri.setUser(Constants.NEXUS_USERNAME);
        uri.setPassword(Constants.NEXUS_PASSWORD);
        return uri;
    }

    private void validType(String type) {
        if (!Arrays.asList(VOS_DDL, K8S).contains(type)) {
            throw new IllegalArgumentException(String.format("type %s is allowed", type));
        }
    }

    private void ensureDirValid(File dir) {
        if (!dir.exists()) {
            throw new IllegalStateException(dir.getAbsolutePath() + " not exists");
        }
        if (!dir.isDirectory()) {
            throw new IllegalArgumentException(dir.getAbsolutePath() + " is not dir");
        }
    }

    private void ensureNexusEnable() {
        NexusUri registry = getNexusUri();
        if (!registry.isEnabled()) {
            throw new IllegalStateException("datasophon require an nexus available, but nexus is disabled");
        }
    }

}
