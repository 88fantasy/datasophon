package com.datasophon.common.k8s.vo.docker;

import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class LoadImageResult {

    private String oldImage;

    private String oldTag;

    private String os;

    private String arch;

    private String newImage;

    private String newTag;

    public String getPlatform() {
        return os + "/" + arch;
    }

    public String getOldQualifierImage() {
        return oldImage + ":" + oldTag;
    }


    public String getNewQualifierImage() {
        return newImage + ":" + newTag;
    }
}
