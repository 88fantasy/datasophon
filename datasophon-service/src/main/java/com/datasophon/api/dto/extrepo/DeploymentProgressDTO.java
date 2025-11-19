package com.datasophon.api.dto.extrepo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * @author zhanghuangbin
 * @date 2025/11/11
 */
@Data
public class DeploymentProgressDTO {


    @Schema(description = "集群ID")
    private Integer clusterId;

    @Schema(description = "上一步安装，返回的命令行ID")
    private List<String> cmdIds;


}
