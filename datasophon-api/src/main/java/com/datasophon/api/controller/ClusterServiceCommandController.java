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

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.EnumUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.datasophon.api.enums.Status;
import com.datasophon.api.security.UserPermission;
import com.datasophon.api.service.ClusterServiceCommandService;
import com.datasophon.api.service.dag.DAGService;
import com.datasophon.api.service.extrepo.ExtRepoInstallService;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.utils.ConverterUtils;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterServiceCommandEntity;
import com.datasophon.dao.entity.dag.DagDefinitionEntity;
import io.swagger.v3.oas.annotations.Operation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("cluster/service/command")
public class ClusterServiceCommandController extends ApiController {
    
    @Autowired
    private ClusterServiceCommandService clusterServiceCommandService;


    @Autowired
    private ExtRepoInstallService extRepoInstallService;

    @Autowired
    private DAGService dagService;

    /**
     * 查询集群服务指令列表
     */
    @RequestMapping("/getServiceCommandlist")
    public Result list(Integer clusterId, Integer page, Integer pageSize) {
        return clusterServiceCommandService.getServiceCommandlist(clusterId, page, pageSize);
    }


    @RequestMapping("/findDagByPage")
    public Result findDagByPage(Integer clusterId, Integer page, Integer pageSize) {
        IPage<DagDefinitionEntity>  result = dagService.findDagByPage(clusterId, page, pageSize);
        return Result.success(result.getTotal(), result.getRecords());
    }

    /**
     * @deprecated 
     * @see #generateGenericInstallCommand(Integer, String)
     * 生成服务安装操作指令
     */
    @UserPermission
    @RequestMapping("/generateCommand")
    @Operation(deprecated = true)
    @Deprecated
    public Result generateCommand(Integer clusterId, String commandType, String serviceNames) {
        CommandType command = EnumUtil.fromString(CommandType.class, commandType);
        List<String> list = Arrays.asList(serviceNames.split(","));
        return Result.success(clusterServiceCommandService.generateCommand(clusterId, command, list));
    }

    @UserPermission
    @RequestMapping("/generateGenericInstallCommand")
    @Operation(summary = "生成通用安装命令")
    public Result generateGenericInstallCommand(Integer clusterId, String serviceNames) {
        List<String> list = Arrays.asList(serviceNames.split(","));
        return Result.success(extRepoInstallService.generateGenericInstallCommand(clusterId, list));
    }
    
    /**
     * 生成服务实例操作指令
     */
    @RequestMapping("/generateServiceCommand")
    @UserPermission
    public Result generateServiceCommand(Integer clusterId, String commandType, String serviceInstanceIds) {
        CommandType command = EnumUtil.fromString(CommandType.class, commandType);
        if (StringUtils.isNotBlank(serviceInstanceIds)) {
            List<String> ids = Arrays.asList(serviceInstanceIds.split(","));
            return clusterServiceCommandService.generateServiceCommand(clusterId, command, ids);
        } else {
            return Result.error(Status.NO_SERVICE_EXECUTE.getMsg());
        }
    }



    @PostMapping("/generateAndExecSrvInstCmd")
    @UserPermission
    @Operation(summary = "执行服务通用操作(启停,不含安装)")
    public Result generateAndExecSrvInstCmd(Integer clusterId, String commandType, String serviceInstanceIds) {
        CommandType command = EnumUtil.fromString(CommandType.class, commandType);
        if (StringUtils.isNotBlank(serviceInstanceIds)) {
            List<Integer> ids = ConverterUtils.convertIds(serviceInstanceIds, Integer::parseInt);
            return Result.success(extRepoInstallService.generateAndExecSrvInstCmd(clusterId, command, ids));
        } else {
            return Result.error(Status.NO_SERVICE_EXECUTE.getMsg());
        }
    }
    
    /**
     * 生成服务角色实例操作指令
     */
    @RequestMapping("/generateServiceRoleCommand")
    @UserPermission
    public Result generateServiceRoleCommand(Integer clusterId, String commandType, Integer serviceInstanceId,
                                             String serviceRoleInstancesIds) {
        CommandType command = EnumUtil.fromString(CommandType.class, commandType);
        List<String> ids = Arrays.asList(serviceRoleInstancesIds.split(","));
        return clusterServiceCommandService.generateServiceRoleCommand(clusterId, command, serviceInstanceId, ids);
        
    }

    @PostMapping("/generateAndSrvRoleCmd")
    @UserPermission
    public Result generateAndSrvRoleCmd(Integer clusterId, String commandType, Integer serviceInstanceId,
                                             String serviceRoleInstancesIds) {
        List<Integer> ids = ConverterUtils.convertIds(serviceRoleInstancesIds, Integer::parseInt);
        if (CollectionUtil.isEmpty(ids)) {
            CommandType command = EnumUtil.fromString(CommandType.class, commandType);
            return Result.success(extRepoInstallService.generateAndExecSrvRoleCmd(clusterId, command, serviceInstanceId, ids));
        } else {
            return Result.error(Status.NO_SERVICE_EXECUTE.getMsg());
        }
    }


    /**
     * 启动执行指令
     */
    @RequestMapping("/startExecuteCommand")
    @UserPermission
    public Result startExecuteCommand(Integer clusterId, String commandType, String commandIds) {
        clusterServiceCommandService.startExecuteCommand(clusterId, commandType, commandIds);
        return Result.success();
    }
    
    @RequestMapping("/cancelCommand")
    public Result cancelCommand(String commandId) {
        clusterServiceCommandService.cancelCommand(commandId);
        return Result.success();
    }
    
    /**
     * 信息
     */
    @RequestMapping("/info/{id}")
    public Result info(@PathVariable("id") Integer id) {
        ClusterServiceCommandEntity clusterServiceCommand = clusterServiceCommandService.getById(id);
        
        return Result.success().put("clusterServiceCommand", clusterServiceCommand);
    }
    
    /**
     * 保存
     */
    @RequestMapping("/save")
    public Result save(@RequestBody ClusterServiceCommandEntity clusterServiceCommand) {
        clusterServiceCommandService.save(clusterServiceCommand);
        
        return Result.success();
    }
    
    /**
     * 修改
     */
    @RequestMapping("/update")
    public Result update(@RequestBody ClusterServiceCommandEntity clusterServiceCommand) {
        clusterServiceCommandService.updateById(clusterServiceCommand);
        
        return Result.success();
    }
    
    /**
     * 删除
     */
    @RequestMapping("/delete")
    public Result delete(@RequestBody Integer[] ids) {
        clusterServiceCommandService.removeByIds(Arrays.asList(ids));
        
        return Result.success();
    }
    
}
