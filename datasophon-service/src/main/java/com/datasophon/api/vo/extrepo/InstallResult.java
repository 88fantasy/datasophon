package com.datasophon.api.vo.extrepo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author zhanghuangbin
 * @date 2025/11/11
 */
@Data
@AllArgsConstructor
public class InstallResult implements java.io.Serializable {


    @Schema(description = "dagId")
    private String dagId;


}
