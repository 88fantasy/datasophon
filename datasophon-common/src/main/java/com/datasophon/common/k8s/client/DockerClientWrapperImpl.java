package com.datasophon.common.k8s.client;

import cn.hutool.core.bean.BeanUtil;
import com.datasophon.common.k8s.dto.ReTagDTO;
import com.datasophon.common.k8s.spec.DockerImageParser;
import com.datasophon.common.k8s.vo.ImageManifest;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.PushImageCmd;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.Identifier;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * @author zhanghuangbin
 */
@Slf4j
public class DockerClientWrapperImpl implements DockerClientWrapper {

    private final AuthConfig auth;

    public DockerClientWrapperImpl(AuthConfig auth) {
        this.auth = auth;
    }

    @Override
    public List<ImageManifest> load(File file) {
        DockerImageParser parser = new DockerImageParser();
        try {
            List<ImageManifest> result = new ArrayList<>();
            List<ImageManifest> originalImages = parser.parseImage(file);
            try (DockerClient client = DockerClientFactory.newClient();) {
                try (InputStream inputStream = Files.newInputStream(file.toPath())) {
                    client.loadImageCmd(inputStream).exec();
                }
                originalImages.forEach(manifest-> {
                    ImageManifest newImage = BeanUtil.toBean(manifest, ImageManifest.class);

//                    重新命名为私库的tag
                    String newTag = manifest.getImage();
                    if (!newTag.endsWith("-" + newImage.getArch())) {
                        newTag = newTag + "-" + newImage.getArch();
                    }
                    newTag = normalTag(newTag);
                    newImage.setImage(newTag);
                    client.tagImageCmd(manifest.getFullTag(), newTag, manifest.getVersion());

//                    删除原来的tag
                    client.removeImageCmd(manifest.getFullTag());
                    result.add(newImage);
                });
                return result;
            }
        } catch (IOException e) {
            throw new IllegalStateException(String.format("IO异常，%s", e.getMessage()), e);
        }
    }

    @Override
    public String tag(ReTagDTO dto) {
        try (DockerClient client = DockerClientFactory.newClient()) {
            StringBuilder sb = new StringBuilder();
            if (dto.getRepository() != null) {
                sb.append(dto.getRepository());
            }
            if (sb.length() > 0) {
                sb.append("/");
            }
            sb.append(dto.getNewTag());
            String finalTag = sb.toString();
            String repo = finalTag.split(":")[0];
            String version = finalTag.split(":")[1];

            client.tagImageCmd(dto.getOldTag(), repo, version);

            if (dto.isRemoveOldTag()) {
                client.removeImageCmd(dto.getOldTag());
            }
            return finalTag;
        } catch (IOException e) {
            throw new IllegalStateException(String.format("IO异常，%s", e.getMessage()), e);
        }
    }

    @Override
    public String normalTag(String tag) {
        int idx = tag.lastIndexOf("/");
        if (idx == -1) {
            return auth.getRegistryAddress() + "/" + tag;
        } else {
            String simpleTag = tag.substring(idx + 1);
            return auth.getRegistryAddress() + "/" + simpleTag;
        }
    }


    @Override
    public void push(Identifier image) {
        try (DockerClient client = DockerClientFactory.newClient();) {
            PushImageCmd cmd = client.pushImageCmd(image);
            cmd = cmd.withAuthConfig(auth);
            cmd.exec(new ResultCallback.Adapter<>()).awaitCompletion();
        } catch (IOException e) {
            throw new IllegalStateException(String.format("IO异常，%s", e.getMessage()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }


}
