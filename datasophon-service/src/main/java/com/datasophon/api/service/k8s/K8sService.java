package com.datasophon.api.service.k8s;

import com.datasophon.api.dto.instance.K8sServiceInstanceQueryDTO;
import com.datasophon.api.vo.k8s.K8sClusterStatus;
import com.datasophon.api.vo.k8s.K8sConfigMapInfo;
import com.datasophon.api.vo.k8s.K8sConnectionResult;
import com.datasophon.api.vo.k8s.K8sDeploymentInfo;
import com.datasophon.api.vo.k8s.K8sIngressInfo;
import com.datasophon.api.vo.k8s.K8sNamespace;
import com.datasophon.api.vo.k8s.K8sPodInfo;
import com.datasophon.api.vo.k8s.K8sServiceInfo;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;

import java.util.List;

/**
 * @author zhanghuangbin
 */
public interface K8sService {

    String READY = "Ready";

    String VOS_ORG = "ltszyl.com/vos-";

    String MANGED_BY_LABEL = "app.kubernetes.io/managed-by";

    String MANGED_BY_LABEL_VALUE = "Helm";

    String SRV_INST_ID_LABEL = "app.kubernetes.io/instance";


    String POD_TYPE = "pod";
    String DEPLOYMENT_TYPE = "deployment";
    String SERVICE_TYPE = "service";
    String INGRESS_TYPE = "ingress";
    String CONFIGMAP_TYPE = "configmap";


    K8sClusterStatus getState(K8sClusterConfig config);

    K8sConnectionResult testConnection(K8sClusterConfig config);


    /**
     * 获取 K8s 集群的命名空间及其状态
     *
     * @param config K8s 集群配置
     * @return 命名空间名称到状态的映射 (active/inactive)
     */
    List<K8sNamespace> listNamespaces(K8sClusterConfig config);


    /**
     * 获取指定命名空间可管理的 K8s 资源类型
     *
     * @param config    K8s 集群配置
     * @param namespace 命名空间
     * @return 命名空间可管理的资源类型列表
     */
    List<String> getResourceTypes(K8sClusterConfig config, K8sServiceInstanceQueryDTO namespace);

    /**
     * 获取 Deployment 资源列表
     *
     * @param config    K8s 集群配置
     * @param query     查询条件
     * @return Deployment 资源列表
     */
    List<K8sDeploymentInfo> listDeployments(K8sClusterConfig config, K8sServiceInstanceQueryDTO query);

    /**
     * 获取 Pod 资源列表
     *
     * @param config    K8s 集群配置
     * @param query     查询条件
     * @return Pod 资源列表
     */
    List<K8sPodInfo> listPods(K8sClusterConfig config, K8sServiceInstanceQueryDTO query);


    /**
     * 获取 Service 资源列表
     *
     * @param config    K8s 集群配置
     * @param query     查询条件
     * @return Service 资源列表
     */
    List<K8sServiceInfo> listServices(K8sClusterConfig config, K8sServiceInstanceQueryDTO query);

    /**
     * 获取 Ingress 资源列表
     *
     * @param config    K8s 集群配置
     * @param query     查询条件
     * @return Ingress 资源列表
     */
    List<K8sIngressInfo> listIngresses(K8sClusterConfig config, K8sServiceInstanceQueryDTO query);

    /**
     * 获取 ConfigMap 资源列表
     *
     * @param config    K8s 集群配置
     * @param query     查询条件
     * @return ConfigMap 资源列表
     */
    List<K8sConfigMapInfo> listConfigMaps(K8sClusterConfig config, K8sServiceInstanceQueryDTO query);

    /**
     * 确保 K8s namespace 存在，如果不存在则创建
     *
     * @param config        K8s 集群配置
     * @param namespaceName namespace 名称
     * @return 创建或获取的 namespace 信息
     */
    K8sNamespace createIfAbsent(K8sClusterConfig config, String namespaceName);

    /**
     * 重启 Deployment
     *
     * @param config    K8s 集群配置
     * @param deployments     查询条件
     * @return 重启结果信息
     */
    void restartDeployment(K8sClusterConfig config, List<K8sDeploymentInfo> deployments);

    /**
     * 缩放 Deployment 副本数
     *
     * @param config    K8s 集群配置
     * @param deployments     Deployment 列表
     * @param replicas  目标副本数
     */
    void scaleDeployments(K8sClusterConfig config, List<K8sDeploymentInfo> deployments, int replicas);


}
