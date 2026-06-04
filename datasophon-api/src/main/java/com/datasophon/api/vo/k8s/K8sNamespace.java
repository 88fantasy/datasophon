package com.datasophon.api.vo.k8s;

import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class K8sNamespace {
    
    private String name;
    
    private String status;
}
