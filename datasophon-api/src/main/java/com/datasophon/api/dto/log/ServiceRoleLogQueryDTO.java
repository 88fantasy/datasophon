package com.datasophon.api.dto.log;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class ServiceRoleLogQueryDTO {

    @Schema(description = "集群ID")
    private Integer clusterId;

    @Schema(description = "服务名称")
    private String serviceName;

    @Schema(description = "角色名称")
    private String serviceRoleName;

    @Schema(description = "主机名")
    private String host;

    @Schema(description = "日志行数")
    private Integer lines;


}
