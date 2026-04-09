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

package com.datasophon.api.controller.cmd;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.datasophon.api.controller.ApiController;
import com.datasophon.api.service.cmd.ClusterK8sServiceCommandService;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.cmd.ClusterK8sServiceCommandEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * K8s 服务命令执行记录
 *
 * @author zhanghuangbin
 * @date 2026-03-30
 */
@RestController
@RequestMapping("cluster/k8sService/command")
@Tag(name = "K8s 服务命令管理")
public class ClusterK8sServiceCommandController extends ApiController {

    @Autowired
    private ClusterK8sServiceCommandService clusterK8sServiceCommandService;

    /**
     * 分页查询 K8s 服务命令列表
     */
    @PostMapping("findCommandByPage")
    @Operation(summary = "分页查询 K8s 服务命令列表")
    @ApiResponse(
            content = {@Content(mediaType = "application/json", schema = @Schema(implementation = ClusterK8sServiceCommandEntity.class))}
    )
    public Result findCommandByPage(Integer clusterId, String serviceName, Integer page, Integer pageSize) {
        IPage<ClusterK8sServiceCommandEntity> result = clusterK8sServiceCommandService.findCommandByPage(clusterId, serviceName, page, pageSize);
        return Result.success(result.getTotal(), result.getRecords());
    }
}
