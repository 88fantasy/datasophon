package com.datasophon.api.service.extrepo;

import com.datasophon.api.dto.extrepo.DeploymentDTO;
import com.datasophon.api.dto.extrepo.ServiceRoleQueryDTO;
import com.datasophon.common.enums.CommandType;
import com.datasophon.dao.entity.FrameServiceEntity;
import com.datasophon.dao.entity.FrameServiceRoleEntity;

import java.util.List;
import java.util.Map;

/**
 * @author zhanghuangbin
 */
public interface VosProductInstallService extends ExtRepoInstallService {

    String generateAndExecSrvRoleCmd(Integer clusterId, CommandType command, Integer serviceInstanceId, List<Integer> ids);

    void generateAndExecSrvRoleCommands(Integer clusterId, CommandType commandType, Map<Integer, List<Integer>> instanceIdMap);

    List<FrameServiceEntity> listNewestByDeployment(DeploymentDTO dto);

    List<FrameServiceRoleEntity> getServiceRoleListByDeployment(ServiceRoleQueryDTO dto);


    List<FrameServiceRoleEntity> getNonMasterRoleListByDeployment(DeploymentDTO dto);
}
