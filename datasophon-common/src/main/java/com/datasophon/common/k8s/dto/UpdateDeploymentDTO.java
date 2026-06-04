package com.datasophon.common.k8s.dto;

import java.util.List;

import lombok.Data;

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
        
        private String newImage;
        
        private String tag;
    }
}
