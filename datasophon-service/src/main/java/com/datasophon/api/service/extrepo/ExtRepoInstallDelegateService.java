package com.datasophon.api.service.extrepo;

import com.datasophon.api.dto.extrepo.DeploymentDTO;
import com.datasophon.api.dto.extrepo.RunDagDto;
import com.datasophon.api.vo.extrepo.InstallProgressDAG;
import com.datasophon.api.vo.extrepo.InstallResult;
import com.datasophon.api.vo.extrepo.ValidateResultVO;
import com.datasophon.common.enums.CommandType;

import java.util.List;

/**
 * @author zhanghuangbin
 * @date 2025/11/18
 */
public interface ExtRepoInstallDelegateService {

    ValidateResultVO validDeploymentFile(DeploymentDTO dto);

    InstallResult deploy(DeploymentDTO dto);


    void redeploy(RunDagDto dto);


    InstallProgressDAG getDeployProgressDAG2(String dagId);


    String generateGenericInstallCommand(Integer clusterId, List<String> serviceNames);

    String generateAndExecSrvInstCmd(Integer clusterId, CommandType command, List<Integer> ids);




}
