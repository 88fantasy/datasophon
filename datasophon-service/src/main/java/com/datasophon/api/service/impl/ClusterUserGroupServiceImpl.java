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

package com.datasophon.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datasophon.api.service.ClusterUserGroupService;
import com.datasophon.common.Constants;
import com.datasophon.dao.entity.ClusterGroup;
import com.datasophon.dao.entity.ClusterUser;
import com.datasophon.dao.entity.ClusterUserGroup;
import com.datasophon.dao.mapper.ClusterGroupMapper;
import com.datasophon.dao.mapper.ClusterUserGroupMapper;
import com.datasophon.dao.mapper.ClusterUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service("clusterUserGroupService")
public class ClusterUserGroupServiceImpl extends ServiceImpl<ClusterUserGroupMapper, ClusterUserGroup>
    implements
    ClusterUserGroupService {

  @Autowired
  private ClusterGroupMapper clusterGroupMapper;

  @Autowired
  private ClusterUserMapper clusterUserMapper;

  @Override
  public Long countGroupUserNum(Integer groupId) {
    return this.count(new QueryWrapper<ClusterUserGroup>().eq(Constants.GROUP_ID, groupId));
  }

  @Override
  public void deleteByUser(Integer id) {
    this.remove(new QueryWrapper<ClusterUserGroup>().eq(Constants.USER_ID, id));
  }

  @Override
  public ClusterGroup queryMainGroup(Integer userId) {
    List<ClusterUserGroup> clusterUserGroups =
        this.list(new QueryWrapper<ClusterUserGroup>().eq(Constants.USER_ID, userId).eq("user_group_type", 1));
    List<Integer> groupIds = clusterUserGroups.stream().map(ClusterUserGroup::getGroupId).collect(Collectors.toList());
    return clusterGroupMapper.selectById(groupIds.get(0));
  }

  @Override
  public List<ClusterGroup> listOtherGroups(Integer userId) {
    List<ClusterUserGroup> clusterUserGroups =
        this.list(new QueryWrapper<ClusterUserGroup>().eq(Constants.USER_ID, userId).eq("user_group_type", 2));
    List<Integer> groupIds = clusterUserGroups.stream().map(ClusterUserGroup::getGroupId).collect(Collectors.toList());
    if (!groupIds.isEmpty()) {
      return clusterGroupMapper.selectByIds(groupIds);
    }
    return null;
  }

  @Override
  public List<ClusterUser> listClusterUsers(Integer groupId) {
    List<ClusterUserGroup> clusterUserGroups =
        this.list(new QueryWrapper<ClusterUserGroup>().eq(Constants.GROUP_ID, groupId));
    if (!clusterUserGroups.isEmpty()) {
      List<Integer> userIds = clusterUserGroups.stream().map(ClusterUserGroup::getUserId).collect(Collectors.toList());
      return clusterUserMapper.selectByIds(userIds);
    }
    return null;
  }
}
