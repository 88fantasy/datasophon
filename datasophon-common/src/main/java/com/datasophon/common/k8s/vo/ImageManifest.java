package com.datasophon.common.k8s.vo;

import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class ImageManifest {

    private String image;

    private String version;

    private String os;

    private String arch;

    public String getPlatform() {
        return os + "/" + arch;
    }

    public String getFullTag() {
        return image + ":" + version;
    }

}
