package com.datasophon.common.storage.impl;

import cn.hutool.core.util.StrUtil;
import com.datasophon.common.Constants;
import com.datasophon.common.storage.MetaStorage;
import com.datasophon.common.storage.vo.ServiceMetaItem;
import com.datasophon.common.utils.NexusFileUtils;
import com.datasophon.common.utils.PathUtils;
import com.datasophon.common.utils.nexus.NexusFacade;
import com.datasophon.common.utils.nexus.vo.Component;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
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
public class NexusMetaStorage extends NexusStorageSupport implements MetaStorage {

    public final static String REPO = "raw";

    @Override
    public List<ServiceMetaItem> listService(String type) {
        validType(type);
        try {
            String ddlName = VOS_DDL.equals(type) ? Constants.SERVICE_DDL : Constants.MANIFEST_DDL;
            List<Component> components = NexusFacade.getCommonClient().listMatchedItem(REPO, String.format("/meta/*/%s/*/%s", type, ddlName));
            List<ServiceMetaItem> items = new ArrayList<>();
            for (Component component : components) {
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
    public void downResource(ServiceMetaItem item, String relativePath, OutputStreamSupplier supplier) throws IOException {
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

    @Override
    public void removeMeta(String frameCode, String serviceName, String type) {
        NexusFileUtils.removeFolderFromRawRepo(String.format("/meta/%s/%s/%s", frameCode, type, serviceName));
    }


    private void validType(String type) {
        if (!Arrays.asList(VOS_DDL, K8S).contains(type)) {
            throw new IllegalArgumentException(String.format("type %s is allowed", type));
        }
    }


}
