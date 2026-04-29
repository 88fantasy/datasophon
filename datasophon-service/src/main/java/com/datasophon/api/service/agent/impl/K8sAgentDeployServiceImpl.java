/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.datasophon.api.service.agent.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import com.datasophon.api.dto.instance.K8sNamespaceIdentityDTO;
import com.datasophon.api.service.agent.K8sAgentDeployService;
import com.datasophon.api.service.cluster.K8sClusterNamespaceService;
import com.datasophon.api.service.k8s.K8sService;
import com.datasophon.api.utils.HelmValueUtils;
import com.datasophon.common.k8s.client.HelmClient;
import com.datasophon.common.k8s.config.ClientOptions;
import com.datasophon.common.k8s.dto.UninstallParams;
import com.datasophon.common.k8s.dto.UpgradeParams;
import com.datasophon.common.k8s.vo.helm.HelmReleaseVO;
import com.datasophon.common.utils.PathUtils;
import com.datasophon.common.utils.PropertyUtils;
import com.datasophon.common.utils.nexus.NexusFacade;
import com.datasophon.common.utils.nexus.vo.Assert;
import com.datasophon.common.utils.nexus.vo.Component;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * K8s Agent 部署服务实现
 */
@Slf4j
@Service
@Transactional(rollbackFor = Exception.class)
public class K8sAgentDeployServiceImpl implements K8sAgentDeployService {

    private static final String RELEASE_NAME = "datasophon-k8s-agent";
    private static final String HELM_CHART = "datasophon-k8s-agent";

    @Autowired
    private K8sClusterNamespaceService k8sClusterNamespaceService;

    @Autowired
    private K8sService k8sService;

    @Override
    public void deployAgent(K8sClusterConfig config) {
        log.info("开始部署 K8s Agent: clusterId={}", config.getClusterId());
        try {
            ensureNamespaceExists(config);
            // 从 classpath 读取 Helm Chart 到临时目录
            File chartFile = downloadChartToTemp();
            applyHelmChart(config, chartFile);
        } catch (Exception e) {
            log.error("K8s Agent 部署失败：{}", RELEASE_NAME, e);
            throw new RuntimeException("部署 K8s Agent 失败：" + e.getMessage(), e);
        }
    }



    private void ensureNamespaceExists(K8sClusterConfig config) {
        log.info("检查并创建 Namespace，clusterId:{}, namespace:{}", config.getClusterId(), NAMESPACE);
        boolean created = k8sService.createIfAbsent(config, NAMESPACE);
        if (created) {
            log.info("已经在{}创建名空间{}", config.getServerHost(), NAMESPACE);
        }
        k8sClusterNamespaceService.createIfAbsent(new K8sNamespaceIdentityDTO(config.getClusterId(), NAMESPACE), 1);
        log.info("K8s namespace '{}' state 更新为 active, clusterId:{}", NAMESPACE, config.getClusterId());
    }

    /**
     * 从 nexus上下载 Helm Chart 到临时目录
     */
    private File downloadChartToTemp() throws IOException {
        Component component = NexusFacade.getHelmClient().getNewestComponent(HELM_CHART);
        if (component == null || CollUtil.isEmpty(component.getAssets())) {
            throw new IllegalStateException("未找到 Helm Chart: " + HELM_CHART);
        }

        Assert asset = component.getAssets().get(0);
        String downloadUrl = asset.getDownloadUrl();
        File tmpFile = PathUtils.createTmpFile("helm/" + RandomUtil.randomString(12), HELM_CHART + ".tgz");
        try (OutputStream out = Files.newOutputStream(tmpFile.toPath())) {
            NexusFacade.getCommonClient().download(downloadUrl, out);
        }

        log.info("Helm Chart 已下载到临时文件: {}", tmpFile.getAbsolutePath());
        return tmpFile;
    }


    private HelmReleaseVO applyHelmChart(K8sClusterConfig config, File chartFile) {
        log.info("应用 Helm Chart, Release 名称：{}, Namespace: {}", RELEASE_NAME, NAMESPACE);

        try (HelmClient client = buildHelmClient(config)) {
            UpgradeParams params = new UpgradeParams();
            params.setReleaseName(RELEASE_NAME);
            params.setChartPath(chartFile.getAbsolutePath());
            params.setNamespace(NAMESPACE);

            prepareParameter(params);


            log.info("使用 Helm 开始安装 {}", RELEASE_NAME);
            HelmReleaseVO result = client.upgrade(params);
            log.info("Helm upgrade 完成，状态：{}", result.getInfo().getStatus());


            if (!result.getInfo().getStatus().equalsIgnoreCase("deployed")) {
                throw new IllegalStateException(String.format("安装 %s 失败，原因：%s", RELEASE_NAME, result.getInfo().getNotes()));
            }

            log.info("K8s Agent 部署成功：{}, 版本：{}", RELEASE_NAME, result.getVersion());
            return result;
        } finally {
            FileUtil.del(chartFile);
        }
    }


    /**
     * 生成helm的运行时变量
     * @param params
     */
    private void prepareParameter(UpgradeParams params) {
        // 读取 master 的 common.properties，通过 --set-file 传入 Helm Chart
        String path = PropertyUtils.getFunctionalPropertyFile().getAbsolutePath();
        path = path.replaceAll("\\\\", "/");
        params.setSetFileValues(Collections.singletonList("config.commonProperties=" + path));

        Map<String, String> helmValues = HelmValueUtils.getExtraValues();
        Map<String, String> extraValues = new HashMap<>(helmValues);
        extraValues.put("image.registry", helmValues.get("nexus.imageRegistry"));
        List<String> extraValueList = new ArrayList<>();
        extraValues.forEach((k, v) -> extraValueList.add(k + "=" + v));
        params.setSetValues(extraValueList);
    }

    @Override
    public void undeployAgent(K8sClusterConfig config) {
        log.info("开始卸载 K8s Agent: release={}, namespace={}", RELEASE_NAME, NAMESPACE);

        try (HelmClient client = buildHelmClient(config)) {
            // 执行 helm uninstall
            UninstallParams uninstallParams = new UninstallParams();
            uninstallParams.setReleaseName(RELEASE_NAME);
            uninstallParams.setNamespace(NAMESPACE);
            uninstallParams.setKeepHistory(false);

            client.uninstall(uninstallParams);

            log.info("集群{}， K8s Agent 卸载成功：{}", config.getClusterId(), RELEASE_NAME);
        } catch (Exception e) {
            log.error("集群{}， K8s Agent 卸载失败：{}", config.getClusterId(), RELEASE_NAME, e);
            throw new IllegalStateException("卸载 K8s Agent 失败：" + e.getMessage(), e);
        }
    }


    /**
     * 根据 K8sClusterConfig 构建 Helm Client 配置
     */
    private HelmClient buildHelmClient(K8sClusterConfig config) {
        ClientOptions options = new ClientOptions();
        options.setKubeConfig(config.getKubeConfig());
        options.setToken(config.getToken());
        options.setUsername(config.getUsername());
        options.setPassword(config.getPassword());
        options.setServerCert(config.getServerCert());
        options.setServerName(config.getServerHost());
        return new HelmClient(options);
    }
}
