package com.datasophon.api.service.cluster.impl;

import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.master.service.DispatcherK8sAgentService;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.api.service.k8s.K8sDashboardCollectorService;
import com.datasophon.api.service.k8s.K8sService;
import com.datasophon.api.vo.k8s.K8sConnectionResult;
import com.datasophon.common.command.DispatcherK8sAgentCommand;
import com.datasophon.common.k8s.config.ClientOptions;
import com.datasophon.common.k8s.config.KubeConfigParser;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.enums.ClusterArchType;
import com.datasophon.dao.enums.ManageMode;
import com.datasophon.dao.enums.k8s.K8sAuthType;
import com.datasophon.dao.mapper.cluster.K8sClusterConfigMapper;

import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import lombok.extern.slf4j.Slf4j;

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

    @Autowired
    private DispatcherK8sAgentService dispatcherK8sAgentService;

    @Autowired
    private K8sDashboardCollectorService k8sDashboardCollectorService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public K8sClusterConfig saveOrUpdateConfig(K8sClusterConfig config) {
        ClusterInfoEntity cluster = clusterInfoService.getById(config.getClusterId());
        if (cluster == null || !ClusterArchType.k8s.equals(cluster.getArchType())) {
            throw new BusinessHintException("K8s 集群不存在");
        }
        K8sClusterConfig db = getByClusterId(config.getClusterId());
        K8sClusterConfig effectiveConfig = mergeStoredCredential(config, db);
        requireCredential(effectiveConfig);
        String host;
        String cert;
        if (K8sAuthType.config_file.equals(effectiveConfig.getType())) {
            KubeConfigParser parser = new KubeConfigParser();
            ClientOptions options = parser.parse(effectiveConfig.getKubeConfig());
            host = options.getServerName();
            cert = options.getServerCert();
        } else {
            host = effectiveConfig.getServerHost();
            cert = effectiveConfig.getServerCert();
        }

        if (db == null) {
            db = BeanUtil.toBean(effectiveConfig, K8sClusterConfig.class);
            db.setServerHost(host);
            db.setServerCert(cert);
            save(db);
        } else {
            if (!db.getServerHost().equals(host)) {
                throw new BusinessHintException("不能修改集群的serverName");
            }
            BeanUtil.copyProperties(effectiveConfig, db, CopyOptions.create().setIgnoreProperties(K8sClusterConfig::getId));
            db.setServerHost(host);
            db.setServerCert(cert);
            updateById(db);
        }

        K8sConnectionResult result = k8sService.testConnection(effectiveConfig);
        if (!result.isSuccess()) {
            throw new BusinessHintException(String.format("集群联调性测试失败，%s", result.getInfo()));
        }

        if (!ManageMode.IMPORTED.equals(cluster.getManageMode())) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    DispatcherK8sAgentCommand cmd = new DispatcherK8sAgentCommand();
                    cmd.setClusterId(cluster.getId());
                    dispatcherK8sAgentService.dispatchK8sAgent(cmd);
                    k8sDashboardCollectorService.install(cluster.getId());
                }
            });
        }
        return db;
    }

    @Override
    public K8sConnectionResult testConnection(K8sClusterConfig config) {
        return k8sService.testConnection(mergeStoredCredential(config, getByClusterId(config.getClusterId())));
    }

    private K8sClusterConfig mergeStoredCredential(K8sClusterConfig config, K8sClusterConfig stored) {
        K8sClusterConfig merged = BeanUtil.toBean(config, K8sClusterConfig.class);
        K8sAuthType type = merged.getType();
        if (type == null) {
            return merged;
        }
        boolean sameType = stored != null && Objects.equals(stored.getType(), type);
        switch (type) {
            case config_file -> {
                if (sameType && StringUtils.isBlank(merged.getKubeConfig())) {
                    merged.setKubeConfig(stored.getKubeConfig());
                }
                merged.setToken(null);
                merged.setUsername(null);
                merged.setPassword(null);
            }
            case token -> {
                if (sameType && StringUtils.isBlank(merged.getToken())) {
                    merged.setToken(stored.getToken());
                }
                merged.setKubeConfig(null);
                merged.setUsername(null);
                merged.setPassword(null);
            }
            case password -> {
                if (sameType && StringUtils.isBlank(merged.getPassword())) {
                    merged.setPassword(stored.getPassword());
                }
                merged.setKubeConfig(null);
                merged.setToken(null);
            }
        }
        return merged;
    }

    /**
     * 合并已存凭据后校验：首次配置（或已存凭据为空）时缺失凭据必须显式拒绝，
     * 避免 {@code parser.parse(null)} 等空值一路传导到 NPE 500。
     */
    private void requireCredential(K8sClusterConfig config) {
        K8sAuthType type = config.getType();
        if (type == null) {
            throw new BusinessHintException("连接集群方式不能为空");
        }
        switch (type) {
            case config_file -> {
                if (StringUtils.isBlank(config.getKubeConfig())) {
                    throw new BusinessHintException("首次配置必须填写 kubeconfig 内容");
                }
            }
            case token -> {
                if (StringUtils.isBlank(config.getToken())) {
                    throw new BusinessHintException("首次配置必须填写 token");
                }
            }
            case password -> {
                if (StringUtils.isBlank(config.getUsername()) || StringUtils.isBlank(config.getPassword())) {
                    throw new BusinessHintException("首次配置必须填写用户名和密码");
                }
            }
        }
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
