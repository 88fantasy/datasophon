package com.datasophon.api.service.extrepo.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.datasophon.api.dto.extrepo.DeploymentDTO;
import com.datasophon.api.dto.extrepo.RunDagDto;
import com.datasophon.api.service.extrepo.K8sProductInstallService;
import com.datasophon.api.service.frame.FrameK8sServiceService;
import com.datasophon.api.service.instance.K8sServiceInstanceValuesService;
import com.datasophon.api.vo.extrepo.InstallResult;
import com.datasophon.api.vo.extrepo.ValidateResultVO;
import com.datasophon.common.enums.CommandType;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.frame.FrameK8sServiceEntity;
import com.datasophon.dao.model.extrepo.DeploySrvModel;
import com.datasophon.dao.model.extrepo.DeploymentModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author zhanghuangbin
 */
@Component("k8SProductInstallService")
public class K8SProductInstallServiceImpl extends ProductDeployHandlerSupport implements K8sProductInstallService {


    @Autowired
    private FrameK8sServiceService frameK8sServiceService;

    @Autowired
    private K8sServiceInstanceValuesService k8sServiceInstanceValuesService;


    @Override
    public ValidateResultVO validateDeploymentModel(DeploymentModel model, DeploymentDTO dto) {
        List<String> errors = new ArrayList<>();
        List<DeploySrvModel> apps = getTargetApps(model);

        ClusterInfoEntity clusterInfo = clusterInfoService.getById(dto.getClusterId());
        List<FrameK8sServiceEntity> serviceList = frameK8sServiceService.getByFrameCode(clusterInfo.getClusterFrame());
        Map<String, FrameK8sServiceEntity> map = serviceList.stream().collect(Collectors.toMap(
                e-> e.getServiceName() + ":" + e.getServiceVersion(),
                e->e,
                (a,b)->a
        ));
        apps.forEach(app -> {
            FrameK8sServiceEntity entity = map.get(app.getName() + ":" + app.getVersion());
            if (entity == null) {
                errors.add(String.format("服务%s %s不存在", app.getName(), app.getVersion()));
            }
        });

        if (errors.isEmpty()) {
            ValidateResultVO vo = new ValidateResultVO();
            List<ValidateResultVO.DeployK8sServiceModel> services = new ArrayList<>();
            apps.forEach(app -> {
                app.getRoles().forEach(role -> {
                    ValidateResultVO.DeployK8sServiceModel tmp = new ValidateResultVO.DeployK8sServiceModel();
                    tmp.setServiceName(app.getName());
                    tmp.setVersion(app.getVersion());
                    tmp.setNamespace(app.getNamespace());
                    tmp.setMetaFileType(app.getMetaFileType().toLowerCase());
                    services.add(tmp);
                });
            });
            vo.setK8sServices(services);
            return vo;
        } else {
            return new ValidateResultVO(errors);
        }
    }

    @Override
    public InstallResult deploy(DeploymentDTO dto) {
        return null;
    }

    @Override
    public void redeploy(RunDagDto dto) {

    }

    @Override
    public String generateGenericInstallCommand(Integer clusterId, List<String> serviceNames) {
        return "";
    }

    @Override
    public String generateAndExecSrvInstCmd(Integer clusterId, CommandType commandType, List<Integer> serviceInstanceIds) {
        return "";
    }


    @Override
    public List<FrameK8sServiceEntity> listNewestByDeployment(DeploymentDTO dto) {
        DeploymentModel model = doParseDeploymentFile(dto);
        Map<String, DeploySrvModel> map = CollectionUtil.toMap(getTargetApps(model), new HashMap<>(), DeploySrvModel::getName);
        List<FrameK8sServiceEntity> list = frameK8sServiceService.listNewest(dto.getClusterId());
        list.forEach(et -> {
            DeploySrvModel srv = map.get(et.getServiceName());
            if (srv != null) {
                et.setSelected(true);
                et.setMetaFileType(srv.getMetaFileType().toLowerCase());
                et.setNamespace(srv.getNamespace());
            }
        });
        return list;
    }




    private List<DeploySrvModel> getTargetApps(DeploymentModel model) {
        return model.getApp()
                .stream()
                .filter(app -> app.getDeployType().equals("K8S"))
                .collect(Collectors.toList());
    }
}
