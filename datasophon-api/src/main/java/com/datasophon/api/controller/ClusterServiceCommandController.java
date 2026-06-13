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

import com.datasophon.api.enums.Status;
import com.datasophon.api.security.UserPermission;
import com.datasophon.api.service.cmd.ClusterServiceCommandService;
import com.datasophon.api.service.dag.DAGService;
import com.datasophon.api.service.extrepo.ExtRepoInstallDelegateService;
import com.datasophon.api.service.extrepo.PhysicalProductInstallService;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.utils.ConverterUtils;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.cmd.ClusterServiceCommandEntity;
import com.datasophon.dao.entity.dag.DagDefinitionEntity;

import io.swagger.v3.oas.annotations.Operation;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.metadata.IPage;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.EnumUtil;

@RestController
@RequestMapping("cluster/service/command")
public class ClusterServiceCommandController extends ApiController {
    
    @Autowired
    private ClusterServiceCommandService clusterServiceCommandService;
    
    @Autowired
    private ExtRepoInstallDelegateService extRepoInstallDelegateService;
    
    @Autowired
    private PhysicalProductInstallService physicalProductActionService;
    
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
        IPage<DagDefinitionEntity> result = dagService.findDagByPage(clusterId, page, pageSize);
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
            return Result.success(physicalProductActionService.generateAndExecSrvRoleCmd(clusterId, command, serviceInstanceId, ids));
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
