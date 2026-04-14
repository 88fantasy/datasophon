package com.datasophon.api.controller.instance;

import com.datasophon.api.controller.ApiController;
import com.datasophon.api.dto.instance.K8sNamespaceIdentityDTO;
import com.datasophon.api.dto.instance.K8sServiceInstanceQueryDTO;
import com.datasophon.api.service.instance.K8sServiceInstanceService;
import com.datasophon.api.vo.k8s.K8sConfigMapInfo;
import com.datasophon.api.vo.k8s.K8sDeploymentInfo;
import com.datasophon.api.vo.k8s.K8sEventInfo;
import com.datasophon.api.vo.k8s.K8sIngressInfo;
import com.datasophon.api.vo.k8s.K8sPodInfo;
import com.datasophon.api.vo.k8s.K8sServiceInfo;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.vo.instance.K8sServiceInstanceVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * @author zhanghuangbin
 */
@RestController
@RequestMapping("cluster/k8sInstance")
@Tag(name = "k8s服务管理")
public class K8sServiceInstanceController extends ApiController {


    @Autowired
    private K8sServiceInstanceService k8sServiceInstanceService;

    @PostMapping("queryInstanceList")
    @Operation(summary = "查询集群下的服务列表")
    @ApiResponse(
            content = {@Content(mediaType = "application/json", schema = @Schema(implementation = K8sServiceInstanceVO.class))}
    )
    public Result queryInstanceList(@RequestBody K8sNamespaceIdentityDTO query) {
        List<K8sServiceInstanceVO> instances = k8sServiceInstanceService.queryInstanceList(query);
        return Result.success(instances);
    }

    @PostMapping("listResourceType")
    @Operation(summary = "查询服务实例的资源类型列表")
    @ApiResponse(content = {
            @Content(
                    mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(implementation = String.class)))
    })
    public Result listResourceType(@RequestBody K8sServiceInstanceQueryDTO query) {
        List<String> resourceTypes = k8sServiceInstanceService.listResourceType(query);
        return Result.success(resourceTypes);
    }


    @PostMapping("listResource")
    @Operation(summary = "查询服务实例的资源列表")
    @ApiResponse(content = {
            @Content(
                    mediaType = "application/json",
                    array = @ArraySchema(schema = @Schema(oneOf = {
                            K8sDeploymentInfo.class,
                            K8sPodInfo.class,
                            K8sServiceInfo.class,
                            K8sIngressInfo.class,
                            K8sConfigMapInfo.class,
                    })))
    })
    public Result listResource(@RequestBody K8sServiceInstanceQueryDTO query) {
        return Result.success(k8sServiceInstanceService.listResource(query));
    }

    @PostMapping("removeInstanceId/{instanceId}")
    @Operation(summary = "根据ID删除实例")
    public Result removeInstanceId(@PathVariable Integer instanceId) {
        k8sServiceInstanceService.removeInstanceId(instanceId);
        return Result.success();
    }
}
