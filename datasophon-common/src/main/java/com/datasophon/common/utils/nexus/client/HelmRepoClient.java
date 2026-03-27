package com.datasophon.common.utils.nexus.client;

import com.datasophon.common.utils.nexus.vo.ExecResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * @author zhanghuangbin
 */
@Slf4j
public class HelmRepoClient extends CommonNexusClient {


    private String repo;

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

}
