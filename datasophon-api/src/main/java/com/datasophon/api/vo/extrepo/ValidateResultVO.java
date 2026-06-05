package com.datasophon.api.vo.extrepo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

import lombok.Data;
import cn.hutool.core.collection.CollectionUtil;

/**
 * @author zhanghuangbin
 */
@Data
public class ValidateResultVO {
    
    @Schema(description = "错误信息")
    private List<String> errors;
    
    @Schema(description = "部署进程清单")
    private List<DeploySrvRoleModel> roles;
    
    @Schema(description = "部署的K8S服务")
    private List<DeployK8sServiceModel> k8sServices;
    
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
    
    @Data
    public static class DeployK8sServiceModel {
        
        @Schema(description = "服务名")
        private String serviceName;
        
        @Schema(description = "服务版本")
        private String version;
        
        @Schema(description = "名空间")
        private String namespace;
        
        @Schema(description = "部署的方式")
        private String metaFileType;
        
    }
    
}
