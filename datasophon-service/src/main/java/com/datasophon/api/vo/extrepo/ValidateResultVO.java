package com.datasophon.api.vo.extrepo;

import cn.hutool.core.collection.CollectionUtil;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * @author zhanghuangbin
 * @date 2025/11/12
 */
@Data
public class ValidateResultVO {

    @Schema(description = "错误信息")
    private List<String> errors;

    @Schema(description = "部署进程清单")
    private List<DeploySrvRoleModel> roles;


    public ValidateResultVO() {
    }

    public ValidateResultVO(List<String> errors) {
        this.errors = errors;
    }

    public boolean isSuccess() {
        return CollectionUtil.isEmpty(errors);
    }


    @Data
    public static class DeploySrvRoleModel {

        @Schema(description = "服务名")
        private String serviceName;

        @Schema(description = "服务版本")
        private String version;

        @Schema(description = "角色名称")
        private String roleName;

        @Schema(description = "部署主机")
        private List<String> deployHosts;
    }

}
