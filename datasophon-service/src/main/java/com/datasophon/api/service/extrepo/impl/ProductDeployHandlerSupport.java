package com.datasophon.api.service.extrepo.impl;

import com.datasophon.api.dto.extrepo.DeploymentDTO;
import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.cmd.ClusterServiceCommandHostCommandService;
import com.datasophon.api.service.cmd.ClusterServiceCommandHostService;
import com.datasophon.api.service.cmd.ClusterServiceCommandService;
import com.datasophon.api.service.dag.DAGService;
import com.datasophon.api.service.extrepo.utils.MetaUtils;
import com.datasophon.api.service.tmpfile.UploadTempFileService;
import com.datasophon.api.utils.TransactionalUtils;
import com.datasophon.common.utils.YamlUtils;
import com.datasophon.dao.mapper.dag.DagDefinitionEntityMapper;
import com.datasophon.dao.model.extrepo.DeploymentModel;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;

/**
 * @author zhanghuangbin
 */
public class ProductDeployHandlerSupport {

    @Autowired
    protected UploadTempFileService uploadTempFileService;


    @Autowired
    protected ClusterInfoService clusterInfoService;




    @Autowired
    protected ClusterServiceCommandHostService commandHostService;

    @Autowired
    protected ClusterServiceCommandHostCommandService hostCommandService;


    @Autowired
    protected ClusterServiceCommandService commandService;


    @Autowired
    protected DAGService dagService;


    @Autowired
    protected DagDefinitionEntityMapper dagDefinitionEntityMapper;

    @Autowired
    protected TransactionalUtils transactionalUtils;
    
    protected DeploymentModel doParseDeploymentFile(DeploymentDTO dto) {
        File deploymentFile = uploadTempFileService.getTempFile(dto.getDeployFileId());
        if (deploymentFile == null) {
            throw new BusinessHintException("部署清单文件不存在");
        }
        String content = MetaUtils.decodeFile(deploymentFile, dto.getContentDecodePasswd());
        DeploymentModel model = YamlUtils.parseYaml(content, DeploymentModel.class);
        return model;
    }

}
