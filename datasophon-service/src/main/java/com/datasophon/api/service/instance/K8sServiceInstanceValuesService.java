package com.datasophon.api.service.instance;

import com.baomidou.mybatisplus.extension.service.IService;
import com.datasophon.api.dto.instance.K8sServiceInstanceValuesSaveDTO;
import com.datasophon.api.dto.instance.K8sServiceInstanceValuesUpdateDTO;
import com.datasophon.dao.entity.instance.K8sServiceInstanceValues;

import java.util.List;

/**
 * @author zhanghuangbin
 */
public interface K8sServiceInstanceValuesService extends IService<K8sServiceInstanceValues> {

    String VOS_VALUES_TYPE = "vos_values";


    List<K8sServiceInstanceValues> listSimpleByInstanceId(Integer instanceId);

    String getValueFromRepo(Integer serviceId, String artifactType);


    K8sServiceInstanceValues save(K8sServiceInstanceValuesSaveDTO values);

    K8sServiceInstanceValues update(K8sServiceInstanceValuesUpdateDTO values);
}
