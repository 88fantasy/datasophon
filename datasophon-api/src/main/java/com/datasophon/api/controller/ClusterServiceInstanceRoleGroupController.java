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

import com.datasophon.api.service.ClusterServiceInstanceRoleGroupService;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterServiceInstanceRoleGroup;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

@RestController
@RequestMapping("cluster/service/instance/role/group")
public class ClusterServiceInstanceRoleGroupController extends ApiController {
    
    @Autowired
    private ClusterServiceInstanceRoleGroupService clusterServiceInstanceRoleGroupService;
    
    /**
     * 列表
     */
    @RequestMapping("/list")
    public Result list(Integer serviceInstanceId) {
        List<ClusterServiceInstanceRoleGroup> list =
                clusterServiceInstanceRoleGroupService.list(new QueryWrapper<ClusterServiceInstanceRoleGroup>()
                        .eq(Constants.SERVICE_INSTANCE_ID, serviceInstanceId));
        return Result.success(list);
    }
    
    /**
     * 信息
     */
    @RequestMapping("/info/{id}")
    public Result info(@PathVariable("id") Integer id) {
        ClusterServiceInstanceRoleGroup clusterServiceInstanceRoleGroup =
                clusterServiceInstanceRoleGroupService.getById(id);
        
        return Result.success().put("clusterServiceInstanceRoleGroup", clusterServiceInstanceRoleGroup);
    }
    
    /**
     * 保存
     */
    @RequestMapping("/save")
    public Result save(Integer serviceInstanceId, Integer roleGroupId, String roleGroupName) {
        clusterServiceInstanceRoleGroupService.saveRoleGroup(serviceInstanceId, roleGroupId, roleGroupName);
        return Result.success();
    }
    
    /**
     * 分配角色组
     */
    @RequestMapping("/bind")
    public Result bind(String roleInstanceIds, Integer roleGroupId) {
        return clusterServiceInstanceRoleGroupService.bind(roleInstanceIds, roleGroupId);
    }
    
    /**
     * 修改
     */
    @RequestMapping("/rename")
    public Result update(Integer roleGroupId, String roleGroupName) {
        
        return clusterServiceInstanceRoleGroupService.rename(roleGroupId, roleGroupName);
        
    }
    
    /**
     * 删除
     */
    @RequestMapping("/delete")
    public Result delete(Integer roleGroupId) {
        // clusterServiceInstanceRoleGroupService.removeByIds(Arrays.asList(ids));
        
        return clusterServiceInstanceRoleGroupService.deleteRoleGroup(roleGroupId);
    }
    
}
