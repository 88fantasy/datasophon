package com.datasophon.api.vo.extrepo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author zhanghuangbin
 * @date 2025/11/11
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InstallResult implements scala.Serializable {


    @Schema(description = "dagId")
    private String dagId;

    @Schema(description = "命令行ID")
    private List<String> commandIds;


}
