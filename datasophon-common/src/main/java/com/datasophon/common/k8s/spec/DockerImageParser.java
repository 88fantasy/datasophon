package com.datasophon.common.k8s.spec;


import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSONObject;
import com.datasophon.common.k8s.vo.ImageManifest;
import com.datasophon.common.utils.TarUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class DockerImageParser {


    public static final String INDEX_FILE = "index.json";
    public static final String MANIFEST_JSON = "manifest.json";

    /**
     * 解析Docker save生成的tar包，提取所有镜像的标签和平台信息。
     *
     * @param tar tar文件
     * @return 镜像清单列表（每个标签对应一个条目）
     */
    public List<ImageManifest> parseImage(File tar) throws IOException {
        String unzipDir = null;
        try {
            unzipDir = TarUtils.decompressToTemp(tar.getAbsolutePath());
            File ociFile = new File(unzipDir, INDEX_FILE);
            File oldFormat = new File(unzipDir, MANIFEST_JSON);
            if (ociFile.exists()) {
                return parseOciFormat(unzipDir);
            } else if (oldFormat.exists()) {
                return parseDockerFormat(unzipDir);
            }
            throw new UnsupportedFormatException(String.format("lack of %s and %s", INDEX_FILE, MANIFEST_JSON));
        } catch (UnsupportedFormatException e) {
            throw new IllegalStateException(String.format("解析文件%s失败，%s", tar.getName(), e.getMessage()), e);
        } finally {
            FileUtil.del(unzipDir);
        }
    }

    /**
     * 解析旧docker格式
     * @param unzipDir
     * @return
     */
    private List<ImageManifest> parseDockerFormat(String unzipDir) {
        List<ImageManifest> result = new ArrayList<>();
        File oldFormat = new File(unzipDir, MANIFEST_JSON);
        String content = FileUtil.readString(oldFormat, StandardCharsets.UTF_8);

        List<DockerManifestEntry> entries = JSONObject.parseArray(content, DockerManifestEntry.class);

        for (DockerManifestEntry entry : entries) {
            // 读取配置文件
            String configFile = entry.getConfig();
            String configContent = FileUtil.readString(Paths.get(unzipDir, configFile).toFile(), StandardCharsets.UTF_8);
            ImageHostPlatform config = JSONObject.parseObject(configContent, ImageHostPlatform.class);
            for (String repoTag : entry.getRepoTags()) {
                String[] parts = splitRepoTag(repoTag);
                ImageManifest im = new ImageManifest();
                im.setImage(parts[0]);
                im.setVersion(parts[1]);
                im.setOs(config.getOs());
                im.setArch(config.getArchitecture());
                result.add(im);
            }
        }
        return result;
    }

    private String[] splitRepoTag(String repoTag) {
        int colonIdx = repoTag.lastIndexOf(':');
        if (colonIdx > 0) {
            return new String[]{repoTag.substring(0, colonIdx), repoTag.substring(colonIdx + 1)};
        } else {
            return new String[]{repoTag, "latest"};
        }
    }

    /**
     * 解析oci格式(新版）
     * @param unzipDir
     * @return
     */
    private List<ImageManifest> parseOciFormat(String unzipDir) {
        String content = FileUtil.readString(new File(unzipDir, INDEX_FILE), StandardCharsets.UTF_8);
        List<ImageManifest> result = new ArrayList<>();
        OciIndex index = JSONObject.parseObject(content, OciIndex.class);
        for (OciManifestRef ref : index.getManifests()) {
            String tag = null;
            if (ref.getAnnotations() != null) {
                tag = ref.getAnnotations().get("org.opencontainers.image.ref.name");
            }
            if (tag == null) {
                continue;
            }

            ImageHostPlatform platform = ref.getPlatform();
            if (platform == null) {
                String digest = ref.getDigest();
                platform = parsePlatformFromOldFormat(unzipDir, digest);
            }

            if (platform != null) {
                String[] parts = splitRepoTag(tag);
                ImageManifest im = new ImageManifest();
                im.setImage(parts[0]);
                im.setVersion(parts[1]);
                im.setOs(platform.getOs());
                im.setArch(platform.getArchitecture());
                result.add(im);
            }
        }
        return result;
    }

    private ImageHostPlatform parsePlatformFromOldFormat(String unzipDir, String digest) {
        String content = readDigestFileContent(unzipDir, digest);
        if (content == null) {
            return null;
        }
        OciManifest manifest = JSONObject.parseObject(content, OciManifest.class);
        if (manifest.getConfig() != null) {
            String configDigestContent = readDigestFileContent(unzipDir, manifest.getConfig().getDigest());
            if (configDigestContent != null) {
                return JSONObject.parseObject(configDigestContent, ImageHostPlatform.class);
            }
        } else if (manifest.getMediaType() != null && manifest.getMediaType().contains("image.index")) {
            throw new UnsupportedFormatException(String.format("unsupported format of media type: image.index, sha256: %s", digest));
        }
        return null;
    }



    private String readDigestFileContent(String unzipDir, String digest) {
        if (digest != null && digest.startsWith("sha256:")) {
            String hash = digest.substring(7);
            String subPath = "blobs/sha256/" + hash;
            File file = Paths.get(unzipDir, subPath).toFile();
            if (file.exists()) {
                return FileUtil.readString(file, StandardCharsets.UTF_8);
            }
        }
        return null;
    }


    public static class UnsupportedFormatException extends RuntimeException {
        private static final long serialVersionUID = -212885450196281161L;

        public UnsupportedFormatException(String message) {
            super(message);
        }
    }
}