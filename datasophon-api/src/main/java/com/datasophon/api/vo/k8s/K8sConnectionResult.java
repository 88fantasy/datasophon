package com.datasophon.api.vo.k8s;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author zhanghuangbin
 */
@Data
public class K8sConnectionResult {
    
    @Schema(description = "登录凭据是否正确")
    private boolean success;
    
    @Schema(description = "错误信息")
    private String info;
    
}
