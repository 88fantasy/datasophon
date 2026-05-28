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

import com.datasophon.api.service.ClusterUserService;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterUser;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("cluster/user")
public class ClusterUserController extends ApiController {
    
    @Autowired
    private ClusterUserService clusterUserService;
    
    /**
     * 列表
     */
    @RequestMapping("/list")
    public Result list(Integer clusterId, String username, Integer page, Integer pageSize) {
        
        return clusterUserService.listPage(clusterId, username, page, pageSize);
    }
    
    /**
     * 信息
     */
    @RequestMapping("/info/{id}")
    public Result info(@PathVariable("id") Integer id) {
        ClusterUser clusterUser = clusterUserService.getById(id);
        
        return Result.success().put("clusterUser", clusterUser);
    }
    
    /**
     * 保存
     */
    @RequestMapping("/create")
    public Result save(Integer clusterId, String username, Integer mainGroupId, String otherGroupIds) {
        
        return clusterUserService.create(clusterId, username, mainGroupId, otherGroupIds);
    }
    
    /**
     * 修改
     */
    @RequestMapping("/update")
    public Result update(@RequestBody ClusterUser clusterUser) {
        
        clusterUserService.updateById(clusterUser);
        
        return Result.success();
    }
    
    /**
     * 删除
     */
    @RequestMapping("/delete")
    public Result delete(Integer id) {
        return clusterUserService.deleteClusterUser(id);
    }
    
}
