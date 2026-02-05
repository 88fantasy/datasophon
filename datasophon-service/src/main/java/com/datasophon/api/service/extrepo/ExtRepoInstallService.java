package com.datasophon.api.service.extrepo;

import com.datasophon.api.dto.extrepo.DeploymentDTO;
import com.datasophon.api.dto.extrepo.RunDagDto;
import com.datasophon.api.dto.extrepo.ServiceRoleQueryDTO;
import com.datasophon.api.vo.extrepo.InstallProgressDAG2;
import com.datasophon.api.vo.extrepo.InstallResult;
import com.datasophon.api.vo.extrepo.ValidateResultVO;
import com.datasophon.common.enums.CommandType;
import com.datasophon.dao.entity.FrameServiceEntity;
import com.datasophon.dao.entity.FrameServiceRoleEntity;

import java.util.List;
import java.util.Map;

/**
 * @author zhanghuangbin
 * @date 2025/11/18
 */
public interface ExtRepoInstallService {

    ValidateResultVO validDeploymentFile(DeploymentDTO dto);

    InstallResult deploy(DeploymentDTO dto);


    void redeploy(RunDagDto dto);


    InstallProgressDAG2 getDeployProgressDAG2(String dagId);



    String generateGenericInstallCommand(Integer clusterId, List<String> serviceNames);

    String generateAndExecSrvInstCmd(Integer clusterId, CommandType command, List<Integer> ids);

    String generateAndExecSrvRoleCmd(Integer clusterId, CommandType command, Integer serviceInstanceId, List<Integer> ids);


    void generateAndExecSrvRoleCommands(Integer clusterId, CommandType commandType, Map<Integer, List<Integer>> instanceIdMap);

    List<FrameServiceEntity> listNewestByDeployment(DeploymentDTO dto);

    List<FrameServiceRoleEntity> getServiceRoleListByDeployment(ServiceRoleQueryDTO dto);


    List<FrameServiceRoleEntity> getNonMasterRoleListByDeployment(DeploymentDTO dto);
}
