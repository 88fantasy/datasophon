package com.datasophon.api.master.handler.k8s;

import cn.hutool.extra.spring.SpringUtil;
import com.datasophon.api.dto.instance.K8sServiceInstanceQueryDTO;
import com.datasophon.api.service.instance.K8sServiceInstanceService;
import com.datasophon.api.service.k8s.K8sService;
import com.datasophon.api.vo.k8s.K8sDeploymentInfo;
import com.datasophon.common.model.k8s.K8sServiceNode;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.vo.instance.K8sServiceInstanceVO;

import java.util.List;

/**
 * @author zhanghuangbin
 */
public class RestartServiceHandler extends ServiceHandler {

    private final K8sService k8sService;


    private final K8sServiceInstanceService instanceService;


    public RestartServiceHandler() {
        k8sService = SpringUtil.getBean(K8sService.class);
        instanceService = SpringUtil.getBean(K8sServiceInstanceService.class);
    }

    @Override
    public ExecResult handlerRequest(K8sServiceNode serviceNode) throws Exception {
        K8sClusterConfig config = getK8sConfig(serviceNode.getClusterId());
        K8sServiceInstanceVO vo = instanceService.getVoById(serviceNode.getServiceInstanceId());

        K8sServiceInstanceQueryDTO query = new K8sServiceInstanceQueryDTO();
        query.setInstanceId(serviceNode.getServiceInstanceId());
        query.setNamespace(vo.getNamespace());
        List<K8sDeploymentInfo> deployments = k8sService.listDeployments(config, query);

        k8sService.restartDeployment(config, deployments);
        return ExecResult.success(String.format("重启服务%s成功", serviceNode.getServiceName()));
    }
}
