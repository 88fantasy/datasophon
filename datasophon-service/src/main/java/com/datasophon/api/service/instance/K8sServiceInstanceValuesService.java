package com.datasophon.api.service.instance;

import com.baomidou.mybatisplus.extension.service.IService;
import com.datasophon.dao.entity.instance.K8sServiceInstanceValues;

/**
 * @author zhanghuangbin
 */
public interface K8sServiceInstanceValuesService extends IService<K8sServiceInstanceValues> {

    String VOS_VALUES_TYPE = "vos_values";

    K8sServiceInstanceValues getByInstanceId(Integer instanceId);
}
