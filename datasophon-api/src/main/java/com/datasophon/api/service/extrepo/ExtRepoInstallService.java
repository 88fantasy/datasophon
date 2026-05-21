package com.datasophon.api.service.extrepo;

import com.datasophon.api.dto.extrepo.DeploymentDTO;
import com.datasophon.api.dto.extrepo.RunDagDto;
import com.datasophon.api.vo.extrepo.InstallResult;
import com.datasophon.api.vo.extrepo.ValidateResultVO;
import com.datasophon.common.enums.CommandType;
import com.datasophon.dao.model.extrepo.DeploymentModel;

import java.util.List;

/**
 * @author zhanghuangbin
 */
public interface ExtRepoInstallService {

    ValidateResultVO validateDeploymentModel(DeploymentModel model, DeploymentDTO dto);

    InstallResult deploy(DeploymentDTO dto);

    void redeploy(RunDagDto dto);

    String generateGenericInstallCommand(Integer clusterId, List<String> serviceNames);

    String generateAndExecSrvInstCmd(Integer clusterId, CommandType commandType, List<Integer> serviceInstanceIds);
}
