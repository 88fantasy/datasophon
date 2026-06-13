package com.datasophon.api.controller.log;

import com.datasophon.api.controller.ApiController;
import com.datasophon.api.dto.log.K8sRuntimeEventQueryDTO;
import com.datasophon.api.dto.log.K8sRuntimeLogQueryDTO;
import com.datasophon.api.dto.log.ServiceRoleLogQueryDTO;
import com.datasophon.api.service.log.K8sProductService;
import com.datasophon.api.service.log.MasterLogService;
import com.datasophon.api.service.log.PhysicalProductService;
import com.datasophon.api.vo.k8s.K8sEventInfo;
import com.datasophon.common.utils.PropertyUtils;
import com.datasophon.common.utils.Result;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author zhanghuangbin
 */
@RestController
@RequestMapping("/log")
@Tag(name = "日志")
public class ScheduleLogController extends ApiController {
    
    @Autowired
    private MasterLogService masterLogService;
    
    @Autowired
    private PhysicalProductService vosProductService;
    
    @Autowired
    private K8sProductService k8sProductService;
    
    @RequestMapping("getScheduleLog")
    @Operation(summary = "获取安装调用日志")
    public Result getScheduleLog(@Schema(description = "日志行数") Integer lines) {
        String content = masterLogService.getMasterLog(getRows(lines));
        return Result.success(content);
    }
    
    @PostMapping("getVosServiceRoleRuntimeLog")
    @Operation(summary = "获取Vos运行日志")
    public Result getVosServiceRoleRuntimeLog(@RequestBody ServiceRoleLogQueryDTO dto) throws Exception {
        dto.setLines(getRows(dto.getLines()));
        String content = vosProductService.getVosServiceRoleRuntimeLog(dto);
        return Result.success(content);
    }
    
    @PostMapping("getK8sRuntimeLog")
    @Operation(summary = "获取K8S pod运行日志")
    public Result getK8sRuntimeLog(@RequestBody K8sRuntimeLogQueryDTO dto) {
        dto.setLines(getRows(dto.getLines()));
        String content = k8sProductService.getK8sRuntimeLog(dto);
        return Result.success(content);
    }
    
    @PostMapping("getK8sEvents")
    @Operation(summary = "获取K8S 事件")
    @ApiResponse(content = {
            @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = K8sEventInfo.class)))
    })
    public Result getK8sEvents(@RequestBody K8sRuntimeEventQueryDTO dto) {
        List<K8sEventInfo> events = k8sProductService.getK8sEvents(dto);
        return Result.success(events);
    }
    
    @RequestMapping("getK8sExecLog/{commandId}")
    @Operation(summary = "获取K8S服务执行日志")
    public Result getK8sExecLog(@PathVariable String commandId, @Schema(description = "日志行数") Integer lines) {
        String content = k8sProductService.getK8sExecLog(commandId, getRows(lines));
        return Result.success(content);
    }
    
    private int getRows(Integer lines) {
        return lines == null || lines <= 0 ? PropertyUtils.getInt("rows") : lines;
    }
}
