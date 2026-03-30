package com.datasophon.common.k8s.client;

import cn.hutool.core.bean.BeanUtil;
import com.datasophon.common.k8s.dto.ReTagDTO;
import com.datasophon.common.k8s.spec.docker.DockerImageParser;
import com.datasophon.common.k8s.vo.ImageManifest;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.PushImageCmd;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.Identifier;
import com.github.dockerjava.api.model.Repository;
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

    private final String DEFAULT_ORG = "bigdata";

    public DockerClientWrapperImpl(AuthConfig auth) {
        this.auth = auth;
    }

    @Override
    public List<ImageManifest> load(File file) {
        DockerImageParser parser = new DockerImageParser();
        try {
            List<ImageManifest> result = new ArrayList<>();
            List<ImageManifest> originalImages = parser.parseImage(file);
            log.info("从文件{}中，解析出{}个镜像tag", file.getName(), originalImages.size());
            try (DockerClient client = DockerClientFactory.newClient();) {
                try (InputStream inputStream = Files.newInputStream(file.toPath())) {
                    log.info("执行命令：docker load -i {}", file.getName());
                    client.loadImageCmd(inputStream).exec();
                }
                originalImages.forEach(manifest -> {
                    ImageManifest newImage = BeanUtil.toBean(manifest, ImageManifest.class);

//                    重新命名为私库的tag
                    String newTag = manifest.getImage();
                    if (!newTag.endsWith("-" + newImage.getArch())) {
                        newTag = newTag + "-" + newImage.getArch();
                    }
                    newTag = normalTag(newTag);
                    newImage.setImage(newTag);

                    log.info("为镜像{}添加新的tag:{}", manifest.getFullTag(), newImage.getFullTag());
                    client.tagImageCmd(manifest.getFullTag(), newTag, manifest.getVersion()).exec();

//                    删除原来的tag
                    log.info("删除镜像tag:{}", manifest.getFullTag());
                    client.removeImageCmd(manifest.getFullTag()).exec();
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

            client.tagImageCmd(dto.getOldTag(), repo, version).exec();

            if (dto.isRemoveOldTag()) {
                client.removeImageCmd(dto.getOldTag()).exec();
            }
            return finalTag;
        } catch (IOException e) {
            throw new IllegalStateException(String.format("IO异常，%s", e.getMessage()), e);
        }
    }

    @Override
    public String normalTag(String tag) {
        String address = auth.getRegistryAddress();
        int protocolIdx = address.indexOf("://");
        if (protocolIdx != -1) {
            address = address.substring(protocolIdx + 3);
        }
        if (!address.endsWith("/")) {
            address += "/";
        }

        int count = 0;
        for (int i = 0; i < tag.length(); i++) {
            if (tag.charAt(i) == '/') {
                count++;
            }
        }
        if (count == 0) {
            return address + DEFAULT_ORG + tag;
        } else if (count == 1) {
            return address + tag;
        } else if (count == 2) {
            String[] parts = tag.split("/");
            if (tag.startsWith("docker.io")) {
                if ("library".equals(parts[1])) {
                    return address + DEFAULT_ORG + "/" + parts[2];
                }
            }
            return address + parts[1] + "/" + parts[2];
        } else if (count == 3) {
//            count == 3, 只有nexus私库有这个问题，当作正常值
            int i;
            int cnt = 0;
            for (i = tag.length() - 1; i >= 0; i--) {
                if (tag.charAt(i) == '/') {
                    cnt++;
                }
                if (cnt == count) {
                    break;
                }
            }
            String simpleTag = tag.substring(i + 1);
            return address + simpleTag;
        } else {
            throw new IllegalArgumentException(String.format("tag %s do not matched the docker specification", tag));
        }
    }


    @Override
    public void push(String imageId) {
        try (DockerClient client = DockerClientFactory.newClient()) {
            log.info("执行docker push {}", imageId);
            Identifier identifier = fromImageId(imageId);
            PushImageCmd cmd = client.pushImageCmd(identifier);
            cmd = cmd.withAuthConfig(auth);
            cmd.exec(new ResultCallback.Adapter<>()).awaitCompletion();
        } catch (IOException e) {
            throw new IllegalStateException(String.format("IO异常，%s", e.getMessage()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }


    private Identifier fromImageId(String imageId) {
//        fix Identifier#fromCompoundString
//        nexus的命名方式和docker的规范不一致，这里修复nexus获取镜像名的方法。
//        @see https://help.sonatype.com/en/docker-registry.html
        int count = 0;
        for (int i = 0; i < imageId.length(); i++) {
            if (imageId.charAt(i) == '/') {
                count++;
            }
        }
        if (count < 2 || count > 3) {
            throw new IllegalArgumentException(String.format("tag %s do not matched the docker specification, consider full qualifier, eg: docker.io/org_name/image_name", imageId));
        }

        int lastSlashIdx = imageId.lastIndexOf("/");
        String imageName = imageId.substring(lastSlashIdx + 1);
        int colonIdx = imageName.indexOf(":");
        if (colonIdx == -1) {
            return new Identifier(new Repository(imageId), null);
        } else {
            String version = imageName.substring(colonIdx + 1);
            return new Identifier(new Repository(imageId.substring(0, lastSlashIdx) + "/" + imageName.substring(0, colonIdx)), version);
        }
    }
}
