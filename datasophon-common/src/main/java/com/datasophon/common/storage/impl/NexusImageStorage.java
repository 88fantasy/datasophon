package com.datasophon.common.storage.impl;

import cn.hutool.core.io.FileUtil;
import com.datasophon.common.k8s.client.DockerClientWrapper;
import com.datasophon.common.k8s.client.DockerClientWrapperImpl;
import com.datasophon.common.k8s.config.DockerOptions;
import com.datasophon.common.k8s.vo.docker.LoadImageResult;
import com.datasophon.common.model.uni.NexusUri;
import com.datasophon.common.storage.ImageStorage;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author zhanghuangbin
 */
@Slf4j
public class NexusImageStorage extends NexusStorageSupport implements ImageStorage {


    @Override
    public void pushImages(File dir, PushCallback cb) {
        ensureNexusEnable();
        ensureDirValid(dir);

        List<File> files = FileUtil.loopFiles(dir).stream()
                .filter(file -> file.getName().endsWith(".tar"))
                .collect(Collectors.toList());

        List<LoadImageResult> results = new ArrayList<>();
        DockerClientWrapper client = newClient();
        for(File file : files) {
           try {
               results.addAll(client.load(file));
               cb.onEntryLoad(file, 1.0 / file.length());
           } catch (IOException e) {
               throw new IllegalStateException(String.format("load image %s failed", file.getName()), e);
           }
        }


        for (LoadImageResult result : results) {
            try {
                client.push(result.getNewQualifierImage());
                cb.onEntryPush(result, 1.0 / results.size());
            } catch (Exception e) {
                throw new IllegalStateException(String.format("push image %s failed, %s", result.getNewQualifierImage(), e.getMessage()), e);
            }
        }

        Map<String, List<LoadImageResult>> map = results.stream().collect(Collectors.groupingBy(LoadImageResult::getOldQualifierImage));
        for (Map.Entry<String, List<LoadImageResult>> entry : map.entrySet()) {
            try {
                client.createManifest(entry.getKey(), entry.getValue());
                cb.onManifest(entry.getKey(), 1.0 / map.size());
            }catch (Exception e) {
                throw new IllegalStateException(String.format("create and push manifest %s failed, %s", entry.getValue(), e.getMessage()), e);
            }
        }
    }




    private DockerClientWrapper newClient() {
        NexusUri uri = getNexusUri();
        DockerOptions options  = new DockerOptions();
        options.setInsecure(true);
        options.setRepoHost(uri.getIp());
        options.setRepoPort(uri.getPort());
        options.setUsername(uri.getUser());
        options.setPassword(uri.getPassword());
        options.setRepo(REPO);
        return new DockerClientWrapperImpl(options);
    }


}
