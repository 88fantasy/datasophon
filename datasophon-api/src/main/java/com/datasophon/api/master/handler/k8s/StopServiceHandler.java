package com.datasophon.api.master.handler.k8s;

import com.datasophon.api.dto.instance.K8sServiceInstanceQueryDTO;
import com.datasophon.api.vo.k8s.K8sDeploymentInfo;
import com.datasophon.common.model.k8s.K8sServiceNode;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * K8s 服务停止处理器
 * 负责将 K8s 服务的 Deployment 副本数缩容至 0，实现服务停止
 *
 * @author zhanghuangbin
 */
@Slf4j
public class StopServiceHandler extends ServiceHandler {


    @Override
    public ExecResult handlerRequest(K8sServiceNode serviceNode) throws Exception {
        logger.info("开始停止 K8s 服务，服务名：{}, 服务实例 ID:{}", serviceNode.getServiceName(), serviceNode.getServiceInstanceId());

        // 获取 K8s 集群配置
        K8sClusterConfig config = getK8sConfig(serviceNode.getClusterId());

        // 获取服务实例信息

        // 构建查询条件
        K8sServiceInstanceQueryDTO query = new K8sServiceInstanceQueryDTO();
        query.setInstanceId(serviceNode.getServiceInstanceId());

        // 获取该服务关联的所有 Deployment
        List<K8sDeploymentInfo> deployments = k8sService.listDeployments(config, query);
        logger.info("找到 {} 个 Deployment 需要停止", deployments.size());
        updateCmdProgress(serviceNode, 10);

        // 将所有 Deployment 的副本数缩容至 0
        k8sService.scaleDeployments(config, deployments, 0);
        logger.info("服务{}停止成功", serviceNode.getServiceName());
        updateCmdProgress(serviceNode, 90);

        return ExecResult.success(String.format("停止%s 成功", serviceNode.getServiceName()));
    }
}
