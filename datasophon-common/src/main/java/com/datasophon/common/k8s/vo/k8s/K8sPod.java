package com.datasophon.common.k8s.vo.k8s;

import java.util.List;
import java.util.Map;

import lombok.Data;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * K8s Pod 资源
 */
@Data
public class K8sPod {
    private String apiVersion;
    private String kind;
    private Metadata metadata;
    private PodSpec spec;
    private PodStatus status;
    
    @Data
    public static class Metadata {
        private String name;
        private String namespace;
        private Map<String, String> labels;
        private Map<String, String> annotations;
        private String creationTimestamp;
        private String resourceVersion;
        private String uid;
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
    public static class PodSpec {
        @JsonProperty("nodeName")
        private String nodeName;
        @JsonProperty("serviceAccountName")
        private String serviceAccountName;
        
        private String serviceAccount;
        
        @JsonProperty("containers")
        private List<Container> containers;
        @JsonProperty("initContainers")
        private List<Container> initContainers;
        @JsonProperty("restartPolicy")
        private String restartPolicy;
        @JsonProperty("terminationGracePeriodSeconds")
        private Long terminationGracePeriodSeconds;
        @JsonProperty("dnsPolicy")
        private String dnsPolicy;
        @JsonProperty("hostAliases")
        private List<HostAlias> hostAliases;
        @JsonProperty("nodeSelector")
        private Map<String, String> nodeSelector;
        
        @JsonProperty("volumes")
        private List<Volume> volumes;
        @JsonProperty("runtimeClassName")
        private String runtimeClassName;
        @JsonProperty("priorityClassName")
        private String priorityClassName;
        @JsonProperty("priority")
        private Integer priority;
        @JsonProperty("hostNetwork")
        private Boolean hostNetwork;
        @JsonProperty("hostPID")
        private Boolean hostPID;
        @JsonProperty("hostIPC")
        private Boolean hostIPC;
        
        @JsonProperty("imagePullSecrets")
        private List<LocalObjectReference> imagePullSecrets;
        @JsonProperty("enableServiceLinks")
        private Boolean enableServiceLinks;
        @JsonProperty("preemptionPolicy")
        private String preemptionPolicy;
        @JsonProperty("setHostnameAsFQDN")
        private Boolean setHostnameAsFQDN;
        @JsonProperty("os")
        private PodOS os;
        @JsonProperty("hostUsers")
        private Boolean hostUsers;
        @JsonProperty("schedulingGates")
        private List<SchedulingGate> schedulingGates;
        @JsonProperty("resourceClaims")
        private List<ResourceClaim> resourceClaims;
    }
    
    @Data
    public static class Container {
        @JsonProperty("name")
        private String name;
        @JsonProperty("image")
        private String image;
        @JsonProperty("ports")
        private List<ContainerPort> ports;
        @JsonProperty("env")
        private List<EnvVar> env;
        @JsonProperty("resources")
        private ResourceRequirements resources;
        @JsonProperty("volumeMounts")
        private List<VolumeMount> volumeMounts;
        @JsonProperty("volumeDevices")
        private List<VolumeDevice> volumeDevices;
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
        @JsonProperty("terminationMessagePath")
        private String terminationMessagePath;
        @JsonProperty("terminationMessagePolicy")
        private String terminationMessagePolicy;
        @JsonProperty("securityContext")
        private ContainerSecurityContext securityContext;
        @JsonProperty("stdin")
        private Boolean stdin;
        @JsonProperty("stdinOnce")
        private Boolean stdinOnce;
        @JsonProperty("tty")
        private Boolean tty;
    }
    
    @Data
    public static class ContainerPort {
        @JsonProperty("name")
        private String name;
        @JsonProperty("containerPort")
        private Integer containerPort;
        @JsonProperty("hostPort")
        private Integer hostPort;
        @JsonProperty("hostIP")
        private String hostIP;
        @JsonProperty("protocol")
        private String protocol;
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
        @JsonProperty("configMapKeyRef")
        private ConfigMapKeyRef configMapKeyRef;
        @JsonProperty("secretKeyRef")
        private SecretKeyRef secretKeyRef;
        @JsonProperty("fieldRef")
        private FieldRef fieldRef;
        @JsonProperty("resourceFieldRef")
        private ResourceFieldRef resourceFieldRef;
    }
    
    @Data
    public static class ConfigMapKeyRef {
        private String name;
        private String key;
        private Boolean optional;
    }
    
    @Data
    public static class SecretKeyRef {
        private String name;
        private String key;
        private Boolean optional;
    }
    
    @Data
    public static class FieldRef {
        private String apiVersion;
        private String fieldPath;
    }
    
    @Data
    public static class ResourceFieldRef {
        private String containerName;
        private String resource;
        private String divisor;
    }
    
    @Data
    public static class VolumeMount {
        private String name;
        @JsonProperty("mountPath")
        private String mountPath;
        @JsonProperty("readOnly")
        private Boolean readOnly;
        @JsonProperty("subPath")
        private String subPath;
        @JsonProperty("mountPropagation")
        private String mountPropagation;
        @JsonProperty("subPathExpr")
        private String subPathExpr;
        @JsonProperty("recursiveReadOnly")
        private String recursiveReadOnly;
    }
    
    @Data
    public static class VolumeDevice {
        private String name;
        private String devicePath;
    }
    
    @Data
    public static class EnvFromSource {
        private String prefix;
        @JsonProperty("configMapRef")
        private ConfigMapRef configMapRef;
        @JsonProperty("secretRef")
        private SecretRef secretRef;
    }
    
    @Data
    public static class ConfigMapRef {
        private String name;
        private Boolean optional;
    }
    
    @Data
    public static class SecretRef {
        private String name;
        private Boolean optional;
    }
    
    @Data
    public static class Probe {
        @JsonProperty("httpGet")
        private HTTPGet httpGet;
        @JsonProperty("exec")
        private Exec exec;
        @JsonProperty("tcpSocket")
        private TCPSocket tcpSocket;
        @JsonProperty("grpc")
        private GRPC grpc;
        @JsonProperty("initialDelaySeconds")
        private Integer initialDelaySeconds;
        @JsonProperty("timeoutSeconds")
        private Integer timeoutSeconds;
        @JsonProperty("periodSeconds")
        private Integer periodSeconds;
        @JsonProperty("successThreshold")
        private Integer successThreshold;
        @JsonProperty("failureThreshold")
        private Integer failureThreshold;
        @JsonProperty("terminationGracePeriodSeconds")
        private Integer terminationGracePeriodSeconds;
    }
    
    @Data
    public static class HTTPGet {
        private String path;
        @JsonProperty("port")
        private String port;
        private String scheme;
        @JsonProperty("httpHeaders")
        private List<HTTPHeader> httpHeaders;
        private String host;
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
    public static class GRPC {
        private Integer port;
        private String service;
    }
    
    @Data
    public static class Lifecycle {
        @JsonProperty("postStart")
        private Handler postStart;
        @JsonProperty("preStop")
        private Handler preStop;
    }
    
    @Data
    public static class Handler {
        @JsonProperty("httpGet")
        private HTTPGet httpGet;
        @JsonProperty("exec")
        private Exec exec;
        @JsonProperty("tcpSocket")
        private TCPSocket tcpSocket;
        @JsonProperty("sleep")
        private Sleep sleep;
    }
    
    @Data
    public static class Sleep {
        private Long seconds;
    }
    
    @Data
    public static class ContainerSecurityContext {
        @JsonProperty("privileged")
        private Boolean privileged;
        @JsonProperty("capabilities")
        private Capabilities capabilities;
        @JsonProperty("runAsNonRoot")
        private Boolean runAsNonRoot;
        @JsonProperty("readOnlyRootFilesystem")
        private Boolean readOnlyRootFilesystem;
        @JsonProperty("allowPrivilegeEscalation")
        private Boolean allowPrivilegeEscalation;
        @JsonProperty("runAsUser")
        private Long runAsUser;
        @JsonProperty("runAsGroup")
        private Long runAsGroup;
        @JsonProperty("seccompProfile")
        private SeccompProfile seccompProfile;
        @JsonProperty("procMount")
        private String procMount;
    }
    
    @Data
    public static class Capabilities {
        private List<String> add;
        private List<String> drop;
    }
    
    @Data
    public static class SeccompProfile {
        private String type;
        private String localhostProfile;
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
    public static class HostAlias {
        private String ip;
        private List<String> hostnames;
    }
    
    @Data
    public static class NodeAffinity {
        @JsonProperty("requiredDuringSchedulingIgnoredDuringExecution")
        private NodeSelector requiredDuringSchedulingIgnoredDuringExecution;
        @JsonProperty("preferredDuringSchedulingIgnoredDuringExecution")
        private List<PreferredSchedulingTerm> preferredDuringSchedulingIgnoredDuringExecution;
    }
    
    @Data
    public static class NodeSelector {
        private List<NodeSelectorTerm> nodeSelectorTerms;
    }
    
    @Data
    public static class NodeSelectorTerm {
        private List<NodeSelectorRequirement> matchExpressions;
        private List<NodeSelectorRequirement> matchFields;
    }
    
    @Data
    public static class NodeSelectorRequirement {
        private String key;
        private String operator;
        private List<String> values;
    }
    
    @Data
    public static class PreferredSchedulingTerm {
        private Integer weight;
        private NodeSelectorTerm preference;
    }
    
    @Data
    public static class Volume {
        private String name;
    }
    
    @Data
    public static class LocalObjectReference {
        private String name;
    }
    
    @Data
    public static class PodOS {
        private String name;
    }
    
    @Data
    public static class SchedulingGate {
        private String name;
    }
    
    @Data
    public static class PodStatus {
        @JsonProperty("phase")
        private String phase;
        @JsonProperty("podIP")
        private String podIP;
        @JsonProperty("podIPs")
        private List<PodIP> podIPs;
        @JsonProperty("hostIP")
        private String hostIP;
        @JsonProperty("hostIPs")
        private List<HostIP> hostIPs;
        @JsonProperty("containerStatuses")
        private List<ContainerStatus> containerStatuses;
        @JsonProperty("initContainerStatuses")
        private List<ContainerStatus> initContainerStatuses;
        @JsonProperty("ephemeralContainerStatuses")
        private List<ContainerStatus> ephemeralContainerStatuses;
        @JsonProperty("startTime")
        private String startTime;
        @JsonProperty("conditions")
        private List<Condition> conditions;
        @JsonProperty("qosClass")
        private String qosClass;
        @JsonProperty("nominatedNodeName")
        private String nominatedNodeName;
        @JsonProperty("resourceClaimStatuses")
        private List<ResourceClaimStatus> resourceClaimStatuses;
    }
    
    @Data
    public static class PodIP {
        private String ip;
    }
    
    @Data
    public static class HostIP {
        private String ip;
    }
    
    @Data
    public static class ContainerStatus {
        @JsonProperty("name")
        private String name;
        @JsonProperty("ready")
        private Boolean ready;
        @JsonProperty("restartCount")
        private Integer restartCount;
        @JsonProperty("state")
        private ContainerState state;
        @JsonProperty("lastState")
        private ContainerState lastState;
        @JsonProperty("started")
        private Boolean started;
        @JsonProperty("image")
        private String image;
        @JsonProperty("imageID")
        private String imageID;
        @JsonProperty("containerID")
        private String containerID;
    }
    
    @Data
    public static class ContainerState {
        @JsonProperty("waiting")
        private WaitingState waiting;
        @JsonProperty("running")
        private RunningState running;
        @JsonProperty("terminated")
        private TerminatedState terminated;
    }
    
    @Data
    public static class WaitingState {
        @JsonProperty("reason")
        private String reason;
        @JsonProperty("message")
        private String message;
    }
    
    @Data
    public static class RunningState {
        @JsonProperty("startedAt")
        private String startedAt;
    }
    
    @Data
    public static class TerminatedState {
        @JsonProperty("exitCode")
        private Integer exitCode;
        @JsonProperty("reason")
        private String reason;
        @JsonProperty("message")
        private String message;
        @JsonProperty("startedAt")
        private String startedAt;
        @JsonProperty("finishedAt")
        private String finishedAt;
        @JsonProperty("signal")
        private Integer signal;
        @JsonProperty("containerID")
        private String containerID;
    }
    
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
        @JsonProperty("lastUpdateTime")
        private String lastUpdateTime;
        @JsonProperty("lastTransitionTime")
        private String lastTransitionTime;
        @JsonProperty("lastProbeTime")
        private String lastProbeTime;
    }
    
    @Data
    public static class ResourceClaimStatus {
        private String name;
    }
}
