package com.datasophon.dao.entity.instance;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * @author zhanghuangbin
 */
@Data
@TableName("t_ddh_k8s_service_instance_values")
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

    @Schema(description = "原始yaml的文本")
    private String values;

    @Schema(description = "用户新增的配置项，yaml")
    private String deltaValues;

    @Schema(description = "版本")
    private Integer version;

    @Schema(description = "部署清单采用的部署方式, helm, yaml")
    private String metaFileType;
}
