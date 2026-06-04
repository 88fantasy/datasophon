package com.datasophon.common.model.k8s;

import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class K8sArtifact {
    
    private String helm;
    
    private String yaml;
}
