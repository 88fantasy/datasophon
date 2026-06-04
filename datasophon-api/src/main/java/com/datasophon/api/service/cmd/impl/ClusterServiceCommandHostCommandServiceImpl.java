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

import com.datasophon.api.grpc.WorkerCommandClient;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.cmd.ClusterServiceCommandHostCommandService;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.cmd.ClusterServiceCommandEntity;
import com.datasophon.dao.entity.cmd.ClusterServiceCommandHostCommandEntity;
import com.datasophon.dao.enums.CommandState;
import com.datasophon.dao.mapper.cmd.ClusterServiceCommandHostCommandMapper;
import com.datasophon.dao.mapper.cmd.ClusterServiceCommandMapper;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

@Service("clusterServiceCommandHostCommandService")
public class ClusterServiceCommandHostCommandServiceImpl
        extends
            ServiceImpl<ClusterServiceCommandHostCommandMapper, ClusterServiceCommandHostCommandEntity>
        implements
            ClusterServiceCommandHostCommandService {
    
    private static final Logger logger = LoggerFactory.getLogger(ClusterServiceCommandHostCommandServiceImpl.class);
    
    @Autowired
    ClusterServiceCommandHostCommandMapper hostCommandMapper;
    
    @Autowired
    ClusterInfoService clusterInfoService;
    
    @Autowired
    ClusterServiceCommandMapper commandMapper;
    
    @Autowired
    private WorkerCommandClient workerCommandClient;
    
    @Override
    public Result getHostCommandList(String hostname, String commandHostId, Integer page, Integer pageSize) {
        Integer offset = (page - 1) * pageSize;
        List<ClusterServiceCommandHostCommandEntity> list =
                this.list(new QueryWrapper<ClusterServiceCommandHostCommandEntity>()
                        .eq(Constants.COMMAND_HOST_ID, commandHostId)
                        .orderByDesc(Constants.CREATE_TIME)
                        .last("limit " + offset + "," + pageSize));
        long total = this.count(new QueryWrapper<ClusterServiceCommandHostCommandEntity>()
                .eq(Constants.COMMAND_HOST_ID, commandHostId));
        return Result.success(list).put(Constants.TOTAL, total);
    }
    
    @Override
    public List<ClusterServiceCommandHostCommandEntity> getHostCommandListByCommandId(String commandId) {
        return this.lambdaQuery().eq(ClusterServiceCommandHostCommandEntity::getCommandId, commandId).list();
    }
    
    @Override
    public ClusterServiceCommandHostCommandEntity getByHostCommandId(String hostCommandId) {
        return this.getOne(new QueryWrapper<ClusterServiceCommandHostCommandEntity>().eq(Constants.HOST_COMMAND_ID,
                hostCommandId));
    }
    
    @Override
    public void updateByHostCommandId(ClusterServiceCommandHostCommandEntity hostCommand) {
        this.update(hostCommand, new QueryWrapper<ClusterServiceCommandHostCommandEntity>()
                .eq(Constants.HOST_COMMAND_ID, hostCommand.getHostCommandId()));
    }
    
    @Override
    public Long getHostCommandSizeByHostnameAndCommandHostId(String hostname, String commandHostId) {
        return this.count(new QueryWrapper<ClusterServiceCommandHostCommandEntity>()
                .eq(Constants.HOSTNAME, hostname).eq(Constants.COMMAND_HOST_ID, commandHostId));
    }
    
    @Override
    public Integer getHostCommandTotalProgressByHostnameAndCommandHostId(String hostname, String commandHostId) {
        return hostCommandMapper.getHostCommandTotalProgressByHostnameAndCommandHostId(hostname, commandHostId);
    }
    
    @Override
    public Result getHostCommandLog(Integer clusterId, String hostCommandId) throws Exception {
        ClusterServiceCommandHostCommandEntity hostCommand =
                this.getOne(new QueryWrapper<ClusterServiceCommandHostCommandEntity>().eq(Constants.HOST_COMMAND_ID,
                        hostCommandId));
        
        ClusterServiceCommandEntity commandEntity = commandMapper.getCommandById(hostCommand.getCommandId());
        
        String serviceName = commandEntity.getServiceName();
        String serviceRoleName = hostCommand.getServiceRoleName();
        String logFile = String.format("logs/%s/%s.log", serviceName, serviceRoleName);
        
        logger.info("Start to get {} install log from host {}", serviceRoleName, hostCommand.getHostname());
        ExecResult logResult = workerCommandClient.getLog(hostCommand.getHostname(), logFile, Constants.WORKER_PATH);
        if (Objects.nonNull(logResult) && logResult.getExecResult()) {
            return Result.success(logResult.getExecOut());
        }
        return Result.success();
    }
    
    @Override
    public List<ClusterServiceCommandHostCommandEntity> findFailedHostCommand(String hostname, String commandHostId) {
        return this.list(new QueryWrapper<ClusterServiceCommandHostCommandEntity>()
                .eq(Constants.HOSTNAME, hostname)
                .eq(Constants.COMMAND_HOST_ID, commandHostId)
                .eq(Constants.COMMAND_STATE, CommandState.FAILED));
    }
    
    @Override
    public List<ClusterServiceCommandHostCommandEntity> findCanceledHostCommand(String hostname, String commandHostId) {
        return this.list(new QueryWrapper<ClusterServiceCommandHostCommandEntity>()
                .eq(Constants.HOSTNAME, hostname)
                .eq(Constants.COMMAND_HOST_ID, commandHostId)
                .eq(Constants.COMMAND_STATE, CommandState.CANCEL));
    }
}
