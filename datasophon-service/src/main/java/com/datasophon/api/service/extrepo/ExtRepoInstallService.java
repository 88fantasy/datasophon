package com.datasophon.api.service.extrepo;

import com.datasophon.api.dto.extrepo.DeploymentDTO;
import com.datasophon.api.vo.extrepo.InstallProgressDAG;

import java.util.List;

/**
 * @author zhanghuangbin
 * @date 2025/11/18
 */
public interface ExtRepoInstallService {


    List<String> deploy(DeploymentDTO dto);


    InstallProgressDAG getDeployProgressDAG(Integer clusterId, List<String> cmdIds);
}
