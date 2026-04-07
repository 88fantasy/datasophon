package com.datasophon.common.k8s.spec.docker;

import cn.hutool.core.util.StrUtil;

/**
 * @author zhanghuangbin
 */
public class DockerTagUtils {

    public static String DEFAULT_ORG = "bigdata";

    public static String normalTag(String repository, String tag) {
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


    public static String normalVersion(String version, String os, String arch) {
        if (StrUtil.isBlank(version) || "latest".equalsIgnoreCase(version)) {
            return os + "_" + arch + "-latest";
        }

        String suffix = os + "_" + arch;
        if (version.endsWith(suffix)) {
            return version;
        } else {
            return version + "-" + suffix;
        }
    }
}
