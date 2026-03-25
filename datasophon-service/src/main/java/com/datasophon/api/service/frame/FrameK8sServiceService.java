package com.datasophon.api.service.frame;

import com.baomidou.mybatisplus.extension.service.IService;
import com.datasophon.dao.entity.frame.FrameK8sServiceEntity;

import java.util.List;

/**
 * @author zhanghuangbin
 */
public interface FrameK8sServiceService extends IService<FrameK8sServiceEntity> {

    List<FrameK8sServiceEntity> listSimpleService(List<Integer> frameIds);
}
