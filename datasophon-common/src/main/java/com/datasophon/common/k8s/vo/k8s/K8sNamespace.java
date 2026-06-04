package com.datasophon.common.k8s.vo.k8s;

import java.util.List;
import java.util.Map;

import lombok.Data;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * K8s Namespace 资源
 */
@Data
public class K8sNamespace {
    private String apiVersion;
    private String kind;
    private Metadata metadata;
    private NamespaceSpec spec;
    private NamespaceStatus status;
    
    @Data
    public static class Metadata {
        private String name;
        private Map<String, String> labels;
        private Map<String, String> annotations;
        private String creationTimestamp;
        private String resourceVersion;
        private String uid;
        private List<OwnerReference> ownerReferences;
        private String finalizers;
        private String managedFields;
        
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
    
    @Data
    public static class NamespaceSpec {
        @JsonProperty("finalizers")
        private List<String> finalizers;
    }
    
    @Data
    public static class NamespaceStatus {
        @JsonProperty("phase")
        private String phase;
        @JsonProperty("conditions")
        private List<Condition> conditions;
        
        @Data
        public static class Condition {
            @JsonProperty("type")
            private String type;
            @JsonProperty("status")
            private String status;
            @JsonProperty("reason")
            private String reason;
            @JsonProperty("message")
            private String message;
            @JsonProperty("lastTransitionTime")
            private String lastTransitionTime;
        }
    }
}
