package com.datasophon.api.controller.frame;

import com.datasophon.api.controller.ApiController;
import com.datasophon.api.service.frame.FrameK8sServiceService;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.frame.FrameK8sServiceEntity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author zhanghuangbin
 */
@RestController
@RequestMapping("frame/k8sService")
@Tag(name = "k8s框架服务管理")
public class FrameK8sServiceController extends ApiController {
    
    @Autowired
    private FrameK8sServiceService frameK8sServiceService;
    
    @RequestMapping("/listNewest")
    @Operation(summary = "获取组件列表(最高版本)")
    @ApiResponse(content = {
            @Content(mediaType = "application/json", schema = @Schema(implementation = FrameK8sServiceEntity[].class))
    })
    public Result listNewest(Integer clusterId) {
        List<FrameK8sServiceEntity> result = frameK8sServiceService.listNewest(clusterId);
        return Result.success(result);
    }
    
    @RequestMapping("/delete/{serviceId}")
    @Operation(summary = "删除 K8s 服务")
    public Result delete(@PathVariable Integer serviceId) {
        frameK8sServiceService.removeById(serviceId);
        return Result.success();
    }
    
}
