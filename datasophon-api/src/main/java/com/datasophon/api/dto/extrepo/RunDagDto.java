package com.datasophon.api.dto.extrepo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class RunDagDto {
    
    @Schema(description = "dagId")
    private String dagId;
    
    @Schema(description = "是否重启，true会忽略已成功的节点")
    private boolean restart;
    
}
