package com.datasophon.api.service.extrepo;

import com.datasophon.api.dto.extrepo.DeploymentDTO;
import com.datasophon.dao.entity.frame.FrameK8sServiceEntity;

import java.util.List;

/**
 * @author zhanghuangbin
 */
public interface K8sProductInstallService extends ExtRepoInstallService {


    List<FrameK8sServiceEntity> listNewestByDeployment(DeploymentDTO dto);


}
