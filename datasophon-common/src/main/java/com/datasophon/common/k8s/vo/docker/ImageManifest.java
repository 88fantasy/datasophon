package com.datasophon.common.k8s.vo.docker;

import lombok.Data;

import java.util.List;

/**
 * @author zhanghuangbin
 */
@Data
public class ImageManifest {

    private String image;

    private String tag;

    private List<ImagePlatform> platforms;


    @Data
    public static class ImagePlatform {
        private String os;

        private String arch;

        public String getPlatform() {
            return os + "/" + arch;
        }
    }

}
