package com.datasophon.api.dto.log;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class K8sRuntimeLogQueryDTO {

    @Schema(description = "服务实例ID")
    private Integer instanceId;

    @Schema(description = "pod名称")
    private String podName;

    @Schema(description = "container名称，pod存在多个容器时，必填")
    private String containerName;

    @Schema(description = "查看容器上一次运行终止时的日志")
    private boolean previous;

    @Schema(description = "日志行数")
    private Integer lines;


}
