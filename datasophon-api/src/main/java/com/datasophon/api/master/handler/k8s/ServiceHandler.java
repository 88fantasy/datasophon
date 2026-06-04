package com.datasophon.api.master.handler.k8s;

import com.datasophon.api.log.ExecLogConstant;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.api.service.cmd.ClusterK8sServiceCommandService;
import com.datasophon.api.service.instance.K8sServiceInstanceService;
import com.datasophon.api.service.k8s.K8sService;
import com.datasophon.common.Constants;
import com.datasophon.common.model.k8s.K8sServiceNode;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.entity.cmd.ClusterK8sServiceCommandEntity;

import lombok.Data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.hutool.extra.spring.SpringUtil;

/**
 * K8s 服务处理器基类
 * 提供通用的 K8s 集群配置获取方法
 * 支持责任链模式，可通过 thenNext 链接多个处理器
 *
 * @author zhanghuangbin
 */
@Data
public abstract class ServiceHandler {
    
    protected final K8sClusterConfigService configService;
    
    protected final K8sService k8sService;
    
    protected final K8sServiceInstanceService instanceService;
    
    private final ClusterK8sServiceCommandService commandService;
    
    private ServiceHandler next;
    
    protected K8sClusterConfig config;
    
    protected Logger logger;
    
    private Logger innerLogger;
    
    public ServiceHandler() {
        configService = SpringUtil.getBean(K8sClusterConfigService.class);
        k8sService = SpringUtil.getBean(K8sService.class);
        instanceService = SpringUtil.getBean(K8sServiceInstanceService.class);
        commandService = SpringUtil.getBean(ClusterK8sServiceCommandService.class);
    }
    
    protected void init(K8sServiceNode node) {
        innerLogger = LoggerFactory.getLogger(ExecLogConstant.createLoggerName(node.getServiceName(), node.getNamespace(), ServiceHandler.class));
        logger = LoggerFactory.getLogger(ExecLogConstant.createLoggerName(node.getServiceName(), node.getNamespace(), this.getClass()));
        config = getK8sConfig(node.getClusterId());
    }
    
    /**
     * 获取 K8s 集群配置
     * 根据集群 ID 从数据库获取 K8s 连接配置信息
     *
     * @param clusterId 集群 ID
     * @return K8s 集群配置
     * @throws IllegalStateException 当集群 ID 对应的 K8s 配置不存在时抛出
     */
    protected K8sClusterConfig getK8sConfig(Integer clusterId) {
        innerLogger.info("获取集群 {} 的 K8s 配置", clusterId);
        K8sClusterConfig config = configService.getByClusterId(clusterId);
        if (config == null) {
            innerLogger.error("集群 {} 未配置 K8s 连接信息", clusterId);
            throw new IllegalStateException("集群 " + clusterId + " 未配置 K8s 连接信息");
        }
        innerLogger.info("集群 {} 的 K8s 配置获取成功，serverHost:{}", clusterId, config.getServerHost());
        return config;
    }
    
    public ExecResult invoke(K8sServiceNode serviceNode) throws Exception {
        init(serviceNode);
        return handlerRequest(serviceNode);
    }
    
    /**
     * 处理请求
     * 子类实现具体的业务逻辑
     *
     * @param serviceNode K8s 服务节点
     * @return 执行结果
     * @throws Exception 处理过程中可能发生的异常
     */
    public abstract ExecResult handlerRequest(K8sServiceNode serviceNode) throws Exception;
    
    /**
     * 链接下一个处理器
     * 构建责任链
     *
     * @param next 下一个处理器
     * @return 下一个处理器，便于链式调用
     */
    public ServiceHandler thenNext(ServiceHandler next) {
        this.next = next;
        return next;
    }
    
    /**
     * 调用下一个处理器
     * 如果当前执行成功且存在下一个处理器，则继续执行
     *
     * @param serviceNode K8s 服务节点
     * @param lastResult  上一次执行结果
     * @return 执行结果
     * @throws Exception 处理过程中可能发生的异常
     */
    protected ExecResult invokeNext(K8sServiceNode serviceNode, ExecResult lastResult) throws Exception {
        boolean canGoOn = lastResult != null && lastResult.isSuccess() && next != null;
        if (!canGoOn) {
            innerLogger.info("责任链执行终止，成功：{}, 存在下一个处理器：{}", lastResult != null && lastResult.isSuccess(), next != null);
            return lastResult;
        }
        innerLogger.info("继续执行责任链中的下一个处理器");
        return next.handlerRequest(serviceNode);
    }
    
    protected void updateCmdProgress(K8sServiceNode serviceNode, Integer progress) {
        innerLogger.info("更新{}{}的进度为{}%", serviceNode.getCommandType().getCommandName(Constants.CN), serviceNode.getServiceName(), progress);
        commandService.lambdaUpdate()
                .eq(ClusterK8sServiceCommandEntity::getCommandId, serviceNode.getCommandId())
                .set(ClusterK8sServiceCommandEntity::getCommandProgress, progress)
                .update();
    }
    
}
