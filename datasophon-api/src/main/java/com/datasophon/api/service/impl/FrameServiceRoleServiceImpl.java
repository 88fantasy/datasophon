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

import com.datasophon.api.service.FrameServiceRoleService;
import com.datasophon.common.Constants;
import com.datasophon.common.cache.CacheUtils;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.entity.FrameServiceEntity;
import com.datasophon.dao.entity.FrameServiceRoleEntity;
import com.datasophon.dao.enums.RoleType;
import com.datasophon.dao.mapper.ClusterInfoMapper;
import com.datasophon.dao.mapper.ClusterServiceRoleInstanceMapper;
import com.datasophon.dao.mapper.FrameServiceMapper;
import com.datasophon.dao.mapper.FrameServiceRoleMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

@Service("frameServiceRoleService")
public class FrameServiceRoleServiceImpl extends ServiceImpl<FrameServiceRoleMapper, FrameServiceRoleEntity>
        implements
            FrameServiceRoleService {

    @Autowired
    private ClusterInfoMapper clusterInfoMapper;

    @Autowired
    private ClusterServiceRoleInstanceMapper roleInstanceMapper;

    @Autowired
    private FrameServiceMapper frameServiceMapper;

    @Override
    public List<FrameServiceRoleEntity> getServiceRoleList(Integer clusterId, List<Integer> serviceIds, Integer serviceRoleType) {
        List<FrameServiceRoleEntity> list = this.lambdaQuery()
                .eq(Objects.nonNull(serviceRoleType), FrameServiceRoleEntity::getServiceRoleType, serviceRoleType)
                .in(FrameServiceRoleEntity::getServiceId, serviceIds)
                .orderByAsc(FrameServiceRoleEntity::getSortNum)
                .list();
        // 校验是否已安装依赖的服务
        ClusterInfoEntity clusterInfo = clusterInfoMapper.selectById(clusterId);
        String key = clusterInfo.getClusterCode() + Constants.UNDERLINE + Constants.SERVICE_ROLE_HOST_MAPPING;

        for (FrameServiceRoleEntity role : list) {
            FrameServiceEntity frameServiceEntity = frameServiceMapper.selectById(role.getServiceId());
            List<ClusterServiceRoleInstanceEntity> roleInstanceList =
                    roleInstanceMapper.selectList(new QueryWrapper<ClusterServiceRoleInstanceEntity>()
                            .eq(Constants.SERVICE_NAME, frameServiceEntity.getServiceName())
                            .eq(Constants.SERVICE_ROLE_NAME, role.getServiceRoleName())
                            .eq(Constants.CLUSTER_ID, clusterId));
            if (Objects.nonNull(roleInstanceList) && roleInstanceList.size() > 0) {
                List<String> hosts = roleInstanceList.stream().map(e -> e.getHostname()).toList();
                role.setHosts(hosts);
            } else if (CacheUtils.containsKey(key)) {
                @SuppressWarnings("unchecked")
                Map<String, List<String>> map = (Map<String, List<String>>) CacheUtils.get(key);
                if (map.containsKey(role.getServiceRoleName())) {
                    role.setHosts(map.get(role.getServiceRoleName()));
                }
            }
        }
        return list;
    }

    @Override
    public FrameServiceRoleEntity getServiceRoleByServiceIdAndServiceRoleName(Integer serviceId, String roleName) {
        return this.lambdaQuery()
                .eq(FrameServiceRoleEntity::getServiceId, serviceId)
                .eq(FrameServiceRoleEntity::getServiceRoleName, roleName)
                .one();
    }

    @Override
    public FrameServiceRoleEntity getServiceRoleByFrameCodeAndServiceRoleName(String clusterFrame,
                                                                              String serviceRoleName) {
        return this.getOne(new QueryWrapper<FrameServiceRoleEntity>()
                .eq(Constants.FRAME_CODE_1, clusterFrame).eq(Constants.SERVICE_ROLE_NAME, serviceRoleName));
    }

    @Override
    public Result getNonMasterRoleList(Integer clusterId, String serviceIds) {
        List<String> ids = Arrays.asList(serviceIds.split(","));
        List<FrameServiceRoleEntity> list = this.lambdaQuery()
                .ne(FrameServiceRoleEntity::getServiceRoleType, RoleType.MASTER)
                .in(FrameServiceRoleEntity::getServiceId, ids)
                .orderByAsc(FrameServiceRoleEntity::getSortNum)
                .list();
        ClusterInfoEntity clusterInfo = clusterInfoMapper.selectById(clusterId);
        String key = clusterInfo.getClusterCode() + Constants.UNDERLINE + Constants.SERVICE_ROLE_HOST_MAPPING;
        List<String> hosts = new ArrayList<>();
        for (FrameServiceRoleEntity role : list) {
            FrameServiceEntity frameServiceEntity = frameServiceMapper.selectById(role.getServiceId());
            List<ClusterServiceRoleInstanceEntity> roleInstanceList =
                    roleInstanceMapper.selectList(new QueryWrapper<ClusterServiceRoleInstanceEntity>()
                            .eq(Constants.SERVICE_NAME, frameServiceEntity.getServiceName())
                            .eq(Constants.SERVICE_ROLE_NAME, role.getServiceRoleName())
                            .eq(Constants.CLUSTER_ID, clusterId));
            if (!roleInstanceList.isEmpty()) {
                hosts = roleInstanceList.stream().map(e -> e.getHostname()).toList();

            } else if (CacheUtils.containsKey(key)) {
                @SuppressWarnings("unchecked")
                Map<String, List<String>> map = (Map<String, List<String>>) CacheUtils.get(key);
                if (map.containsKey(role.getServiceRoleName())) {
                    hosts = map.get(role.getServiceRoleName());
                }
            }
            role.setHosts(hosts);
        }
        return Result.success(list);
    }

    @Override
    public Result getServiceRoleByServiceName(Integer clusterId, String serviceName) {
        if ("NODE".equals(serviceName)) {
            List<FrameServiceRoleEntity> list = new ArrayList<>();
            FrameServiceRoleEntity frameServiceRoleEntity = new FrameServiceRoleEntity();
            frameServiceRoleEntity.setServiceRoleName("node");
            list.add(frameServiceRoleEntity);
            return Result.success(list);
        }
        ClusterInfoEntity clusterInfoEntity = clusterInfoMapper.selectById(clusterId);
        FrameServiceEntity frameServiceEntity =
                frameServiceMapper.getServiceByFrameCodeAndServiceName(clusterInfoEntity.getClusterFrame(), serviceName);
        List<FrameServiceRoleEntity> list = this.lambdaQuery()
                .eq(FrameServiceRoleEntity::getServiceId, frameServiceEntity.getId())
                .orderByAsc(FrameServiceRoleEntity::getSortNum)
                .list();
        return Result.success(list);
    }

    @Override
    public List<FrameServiceRoleEntity> getAllServiceRoleList(Integer frameServiceId) {
        return this.lambdaQuery()
                .eq(FrameServiceRoleEntity::getServiceId, frameServiceId)
                .orderByAsc(FrameServiceRoleEntity::getSortNum)
                .list();
    }

    @Override
    public String getServiceName(String frameCode, String serviceRoleName) {
        return getBaseMapper().getServiceName(frameCode, serviceRoleName);
    }

}
