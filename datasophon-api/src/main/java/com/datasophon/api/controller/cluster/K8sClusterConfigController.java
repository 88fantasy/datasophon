package com.datasophon.api.controller.cluster;

import com.datasophon.api.controller.ApiController;
import com.datasophon.api.security.ClusterAccessGuard;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.api.vo.k8s.K8sClusterConfigVO;
import com.datasophon.api.vo.k8s.K8sConnectionResult;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * @author zhanghuangbin
 */

@RestController
@RequestMapping("cluster/k8sConfig")
@Tag(name = "k8s集群初始化配置")
public class K8sClusterConfigController extends ApiController {

    @Autowired
    private K8sClusterConfigService k8sClusterConfigService;

    @Autowired
    private ClusterAccessGuard clusterAccessGuard;

    @PostMapping("testConnection")
    @Operation(summary = "测试集群连通性")
    @ApiResponse(content = {@Content(mediaType = "application/json", schema = @Schema(implementation = K8sConnectionResult.class))})
    public Result testConnection(@RequestBody @Validated K8sClusterConfig config) {
        clusterAccessGuard.requireAccess(config.getClusterId());
        return Result.success(k8sClusterConfigService.testConnection(config));
    }

    @PostMapping("saveOrUpdateConfig")
    @Operation(summary = "新增修改集群配置")
    @ApiResponse(content = {@Content(mediaType = "application/json", schema = @Schema(implementation = K8sClusterConfigVO.class))})
    public Result saveOrUpdateConfig(@RequestBody @Validated K8sClusterConfig config) {
        clusterAccessGuard.requireAccess(config.getClusterId());
        return Result.success(K8sClusterConfigVO.from(k8sClusterConfigService.saveOrUpdateConfig(config)));
    }

    @GetMapping("getConfigByClusterId/{clusterId}")
    @Operation(summary = "根据集群id获取配置")
    @ApiResponse(content = {@Content(mediaType = "application/json", schema = @Schema(implementation = K8sClusterConfigVO.class))})
    public Result getConfigByClusterId(@PathVariable Integer clusterId) {
        clusterAccessGuard.requireAccess(clusterId);
        return Result.success(K8sClusterConfigVO.from(k8sClusterConfigService.getByClusterId(clusterId)));
    }
}
