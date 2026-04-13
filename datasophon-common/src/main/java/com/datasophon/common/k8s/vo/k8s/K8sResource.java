package com.datasophon.common.k8s.vo.k8s;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

/**
 * @author zhanghuangbin
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class K8sResource {

    private String apiVersion;
    private String kind;
    private Metadata metadata;


    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Metadata {
        private String name;
        private String namespace;
        private Map<String, String> labels;
        private Map<String, String> annotations;
        private String creationTimestamp;
    }
}
