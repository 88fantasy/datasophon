package com.datasophon.api.service.instance.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datasophon.api.dto.instance.K8sNamespaceIdentityDTO;
import com.datasophon.api.dto.instance.K8sServiceInstanceQueryDTO;
import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.api.service.cluster.K8sClusterNamespaceService;
import com.datasophon.api.service.instance.K8sServiceInstanceService;
import com.datasophon.api.service.k8s.K8sService;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.entity.cluster.K8sClusterNamespace;
import com.datasophon.dao.entity.instance.K8sServiceInstance;
import com.datasophon.dao.mapper.instance.K8sServiceInstanceMapper;
import com.datasophon.dao.vo.instance.K8sServiceInstanceVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

import static com.datasophon.api.service.k8s.K8sService.CONFIGMAP_TYPE;
import static com.datasophon.api.service.k8s.K8sService.DEPLOYMENT_TYPE;
import static com.datasophon.api.service.k8s.K8sService.INGRESS_TYPE;
import static com.datasophon.api.service.k8s.K8sService.POD_TYPE;
import static com.datasophon.api.service.k8s.K8sService.SERVICE_TYPE;

/**
 * @author zhanghuangbin
 */
@Service("k8sServiceInstanceService")
public class K8sServiceInstanceServiceImpl extends ServiceImpl<K8sServiceInstanceMapper, K8sServiceInstance> implements K8sServiceInstanceService {

    @Autowired
    private K8sClusterNamespaceService k8sClusterNamespaceService;

    @Autowired
    private K8sService k8sService;

    @Autowired
    private K8sClusterConfigService k8sClusterConfigService;


    @Override
    public List<K8sServiceInstanceVO> queryInstanceList(K8sNamespaceIdentityDTO query) {
        if (k8sClusterNamespaceService.getNamespace(query) == null) {
            throw new BusinessHintException(String.format("名空间%s不存在", query.getNamespace()));
        }
        // 2. 使用@K8sServiceInstanceMapper 查询 K8sServiceInstanceVO 对象
        return baseMapper.selectInstanceList(query.getClusterId(), query.getNamespace());
    }

    @Override
    public List<String> listResourceType(K8sServiceInstanceQueryDTO query) {
        K8sServiceInstance instance = getById(query.getInstanceId());
        Objects.requireNonNull(instance);

        K8sClusterNamespace ns = k8sClusterNamespaceService.getById(instance.getNamespaceId());
        query.setNamespace(ns.getNamespace());
        K8sClusterConfig config = k8sClusterConfigService.getInitConfig(ns.getClusterId());

        List<String> types = k8sService.getResourceTypes(config, query);
        return types;
    }

    @Override
    public Object listResource(K8sServiceInstanceQueryDTO query) {
        K8sServiceInstance instance = getById(query.getInstanceId());
        Objects.requireNonNull(instance);

        K8sClusterNamespace ns = k8sClusterNamespaceService.getById(instance.getNamespaceId());
        query.setNamespace(ns.getNamespace());
        K8sClusterConfig config = k8sClusterConfigService.getInitConfig(ns.getClusterId());

        // 根据 resourceType 调用 K8sService 中的对应方法
        switch (query.getResourceType()) {
            case DEPLOYMENT_TYPE:
                return k8sService.listDeployments(config, query);
            case POD_TYPE:
                return k8sService.listPods(config, query);
            case SERVICE_TYPE:
                return k8sService.listServices(config, query);
            case INGRESS_TYPE:
                return k8sService.listIngresses(config, query);
            case CONFIGMAP_TYPE:
                return k8sService.listConfigMaps(config, query);
            default:
                throw new BusinessHintException("不支持的资源类型：" + query.getResourceType());
        }
    }

    @Override
    public K8sServiceInstance createIfAbsent(Integer clusterId, Integer namespaceId, Integer serviceId) {
        // 2. 根据 serviceId 查询服务实例，如果不存在则创建
        K8sServiceInstance instance =lambdaQuery()
                .eq(K8sServiceInstance::getClusterId, clusterId)
                .eq(K8sServiceInstance::getNamespaceId, namespaceId)
                .eq(K8sServiceInstance::getServiceId, serviceId)
                .one();

        if (instance == null) {
            instance = new K8sServiceInstance();
            instance.setClusterId(clusterId);
            instance.setNamespaceId(namespaceId);
            instance.setServiceId(serviceId);
            instance.setState(0); // 初始化状态
            save(instance);
        }
        return instance;
    }


}
