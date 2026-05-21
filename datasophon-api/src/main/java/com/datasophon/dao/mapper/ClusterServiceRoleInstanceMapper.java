/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.datasophon.dao.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.datasophon.common.Constants;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.NeedRestart;
import com.datasophon.dao.enums.ServiceRoleState;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Objects;

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
