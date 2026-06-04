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

import com.datasophon.api.service.cmd.ClusterServiceCommandHostCommandService;
import com.datasophon.api.service.cmd.ClusterServiceCommandHostService;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.cmd.ClusterServiceCommandHostEntity;
import com.datasophon.dao.enums.CommandState;
import com.datasophon.dao.mapper.cmd.ClusterServiceCommandHostMapper;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

@Service("clusterServiceCommandHostService")
public class ClusterServiceCommandHostServiceImpl
        extends
            ServiceImpl<ClusterServiceCommandHostMapper, ClusterServiceCommandHostEntity>
        implements
            ClusterServiceCommandHostService {
    
    @Autowired
    private ClusterServiceCommandHostCommandService hostCommandService;
    
    @Autowired
    private ClusterServiceCommandHostMapper hostMapper;
    
    @Override
    public Result getCommandHostList(Integer clusterId, String commandId, Integer page, Integer pageSize) {
        Integer offset = (page - 1) * pageSize;
        
        LambdaQueryChainWrapper<ClusterServiceCommandHostEntity> wrapper = this.lambdaQuery()
                .eq(ClusterServiceCommandHostEntity::getCommandId, commandId);
        long total = wrapper.count();
        List<ClusterServiceCommandHostEntity> list = wrapper
                .orderByDesc(ClusterServiceCommandHostEntity::getCreateTime)
                .last("limit " + offset + "," + pageSize)
                .list();
        
        return Result.success(list).put(Constants.TOTAL, total);
    }
    
    @Override
    public Long getCommandHostSizeByCommandId(String commandId) {
        return this.lambdaQuery().eq(ClusterServiceCommandHostEntity::getCommandId, commandId).count();
    }
    
    @Override
    public Integer getCommandHostTotalProgressByCommandId(String commandId) {
        return hostMapper.getCommandHostTotalProgressByCommandId(commandId);
    }
    
    @Override
    public List<ClusterServiceCommandHostEntity> findFailedCommandHost(String commandId) {
        return this.lambdaQuery()
                .eq(ClusterServiceCommandHostEntity::getCommandId, commandId)
                .eq(ClusterServiceCommandHostEntity::getCommandState, CommandState.FAILED)
                .list();
    }
    
    @Override
    public List<ClusterServiceCommandHostEntity> findCanceledCommandHost(String commandId) {
        return this.lambdaQuery()
                .eq(ClusterServiceCommandHostEntity::getCommandId, commandId)
                .eq(ClusterServiceCommandHostEntity::getCommandState, CommandState.CANCEL)
                .list();
    }
    
}
