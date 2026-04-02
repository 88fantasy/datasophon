package com.datasophon.common.storage.impl;

import com.datasophon.common.k8s.client.DockerClientWrapper;
import com.datasophon.common.k8s.client.DockerClientWrapperImpl;
import com.datasophon.common.k8s.vo.ImageManifest;
import com.datasophon.common.model.uni.NexusUri;
import com.datasophon.common.storage.ImageStorage;
import com.github.dockerjava.api.model.AuthConfig;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;

/**
 * @author zhanghuangbin
 */
@Slf4j
public class NexusImageStorage extends NexusStorageSupport implements ImageStorage {


    @Override
    public void pushImages(File dir, PushCallback cb) throws IOException {
        ensureNexusEnable();
        ensureDirValid(dir);
        DockerClientWrapper client = newClient();
        Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                cb.onEntryStart(path.toFile());
                if (path.toFile().getName().endsWith(".tar")) {

                    log.info("load image {} to local repo", path);
                    List<ImageManifest> manifests = client.load(path.toFile());
                    manifests.forEach(manifest -> {
                        log.info("push image {} to repo", manifest.getFullTag());
                        client.push(manifest.getFullTag());
                    });
                }
                cb.onEntryCompleted(path.toFile());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                throw exc;
            }
        });
    }


    public static AuthConfig getAuthConfig() {
        NexusUri uri = getNexusUri();
        return new AuthConfig()
                .withUsername(uri.getUser())
                .withPassword(uri.getPassword())
                .withRegistryAddress(uri.getUri() + "/image");
    }

    private DockerClientWrapper newClient() {
        return new DockerClientWrapperImpl(getAuthConfig());
    }
}
