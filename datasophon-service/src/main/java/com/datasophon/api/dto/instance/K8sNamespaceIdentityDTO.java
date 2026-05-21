package com.datasophon.api.dto.instance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * @author zhanghuangbin
 */
@Data
@NoArgsConstructor
public class K8sNamespaceIdentityDTO {

    @NotBlank(message = "集群ID不能为空")
    @Schema(description = "集群ID")
    private Integer clusterId;

    @NotBlank(message = "名空间不能为空")
    @Schema(description = "名空间")
    private String namespace;


    public K8sNamespaceIdentityDTO(Integer clusterId, String namespace) {
        this.clusterId = clusterId;
        this.namespace = namespace;
    }
}
