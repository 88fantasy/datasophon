package com.datasophon.common.utils.nexus.client;

import com.datasophon.common.enums.RepositoriesType;
import com.datasophon.common.utils.nexus.dto.AssertQueryDTO;
import com.datasophon.common.utils.nexus.vo.Assert;
import com.datasophon.common.utils.nexus.vo.ExecResult;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import lombok.extern.slf4j.Slf4j;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;

/**
 * @author zhanghuangbin
 */
@Slf4j
public class RawRepoClient extends CommonNexusClient {
    
    public String repo;
    
    public RawRepoClient(String repo) {
        this.repo = repo;
    }
    
    public ExecResult uploadFileToRawRepo(String path, File file) throws IOException {
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
        
        Assert nexusAssert = getAssert(repo, AssertQueryDTO.builder().group(path).name(String.format("%s/%s", path, file.getName())).build());
        if (nexusAssert != null) {
            String md5 = SecureUtil.md5(file);
            if (md5.equals(nexusAssert.getMd5())) {
                log.info("file {} is already exist and content is not changed", file.getAbsolutePath());
                return ExecResult.success("already exists, we do not need to upload");
            } else {
                log.warn("file {} exists, but content change, we delete it", file.getAbsolutePath());
                deleteAssert(nexusAssert.getId());
            }
        }
        
        String url = String.format("%s/service/rest/internal/ui/upload/%s", uri.getUri(), repo);
        
        try (CloseableHttpClient httpClient = newLongTimeClient()) {
            
            HttpPost post = new HttpPost(url);
            prepareAuth(post);
            
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
    
    public ExecResult uploadFileToRawRepo(String path, String fileName, String content) throws IOException {
        path = StrUtil.isBlank(path) ? "/" : path;
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (!"/".equals(path) && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        
        Assert nexusAssert = getAssert(repo, AssertQueryDTO.builder().group(path).name(String.format("%s/%s", path, fileName)).build());
        if (nexusAssert != null) {
            deleteAssert(nexusAssert.getId());
        }
        
        String url = String.format("%s/service/rest/internal/ui/upload/%s", uri.getUri(), RepositoriesType.RAW.getDesc());
        try (CloseableHttpClient httpClient = newLongTimeClient()) {
            HttpPost post = new HttpPost(url);
            prepareAuth(post);
            
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            StringBody contentBody = new StringBody(content, ContentType.create("text/plain", StandardCharsets.UTF_8));
            builder.addPart("asset0", contentBody);
            
            // 根据仓库类型添加额外参数
            builder.addTextBody("asset0.filename", fileName, ContentType.TEXT_PLAIN);
            builder.addTextBody("directory", path, ContentType.TEXT_PLAIN);
            
            HttpEntity entity = builder.build();
            post.setEntity(entity);
            
            log.info("开始上传 {} 到 {}", fileName, url);
            
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
    
    public String getAssertMd5FromRawRepo(String relativePathFromRawRepo) {
        String group = getGroupOfRaw(relativePathFromRawRepo);
        try {
            Assert assertItem = getAssert(RepositoriesType.RAW.getDesc(), AssertQueryDTO.builder().group(group).name(relativePathFromRawRepo).build());
            return assertItem == null ? null : assertItem.getMd5();
        } catch (IOException e) {
            return null;
        }
    }
    
    private static String getGroupOfRaw(String relativePathFromRawRepo) {
        String group = null;
        int idx = relativePathFromRawRepo.lastIndexOf("/");
        if (idx != -1) {
            group = relativePathFromRawRepo.substring(0, idx);
        }
        if (StrUtil.isBlank(group)) {
            group = "/";
        }
        if (!group.startsWith("/")) {
            group = "/" + group;
        }
        return group;
    }
    
    public void removeFileFromRawRepo(String relativePathFromRawRepo) {
        try {
            String group = getGroupOfRaw(relativePathFromRawRepo);
            Assert assertItem = getAssert(repo, AssertQueryDTO.builder().group(group).name(relativePathFromRawRepo).build());
            if (assertItem != null) {
                log.info("remove nexus file: {}", assertItem.getDownloadUrl());
                deleteAssert(assertItem.getId());
            }
        } catch (IOException e) {
            log.warn("remove file: {} fail, {}", relativePathFromRawRepo, e.getMessage(), e);
        }
    }
    
    public void removeFolderFromRawRepo(String folder) {
        removeFolder(repo, folder);
    }
    
    public String getNexusRawObjectUrl(String objectName) {
        objectName = objectName.startsWith("/") ? objectName : "/" + objectName;
        return String.format("%s/repository/%s%s", uri.getUri(), repo, objectName);
    }
    
}
