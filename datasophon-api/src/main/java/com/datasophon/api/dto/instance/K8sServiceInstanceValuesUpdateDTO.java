package com.datasophon.api.dto.instance;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * @author zhanghuangbin
 */
@Data
public class K8sServiceInstanceValuesUpdateDTO implements Serializable {


    @Schema(description = "主键")
    private Integer id;

    @Schema(description = "用户新增的配置项，yaml")
    private String deltaValues;

}
