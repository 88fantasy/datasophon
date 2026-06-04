package com.datasophon.common.k8s.client;

import com.datasophon.common.k8s.config.DockerRegistryOptions;
import com.datasophon.common.k8s.spec.docker.DockerImageParser;
import com.datasophon.common.k8s.spec.docker.DockerTagUtils;
import com.datasophon.common.k8s.vo.docker.ImageManifest;
import com.datasophon.common.k8s.vo.docker.LoadImageResult;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import cn.hutool.core.bean.BeanUtil;

/**
 * @author zhanghuangbin
 */
@Slf4j
public class DockerClientWrapperImpl implements DockerClientWrapper {
    
    private final DockerRegistryOptions options;
    
    public DockerClientWrapperImpl(DockerRegistryOptions options) {
        this.options = options;
    }
    
    @Override
    public void login() {
        DockerClient client = new DockerClient();
        client.login(options.getRegistry(), options.getUsername(), options.getPassword());
    }
    
    @Override
    public List<LoadImageResult> load(File file) throws IOException {
        DockerImageParser parser = new DockerImageParser(file);
        List<ImageManifest> parsedImages = parser.parseImage();
        if (parsedImages.isEmpty()) {
            log.warn("从文件{}中，解析出0个镜像 tag", file.getName());
            return new ArrayList<>(0);
        }
        
        log.info("从文件{}中，解析出{}个镜像 tag", file.getName(), parsedImages.size());
        DockerClient client = new DockerClient();
        
        List<LoadImageResult> results = new ArrayList<>();
        for (ImageManifest manifest : parsedImages) {
            ImageManifest.ImagePlatform platform = manifest.getPlatforms().get(0);
            log.info("执行命令：docker load -i {} --platform {}", file.getName(), platform.getPlatform());
            client.load(file, platform.getPlatform());
            
            LoadImageResult result = BeanUtil.toBean(manifest, LoadImageResult.class);
            result.setOldImage(manifest.getImage());
            result.setOldTag(manifest.getTag());
            result.setOs(platform.getOs());
            result.setArch(platform.getArch());
            
            // 重新命名为私库的 tag
            result.setNewImage(DockerTagUtils.normalRepository(options.getImageRegistry(), manifest.getImage()));
            result.setNewTag(DockerTagUtils.normalTag(manifest.getTag(), platform.getOs(), platform.getArch()));
            
            log.info("为镜像{}添加新的 tag:{}", result.getOldQualifierImage(), result.getNewQualifierImage());
            client.tagImage(result.getOldQualifierImage(), result.getNewQualifierImage());
            
            // 删除原来的 tag
            log.info("删除镜像 tag:{}", result.getOldQualifierImage());
            client.removeImage(result.getOldQualifierImage());
            results.add(result);
        }
        return results;
    }
    
    @Override
    public void push(String imageId) {
        DockerClient client = new DockerClient();
        log.info("执行命令：docker push {}", imageId);
        client.push(imageId);
    }
    
    @Override
    public void createManifest(String manifestName, List<LoadImageResult> images) {
        String selfRepoManifestName = DockerTagUtils.normalRepository(options.getImageRegistry(), manifestName);
        DockerClient client = new DockerClient();
        
        // 删除已存在的 manifest
        log.info("删除已存在的 manifest: {}", selfRepoManifestName);
        client.rmManifest(selfRepoManifestName, true);
        
        // 收集所有新的镜像 tag
        List<String> tags = images.stream()
                .map(LoadImageResult::getNewQualifierImage)
                .collect(Collectors.toList());
        
        // 创建 manifest
        log.info("创建 manifest: {}, 包含镜像 tags: {}", selfRepoManifestName, tags);
        client.createManifest(selfRepoManifestName, tags, true);
        
        // 为每个镜像添加架构标注
        for (LoadImageResult image : images) {
            String tag = image.getNewQualifierImage();
            log.info("标注 manifest: {} 的镜像{}，arch={}, os={}", selfRepoManifestName, tag, image.getArch(), image.getOs());
            client.annotateManifest(selfRepoManifestName, tag, image.getArch(), image.getOs());
        }
        
        // 推送 manifest 到仓库
        log.info("推送 manifest: {}", selfRepoManifestName);
        client.pushManifest(selfRepoManifestName, true);
        
        client.rmManifest(selfRepoManifestName, false);
    }
}
