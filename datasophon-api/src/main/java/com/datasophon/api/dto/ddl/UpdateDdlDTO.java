package com.datasophon.api.dto.ddl;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class UpdateDdlDTO {
    
    @Schema(description = "ddl的内容")
    @NotBlank(message = "ddl不能为空")
    private String content;
    
}
