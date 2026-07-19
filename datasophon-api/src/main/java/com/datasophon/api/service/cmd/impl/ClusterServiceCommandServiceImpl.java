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

import com.datasophon.api.service.cmd.ClusterServiceCommandService;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.cmd.ClusterServiceCommandEntity;
import com.datasophon.dao.mapper.cmd.ClusterServiceCommandMapper;

import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import cn.hutool.core.date.BetweenFormatter;
import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;

@Service("clusterServiceCommandService")
public class ClusterServiceCommandServiceImpl extends ServiceImpl<ClusterServiceCommandMapper, ClusterServiceCommandEntity>
        implements
            ClusterServiceCommandService {

    @Override
    public Result getServiceCommandlist(Integer clusterId, Integer page, Integer pageSize) {
        Integer offset = (page - 1) * pageSize;
        List<ClusterServiceCommandEntity> list = this.list(new QueryWrapper<ClusterServiceCommandEntity>()
                .eq(Constants.CLUSTER_ID, clusterId)
                .orderByDesc(Constants.CREATE_TIME).last("limit " + offset + "," + pageSize));
        Long total = this.count(new QueryWrapper<ClusterServiceCommandEntity>()
                .eq(Constants.CLUSTER_ID, clusterId));
        for (ClusterServiceCommandEntity commandEntity : list) {
            Date createTime = commandEntity.getCreateTime();
            Date endTime = commandEntity.getEndTime();
            if (Objects.isNull(endTime)) {
                endTime = new Date();
            }
            long between = DateUtil.between(createTime, endTime, DateUnit.MS);
            String durationTime = DateUtil.formatBetween(between, BetweenFormatter.Level.SECOND);
            commandEntity.setDurationTime(durationTime);
        }
        return Result.success(total, list);
    }

    @Override
    public ClusterServiceCommandEntity getCommandById(String commandId) {
        return lambdaQuery().eq(ClusterServiceCommandEntity::getCommandId, commandId).one();
    }

    @Override
    public ClusterServiceCommandEntity getLatestCommand(Integer clusterId, String serviceName) {
        return lambdaQuery()
                .eq(ClusterServiceCommandEntity::getClusterId, clusterId)
                .eq(ClusterServiceCommandEntity::getServiceName, serviceName)
                .orderByDesc(ClusterServiceCommandEntity::getCreateTime)
                .last("LIMIT 1")
                .one();
    }

}
