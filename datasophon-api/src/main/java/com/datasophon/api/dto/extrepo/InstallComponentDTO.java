package com.datasophon.api.dto.extrepo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class InstallComponentDTO {
    
    @NotNull(message = "meta文件不能为空")
    @Schema(description = "meta文件ID")
    private Integer meteFileId;
    
    @Schema(description = "软件安装文件ID, 允许为空，为空时，则不上传到nexus")
    private Integer pkgFileId;
    
    @NotBlank(message = "敏感文件解密密码不能为空")
    @Schema(description = "敏感文件解密密码")
    private String contentDecodePasswd;
    
}
