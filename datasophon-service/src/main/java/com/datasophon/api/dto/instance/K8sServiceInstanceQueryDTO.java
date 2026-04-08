package com.datasophon.api.dto.instance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * @author zhanghuangbin
 */
@Data
public class K8sServiceInstanceQueryDTO {

    @Schema(description = "实例ID")
    @NotBlank(message = "实例ID不能为空")
    private Integer instanceId;

    @Schema(description = "资源类型，pod, service，deployment等")
    private String resourceType;

}
