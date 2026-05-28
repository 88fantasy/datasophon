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

import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("cluster/service/role/instance")
public class ClusterServiceRoleInstanceController extends ApiController {
    
    @Autowired
    private ClusterServiceRoleInstanceService clusterServiceRoleInstanceService;
    
    /**
     * 列表
     */
    @RequestMapping("/list")
    public Result list(Integer serviceInstanceId, String hostname, Integer serviceRoleState, String serviceRoleName,
                       Integer roleGroupId, Integer page, Integer pageSize) {
        return clusterServiceRoleInstanceService.listAll(serviceInstanceId, hostname, serviceRoleState, serviceRoleName,
                roleGroupId, page, pageSize);
    }
    
    /**
     * 信息
     */
    @RequestMapping("/getLog")
    public Result getLog(Integer serviceRoleInstanceId) throws Exception {
        return clusterServiceRoleInstanceService.getLog(serviceRoleInstanceId);
    }
    
    /**
     * 退役
     */
    @RequestMapping("/decommissionNode")
    public Result decommissionNode(String serviceRoleInstanceIds, String serviceName) throws Exception {
        return clusterServiceRoleInstanceService.decommissionNode(serviceRoleInstanceIds, serviceName);
    }
    
    /**
     * 重启过时服务
     */
    @RequestMapping("/restartObsoleteService")
    public Result restartObsoleteService(Integer roleGroupId) throws Exception {
        return clusterServiceRoleInstanceService.restartObsoleteService(roleGroupId);
    }
    
    /**
     * 保存
     */
    @RequestMapping("/save")
    public Result save(@RequestBody ClusterServiceRoleInstanceEntity clusterServiceRoleInstance) {
        clusterServiceRoleInstanceService.save(clusterServiceRoleInstance);
        
        return Result.success();
    }
    
    /**
     * 修改
     */
    @RequestMapping("/update")
    public Result update(@RequestBody ClusterServiceRoleInstanceEntity clusterServiceRoleInstance) {
        clusterServiceRoleInstanceService.updateById(clusterServiceRoleInstance);
        return Result.success();
    }
    
    /**
     * 删除
     */
    @RequestMapping("/delete")
    public Result delete(String serviceRoleInstancesIds) {
        List<String> idList = Arrays.asList(serviceRoleInstancesIds.split(","));
        return clusterServiceRoleInstanceService.deleteServiceRole(idList);
    }
    
}
