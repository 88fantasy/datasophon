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

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datasophon.api.service.FrameInfoService;
import com.datasophon.api.service.FrameServiceService;
import com.datasophon.api.service.frame.FrameK8sServiceService;
import com.datasophon.api.vo.frameinfo.FrameInfoVO;
import com.datasophon.common.utils.CollectionUtils;
import com.datasophon.dao.entity.FrameInfoEntity;
import com.datasophon.dao.entity.FrameServiceEntity;
import com.datasophon.dao.entity.frame.FrameK8sServiceEntity;
import com.datasophon.dao.mapper.FrameInfoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service("frameInfoService")
public class FrameInfoServiceImpl extends ServiceImpl<FrameInfoMapper, FrameInfoEntity> implements FrameInfoService {

    @Autowired
    private FrameServiceService frameServiceService;

    @Autowired
    private FrameK8sServiceService frameK8sServiceService;

    @Override
    public List<FrameInfoVO> getAllClusterFrame() {
        List<FrameInfoEntity> entities = this.list();
        if (CollectionUtils.isEmpty(entities)) {
            return new ArrayList<>(0);
        }

        List<FrameInfoVO> result = BeanUtil.copyToList(entities, FrameInfoVO.class);

        Set<Integer> frameInfoIds = entities.stream().map(FrameInfoEntity::getId).collect(Collectors.toSet());
        Map<Integer, List<FrameServiceEntity>> frameServiceGroupBys = frameServiceService.lambdaQuery()
                .select(FrameServiceEntity::getId, FrameServiceEntity::getFrameId, FrameServiceEntity::getFrameCode,
                        FrameServiceEntity::getServiceName, FrameServiceEntity::getServiceVersion,
                        FrameServiceEntity::getServiceDesc)
                .in(FrameServiceEntity::getFrameId, frameInfoIds)
                .orderByAsc(FrameServiceEntity::getServiceName)
                .list()
                .stream()
                .collect(Collectors.groupingBy(FrameServiceEntity::getFrameId));

        result.forEach(f -> f.setFrameServiceList(frameServiceGroupBys.get(f.getId())));


        Map<Integer, List<FrameK8sServiceEntity>> k8sServiceGroupBys = frameK8sServiceService.lambdaQuery()
                .select(FrameK8sServiceEntity::getId, FrameK8sServiceEntity::getFrameId,
                        FrameK8sServiceEntity::getServiceName, FrameK8sServiceEntity::getServiceVersion,
                        FrameK8sServiceEntity::getServiceDesc, FrameK8sServiceEntity::getSupportArtifacts)
                .in(FrameK8sServiceEntity::getFrameId, frameInfoIds)
                .orderByAsc(FrameK8sServiceEntity::getServiceName)
                .list()
                .stream()
                .collect(Collectors.groupingBy(FrameK8sServiceEntity::getFrameId));
        result.forEach(f -> f.setFrameK8sServiceList(k8sServiceGroupBys.get(f.getId())));

        return result;
    }

    @Override
    public FrameInfoEntity saveFrameIfAbsent(String frameCode) {
        FrameInfoEntity frameInfo = lambdaQuery().eq(FrameInfoEntity::getFrameCode, frameCode).one();
        if (Objects.isNull(frameInfo)) {
            frameInfo = new FrameInfoEntity();
            frameInfo.setFrameCode(frameCode);
            save(frameInfo);
        }
        return frameInfo;
    }

    @Override
    public FrameInfoEntity getByFrameCode(String frameCode) {
        return lambdaQuery().eq(FrameInfoEntity::getFrameCode, frameCode).one();
    }
}
