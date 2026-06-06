package com.datasophon.api.dto.upload;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class CheckChunkDTO {
    
    @Schema(description = "附件ID")
    private Integer attachId;
    
    @Schema(description = "分片索引 0base")
    private Integer chunkNo;
    
    private String md5;
}
