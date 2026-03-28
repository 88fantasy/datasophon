package com.datasophon.common.k8s.vo.k8s;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * K8s Deployment 资源
 */
@Data
public class K8sDeployment {
    private String apiVersion;
    private String kind;
    private Metadata metadata;
    private DeploymentSpec spec;
    private DeploymentStatus status;

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
    public static class DeploymentSpec {
        private Integer replicas;
        @JsonProperty("selector")
        private LabelSelector selector;
        @JsonProperty("template")
        private PodTemplateSpec template;
        @JsonProperty("strategy")
        private DeploymentStrategy strategy;
        private Integer minReadySeconds;
        private Integer revisionHistoryLimit;
        private Boolean paused;
        private Integer progressDeadlineSeconds;
    }

    @Data
    public static class LabelSelector {
        private Map<String, String> matchLabels;
        private List<LabelSelectorRequirement> matchExpressions;
    }

    @Data
    public static class LabelSelectorRequirement {
        private String key;
        private String operator;
        private List<String> values;
    }

    @Data
    public static class DeploymentStrategy {
        private String type;
        @JsonProperty("rollingUpdate")
        private RollingUpdate rollingUpdate;
    }

    @Data
    public static class RollingUpdate {
        @JsonProperty("maxUnavailable")
        private String maxUnavailable;
        @JsonProperty("maxSurge")
        private String maxSurge;
    }

    @Data
    public static class PodTemplateSpec {
        private PodTemplateMetadata metadata;
        @JsonProperty("spec")
        private PodSpec spec;
    }

    @Data
    public static class PodTemplateMetadata {
        private Map<String, String> labels;
        private Map<String, String> annotations;
    }

    @Data
    public static class PodSpec {
        private List<Container> containers;
        private List<Container> initContainers;
        @JsonProperty("nodeName")
        private String nodeName;
        @JsonProperty("serviceAccountName")
        private String serviceAccountName;
        private String serviceAccount;
        private String restartPolicy;
        private String terminationGracePeriodSeconds;
        private String dnsPolicy;
        private List<String> hostAliases;
        private String nodeSelector;
        private String affinity;
        private String tolerations;
        private List<PodVolume> volumes;
        private String runtimeClassName;
        private String priorityClassName;
        private Integer priority;
        private Boolean hostNetwork;
        private Boolean hostPID;
        private Boolean hostIPC;
        @JsonProperty("securityContext")
        private PodSecurityContext securityContext;
    }

    @Data
    public static class PodVolume {
        private String name;
    }

    @Data
    public static class Container {
        private String name;
        private String image;
        @JsonProperty("ports")
        private List<ContainerPort> ports;
        @JsonProperty("env")
        private List<EnvVar> env;
        @JsonProperty("resources")
        private ResourceRequirements resources;
        @JsonProperty("volumeMounts")
        private List<VolumeMount> volumeMounts;
        @JsonProperty("command")
        private List<String> command;
        @JsonProperty("args")
        private List<String> args;
        @JsonProperty("workingDir")
        private String workingDir;
        @JsonProperty("envFrom")
        private List<EnvFromSource> envFrom;
        @JsonProperty("imagePullPolicy")
        private String imagePullPolicy;
        @JsonProperty("livenessProbe")
        private Probe livenessProbe;
        @JsonProperty("readinessProbe")
        private Probe readinessProbe;
        @JsonProperty("startupProbe")
        private Probe startupProbe;
        @JsonProperty("lifecycle")
        private Lifecycle lifecycle;
        @JsonProperty("stdin")
        private Boolean stdin;
        @JsonProperty("stdinOnce")
        private Boolean stdinOnce;
        @JsonProperty("tty")
        private Boolean tty;
        @JsonProperty("terminationMessagePath")
        private String terminationMessagePath;
        @JsonProperty("terminationMessagePolicy")
        private String terminationMessagePolicy;
    }

    @Data
    public static class ContainerPort {
        @JsonProperty("name")
        private String name;
        @JsonProperty("containerPort")
        private Integer containerPort;
        @JsonProperty("protocol")
        private String protocol;
        @JsonProperty("hostPort")
        private Integer hostPort;
        @JsonProperty("hostIP")
        private String hostIP;
    }

    @Data
    public static class EnvVar {
        @JsonProperty("name")
        private String name;
        @JsonProperty("value")
        private String value;
        @JsonProperty("valueFrom")
        private EnvVarSource valueFrom;
    }

    @Data
    public static class EnvVarSource {
        private ConfigMapKeyRef configMapKeyRef;
        private SecretKeyRef secretKeyRef;
        private FieldRef fieldRef;
        private ResourceFieldRef resourceFieldRef;
    }

    @Data
    public static class ConfigMapKeyRef {
        private String name;
        private String key;
    }

    @Data
    public static class SecretKeyRef {
        private String name;
        private String key;
    }

    @Data
    public static class FieldRef {
        private String fieldPath;
    }

    @Data
    public static class ResourceFieldRef {
        private String resource;
    }

    @Data
    public static class VolumeMount {
        private String name;
        private String mountPath;
        private Boolean readOnly;
        private String subPath;
        private String mountPropagation;
        private Boolean readOnlyRootFilesystem;
    }

    @Data
    public static class EnvFromSource {
        private String prefix;
        private ConfigMapRef configMapRef;
        private SecretRef secretRef;
    }

    @Data
    public static class ConfigMapRef {
        private String name;
    }

    @Data
    public static class SecretRef {
        private String name;
    }

    @Data
    public static class Probe {
        private HTTPGet httpGet;
        private Exec exec;
        private TCPSocket tcpSocket;
        private Integer initialDelaySeconds;
        private Integer timeoutSeconds;
        private Integer periodSeconds;
        private Integer successThreshold;
        private Integer failureThreshold;
        private Integer terminationGracePeriodSeconds;
    }

    @Data
    public static class HTTPGet {
        private String path;
        private String port;
        private String scheme;
        private List<HTTPHeader> httpHeaders;
    }

    @Data
    public static class HTTPHeader {
        private String name;
        private String value;
    }

    @Data
    public static class Exec {
        private List<String> command;
    }

    @Data
    public static class TCPSocket {
        private String port;
        private String host;
    }

    @Data
    public static class Lifecycle {
        private Handler postStart;
        private Handler preStop;
    }

    @Data
    public static class Handler {
        private HTTPGet httpGet;
        private Exec exec;
        private TCPSocket tcpSocket;
    }



    @Data
    public static class ResourceRequirements {
        @JsonProperty("requests")
        private Map<String, String> requests;
        @JsonProperty("limits")
        private Map<String, String> limits;
        @JsonProperty("claims")
        private List<ResourceClaim> claims;
    }

    @Data
    public static class ResourceClaim {
        private String name;
    }

    @Data
    public static class PodSecurityContext {
        private Long runAsUser;
        private Long runAsGroup;
        private Long fsGroup;
        private List<String> supplementalGroups;
        private Boolean runAsNonRoot;
        private String seLinuxOptions;
        private String fsGroupChangePolicy;
        private String sysctls;
    }

    @Data
    public static class DeploymentStatus {
        private Integer replicas;
        @JsonProperty("readyReplicas")
        private Integer readyReplicas;
        @JsonProperty("availableReplicas")
        private Integer availableReplicas;
        @JsonProperty("unavailableReplicas")
        private Integer unavailableReplicas;
        @JsonProperty("updatedReplicas")
        private Integer updatedReplicas;
        @JsonProperty("observedGeneration")
        private Long observedGeneration;
        private Long collisionCount;
        private List<DeploymentCondition> conditions;
        private String unavailableNodes;
    }

    @Data
    public static class DeploymentCondition {
        @JsonProperty("type")
        private String type;
        @JsonProperty("status")
        private String status;
        @JsonProperty("reason")
        private String reason;
        @JsonProperty("message")
        private String message;
        @JsonProperty("lastUpdateTime")
        private String lastUpdateTime;
        @JsonProperty("lastTransitionTime")
        private String lastTransitionTime;
    }
}
