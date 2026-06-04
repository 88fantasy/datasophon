package com.datasophon.common.utils.nexus.client;

import com.datasophon.common.Constants;
import com.datasophon.common.utils.nexus.vo.Component;
import com.datasophon.common.utils.nexus.vo.ExecResult;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.VersionUtil;

/**
 * @author zhanghuangbin
 */
@Slf4j
public class HelmRepoClient extends CommonNexusClient {
    
    private final String repo;
    
    public HelmRepoClient(String repo) {
        this.repo = repo;
    }
    
    public ExecResult uploadChartToHelmRepo(File file) throws IOException {
        String url = String.format("%s/repository/%s/%s", uri.getUri(), repo, file.getName());
        try (CloseableHttpClient httpClient = newLongTimeClient()) {
            HttpPut request = new HttpPut(url);
            prepareAuth(request);
            
            FileEntity fileEntity = new FileEntity(file, ContentType.APPLICATION_OCTET_STREAM);
            request.setEntity(fileEntity);
            log.info("开始上传 {} 到 {}", file.getName(), url);
            
            try (CloseableHttpResponse response = httpClient.execute(request)) {
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
    
    public Component getNewestComponent(String folder) throws IOException {
        String encodedFolder = encodePath(folder);
        String nameParam = StrUtil.EMPTY.equals(encodedFolder) ? "*" : encodedFolder;
        String baseUrl = String.format("http://%s:%s/service/rest/v1/search?repository=%s&name=%s",
                Constants.NEXUS_IP, Constants.NEXUS_PORT, repo, nameParam);
        List<Component> components = doListMatchedItem(baseUrl);
        if (components.isEmpty()) {
            return null;
        }
        
        Component component = components.get(0);
        for (Component comp : components) {
            if (VersionUtil.isGreaterThan(comp.getVersion(), component.getVersion())) {
                component = comp;
            }
        }
        return component;
    }
    
}
