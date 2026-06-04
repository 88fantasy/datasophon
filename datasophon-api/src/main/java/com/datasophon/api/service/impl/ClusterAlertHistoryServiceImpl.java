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

import com.datasophon.api.master.service.AlertService;
import com.datasophon.api.master.service.PrometheusService;
import com.datasophon.api.service.ClusterAlertHistoryService;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.common.Constants;
import com.datasophon.common.command.GeneratePrometheusConfigCommand;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterAlertHistory;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.mapper.ClusterAlertHistoryMapper;
import com.datasophon.dao.mapper.ClusterServiceRoleInstanceMapper;
import com.datasophon.domain.alert.model.AlertMessage;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

@Service("clusterAlertHistoryService")
@Transactional
public class ClusterAlertHistoryServiceImpl extends ServiceImpl<ClusterAlertHistoryMapper, ClusterAlertHistory>
        implements
            ClusterAlertHistoryService {
    
    private static final Logger logger = LoggerFactory.getLogger(ClusterAlertHistoryServiceImpl.class);
    
    @Autowired
    private ClusterServiceRoleInstanceMapper roleInstanceMapper;
    
    @Autowired
    private ClusterInfoService clusterInfoService;
    
    @Autowired
    private AlertService alertService;
    
    @Lazy
    @Autowired
    private PrometheusService prometheusService;
    
    @Override
    public void saveAlertHistory(String alertMessage) {
        logger.warn("Receive Alert Message : {}", alertMessage);
        AlertMessage message = JSONObject.parseObject(alertMessage, AlertMessage.class);
        alertService.handleAlertMessage(message);
    }
    
    @Override
    public Result getAlertList(Integer serviceInstanceId) {
        List<ClusterAlertHistory> list = this.list(new QueryWrapper<ClusterAlertHistory>()
                .eq(serviceInstanceId != null, Constants.SERVICE_INSTANCE_ID, serviceInstanceId)
                .eq(Constants.IS_ENABLED, 1)
                .orderByDesc(Constants.CREATE_TIME));
        return Result.success(list);
    }
    
    @Override
    public Result getAllAlertList(Integer clusterId, Integer page, Integer pageSize) {
        int offset = (page - 1) * pageSize;
        List<ClusterAlertHistory> list = this.list(new QueryWrapper<ClusterAlertHistory>()
                .eq(Constants.CLUSTER_ID, clusterId)
                .eq(Constants.IS_ENABLED, 1)
                .orderByDesc(Constants.CREATE_TIME)
                .last("limit " + offset + "," + pageSize));
        long count = this.count(new QueryWrapper<ClusterAlertHistory>()
                .eq(Constants.CLUSTER_ID, clusterId)
                .eq(Constants.IS_ENABLED, 1));
        return Result.success(list).put(Constants.TOTAL, count);
    }
    
    @Override
    public void removeAlertByRoleInstanceIds(List<Integer> ids) {
        ClusterServiceRoleInstanceEntity roleInstanceEntity = roleInstanceMapper.selectById(ids.get(0));
        ClusterInfoEntity clusterInfoEntity = clusterInfoService.getById(roleInstanceEntity.getClusterId());
        this.remove(new QueryWrapper<ClusterAlertHistory>()
                .eq(Constants.IS_ENABLED, 1)
                .in(Constants.SERVICE_ROLE_INSTANCE_ID, ids));
        // 重新配置prometheus
        GeneratePrometheusConfigCommand prometheusConfigCommand = new GeneratePrometheusConfigCommand();
        prometheusConfigCommand.setServiceInstanceId(roleInstanceEntity.getServiceId());
        prometheusConfigCommand.setClusterFrame(clusterInfoEntity.getClusterFrame());
        prometheusConfigCommand.setClusterId(roleInstanceEntity.getClusterId());
        prometheusService.generatePrometheus(prometheusConfigCommand);
    }
}
