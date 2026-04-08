package com.datasophon.common.k8s.vo.k8s;

import lombok.Data;

import java.util.Map;

/**
 * K8s Secret 资源
 */
@Data
public class K8sSecret {
    private String apiVersion;
    private String kind;
    private Metadata metadata;
    private String type;

    @Data
    public static class Metadata {
        private String name;
        private String namespace;
        private Map<String, String> labels;
        private Map<String, String> annotations;
        private String creationTimestamp;
    }


}
