package com.datasophon.api.dto.upload;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * @author zhanghuangbin
 * @date 2025/11/6
 */
@Data
public class BigFileDTO {

    @Schema(description = "文件名称")
    @NotBlank(message = "文件名称不能为空")
    private String fileName;

    @Schema(description = "contentType")
    private String contentType;

    @Schema(description = "文件总大小")
    @NotNull(message = "文件总大小不能为空")
    private Long byteCnt;

    @Schema(description = "文件MD5, 可以为空，如果不为空，则检查文件是否已经存在，则uploadType为2，表示秒传，不需要后续上传")
    private String md5;


}
