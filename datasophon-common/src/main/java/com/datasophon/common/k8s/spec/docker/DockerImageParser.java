package com.datasophon.common.k8s.spec.docker;


import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.io.FileUtil;
import com.alibaba.fastjson.JSONObject;
import com.datasophon.common.k8s.exception.UnsupportedFormatException;
import com.datasophon.common.k8s.vo.docker.ImageManifest;
import com.datasophon.common.utils.TarUtils;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class DockerImageParser {


    public static final String INDEX_FILE = "index.json";
    public static final String MANIFEST_JSON = "manifest.json";

    private final File tar;

    public DockerImageParser(File tar) {
        this.tar = tar;
    }

    /**
     * 解析Docker save生成的tar包，提取所有镜像的标签和平台信息。
     *
     * @return 镜像清单列表（每个标签对应一个条目）
     */
    public List<ImageManifest> parseImage() throws IOException {
        String unzipDir = null;
        try {
            unzipDir = TarUtils.decompressToTemp(tar.getAbsolutePath());
            File ociFile = new File(unzipDir, INDEX_FILE);
            File oldFormat = new File(unzipDir, MANIFEST_JSON);

//            优先使用旧格式
            if (oldFormat.exists()) {
                return parseDockerFormat(unzipDir);
            } else if (ociFile.exists()) {
                return parseOciFormat(unzipDir);
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
     *
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
                im.setTag(parts[1]);

                ImageManifest.ImagePlatform platform = new ImageManifest.ImagePlatform();
                platform.setOs(config.getOs());
                platform.setArch(config.getArchitecture());
                im.setPlatforms(CollectionUtil.newArrayList(platform));
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
     *
     * @param unzipDir
     * @return
     */
    private List<ImageManifest> parseOciFormat(String unzipDir) {
        String content = FileUtil.readString(new File(unzipDir, INDEX_FILE), StandardCharsets.UTF_8);
        OciIndex index = JSONObject.parseObject(content, OciIndex.class);

        List<ImageManifest> result = new ArrayList<>();
        for (OciManifestRef ref : index.getManifests()) {
            String tag = null;
            if (ref.getAnnotations() != null) {
                tag = ref.getAnnotations().get("io.containerd.image.name");
            }
            if (tag == null) {
                continue;
            }


            List<ImageHostPlatform> platforms = new ArrayList<>();
            if (ref.getPlatform() != null) {
                platforms.add(ref.getPlatform());
            } else {
//                单架构
//                vnd.docker.distribution.manifest.list.v
                if (ref.getMediaType().contains("vnd.oci.image.manifest.v")) {
                    String digest = ref.getDigest();
                    ImageHostPlatform platform = parseSinglePlatform(unzipDir, digest);
                    if (platform != null) {
                        platforms.add(platform);
                    }
                } else if (ref.getMediaType().contains("vnd.docker.distribution.manifest.list.v")) {
                    String digest = ref.getDigest();
                    List<ImageHostPlatform> tempList = parseMultiPlatforms(unzipDir, digest);
                    platforms.addAll(tempList);
                } else {
                    throw new UnsupportedFormatException(String.format("file: %s index.json: unsupported format of media type: %s", tar.getName(), ref.getMediaType()));
                }
            }
            if (platforms.isEmpty()) {
                throw new UnsupportedFormatException(String.format("文件: %s无法解析镜像%s的架构", tar.getName(), tag));
            }

            String[] parts = splitRepoTag(tag);
            ImageManifest im = new ImageManifest();
            im.setImage(parts[0]);
            im.setTag(parts[1]);

            List<ImageManifest.ImagePlatform> pms = platforms.stream().map(p-> {
                ImageManifest.ImagePlatform pm = new ImageManifest.ImagePlatform();
                pm.setOs(p.getOs());
                pm.setArch(p.getArchitecture());
                return pm;
            }).collect(Collectors.toList());
            im.setPlatforms(pms);
            result.add(im);
        }
        return result;
    }

    private ImageHostPlatform parseSinglePlatform(String unzipDir, String digest) {
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
            throw new UnsupportedFormatException(String.format("unsupported format of media type: %s, sha256: %s", manifest.getMediaType(), digest));
        }
        return null;
    }


    private List<ImageHostPlatform> parseMultiPlatforms(String unzipDir, String digest) {
        String content = readDigestFileContent(unzipDir, digest);
        if (content == null) {
            return new ArrayList<>(0);
        }
        List<ImageHostPlatform> result = new ArrayList<>();

        OciIndex manifest = JSONObject.parseObject(content, OciIndex.class);
        for (OciManifestRef ref : manifest.getManifests()) {
            if (ref.getPlatform() != null) {
                result.add(ref.getPlatform());
            } else {
                log.warn("file: {}，digest: {} can not parse platforms", tar.getName(), ref.getDigest());
            }
        }
        return result;
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


}