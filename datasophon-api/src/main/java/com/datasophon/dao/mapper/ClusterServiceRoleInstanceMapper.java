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

package com.datasophon.dao.mapper;

import com.datasophon.common.Constants;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.NeedRestart;
import com.datasophon.dao.enums.ServiceRoleState;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Objects;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

@Mapper
public interface ClusterServiceRoleInstanceMapper extends BaseMapper<ClusterServiceRoleInstanceEntity> {
    
    void updateToNeedRestart(@Param("roleGroupId") Integer roleGroupId);
    
    void updateToNeedRestartByHost(@Param("hostName") String hostName);
    
    default List<ClusterServiceRoleInstanceEntity> getObsoleteService(Integer serviceInstanceId) {
        return selectList(Wrappers.<ClusterServiceRoleInstanceEntity>lambdaQuery()
                .eq(ClusterServiceRoleInstanceEntity::getServiceId, serviceInstanceId)
                .eq(ClusterServiceRoleInstanceEntity::getNeedRestart, NeedRestart.YES));
    }
    
    default List<ClusterServiceRoleInstanceEntity> getStoppedRoleInstanceOnHost(Integer clusterId, String hostname,
                                                                                ServiceRoleState state) {
        return selectList(Wrappers.<ClusterServiceRoleInstanceEntity>lambdaQuery()
                .eq(ClusterServiceRoleInstanceEntity::getClusterId, clusterId)
                .eq(ClusterServiceRoleInstanceEntity::getHostname, hostname)
                .eq(ClusterServiceRoleInstanceEntity::getServiceRoleState, state));
    }
    
    default List<ClusterServiceRoleInstanceEntity> getServiceRoleInstanceListByServiceIdAndRoleState(Integer serviceId,
                                                                                                     ServiceRoleState stop) {
        return selectList(Wrappers.<ClusterServiceRoleInstanceEntity>lambdaQuery()
                .eq(ClusterServiceRoleInstanceEntity::getServiceId, serviceId)
                .eq(ClusterServiceRoleInstanceEntity::getServiceRoleState, stop));
    }
    
    default void reomveRoleInstance(Integer serviceInstanceId) {
        delete(Wrappers.<ClusterServiceRoleInstanceEntity>lambdaQuery()
                .eq(ClusterServiceRoleInstanceEntity::getServiceId, serviceInstanceId)
                .eq(ClusterServiceRoleInstanceEntity::getServiceRoleState, ServiceRoleState.STOP));
    }
    
    default ClusterServiceRoleInstanceEntity getKAdminRoleIns(Integer clusterId) {
        return selectOne(new QueryWrapper<ClusterServiceRoleInstanceEntity>()
                .eq(Constants.CLUSTER_ID, clusterId)
                .eq(Constants.SERVICE_ROLE_NAME, "KAdmin"));
    }
    
    default List<ClusterServiceRoleInstanceEntity> listServiceRoleByName(String name) {
        return selectList(new QueryWrapper<ClusterServiceRoleInstanceEntity>()
                .eq(Constants.SERVICE_ROLE_NAME, name));
    }
    
    default ClusterServiceRoleInstanceEntity getServiceRoleInsByHostAndName(String hostName, String serviceRoleName) {
        return selectOne(new QueryWrapper<ClusterServiceRoleInstanceEntity>()
                .eq(Constants.HOSTNAME, hostName)
                .eq(Constants.SERVICE_ROLE_NAME, serviceRoleName));
    }
    
    default List<ClusterServiceRoleInstanceEntity> listRoleIns(String hostname, String serviceName) {
        return selectList(Wrappers.<ClusterServiceRoleInstanceEntity>lambdaQuery()
                .eq(ClusterServiceRoleInstanceEntity::getHostname, hostname)
                .eq(ClusterServiceRoleInstanceEntity::getServiceName, serviceName));
    }
    
    default List<ClusterServiceRoleInstanceEntity> getServiceRoleInstanceListByServiceId(int id) {
        return selectList(Wrappers.<ClusterServiceRoleInstanceEntity>lambdaQuery()
                .eq(ClusterServiceRoleInstanceEntity::getServiceId, id));
    }
    
    default List<ClusterServiceRoleInstanceEntity> getServiceRoleInstanceListByClusterId(int clusterId) {
        return selectList(Wrappers.<ClusterServiceRoleInstanceEntity>lambdaQuery()
                .eq(ClusterServiceRoleInstanceEntity::getClusterId, clusterId));
    }
    
    default List<ClusterServiceRoleInstanceEntity> getRunningServiceRoleInstanceListByServiceId(Integer serviceInstanceId) {
        return selectList(Wrappers.<ClusterServiceRoleInstanceEntity>lambdaQuery()
                .eq(ClusterServiceRoleInstanceEntity::getServiceId, serviceInstanceId)
                .eq(ClusterServiceRoleInstanceEntity::getServiceRoleState, ServiceRoleState.RUNNING));
    }
    
    default ClusterServiceRoleInstanceEntity getOneServiceRole(String name, String hostname, Integer id) {
        List<ClusterServiceRoleInstanceEntity> list = selectList(new QueryWrapper<ClusterServiceRoleInstanceEntity>()
                .eq(Constants.SERVICE_ROLE_NAME, name)
                .eq(StringUtils.isNotBlank(hostname), Constants.HOSTNAME, hostname)
                .eq(Constants.CLUSTER_ID, id));
        if (Objects.nonNull(list) && !list.isEmpty()) {
            return list.get(0);
        }
        return null;
    }
    
    default List<ClusterServiceRoleInstanceEntity> listStoppedServiceRoleListByHostnameAndClusterId(String hostname,
                                                                                                    Integer clusterId) {
        return selectList(Wrappers.<ClusterServiceRoleInstanceEntity>lambdaQuery()
                .eq(ClusterServiceRoleInstanceEntity::getClusterId, clusterId)
                .eq(ClusterServiceRoleInstanceEntity::getHostname, hostname)
                .eq(ClusterServiceRoleInstanceEntity::getServiceRoleState, ServiceRoleState.STOP));
    }
    
    default List<ClusterServiceRoleInstanceEntity> getServiceRoleListByHostnameAndClusterId(String hostname,
                                                                                            Integer clusterId) {
        return selectList(new QueryWrapper<ClusterServiceRoleInstanceEntity>()
                .eq(Constants.CLUSTER_ID, clusterId)
                .eq(Constants.HOSTNAME, hostname));
    }
    
    default List<ClusterServiceRoleInstanceEntity> getServiceRoleInstanceListByClusterIdAndRoleName(Integer clusterId,
                                                                                                    String roleName) {
        return selectList(new QueryWrapper<ClusterServiceRoleInstanceEntity>()
                .eq(Constants.CLUSTER_ID, clusterId).eq(Constants.SERVICE_ROLE_NAME, roleName));
    }
}
