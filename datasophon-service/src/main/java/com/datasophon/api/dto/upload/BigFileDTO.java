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

//    @Schema(description = "分片数量")
//    @NotNull(message = "分片数量不能为空")
//    private Integer chunk;

}
