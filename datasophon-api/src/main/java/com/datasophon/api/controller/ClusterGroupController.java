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

import com.datasophon.api.service.ClusterGroupService;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterGroup;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("cluster/group")
public class ClusterGroupController extends ApiController {
    
    @Autowired
    private ClusterGroupService clusterGroupService;
    
    /**
     * 列表
     */
    @RequestMapping("/list")
    public Result list(String groupName, Integer clusterId, Integer page, Integer pageSize) {
        
        return clusterGroupService.listPage(groupName, clusterId, page, pageSize);
    }
    
    /**
     * 信息
     */
    @RequestMapping("/info/{id}")
    public Result info(@PathVariable("id") Integer id) {
        ClusterGroup clusterGroup = clusterGroupService.getById(id);
        
        return Result.success().put("clusterGroup", clusterGroup);
    }
    
    /**
     * 保存
     */
    @RequestMapping("/save")
    public Result save(Integer clusterId, String groupName) {
        return clusterGroupService.saveClusterGroup(clusterId, groupName);
    }
    
    /**
     * 删除用户组
     */
    @RequestMapping("/delete")
    public Result delete(Integer id) {
        return clusterGroupService.deleteUserGroup(id);
    }
    
    /**
     * 刷新用户组到主机
     */
    @RequestMapping("/refreshUserGroupToHost")
    public Result refreshUserGroupToHost(Integer clusterId) {
        
        clusterGroupService.refreshUserGroupToHost(clusterId);
        
        return Result.success();
    }
    
}
