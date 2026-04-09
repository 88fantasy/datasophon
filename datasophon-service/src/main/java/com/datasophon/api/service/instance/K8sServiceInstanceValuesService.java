package com.datasophon.api.service.instance;

import com.baomidou.mybatisplus.extension.service.IService;
import com.datasophon.api.dto.instance.K8sServiceInstanceValuesSaveDTO;
import com.datasophon.api.dto.instance.K8sServiceInstanceValuesUpdateDTO;
import com.datasophon.api.vo.instance.K8sServiceInstanceValuesVO;
import com.datasophon.dao.entity.instance.K8sServiceInstanceValues;

import java.util.List;

/**
 * @author zhanghuangbin
 */
public interface K8sServiceInstanceValuesService extends IService<K8sServiceInstanceValues> {



    List<K8sServiceInstanceValues> listSimpleByInstanceId(Integer instanceId);

    K8sServiceInstanceValuesVO getValueFromRepo(Integer serviceId, String artifactType);


    K8sServiceInstanceValues save(K8sServiceInstanceValuesSaveDTO values);

    K8sServiceInstanceValues update(K8sServiceInstanceValuesUpdateDTO values);

    K8sServiceInstanceValues getNewestValuesByInstanceId(Integer instanceId);

    void removeByInstanceId(Integer instanceId);
}
