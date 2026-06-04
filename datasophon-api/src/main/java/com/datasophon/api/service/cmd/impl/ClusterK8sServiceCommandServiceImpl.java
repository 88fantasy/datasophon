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

package com.datasophon.api.service.cmd.impl;

import com.datasophon.api.service.cmd.ClusterK8sServiceCommandService;
import com.datasophon.dao.entity.cmd.ClusterK8sServiceCommandEntity;
import com.datasophon.dao.mapper.cmd.ClusterK8sServiceCommandMapper;

import org.apache.commons.lang3.StringUtils;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

@Service("clusterK8sServiceCommandService")
public class ClusterK8sServiceCommandServiceImpl extends ServiceImpl<ClusterK8sServiceCommandMapper, ClusterK8sServiceCommandEntity>
        implements
            ClusterK8sServiceCommandService {
    
    @Override
    public ClusterK8sServiceCommandEntity getCommandById(String commandId) {
        return lambdaQuery().eq(ClusterK8sServiceCommandEntity::getCommandId, commandId).one();
    }
    
    @Override
    public IPage<ClusterK8sServiceCommandEntity> findCommandByPage(Integer clusterId, String serviceName, Integer page, Integer pageSize) {
        return lambdaQuery().eq(clusterId != null, ClusterK8sServiceCommandEntity::getClusterId, clusterId)
                .eq(StringUtils.isNotBlank(serviceName), ClusterK8sServiceCommandEntity::getServiceName, serviceName)
                .orderByDesc(ClusterK8sServiceCommandEntity::getCreateTime)
                .page(new Page<>(page, pageSize));
    }
    
}
