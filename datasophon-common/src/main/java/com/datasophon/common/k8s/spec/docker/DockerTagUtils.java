package com.datasophon.common.k8s.spec.docker;

import cn.hutool.core.util.StrUtil;

import java.util.Arrays;

/**
 * @author zhanghuangbin
 */
public class DockerTagUtils {

    public static String DEFAULT_ORG = "bigdata";

    public static String normalTag(String repository, String tag) {
        int protocolIdx = repository.indexOf("://");
        if (protocolIdx != -1) {
            repository = repository.substring(protocolIdx + 3);
        }
        if (!repository.endsWith("/")) {
            repository += "/";
        }

        int count = 0;
        for (int i = 0; i < tag.length(); i++) {
            if (tag.charAt(i) == '/') {
                count++;
            }
        }
        if (count == 0) {
            return repository + DEFAULT_ORG + tag;
        } else if (count == 1) {
            return repository + tag;
        } else if (count == 2) {
            String[] parts = tag.split("/");
            if (tag.startsWith("docker.io")) {
                if ("library".equals(parts[1])) {
                    return repository + DEFAULT_ORG + "/" + parts[2];
                }
            }
            return repository + parts[1] + "/" + parts[2];
        } else if (count == 3) {
//            count == 3, 只有nexus私库有这个问题，当作正常值
            int i;
            int cnt = 0;
            for (i = tag.length() - 1; i >= 0; i--) {
                if (tag.charAt(i) == '/') {
                    cnt++;
                }
                if (cnt == count) {
                    break;
                }
            }
            String simpleTag = tag.substring(i + 1);
            return repository + simpleTag;
        } else {
            throw new IllegalArgumentException(String.format("tag %s do not matched the docker specification", tag));
        }
    }


    public static String normalVersion(String version, String arch) {
        if (StrUtil.isBlank(version) || "latest".equalsIgnoreCase(version)) {
            version = "latest";
        }

        // 如果标签已经包含架构后缀，则不重复添加
        if (version.endsWith("-amd") || version.endsWith("-arm") || version.endsWith("-amd64") || version.endsWith("-arm64")) {
            return version;
        }
        for (String suffix : Arrays.asList("amd", "arm", "amd64", "amd64")) {
            if (version.endsWith("-" + suffix) || version.endsWith("_" + suffix)) {
                return version;
            }
        }
        if (Arrays.asList("amd", "arm", "amd64", "x86_64", "arm64", "aarch64").contains(arch)) {
            return version + "-" + arch;
        }

        // 未知架构或不匹配时返回原标签
        return version;
    }
}
