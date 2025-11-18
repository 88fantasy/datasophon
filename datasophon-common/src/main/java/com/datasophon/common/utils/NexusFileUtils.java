package com.datasophon.common.utils;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.enums.RepositoriesType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang3.tuple.Pair;
import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class NexusFileUtils {

    private static final Logger log = LoggerFactory.getLogger(NexusFileUtils.class);

    /**
     * 下载文件
     * @param url: http://ip:port/repository/raw/linux/x86_64/centos7/tree-1.6.0-10.el7.x86_64.rpm
     * @param username
     * @param password
     */
    public static InputStream downStream(String url, String username, String password) {
        HttpResponse response = HttpRequest.get(url)
                    .basicAuth(username, password)
                    .execute();
        return response.bodyStream();
    }

    /**
     * 批量上传仓库文件:
     * @param packageFullDir: /data/packages
     * @param baseUrl: http://ip:port
     * @param username
     * @param password
     */
    public static Pair<Map<String, String>, Map<String, String>> repositoryUploadBatch(String packageFullDir, String baseUrl, String username, String password) {
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
                                repositoryUploadFile(baseUrl, repositoriesType, archType, osType, file, username, password, uploadSucess, uploadFails);
                            }
                        }
                    }
                    break;
                case RAW:
                    File[] files = FileUtil.ls(repoFile.getAbsolutePath());
                    for (File file : files) {
                        repositoryUploadFile(baseUrl, repositoriesType, null, null, file, username, password, uploadSucess, uploadFails);
                    }
                    break;
                default:
                    log.info("不支持:{},跳过", repositoriesType.getDesc());
            }

            if(repositoriesType == RepositoriesType.YUM || repositoriesType == RepositoriesType.APT) {

            }
        }
        return Pair.of(uploadSucess, uploadFails);
    }

    private static void repositoryUploadFile(String baseUrl, RepositoriesType repository, ArchType archType,
                                             OsType os, File file, String username, String password,
                                             Map<String, String> uploadSuccess, Map<String, String> uploadFails) {
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
                    builder.addTextBody("directory", "/", ContentType.TEXT_PLAIN);
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
                    log.info("上传 {} 成功, Status: {}, Response: {}", file.getAbsolutePath(), status, body);
                    uploadSuccess.put(file.getAbsolutePath(), body);
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
}
