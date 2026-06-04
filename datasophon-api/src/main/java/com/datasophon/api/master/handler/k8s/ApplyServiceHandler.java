package com.datasophon.api.master.handler.k8s;

import com.datasophon.api.dto.instance.K8sNamespaceIdentityDTO;
import com.datasophon.api.service.cluster.K8sClusterNamespaceService;
import com.datasophon.api.service.frame.FrameK8sServiceService;
import com.datasophon.api.service.instance.K8sServiceInstanceValuesService;
import com.datasophon.api.utils.HelmValueUtils;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.k8s.client.HelmClient;
import com.datasophon.common.k8s.client.HelmifyClient;
import com.datasophon.common.k8s.config.ClientOptions;
import com.datasophon.common.k8s.dto.UpgradeParams;
import com.datasophon.common.k8s.spec.helm.HelmUtils;
import com.datasophon.common.k8s.vo.helm.HelmHistoryVO;
import com.datasophon.common.k8s.vo.helm.HelmReleaseVO;
import com.datasophon.common.k8s.vo.helm.HelmStatusVO;
import com.datasophon.common.model.k8s.K8sArtifact;
import com.datasophon.common.model.k8s.K8sServiceNode;
import com.datasophon.common.storage.MetaStorage;
import com.datasophon.common.storage.StorageUtils;
import com.datasophon.common.storage.vo.ServiceMetaItem;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PathUtils;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.entity.cluster.K8sClusterNamespace;
import com.datasophon.dao.entity.frame.FrameK8sServiceEntity;
import com.datasophon.dao.entity.instance.K8sServiceInstance;
import com.datasophon.dao.entity.instance.K8sServiceInstanceValues;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import com.alibaba.fastjson2.JSONObject;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;

/**
 * K8s 服务应用处理器
 * 负责处理 K8s 服务的安装、升级、启动操作
 * 支持 Helm Chart 和原生 YAML 两种部署方式
 *
 * @author zhanghuangbin
 */
@Slf4j
public class ApplyServiceHandler extends ServiceHandler {
    
    private final K8sClusterNamespaceService namespaceService;
    
    private final FrameK8sServiceService frameK8sServiceService;
    
    private final K8sServiceInstanceValuesService k8sServiceInstanceValuesService;
    
    private final CommandType commandType;
    
    public ApplyServiceHandler(CommandType commandType) {
        this.commandType = commandType;
        namespaceService = SpringUtil.getBean(K8sClusterNamespaceService.class);
        frameK8sServiceService = SpringUtil.getBean(FrameK8sServiceService.class);
        k8sServiceInstanceValuesService = SpringUtil.getBean(K8sServiceInstanceValuesService.class);
    }
    
    @Override
    public ExecResult handlerRequest(K8sServiceNode serviceNode) throws Exception {
        logger.info("开始处理 K8s 服务节点，服务名：{}, 命令类型：{}", serviceNode.getServiceName(), commandType.getCommandName("en"));
        return doInstallServiceNode(BeanUtil.toBean(serviceNode, K8sServiceNode.class));
    }
    
    /**
     * 执行服务节点安装流程
     * 主要步骤：
     * 1. 填充节点字段（针对非安装/升级场景）
     * 2. 确保 Namespace 存在
     * 3. 下载或生成 Helm Chart
     * 4. 应用 Helm Chart 到 K8s 集群
     * 5. 更新服务实例状态
     *
     * @param serviceNode K8s 服务节点
     * @return 执行结果
     * @throws IOException IO 异常
     */
    private ExecResult doInstallServiceNode(K8sServiceNode serviceNode) throws IOException {
        String chartPath = null;
        try {
            // 步骤 1: 填充节点字段（针对非安装/升级场景，获取 metaFileType 和 valueId）
            fillNodeFields(serviceNode);
            
            // 步骤 2: 确保 Namespace 存在
            ensureNamespaceExists(serviceNode);
            updateCmdProgress(serviceNode, 10);
            
            // 步骤 3: 获取服务实例和服务定义
            K8sServiceInstance instance = instanceService.getById(serviceNode.getServiceInstanceId());
            FrameK8sServiceEntity serviceDef = frameK8sServiceService.getById(instance.getServiceId());
            K8sServiceInstanceValues values = k8sServiceInstanceValuesService.getById(serviceNode.getValueId());
            
            // 步骤 4: 下载或生成 Helm Chart
            chartPath = downloadOrGenerateChart(serviceNode, serviceDef, values);
            logger.info("Chart 已下载到路径：{}", chartPath);
            updateCmdProgress(serviceNode, 20);
            
            // 步骤 5: 应用 Helm Chart
            HelmReleaseVO result = applyHelmChart(serviceNode, chartPath, serviceDef, values);
            logger.info("Helm 发布结果，状态：{}", result.getInfo().getStatus());
            updateCmdProgress(serviceNode, 80);
            
            if (!result.getInfo().getStatus().equalsIgnoreCase("deployed")) {
                logger.error("安装{}失败，原因：{}", serviceNode.getServiceName(), result.getInfo().getNotes());
                return ExecResult.error(String.format("安装%s失败，原因：%s", serviceNode.getServiceName(), result.getInfo().getNotes()));
            }
            
            // 步骤 6: 更新服务实例状态
            updateServiceInstance(serviceNode, instance, result.getVersion());
            logger.info("服务{}安装成功，版本：{}", serviceNode.getServiceName(), result.getVersion());
            updateCmdProgress(serviceNode, 90);
            
            return ExecResult.success(String.format("安装%s 成功", serviceNode.getServiceName()));
        } finally {
            // 清理临时 Chart 文件
            FileUtil.del(chartPath);
        }
    }
    
    /**
     * 填充节点字段
     * 针对非安装/升级场景，从数据库获取 metaFileType 和 valueId
     *
     * @param serviceNode K8s 服务节点
     */
    private void fillNodeFields(K8sServiceNode serviceNode) {
        if (!Arrays.asList(CommandType.INSTALL_SERVICE, CommandType.UPGRADE_SERVICE).contains(commandType)) {
            K8sServiceInstance instance = instanceService.getById(serviceNode.getServiceInstanceId());
            serviceNode.setMetaFileType(instance.getLastMetaFileType());
            
            K8sServiceInstanceValues values = k8sServiceInstanceValuesService.getNewestValuesByInstanceId(serviceNode.getServiceInstanceId());
            serviceNode.setValueId(values.getId());
        }
    }
    
    /**
     * 检查 namespace 是否存在，如果不存在则创建
     * 并更新 K8sClusterNamespace 的 state 字段为 active
     *
     * @param serviceNode K8s 服务节点
     */
    private void ensureNamespaceExists(K8sServiceNode serviceNode) {
        Integer clusterId = serviceNode.getClusterId();
        String namespaceName = serviceNode.getNamespace();
        logger.info("检查并创建 Namespace，clusterId:{}, namespace:{}", clusterId, namespaceName);
        
        // 获取 K8s 集群配置
        K8sClusterConfig config = getK8sConfig(clusterId);
        
        // 调用 K8sService 确保 namespace 存在
        k8sService.createIfAbsent(config, namespaceName);
        
        // 更新 K8sClusterNamespace 状态为 active
        K8sClusterNamespace db = namespaceService.getNamespace(new K8sNamespaceIdentityDTO(clusterId, namespaceName));
        if (db.getState() != 1) {
            db.setState(1);
            namespaceService.updateById(db);
            logger.info("K8s namespace '{}' state 更新为 active, clusterId:{}", namespaceName, clusterId);
        }
    }
    
    /**
     * 下载或生成 Chart
     * 根据 metaFileType 决定下载 Helm Chart 或生成 YAML Chart
     *
     * @param serviceNode K8s 服务节点
     * @param serviceDef  服务定义
     * @param values      服务配置值
     * @return Chart 路径
     */
    private String downloadOrGenerateChart(K8sServiceNode serviceNode, FrameK8sServiceEntity serviceDef, K8sServiceInstanceValues values) {
        String metaFileType = serviceNode.getMetaFileType();
        logger.info("Chart 类型：{}, 服务：{}", metaFileType, serviceNode.getServiceName());
        
        if ("helm".equals(metaFileType)) {
            logger.info("下载 Helm Chart, 服务定义 ID:{}", serviceDef.getId());
            return downloadHelmChart(serviceDef);
        } else if ("yaml".equals(metaFileType)) {
            logger.info("生成 Helm Chart, 服务定义 ID:{}", serviceDef.getId());
            return generateChart(serviceDef, values);
        } else {
            throw new IllegalArgumentException("不支持的 metaFileType: " + serviceNode.getMetaFileType());
        }
    }
    
    /**
     * 下载 Helm Chart
     *
     * @param serviceDef 服务定义
     * @return Chart 文件路径
     */
    private String downloadHelmChart(FrameK8sServiceEntity serviceDef) {
        ServiceMetaItem item = frameK8sServiceService.getServiceMetaItem(serviceDef.getId());
        MetaStorage metaStorage = StorageUtils.getMetaStorage();
        K8sArtifact artifact = JSONObject.parseObject(serviceDef.getArtifact(), K8sArtifact.class);
        File tmp = PathUtils.createTmpFile("helm/" + RandomUtil.randomString(12), artifact.getHelm());
        try (OutputStream out = Files.newOutputStream(tmp.toPath())) {
            metaStorage.downResource(item, artifact.getHelm(), () -> out);
            logger.info("Helm Chart 下载成功，路径：{}", tmp.getAbsolutePath());
        } catch (IOException e) {
            throw new IllegalStateException(String.format("下载%s 失败", artifact.getHelm()), e);
        }
        return tmp.getAbsolutePath();
    }
    
    /**
     * 从 YAML 生成 Helm Chart
     *
     * @param serviceDef 服务定义
     * @param values     服务配置值
     * @return Chart 文件路径
     */
    private String generateChart(FrameK8sServiceEntity serviceDef, K8sServiceInstanceValues values) {
        K8sArtifact artifact = JSONObject.parseObject(serviceDef.getArtifact(), K8sArtifact.class);
        File tmp = PathUtils.createTmpFile("yaml/" + RandomUtil.randomString(12), artifact.getYaml());
        
        // 使用全量 values 或 delta values
        String mergeContent = StrUtil.isNotBlank(values.getDeltaValues()) ? values.getValues() : values.getDeltaValues();
        FileUtil.writeString(mergeContent, tmp, StandardCharsets.UTF_8);
        logger.info("YAML 内容已写入临时文件，路径：{}", tmp.getAbsolutePath());
        
        try (HelmifyClient client = new HelmifyClient()) {
            String chartPath = client.createChart(serviceDef.getServiceName(), serviceDef.getServiceVersion(), tmp.getAbsolutePath());
            logger.info("Helm Chart 生成成功，路径：{}", chartPath);
            return chartPath;
        }
    }
    
    /**
     * 应用 Helm Chart 到 K8s 集群
     *
     * @param serviceNode K8s 服务节点
     * @param chartPath   Chart 文件路径
     * @param serviceDef  服务定义
     * @param values      服务配置值
     * @return Helm 发布结果
     * @throws IOException IO 异常
     */
    private HelmReleaseVO applyHelmChart(K8sServiceNode serviceNode, String chartPath, FrameK8sServiceEntity serviceDef, K8sServiceInstanceValues values) throws IOException {
        ClientOptions options = BeanUtil.toBean(config, ClientOptions.class);
        options.setServerName(config.getServerHost());
        logger.info("应用 Helm Chart, Release 名称：{}, Namespace: {}", serviceDef.getServiceName(), serviceNode.getNamespace());
        
        try (HelmClient client = new HelmClient(options)) {
            deletePendingPreRelease(client, serviceNode);
            return doApplyHelmChart(client, serviceNode, chartPath, values);
        }
    }
    
    private void deletePendingPreRelease(HelmClient client, K8sServiceNode serviceNode) {
        String namespace = serviceNode.getNamespace();
        String serviceName = serviceNode.getServiceName();
        String releaseName = HelmUtils.createReleaseName(serviceName);
        
        try {
            logger.info("开始清理 pending 状态的 release 历史，releaseName={}, namespace={}", releaseName, namespace);
            // 获取该 release 的历史记录（按版本号降序排列）
            List<HelmHistoryVO> history = client.history(releaseName, namespace);
            if (history == null || history.isEmpty()) {
                logger.info("release {} 没有找到历史记录", releaseName);
                return;
            }
            history = new ArrayList<>(history);
            history.sort(Comparator.comparing(HelmHistoryVO::getRevision).reversed());
            
            // 从高版本开始删除，直到遇到第一个非 pending 状态
            List<HelmHistoryVO> revisions = new ArrayList<>();
            for (HelmHistoryVO entry : history) {
                String status = entry.getStatus();
                boolean isPending = "pending-upgrade".equalsIgnoreCase(status) || "pending-install".equalsIgnoreCase(status);
                
                if (!isPending) {
                    // 遇到第一个非 pending 状态，停止删除
                    logger.info("release: {}, 遇到第一个非 pending 状态：{}, 版本：{}", releaseName, status, entry.getRevision());
                    break;
                }
                revisions.add(entry);
            }
            logger.info("待删除的releaseName {}, 共{}个版本", releaseName, revisions.size());
            if (!revisions.isEmpty()) {
                k8sService.batchExec(config, k8sClient -> {
                    for (HelmHistoryVO rev : revisions) {
                        List<String> resources = new ArrayList<>();
                        HelmStatusVO status = client.status(releaseName, namespace, rev.getRevision());
                        Optional.ofNullable(status.getInfo().getResources())
                                .orElse(new HashMap<>())
                                .forEach((k, rs) -> {
                                    if (CollectionUtil.isNotEmpty(rs)) {
                                        rs.forEach(r -> {
                                            if (!"Pod".equals(r.getKind())) {
                                                resources.add(String.format("%s: %s", r.getKind(), r.getMetadata().getName()));
                                            }
                                        });
                                    }
                                });
                                
                        String key = String.format("sh.helm.release.v1.%s.v%s", releaseName, rev.getRevision());
                        
                        logger.info("删除Release: {}, revision: {}, revision的状态为：{}", releaseName, rev.getRevision(), rev.getStatus());
                        k8sClient.deleteSecrets(namespace, Collections.singletonList(key));
                        if (!resources.isEmpty()) {
                            logger.warn("Release: {}, revision: {}, 请注意人工判断这些下列资源是否需要人工修改:\n {}\n", releaseName, rev.getRevision(), StrUtil.join("\n\t", resources));
                        }
                    }
                    return null;
                }, "删除Helm Release资源");
                logger.info("清理 pending release 历史完成");
            }
        } catch (Exception e) {
            logger.warn("清理 pending release 历史失败：{}", e.getMessage(), e);
            // 不抛出异常，避免影响主流程
        }
    }
    
    private HelmReleaseVO doApplyHelmChart(HelmClient client, K8sServiceNode serviceNode, String chartPath, K8sServiceInstanceValues values) {
        File tempValueFile = null;
        
        try {
            UpgradeParams params = new UpgradeParams();
            
            if (serviceNode.getMetaFileType().equals("helm") && StrUtil.isNotBlank(values.getDeltaValues())) {
                tempValueFile = HelmValueUtils.writeHelmValueTempFile(values.getDeltaValues());
                logger.info("Delta Values 已写入临时文件：{}", tempValueFile.getAbsolutePath());
            }
            
            params.setReleaseName(HelmUtils.createReleaseName(serviceNode.getServiceName()));
            params.setChartPath(chartPath);
            params.setNamespace(serviceNode.getNamespace());
            if (tempValueFile != null) {
                params.setValuesFiles(Collections.singletonList(tempValueFile.getAbsolutePath()));
            }
            Map<String, String> extraValues = HelmValueUtils.getExtraValues();
            List<String> extraValueList = new ArrayList<>();
            extraValues.forEach((k, v) -> extraValueList.add(k + "=" + v));
            params.setSetValues(extraValueList);
            
            logger.info("使用Helm, 开始安装 {}", serviceNode.getServiceName());
            HelmReleaseVO result = client.upgrade(params);
            logger.info("Helm upgrade 完成，状态：{}", result.getInfo().getStatus());
            return result;
        } finally {
            FileUtil.del(tempValueFile);
        }
    }
    
    /**
     * 更新服务实例状态
     * 设置 metaFileType 和 state 为 active
     *
     * @param serviceNode K8s 服务节点
     * @param instance    服务实例
     * @param version     Helm Chart 版本
     */
    private void updateServiceInstance(K8sServiceNode serviceNode, K8sServiceInstance instance, Integer version) {
        instance.setLastMetaFileType(serviceNode.getMetaFileType());
        instance.setState(1);
        instanceService.updateById(instance);
        logger.info("服务实例状态已更新，ID:{}, state:1, version:{}", instance.getId(), version);
    }
}
