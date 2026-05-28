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
import com.datasophon.api.enums.Status;
import com.datasophon.api.exceptions.ServiceException;
import com.datasophon.api.master.transport.WorkerCallAdapter;
import com.datasophon.api.service.ClusterUserGroupService;
import com.datasophon.api.service.ClusterUserService;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.api.utils.SpringTool;
import com.datasophon.common.Constants;
import com.datasophon.common.command.remote.CreateUnixUserCommand;
import com.datasophon.common.command.remote.DelUnixUserCommand;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterGroup;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterUser;
import com.datasophon.dao.entity.ClusterUserGroup;
import com.datasophon.dao.mapper.ClusterGroupMapper;
import com.datasophon.dao.mapper.ClusterUserMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service("clusterUserService")
@Transactional
public class ClusterUserServiceImpl extends ServiceImpl<ClusterUserMapper, ClusterUser> implements ClusterUserService {
    
    private static final Logger logger = LoggerFactory.getLogger(ClusterUserServiceImpl.class);

    @Autowired
    private ClusterGroupMapper clusterGroupMapper;
    
    @Autowired
    private ClusterHostService hostService;
    
    @Autowired
    private ClusterUserGroupService userGroupService;
    
    @Override
    public Result create(Integer clusterId, String username, Integer mainGroupId, String groupIds) {
        
        if (hasRepeatUserName(clusterId, username)) {
            return Result.error(Status.DUPLICATE_USER_NAME.getMsg());
        }
        List<ClusterHostDO> hostList = hostService.getHostListByClusterId(clusterId);
        
        ClusterUser clusterUser = new ClusterUser();
        clusterUser.setUsername(username);
        clusterUser.setClusterId(clusterId);
        this.save(clusterUser);
        buildClusterUserGroup(clusterId, clusterUser.getId(), mainGroupId, 1);
        
        String otherGroup = null;
        if (StringUtils.isNotBlank(groupIds)) {
            List<Integer> otherGroupIds =
                    Arrays.stream(groupIds.split(",")).map(Integer::parseInt).collect(Collectors.toList());
            for (Integer id : otherGroupIds) {
                buildClusterUserGroup(clusterId, clusterUser.getId(), id, 2);
            }
            List<ClusterGroup> clusterGroups = clusterGroupMapper.selectByIds(otherGroupIds);
            otherGroup = clusterGroups.stream().map(ClusterGroup::getGroupName).collect(Collectors.joining(","));
        }
        
        ClusterGroup mainGroup = clusterGroupMapper.selectById(mainGroupId);
        // sync to all hosts
        WorkerCallAdapter adapter = SpringTool.getApplicationContext().getBean(WorkerCallAdapter.class);
        for (ClusterHostDO clusterHost : hostList) {
            CreateUnixUserCommand createUnixUserCommand = new CreateUnixUserCommand();
            createUnixUserCommand.setUsername(username);
            createUnixUserCommand.setMainGroup(mainGroup.getGroupName());
            createUnixUserCommand.setOtherGroups(otherGroup);
            ExecResult execResult = adapter.createUnixUser(clusterHost.getHostname(), createUnixUserCommand);
            if (execResult.getExecResult()) {
                logger.info("create unix user {} success at {}", username, clusterHost.getHostname());
            } else {
                logger.info(execResult.getExecOut());
                throw new ServiceException(500,
                        "create unix user " + username + " failed at " + clusterHost.getHostname());
            }
        }
        return Result.success();
    }
    
    private void buildClusterUserGroup(Integer clusterId, Integer userId, Integer groupId, Integer userGroupType) {
        ClusterUserGroup clusterUserGroup = new ClusterUserGroup();
        clusterUserGroup.setUserId(userId);
        clusterUserGroup.setGroupId(groupId);
        clusterUserGroup.setClusterId(clusterId);
        clusterUserGroup.setUserGroupType(userGroupType);
        userGroupService.save(clusterUserGroup);
    }
    
    private boolean hasRepeatUserName(Integer clusterId, String username) {
        List<ClusterUser> list = this.list(new QueryWrapper<ClusterUser>()
                .eq(Constants.CLUSTER_ID, clusterId)
                .eq(Constants.USERNAME, username));
      return !list.isEmpty();
    }
    
    @Override
    public Result listPage(Integer clusterId, String username, Integer page, Integer pageSize) {
        int offset = (page - 1) * pageSize;
        List<ClusterUser> list = this.list(new QueryWrapper<ClusterUser>()
                .like(StringUtils.isNotBlank(username), Constants.USERNAME, username)
                .eq(Constants.CLUSTER_ID, clusterId)
                .last("limit " + offset + "," + pageSize));
        for (ClusterUser clusterUser : list) {
            ClusterGroup mainGroup = userGroupService.queryMainGroup(clusterUser.getId());
            List<ClusterGroup> otherGroupList = userGroupService.listOtherGroups(clusterUser.getId());
            if (Objects.nonNull(otherGroupList) && !otherGroupList.isEmpty()) {
                String otherGroups =
                        otherGroupList.stream().map(ClusterGroup::getGroupName).collect(Collectors.joining(","));
                clusterUser.setOtherGroups(otherGroups);
            }
            clusterUser.setMainGroup(mainGroup.getGroupName());
        }
        long total = this.count(new QueryWrapper<ClusterUser>()
                .like(StringUtils.isNotBlank(username), Constants.USERNAME, username)
                .eq(Constants.CLUSTER_ID, clusterId));
        return Result.success(list).put(Constants.TOTAL, total);
    }
    
    @Override
    public Result deleteClusterUser(Integer id) {
        ClusterUser clusterUser = this.getById(id);
        // delete user and group
        userGroupService.deleteByUser(id);
        List<ClusterHostDO> hostList = hostService.getHostListByClusterId(clusterUser.getClusterId());
        // sync to all hosts
        WorkerCallAdapter adapter = SpringTool.getApplicationContext().getBean(WorkerCallAdapter.class);
        for (ClusterHostDO clusterHost : hostList) {
            DelUnixUserCommand delUnixUserCommand = new DelUnixUserCommand();
            delUnixUserCommand.setUsername(clusterUser.getUsername());
            ExecResult execResult = adapter.deleteUnixUser(clusterHost.getHostname(), delUnixUserCommand);
            if (execResult.getExecResult()) {
                logger.info("del unix user success at {}", clusterHost.getHostname());
            } else {
                logger.info("del unix user failed at {}", clusterHost.getHostname());
            }
        }
        this.removeById(id);
        return Result.success();
    }
    
    @Override
    public List<ClusterUser> listAllUser(Integer clusterId) {
        return this.lambdaQuery().eq(ClusterUser::getClusterId, clusterId).list();
    }
    
    @Override
    public void createUnixUserOnHost(ClusterUser clusterUser, String hostname) {
        String username = clusterUser.getUsername();
        ClusterGroup mainGroup = userGroupService.queryMainGroup(clusterUser.getId());
        List<ClusterGroup> otherGroupList = userGroupService.listOtherGroups(clusterUser.getId());
        String otherGroup = "";
        if (Objects.nonNull(otherGroupList) && !otherGroupList.isEmpty()) {
            otherGroup = otherGroupList.stream().map(ClusterGroup::getGroupName).collect(Collectors.joining(","));
        }
        CreateUnixUserCommand createUnixUserCommand = new CreateUnixUserCommand();
        createUnixUserCommand.setUsername(clusterUser.getUsername());
        createUnixUserCommand.setMainGroup(mainGroup.getGroupName());
        createUnixUserCommand.setOtherGroups(otherGroup);
        WorkerCallAdapter adapter = SpringTool.getApplicationContext().getBean(WorkerCallAdapter.class);
        ExecResult execResult = adapter.createUnixUser(hostname, createUnixUserCommand);
        if (execResult.getExecResult()) {
            logger.info("create unix user {} success at {}", username, hostname);
        } else {
            logger.info(execResult.getExecOut());
            throw new ServiceException(500, "create unix user " + username + " failed at " + hostname);
        }
    }
}
