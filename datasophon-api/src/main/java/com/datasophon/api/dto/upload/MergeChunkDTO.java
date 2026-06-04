package com.datasophon.api.dto.upload;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author zhanghuangbin
 * @date 2025/11/6
 */
@Data
public class MergeChunkDTO {
    
    @Schema(description = "附件ID")
    private Integer attachId;
    
    @Schema(description = "md5")
    private String md5;
    
    @Schema(description = "是否异步合并", defaultValue = "false")
    private boolean async;
}
