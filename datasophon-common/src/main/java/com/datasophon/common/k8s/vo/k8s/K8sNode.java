package com.datasophon.common.k8s.vo.k8s;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * K8s Node 资源
 */
@Data
public class K8sNode {
    private String apiVersion;
    private String kind;
    private Metadata metadata;
    private NodeSpec spec;
    private NodeStatus status;

    @Data
    public static class Metadata {
        private String name;
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

    @Data
    public static class NodeSpec {
        @JsonProperty("providerID")
        private String providerID;
        @JsonProperty("unschedulable")
        private Boolean unschedulable;
        @JsonProperty("taints")
        private List<Taint> taints;
        @JsonProperty("configSource")
        private NodeConfigSource configSource;
        @JsonProperty("podCIDR")
        private String podCIDR;
        @JsonProperty("podCIDRs")
        private List<String> podCIDRs;
        @JsonProperty("externalID")
        private String externalID;
    }

    @Data
    public static class Taint {
        @JsonProperty("key")
        private String key;
        @JsonProperty("value")
        private String value;
        @JsonProperty("effect")
        private String effect;
        @JsonProperty("timeAdded")
        private String timeAdded;
    }

    @Data
    public static class NodeConfigSource {
        private ConfigMapRef configMap;
    }

    @Data
    public static class ConfigMapRef {
        private String namespace;
        private String name;
        private String uid;
        private String resourceVersion;
    }

    @Data
    public static class NodeStatus {
        @JsonProperty("capacity")
        private Map<String, String> capacity;
        @JsonProperty("allocatable")
        private Map<String, String> allocatable;
        @JsonProperty("phase")
        private String phase;
        @JsonProperty("conditions")
        private List<NodeCondition> conditions;
        @JsonProperty("addresses")
        private List<NodeAddress> addresses;
        @JsonProperty("nodeInfo")
        private NodeInfo nodeInfo;
        @JsonProperty("images")
        private List<ContainerImage> images;
        @JsonProperty("volumesInUse")
        private List<String> volumesInUse;
        @JsonProperty("volumesAttached")
        private List<AttachedVolume> volumesAttached;
        @JsonProperty("config")
        private NodeConfigStatus config;
        @JsonProperty("resources")
        private ResourceStatus resources;
    }

    @Data
    public static class NodeCondition {
        @JsonProperty("type")
        private String type;
        @JsonProperty("status")
        private String status;
        @JsonProperty("reason")
        private String reason;
        @JsonProperty("message")
        private String message;
        @JsonProperty("lastHeartbeatTime")
        private String lastHeartbeatTime;
        @JsonProperty("lastTransitionTime")
        private String lastTransitionTime;
    }

    @Data
    public static class NodeAddress {
        @JsonProperty("type")
        private String type;
        @JsonProperty("address")
        private String address;
    }

    @Data
    public static class NodeInfo {
        @JsonProperty("machineID")
        private String machineID;
        @JsonProperty("systemUUID")
        private String systemUUID;
        @JsonProperty("bootID")
        private String bootID;
        @JsonProperty("kernelVersion")
        private String kernelVersion;
        @JsonProperty("osImage")
        private String osImage;
        @JsonProperty("containerRuntimeVersion")
        private String containerRuntimeVersion;
        @JsonProperty("kubeletVersion")
        private String kubeletVersion;
        @JsonProperty("kubeProxyVersion")
        private String kubeProxyVersion;
        @JsonProperty("operatingSystem")
        private String operatingSystem;
        @JsonProperty("architecture")
        private String architecture;
        @JsonProperty("swap")
        private MemorySwapInfo swap;
    }

    @Data
    public static class MemorySwapInfo {
        @JsonProperty("swapCapacityMB")
        private Long swapCapacityMB;
    }

    @Data
    public static class ContainerImage {
        @JsonProperty("names")
        private List<String> names;
        @JsonProperty("sizeBytes")
        private Long sizeBytes;
    }

    @Data
    public static class AttachedVolume {
        @JsonProperty("name")
        private String name;
        @JsonProperty("devicePath")
        private String devicePath;
    }

    @Data
    public static class NodeConfigStatus {
        @JsonProperty("active")
        private NodeConfigSource active;
        @JsonProperty("assigned")
        private NodeConfigSource assigned;
        @JsonProperty("lastKnownGood")
        private NodeConfigSource lastKnownGood;
        @JsonProperty("error")
        private String error;
    }

    @Data
    public static class ResourceStatus {
        @JsonProperty("resourceClaimStatusList")
        private List<ResourceClaimStatus> resourceClaimStatusList;
    }

    @Data
    public static class ResourceClaimStatus {
        private String name;
    }
}
