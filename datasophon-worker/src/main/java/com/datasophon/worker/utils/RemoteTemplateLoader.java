package com.datasophon.worker.utils;

import cn.hutool.http.HttpUtil;
import freemarker.cache.TemplateLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.Charset;

/**
 * @author zhanghuangbin
 */
public class RemoteTemplateLoader implements TemplateLoader {


    private final String baseUrl;

    public RemoteTemplateLoader(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public Object findTemplateSource(String name) {
        String downloadUrl = String.format("%s/ddh/api/service/install/downloadTemplate?templateName=%s", baseUrl, name);
        return HttpUtil.createGet(downloadUrl).execute().bodyStream();
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
