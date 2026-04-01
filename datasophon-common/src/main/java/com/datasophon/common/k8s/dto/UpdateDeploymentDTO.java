package com.datasophon.common.k8s.dto;

import lombok.Data;

import java.util.List;

/**
 * @author zhanghuangbin
 */
@Data
public class UpdateDeploymentDTO {

    private String namespace;

    private String deployment;


    private List<Image> images;

    @Data
    public static class Image {

        private String containerName;

        private String repository;

        private String tag;
    }
}
