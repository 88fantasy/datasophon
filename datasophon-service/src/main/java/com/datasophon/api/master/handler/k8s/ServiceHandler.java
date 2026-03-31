package com.datasophon.api.master.handler.k8s;

import cn.hutool.extra.spring.SpringUtil;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.common.model.k8s.K8sServiceNode;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import lombok.Data;

@Data
public abstract class ServiceHandler {

    private ServiceHandler next;

    protected K8sClusterConfigService configService;

    protected K8sClusterConfig config;

    protected ServiceHandler() {
        configService = SpringUtil.getBean(K8sClusterConfigService.class);
    }

    protected K8sClusterConfig getK8sConfig(Integer clusterId) {
        // 1. 获取 K8sClusterConfig
        K8sClusterConfig config = configService.getByClusterId(clusterId);
        if (config == null) {
            throw new IllegalStateException("集群 " + clusterId + " 未配置 K8s 连接信息");
        }
        return config;
    }


    public abstract ExecResult handlerRequest(K8sServiceNode serviceNode) throws Exception;


    public ServiceHandler thenNext(ServiceHandler next) {
        this.next = next;
        return next;
    }

    public ExecResult invokeNext(K8sServiceNode serviceNode, ExecResult lastResult) throws Exception {
        boolean canGoOn = lastResult != null && lastResult.isSuccess() && next != null;
        if (!canGoOn) {
            return lastResult;
        }
        return next.handlerRequest(serviceNode);
    }
}