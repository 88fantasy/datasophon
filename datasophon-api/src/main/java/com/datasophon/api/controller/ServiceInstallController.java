/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.datasophon.api.controller;

import com.datasophon.api.security.UserPermission;
import com.datasophon.api.service.ServiceInstallService;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceRoleHostMapping;
import com.datasophon.common.utils.ConverterUtils;
import com.datasophon.common.utils.Result;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.fastjson2.JSONArray;

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
     * 服务部署总览
     */
    @RequestMapping("/checkServiceDependency")
    public Result checkServiceDependency(Integer clusterId, String serviceIds) {
        return serviceInstallService.checkServiceDependency(clusterId, ConverterUtils.convertIds(serviceIds, Integer::parseInt));
    }
    
}
