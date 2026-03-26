package com.datasophon.api.dto.instance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * @author zhanghuangbin
 */
@Data
public class K8sNamespaceIdentityDTO {

    @NotBlank(message = "集群ID不能为空")
    @Schema(description = "集群ID")
    private Integer clusterId;

    @NotBlank(message = "名空间不能为空")
    @Schema(description = "名空间")
    private String namespace;

}
