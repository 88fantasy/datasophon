package com.datasophon.api.controller.cluster;

import com.datasophon.api.controller.ApiController;
import com.datasophon.api.security.UserPermission;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.api.service.k8s.K8sService;
import com.datasophon.api.vo.k8s.K8sConnectionResult;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author zhanghuangbin
 */

@RestController
@RequestMapping("cluster/k8sConfig")
@Tag(name = "k8s集群初始化配置")
public class K8sClusterConfigController extends ApiController {
    
    @Autowired
    private K8sService k8sService;
    
    @Autowired
    private K8sClusterConfigService k8sClusterConfigService;
    
    @PostMapping("testConnection")
    @Operation(summary = "测试集群连通性")
    @ApiResponse(content = {@Content(mediaType = "application/json", schema = @Schema(implementation = K8sConnectionResult.class))})
    public Result testConnection(@RequestBody @Validated K8sClusterConfig config) {
        return Result.success(k8sService.testConnection(config));
    }
    
    @PostMapping("saveOrUpdateConfig")
    @Operation(summary = "新增修改集群配置")
    @ApiResponse(content = {@Content(mediaType = "application/json", schema = @Schema(implementation = K8sClusterConfig.class))})
    @UserPermission
    public Result saveOrUpdateConfig(@RequestBody @Validated K8sClusterConfig config) {
        return Result.success(k8sClusterConfigService.saveOrUpdateConfig(config));
    }
    
    @GetMapping("getConfigByClusterId/{clusterId}")
    @Operation(summary = "根据集群id获取配置")
    @ApiResponse(content = {@Content(mediaType = "application/json", schema = @Schema(implementation = K8sClusterConfig.class))})
    @UserPermission
    public Result getConfigByClusterId(@PathVariable Integer clusterId) {
        return Result.success(k8sClusterConfigService.getByClusterId(clusterId));
    }
}
