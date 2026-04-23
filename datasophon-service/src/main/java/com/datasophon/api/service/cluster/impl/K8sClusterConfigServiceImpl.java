package com.datasophon.api.service.cluster.impl;

import akka.actor.ActorRef;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.master.ActorUtils;
import com.datasophon.api.master.ClusterActor;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.agent.K8sAgentDeployService;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.common.command.ClusterCommand;
import com.datasophon.common.enums.ClusterCommandType;
import com.datasophon.common.k8s.config.ClientOptions;
import com.datasophon.common.k8s.config.KubeConfigParser;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.enums.ClusterArchType;
import com.datasophon.dao.enums.k8s.K8sAuthType;
import com.datasophon.dao.mapper.cluster.K8sClusterConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.CompletableFuture;

/**
 * @author zhanghuangbin
 */
@Slf4j
@Service("k8sClusterConfigService")
public class K8sClusterConfigServiceImpl extends ServiceImpl<K8sClusterConfigMapper, K8sClusterConfig> implements K8sClusterConfigService {


    @Autowired
    private ClusterInfoService clusterInfoService;

    @Autowired
    private K8sAgentDeployService k8sAgentDeployService;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public K8sClusterConfig saveOrUpdateConfig(K8sClusterConfig config) {
        ClusterInfoEntity cluster = clusterInfoService.getById(config.getClusterId());
        if (cluster == null || !ClusterArchType.k8s.equals(cluster.getArchType())) {
            // K8sClusterConfigServiceImpl.java:64
            throw new BusinessHintException("不能修改集群的 serverName");
        }
        String host;
        String cert;
        if (K8sAuthType.config_file.equals(config.getType())) {
            KubeConfigParser parser = new KubeConfigParser();
            ClientOptions options = parser.parse(config.getKubeConfig());
            host = options.getServerName();
            cert = options.getServerCert();
        } else {
            host = config.getServerHost();
            cert = config.getServerCert();
        }

        K8sClusterConfig db = getByClusterId(config.getClusterId());
        if (db == null) {
            db = BeanUtil.toBean(config, K8sClusterConfig.class);
            db.setServerHost(host);
            db.setServerCert(cert);
            save(db);
        } else {
            if (!db.getServerHost().equals(host)) {
                throw new BusinessHintException("不能修改集群的serverName");
            }
            BeanUtil.copyProperties(config, db, CopyOptions.create().setIgnoreProperties(K8sClusterConfig::getId));
            db.setServerHost(host);
            db.setServerCert(cert);
            updateById(db);
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                ActorRef hostActor = ActorUtils.getLocalActor(ClusterActor.class, "clusterActor-" + config.getClusterId());
                hostActor.tell(new ClusterCommand(ClusterCommandType.CHECK, config.getClusterId()), ActorRef.noSender());

                // 异步部署 K8s Agent
                CompletableFuture.runAsync(() -> {
                    try {
                        k8sAgentDeployService.deployAgent(config);
                    } catch (Exception e) {
                        log.error("异步部署 K8s Agent 失败：clusterId={}", config.getClusterId(), e);
                    }
                });
            }
        });
        return db;
    }

    @Override
    public K8sClusterConfig getByClusterId(Integer clusterId) {
        return lambdaQuery().eq(K8sClusterConfig::getClusterId, clusterId).one();
    }

    @Override
    public K8sClusterConfig getInitConfig(Integer clusterId) {
        K8sClusterConfig config = getByClusterId(clusterId);
        if (config == null) {
            throw new BusinessHintException("集群未初始化");
        }
        return config;
    }

    @Override
    public void removeByClusterId(Integer clusterId) {
        // 先获取集群配置，用于卸载 Agent
        K8sClusterConfig config = getByClusterId(clusterId);
        if (config != null) {
            // 同步卸载 Agent
            try {
                k8sAgentDeployService.undeployAgent(config);
                log.info("已成功卸载 K8s Agent: clusterId={}", clusterId);
            } catch (Exception e) {
                log.error("卸载 K8s Agent 失败：clusterId={}", clusterId, e);
                // 继续删除配置，不阻断删除流程
            }
        }

        // 删除集群配置
        lambdaUpdate().eq(K8sClusterConfig::getClusterId, clusterId).remove();
    }

}
