package com.datasophon.api.dto.upload;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import org.springframework.web.multipart.MultipartFile;

/**
 * @author zhanghuangbin
 */
@Data
public class ChunkDTO {
    
    @Schema(description = "附件ID")
    private Integer attachId;
    
    @Schema(description = "分片索引 0base")
    private Integer chunkNo;
    
    private MultipartFile chunk;
    
    private String md5;
}
