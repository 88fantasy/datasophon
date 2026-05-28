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
import com.datasophon.api.service.ClusterYarnQueueService;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterYarnQueue;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

@RestController
@RequestMapping("cluster/yarn/queue")
public class ClusterYarnQueueController extends ApiController {
    
    @Autowired
    private ClusterYarnQueueService clusterYarnQueueService;
    
    /**
     * 列表
     */
    @RequestMapping("/list")
    public Result list(Integer clusterId, Integer page, Integer pageSize) {
        return clusterYarnQueueService.listByPage(clusterId, page, pageSize);
    }
    
    /**
     * 刷新队列
     */
    @RequestMapping("/refreshQueues")
    public Result refreshQueues(Integer clusterId) throws Exception {
        return clusterYarnQueueService.refreshQueues(clusterId);
    }
    
    /**
     * 信息
     */
    @RequestMapping("/info/{id}")
    public Result info(@PathVariable("id") Integer id) {
        ClusterYarnQueue clusterYarnQueue = clusterYarnQueueService.getById(id);
        
        return Result.success().put("clusterYarnQueue", clusterYarnQueue);
    }
    
    /**
     * 保存
     */
    @RequestMapping("/save")
    public Result save(@RequestBody ClusterYarnQueue clusterYarnQueue) {
        List<ClusterYarnQueue> list = clusterYarnQueueService
                .list(new QueryWrapper<ClusterYarnQueue>().eq(Constants.QUEUE_NAME, clusterYarnQueue.getQueueName()));
        if (Objects.nonNull(list) && list.size() == 1) {
            return Result.error(Status.QUEUE_NAME_ALREADY_EXISTS.getMsg());
        }
        clusterYarnQueue.setCreateTime(new Date());
        clusterYarnQueueService.save(clusterYarnQueue);
        
        return Result.success();
    }
    
    /**
     * 修改
     */
    @RequestMapping("/update")
    public Result update(@RequestBody ClusterYarnQueue clusterYarnQueue) {
        
        clusterYarnQueueService.updateById(clusterYarnQueue);
        
        return Result.success();
    }
    
    /**
     * 删除
     */
    @RequestMapping("/delete")
    public Result delete(@RequestBody Integer[] ids) {
        clusterYarnQueueService.removeByIds(Arrays.asList(ids));
        
        return Result.success();
    }
    
}
