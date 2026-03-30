package com.datasophon.api.vo.instance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class K8sServiceInstanceValuesVO {

    @Schema(description = "原始yaml的文本")
    private String values;

    @Schema(description = "用户新增的配置项，yaml")
    private String deltaValues;
}
