package com.datasophon.api.dto.extrepo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * @author zhanghuangbin
 * @date 2025/11/7
 */
@Data
public class InstallComponentDTO {

    @NotNull(message = "meta文件不能为空")
    @Schema(description = "meta文件ID")
    private Long meteFileId;

    @NotNull(message = "软件安装文件不能为空")
    @Schema(description = "软件安装文件ID")
    private Long pkgFileId;

    @NotBlank(message = "meta文件解压密码不能为空")
    @Schema(description = "meta文件解压密码")
    private String unzipPasswd;

//    @NotBlank(message = "敏感文件解密密码不能为空")
    @Schema(description = "敏感文件解密密码")
    private String contentDecodePasswd;
}
