package com.datasophon.common.k8s.vo.k8s;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * K8s Event 资源
 * 对应 kubectl events --for=deployment/foo -o json 返回的 EventList 中的单个 Event 项
 */
@Data
public class K8sEvent {
    private String apiVersion;
    private String kind;
    private Metadata metadata;
    private InvolvedObject involvedObject;
    private String reason;
    private String message;
    private EventSource source;
    private String firstTimestamp;
    private String lastTimestamp;
    private Integer count;
    private String type;
    private String eventTime;
    private String reportingComponent;
    private String reportingInstance;

    @Data
    public static class Metadata {
        private String name;
        private String namespace;
        private String uid;
        private String resourceVersion;
        private String creationTimestamp;
    }

    @Data
    public static class InvolvedObject {
        @JsonProperty("kind")
        private String kind;
        @JsonProperty("name")
        private String name;
        @JsonProperty("namespace")
        private String namespace;
        @JsonProperty("apiVersion")
        private String apiVersion;
        @JsonProperty("uid")
        private String uid;
        @JsonProperty("resourceVersion")
        private String resourceVersion;
        @JsonProperty("fieldPath")
        private String fieldPath;
    }

    @Data
    public static class EventSource {
        @JsonProperty("component")
        private String component;
        @JsonProperty("host")
        private String host;
    }
}
