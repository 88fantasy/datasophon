package com.datasophon.api.controller;

import com.datasophon.api.service.log.MasterLogService;
import com.datasophon.common.utils.PropertyUtils;
import com.datasophon.common.utils.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @author zhanghuangbin
 */
@RestController
@RequestMapping("/log")
public class ScheduleLogController extends ApiController {

    @Autowired
    private MasterLogService masterLogService;

    @RequestMapping("getScheduleLog")
    @Operation(summary = "获取安装调用日志")
    public Result getScheduleLog(@Schema(name = "日志行数") Integer lines) {
        int rows = lines == null || lines <= 0 ? PropertyUtils.getInt("rows") : lines;
        String content = masterLogService.getMasterLog(rows);
        return Result.success(content);
    }
}
