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

import com.datasophon.api.service.ClusterQueueCapacityService;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterQueueCapacity;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("cluster/queue/capacity")
public class ClusterQueueCapacityController extends ApiController {
    
    @Autowired
    private ClusterQueueCapacityService clusterQueueCapacityService;
    
    /**
     * 列表
     */
    @RequestMapping("/list")
    public Result list(Integer clusterId) {
        
        return clusterQueueCapacityService.listCapacityQueue(clusterId);
        
    }
    
    /**
     * 信息
     */
    @RequestMapping("/info/{id}")
    public Result info(@PathVariable("id") Integer id) {
        ClusterQueueCapacity clusterQueueCapacity = clusterQueueCapacityService.getById(id);
        
        return Result.success().put("clusterQueueCapacity", clusterQueueCapacity);
    }
    
    /**
     * 保存
     */
    @RequestMapping("/save")
    public Result save(@RequestBody ClusterQueueCapacity clusterQueueCapacity) {
        clusterQueueCapacityService.save(clusterQueueCapacity);
        
        return Result.success();
    }
    
    /**
     * 修改
     */
    @RequestMapping("/update")
    public Result update(@RequestBody ClusterQueueCapacity clusterQueueCapacity) {
        
        clusterQueueCapacityService.updateById(clusterQueueCapacity);
        
        return Result.success();
    }
    
    /**
     * 删除
     */
    @RequestMapping("/delete")
    public Result delete(Integer id) {
        clusterQueueCapacityService.removeById(id);
        
        return Result.success();
    }
    
    /**
     * 删除
     */
    @RequestMapping("/refreshToYarn")
    public Result refreshToYarn(Integer clusterId) throws Exception {
        return clusterQueueCapacityService.refreshToYarn(clusterId);
    }
}
