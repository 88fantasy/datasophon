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


package com.datasophon.api.service;

import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterServiceInstanceEntity;

import java.util.List;

import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 集群服务表
 *
 * @author dygao2
 * @email gaodayu2022@163.com
 * @date 2022-04-24 16:25:17
 */
public interface ClusterServiceInstanceService extends IService<ClusterServiceInstanceEntity> {
    
    ClusterServiceInstanceEntity getServiceInstanceByClusterIdAndServiceName(Integer clusterId, String parentName);


    List<ClusterServiceInstanceEntity> getServiceInstanceByClusterId(Integer clusterId);



    List<ClusterServiceInstanceEntity> listAll(Integer clusterId);
    

    Result getServiceRoleType(Integer serviceInstanceId);
    
    Result configVersionCompare(Integer serviceInstanceId, Integer roleGroupId);
    
    Result delServiceInstance(Integer serviceInstanceId);
    
    List<ClusterServiceInstanceEntity> listRunningServiceInstance(Integer clusterId);
    
    boolean hasRunningRoleInstance(Integer serviceInstanceId);

}
