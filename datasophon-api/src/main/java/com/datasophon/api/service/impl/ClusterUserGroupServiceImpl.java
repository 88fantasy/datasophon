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
