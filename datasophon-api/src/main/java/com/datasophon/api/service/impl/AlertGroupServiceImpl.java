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

import com.datasophon.api.service.AlertGroupService;
import com.datasophon.api.service.ClusterAlertGroupMapService;
import com.datasophon.api.service.ClusterAlertQuotaService;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.CollectionUtils;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.AlertGroupEntity;
import com.datasophon.dao.entity.ClusterAlertGroupMap;
import com.datasophon.dao.entity.ClusterAlertQuota;
import com.datasophon.dao.mapper.AlertGroupMapper;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

@Service("alertGroupService")
public class AlertGroupServiceImpl extends ServiceImpl<AlertGroupMapper, AlertGroupEntity>
        implements
            AlertGroupService {

    @Autowired
    private ClusterAlertGroupMapService alertGroupMapService;

    @Autowired
    private ClusterAlertQuotaService quotaService;

    @Override
    public Result getAlertGroupList(Integer clusterId, String alertGroupName, Integer page, Integer pageSize) {
        Integer offset = (page - 1) * pageSize;

        List<ClusterAlertGroupMap> alertGroupMapList =
                alertGroupMapService.list(new QueryWrapper<ClusterAlertGroupMap>().eq(Constants.CLUSTER_ID, clusterId));
        if (CollectionUtils.isEmpty(alertGroupMapList)) {
            return Result.successEmptyCount();
        }

        List<Integer> groupIds =
                alertGroupMapList.stream().map(ClusterAlertGroupMap::getAlertGroupId).toList();
        LambdaQueryChainWrapper<AlertGroupEntity> wrapper = this.lambdaQuery()
                .in(AlertGroupEntity::getId, groupIds)
                .like(StringUtils.isNotBlank(alertGroupName), AlertGroupEntity::getAlertGroupName, alertGroupName);
        long count = wrapper.count() == null ? 0 : wrapper.count();
        List<AlertGroupEntity> alertGroupList = wrapper.last("limit " + offset + "," + pageSize).list();
        if (CollectionUtils.isEmpty(alertGroupList)) {
            return Result.successEmptyCount();
        }

        Set<Integer> alertGroupIdList =
                alertGroupList.stream().map(AlertGroupEntity::getId).collect(Collectors.toSet());
        // 查询告警组下告警指标个数
        List<ClusterAlertQuota> clusQuotaList =
                quotaService.lambdaQuery().in(ClusterAlertQuota::getAlertGroupId, alertGroupIdList).list();
        if (CollectionUtils.isNotEmpty(clusQuotaList)) {
            Map<Integer, List<ClusterAlertQuota>> alertGroupByGroupId =
                    clusQuotaList.stream().collect(Collectors.groupingBy(ClusterAlertQuota::getAlertGroupId));
            alertGroupList.forEach(a -> {
                List<ClusterAlertQuota> tmpQuotaList = alertGroupByGroupId.get(a.getId());
                int quotaCnt = CollectionUtils.isEmpty(tmpQuotaList) ? 0 : tmpQuotaList.size();
                a.setAlertQuotaNum(quotaCnt);
            });
        }

        return Result.success(alertGroupList).put(Constants.TOTAL, count);
    }

    @Override
    public Result saveAlertGroup(AlertGroupEntity alertGroup) {
        this.save(alertGroup);
        ClusterAlertGroupMap clusterAlertGroupMap = new ClusterAlertGroupMap();
        clusterAlertGroupMap.setAlertGroupId(alertGroup.getId());
        clusterAlertGroupMap.setClusterId(alertGroup.getClusterId());
        alertGroupMapService.save(clusterAlertGroupMap);
        return Result.success();
    }
}
