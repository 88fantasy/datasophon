package com.datasophon.api.service.instance.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datasophon.api.service.instance.K8sServiceInstanceService;
import com.datasophon.dao.entity.instance.K8sServiceInstance;
import com.datasophon.dao.mapper.instance.K8sServiceInstanceMapper;
import org.springframework.stereotype.Service;

/**
 * @author zhanghuangbin
 */
@Service("k8sServiceInstanceService")
public class K8sServiceInstanceServiceImpl extends ServiceImpl<K8sServiceInstanceMapper, K8sServiceInstance> implements K8sServiceInstanceService {

}
