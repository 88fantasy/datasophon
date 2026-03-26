package com.datasophon.api.service.cluster.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datasophon.api.dto.instance.K8sNamespaceIdentityDTO;
import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.api.service.cluster.K8sClusterNamespaceService;
import com.datasophon.api.service.instance.K8sServiceInstanceService;
import com.datasophon.api.service.instance.K8sServiceInstanceValuesService;
import com.datasophon.api.service.k8s.K8sService;
import com.datasophon.api.vo.k8s.K8sNamespace;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.entity.cluster.K8sClusterNamespace;
import com.datasophon.dao.entity.instance.K8sServiceInstance;
import com.datasophon.dao.entity.instance.K8sServiceInstanceValues;
import com.datasophon.dao.mapper.cluster.K8sClusterNamespaceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author zhanghuangbin
 */
@Service("k8sClusterNamespaceService")
@Slf4j
public class K8sClusterNamespaceServiceImpl extends ServiceImpl<K8sClusterNamespaceMapper, K8sClusterNamespace> implements K8sClusterNamespaceService {

    @Autowired
    private K8sClusterConfigService k8sClusterConfigService;

    @Autowired
    private K8sServiceInstanceService k8sServiceInstanceService;

    @Autowired
    private K8sServiceInstanceValuesService k8sServiceInstanceValuesService;

    @Autowired
    private K8sService k8sService;

    @Override
    public List<K8sClusterNamespace> listAndUpdateNamespaceByClusterId(Integer clusterId) {
        // 1. 获取 K8s 集群配置
        K8sClusterConfig config = k8sClusterConfigService.getByClusterId(clusterId);
        if (config == null) {
           throw new BusinessHintException("集群未初始化");
        }

        // 2. 从 K8s 集群获取命名空间列表及状态 (active/inactive)
        List<K8sNamespace>  namespaces = k8sService.listNamespaces(config);

        // 3. 获取数据库中已保存的命名空间
        List<K8sClusterNamespace> dbNamespaces = lambdaQuery()
                .eq(K8sClusterNamespace::getClusterId, clusterId)
                .list();

        // 4. 对比并更新数据库
        List<K8sClusterNamespace> toInsert = new ArrayList<>();
        List<K8sClusterNamespace> toUpdate = new ArrayList<>();
        List<Integer> toDelete = new ArrayList<>();

        // 4.1 构建数据库中已存在的命名空间映射
        Map<String, K8sClusterNamespace> dbNamespaceMap = dbNamespaces.stream()
                .collect(Collectors.toMap(K8sClusterNamespace::getNamespace, ns -> ns));

        // 4.2 需要新增或更新的命名空间（K8s 中存在的）
        for (K8sNamespace entry : namespaces) {
            String nsName = entry.getName();
            String status = entry.getStatus();
            int state = "active".equals(status) ? 1 : 0;

            K8sClusterNamespace dbNs = dbNamespaceMap.get(nsName);
            if (dbNs == null) {
                // 新增
                K8sClusterNamespace namespace = new K8sClusterNamespace();
                namespace.setClusterId(clusterId);
                namespace.setNamespace(nsName);
                namespace.setState(state);
                toInsert.add(namespace);
            } else if (dbNs.getState() != state) {
                // 状态发生变化，需要更新
                dbNs.setState(state);
                toUpdate.add(dbNs);
            }
        }


        Set<String> k8sNamespaceSet = namespaces.stream().map(K8sNamespace::getName).collect(Collectors.toSet());
        // 4.3 需要设置为未知状态的命名空间（数据库中有，K8s 中没有）
        for (K8sClusterNamespace ns : dbNamespaces) {
            if (!k8sNamespaceSet.contains(ns.getNamespace())) {
                if (!isRef(ns.getId())) {
                    toDelete.add(ns.getId());
                } else {
                    ns.setState(-1);
                    toUpdate.add(ns);
                }
            }
        }

        // 5. 执行批量操作
        if (CollUtil.isNotEmpty(toInsert)) {
            saveBatch(toInsert);
        }

        if (CollUtil.isNotEmpty(toUpdate)) {
            updateBatchById(toUpdate);
        }
        if (CollUtil.isNotEmpty(toDelete)) {
            removeByIds(toDelete);
        }

        // 6. 返回更新后的列表
        return lambdaQuery()
                .eq(K8sClusterNamespace::getClusterId, clusterId)
                .list();
    }

    @Override
    public K8sClusterNamespace getNamespace(K8sNamespaceIdentityDTO query) {
        return lambdaQuery()
                .eq(K8sClusterNamespace::getClusterId, query.getClusterId())
                .eq(K8sClusterNamespace::getNamespace, query.getNamespace())
                .one();
    }


    private boolean isRef(Integer nsId) {
        // 检查 K8sServiceInstance 是否引用了该命名空间
        Long instanceCount = k8sServiceInstanceService.lambdaQuery()
                .eq(K8sServiceInstance::getNamespaceId, nsId)
                .count();

        if (instanceCount > 0) {
            return true;
        }

        // 检查 K8sServiceInstanceValues 是否引用了该命名空间
        Long valuesCount = k8sServiceInstanceValuesService.lambdaQuery()
                .eq(K8sServiceInstanceValues::getNamespaceId, nsId)
                .count();

        return valuesCount > 0;
    }
}
