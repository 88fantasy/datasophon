package com.datasophon.common.k8s.vo.k8s;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * K8s Ingress 资源 (networking.k8s.io/v1)
 */
@Data
public class K8sIngress {
    private String apiVersion;
    private String kind;
    private Metadata metadata;
    private IngressSpec spec;
    private IngressStatus status;

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
    }

    @Data
    public static class IngressSpec {
        @JsonProperty("ingressClassName")
        private String ingressClassName;
        @JsonProperty("rules")
        private List<IngressRule> rules;
        @JsonProperty("tls")
        private List<TLS> tls;
        @JsonProperty("defaultBackend")
        private IngressBackend defaultBackend;
    }

    @Data
    public static class IngressRule {
        @JsonProperty("host")
        private String host;
        @JsonProperty("http")
        private HTTP http;
    }

    @Data
    public static class HTTP {
        @JsonProperty("paths")
        private List<HTTPPath> paths;
    }

    @Data
    public static class HTTPPath {
        @JsonProperty("path")
        private String path;
        @JsonProperty("pathType")
        private String pathType;
        @JsonProperty("backend")
        private IngressBackend backend;
    }

    @Data
    public static class TLS {
        @JsonProperty("hosts")
        private List<String> hosts;
        @JsonProperty("secretName")
        private String secretName;
    }

    @Data
    public static class IngressBackend {
        @JsonProperty("service")
        private Service service;
        @JsonProperty("resource")
        private TypedLocalObjectReference resource;
    }

    @Data
    public static class Service {
        @JsonProperty("name")
        private String name;
        @JsonProperty("port")
        private ServicePort port;
    }

    @Data
    public static class ServicePort {
        @JsonProperty("number")
        private Integer number;
        @JsonProperty("name")
        private String name;
    }

    @Data
    public static class TypedLocalObjectReference {
        @JsonProperty("apiGroup")
        private String apiGroup;
        @JsonProperty("kind")
        private String kind;
        @JsonProperty("name")
        private String name;
    }

    @Data
    public static class IngressStatus {
        @JsonProperty("loadBalancer")
        private LoadBalancerStatus loadBalancer;
    }

    @Data
    public static class LoadBalancerStatus {
        @JsonProperty("ingress")
        private List<LoadBalancerIngress> ingress;
    }

    @Data
    public static class LoadBalancerIngress {
        @JsonProperty("ip")
        private String ip;
        @JsonProperty("hostname")
        private String hostname;
        @JsonProperty("ports")
        private List<PortStatus> ports;
    }

    @Data
    public static class PortStatus {
        @JsonProperty("port")
        private Integer port;
        @JsonProperty("protocol")
        private String protocol;
        @JsonProperty("error")
        private String error;
    }
}
