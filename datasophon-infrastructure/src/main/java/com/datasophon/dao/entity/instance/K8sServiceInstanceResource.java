package com.datasophon.dao.entity.instance;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 *
 * @author zhanghuangbin
 */
@Data
@TableName("t_ddh_k8s_service_instance_resource")
public class K8sServiceInstanceResource implements Serializable {

    @TableId
    private Integer id;


    @Schema(description = "集群")
    private Integer clusterId;

    @Schema(description = "名空间ID")
    private Integer namespaceId;

    @Schema(description = "服务ID")
    private Integer serviceId;

    @Schema(description = "实例ID")
    private Integer instanceId;

    @Schema(description = "资源类型， deployment， statfulset")
    private String type;

    @Schema(description = "资源名称")
    private String resourceName;

    @Schema(description = "副本数")
    private Integer replicas;

}
