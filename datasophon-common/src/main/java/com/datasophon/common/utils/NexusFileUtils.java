package com.datasophon.common.utils;

import com.datasophon.common.Constants;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.enums.RepositoriesType;
import com.datasophon.common.utils.nexus.NexusFacade;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import lombok.Data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.hutool.core.io.FileUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;

/**
 * @deprecated
 * @see NexusFacade
 */
@Deprecated
public class NexusFileUtils {
    
    private static final Logger log = LoggerFactory.getLogger(NexusFileUtils.class);
    
    /**
     * 下载文件
     * @deprecated
     * @see #downStream(String, OutputStream)
     * @param url:     http://ip:port/repository/raw/linux/x86_64/centos7/tree-1.6.0-10.el7.x86_64.rpm
     * @param username
     * @param password
     */
    @Deprecated
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
    
    public static void downStream(String url, OutputStream out) throws IOException {
        NexusFacade.getCommonClient().download(url, out);
    }
    
    public static String downloadAsString(String url) throws IOException {
        return NexusFacade.getCommonClient().downloadAsString(url);
    }
    
    public static String getNexusRawObjectUrl(String objectName) {
        return NexusFacade.getRawRepoClient().getNexusRawObjectUrl(objectName);
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
                    String osArmPath = repoFile.getAbsolutePath() + Constants.SLASH + "os" + Constants.SLASH + ArchType.AARCH64.getArch();
                    if (FileUtil.exist(osArmPath)) {
                        for (File file : files) {
                            repositoryUploadFile(baseUrl, repositoriesType, null, null, file, username, password, uploadSucess, uploadFails, isSuccessDelete);
                        }
                    }
                    String osX86Path = repoFile.getAbsolutePath() + Constants.SLASH + "os" + Constants.SLASH + ArchType.X86_64.getArch();
                    if (FileUtil.exist(osX86Path)) {
                        for (File file : files) {
                            repositoryUploadFile(baseUrl, repositoriesType, null, null, file, username, password, uploadSucess, uploadFails, isSuccessDelete);
                        }
                    }
                    break;
                case DOCKER:
                    // 单独命令推送
                    break;
                case HELM:
                    String helmPath = repoFile.getAbsolutePath();
                    File[] helmFiles = FileUtil.ls(helmPath);
                    for (File helmFile : helmFiles) {
                        repositoryUploadFile(baseUrl, repositoriesType, null, null, helmFile, username, password, uploadSucess, uploadFails, isSuccessDelete);
                    }
                    break;
                default:
                    log.info("不支持:{},跳过", repositoriesType.getDesc());
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
                .setConnectTimeout(30000) // 连接超时 30s
                .setSocketTimeout(600000) // 上传超时 10分钟
                .build();
        
        try (
                CloseableHttpClient httpClient = HttpClients.custom()
                        .setDefaultRequestConfig(config)
                        .build()) {
            
            HttpPost post = new HttpPost(url);
            prepareAuth(post);
            
            // 构建 multipart/form-data（流式，不加载到内存）
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            FileBody fileBody = new FileBody(file, ContentType.DEFAULT_BINARY);
            
            // 根据仓库类型添加额外参数
            switch (repository) {
                case YUM:
                    builder.addPart("asset0", fileBody);
                    builder.addTextBody("asset0.filename", file.getName(), ContentType.TEXT_PLAIN);
                    builder.addTextBody("directory", String.format("%s/%s", archType.getArch(), os.getDesc()), ContentType.TEXT_PLAIN);
                    break;
                case RAW:
                    builder.addPart("asset0", fileBody);
                    builder.addTextBody("asset0.filename", file.getName(), ContentType.TEXT_PLAIN);
                    builder.addTextBody("directory", "/packages", ContentType.TEXT_PLAIN);
                    break;
                case APT:
                    // APT包上传
                    builder.addTextBody("asset0.filename", file.getName(), ContentType.TEXT_PLAIN);
                    builder.addPart("asset0", new FileBody(file, ContentType.APPLICATION_OCTET_STREAM));
                    break;
                case DOCKER:
                    // Docker镜像上传
                    break;
                case HELM:
                    // Helm Chart上传
                    url = String.format("%s/repository/%s/api/charts", baseUrl, repository.getDesc());
                    builder.addBinaryBody("chart", file, ContentType.APPLICATION_OCTET_STREAM, file.getName());
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
                    if (file.getAbsolutePath().contains(Constants.PACKAGES_NAME) && isSuccessDelete) {
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
        com.datasophon.common.utils.nexus.vo.ExecResult result = NexusFacade.getRawRepoClient().uploadFileToRawRepo(path, file);
        return new ExecResult(result.isSuccess(), result.getMessage());
    }
    
    public static ExecResult uploadFileToRawRepo(String path, String fileName, String content) throws IOException {
        com.datasophon.common.utils.nexus.vo.ExecResult result = NexusFacade.getRawRepoClient().uploadFileToRawRepo(path, fileName, content);
        return new ExecResult(result.isSuccess(), result.getMessage());
    }
    
    public static String getAssertMd5FromRawRepo(String relativePathFromRawRepo) {
        return NexusFacade.getRawRepoClient().getAssertMd5FromRawRepo(relativePathFromRawRepo);
    }
    
    public static void removeFileFromRawRepo(String relativePathFromRawRepo) {
        NexusFacade.getRawRepoClient().removeFileFromRawRepo(relativePathFromRawRepo);
    }
    
    public static void removeFolderFromRawRepo(String folder) {
        NexusFacade.getRawRepoClient().removeFolderFromRawRepo(folder);
        
    }
    
    private static void prepareAuth(HttpEntityEnclosingRequestBase req) {
        String auth = Base64.getEncoder().encodeToString((Constants.NEXUS_USERNAME + ":" + Constants.NEXUS_PASSWORD).getBytes(StandardCharsets.UTF_8));
        req.setHeader("Authorization", "Basic " + auth);
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
    
}
