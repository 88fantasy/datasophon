package com.datasophon.common.utils;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.datasophon.common.Constants;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.enums.RepositoriesType;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import lombok.Data;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class NexusFileUtils {

    private static final Logger log = LoggerFactory.getLogger(NexusFileUtils.class);

    /**
     * 下载文件
     *
     * @param url:     http://ip:port/repository/raw/linux/x86_64/centos7/tree-1.6.0-10.el7.x86_64.rpm
     * @param username
     * @param password
     */
    public static InputStream downStream(String url, String username, String password) throws FileNotFoundException {
        HttpResponse response = HttpRequest.get(url)
                .basicAuth(username, password)
                .execute();
        if (response.getStatus() == 404) {
            throw new FileNotFoundException(String.format("url: %s not found", url));
        }
        if (response.getStatus() == 401) {
            throw new IllegalArgumentException("nexus require an auth, but fail");
        }
        if (response.getStatus() == 200) {
            return response.bodyStream();
        }
        throw new IllegalStateException(String.format("download fail, response status is %s, message is %s", response.getStatus(), response.body()));
    }


    public static String getNexusRawObjectUrl(String objectName) {
        objectName = objectName.startsWith("/") ? objectName : "/" + objectName;
        return String.format("http://%s:%s/repository/%s%s", Constants.NEXUS_IP, Constants.NEXUS_PORT, RepositoriesType.RAW.getDesc(), objectName);
    }

    /**
     * 批量上传仓库文件:
     *
     * @param packageFullDir: /data/packages
     * @param baseUrl:        http://ip:port
     * @param username
     * @param password
     */
    public static Pair<Map<String, String>, Map<String, String>> repositoryUploadBatch(String packageFullDir, String baseUrl,
                                                                                       String username, String password, boolean isSuccessDelete) {
        File[] repoFiles = FileUtil.ls(packageFullDir);
        Map<String, String> uploadSucess = new HashMap<>();
        Map<String, String> uploadFails = new HashMap<>();

        for (File repoFile : repoFiles) {
            log.info("repoFile:{}", repoFile.getAbsolutePath());
            RepositoriesType repositoriesType = RepositoriesType.of(repoFile.getName());
            switch (repositoriesType) {
                case YUM:
                case APT:
                    File[] archTypes = FileUtil.ls(repoFile.getAbsolutePath());
                    for (File archFile : archTypes) {
                        log.info("archFile:{}", archFile.getAbsolutePath());
                        ArchType archType = ArchType.of(archFile.getName());
                        File[] osFiles = FileUtil.ls(archFile.getAbsolutePath());

                        for (File osFile : osFiles) {
                            log.info("osFile:{}", osFile.getAbsolutePath());
                            File[] files = FileUtil.ls(osFile.getAbsolutePath());
                            OsType osType = OsType.of(osFile.getName());

                            for (File file : files) {
                                repositoryUploadFile(baseUrl, repositoriesType, archType, osType, file, username, password, uploadSucess, uploadFails, isSuccessDelete);
                            }
                        }
                    }
                    break;
                case RAW:
                    String packagesPath = repoFile.getAbsolutePath() + Constants.SLASH + "packages";
                    File[] files = FileUtil.ls(packagesPath);
                    for (File file : files) {
                        repositoryUploadFile(baseUrl, repositoriesType, null, null, file, username, password, uploadSucess, uploadFails, isSuccessDelete);
                    }
                    break;
                default:
                    log.info("不支持:{},跳过", repositoriesType.getDesc());
            }

            if (repositoriesType == RepositoriesType.YUM || repositoriesType == RepositoriesType.APT) {

            }
        }
        return Pair.of(uploadSucess, uploadFails);
    }

    private static void repositoryUploadFile(String baseUrl, RepositoriesType repository, ArchType archType,
                                             OsType os, File file, String username, String password,
                                             Map<String, String> uploadSuccess, Map<String, String> uploadFails, boolean isSuccessDelete) {
        if (file.isDirectory()) {
            return;
        }

        String url = String.format("%s/service/rest/internal/ui/upload/%s", baseUrl, repository.getDesc());

        // 配置超时（根据文件大小调整）
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(30000)      // 连接超时 30s
                .setSocketTimeout(600000)      // 上传超时 10分钟
                .build();

        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(config)
                .build()) {

            HttpPost post = new HttpPost(url);

            // Basic Auth
            String auth = Base64.getEncoder().encodeToString(
                    (username + ":" + password).getBytes(StandardCharsets.UTF_8)
            );
            post.setHeader("Authorization", "Basic " + auth);

            // 构建 multipart/form-data（流式，不加载到内存）
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            FileBody fileBody = new FileBody(file, ContentType.DEFAULT_BINARY);
            builder.addPart("asset0", fileBody);

            // 根据仓库类型添加额外参数
            switch (repository) {
                case YUM:
                    builder.addTextBody("asset0.filename", file.getName(), ContentType.TEXT_PLAIN);
                    builder.addTextBody("directory", String.format("%s/%s", archType.getArch(), os.getDesc()), ContentType.TEXT_PLAIN);
                    break;
                case RAW:
                    builder.addTextBody("asset0.filename", file.getName(), ContentType.TEXT_PLAIN);
                    builder.addTextBody("directory", "/packages", ContentType.TEXT_PLAIN);
                    break;
                case APT:
                    break;
                default:
                    log.info("不支持:{},跳过", repository);
            }

            HttpEntity entity = builder.build();
            post.setEntity(entity);

            log.info("开始上传 {}", file.getAbsolutePath());

            try (CloseableHttpResponse response = httpClient.execute(post)) {
                int status = response.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                if (status == 200) {
                    log.info("上传 {} 成功, Status: {}, Response: {}, isSuccessDelete:{}", file.getAbsolutePath(), status, body, isSuccessDelete);
                    uploadSuccess.put(file.getAbsolutePath(), body);
                    if(file.getAbsolutePath().contains(Constants.PACKAGES_NAME) && isSuccessDelete) {
                        FileUtil.del(file);
                        log.info("delete {}", file.getAbsolutePath());
                    }
                } else {
                    log.error("上传 {} 失败. URL: {}, Status: {}, Response: {}",
                            file.getAbsolutePath(), url, status, body);
                    uploadFails.put(file.getAbsolutePath(), body);
                }
            }
        } catch (Exception e) {
            String msg = "上传 " + file.getAbsolutePath() + " 失败: " + e.getMessage();
            log.error(msg, e);
            uploadFails.put(file.getAbsolutePath(), e.toString());
        }
    }

    public static ExecResult uploadFileToRawRepo(String path, File file) throws IOException {
        if (!file.isFile()) {
            throw new IllegalArgumentException(String.format("file %s is not a file", file.getAbsoluteFile()));
        }
        path = StrUtil.isBlank(path) ? "/" : path;
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (!"/".equals(path) && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }

        Assert nexusAssert = getAssertFromRawRepo(path, String.format("%s/%s", path, file.getName()));
        if (nexusAssert != null) {
            String md5 = SecureUtil.md5(file);
            if (md5.equals(nexusAssert.getMd5())) {
                log.info("file {} is already exist and content is not changed", file.getAbsolutePath());
                return ExecResult.success("already exists, we do not need to upload");
            } else {
                log.warn("file {} exists, but content change, we delete it", file.getAbsolutePath());
                deleteAssertFromRawRepo(nexusAssert.getId());
            }
        }


        String url = String.format("http://%s:%s/service/rest/internal/ui/upload/%s", Constants.NEXUS_IP, Constants.NEXUS_PORT, RepositoriesType.RAW.getDesc());

        // 配置超时（根据文件大小调整）
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(30000)      // 连接超时 30s
                .setSocketTimeout(600000)      // 上传超时 10分钟
                .build();

        try (CloseableHttpClient httpClient = HttpClients.custom()
                .setDefaultRequestConfig(config)
                .build()) {

            HttpPost post = new HttpPost(url);

            // Basic Auth
            String auth = Base64.getEncoder().encodeToString((Constants.NEXUS_USERNAME + ":" + Constants.NEXUS_PASSWORD).getBytes(StandardCharsets.UTF_8));
            post.setHeader("Authorization", "Basic " + auth);

            // 构建 multipart/form-data（流式，不加载到内存）
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            FileBody fileBody = new FileBody(file, ContentType.DEFAULT_BINARY);
            builder.addPart("asset0", fileBody);

            // 根据仓库类型添加额外参数
            builder.addTextBody("asset0.filename", file.getName(), ContentType.TEXT_PLAIN);
            builder.addTextBody("directory", path, ContentType.TEXT_PLAIN);

            HttpEntity entity = builder.build();
            post.setEntity(entity);

            log.info("开始上传 {} 到 {}", file.getAbsolutePath(), url);

            try (CloseableHttpResponse response = httpClient.execute(post)) {
                int status = response.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                if (status == 200) {
                    return ExecResult.success(body);
                } else {
                    return ExecResult.fail(body);
                }
            }
        }
    }



    private static Assert getAssertFromRawRepo(String group, String name) throws IOException {
        String url = String.format("http://%s:%s/service/rest/v1/search/assets?repository=raw&format=raw&group=%s&name=%s",
                Constants.NEXUS_IP, Constants.NEXUS_PORT, group, name
        );


        try (CloseableHttpClient httpClient = HttpClients.custom().build()) {

            HttpGet get = new HttpGet(url);

            // Basic Auth
            String auth = Base64.getEncoder().encodeToString((Constants.NEXUS_USERNAME + ":" + Constants.NEXUS_PASSWORD).getBytes(StandardCharsets.UTF_8));
            get.setHeader("Authorization", "Basic " + auth);

            try (CloseableHttpResponse response = httpClient.execute(get)) {
                int status = response.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                if (status == 200) {
                    ObjectMapper mapper = getMapper();
                    AssertResponse resp = mapper.readValue(body, AssertResponse.class);
                    if (CollectionUtil.isNotEmpty(resp.getItems())) {
                        return resp.getItems().get(0);
                    }
                    return null;
                } else {
                    throw new IllegalStateException(String.format("request url: %s fail, status: %s, response %s", url, status, body));
                }
            }
        }
    }


    private static void deleteAssertFromRawRepo(String id) throws IOException {
        String url = String.format("http://%s:%s/service/rest/v1/assets/%s", Constants.NEXUS_IP, Constants.NEXUS_PORT, id);
        try (CloseableHttpClient httpClient = HttpClients.custom().build()) {
            HttpDelete deleteAction = new HttpDelete(url);
            String auth = Base64.getEncoder().encodeToString((Constants.NEXUS_USERNAME + ":" + Constants.NEXUS_PASSWORD).getBytes(StandardCharsets.UTF_8));
            deleteAction.setHeader("Authorization", "Basic " + auth);

            try (CloseableHttpResponse response = httpClient.execute(deleteAction)) {
                int status = response.getStatusLine().getStatusCode();
                boolean isSuccess = status >= 200 && status < 300;
                if (!isSuccess) {
                    String body = response.getEntity() == null ? null : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    throw new IllegalStateException(String.format("request url: %s fail, status: %s, response %s", url, status, body));
                }
            }
        }
    }
    private static ObjectMapper getMapper() {
        JsonMapper.Builder builder = JsonMapper.builder();

        builder.defaultDateFormat(new SimpleDateFormat("yyyy-MM-dd"));
        builder.defaultLocale(Locale.CHINA);
        builder.defaultTimeZone(TimeZone.getTimeZone("GMT+8"));

        builder.disable(MapperFeature.DEFAULT_VIEW_INCLUSION);
        builder.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        builder.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        builder.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);

        return builder.build();
    }

    @Data
    public static class ExecResult {

        private final boolean success;

        private final String message;


        public static ExecResult success(String message) {
            return new ExecResult(true, message);
        }


        public static ExecResult fail(String message) {
            return new ExecResult(false, message);
        }

    }

    @Data
    public static class AssertResponse {
        private List<Assert> items;
    }

    @Data
    public static class Assert {
        private String id;
        private String repository;
        private String format;
        private Checksum checksum;

        public String getMd5() {
            return checksum == null ? null : checksum.getMd5();
        }
    }

    @Data
    public static class Checksum {
        private String md5;
    }
}
