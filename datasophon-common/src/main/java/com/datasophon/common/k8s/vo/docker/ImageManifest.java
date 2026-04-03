package com.datasophon.common.k8s.vo.docker;

import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class ImageManifest {

    private String image;

    private String tag;

    private String os;

    private String arch;

    public String getPlatform() {
        return os + "/" + arch;
    }

    public String getQualifierImage() {
        return image + ":" + tag;
    }

}
