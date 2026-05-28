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

import com.datasophon.api.service.ClusterServiceInstanceConfigService;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterServiceInstanceConfigEntity;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("cluster/service/instance/config")
public class ClusterServiceInstanceConfigController extends ApiController {
    
    @Autowired
    private ClusterServiceInstanceConfigService clusterServiceInstanceConfigService;
    
    /**
     * 列表
     */
    @RequestMapping("/getConfigVersion")
    public Result getConfigVersion(Integer serviceInstanceId, Integer roleGroupId) {
        return clusterServiceInstanceConfigService.getConfigVersion(serviceInstanceId, roleGroupId);
    }
    
    /**
     * 信息
     */
    @RequestMapping("/info")
    public Result info(Integer serviceInstanceId, Integer version, Integer roleGroupId, Integer page,
                       Integer pageSize) {
        return clusterServiceInstanceConfigService.getServiceInstanceConfig(serviceInstanceId, version, roleGroupId,
                page, pageSize);
    }
    
    /**
     * 保存
     */
    @RequestMapping("/save")
    public Result save(@RequestBody ClusterServiceInstanceConfigEntity clusterServiceInstanceConfig) {
        clusterServiceInstanceConfigService.save(clusterServiceInstanceConfig);
        
        return Result.success();
    }
    
    /**
     * 修改
     */
    @RequestMapping("/update")
    public Result update(@RequestBody ClusterServiceInstanceConfigEntity clusterServiceInstanceConfig) {
        clusterServiceInstanceConfigService.updateById(clusterServiceInstanceConfig);
        
        return Result.success();
    }
    
    /**
     * 删除
     */
    @RequestMapping("/delete")
    public Result delete(@RequestBody Integer[] ids) {
        clusterServiceInstanceConfigService.removeByIds(Arrays.asList(ids));
        
        return Result.success();
    }
    
}
