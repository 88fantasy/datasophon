package com.datasophon.api.service.log.impl;

import com.datasophon.api.dto.log.K8sRuntimeEventQueryDTO;
import com.datasophon.api.dto.log.K8sRuntimeLogQueryDTO;
import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.log.ExecLogConstant;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.api.service.cluster.K8sClusterNamespaceService;
import com.datasophon.api.service.cmd.ClusterK8sServiceCommandService;
import com.datasophon.api.service.instance.K8sServiceInstanceService;
import com.datasophon.api.service.k8s.K8sService;
import com.datasophon.api.service.log.K8sProductService;
import com.datasophon.api.vo.k8s.K8sEventInfo;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.entity.cluster.K8sClusterNamespace;
import com.datasophon.dao.entity.cmd.ClusterK8sServiceCommandEntity;
import com.datasophon.dao.entity.instance.K8sServiceInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * @author zhanghuangbin
 */
@Service("k8sProductService")
public class K8sProductServiceImpl implements K8sProductService {

    @Autowired
    private ClusterK8sServiceCommandService clusterK8sServiceCommandService;

    @Autowired
    private K8sService k8sService;

    @Autowired
    private K8sServiceInstanceService k8sServiceInstanceService;

    @Autowired
    private K8sClusterNamespaceService k8sClusterNamespaceService;
    @Autowired
    private K8sClusterConfigService k8sClusterConfigService;


    @Override
    public String getK8sExecLog(String commandId, int rows) {
        ClusterK8sServiceCommandEntity cmd = clusterK8sServiceCommandService.getById(commandId);
        if (cmd == null) {
            throw new BusinessHintException("命令不存在");
        }
        String path =  String.format("logs/%s/%s%s.log", cmd.getServiceName(), ExecLogConstant.LOGGER_FILE_PREFIX, cmd.getNamespace());
        return LogSupport.getMasterLog(path, rows);
    }

    @Override
    public String getK8sRuntimeLog(K8sRuntimeLogQueryDTO dto) {
        K8sServiceInstance instance = k8sServiceInstanceService.getById(dto.getInstanceId());
        Objects.requireNonNull(instance);

        K8sClusterNamespace ns = k8sClusterNamespaceService.getById(instance.getNamespaceId());
        K8sClusterConfig config = k8sClusterConfigService.getInitConfig(ns.getClusterId());

        return k8sService.getPodLog(config, dto);
    }

    @Override
    public List<K8sEventInfo> getK8sEvents(K8sRuntimeEventQueryDTO query) {
        K8sServiceInstance instance = k8sServiceInstanceService.getById(query.getInstanceId());
        Objects.requireNonNull(instance);

        K8sClusterNamespace ns = k8sClusterNamespaceService.getById(instance.getNamespaceId());
        K8sClusterConfig config = k8sClusterConfigService.getInitConfig(ns.getClusterId());
        return k8sService.listK8sServiceInstanceEvents(config, query);
    }
}
