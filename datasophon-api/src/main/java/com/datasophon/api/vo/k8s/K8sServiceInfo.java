package com.datasophon.api.vo.k8s;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

import lombok.Data;

/**
 * K8s Service 资源信息
 *
 * @author zhanghuangbin
 */
@Data
public class K8sServiceInfo {
    
    @Schema(description = "Service 名称")
    private String name;
    
    @Schema(description = "命名空间")
    private String namespace;
    
    @Schema(description = "运行时间")
    private String age;
    
    @Schema(description = "服务类型（ClusterIP/NodePort/LoadBalancer/ExternalName）")
    private String type;
    
    @Schema(description = "集群 IP")
    private String clusterIP;
    
    @Schema(description = "外部 IP")
    private String externalIP;
    
    @Schema(description = "外部负载均衡 IP")
    private String loadBalancerIP;
    
    @Schema(description = "端口列表")
    private List<PortInfo> ports;
    
    @Schema(description = "选择器标签")
    private String selector;
    
    @Schema(description = "会话粘性")
    private String sessionAffinity;
    
    @Schema(description = "状态")
    private String status;
    
    @Schema(description = "标签")
    private Map<String, String> labels;
    
    /**
     * 端口信息
     */
    @Data
    public static class PortInfo {
        @Schema(description = "端口名称")
        private String name;
        
        @Schema(description = "端口")
        private Integer port;
        
        @Schema(description = "目标端口")
        private String targetPort;
        
        @Schema(description = "节点端口")
        private Integer nodePort;
        
        @Schema(description = "协议")
        private String protocol;
    }
}
