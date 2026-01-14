package com.datasophon.api.service.extrepo;

import com.datasophon.api.dto.extrepo.DeploymentDTO;
import com.datasophon.api.vo.extrepo.InstallProgressDAG2;
import com.datasophon.api.vo.extrepo.InstallResult;
import com.datasophon.api.vo.extrepo.ValidateResultVO;

/**
 * @author zhanghuangbin
 * @date 2025/11/18
 */
public interface ExtRepoInstallService {

    ValidateResultVO validDeploymentFile(DeploymentDTO dto);

    InstallResult deploy(DeploymentDTO dto);


    void redeploy(String dagId);


    InstallProgressDAG2 getDeployProgressDAG2(String dagId);


}
