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
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.UniEngineService;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterInfoEntity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cn.hutool.core.util.ArrayUtil;

@RestController
@RequestMapping("cluster")
public class ClusterInfoController extends ApiController {
    
    @Autowired
    private ClusterInfoService clusterInfoService;
    
    @Autowired
    private UniEngineService uniEngineService;
    
    /**
     * 列表
     */
    @RequestMapping("/list")
    public Result list() {
        return Result.success(clusterInfoService.getClusterList());
    }
    
    /**
     * 配置好的集群列表
     */
    @RequestMapping("/runningClusterList")
    public Result runningClusterList() {
        return Result.success(clusterInfoService.runningClusterList());
    }
    
    /**
     * 信息
     */
    @RequestMapping("/info/{id}")
    public Result info(@PathVariable("id") Integer id) {
        ClusterInfoEntity clusterInfo = clusterInfoService.getById(id);
        return Result.success(clusterInfo);
    }
    
    /**
     * 保存
     */
    @RequestMapping("/save")
    @UserPermission
    public Result save(@RequestBody ClusterInfoEntity clusterInfo) {
        return Result.success(clusterInfoService.saveCluster(clusterInfo));
    }
    
    @RequestMapping("/updateClusterState")
    public Result updateClusterState(Integer clusterId, Integer clusterState) {
        clusterInfoService.updateClusterState(clusterId, clusterState);
        return Result.success();
    }
    
    /**
     * 修改
     */
    @RequestMapping("/update")
    @UserPermission
    public Result update(@RequestBody ClusterInfoEntity clusterInfo) {
        clusterInfoService.updateCluster(clusterInfo);
        return Result.success();
    }
    
    /**
     * 删除
     */
    @RequestMapping("/delete")
    @UserPermission
    public Result delete(@RequestBody Integer[] ids) {
        if (ArrayUtil.isNotEmpty(ids)) {
            clusterInfoService.deleteCluster(ids[0]);
        }
        
        return Result.success();
    }
    
    /**
     * 提供集群引擎信息给中台引擎
     */
    @RequestMapping("/engineInfo")
    public Result engineInfo() {
        return uniEngineService.getEngineInfo();
    }
    
}
