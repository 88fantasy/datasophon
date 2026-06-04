package com.datasophon.common.k8s.vo.k8s;

import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * K8s ConfigMap 资源
 */
@Data
public class K8sConfigMap {
    private String apiVersion;
    private String kind;
    private Metadata metadata;
    
    @Data
    public static class Metadata {
        private String name;
        private String namespace;
        private Map<String, String> labels;
        private Map<String, String> annotations;
        private String creationTimestamp;
        private String resourceVersion;
        private String uid;
        private Long generation;
        private List<OwnerReference> ownerReferences;
        private String finalizers;
        private String managedFields;
        
    }
    
    @Data
    public static class OwnerReference {
        private String apiVersion;
        private String kind;
        private String name;
        private String uid;
        private Boolean controller;
        private Boolean blockOwnerDeletion;
    }
}
