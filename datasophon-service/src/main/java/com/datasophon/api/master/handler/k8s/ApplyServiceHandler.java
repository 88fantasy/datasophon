package com.datasophon.api.master.handler.k8s;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.fastjson.JSONObject;
import com.datasophon.api.dto.instance.K8sNamespaceIdentityDTO;
import com.datasophon.api.service.cluster.K8sClusterNamespaceService;
import com.datasophon.api.service.frame.FrameK8sServiceService;
import com.datasophon.api.service.instance.K8sServiceInstanceService;
import com.datasophon.api.service.instance.K8sServiceInstanceValuesService;
import com.datasophon.api.service.k8s.K8sService;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.k8s.client.HelmClient;
import com.datasophon.common.k8s.client.HelmifyClient;
import com.datasophon.common.k8s.config.ClientOptions;
import com.datasophon.common.k8s.dto.UpgradeParams;
import com.datasophon.common.k8s.vo.helm.HelmReleaseVO;
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
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

/**
 * @author zhanghuangbin
 */
@Slf4j
public class ApplyServiceHandler extends ServiceHandler {

    private final K8sService k8sService;

    private final K8sClusterNamespaceService namespaceService;

    private final K8sServiceInstanceService instanceService;
    private final FrameK8sServiceService frameK8sServiceService;
    private final K8sServiceInstanceValuesService k8sServiceInstanceValuesService;

    private final CommandType commandType;

    public ApplyServiceHandler(CommandType commandType) {
        this.commandType = commandType;
        k8sService = SpringUtil.getBean(K8sService.class);
        namespaceService = SpringUtil.getBean(K8sClusterNamespaceService.class);
        instanceService = SpringUtil.getBean(K8sServiceInstanceService.class);
        frameK8sServiceService = SpringUtil.getBean(FrameK8sServiceService.class);
        k8sServiceInstanceValuesService = SpringUtil.getBean(K8sServiceInstanceValuesService.class);
    }


    @Override
    public ExecResult handlerRequest(K8sServiceNode serviceNode) throws Exception {
        return doInstallServiceNode(BeanUtil.toBean(serviceNode, K8sServiceNode.class));
    }

    private ExecResult doInstallServiceNode(K8sServiceNode serviceNode) throws IOException {
        // 步骤 1: 检查并创建 namespace
        String chartPath = null;
        try {
//            填充缺省的值
            fillNodeFields(serviceNode);

            ensureNamespaceExists(serviceNode);
            K8sServiceInstance instance = instanceService.getById(serviceNode.getServiceInstanceId());
            FrameK8sServiceEntity serviceDef = frameK8sServiceService.getById(instance.getServiceId());
            K8sServiceInstanceValues values = k8sServiceInstanceValuesService.getById(serviceNode.getValueId());

            chartPath = downloadOrGenerateChart(serviceNode, serviceDef, values);

            HelmReleaseVO result = applyHelmChart(serviceNode, chartPath, serviceDef, instance, values);
            if (!result.getInfo().getStatus().equalsIgnoreCase("deployed")) {
                return ExecResult.error(String.format("安装%s失败，原因：%s", serviceNode.getServiceName(), result.getInfo().getNotes()));
            }
            // 步骤 4: 更新服务实例状态
            updateServiceInstance(serviceNode, instance, result.getVersion());

            return ExecResult.success(String.format("安装%s成功", serviceNode.getServiceName()));
        } finally {
            FileUtil.del(chartPath);
        }
    }

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
     * 并更新 K8sClusterNamespace 的 state 字段
     */
    private void ensureNamespaceExists(K8sServiceNode serviceNode) {
        Integer clusterId = serviceNode.getClusterId();
        String namespaceName = serviceNode.getNamespace();

        // 1. 获取 K8sClusterConfig
        K8sClusterConfig config = getK8sConfig(clusterId);

        // 3. 调用 K8sService 确保 namespace 存在
        k8sService.createIfAbsent(config, namespaceName);

        // 4. 更新 K8sClusterNamespace 状态为 active
        K8sClusterNamespace db = namespaceService.getNamespace(new K8sNamespaceIdentityDTO(clusterId, namespaceName));
        if (db.getState() != 1) {
            db.setState(1);
            namespaceService.updateById(db);
            log.info("K8s namespace '{}' state updated to active in cluster {}", namespaceName, clusterId);
        }
    }


    /**
     * @param serviceNode 服务节点信息
     * @param serviceDef
     * @param values
     * @return yaml 内容
     */
    private String downloadOrGenerateChart(K8sServiceNode serviceNode, FrameK8sServiceEntity serviceDef, K8sServiceInstanceValues values) {
        String metaFileType = serviceNode.getMetaFileType();
        if ("helm".equals(metaFileType)) {
            return downloadHelmChart(serviceDef);
        } else if ("yaml".equals(metaFileType)) {
            return generateChart(serviceDef, values);
        } else {
            throw new IllegalArgumentException("不支持的 metaFileType: " + serviceNode.getMetaFileType());
        }
    }


    private String downloadHelmChart(FrameK8sServiceEntity serviceDef) {
        ServiceMetaItem item = frameK8sServiceService.getServiceMetaItem(serviceDef.getId());
        MetaStorage metaStorage = StorageUtils.getMetaStorage();
        K8sArtifact artifact = JSONObject.parseObject(serviceDef.getArtifact(), K8sArtifact.class);
        File tmp = PathUtils.createTmpFile("helm/" + RandomUtil.randomString(12), artifact.getHelm());
        try (OutputStream out = Files.newOutputStream(tmp.toPath())) {
            metaStorage.downResource(item, artifact.getHelm(), () -> out);
        } catch (IOException e) {
            throw new IllegalStateException(String.format("下载%s失败", artifact.getHelm()), e);
        }
        return tmp.getAbsolutePath();
    }

    private String generateChart(FrameK8sServiceEntity serviceDef, K8sServiceInstanceValues values) {
        K8sArtifact artifact = JSONObject.parseObject(serviceDef.getArtifact(), K8sArtifact.class);
        File tmp = PathUtils.createTmpFile("yaml/" + RandomUtil.randomString(12), artifact.getYaml());

//        yaml使用全量？
        String mergeContent = StrUtil.isNotBlank(values.getDeltaValues()) ? values.getValues() : values.getDeltaValues();
        FileUtil.writeString(mergeContent, tmp, StandardCharsets.UTF_8);

        try (HelmifyClient client = new HelmifyClient()) {
            return client.createChart(serviceDef.getServiceName(), serviceDef.getServiceVersion(), tmp.getAbsolutePath());
        }
    }

    private HelmReleaseVO applyHelmChart(K8sServiceNode serviceNode, String chartPath, FrameK8sServiceEntity serviceDef, K8sServiceInstance instance, K8sServiceInstanceValues values) throws IOException {
        ClientOptions options = BeanUtil.toBean(config, ClientOptions.class);
        options.setServerName(config.getServerHost());

        File tempValueFile = null;
        try (HelmClient client = new HelmClient(options)) {
            UpgradeParams params = new UpgradeParams();

            if (serviceNode.getMetaFileType().equals("helm") && StrUtil.isNotBlank(values.getDeltaValues())) {
                tempValueFile = PathUtils.createTmpFile("sensitive/" + RandomUtil.randomString(12), serviceDef.getRuntime());
                FileUtil.writeString(values.getDeltaValues(), tempValueFile, StandardCharsets.UTF_8);
            }

            params.setReleaseName(serviceDef.getServiceName() + "_" + instance.getServiceId());
            params.setChartPath(chartPath);
            params.setNamespace(serviceNode.getNamespace());
            if (tempValueFile != null) {
                params.setValuesFiles(Collections.singletonList(tempValueFile.getAbsolutePath()));
            }

            return client.upgrade(params);
        } finally {
            FileUtil.del(tempValueFile);
        }
    }

    private void updateServiceInstance(K8sServiceNode serviceNode, K8sServiceInstance instance, Integer version) {
        instance.setLastMetaFileType(serviceNode.getMetaFileType());
        instance.setState(1);
        instanceService.updateById(instance);
    }
}
