package com.datasophon.api.controller.instance;

import com.datasophon.api.controller.ApiController;
import com.datasophon.api.dto.instance.K8sServiceInstanceValuesUpdateDTO;
import com.datasophon.api.security.UserPermission;
import com.datasophon.api.service.instance.K8sServiceInstanceValuesService;
import com.datasophon.api.vo.instance.K8sServiceInstanceValuesVO;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.instance.K8sServiceInstanceValues;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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

import java.util.List;

/**
 * k8s 服务实例 values 管理
 *
 * @author zhanghuangbin
 */
@RestController
@RequestMapping("frame/k8sInstanceValues")
@Tag(name = "k8s 服务实例 values 管理")
public class K8sServiceInstanceValuesController extends ApiController {

    @Autowired
    private K8sServiceInstanceValuesService k8sServiceInstanceValuesService;

    @GetMapping("listSimpleByInstanceId/{instanceId}")
    @Operation(summary = "根据实例 ID 获取 values 列表")
    @ApiResponse(content = {
            @Content(
                    mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = K8sServiceInstanceValues.class)))
    })
    @UserPermission
    public Result listSimpleByInstanceId(@PathVariable Integer instanceId) {
        List<K8sServiceInstanceValues> result = k8sServiceInstanceValuesService.listSimpleByInstanceId(instanceId);
        return Result.success(result);
    }


    @GetMapping("getById/{valueId}")
    @Operation(summary = "根据配置ID获取values的值")
    @ApiResponse(content = {
            @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = K8sServiceInstanceValues.class))
    })
    @UserPermission
    public Result getById(@PathVariable Integer valueId) {
        K8sServiceInstanceValues result = k8sServiceInstanceValuesService.getById(valueId);
        return Result.success(result);
    }


    @GetMapping("getValueFromRepo/{serviceId}")
    @Operation(summary = "从仓库获取服务的 values 配置")
    @ApiResponse(content = {
            @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = K8sServiceInstanceValuesVO.class))
    })
    @UserPermission
    public Result getValueFromRepo(@PathVariable Integer serviceId, @Schema(description = "部署类型，可选值，helm, k8s") String artifactType) {
        K8sServiceInstanceValuesVO result = k8sServiceInstanceValuesService.getValueFromRepo(serviceId, artifactType);
        return Result.success(result);
    }


    @PostMapping("update")
    @Operation(summary = "更新服务实例 values")
    @ApiResponse(content = {
            @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = K8sServiceInstanceValues.class))
    })
    public Result update(@Validated @RequestBody K8sServiceInstanceValuesUpdateDTO values) {
        K8sServiceInstanceValues result = k8sServiceInstanceValuesService.update(values);
        return Result.success(result);
    }
}
