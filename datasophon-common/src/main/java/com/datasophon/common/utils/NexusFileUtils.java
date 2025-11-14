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
            File[] archTypes = FileUtil.ls(repoFile.getAbsolutePath());

            for (File archFile : archTypes) {
                log.info("archFile:{}", archFile.getAbsolutePath());
                ArchType  archType = ArchType.of(archFile.getName());
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
        }
        return Pair.of(uploadSucess, uploadFails);
    }

    private static void repositoryUploadFile(String baseUrl, RepositoriesType repository, ArchType archType,
                               OsType os, File file, String username, String password,
                               Map<String, String> uploadSucess, Map<String, String> uploadFails) {
        if(file.isDirectory()) {
            return;
        }
        String url = String.format("%s/service/rest/internal/ui/upload/%s", baseUrl, repository.getDesc());
        HttpRequest request = HttpRequest.post(url)
                .basicAuth(username, password)
                .form("asset0", file);
        if(repository == RepositoriesType.YUM || repository == RepositoriesType.RAW) {
            request.form("asset0.filename", file.getName())
                    .form("directory", String.format("%s/%s", archType.getArch(), os.getDesc()));
        }
        HttpResponse response;
        try {
            response = request.execute();
            if (response.getStatus() == 200) {
                log.info("上传{}成功", file.getAbsolutePath());
                uploadSucess.put(file.getAbsolutePath(), response.body());
            } else {
                log.error("上传{}失败. url:{}, response.status:{}, response.body:{}", file.getAbsolutePath(), url, response.getStatus(), response.body());
                uploadFails.put(file.getAbsolutePath(), response.body());
            }
        } catch (Exception e) {
            log.error("上传{}失败, e:{},msg:{}", file.getAbsoluteFile(), e, e.getMessage());
            uploadFails.put(file.getAbsolutePath(), e.getMessage());
        }
    }
}
