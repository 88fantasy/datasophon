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
import com.datasophon.api.service.cmd.ClusterServiceCommandService;
import com.datasophon.api.service.dag.DAGService;
import com.datasophon.api.service.extrepo.ExtRepoInstallDelegateService;
import com.datasophon.api.service.extrepo.VosProductInstallService;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.utils.ConverterUtils;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.cmd.ClusterServiceCommandEntity;
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
    private ExtRepoInstallDelegateService extRepoInstallDelegateService;

    @Autowired
    private VosProductInstallService vosProductActionService;

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



    @UserPermission
    @RequestMapping("/generateGenericInstallCommand")
    @Operation(summary = "生成通用安装命令")
    public Result generateGenericInstallCommand(Integer clusterId, String serviceNames) {
        List<String> list = Arrays.asList(serviceNames.split(","));
        return Result.success(extRepoInstallDelegateService.generateGenericInstallCommand(clusterId, list));
    }



    @PostMapping("/generateAndExecSrvInstCmd")
    @UserPermission
    @Operation(summary = "执行服务通用操作(启停,不含安装)")
    public Result generateAndExecSrvInstCmd(Integer clusterId, String commandType, String serviceInstanceIds) {
        CommandType command = EnumUtil.fromString(CommandType.class, commandType);
        if (StringUtils.isNotBlank(serviceInstanceIds)) {
            List<Integer> ids = ConverterUtils.convertIds(serviceInstanceIds, Integer::parseInt);
            return Result.success(extRepoInstallDelegateService.generateAndExecSrvInstCmd(clusterId, command, ids));
        } else {
            return Result.error(Status.NO_SERVICE_EXECUTE.getMsg());
        }
    }


    @PostMapping("/generateAndSrvRoleCmd")
    @UserPermission
    public Result generateAndSrvRoleCmd(Integer clusterId, String commandType, Integer serviceInstanceId,
                                             String serviceRoleInstancesIds) {
        List<Integer> ids = ConverterUtils.convertIds(serviceRoleInstancesIds, Integer::parseInt);
        if (CollectionUtil.isNotEmpty(ids)) {
            CommandType command = EnumUtil.fromString(CommandType.class, commandType);
            return Result.success(vosProductActionService.generateAndExecSrvRoleCmd(clusterId, command, serviceInstanceId, ids));
        } else {
            return Result.error(Status.NO_SERVICE_EXECUTE.getMsg());
        }
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
