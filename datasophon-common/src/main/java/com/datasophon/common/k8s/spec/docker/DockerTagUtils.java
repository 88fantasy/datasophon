package com.datasophon.common.k8s.spec.docker;

import cn.hutool.core.util.StrUtil;

/**
 * @author zhanghuangbin
 */
public class DockerTagUtils {
    
    public static String normalRepository(String registry, String image) {
        if (!registry.endsWith("/")) {
            registry += "/";
        }
        
        int count = 0;
        for (int i = 0; i < image.length(); i++) {
            if (image.charAt(i) == '/') {
                count++;
            }
        }
        if (count == 0) {
            return registry + image;
        } else if (count == 1) {
            return registry + image;
        } else if (count == 2) {
            String[] parts = image.split("/");
            if (image.startsWith("docker.io")) {
                if ("library".equals(parts[1])) {
                    return registry + parts[2];
                }
            }
            return registry + parts[1] + "/" + parts[2];
        } else if (count == 3) {
            // count == 3, 只有nexus私库有这个问题，当作正常值
            int i;
            int cnt = 0;
            for (i = image.length() - 1; i >= 0; i--) {
                if (image.charAt(i) == '/') {
                    cnt++;
                }
                if (cnt == count) {
                    break;
                }
            }
            String simpleTag = image.substring(i + 1);
            return registry + simpleTag;
        } else {
            throw new IllegalArgumentException(String.format("tag %s do not matched the docker specification", image));
        }
    }
    
    public static String normalTag(String version, String os, String arch) {
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
