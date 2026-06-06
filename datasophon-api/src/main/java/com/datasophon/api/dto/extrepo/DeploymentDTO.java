package com.datasophon.api.dto.extrepo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class DeploymentDTO {
    
    @Schema(description = "集群ID")
    @NotNull(message = "集群ID不能为空")
    private Integer clusterId;
    
    @Schema(description = "发布清单文件ID")
    @NotNull(message = "发布清单文件不能为空")
    private Integer deployFileId;
    
    @Schema(description = "敏感文件解密密码")
    @NotBlank(message = "解密密码不能为空")
    private String contentDecodePasswd;
    
    @Schema(description = "服务ID列表")
    private String serviceIds;
    
    @Schema(description = "当前选中的服务ID")
    private Integer selectedServiceId;
    
}
