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

 *
 */

package com.datasophon.api.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datasophon.api.enums.Status;
import com.datasophon.api.service.ClusterServiceInstanceRoleGroupService;
import com.datasophon.api.service.ClusterServiceRoleGroupConfigService;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterServiceInstanceEntity;
import com.datasophon.dao.entity.ClusterServiceInstanceRoleGroup;
import com.datasophon.dao.entity.ClusterServiceRoleGroupConfig;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.NeedRestart;
import com.datasophon.dao.mapper.ClusterServiceInstanceMapper;
import com.datasophon.dao.mapper.ClusterServiceInstanceRoleGroupMapper;
import com.datasophon.dao.mapper.ClusterServiceRoleInstanceMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service("clusterServiceInstanceRoleGroupService")
public class ClusterServiceInstanceRoleGroupServiceImpl
    extends
    ServiceImpl<ClusterServiceInstanceRoleGroupMapper, ClusterServiceInstanceRoleGroup>
    implements
    ClusterServiceInstanceRoleGroupService {

  @Autowired
  private ClusterServiceInstanceMapper serviceInstanceMapper;

  @Autowired
  private ClusterServiceRoleInstanceMapper roleInstanceMapper;

  @Autowired
  private ClusterServiceRoleGroupConfigService roleGroupConfigService;

  private static final String DEFAULT = "default";

  @Override
  public ClusterServiceInstanceRoleGroup getDefaultRoleGroupByServiceInstanceId(
      Integer serviceInstanceId) {
    return this.getOne(
        new QueryWrapper<ClusterServiceInstanceRoleGroup>()
            .eq(Constants.SERVICE_INSTANCE_ID, serviceInstanceId)
            .eq(Constants.ROLE_GROUP_TYPE, DEFAULT));
  }

  @Override
  public Result saveRoleGroup(
      Integer serviceInstanceId, Integer roleGroupId, String roleGroupName) {
    ClusterServiceInstanceEntity serviceInstance =
        serviceInstanceMapper.selectById(serviceInstanceId);
    // is repeat name
    if (isRepeatRoleGroupName(serviceInstanceId, roleGroupName)) {
      return Result.error(Status.REPEAT_ROLE_GROUP_NAME.getMsg());
    }
    ClusterServiceInstanceRoleGroup roleGroup = new ClusterServiceInstanceRoleGroup();
    roleGroup.setRoleGroupType("custom");
    roleGroup.setRoleGroupName(roleGroupName);
    roleGroup.setServiceName(serviceInstance.getServiceName());
    roleGroup.setServiceInstanceId(serviceInstanceId);
    roleGroup.setClusterId(serviceInstance.getClusterId());
    roleGroup.setNeedRestart(NeedRestart.NO);
    this.save(roleGroup);
    ClusterServiceRoleGroupConfig config =
        roleGroupConfigService.getConfigByRoleGroupId(roleGroupId);
    ClusterServiceRoleGroupConfig roleGroupConfig = new ClusterServiceRoleGroupConfig();
    BeanUtils.copyProperties(config, roleGroupConfig);
    roleGroupConfig.setConfigVersion(1);
    roleGroupConfig.setId(null);
    roleGroupConfig.setRoleGroupId(roleGroup.getId());
    roleGroupConfigService.save(roleGroupConfig);
    return Result.success();
  }

  private boolean isRepeatRoleGroupName(Integer serviceInstanceId, String roleGroupName) {
    List<ClusterServiceInstanceRoleGroup> list =
        this.list(
            new QueryWrapper<ClusterServiceInstanceRoleGroup>()
                .eq(Constants.SERVICE_INSTANCE_ID, serviceInstanceId)
                .eq(Constants.ROLE_GROUP_NAME, roleGroupName));
    if (list.size() > 0) {
      return true;
    }
    return false;
  }

  @Override
  public Result bind(String roleInstanceIds, Integer roleGroupId) {
    String[] ids = roleInstanceIds.split(",");
    ArrayList<ClusterServiceRoleInstanceEntity> list = new ArrayList<>();
    for (String id : ids) {
      ClusterServiceRoleInstanceEntity roleInstanceEntity = roleInstanceMapper.selectById(id);
      if (!isSameRoleGroup(roleInstanceEntity, Arrays.asList(ids))) {
        return Result.error(Status.NEED_SAME_ROLE_GROUP.getMsg());
      }
      // 判断新角色组与原角色组配置是否相同，不相同则需标识该角色实例需要重启
      if (!isSameConfig(roleInstanceEntity.getRoleGroupId(), roleGroupId)) {
        roleInstanceEntity.setNeedRestart(NeedRestart.YES);
      }
      roleInstanceEntity.setRoleGroupId(roleGroupId);
      list.add(roleInstanceEntity);
    }
    roleInstanceMapper.updateById(list);
    return Result.success();
  }

  private boolean isSameRoleGroup(ClusterServiceRoleInstanceEntity roleInstanceEntity, List<String> ids) {

    // query role instance by hostname and servicename
    List<ClusterServiceRoleInstanceEntity> roleList =
        roleInstanceMapper.listRoleIns(roleInstanceEntity.getHostname(), roleInstanceEntity.getServiceName());
    List<String> listIds = roleList.stream().map(e -> e.getId().toString()).collect(Collectors.toList());
    if (ids.containsAll(listIds)) {
      return true;
    }
    return false;
  }

  private boolean isSameConfig(Integer oldRoleGroupId, Integer newRoleGroupId) {
    ClusterServiceRoleGroupConfig oldConfig =
        roleGroupConfigService.getConfigByRoleGroupId(oldRoleGroupId);
    ClusterServiceRoleGroupConfig newConfig =
        roleGroupConfigService.getConfigByRoleGroupId(newRoleGroupId);
    if (oldConfig.getConfigJsonMd5().equals(newConfig.getConfigJsonMd5())) {
      return true;
    }
    return false;
  }

  @Override
  public ClusterServiceRoleGroupConfig getRoleGroupConfigByServiceId(Integer serviceInstanceId) {
    ClusterServiceInstanceRoleGroup instanceRoleGroup =
        this.getOne(
            new QueryWrapper<ClusterServiceInstanceRoleGroup>()
                .eq(Constants.SERVICE_INSTANCE_ID, serviceInstanceId)
                .eq(Constants.ROLE_GROUP_TYPE, DEFAULT));
    return roleGroupConfigService.getConfigByRoleGroupId(instanceRoleGroup.getId());
  }

  @Override
  public Result rename(Integer roleGroupId, String roleGroupName) {
    ClusterServiceInstanceRoleGroup roleGroup = this.getById(roleGroupId);
    if (!roleGroup.getRoleGroupName().equals(roleGroupName)
        && isRepeatRoleGroupName(roleGroup.getServiceInstanceId(), roleGroupName)) {
      return Result.error(Status.REPEAT_ROLE_GROUP_NAME.getMsg());
    }
    roleGroup.setRoleGroupName(roleGroupName);
    this.updateById(roleGroup);
    return Result.success();
  }

  @Override
  public Result deleteRoleGroup(Integer roleGroupId) {
    if (hasRoleInstanceUse(roleGroupId)) {
      return Result.error(Status.THE_CURRENT_ROLE_GROUP_BE_USING.getMsg());
    }
    if (isDefaultRoleGroup(roleGroupId)) {
      return Result.error(Status.THE_CURRENT_ROLE_GROUP_IS_DEFAULT.getMsg());
    }
    this.removeById(roleGroupId);
    roleGroupConfigService.removeAllByRoleGroupId(roleGroupId);
    return Result.success();
  }

  private boolean isDefaultRoleGroup(Integer roleGroupId) {
    ClusterServiceInstanceRoleGroup roleGroup = this.getById(roleGroupId);
    String roleGroupType = roleGroup.getRoleGroupType();
    if (DEFAULT.equals(roleGroupType)) {
      return true;
    }
    return false;
  }

  @Override
  public List<ClusterServiceInstanceRoleGroup> listRoleGroupByServiceInstanceId(
      Integer serviceInstanceId) {

    return this.list(
        new QueryWrapper<ClusterServiceInstanceRoleGroup>()
            .eq(Constants.SERVICE_INSTANCE_ID, serviceInstanceId));
  }

  @Override
  public void updateToNeedRestart(Integer roleGroupId) {
    ClusterServiceInstanceRoleGroup roleGroup = this.getById(roleGroupId);
    roleGroup.setNeedRestart(NeedRestart.YES);
    this.updateById(roleGroup);
  }

  private boolean hasRoleInstanceUse(Integer roleGroupId) {
    List<ClusterServiceRoleInstanceEntity> list =
        roleInstanceMapper.selectList(new QueryWrapper<ClusterServiceRoleInstanceEntity>()
            .eq(Constants.ROLE_GROUP_ID, roleGroupId));
    return !list.isEmpty();
  }
}
