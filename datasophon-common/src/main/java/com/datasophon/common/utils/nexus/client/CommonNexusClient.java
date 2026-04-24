package com.datasophon.common.utils.nexus.client;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import com.datasophon.common.Constants;
import com.datasophon.common.model.uni.NexusUri;
import com.datasophon.common.utils.JacksonUtils;
import com.datasophon.common.utils.nexus.NexusFacade;
import com.datasophon.common.utils.nexus.dto.AssertQueryDTO;
import com.datasophon.common.utils.nexus.vo.Assert;
import com.datasophon.common.utils.nexus.vo.AssertResponse;
import com.datasophon.common.utils.nexus.vo.Component;
import com.datasophon.common.utils.nexus.vo.ComponentResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * @author zhanghuangbin
 */
@Slf4j
public class CommonNexusClient {

    protected NexusUri uri;

    public CommonNexusClient() {
        uri = NexusFacade.getNexusUri();
    }

    public void download(String url, OutputStream out) throws IOException {
        try (CloseableHttpClient client = newLongTimeClient()) {
            String realUrl = url.startsWith("http") ? url : String.format("%s%s", uri.getUri(), url);
            HttpGet req = new HttpGet(realUrl);
            prepareAuth(req);
            log.info("开始下载 {}", realUrl);
            try (CloseableHttpResponse response = client.execute(req)) {
                int status = response.getStatusLine().getStatusCode();
                boolean isSuccess = status >= 200 && status < 300;
                if (isSuccess) {
                    try (InputStream in = response.getEntity().getContent()) {
                        IoUtil.copy(in, out);
                    }
                } else {
                    EntityUtils.consume(response.getEntity());
                    if (status == 404) {
                        throw new FileNotFoundException(String.format("url: %s not found", realUrl));
                    }
                    if (status == 401) {
                        throw new IllegalArgumentException("nexus require an auth, but fail");
                    }
                    throw new IllegalStateException(String.format("download %s fail, response status is %s, message is %s", realUrl, status, response.getStatusLine().getReasonPhrase()));
                }
            }
        }
    }

    public String downloadAsString(String url) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        download(url, out);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    public List<Component> listMatchedItem(String repo, String namePattern) throws IOException {
        String encodedFolder = encodePath(namePattern);
        String nameParam = StrUtil.EMPTY.equals(encodedFolder) ? "*" : encodedFolder;
        String baseUrl = String.format("http://%s:%s/service/rest/v1/search?repository=%s&name=%s",
                Constants.NEXUS_IP, Constants.NEXUS_PORT, repo, nameParam
        );
        return doListMatchedItem(baseUrl);
    }


    public void removeFolder(String repo, String folder) {
        String baseUrl = String.format("%s/service/rest/v1/components?repository=%s", uri.getUri(), repo);
        try {
            List<Component> components = doListMatchedItem(baseUrl);
            for (Component comp : components) {
                if (comp.getName().startsWith(folder)) {
                    for (Assert asset : comp.getAssets()) {
                        log.info("remove nexus file: {}", asset.getDownloadUrl());
                        deleteAssert(asset.getId());
                    }
                }
            }
        } catch (IOException e) {
            log.warn("remove file: {} fail, {}", folder, e.getMessage(), e);
        }
    }

    /**
     * 对路径进行 URL 编码，保留斜杠分隔符
     * 例如：输入 "releases/app/v1.0" 输出 "releases/app/v1.0"（各部分编码后重组）
     */
    protected String encodePath(String path) {
        if (StrUtil.isBlank(path) || path.equals("/")) {
            return "";
        }
        String[] parts = path.split("/");
        StringBuilder encoded = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                encoded.append("/");
            }
            try {
                encoded.append(URLEncoder.encode(parts[i], StandardCharsets.UTF_8.toString()));
            } catch (Exception e) {
                encoded.append(parts[i]);
            }
        }
        return encoded.toString();
    }

    protected CloseableHttpClient newLongTimeClient() {
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(30000)      // 连接超时 30s
                .setSocketTimeout(600000)      // 上传超时 10分钟
                .build();

        return HttpClients.custom()
                .setDefaultRequestConfig(config)
                .build();
    }

    protected CloseableHttpClient newClient() {
        return HttpClients.custom().build();
    }


    protected void prepareAuth(HttpRequestBase req) {
        String auth = Base64.getEncoder().encodeToString((uri.getUser() + ":" + uri.getPassword()).getBytes(StandardCharsets.UTF_8));
        req.setHeader("Authorization", "Basic " + auth);
    }

    protected Assert getAssert(String repo, AssertQueryDTO query) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%s/service/rest/v1/search/assets?repository=%s", uri.getUri(), repo));
        if (StrUtil.isNotBlank(query.getGroup())) {
            sb.append("&group=").append(query.getGroup());
        }
        if (StrUtil.isNotBlank(query.getName())) {
            sb.append("&name=").append(query.getName());
        }
        if (StrUtil.isNotBlank(query.getFormat())) {
            sb.append("&format=").append(query.getFormat());
        }
        String url = sb.toString();

        try (CloseableHttpClient httpClient = newClient()) {
            HttpGet get = new HttpGet(url);
            prepareAuth(get);

            try (CloseableHttpResponse response = httpClient.execute(get)) {
                int status = response.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                if (status == 200) {
                    ObjectMapper mapper = JacksonUtils.getInstance();
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


    protected void deleteAssert(String assertId) throws IOException {
        String url = String.format("%s/service/rest/v1/assets/%s", uri.getUri(), assertId);
        try (CloseableHttpClient httpClient = newClient()) {
            HttpDelete req = new HttpDelete(url);
            prepareAuth(req);

            try (CloseableHttpResponse response = httpClient.execute(req)) {
                int status = response.getStatusLine().getStatusCode();
                boolean isSuccess = status >= 200 && status < 300;
                if (!isSuccess) {
                    String body = response.getEntity() == null ? null : EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    throw new IllegalStateException(String.format("request url: %s fail, status: %s, response %s", url, status, body));
                }
            }
        }
    }

    protected List<Component> doListMatchedItem(String baseUrl) throws IOException {
        List<Component> components = new ArrayList<>();
        try (CloseableHttpClient httpClient = newClient()) {
            String continuationToken = null;
            do {
                String url = baseUrl;
                if (StrUtil.isNotBlank(continuationToken)) {
                    url += "&continuationToken=" + continuationToken;
                }
                HttpGet get = new HttpGet(url);

                prepareAuth(get);

                try (CloseableHttpResponse response = httpClient.execute(get)) {
                    int status = response.getStatusLine().getStatusCode();
                    String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
                    if (status == 200) {
                        ObjectMapper mapper = JacksonUtils.getInstance();
                        ComponentResponse resp = mapper.readValue(body, ComponentResponse.class);
                        components.addAll(resp.getItems());
                        continuationToken = resp.getContinuationToken();
                    } else {
                        throw new IllegalStateException(String.format("request url: %s fail, status: %s, response %s", url, status, body));
                    }
                }
            } while (StrUtil.isNotBlank(continuationToken));
        }
        return components;
    }


}
