package com.datasophon.api.dto.extrepo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.util.List;

import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class K8sServiceNamespaceMappingSaveDTO implements Serializable {
    
    @Schema(description = "集群 ID")
    @NotNull(message = "集群 ID 不能为空")
    private Integer clusterId;
    
    @Schema(description = "服务命名空间映射列表")
    @NotEmpty(message = "服务命名空间映射列表不能为空")
    private List<K8sProductDeployMapping> mappings;
    
}
