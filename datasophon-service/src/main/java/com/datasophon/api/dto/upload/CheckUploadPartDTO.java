package com.datasophon.api.dto.upload;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class CheckUploadPartDTO {
    @Schema(description = "附件ID")
    @NotNull(message = "附件ID不能为空")
    private Long attachId;

    @Schema(description = "分片索引 0base")
    @NotNull(message = "分片索引不能为空")
    private Integer chunkNo;

    @Schema(description = "md5")
    @NotBlank(message = "分片索引不能为空")
    private String md5;
}