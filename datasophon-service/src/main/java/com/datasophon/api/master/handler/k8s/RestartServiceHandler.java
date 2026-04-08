package com.datasophon.api.master.handler.k8s;

import com.datasophon.api.dto.instance.K8sServiceInstanceQueryDTO;
import com.datasophon.api.vo.k8s.K8sDeploymentInfo;
import com.datasophon.common.model.k8s.K8sServiceNode;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.vo.instance.K8sServiceInstanceVO;

import java.util.List;

/**
 * K8s 服务重启处理器
 * 负责重启 K8s 服务关联的所有 Deployment
 *
 * @author zhanghuangbin
 */
public class RestartServiceHandler extends ServiceHandler {
    

    @Override
    public ExecResult handlerRequest(K8sServiceNode serviceNode) throws Exception {
        logger.info("开始重启 K8s 服务，服务名：{}, 服务实例 ID:{}", serviceNode.getServiceName(), serviceNode.getServiceInstanceId());

        // 获取 K8s 集群配置
        K8sClusterConfig config = getK8sConfig(serviceNode.getClusterId());

        // 获取服务实例信息
        K8sServiceInstanceVO vo = instanceService.getVoById(serviceNode.getServiceInstanceId());

        // 构建查询条件
        K8sServiceInstanceQueryDTO query = new K8sServiceInstanceQueryDTO();
        query.setInstanceId(serviceNode.getServiceInstanceId());

        // 获取该服务关联的所有 Deployment
        List<K8sDeploymentInfo> deployments = k8sService.listDeployments(config, query);
        logger.info("找到 {} 个 Deployment 需要重启", deployments.size());
        updateCmdProgress(serviceNode, 10);

        // 重启所有 Deployment
        k8sService.restartDeployment(config, deployments);
        logger.info("服务{}重启成功", serviceNode.getServiceName());
        updateCmdProgress(serviceNode, 80);

        return ExecResult.success(String.format("重启服务%s 成功", serviceNode.getServiceName()));
    }
}
