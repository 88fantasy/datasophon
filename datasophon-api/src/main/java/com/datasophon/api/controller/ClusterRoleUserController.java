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
import com.datasophon.api.service.ClusterRoleUserService;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterRoleUserEntity;

import java.util.Arrays;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("cluster/roleUser")
public class ClusterRoleUserController extends ApiController {
    
    @Autowired
    private ClusterRoleUserService clusterRoleUserService;
    
    /**
     * 列表
     */
    @RequestMapping("/list")
    public Result list(@RequestParam Map<String, Object> params) {
        
        return Result.success();
    }
    
    /**
     * 信息
     */
    @RequestMapping("/info/{id}")
    public Result info(@PathVariable("id") Integer id) {
        ClusterRoleUserEntity clusterRoleUser = clusterRoleUserService.getById(id);
        
        return Result.success().put("clusterRoleUser", clusterRoleUser);
    }
    
    /**
     * 保存
     */
    @RequestMapping("/saveClusterManager")
    @UserPermission
    public Result saveClusterManager(Integer clusterId, String userIds) {
        return clusterRoleUserService.saveClusterManager(clusterId, userIds);
    }
    
    /**
     * 修改
     */
    @RequestMapping("/update")
    public Result update(@RequestBody ClusterRoleUserEntity clusterRoleUser) {
        clusterRoleUserService.updateById(clusterRoleUser);
        
        return Result.success();
    }
    
    /**
     * 删除
     */
    @RequestMapping("/delete")
    public Result delete(@RequestBody Integer[] ids) {
        clusterRoleUserService.removeByIds(Arrays.asList(ids));
        
        return Result.success();
    }
    
}
