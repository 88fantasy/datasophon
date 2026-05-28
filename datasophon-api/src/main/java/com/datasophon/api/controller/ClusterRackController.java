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

import com.datasophon.api.service.ClusterRackService;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterRack;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("cluster/rack")
public class ClusterRackController extends ApiController {
    
    @Autowired
    private ClusterRackService clusterRackService;
    
    /**
     * 列表
     */
    @RequestMapping("/list")
    public Result list(Integer clusterId) {
        List<ClusterRack> list = clusterRackService.queryClusterRack(clusterId);
        return Result.success(list);
    }
    
    /**
     * 信息
     */
    @RequestMapping("/info/{id}")
    public Result info(@PathVariable("id") Integer id) {
        ClusterRack clusterRack = clusterRackService.getById(id);
        
        return Result.success().put("clusterRack", clusterRack);
    }
    
    /**
     * 保存
     */
    @RequestMapping("/save")
    public Result save(Integer clusterId, String rack) {
        clusterRackService.saveRack(clusterId, rack);
        return Result.success();
    }
    
    /**
     * 删除
     */
    @RequestMapping("/delete")
    public Result delete(Integer clusterId, Integer rackId) {
        return clusterRackService.deleteRack(rackId);
    }
    
}
