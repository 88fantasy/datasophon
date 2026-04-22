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

/**
 * Docker 镜像解析器
 * 用于解析 Docker save 命令生成的镜像 tar 包，支持 OCI 格式和旧版 Docker 格式
 */
@Slf4j
public class DockerImageParser {

    /**
     * OCI 镜像索引文件名
     */
    public static final String INDEX_FILE = "index.json";

    /**
     * 旧版 Docker 镜像清单文件名
     */
    public static final String MANIFEST_JSON = "manifest.json";

    /**
     * Docker 镜像 tar 包文件
     */
    private final File tar;

    /**
     * 构造 Docker 镜像解析器
     *
     * @param tar Docker save 生成的 tar 包文件
     */
    public DockerImageParser(File tar) {
        this.tar = tar;
    }

    /**
     * 解析 Docker save 生成的 tar 包，提取所有镜像的标签和平台信息。
     * 支持两种格式：
     * 1. OCI 格式 (index.json) - 新版通用格式
     * 2. Docker 旧格式 (manifest.json) - 向后兼容
     *
     * @return 镜像清单列表（每个标签对应一个条目，包含镜像名、标签和平台信息）
     * @throws IOException 当读取文件或解压 tar 包失败时抛出
     * @throws UnsupportedFormatException 当 tar 包不包含有效的镜像格式时抛出
     */
    public List<ImageManifest> parseImage() throws IOException {
        String unzipDir = null;
        try {
            // 解压 tar 包到临时目录
            unzipDir = TarUtils.decompressToTemp(tar.getAbsolutePath());
            File ociFile = new File(unzipDir, INDEX_FILE);
            File oldFormat = new File(unzipDir, MANIFEST_JSON);

            // 优先使用 OCI 通用格式，其次使用旧版 Docker 格式
            if (ociFile.exists()) {
                return parseOciFormat(unzipDir);
            } else if (oldFormat.exists()) {
                return parseDockerFormat(unzipDir);
            }
            throw new UnsupportedFormatException(String.format("lack of %s and %s", INDEX_FILE, MANIFEST_JSON));
        } catch (UnsupportedFormatException e) {
            throw new IllegalStateException(String.format("解析文件%s失败，%s", tar.getName(), e.getMessage()), e);
        } finally {
            // 清理临时目录
            FileUtil.del(unzipDir);
        }
    }

    /**
     * 解析旧版 Docker 镜像格式（manifest.json 格式）
     *
     * @param unzipDir 解压后的目录
     * @return 镜像清单列表
     */
    private List<ImageManifest> parseDockerFormat(String unzipDir) {
        List<ImageManifest> result = new ArrayList<>();
        File oldFormat = new File(unzipDir, MANIFEST_JSON);
        // 读取 manifest.json 内容
        String content = FileUtil.readString(oldFormat, StandardCharsets.UTF_8);

        // 解析为 DockerManifestEntry 列表
        List<DockerManifestEntry> entries = JSONObject.parseArray(content, DockerManifestEntry.class);

        for (DockerManifestEntry entry : entries) {
            // 读取配置文件（包含 OS 和架构信息）
            String configFile = entry.getConfig();
            String configContent = FileUtil.readString(Paths.get(unzipDir, configFile).toFile(), StandardCharsets.UTF_8);
            ImageHostPlatform config = JSONObject.parseObject(configContent, ImageHostPlatform.class);

            // 为该镜像的每个 RepoTag 生成一个镜像清单条目
            for (String repoTag : entry.getRepoTags()) {
                String[] parts = splitRepoTag(repoTag);
                ImageManifest im = new ImageManifest();
                im.setImage(parts[0]);
                im.setTag(parts[1]);

                // 设置平台信息（OS 和 CPU 架构）
                ImageManifest.ImagePlatform platform = new ImageManifest.ImagePlatform();
                platform.setOs(config.getOs());
                platform.setArch(config.getArchitecture());
                im.setPlatforms(CollectionUtil.newArrayList(platform));
                result.add(im);
            }
        }
        return result;
    }

    /**
     * 分割镜像仓库标签（格式：repo/image:tag）
     *
     * @param repoTag 完整的镜像标签，如 nginx:latest 或 docker.io/library/nginx:1.21
     * @return 包含镜像名和标签的数组，[镜像名，标签]
     */
    private String[] splitRepoTag(String repoTag) {
        int colonIdx = repoTag.lastIndexOf(':');
        if (colonIdx > 0) {
            return new String[]{repoTag.substring(0, colonIdx), repoTag.substring(colonIdx + 1)};
        } else {
            // 没有标签时，默认使用 latest
            return new String[]{repoTag, "latest"};
        }
    }

    /**
     * 解析 OCI 格式镜像（新版标准格式）
     * 支持：
     * - 单架构镜像（image manifest）
     * - 多架构镜像（image index / manifest list）
     *
     * @param unzipDir 解压后的目录
     * @return 镜像清单列表
     */
    private List<ImageManifest> parseOciFormat(String unzipDir) {
        // 读取 index.json 索引文件
        String content = FileUtil.readString(new File(unzipDir, INDEX_FILE), StandardCharsets.UTF_8);
        OciIndex index = JSONObject.parseObject(content, OciIndex.class);

        List<ImageManifest> result = new ArrayList<>();
        // 遍历所有镜像引用
        for (OciManifestRef ref : index.getManifests()) {
            // 从注解中获取镜像标签
            String tag = null;
            if (ref.getAnnotations() != null) {
                tag = ref.getAnnotations().get("io.containerd.image.name");
            }
            if (tag == null) {
                // 没有标签的镜像跳过
                continue;
            }


            List<ImageHostPlatform> platforms = new ArrayList<>();
            // 直接在引用中定义了平台信息
            if (ref.getPlatform() != null) {
                platforms.add(ref.getPlatform());
            } else {
                // 根据媒体类型判断镜像类型并解析平台信息
                // 单架构镜像（application/vnd.oci.image.manifest.v1+json）
                if (ref.getMediaType().contains("manifest.v")) {
                    String digest = ref.getDigest();
                    ImageHostPlatform platform = parseSinglePlatform(unzipDir, digest);
                    if (platform != null) {
                        platforms.add(platform);
                    }
                // 多架构镜像列表（application/vnd.docker.distribution.manifest.list.v2+json）
                } else if (ref.getMediaType().contains("manifest.list.v")) {
                    String digest = ref.getDigest();
                    List<ImageHostPlatform> tempList = parseMultiPlatforms(unzipDir, digest);
                    platforms.addAll(tempList);
                } else {
                    throw new UnsupportedFormatException(String.format("file: %s index.json: unsupported format of media type: %s", tar.getName(), ref.getMediaType()));
                }
            }
            if (platforms.isEmpty()) {
                throw new UnsupportedFormatException(String.format("文件：%s无法解析镜像%s的架构", tar.getName(), tag));
            }

            // 分割镜像名和标签
            String[] parts = splitRepoTag(tag);
            ImageManifest im = new ImageManifest();
            im.setImage(parts[0]);
            im.setTag(parts[1]);

            // 转换平台信息格式
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

    /**
     * 解析单架构镜像的平台信息
     * 通过读取镜像 manifest 中的 config 字段获取 OS 和架构信息
     *
     * @param unzipDir 解压后的目录
     * @param digest 镜像摘要（sha256:xxx）
     * @return 平台信息，解析失败返回 null
     */
    private ImageHostPlatform parseSinglePlatform(String unzipDir, String digest) {
        // 读取镜像 manifest 文件
        String content = readDigestFileContent(unzipDir, digest);
        if (content == null) {
            return null;
        }
        OciManifest manifest = JSONObject.parseObject(content, OciManifest.class);

        // 从 config 配置文件中读取平台信息
        if (manifest.getConfig() != null) {
            String configDigestContent = readDigestFileContent(unzipDir, manifest.getConfig().getDigest());
            if (configDigestContent != null) {
                return JSONObject.parseObject(configDigestContent, ImageHostPlatform.class);
            }
        } else if (manifest.getMediaType() != null && manifest.getMediaType().contains("image.index")) {
            // 不支持的镜像索引类型
            throw new UnsupportedFormatException(String.format("unsupported format of media type: %s, sha256: %s", manifest.getMediaType(), digest));
        }
        return null;
    }


    /**
     * 解析多架构镜像列表的平台信息
     * 多架构镜像包含多个子镜像引用，每个引用对应一个特定平台的镜像
     *
     * @param unzipDir 解压后的目录
     * @param digest 镜像索引摘要（sha256:xxx）
     * @return 支持的平台列表
     */
    private List<ImageHostPlatform> parseMultiPlatforms(String unzipDir, String digest) {
        String content = readDigestFileContent(unzipDir, digest);
        if (content == null) {
            return new ArrayList<>(0);
        }
        List<ImageHostPlatform> result = new ArrayList<>();

        // 解析镜像索引
        OciIndex manifest = JSONObject.parseObject(content, OciIndex.class);
        for (OciManifestRef ref : manifest.getManifests()) {
            // 只有引用的层存在，才认为架构支持
            if (ref.getPlatform() != null && blobExist(unzipDir, ref.getDigest())) {
                result.add(ref.getPlatform());
            }
        }
        return result;
    }


    /**
     * 根据摘要读取 blob 文件内容
     * Docker/OCI 镜像的 blob 文件存储路径为：blobs/sha256/{hash}
     *
     * @param unzipDir 解压后的目录
     * @param digest 镜像摘要（格式：sha256:xxx）
     * @return 文件内容，文件不存在返回 null
     */
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

    /**
     * 检查指定的 blob 文件是否存在
     *
     * @param unzipDir 解压后的目录
     * @param digest 镜像摘要（格式：sha256:xxx）
     * @return blob 文件是否存在
     */
    private boolean blobExist(String unzipDir, String digest) {
        if (digest != null && digest.startsWith("sha256:")) {
            String hash = digest.substring(7);
            String subPath = "blobs/sha256/" + hash;
            File file = Paths.get(unzipDir, subPath).toFile();
            return file.exists();
        }
        return false;
    }


}
