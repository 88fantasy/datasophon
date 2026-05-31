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

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.stream.StreamUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datasophon.api.load.GlobalVariables;
import com.datasophon.api.service.ClusterServiceRoleInstanceWebuisService;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.PlaceholderUtils;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceWebuis;
import com.datasophon.dao.mapper.ClusterServiceInstanceMapper;
import com.datasophon.dao.mapper.ClusterServiceRoleInstanceWebuisMapper;
import com.datasophon.dao.model.WebuisVO;
import com.github.yulichang.wrapper.MPJLambdaWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service("clusterServiceRoleInstanceWebuisService")
public class ClusterServiceRoleInstanceWebuisServiceImpl
        extends
        ServiceImpl<ClusterServiceRoleInstanceWebuisMapper, ClusterServiceRoleInstanceWebuis>
        implements
        ClusterServiceRoleInstanceWebuisService {

    private static final String ACTIVE = "(Active)";

    private static final String STANDBY = "(Standby)";

    @Autowired
    ClusterServiceInstanceMapper clusterServiceInstanceMapper;

    @Override
    public Result getWebUis(Integer serviceInstanceId) {

        MPJLambdaWrapper<ClusterServiceRoleInstanceWebuis> wrapper = new MPJLambdaWrapper<ClusterServiceRoleInstanceWebuis>()
                .selectAll(ClusterServiceRoleInstanceWebuis.class)
                .select(ClusterHostDO::getIp)
                .innerJoin(ClusterServiceRoleInstanceEntity.class, ClusterServiceRoleInstanceEntity::getId, ClusterServiceRoleInstanceWebuis::getServiceRoleInstanceId)
                .innerJoin(ClusterHostDO.class, ClusterHostDO::getHostname, ClusterServiceRoleInstanceEntity::getHostname)
                .eq(ClusterServiceRoleInstanceWebuis::getServiceInstanceId, serviceInstanceId);

        List<WebuisVO> list = getBaseMapper().selectJoinList(WebuisVO.class, wrapper);

//        List<ClusterServiceRoleInstanceWebuis> list = this.list(
//                new QueryWrapper<ClusterServiceRoleInstanceWebuis>()
//                        .eq(Constants.SERVICE_INSTANCE_ID, serviceInstanceId));
        Integer clusterId = clusterServiceInstanceMapper.selectById(serviceInstanceId).getClusterId();
        Map<String, String> globalVariables = GlobalVariables.getVariables(clusterId);
        return Result.success(list.stream().peek(ui -> {
//            存在脏数据，直接忽略
            if (StrUtil.isBlank(ui.getWebUrl())) {
                return;
            }
            String url = PlaceholderUtils.replacePlaceholders(ui.getWebUrl(), MapUtil.builder("${host}", ui.getIp()).build(), Constants.REGEX_VARIABLE);
            String newUrl = PlaceholderUtils.replacePlaceholders(url, globalVariables, Constants.REGEX_VARIABLE);
            ui.setWebUrl(newUrl);
        }).collect(Collectors.toList()));
    }

    @Override
    public void removeByServiceInsId(Integer serviceInstanceId) {
        this.remove(
                new QueryWrapper<ClusterServiceRoleInstanceWebuis>()
                        .eq(Constants.SERVICE_INSTANCE_ID, serviceInstanceId));
    }

    @Override
    public void updateWebUiToActive(Integer roleInstanceId) {
        updateWebUiName(roleInstanceId, ACTIVE);
    }

    @Override
    public ClusterServiceRoleInstanceWebuis getRoleInstanceWebUi(Integer roleInstanceId) {
        return this.lambdaQuery()
                .eq(ClusterServiceRoleInstanceWebuis::getServiceRoleInstanceId, roleInstanceId)
                .one();
    }

    @Override
    public void removeByRoleInsIds(ArrayList<Integer> needRemoveList) {
        this.lambdaUpdate()
                .in(ClusterServiceRoleInstanceWebuis::getServiceRoleInstanceId, needRemoveList)
                .remove();
    }

    @Override
    public void updateWebUiToStandby(Integer roleInstanceId) {
        updateWebUiName(roleInstanceId, STANDBY);
    }

    @Override
    public List<ClusterServiceRoleInstanceWebuis> listWebUisByServiceInstanceId(Integer serviceInstanceId) {
        return this.list(
                new QueryWrapper<ClusterServiceRoleInstanceWebuis>()
                        .eq(Constants.SERVICE_INSTANCE_ID, serviceInstanceId));
    }

    private void updateWebUiName(Integer roleInstanceId, String state) {
        ClusterServiceRoleInstanceWebuis webuis =
                this.lambdaQuery()
                        .eq(
                                ClusterServiceRoleInstanceWebuis::getServiceRoleInstanceId,
                                roleInstanceId)
                        .one();
        String webuiName = webuis.getName();
        boolean needUpdate = false;
        if (webuiName.contains(ACTIVE) && STANDBY.equals(state)) {
            webuiName = webuiName.replace(ACTIVE, STANDBY);
            needUpdate = true;
        }
        if (webuiName.contains(STANDBY) && ACTIVE.equals(state)) {
            webuiName = webuiName.replace(STANDBY, ACTIVE);
            needUpdate = true;
        }
        webuis.setName(webuiName);
        if (!webuiName.contains(ACTIVE) && !webuiName.contains(STANDBY)) {
            webuis.setName(webuis.getName() + state);
            needUpdate = true;
        }
        if (needUpdate) {
            this.lambdaUpdate()
                    .eq(ClusterServiceRoleInstanceWebuis::getServiceRoleInstanceId, roleInstanceId)
                    .update(webuis);
        }
    }
}
