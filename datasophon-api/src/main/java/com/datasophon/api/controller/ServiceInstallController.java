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

import com.alibaba.fastjson.JSONArray;
import com.datasophon.api.security.UserPermission;
import com.datasophon.api.service.ServiceInstallService;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceRoleHostMapping;
import com.datasophon.common.utils.ConverterUtils;
import com.datasophon.common.utils.Result;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("service/install")
public class ServiceInstallController extends ApiController {

    @Autowired
    ServiceInstallService serviceInstallService;

    /**
     * 根据服务名称查询服务配置选项
     */
    @RequestMapping("/getServiceConfigOption")
    public Result getServiceConfigOption(Integer clusterId, String serviceName) {
        return Result.success(serviceInstallService.getServiceConfigOption(clusterId, serviceName));
    }

    /**
     *
     */
    @RequestMapping("/getServiceConfigFromDdl")
    @Operation(summary = "从服务定义中获取配置项")
    public Result getServiceConfigFromDdl(Integer clusterId, String serviceName) {
        return Result.success(serviceInstallService.getServiceConfigFromDdl(clusterId, serviceName));
    }


    /**
     * 保存服务配置
     */
    @RequestMapping("/saveServiceConfig")
    @UserPermission
    public Result saveServiceConfig(Integer clusterId, String serviceName, String serviceConfig, Integer roleGroupId) {
        JSONArray jsonArray = JSONArray.parseArray(serviceConfig);
        List<ServiceConfig> list = jsonArray.toJavaList(ServiceConfig.class);
        serviceInstallService.saveServiceConfig(clusterId, serviceName, list, roleGroupId);
        return Result.success();
    }

    /**
     * 保存服务角色与主机对应关系
     */
    @RequestMapping("/saveServiceRoleHostMapping/{clusterId}")
    public Result saveServiceRoleHostMapping(@RequestBody List<ServiceRoleHostMapping> list,
                                             @PathVariable("clusterId") Integer clusterId) {
        serviceInstallService.saveServiceRoleHostMapping(clusterId, list);
        return Result.success();
    }

    /**
     * 查询服务角色与主机对应关系
     */
    @RequestMapping("/getServiceRoleHostMapping")
    @UserPermission
    public Result getServiceRoleHostMapping(Integer clusterId) {
        return serviceInstallService.getServiceRoleHostMapping(clusterId);
    }

    /**
     * 服务部署总览
     */
    @RequestMapping("/getServiceRoleDeployOverview")
    public Result getServiceRoleDeployOverview(Integer clusterId) {
        return serviceInstallService.getServiceRoleDeployOverview(clusterId);
    }

    /**
     * 下载模板
     */
    @GetMapping("/downloadTemplate")
    public void downloadResource(String templateName, HttpServletResponse response) throws IOException {
        serviceInstallService.downloadTemplate(templateName, response);
    }

    /**
     * 下载额外资源
     */
    @GetMapping("/downloadResource")
    public void downloadResource(String frameCode, String serviceRoleName,
                                 String resource,
                                 HttpServletResponse response) throws Exception {
        
        serviceInstallService.downloadResource(frameCode, serviceRoleName, resource, response);
    }
    
    /**
     * 服务部署总览
     */
    @RequestMapping("/checkServiceDependency")
    public Result checkServiceDependency(Integer clusterId, String serviceIds) {
        return serviceInstallService.checkServiceDependency(clusterId, ConverterUtils.convertIds(serviceIds, Integer::parseInt));
    }

}
