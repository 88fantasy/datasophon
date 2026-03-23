package com.datasophon.api.dto.extrepo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class DagIdDto {

    @Schema(description = "dagId")
    private String dagId;

}
