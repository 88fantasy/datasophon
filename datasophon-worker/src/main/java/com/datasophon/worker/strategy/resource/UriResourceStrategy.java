package com.datasophon.worker.strategy.resource;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.datasophon.common.Constants;
import com.datasophon.common.storage.DownloadResult;
import com.datasophon.common.storage.PackageStorage;
import com.datasophon.common.storage.PackageStorageUtils;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PathUtils;
import com.datasophon.common.utils.PlaceholderUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * @author zhanghuangbin
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class UriResourceStrategy extends ResourceStrategy {

    private String uri;

    private String to;

    @Override
    public String type() {
        return "uriResource";
    }

    @Override
    public ExecResult exec() {
        InputStream in = null;
        try {
            URI parseUrl = parseResource(uri);
            in = fetch(parseUrl);
            String targetPath = to.startsWith("/") ? to : PathUtils.join(basePath, to).toString();
            FileUtil.writeFromStream(in, targetPath);
            return ExecResult.success();
        } catch (Exception e) {
            return ExecResult.error(String.format("下载nexus资源包%s失败, 原因%s", uri, e.getMessage()));
        } finally {
            IoUtil.close(in);
        }
    }

    private InputStream fetch(URI uri) throws IOException {
        if ("nexus".equalsIgnoreCase(uri.getScheme())) {
           return doFetchFromNexus(uri);
        } else if ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) {
            return doFetchFromHttp(uri);
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return doFetchFromFile(uri);
        }
        throw new UnsupportedOperationException(String.format("不支持uri协议：%s", uri.getScheme()));
    }


    private InputStream doFetchFromNexus(URI uri) throws IOException {
        PackageStorage storage = PackageStorageUtils.getStorage();
        DownloadResult result = storage.downloadPackageToLocal(uri.getPath());
        return Files.newInputStream(Paths.get(result.getTarget()));
    }

    private InputStream doFetchFromHttp(URI uri) throws IOException {
        boolean isWhiteList = uri.getHost().equalsIgnoreCase(getVariables().get("${ROOT.VosManager.__hostIp__}"))
                              && (uri.getPort() + "").equalsIgnoreCase(getVariables().get("${ROOT.VosManager.__port__}"));
        if (!isWhiteList) {
            throw new IllegalArgumentException(String.format("资源%s的主机和端口不在系统白名单内", uri));
        }

//        FIXME hutool可能会内存溢出
        HttpResponse response = HttpRequest.get(String.valueOf(uri.toURL())).execute();
        if (response.getStatus() == 404) {
            throw new FileNotFoundException(String.format("url: %s not found", uri));
        }
        if (response.getStatus() == 401) {
            throw new IllegalArgumentException("require an auth, but fail");
        }
        if (response.getStatus() == 200) {
            return new WrapperInputStream(response);
        }
        throw new IllegalStateException(String.format("download fail, response status is %s, message is %s", response.getStatus(), response.body()));
    }

    private InputStream doFetchFromFile(URI uri) throws IOException {
        String path = uri.getPath();
        String targetPath = path.startsWith("/") ? path : PathUtils.join(basePath, path).toString();
        return Files.newInputStream(Paths.get(targetPath));
    }

    private URI parseResource(String from) {
        String fromPath = PlaceholderUtils.replacePlaceholders(from, getVariables(), Constants.REGEX_VARIABLE);
        if (!fromPath.matches("^[a-zA-Z0-9]+://")) {
            fromPath = "nexus://" + fromPath;
        }
        try {
            return new URI(fromPath);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(String.format("资源uri：%s定义有误,不是合法的uri", from));
        }
    }


    private static class WrapperInputStream extends InputStream {

        private HttpResponse response;

        private InputStream in;

        public WrapperInputStream(HttpResponse response) {
            this.response = response;
            this.in = response.bodyStream();
        }

        @Override
        public int read() throws IOException {
            return in.read();
        }

        @Override
        public void close() {
           IoUtil.close(in);
           in = null;
           response.close();
           response = null;
        }
    }

}
