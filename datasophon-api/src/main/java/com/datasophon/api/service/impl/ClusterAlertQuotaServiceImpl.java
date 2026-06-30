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

import com.datasophon.api.service.ClusterAlertQuotaService;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.CollectionUtils;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.AlertGroupEntity;
import com.datasophon.dao.entity.ClusterAlertQuota;
import com.datasophon.dao.enums.QuotaState;
import com.datasophon.dao.mapper.AlertGroupMapper;
import com.datasophon.dao.mapper.ClusterAlertQuotaMapper;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;

@Service("clusterAlertQuotaService")
public class ClusterAlertQuotaServiceImpl extends ServiceImpl<ClusterAlertQuotaMapper, ClusterAlertQuota>
        implements
            ClusterAlertQuotaService {
    
    private static final Logger logger = LoggerFactory.getLogger(ClusterAlertQuotaServiceImpl.class);
    
    @Autowired
    AlertGroupMapper alertGroupMapper;
    
    @Override
    public Result getAlertQuotaList(Integer clusterId, Integer alertGroupId, String quotaName, Integer page,
                                    Integer pageSize) {
        Integer offset = (page - 1) * pageSize;
        
        LambdaQueryChainWrapper<ClusterAlertQuota> wrapper = this.lambdaQuery()
                .eq(alertGroupId != null, ClusterAlertQuota::getAlertGroupId, alertGroupId)
                .like(StringUtils.isNotBlank(quotaName), ClusterAlertQuota::getAlertQuotaName, quotaName);
        long count = wrapper.count() == null ? 0 : wrapper.count();
        List<ClusterAlertQuota> alertQuotaList = wrapper.last("limit " + offset + "," + pageSize).list();
        if (CollectionUtils.isEmpty(alertQuotaList)) {
            return Result.successEmptyCount();
        }
        // 查询通知组
        Set<Integer> alertQuotaIdList =
                alertQuotaList.stream().map(ClusterAlertQuota::getAlertGroupId).collect(Collectors.toSet());
        Collection<AlertGroupEntity> alertGroupEntityList = alertGroupMapper.selectByIds(alertQuotaIdList);
        if (CollectionUtils.isNotEmpty(alertGroupEntityList)) {
            Map<Integer, AlertGroupEntity> idMap = alertGroupEntityList.stream()
                    .collect(Collectors.toMap(AlertGroupEntity::getId, a -> a, (a1, a2) -> a1));
            alertQuotaList.forEach(a -> {
                AlertGroupEntity alertGroupEntity = idMap.get(a.getAlertGroupId());
                if (Objects.nonNull(alertGroupEntity)) {
                    a.setAlertGroupName(alertGroupEntity.getAlertGroupName());
                }
                a.setQuotaStateCode(a.getQuotaState().getValue());
            });
        }
        return Result.success(alertQuotaList).put(Constants.TOTAL, count);
    }
    
    private void startAlertQuotas(Collection<ClusterAlertQuota> alertQuotaList) {
        for (ClusterAlertQuota alertQuota : alertQuotaList) {
            alertQuota.setQuotaState(QuotaState.RUNNING);
        }
        
        if (!alertQuotaList.isEmpty()) {
            logger.info("start alert size is {}", alertQuotaList.size());
            updateBatchById(alertQuotaList);
        }
    }
    
    @Override
    public void start(Integer clusterId, String alertQuotaIds) {
        List<String> ids = Arrays.asList(alertQuotaIds.split(","));
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        
        Collection<ClusterAlertQuota> alertQuotaList = this.listByIds(ids);
        
        startAlertQuotas(alertQuotaList);
    }
    
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void stop(Integer clusterId, String alertQuotaIds) {
        List<String> ids = Arrays.asList(alertQuotaIds.split(StrUtil.COMMA));
        if (CollUtil.isEmpty(ids)) {
            return;
        }
        
        // 1、修改禁用状态 & 更新
        Collection<ClusterAlertQuota> alertQuotas = this.listByIds(ids);
        alertQuotas.forEach(q -> {
            q.setQuotaState(QuotaState.STOPPED);
        });
        this.updateBatchById(alertQuotas);
    }
    
    @Override
    public void saveAlertQuota(ClusterAlertQuota clusterAlertQuota) {
        clusterAlertQuota.setQuotaState(QuotaState.STOPPED);
        clusterAlertQuota.setCreateTime(new Date());
        AlertGroupEntity alertGroupEntity = alertGroupMapper.selectById(clusterAlertQuota.getAlertGroupId());
        clusterAlertQuota.setServiceCategory(alertGroupEntity.getAlertGroupCategory());
        this.save(clusterAlertQuota);
    }
    
    @Override
    public List<ClusterAlertQuota> listAlertQuotaByServiceName(String serviceName) {
        return this.list(new QueryWrapper<ClusterAlertQuota>().eq(Constants.SERVICE_CATEGORY, serviceName));
    }
}
