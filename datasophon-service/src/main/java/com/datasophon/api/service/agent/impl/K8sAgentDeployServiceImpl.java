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

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import com.datasophon.api.dto.agent.K8sAgentDeployParams;
import com.datasophon.api.service.agent.K8sAgentDeployService;
import com.datasophon.common.k8s.client.HelmClient;
import com.datasophon.common.k8s.config.ClientOptions;
import com.datasophon.common.k8s.dto.ChartInstallParameter;
import com.datasophon.common.k8s.dto.UninstallParams;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * K8s Agent 部署服务实现
 */
@Slf4j
@Service
public class K8sAgentDeployServiceImpl implements K8sAgentDeployService {

    private static final String HELM_CHART_PATH = "helm/datasophon-k8s-agent";
    private static final String DEFAULT_IMAGE_TAG = "2.1-SNAPSHOT";
    private static final Integer DEFAULT_SERVER_PORT = 8080;

    @Override
    public void deployAgent(K8sClusterConfig config) {
        log.info("开始部署 K8s Agent: clusterId={}", config.getClusterId());

        K8sAgentDeployParams params = K8sAgentDeployParams.builder()
            .clusterId(config.getClusterId())
            .releaseName("datasophon-agent-" + config.getClusterId())
            .namespace("datasophon-agent-" + config.getClusterId())
            .imageTag(DEFAULT_IMAGE_TAG)
            .serverPort(DEFAULT_SERVER_PORT)
            .build();

        try {
            // 从 classpath 读取 Helm Chart 到临时目录
            File chartDir = copyHelmChartToTemp();

            // 构建 Helm 客户端
            ClientOptions clientOptions = buildClientOptions(config);
            HelmClient helmClient = new HelmClient(clientOptions);

            // 执行 helm install
            ChartInstallParameter installParam = ChartInstallParameter.builder()
                .releaseName(params.getReleaseName())
                .chart(chartDir.getAbsolutePath())
                .namespace(params.getNamespace())
                .setValues(Arrays.asList(
                    "image.tag=" + params.getImageTag(),
                    "server.port=" + params.getServerPort()
                ))
                .build();

            log.info("执行 helm install: release={}, chart={}, namespace={}",
                installParam.getReleaseName(), installParam.getChart(), installParam.getNamespace());

            // TODO: HelmClient 需要添加 install 方法 (Task 6)
            // helmClient.install(installParam);

            log.info("K8s Agent 部署成功：{}", params.getReleaseName());

        } catch (Exception e) {
            log.error("K8s Agent 部署失败：{}", params.getReleaseName(), e);
            throw new RuntimeException("部署 K8s Agent 失败：" + e.getMessage(), e);
        }
    }

    @Override
    public void undeployAgent(K8sClusterConfig config) {
        String releaseName = "datasophon-agent-" + config.getClusterId();
        String namespace = releaseName;

        log.info("开始卸载 K8s Agent: release={}, namespace={}", releaseName, namespace);

        try {
            // 构建 Helm 客户端
            ClientOptions clientOptions = buildClientOptions(config);
            HelmClient helmClient = new HelmClient(clientOptions);

            // 执行 helm uninstall
            UninstallParams uninstallParams = new UninstallParams();
            uninstallParams.setReleaseName(releaseName);
            uninstallParams.setNamespace(namespace);
            uninstallParams.setKeepHistory(true);

            helmClient.uninstall(uninstallParams);

            log.info("K8s Agent 卸载成功：{}", releaseName);

        } catch (Exception e) {
            log.error("K8s Agent 卸载失败：{}", releaseName, e);
            throw new RuntimeException("卸载 K8s Agent 失败：" + e.getMessage(), e);
        }
    }

    @Override
    public boolean checkAgentStatus(K8sClusterConfig config) {
        // 第一阶段预留，返回 false
        log.warn("checkAgentStatus 方法暂未实现");
        return false;
    }

    /**
     * 从 classpath 复制 Helm Chart 到临时目录
     */
    private File copyHelmChartToTemp() throws Exception {
        Path tempDir = Files.createTempDirectory("helm-chart-");
        String[] chartFiles = {
            "Chart.yaml",
            "values.yaml",
            "templates/deployment.yaml",
            "templates/service.yaml",
            "templates/_helpers.tpl"
        };

        for (String file : chartFiles) {
            String resourcePath = HELM_CHART_PATH + "/" + file;
            File targetFile = tempDir.resolve(file).toFile();
            targetFile.getParentFile().mkdirs();

            try (InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
                if (is == null) {
                    throw new RuntimeException("找不到 Helm Chart 资源：" + resourcePath);
                }
                String content = IoUtil.read(is, StandardCharsets.UTF_8);
                FileUtil.writeString(content, targetFile, StandardCharsets.UTF_8);
            }
        }

        return tempDir.toFile();
    }

    /**
     * 根据 K8sClusterConfig 构建 Helm Client 配置
     */
    private ClientOptions buildClientOptions(K8sClusterConfig config) {
        ClientOptions options = new ClientOptions();
        options.setKubeConfig(config.getKubeConfig());
        options.setToken(config.getToken());
        options.setUsername(config.getUsername());
        options.setPassword(config.getPassword());
        options.setServerCert(config.getServerCert());
        options.setServerName(config.getServerHost());
        return options;
    }
}
