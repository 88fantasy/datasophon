package com.datasophon.api.service.instance.impl;

import static com.datasophon.api.service.k8s.K8sService.CONFIGMAP_TYPE;
import static com.datasophon.api.service.k8s.K8sService.DEPLOYMENT_TYPE;
import static com.datasophon.api.service.k8s.K8sService.INGRESS_TYPE;
import static com.datasophon.api.service.k8s.K8sService.POD_TYPE;
import static com.datasophon.api.service.k8s.K8sService.SERVICE_TYPE;

import com.datasophon.api.dto.instance.K8sNamespaceIdentityDTO;
import com.datasophon.api.dto.instance.K8sServiceInstanceQueryDTO;
import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.api.service.cluster.K8sClusterNamespaceService;
import com.datasophon.api.service.instance.K8sServiceInstanceService;
import com.datasophon.api.service.instance.K8sServiceInstanceValuesService;
import com.datasophon.api.service.k8s.K8sService;
import com.datasophon.api.vo.k8s.K8sPodInfo;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.entity.cluster.K8sClusterNamespace;
import com.datasophon.dao.entity.instance.K8sServiceInstance;
import com.datasophon.dao.mapper.instance.K8sServiceInstanceMapper;
import com.datasophon.dao.vo.instance.K8sServiceInstanceVO;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

/**
 * @author zhanghuangbin
 */
@Service("k8sServiceInstanceService")
@Transactional(rollbackFor = BusinessHintException.class)
public class K8sServiceInstanceServiceImpl extends ServiceImpl<K8sServiceInstanceMapper, K8sServiceInstance> implements K8sServiceInstanceService {
    
    @Autowired
    private K8sClusterNamespaceService k8sClusterNamespaceService;
    
    @Autowired
    private K8sService k8sService;
    
    @Autowired
    private K8sClusterConfigService k8sClusterConfigService;
    
    @Autowired
    private K8sServiceInstanceValuesService k8sServiceInstanceValuesService;
    
    @Override
    public List<K8sServiceInstanceVO> queryInstanceList(K8sNamespaceIdentityDTO query) {
        if (k8sClusterNamespaceService.getNamespace(query) == null) {
            throw new BusinessHintException(String.format("名空间%s不存在", query.getNamespace()));
        }
        // 2. 使用@K8sServiceInstanceMapper 查询 K8sServiceInstanceVO 对象
        return baseMapper.selectInstanceList(query.getClusterId(), query.getNamespace());
    }
    
    @Override
    public List<K8sServiceInstanceVO> listByIds(List<Integer> instanceIds) {
        if (instanceIds == null || instanceIds.isEmpty()) {
            return Collections.emptyList();
        }
        // 2. 使用@K8sServiceInstanceMapper#selectByIds 根据 IDs 查询 K8sServiceInstanceVO 对象
        return baseMapper.selectByIds(instanceIds);
    }
    
    @Override
    public List<String> listResourceType(K8sServiceInstanceQueryDTO query) {
        K8sServiceInstance instance = getById(query.getInstanceId());
        Objects.requireNonNull(instance);
        
        K8sClusterNamespace ns = k8sClusterNamespaceService.getById(instance.getNamespaceId());
        K8sClusterConfig config = k8sClusterConfigService.getInitConfig(ns.getClusterId());
        
        List<String> types = k8sService.getResourceTypes(config, query);
        return types;
    }
    
    @Override
    public Object listResource(K8sServiceInstanceQueryDTO query) {
        K8sServiceInstance instance = getById(query.getInstanceId());
        Objects.requireNonNull(instance);
        
        K8sClusterNamespace ns = k8sClusterNamespaceService.getById(instance.getNamespaceId());
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
        K8sServiceInstance instance = lambdaQuery()
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
    
    @Override
    public boolean removeInstanceId(Integer instanceId) {
        // 1. 获取服务实例信息
        K8sServiceInstanceVO instance =
                getVoById(instanceId).orElseThrow(() -> new BusinessHintException("实例不存在"));
        
        // 2. 获取 K8s 配置
        K8sClusterNamespace ns = k8sClusterNamespaceService.getById(instance.getNamespaceId());
        K8sClusterConfig config = k8sClusterConfigService.getInitConfig(ns.getClusterId());
        
        if (hasRunningPod(config, instance.getId())) {
            throw new BusinessHintException(String.format("服务%s存在正在运行的Pod，请先停止服务后再删除实例", instance.getServiceName()));
        }
        
        k8sService.uninstallRelease(config, instanceId);
        
        k8sServiceInstanceValuesService.removeByInstanceId(instanceId);
        // 5. 检查通过，删除服务实例
        return removeById(instanceId);
    }
    
    private boolean hasRunningPod(K8sClusterConfig config, Integer instanceId) {
        // 3. 构建查询参数，用于检查 Pod 资源
        K8sServiceInstanceQueryDTO query = new K8sServiceInstanceQueryDTO();
        query.setInstanceId(instanceId);
        
        // 4. 检查是否存在正在运行的 Pod
        List<K8sPodInfo> pods = k8sService.listPods(config, query);
        if (pods != null && !pods.isEmpty()) {
            // 检查是否有运行中的 Pod（状态为 Running 或 Pending）
            for (K8sPodInfo pod : pods) {
                if (Arrays.asList(K8sService.PENDING, K8sService.RUNNING, K8sService.READY).contains(pod.getStatus())) {
                    return true;
                }
            }
        }
        return false;
    }
    
    @Override
    public void removeByClusterId(Integer clusterId) {
        lambdaUpdate().eq(K8sServiceInstance::getClusterId, clusterId).remove();
    }
    
    @Override
    public boolean hasRunningInstance(Integer clusterId) {
        K8sClusterConfig config = k8sClusterConfigService.getInitConfig(clusterId);
        List<K8sServiceInstance> instances = lambdaQuery().eq(K8sServiceInstance::getClusterId, clusterId).list();
        for (K8sServiceInstance instance : instances) {
            if (hasRunningPod(config, instance.getId())) {
                return true;
            }
        }
        return false;
    }
    
}
