package com.datasophon.api.service.instance.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
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
import com.datasophon.api.vo.instance.K8sServiceInstanceValuesVO;
import com.datasophon.common.model.k8s.K8sArtifact;
import com.datasophon.common.storage.MetaStorage;
import com.datasophon.common.storage.StorageUtils;
import com.datasophon.common.storage.vo.ServiceMetaItem;
import com.datasophon.dao.entity.cluster.K8sClusterNamespace;
import com.datasophon.dao.entity.frame.FrameK8sServiceEntity;
import com.datasophon.dao.entity.instance.K8sServiceInstance;
import com.datasophon.dao.entity.instance.K8sServiceInstanceValues;
import com.datasophon.dao.mapper.instance.K8sServiceInstanceValuesMapper;
import org.eclipse.jetty.util.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.List;

/**
 * @author zhanghuangbin
 */
@Service("k8sServiceInstanceValuesService")
@Transactional(rollbackFor = BusinessHintException.class)
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

                ).orderByDesc(K8sServiceInstanceValues::getVersion)
                .list();
    }

    @Override
    public K8sServiceInstanceValuesVO getValueFromRepo(Integer serviceId, String artifactType) {
        // 1. 根据 serviceId 查询 FrameK8sServiceEntity 对象
        FrameK8sServiceEntity entity = frameK8sServiceService.getById(serviceId);
        // 2. 解析 artifact 字段
        K8sArtifact artifact = JSONObject.parseObject(entity.getArtifact(), K8sArtifact.class);
        if (artifact == null) {
            throw new BusinessException("服务未配置 artifact 信息");
        }
        ServiceMetaItem item = frameK8sServiceService.getServiceMetaItem(serviceId);
        // 3. 根据 artifactType 获取对应的文件内容
        try {
            MetaStorage storage = StorageUtils.getMetaStorage();
            K8sServiceInstanceValuesVO values = new K8sServiceInstanceValuesVO();
            if ("helm".equals(artifactType)) {
                if (StringUtil.isEmpty(artifact.getHelm())) {
                    throw new BusinessException("服务未配置 helm chart 信息");
                }
                values.setValues(storage.getHelmValuesYaml(item, artifact.getHelm()));
            } else if ("yaml".equals(artifactType)) {
                if (StrUtil.isEmpty(artifact.getYaml())) {
                    throw new BusinessException("服务未配置 yaml 文件信息");
                }
                String yamlFile = artifact.getYaml();
                values.setValues(storage.getResourceAsString(item, yamlFile, true));
            } else {
                throw new BusinessException("不支持的 artifact 类型：" + artifactType);
            }
            values.setDeltaValues(storage.getResourceAsString(item, entity.getRuntime(), false));
            return values;
        } catch (IOException e) {
            throw new BusinessException(String.format("IO异常，%s", e.getMessage()), e);
        }
    }

    @Override
    public K8sServiceInstanceValues save(K8sServiceInstanceValuesSaveDTO values) {
        K8sClusterNamespace namespace = k8sClusterNamespaceService.createIfAbsent(new K8sNamespaceIdentityDTO(values.getClusterId(), values.getNamespace()));
        // 2. 根据 serviceId 查询服务实例，如果不存在则创建
        K8sServiceInstance instance = k8sServiceInstanceService.createIfAbsent(values.getClusterId(), namespace.getId(), values.getServiceId());

        // 3. 获取当前最大 version
        List<K8sServiceInstanceValues> list = lambdaQuery()
                .eq(K8sServiceInstanceValues::getInstanceId, instance.getId())
                .orderByDesc(K8sServiceInstanceValues::getVersion)
                .last("limit 1")
                .list();
        Integer maxVersion = list.isEmpty() ? 0 : list.get(0).getVersion();

        // 4. 创建 K8sServiceInstanceValues 对象
        K8sServiceInstanceValues instanceValues = BeanUtil.toBean(values, K8sServiceInstanceValues.class);
        instanceValues.setNamespaceId(namespace.getId());
        instanceValues.setInstanceId(instance.getId());
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

    @Override
    public K8sServiceInstanceValues getNewestValuesByInstanceId(Integer instanceId) {
        return lambdaQuery()
                .eq(K8sServiceInstanceValues::getInstanceId, instanceId)
                .orderByDesc(K8sServiceInstanceValues::getVersion)
                .last("limit 1")
                .one();
    }

    @Override
    public void removeByInstanceId(Integer instanceId) {
        lambdaUpdate().eq(K8sServiceInstanceValues::getInstanceId, instanceId).remove();
    }

    @Override
    public void removeByClusterId(Integer clusterId) {
        lambdaUpdate().eq(K8sServiceInstanceValues::getClusterId, clusterId).remove();
    }

}
