/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.datasophon.api.controller;

import cn.hutool.core.io.IoUtil;
import com.datasophon.api.dto.ddl.UpdateDdlDTO;
import com.datasophon.api.service.FrameServiceService;
import com.datasophon.api.service.ddl.DdlMetaService;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.FrameServiceEntity;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("frame/service")
public class FrameServiceController extends ApiController {
    
    @Autowired
    private FrameServiceService frameServiceService;
    

    @Autowired
    private DdlMetaService ddlMetaService;
    
    /**
     * 列表
     */
    @RequestMapping("/list")
    public Result list(Integer clusterId) {
        return Result.success(frameServiceService.getFrameServiceList(clusterId));
    }

    @RequestMapping("/listNewest")
    @Operation(summary = "获取组件列表(最高版本)")
    public Result listNewest(Integer clusterId, Boolean newest) {
        return Result.success(frameServiceService.listNewest(clusterId,newest));
    }


    /**
     * 列表
     */
    @RequestMapping("/listBasicFrameService")
    @Operation(summary = "获取框架基础组件列表")
    public Result listBasicFrameService(Integer clusterId) {
        return Result.success(frameServiceService.getBasicFrameServiceList(clusterId));
    }


    
    /**
     * 根据servce id列表查询服务
     */
    @RequestMapping("/getServiceListByServiceIds")
    public Result getServiceListByServiceIds(List<Integer> serviceIds) {
        return frameServiceService.getServiceListByServiceIds(serviceIds);
    }
    
    /**
     * 信息
     */
    @RequestMapping("/info/{id}")
    public Result info(@PathVariable("id") Integer id) {
        FrameServiceEntity frameVersionService = frameServiceService.getById(id);
        
        return Result.success().put("frameVersionService", frameVersionService);
    }
    
    /**
     * 保存
     */
    @RequestMapping("/save")
    public Result save(@RequestBody FrameServiceEntity frameVersionService) {
        frameServiceService.save(frameVersionService);
        
        return Result.success();
    }
    
    /**
     * 修改
     */
    @RequestMapping("/update")
    public Result update(@RequestBody FrameServiceEntity frameVersionService) {
        frameServiceService.updateById(frameVersionService);
        
        return Result.success();
    }
    
    /**
     * 删除服务组件
     */
    @RequestMapping("/delete/{id}")
    public Result delete(@PathVariable("id") Integer id) {
        frameServiceService.removeById(id);
        return Result.success();
    }


    @PostMapping("/updateDdl/{serviceId}")
    @Operation(summary = "更新service_ddl")
    public Result updateDdl(@PathVariable("serviceId") Integer serviceId, MultipartFile file) throws IOException {
        String content = IoUtil.read(file.getInputStream(), StandardCharsets.UTF_8);
        ddlMetaService.updateServiceVosDdl(serviceId, content);
        return Result.success();
    }

    @PostMapping("/updateDdl2/{serviceId}")
    @Operation(summary = "更新service_ddl2")
    public Result updateDdl2(@PathVariable("serviceId") Integer serviceId,@RequestBody UpdateDdlDTO dto) {
        ddlMetaService.updateServiceVosDdl(serviceId, dto.getContent());
        return Result.success();
    }



    @RequestMapping("/getServiceDdl/{serviceId}")
    @Operation(summary = "查看serviceDdl定义")
    public Result getServiceDdl(@PathVariable("serviceId") Integer serviceId) {
        return Result.success(ddlMetaService.getServiceVosDdl(serviceId));
    }
}
