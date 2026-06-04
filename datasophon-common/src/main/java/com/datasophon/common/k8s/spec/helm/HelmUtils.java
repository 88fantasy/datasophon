package com.datasophon.common.k8s.spec.helm;

import com.datasophon.common.utils.TarUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

/**
 * @author zhanghuangbin
 */
public class HelmUtils {
    
    public static String unzip(File file) throws IOException {
        return TarUtils.decompressToTemp(file.getAbsolutePath());
    }
    
    public static File getValueFile(String chartDir) {
        File dir = Paths.get(chartDir).toFile();
        File[] files = dir.listFiles();
        if (files == null || files.length > 1) {
            throw new IllegalStateException(String.format("文件夹%s不是一个合法helm chart项目", chartDir));
        }
        File file = files[0];
        return Paths.get(file.getAbsolutePath(), "values.yaml").toFile();
    }
    
    /**
     * Helm 的 release name 必须遵循 Kubernetes DNS 子域名命名规范
     * @param serviceName
     * @return
     */
    public static String createReleaseName(String serviceName) {
        return serviceName.toLowerCase();
    }
}
