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

import com.datasophon.api.service.ClusterAlertHistoryService;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterAlertHistory;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("cluster/alert/history")
public class ClusterAlertHistoryController extends ApiController {
    
    @Autowired
    private ClusterAlertHistoryService clusterAlertHistoryService;
    
    /**
     * 列表
     */
    @RequestMapping("/getAlertList")
    public Result getAlertList(Integer serviceInstanceId) {
        return clusterAlertHistoryService.getAlertList(serviceInstanceId);
    }
    
    /**
     * 列表
     */
    @RequestMapping("/getAllAlertList")
    public Result getAllAlertList(Integer clusterId, Integer page, Integer pageSize) {
        return clusterAlertHistoryService.getAllAlertList(clusterId, page, pageSize);
    }
    
    /**
     * 信息
     */
    @RequestMapping("/info/{id}")
    public Result info(@PathVariable("id") Integer id) {
        ClusterAlertHistory clusterAlertHistory = clusterAlertHistoryService.getById(id);
        
        return Result.success().put("clusterAlertHistory", clusterAlertHistory);
    }
    
    /**
     * 保存
     */
    @RequestMapping("/save")
    public Result save(@RequestBody String alertMessage) {
        clusterAlertHistoryService.saveAlertHistory(alertMessage);
        return Result.success();
    }
    
    /**
     * 修改
     */
    @RequestMapping("/update")
    public Result update(@RequestBody ClusterAlertHistory clusterAlertHistory) {
        
        clusterAlertHistoryService.updateById(clusterAlertHistory);
        
        return Result.success();
    }
    
    /**
     * 删除
     */
    @RequestMapping("/delete")
    public Result delete(@RequestBody Integer[] ids) {
        clusterAlertHistoryService.removeByIds(Arrays.asList(ids));
        
        return Result.success();
    }
    
}
