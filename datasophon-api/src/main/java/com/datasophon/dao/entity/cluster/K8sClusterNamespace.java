package com.datasophon.dao.entity.cluster;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

import lombok.Data;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * @author zhanghuangbin
 */
@Data
@TableName("t_ddh_k8s_cluster_namespace")
public class K8sClusterNamespace implements Serializable {
    
    @TableId
    private Integer id;
    
    @Schema(description = "集群ID")
    private Integer clusterId;
    
    @Schema(description = "管理状态  -1未知状态(即还没有和k8s集群对账) 0->namespace的状态为inactive 1namespace的状态为active")
    private Integer state;
    
    @Schema(description = "名空间的信息")
    private String namespace;
    
}
