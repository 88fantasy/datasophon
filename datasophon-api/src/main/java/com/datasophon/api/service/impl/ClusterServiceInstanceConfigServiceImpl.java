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


package com.datasophon.api.service.impl;

import com.datasophon.api.service.ClusterServiceInstanceConfigService;
import com.datasophon.api.service.ClusterServiceRoleGroupConfigService;
import com.datasophon.common.Constants;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterServiceInstanceConfigEntity;
import com.datasophon.dao.entity.ClusterServiceRoleGroupConfig;
import com.datasophon.dao.mapper.ClusterServiceInstanceConfigMapper;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

@Service("clusterServiceInstanceConfigService")
public class ClusterServiceInstanceConfigServiceImpl
        extends
            ServiceImpl<ClusterServiceInstanceConfigMapper, ClusterServiceInstanceConfigEntity>
        implements
            ClusterServiceInstanceConfigService {
    
    @Autowired
    private ClusterServiceRoleGroupConfigService roleGroupConfigService;
    
    @Override
    public Result getServiceInstanceConfig(Integer serviceInstanceId, Integer version, Integer roleGroupId,
                                           Integer page, Integer pageSize) {
        ClusterServiceRoleGroupConfig roleGroupConfig =
                roleGroupConfigService.getConfigByRoleGroupIdAndVersion(roleGroupId, version);
        if (Objects.nonNull(roleGroupConfig)) {
            String configJson = roleGroupConfig.getConfigJson();
            List<ServiceConfig> serviceConfigs = JSON.parseArray(configJson, ServiceConfig.class);
            return Result.success(serviceConfigs);
        }
        return Result.success();
    }
    
    @Override
    public ClusterServiceInstanceConfigEntity getServiceConfigByServiceId(Integer id) {
        return this.lambdaQuery()
                .eq(ClusterServiceInstanceConfigEntity::getServiceId, id)
                .orderByDesc(ClusterServiceInstanceConfigEntity::getConfigVersion)
                .last("limit 1")
                .one();
    }
    
    @Override
    public Result getConfigVersion(Integer serviceInstanceId, Integer roleGroupId) {
        
        List<ClusterServiceRoleGroupConfig> list =
                roleGroupConfigService.list(new QueryWrapper<ClusterServiceRoleGroupConfig>()
                        .eq(Constants.ROLE_GROUP_ID, roleGroupId)
                        .orderByDesc(Constants.CONFIG_VERSION));
        List<Integer> versions = list.stream().map(e -> e.getConfigVersion()).collect(Collectors.toList());
        return Result.success(versions);
    }
}
