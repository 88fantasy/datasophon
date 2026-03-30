package com.datasophon.api.service.extrepo;

import com.datasophon.api.dto.extrepo.DeploymentDTO;
import com.datasophon.api.dto.extrepo.K8sProductDeployMapping;
import com.datasophon.api.dto.instance.K8sServiceInstanceValuesSaveDTO;
import com.datasophon.dao.entity.frame.FrameK8sServiceEntity;

import java.util.List;

/**
 * @author zhanghuangbin
 */
public interface K8sProductInstallService extends ExtRepoInstallService {


    List<FrameK8sServiceEntity> listNewestByDeployment(DeploymentDTO dto);

    void saveServiceNamespaceMapping(Integer clusterId, List<K8sProductDeployMapping> mappings);


    Integer saveConfigValues(K8sServiceInstanceValuesSaveDTO  dto);
}
