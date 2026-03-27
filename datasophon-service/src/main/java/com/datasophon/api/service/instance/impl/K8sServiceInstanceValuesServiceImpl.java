package com.datasophon.api.service.instance.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.io.FileUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.datasophon.api.dto.instance.K8sNamespaceIdentityDTO;
import com.datasophon.api.dto.instance.K8sServiceInstanceValuesSaveDTO;
import com.datasophon.api.dto.instance.K8sServiceInstanceValuesUpdateDTO;
import com.datasophon.api.exceptions.BusinessException;
import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.service.cluster.K8sClusterNamespaceService;
import com.datasophon.api.service.frame.FrameK8sServiceService;
import com.datasophon.api.service.instance.K8sServiceInstanceService;
import com.datasophon.api.service.instance.K8sServiceInstanceValuesService;
import com.datasophon.common.model.k8s.K8sArtifact;
import com.datasophon.common.storage.MetaStorage;
import com.datasophon.common.storage.StorageUtils;
import com.datasophon.common.storage.vo.ServiceMetaItem;
import com.datasophon.common.utils.PathUtils;
import com.datasophon.common.utils.TarUtils;
import com.datasophon.common.utils.YamlUtils;
import com.datasophon.dao.entity.cluster.K8sClusterNamespace;
import com.datasophon.dao.entity.frame.FrameK8sServiceEntity;
import com.datasophon.dao.entity.instance.K8sServiceInstance;
import com.datasophon.dao.entity.instance.K8sServiceInstanceValues;
import com.datasophon.dao.mapper.instance.K8sServiceInstanceValuesMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * @author zhanghuangbin
 */
@Service("k8sServiceInstanceValuesService")
public class K8sServiceInstanceValuesServiceImpl extends ServiceImpl<K8sServiceInstanceValuesMapper, K8sServiceInstanceValues> implements K8sServiceInstanceValuesService {

    @Autowired
    private FrameK8sServiceService frameK8sServiceService;

    @Autowired
    private K8sServiceInstanceService k8sServiceInstanceService;

    @Autowired
    private K8sClusterNamespaceService k8sClusterNamespaceService;

    @Override
    public List<K8sServiceInstanceValues> listSimpleByInstanceId(Integer instanceId) {
        return lambdaQuery()
                .eq(K8sServiceInstanceValues::getInstanceId, instanceId)
                .select(K8sServiceInstanceValues::getId,
                        K8sServiceInstanceValues::getClusterId,
                        K8sServiceInstanceValues::getNamespaceId,
                        K8sServiceInstanceValues::getServiceId,
                        K8sServiceInstanceValues::getInstanceId,
                        K8sServiceInstanceValues::getVersion

                ).orderByAsc(K8sServiceInstanceValues::getVersion)
                .list();
    }

    @Override
    public String getValueFromRepo(Integer serviceId, String artifactType) {
        // 1. 根据 serviceId 查询 FrameK8sServiceEntity 对象
        FrameK8sServiceEntity entity = frameK8sServiceService.getById(serviceId);
        // 2. 解析 artifact 字段
        K8sArtifact artifact = YamlUtils.parseYaml(entity.getArtifact(), K8sArtifact.class);
        if (artifact == null) {
            throw new BusinessException("服务未配置 artifact 信息");
        }
        ServiceMetaItem item = frameK8sServiceService.getServiceMetaItem(serviceId);

        // 3. 根据 artifactType 获取对应的文件内容
        if ("helm".equals(artifactType)) {
            List<String> helmCharts = artifact.getHelm();
            if (CollectionUtil.isEmpty(helmCharts)) {
                throw new BusinessException("服务未配置 helm chart 信息");
            }
            // 获取第一个 chart 的 values.yaml
            String chartName = helmCharts.get(0);
            return getHelmValuesYaml(item, chartName);
        } else if ("yaml".equals(artifactType)) {
            List<String> yamlFiles = artifact.getYaml();
            if (CollectionUtil.isEmpty(yamlFiles)) {
                throw new BusinessException("服务未配置 yaml 文件信息");
            }
            // 获取第一个 yaml 文件
            String yamlFile = yamlFiles.get(0);
            return getYamlFileContent(item, yamlFile);
        } else {
            throw new BusinessException("不支持的 artifact 类型：" + artifactType);
        }
    }

    @Override
    public K8sServiceInstanceValues save(K8sServiceInstanceValuesSaveDTO values) {
        K8sClusterNamespace namespace = k8sClusterNamespaceService.createIfAbsent(new K8sNamespaceIdentityDTO(values.getClusterId(), values.getNamespace()));
        // 2. 根据 serviceId 查询服务实例，如果不存在则创建
        K8sServiceInstance instance = k8sServiceInstanceService.createIfAbsent(values.getClusterId(), namespace.getId(), values.getServiceId());

        // 3. 获取当前最大 version
        Integer maxVersion = lambdaQuery()
                .eq(K8sServiceInstanceValues::getInstanceId, instance.getId())
                .orderByDesc(K8sServiceInstanceValues::getVersion)
                .oneOpt()
                .map(K8sServiceInstanceValues::getVersion)
                .orElse(0);

        // 4. 创建 K8sServiceInstanceValues 对象
        K8sServiceInstanceValues instanceValues = new K8sServiceInstanceValues();
        instanceValues.setClusterId(values.getClusterId());
        instanceValues.setNamespaceId(namespace.getId());
        instanceValues.setServiceId(values.getServiceId());
        instanceValues.setInstanceId(instance.getId());
        instanceValues.setValues(values.getValues());
        instanceValues.setDeltaValues(values.getDeltaValues());
        instanceValues.setVersion(maxVersion + 1);

        save(instanceValues);
        return instanceValues;
    }

    @Override
    public K8sServiceInstanceValues update(K8sServiceInstanceValuesUpdateDTO values) {
        K8sServiceInstanceValues db = getById(values.getId());
        if (db == null) {
            throw new BusinessHintException("对象不存在");
        }
        db.setDeltaValues(values.getDeltaValues());
        updateById(db);
        return db;
    }

    /**
     * 从 helm chart 中获取 values.yaml 内容
     */
    private String getHelmValuesYaml(ServiceMetaItem item, String chartName) {
        File tmp = null;
        String extractDir = null;
        try {
            MetaStorage storage = StorageUtils.getMetaStorage();
            tmp = PathUtils.createTmpFile("helm", "_" + chartName);
            try (OutputStream out = Files.newOutputStream(tmp.toPath())) {
                storage.downResource(item, chartName, () -> out);
            }
            extractDir = TarUtils.decompressToTemp(tmp.getAbsolutePath());
            Path valuesPath = Paths.get(extractDir, "values.yaml");
            if (!valuesPath.toFile().exists()) {
                throw new BusinessException("chart 中未找到 values.yaml 文件");
            }
            return FileUtil.readString(tmp, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException("读取 helm chart 失败：" + e.getMessage(), e);
        } finally {
            FileUtil.del(tmp);
            FileUtil.del(extractDir);
        }
    }

    /**
     * 从 raw repo 获取 yaml 文件内容
     */
    private String getYamlFileContent(ServiceMetaItem item, String yamlFile) {
        try {
            MetaStorage storage = StorageUtils.getMetaStorage();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            storage.downResource(item, yamlFile, () -> out);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BusinessException("读取 yaml 文件失败：" + e.getMessage(), e);
        }
    }

}
