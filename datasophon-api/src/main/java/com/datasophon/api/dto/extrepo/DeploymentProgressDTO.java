package com.datasophon.api.dto.extrepo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class DeploymentProgressDTO {
    
    @Schema(description = "集群ID")
    private Integer clusterId;
    
    @Schema(description = "上一步安装，返回的命令行ID")
    private List<String> cmdIds;
    
}
