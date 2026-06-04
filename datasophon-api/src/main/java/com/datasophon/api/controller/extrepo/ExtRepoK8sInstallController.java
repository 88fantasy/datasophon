package com.datasophon.api.controller.extrepo;

import com.datasophon.api.controller.ApiController;
import com.datasophon.api.dto.extrepo.DeploymentDTO;
import com.datasophon.api.dto.extrepo.K8sProductDeployMapping;
import com.datasophon.api.dto.instance.K8sServiceInstanceValuesSaveDTO;
import com.datasophon.api.service.extrepo.K8sProductInstallService;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.frame.FrameK8sServiceEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author zhanghuangbin
 * @date 2025/11/5
 */
@RestController
@RequestMapping("extrepo/k8s")
@Tag(name = "外部软件源(K8S制品)", description = "k8s制品独有的接口")
public class ExtRepoK8sInstallController extends ApiController {
    
    @Autowired
    private K8sProductInstallService k8sProductInstallService;
    
    @PostMapping("listNewestByDeployment")
    @Operation(summary = "获取最新的K8s服务列表(并更加部署清单对服务列表进行勾选)")
    @ApiResponse(content = {
            @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = FrameK8sServiceEntity.class)))
    })
    public Result listK8sNewestByDeployment(@RequestBody @Validated DeploymentDTO dto) {
        return Result.success(k8sProductInstallService.listNewestByDeployment(dto));
    }
    
    @PostMapping("saveServiceNamespaceMapping/{clusterId}")
    @Operation(summary = "保存服务命名空间映射")
    public Result saveServiceNamespaceMapping(@PathVariable Integer clusterId, @RequestBody @Validated List<K8sProductDeployMapping> mappings) {
        k8sProductInstallService.saveServiceNamespaceMapping(clusterId, mappings);
        return Result.success();
    }
    
    /**
     * @deprecated
     * @see #saveConfigValueList(List)
     * @param dto
     * @return
     */
    @PostMapping("saveConfigValues")
    @Operation(summary = "保存配置 values", deprecated = true, description = "该为调用saveConfigValueList方法")
    @ApiResponse(content = {
            @Content(mediaType = "application/json", schema = @Schema(implementation = Integer.class))
    })
    @Deprecated
    public Result saveConfigValues(@RequestBody @Validated K8sServiceInstanceValuesSaveDTO dto) {
        return Result.success(k8sProductInstallService.saveConfigValueList(Collections.singletonList(dto)).get(0));
    }
    
    @PostMapping("saveConfigValueList")
    @Operation(summary = "保存配置 values")
    @ApiResponse(content = {
            @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = Integer.class)))
    })
    public Result saveConfigValueList(@RequestBody @Validated List<K8sServiceInstanceValuesSaveDTO> list) {
        return Result.success(k8sProductInstallService.saveConfigValueList(list));
    }
    
}
