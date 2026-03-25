package com.datasophon.api.service.instance.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datasophon.api.service.instance.K8sServiceInstanceValuesService;
import com.datasophon.dao.entity.instance.K8sServiceInstanceValues;
import com.datasophon.dao.mapper.instance.K8sServiceInstanceValuesMapper;
import org.springframework.stereotype.Service;

/**
 * @author zhanghuangbin
 */
@Service("k8sServiceInstanceValuesService")
public class K8sServiceInstanceValuesServiceImpl extends ServiceImpl<K8sServiceInstanceValuesMapper, K8sServiceInstanceValues> implements K8sServiceInstanceValuesService {

}
