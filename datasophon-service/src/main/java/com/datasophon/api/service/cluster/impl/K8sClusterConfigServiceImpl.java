package com.datasophon.api.service.cluster.impl;

import akka.actor.ActorRef;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.master.ActorUtils;
import com.datasophon.api.master.DispatcherK8sAgentActor;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.api.service.k8s.K8sService;
import com.datasophon.api.vo.k8s.K8sConnectionResult;
import com.datasophon.common.command.DispatcherK8sAgentCommand;
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

/**
 * @author zhanghuangbin
 */
@Slf4j
@Service("k8sClusterConfigService")
public class K8sClusterConfigServiceImpl extends ServiceImpl<K8sClusterConfigMapper, K8sClusterConfig> implements K8sClusterConfigService {


    @Autowired
    private ClusterInfoService clusterInfoService;

    @Autowired
    private K8sService k8sService;

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

        K8sConnectionResult result = k8sService.testConnection(config);
        if (!result.isSuccess()) {
            throw new BusinessHintException(String.format("集群联调性测试失败，%s", result.getInfo()));
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                ActorRef hostActor = ActorUtils.getLocalActor(DispatcherK8sAgentActor.class, "k8sAgentInstallActor-" + config.getClusterId());
                DispatcherK8sAgentCommand cmd = new DispatcherK8sAgentCommand();
                cmd.setClusterId(cluster.getId());
                hostActor.tell(cmd, ActorRef.noSender());
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
        lambdaUpdate().eq(K8sClusterConfig::getClusterId, clusterId).remove();
    }

}
