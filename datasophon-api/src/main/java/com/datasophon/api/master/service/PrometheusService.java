/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.datasophon.api.master.service;

import cn.hutool.http.HttpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datasophon.api.load.ServiceRoleJmxMap;
import com.datasophon.api.master.handler.service.ServiceConfigureHandler;
import com.datasophon.api.master.transport.WorkerCallAdapter;
import com.datasophon.api.service.ClusterServiceInstanceService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.FrameServiceService;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.common.Constants;
import com.datasophon.common.cache.CacheUtils;
import com.datasophon.common.command.GenerateAlertConfigCommand;
import com.datasophon.common.command.GenerateHostPrometheusConfig;
import com.datasophon.common.command.GeneratePrometheusConfigCommand;
import com.datasophon.common.command.GenerateSRPromConfigCommand;
import com.datasophon.common.model.Generators;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterServiceInstanceEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.entity.FrameServiceEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Prometheus 配置生成 Spring Service，业务逻辑完全来自 PrometheusActor。
 * 所有 public 方法均标注 @Async("masterExecutor")，保持原有的 fire-and-forget 语义。
 */
@Service
public class PrometheusService {

    private static final Logger logger = LoggerFactory.getLogger(PrometheusService.class);

    private final WorkerCallAdapter workerCallAdapter;
    private final ClusterServiceRoleInstanceService roleInstanceService;
    private final ClusterServiceInstanceService serviceInstanceService;
    private final FrameServiceService frameServiceService;
    private final ClusterHostService hostService;

    public PrometheusService(WorkerCallAdapter workerCallAdapter,
                              ClusterServiceRoleInstanceService roleInstanceService,
                              ClusterServiceInstanceService serviceInstanceService,
                              FrameServiceService frameServiceService,
                              ClusterHostService hostService) {
        this.workerCallAdapter = workerCallAdapter;
        this.roleInstanceService = roleInstanceService;
        this.serviceInstanceService = serviceInstanceService;
        this.frameServiceService = frameServiceService;
        this.hostService = hostService;
    }

    @Async("masterExecutor")
    public void generateAlertConfig(GenerateAlertConfigCommand command) {
        doIfInstancePresent(command.getClusterId(), false, prometheusInstance -> {
            ExecResult configResult = workerCallAdapter.generateAlertConfig(prometheusInstance.getHostname(), command);
            if (configResult.getExecResult()) {
                logger.info("Generate prometheus alert config success, now start to reload prometheus");
                HttpUtil.post("http://" + prometheusInstance.getHostname() + ":9090/-/reload", "");
            }
        });
    }

    @Async("masterExecutor")
    public void generatePrometheus(GeneratePrometheusConfigCommand command) {
        ClusterServiceInstanceEntity serviceInstance = serviceInstanceService.getById(command.getServiceInstanceId());
        List<ClusterServiceRoleInstanceEntity> roleInstanceList =
                roleInstanceService.getServiceRoleInstanceListByServiceId(serviceInstance.getId());

        doIfInstancePresent(command.getClusterId(), false, prometheusInstance -> {
            logger.info("start to generate {} prometheus config", serviceInstance.getServiceName());
            HashMap<Generators, List<ServiceConfig>> configFileMap = new HashMap<>();
            HashMap<String, List<String>> roleMap = new HashMap<>();

            for (ClusterServiceRoleInstanceEntity roleInstance : roleInstanceList) {
                roleMap.computeIfAbsent(roleInstance.getServiceRoleName(), k -> new ArrayList<>())
                        .add(roleInstance.getHostname());
            }

            for (Map.Entry<String, List<String>> roleEntry : roleMap.entrySet()) {
                Generators generators = new Generators();
                generators.setFilename(roleEntry.getKey().toLowerCase() + ".json");
                generators.setOutputDirectory("configs");
                generators.setConfigFormat("custom");
                generators.setTemplateName("scrape.ftl");

                List<String> hostnames = roleEntry.getValue();
                ArrayList<ServiceConfig> serviceConfigs = new ArrayList<>();
                String serviceName = serviceInstance.getServiceName();
                String serviceRoleName = roleEntry.getKey();
                String clusterFrame = command.getClusterFrame();

                for (String hostname : hostnames) {
                    String jmxKey = clusterFrame + Constants.UNDERLINE + serviceName
                                    + Constants.UNDERLINE + serviceRoleName;
                    if (ServiceRoleJmxMap.exists(jmxKey)) {
                        ServiceConfig sc = new ServiceConfig();
                        sc.setName(serviceRoleName + Constants.UNDERLINE + hostname);
                        sc.setValue(hostname + ":" + ServiceRoleJmxMap.get(jmxKey));
                        sc.setRequired(true);
                        sc.setEnabled(true);
                        serviceConfigs.add(sc);
                    }
                }
                configFileMap.put(generators, serviceConfigs);
            }

            ServiceRoleInfo serviceRoleInfo = buildServiceRoleInfo(prometheusInstance);
            serviceRoleInfo.setConfigFileMap(configFileMap);
            serviceRoleInfo.setHostname(prometheusInstance.getHostname());
            ExecResult execResult = new ServiceConfigureHandler().handlerRequest(serviceRoleInfo);
            if (execResult.getExecResult()) {
                HttpUtil.post("http://" + prometheusInstance.getHostname() + ":9090/-/reload", "");
            }
        });
    }

    @Async("masterExecutor")
    public void generateHostPrometheusConfig(GenerateHostPrometheusConfig command) {
        Integer clusterId = command.getClusterId();
        List<ClusterHostDO> hostList = hostService.list(
                new QueryWrapper<ClusterHostDO>()
                        .eq(Constants.MANAGED, 1)
                        .eq(Constants.CLUSTER_ID, clusterId));

        doIfInstancePresent(clusterId, false, prometheusInstance -> {
            HashMap<Generators, List<ServiceConfig>> configFileMap = new HashMap<>();

            Generators workerGenerators = buildGenerators("worker.json");
            Generators nodeGenerators = buildGenerators("linux.json");
            Generators masterGenerators = buildGenerators("master.json");

            ArrayList<ServiceConfig> workerConfigs = new ArrayList<>();
            ArrayList<ServiceConfig> nodeConfigs = new ArrayList<>();
            ArrayList<ServiceConfig> masterConfigs = new ArrayList<>();

            // master node entry
            ServiceConfig masterConfig = new ServiceConfig();
            masterConfig.setName("master_" + CacheUtils.get(Constants.HOSTNAME));
            masterConfig.setValue(CacheUtils.get(Constants.HOSTNAME) + ":8586");
            masterConfig.setRequired(true);
            masterConfig.setEnabled(true);
            masterConfigs.add(masterConfig);

            for (ClusterHostDO host : hostList) {
                ServiceConfig workerConfig = new ServiceConfig();
                workerConfig.setName("worker_" + host.getHostname());
                workerConfig.setValue(host.getHostname() + ":8585");
                workerConfig.setRequired(true);
                workerConfig.setEnabled(true);
                workerConfigs.add(workerConfig);

                ServiceConfig nodeConfig = new ServiceConfig();
                nodeConfig.setName("node_" + host.getHostname());
                nodeConfig.setValue(host.getHostname() + ":9100");
                nodeConfig.setRequired(true);
                nodeConfig.setEnabled(true);
                nodeConfigs.add(nodeConfig);
            }

            configFileMap.put(workerGenerators, workerConfigs);
            configFileMap.put(nodeGenerators, nodeConfigs);
            configFileMap.put(masterGenerators, masterConfigs);

            ServiceRoleInfo serviceRoleInfo = buildServiceRoleInfo(prometheusInstance);
            serviceRoleInfo.setConfigFileMap(configFileMap);
            serviceRoleInfo.setHostname(prometheusInstance.getHostname());
            ExecResult execResult = new ServiceConfigureHandler().handlerRequest(serviceRoleInfo);
            if (execResult.getExecResult()) {
                HttpUtil.post("http://" + prometheusInstance.getHostname() + ":9090/-/reload", "");
            }
        });
    }

    @Async("masterExecutor")
    public void generateSRPromConfig(GenerateSRPromConfigCommand command) {
        ClusterServiceInstanceEntity serviceInstance = serviceInstanceService.getById(command.getServiceInstanceId());
        List<ClusterServiceRoleInstanceEntity> roleInstanceList =
                roleInstanceService.getServiceRoleInstanceListByServiceId(serviceInstance.getId());
        logger.info("start to generate {} prometheus config", serviceInstance.getServiceName());

        doIfInstancePresent(command.getClusterId(), true, prometheusInstance -> {
            ArrayList<String> feList = new ArrayList<>();
            ArrayList<String> beList = new ArrayList<>();

            for (ClusterServiceRoleInstanceEntity roleInstance : roleInstanceList) {
                String jmxKey = command.getClusterFrame() + Constants.UNDERLINE
                                + serviceInstance.getServiceName() + Constants.UNDERLINE
                                + roleInstance.getServiceRoleName();
                logger.info("jmxKey is {}", jmxKey);
                if ("SRFE".equals(roleInstance.getServiceRoleName())
                    || "DorisFE".equals(roleInstance.getServiceRoleName())
                    || "DorisFEObserver".equals(roleInstance.getServiceRoleName())) {
                    feList.add(roleInstance.getHostname() + ":" + ServiceRoleJmxMap.get(jmxKey));
                } else {
                    beList.add(roleInstance.getHostname() + ":" + ServiceRoleJmxMap.get(jmxKey));
                }
            }

            Generators generators = new Generators();
            generators.setFilename(command.getFilename());
            generators.setOutputDirectory("configs");
            generators.setConfigFormat("custom");
            generators.setTemplateName("starrocks-prom.ftl");

            ServiceConfig feConfig = new ServiceConfig();
            feConfig.setName("feList");
            feConfig.setValue(feList);
            feConfig.setRequired(true);
            feConfig.setEnabled(true);
            feConfig.setConfigType("map");

            ServiceConfig beConfig = new ServiceConfig();
            beConfig.setName("beList");
            beConfig.setValue(beList);
            beConfig.setRequired(true);
            beConfig.setEnabled(true);
            beConfig.setConfigType("map");

            ArrayList<ServiceConfig> serviceConfigs = new ArrayList<>();
            serviceConfigs.add(feConfig);
            serviceConfigs.add(beConfig);

            HashMap<Generators, List<ServiceConfig>> configFileMap = new HashMap<>();
            configFileMap.put(generators, serviceConfigs);

            ServiceRoleInfo serviceRoleInfo = buildServiceRoleInfo(prometheusInstance);
            serviceRoleInfo.setConfigFileMap(configFileMap);
            serviceRoleInfo.setHostname(prometheusInstance.getHostname());
            ExecResult execResult = new ServiceConfigureHandler().handlerRequest(serviceRoleInfo);
            if (execResult.getExecResult()) {
                HttpUtil.post("http://" + prometheusInstance.getHostname() + ":9090/-/reload", "");
            }
        });
    }

    // ─── private helpers ────────────────────────────────────────────────────────

    private void doIfInstancePresent(Integer clusterId, boolean throwIfAbsent,
                                     PrometheusConsumer consumer) {
        ClusterServiceRoleInstanceEntity prometheusInstance =
                roleInstanceService.getOneServiceRole("Prometheus", null, clusterId);
        if (prometheusInstance != null) {
            try {
                consumer.accept(prometheusInstance);
            } catch (Exception e) {
                logger.error("Prometheus operation failed for cluster {}: {}", clusterId, e.getMessage(), e);
            }
        } else if (throwIfAbsent) {
            throw new IllegalStateException("cannot find Prometheus service role instance for cluster " + clusterId);
        }
    }

    private ServiceRoleInfo buildServiceRoleInfo(ClusterServiceRoleInstanceEntity roleInstance) {
        ClusterServiceInstanceEntity serviceInstance = serviceInstanceService.lambdaQuery()
                .eq(ClusterServiceInstanceEntity::getId, roleInstance.getServiceId())
                .one();
        FrameServiceEntity frameService = frameServiceService.getById(serviceInstance.getFrameServiceId());

        ServiceRoleInfo result = new ServiceRoleInfo();
        result.setName("Prometheus");
        result.setParentName(frameService.getServiceName());
        result.setDecompressPackageName(frameService.getDecompressPackageName());
        result.setPackageName(frameService.getPackageName());
        return result;
    }

    private static Generators buildGenerators(String filename) {
        Generators g = new Generators();
        g.setFilename(filename);
        g.setOutputDirectory("configs");
        g.setConfigFormat("custom");
        g.setTemplateName("scrape.ftl");
        return g;
    }

    @FunctionalInterface
    interface PrometheusConsumer {
        void accept(ClusterServiceRoleInstanceEntity instance) throws Exception;
    }
}
