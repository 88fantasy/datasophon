package com.datasophon.api.dto.extrepo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author zhanghuangbin
 * @date 2025/11/11
 */
@Data
public class DeploymentDTO {


    @Schema(description = "集群ID")
    private Integer clusterId;

    @Schema(description = "发布清单文件ID")
    private Long deployFileId;

    @Schema(description = "敏感文件解密密码")
    private String contentDecodePasswd;
}
