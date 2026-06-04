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

import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterHostDO;

import org.apache.commons.lang3.StringUtils;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

@Slf4j
@RestController
@RequestMapping("cluster/host")
public class ClusterHostController extends ApiController {
    
    @Autowired
    private ClusterHostService clusterHostService;
    
    /**
     * 查询集群所有主机
     */
    @RequestMapping("/all")
    public Result all(Integer clusterId) {
        List<ClusterHostDO> list =
                clusterHostService.list(new QueryWrapper<ClusterHostDO>().eq(Constants.CLUSTER_ID, clusterId)
                        .eq(Constants.MANAGED, 1)
                        .orderByAsc(Constants.HOSTNAME));
        return Result.success(list);
    }
    
    /**
     * 查询集群所有主机
     */
    @RequestMapping("/list")
    public Result list(Integer clusterId, String hostname, String ip, String cpuArchitecture, Integer hostState,
                       String orderField, String orderType, Integer page, Integer pageSize) {
        return clusterHostService.listByPage(clusterId, hostname, ip, cpuArchitecture, hostState, orderField, orderType,
                page, pageSize);
        
    }
    
    @RequestMapping("/getRoleListByHostname")
    public Result getRoleListByHostname(Integer clusterId, String hostname) {
        return clusterHostService.getRoleListByHostname(clusterId, hostname);
        
    }
    
    @RequestMapping("/getRack")
    public Result getRack(Integer clusterId) {
        return clusterHostService.getRack(clusterId);
        
    }
    
    @RequestMapping("/assignRack")
    public Result assignRack(Integer clusterId, String rack, String hostIds) {
        return clusterHostService.assignRack(clusterId, rack, hostIds);
        
    }
    
    /**
     * 信息
     */
    @RequestMapping("/info/{id}")
    public Result info(@PathVariable("id") Integer id) {
        ClusterHostDO clusterHost = clusterHostService.getById(id);
        
        return Result.success().put(Constants.DATA, clusterHost);
    }
    
    /**
     * 保存
     */
    @RequestMapping("/save")
    public Result save(@RequestBody ClusterHostDO clusterHost) {
        clusterHostService.save(clusterHost);
        
        return Result.success();
    }
    
    /**
     * 修改
     */
    @RequestMapping("/update")
    public Result update(@RequestBody ClusterHostDO clusterHost) {
        clusterHostService.updateById(clusterHost);
        
        return Result.success();
    }
    
    /**
     * 删除
     */
    @RequestMapping("/delete")
    public Result delete(String hostIds) {
        if (StringUtils.isBlank(hostIds)) {
            return Result.error("请选择移除的主机!");
        }
        try {
            return clusterHostService.deleteHosts(hostIds);
        } catch (Exception e) {
            log.warn("移除主机异常.", e);
            return Result.error("移除主机异常, Cause: " + e.getMessage());
        }
    }
    
}
