package com.datasophon.worker.utils;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.StringUtils;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import freemarker.cache.TemplateLoader;

/**
 * @author zhanghuangbin
 */
public class RemoteTemplateLoader implements TemplateLoader {

    private final String baseUrl;

    private final String frameCode;

    private final String serviceName;

    public RemoteTemplateLoader(String baseUrl) {
        this(baseUrl, null, null);
    }

    /**
     * @param frameCode   所属框架代码（如 datacluster-physical），用于 Master 按服务坐标解析 meta 存储中的模板路径，可为空
     * @param serviceName 服务名，与 frameCode 配合使用，可为空
     */
    public RemoteTemplateLoader(String baseUrl, String frameCode, String serviceName) {
        this.baseUrl = baseUrl;
        this.frameCode = frameCode;
        this.serviceName = serviceName;
    }

    @Override
    public Object findTemplateSource(String name) throws FileNotFoundException {
        StringBuilder downloadUrl = new StringBuilder(
                String.format("%s/ddh/api/service/install/downloadTemplate?templateName=%s", baseUrl, name));
        if (StringUtils.isNotBlank(frameCode)) {
            downloadUrl.append("&frameCode=").append(URLEncoder.encode(frameCode, StandardCharsets.UTF_8));
        }
        if (StringUtils.isNotBlank(serviceName)) {
            downloadUrl.append("&serviceName=").append(URLEncoder.encode(serviceName, StandardCharsets.UTF_8));
        }
        try (HttpResponse resp = HttpUtil.createGet(downloadUrl.toString()).execute()) {
            if (resp.getStatus() < 200 || resp.getStatus() >= 300) {
                return null;
            }
            byte[] content = resp.bodyBytes();
            return content.length == 0 ? null : new ByteArrayInputStream(content);
        }
    }

    @Override
    public long getLastModified(Object templateSource) {
        return -1;
    }

    @Override
    public Reader getReader(Object templateSource, String encoding) {
        return new InputStreamReader((InputStream) templateSource, Charset.forName(encoding));
    }

    @Override
    public void closeTemplateSource(Object templateSource) throws IOException {
        if (templateSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) templateSource).close();
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(e.getMessage(), e);
            }
        }
    }

}
