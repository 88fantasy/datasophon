package com.datasophon.api.service.extrepo;

import com.datasophon.api.dto.extrepo.DeploymentDTO;
import com.datasophon.api.dto.extrepo.InstallComponentDTO;
import com.datasophon.api.vo.extrepo.DeploymentDAG;
import com.datasophon.api.vo.extrepo.ImportCompProgressVO;
import com.datasophon.api.vo.extrepo.ValidateResultVO;

import java.util.List;

/**
 * 软件外部源的元数据业务逻辑处理类
 * @author zhanghuangbin
 * @date 2025/11/7
 */
public interface ExtRepoMetaService {

    ValidateResultVO validMetaFile(InstallComponentDTO dto);

    ValidateResultVO validatePkgFile(InstallComponentDTO dto);

    ImportCompProgressVO importCmp(InstallComponentDTO dto);


    ImportCompProgressVO queryProgress(Integer progressId);

    void clearProgressCache();


    DeploymentDAG buildDeploymentDAG(DeploymentDTO dto);

    List<String> deploy(DeploymentDTO dto);

}
