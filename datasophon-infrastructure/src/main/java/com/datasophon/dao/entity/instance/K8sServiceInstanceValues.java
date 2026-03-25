package com.datasophon.dao.entity.instance;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * @author zhanghuangbin
 */
@Data
@TableName("t_ddh_k8s_service_instance_Values")
public class K8sServiceInstanceValues implements Serializable {

    private Integer id;

    @Schema(description = "集群")
    private Integer clusterId;

    @Schema(description = "名空间ID")
    private Integer namespaceId;

    @Schema(description = "服务ID")
    private Integer serviceId;


    @Schema(description = "实例ID")
    private Integer instanceId;

    @Schema(description = "yaml的文本")
    private String values;
}
