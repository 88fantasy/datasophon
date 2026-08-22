package com.datasophon.api.service.k8s;

import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.common.k8s.config.ClientOptions;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.enums.ManageMode;

import org.springframework.stereotype.Component;

/** 统一把 K8s 持久化配置转换为客户端选项，并在这里确定接管集群的只读边界。 */
@Component
public class K8sClientOptionsFactory {

    private final ClusterInfoService clusterInfoService;

    public K8sClientOptionsFactory(ClusterInfoService clusterInfoService) {
        this.clusterInfoService = clusterInfoService;
    }

    /**
     * default-deny：只有确认集群存在且为自建（MANAGED）才放行写操作。
     *
     * <p>集群 ID 为空、或集群记录查不到时一律按只读处理——判不出这套凭据指向谁的集群时，
     * 恰恰是最不该放行写操作的时刻。接管功能的红线是绝不向目标集群写入，宁可让自建集群的
     * 写操作在数据异常时报错，也不能让接管集群在查询失败时被误判为可写。
     */
    public ClientOptions from(K8sClusterConfig config) {
        boolean readOnly = true;
        Integer clusterId = config.getClusterId();
        if (clusterId != null) {
            ClusterInfoEntity cluster = clusterInfoService.getById(clusterId);
            readOnly = cluster == null || !ManageMode.MANAGED.equals(cluster.getManageMode());
        }
        return ClientOptions.from(config, readOnly);
    }
}
