package com.datasophon.api.dto.agent;

import lombok.Builder;
import lombok.Data;

/**
 * K8s Agent 部署参数
 */
@Data
@Builder
public class K8sAgentDeployParams {
    
    /**
     * 集群 ID
     */
    private Integer clusterId;
    
    /**
     * Release 名称（可选，默认根据集群 ID 生成）
     */
    private String releaseName;
    
    /**
     * 命名空间（可选，默认与 releaseName 相同）
     */
    private String namespace;
    
    /**
     * 镜像标签
     */
    private String imageTag;
    
    /**
     * 服务端口
     */
    private Integer serverPort;
}
