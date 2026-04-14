package com.datasophon.api.dto.log;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class K8sRuntimeEventQueryDTO {

    @Schema(description = "服务实例ID")
    private Integer instanceId;


    @Schema(description = "deployment")
    private String deployment;


}
