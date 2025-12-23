package com.datasophon.api.dto.upload;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author zhanghuangbin
 * @date 2025/11/6
 */
@Data
public class CheckChunkDTO {

    @Schema(description = "附件ID")
    private Long attachId;

    @Schema(description = "分片索引 0base")
    private Integer chunkNo;

}
