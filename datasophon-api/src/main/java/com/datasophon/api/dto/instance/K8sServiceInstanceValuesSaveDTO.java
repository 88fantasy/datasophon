package com.datasophon.api.dto.instance;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class K8sServiceInstanceValuesSaveDTO implements Serializable {
    
    @Schema(description = "集群")
    @NotNull(message = "集群不能为空")
    private Integer clusterId;
    
    @Schema(description = "服务ID")
    @NotNull(message = "服务ID不能为空")
    private Integer serviceId;
    
    @Schema(description = "原始yaml的文本")
    private String values;
    
    @Schema(description = "用户新增的配置项，yaml")
    private String deltaValues;
    
    @Schema(description = "部署的名空间")
    @NotBlank(message = "部署名空间不能为空")
    private String namespace;
    
    @Schema(description = "部署清单采用的部署方式, helm, yaml")
    @NotBlank(message = "部署清单采用的部署方式")
    private String metaFileType;
    
}
