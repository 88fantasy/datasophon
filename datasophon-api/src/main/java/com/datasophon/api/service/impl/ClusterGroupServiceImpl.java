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

import com.datasophon.api.enums.Status;
import com.datasophon.api.exceptions.ServiceException;
import com.datasophon.api.master.transport.WorkerCallAdapter;
import com.datasophon.api.service.ClusterGroupService;
import com.datasophon.api.service.ClusterUserGroupService;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.api.utils.ProcessUtils;
import com.datasophon.api.utils.SpringTool;
import com.datasophon.common.Constants;
import com.datasophon.common.command.remote.CreateUnixGroupCommand;
import com.datasophon.common.command.remote.DelUnixGroupCommand;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterGroup;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterUser;
import com.datasophon.dao.mapper.ClusterGroupMapper;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

@Service("clusterGroupService")
@Transactional
public class ClusterGroupServiceImpl extends ServiceImpl<ClusterGroupMapper, ClusterGroup>
        implements
            ClusterGroupService {
    
    private static final Logger logger = LoggerFactory.getLogger(ClusterGroupServiceImpl.class);
    
    @Autowired
    private ClusterHostService hostService;
    
    @Autowired
    private ClusterUserGroupService userGroupService;
    
    @Override
    public Result saveClusterGroup(Integer clusterId, String groupName) {
        if (hasRepeatGroupName(clusterId, groupName)) {
            return Result.error(Status.GROUP_NAME_DUPLICATION.getMsg());
        }
        ClusterGroup clusterGroup = new ClusterGroup();
        clusterGroup.setClusterId(clusterId);
        clusterGroup.setGroupName(groupName);
        this.save(clusterGroup);
        
        List<ClusterHostDO> hostList = hostService.getHostListByClusterId(clusterId);
        WorkerCallAdapter adapter = SpringTool.getApplicationContext().getBean(WorkerCallAdapter.class);
        for (ClusterHostDO clusterHost : hostList) {
            CreateUnixGroupCommand createUnixGroupCommand = new CreateUnixGroupCommand();
            createUnixGroupCommand.setGroupName(groupName);
            ExecResult execResult = adapter.createUnixGroup(clusterHost.getHostname(), createUnixGroupCommand);
            if (execResult.getExecResult()) {
                logger.info("create unix group success at {}", clusterHost.getHostname());
            } else {
                logger.info(execResult.getExecOut());
                throw new ServiceException(500,
                        "create unix group " + groupName + " failed at " + clusterHost.getHostname());
            }
        }

        return Result.success();
    }
    
    private boolean hasRepeatGroupName(Integer clusterId, String groupName) {
        List<ClusterGroup> list = this.list(new QueryWrapper<ClusterGroup>()
                .eq(Constants.CLUSTER_ID, clusterId)
                .eq(Constants.GROUP_NAME, groupName));
        if (list.size() > 0) {
            return true;
        }
        return false;
    }
    
    @Override
    public void refreshUserGroupToHost(Integer clusterId) {
        List<ClusterHostDO> hostList = hostService.getHostListByClusterId(clusterId);
        List<ClusterGroup> groupList = this.list();
        for (ClusterGroup clusterGroup : groupList) {
            ProcessUtils.syncUserGroupToHosts(hostList, clusterGroup.getGroupName(), "groupadd");
        }
    }
    
    @Override
    public Result deleteUserGroup(Integer id) {
        ClusterGroup clusterGroup = this.getById(id);
        Long num = userGroupService.countGroupUserNum(id);
        if (num > 0) {
            return Result.error(Status.USER_GROUP_TIPS_ONE.getMsg());
        }
        this.removeById(id);
        List<ClusterHostDO> hostList = hostService.getHostListByClusterId(clusterGroup.getClusterId());
        WorkerCallAdapter adapter = SpringTool.getApplicationContext().getBean(WorkerCallAdapter.class);
        for (ClusterHostDO clusterHost : hostList) {
            DelUnixGroupCommand delUnixGroupCommand = new DelUnixGroupCommand();
            delUnixGroupCommand.setGroupName(clusterGroup.getGroupName());
            ExecResult execResult = adapter.deleteUnixGroup(clusterHost.getHostname(), delUnixGroupCommand);
            if (execResult.getExecResult()) {
                logger.info("del unix group success at {}", clusterHost.getHostname());
            } else {
                logger.info("del unix group failed at {}", clusterHost.getHostname());
            }
        }
        return Result.success();
    }
    
    @Override
    public Result listPage(String groupName, Integer clusterId, Integer page, Integer pageSize) {
        Integer offset = (page - 1) * pageSize;
        List<ClusterGroup> list = this.list(new QueryWrapper<ClusterGroup>()
                .like(StringUtils.isNotBlank(groupName), Constants.GROUP_NAME, groupName)
                .eq(Constants.CLUSTER_ID, clusterId)
                .last("limit " + offset + "," + pageSize));
        for (ClusterGroup clusterGroup : list) {
            List<ClusterUser> clusterUserList = userGroupService.listClusterUsers(clusterGroup.getId());
            if (Objects.nonNull(clusterUserList) && !clusterUserList.isEmpty()) {
                String clusterUsers =
                        clusterUserList.stream().map(e -> e.getUsername()).collect(Collectors.joining(","));
                clusterGroup.setClusterUsers(clusterUsers);
            }
        }
        long total = this.count(new QueryWrapper<ClusterGroup>()
                .like(StringUtils.isNotBlank(groupName), Constants.GROUP_NAME, groupName)
                .eq(Constants.CLUSTER_ID, clusterId));
        return Result.success(list).put(Constants.TOTAL, total);
    }
    
    @Override
    public List<ClusterGroup> listAllUserGroup(Integer clusterId) {
        return this.lambdaQuery().eq(ClusterGroup::getClusterId, clusterId).list();
    }
    
    @Override
    public void createUnixGroupOnHost(String hostname, String groupName) {
        createUnixGroup(hostname, groupName);
    }

    private void createUnixGroup(String hostname, String groupName) {
        CreateUnixGroupCommand createUnixGroupCommand = new CreateUnixGroupCommand();
        createUnixGroupCommand.setGroupName(groupName);
        WorkerCallAdapter adapter = SpringTool.getApplicationContext().getBean(WorkerCallAdapter.class);
        ExecResult execResult = adapter.createUnixGroup(hostname, createUnixGroupCommand);
        if (execResult.getExecResult()) {
            logger.info("create unix group success at {}", hostname);
        } else {
            logger.info(execResult.getExecOut());
            throw new ServiceException(500, "create unix group " + groupName + " failed at " + hostname);
        }
    }
}
