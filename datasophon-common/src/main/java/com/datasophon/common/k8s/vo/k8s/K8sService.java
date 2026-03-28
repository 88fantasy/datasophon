package com.datasophon.common.k8s.vo.k8s;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * K8s Service 资源
 */
@Data
public class K8sService {
    private String apiVersion;
    private String kind;
    private Metadata metadata;
    private ServiceSpec spec;
    private ServiceStatus status;

    @Data
    public static class Metadata {
        private String name;
        private String namespace;
        private Map<String, String> labels;
        private Map<String, String> annotations;
        private String creationTimestamp;
        private String resourceVersion;
        private String uid;
    }

    @Data
    public static class ServiceSpec {
        private String type;
        private String clusterIP;
        @JsonProperty("clusterIPs")
        private List<String> clusterIPs;
        private List<String> externalIPs;
        private List<ServicePort> ports;
        private Map<String, String> selector;
        private String sessionAffinity;
        private String sessionAffinityConfig;
        @JsonProperty("ipFamilies")
        private List<String> ipFamilies;
        @JsonProperty("ipFamilyPolicy")
        private String ipFamilyPolicy;
        @JsonProperty("externalTrafficPolicy")
        private String externalTrafficPolicy;
        @JsonProperty("internalTrafficPolicy")
        private String internalTrafficPolicy;
        private String loadBalancerClass;
        private String loadBalancerIP;
        private String loadBalancerSourceRanges;
        private List<LoadBalancerSourceRange> loadBalancerSourceRangesList;
        private Boolean publishNotReadyAddresses;
    }

    /**
     *  {
     *         "appProtocol": "http",
     *             "name": "http",
     *             "nodePort": 32379,
     *             "port": 80,
     *             "protocol": "TCP",
     *             "targetPort": "http"
     *     },
     */
    @Data
    public static class ServicePort {
        private String name;
        private String protocol;
        private int port;
        @JsonProperty("targetPort")
        private String targetPort;
        @JsonProperty("nodePort")
        private Integer nodePort;
        @JsonProperty("appProtocol")
        private String appProtocol;
    }

    @Data
    public static class LoadBalancerSourceRange {
        private String cidr;
    }

    @Data
    public static class ServiceStatus {
        private LoadBalancerStatus loadBalancer;
    }

    @Data
    public static class LoadBalancerStatus {
        private List<LoadBalancerIngress> ingress;
    }

    @Data
    public static class LoadBalancerIngress {
        private String ip;
        private String hostname;
        private List<String> ports;
    }
}
