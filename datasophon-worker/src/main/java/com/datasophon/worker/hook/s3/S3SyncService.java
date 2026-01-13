package com.datasophon.worker.hook.s3;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import com.alibaba.nacos.common.utils.VersionUtils;
import com.datasophon.common.utils.ZipUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SystemUtils;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class S3SyncService {

    private final ObjectMapper objectMapper;

    private final S3Client client;


    private final S3SyncParams params;

    public S3SyncService(S3SyncParams params, S3Client client) {
        this.params = params;
        this.client = client;
        this.objectMapper = new ObjectMapper();
    }


    public List<ZipFileInfo> getUnsyncedVersion(String resourcePath, String metaObjectName) {
        List<ZipFileInfo> localVersions = scanLocalZipFiles(resourcePath);
        List<String> syncedVersions = readSyncedVersionsFromS3(metaObjectName);

        return localVersions.stream()
                .filter(zipFile -> !syncedVersions.contains(zipFile.getVersion()))
                .collect(Collectors.toList());
    }


    /**
     * 扫描本地zip文件
     */
    private List<ZipFileInfo> scanLocalZipFiles(String basePath) {
        File dir = new File(basePath);
        if (!dir.exists() || !dir.isDirectory()) {
            return new ArrayList<>(0);
        }
        List<ZipFileInfo> zipFiles = new ArrayList<>();
        Pattern pattern = Pattern.compile("^oss-((\\d)(\\.\\d){0,2}).*\\.zip$");
        try {
            Files.walk(dir.toPath())
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        String filename = path.getFileName().toString();
                        Matcher matcher = pattern.matcher(filename);
                        if (matcher.find()) {
                            zipFiles.add(new ZipFileInfo(matcher.group(1), path.toString()));
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException("查找文件失败", e);
        }

        zipFiles.sort((a, b) -> VersionUtils.compareVersion(a.getVersion(), b.getVersion()));
        return zipFiles;
    }

    private List<String> readSyncedVersionsFromS3(String metaObjectName) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(params.getBucket())
                    .key(metaObjectName)
                    .build();
            try (ResponseInputStream<GetObjectResponse> s3Object = client.getObject(getObjectRequest)) {
                VersionMetaModel model = objectMapper.readValue(s3Object, VersionMetaModel.class);
                return model.getSyncVersions();
            } catch (NoSuchKeyException e) {
//                we just ignore when meta file do not exist
            }
        } catch (IOException e) {
            throw new IllegalStateException(String.format("读取已经同步版本的元数据%s失败", metaObjectName), e);
        }

        return new ArrayList<>(0);
    }

    public void sync(List<ZipFileInfo> zipFiles, String metaObjectName) {
        for (ZipFileInfo zipFileInfo : zipFiles) {
            processZipFile(zipFileInfo);
            List<String> syncedVersions = readSyncedVersionsFromS3(metaObjectName);
            updateSyncedVersions(syncedVersions, zipFileInfo.getVersion());
        }
    }

    /**
     * 处理zip文件
     */
    private void processZipFile(ZipFileInfo zipFile) {
        log.info("上传S3, 处理版本: {}", zipFile.getVersion());
        String dest = Paths.get(SystemUtils.getJavaIoTmpDir().getAbsolutePath(), "ddp_unzip", RandomUtil.randomNumbers(12)).toString();
        try {
            ZipUtils.unzip(zipFile.getFilePath(), dest);

            Path root = Paths.get(dest);
            Files.walk(root)
                    .filter(Files::isRegularFile)
                    .forEach(file -> {
                        try {
                            String key = root.relativize(file).toString().replace("\\", "/");
                            uploadFileToS3(key, file);
                        } catch (IOException e) {
                            throw new IllegalStateException("IO异常，" + e.getMessage(), e);
                        }
                    });

            log.info("上传S3, 处理版本: {}", zipFile.getVersion());
        } catch (IOException e) {
            log.error("上传S3, 处理版本: {}", zipFile.getVersion(), e);
            throw new IllegalStateException("处理zip文件失败: " + zipFile.getVersion() + "," + e.getMessage(), e);
        } finally {
            FileUtil.del(dest);
        }
    }


    /**
     * 上传单个文件到S3
     */
    private void uploadFileToS3(String objectName, Path file) throws IOException {
        if (params.isOverride()) {
            try {
                HeadObjectRequest headRequest = HeadObjectRequest.builder()
                        .bucket(params.getBucket())
                        .key(objectName)
                        .build();
                client.headObject(headRequest);
                log.info("{}已经存在，跳过", objectName);
                return;
            } catch (NoSuchKeyException e) {
                // 文件不存在，继续上传
            }
        }

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(params.getBucket())
                .key(objectName)
                .build();
        client.putObject(putObjectRequest, RequestBody.fromFile(file));
    }

    /**
     * 更新S3中的同步记录
     */
    private void updateSyncedVersions(List<String> addedVersions, String toUpdateVersion) {
        try {
            VersionMetaModel model = new VersionMetaModel();
            List<String> versions = new ArrayList<>(addedVersions);
            versions.add(toUpdateVersion);
            model.setSyncVersions(versions);
            String jsonContent = objectMapper.writeValueAsString(model);
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(params.getBucket())
                    .key(params.getMetaObjectName())
                    .contentType("application/json")
                    .build();
            client.putObject(putObjectRequest, RequestBody.fromString(jsonContent));
            log.info("更新版本记录元数据成功");
        } catch (IOException e) {
            throw new RuntimeException("更新同步记录失败", e);
        }
    }

}