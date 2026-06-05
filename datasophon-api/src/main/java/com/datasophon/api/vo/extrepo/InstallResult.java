package com.datasophon.api.vo.extrepo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
@AllArgsConstructor
public class InstallResult implements java.io.Serializable {
    
    @Schema(description = "dagId")
    private String dagId;
    
}
