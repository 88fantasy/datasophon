/*
 *
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
 *
 */

package com.datasophon.api.load;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterVariableService;
import com.datasophon.api.service.FrameInfoService;
import com.datasophon.api.service.ddl.DdlMetaService;
import com.datasophon.common.storage.MetaStorage;
import com.datasophon.common.storage.StorageUtils;
import com.datasophon.common.storage.vo.ServiceMetaItem;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterVariable;
import com.datasophon.dao.entity.FrameInfoEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class LoadServiceMeta implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(LoadServiceMeta.class);


    @Autowired
    private FrameInfoService frameInfoService;


    @Autowired
    private ClusterVariableService variableService;

    @Autowired
    private ClusterInfoService clusterInfoService;

    @Autowired
    private DdlMetaService ddlMetaService;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        List<ClusterInfoEntity> clusters = clusterInfoService.list();
        loadGlobalVariables(clusters);

        Map<String, FrameInfoEntity> frameworkCache = new HashMap<>();

        MetaStorage metaStorage =  StorageUtils.getMetaStorage();
        List<ServiceMetaItem> vosDdlItems = metaStorage.listService(MetaStorage.VOS_DDL);
        Map<String, List<ServiceMetaItem>> groupeVosDdldMap = vosDdlItems.stream().collect(Collectors.groupingBy(ServiceMetaItem::getFramework));
        groupeVosDdldMap.forEach((frameCode, items)-> {
            FrameInfoEntity frameInfo = frameworkCache.computeIfAbsent(frameCode, c-> frameInfoService.saveFrameIfAbsent(frameCode));
            for (ServiceMetaItem item : items) {
                try {
                    String serviceDdl = metaStorage.getServiceDdL(item);
                    ddlMetaService.loadServiceVosDdl(clusters, frameInfo, item.getServiceName(), serviceDdl);
                } catch (Exception e) {
                    logger.error("invalid service ddl file: {} {}", frameCode, item.getServiceName(), e);
                }
            }
        });
        List<ServiceMetaItem> k8sItems = metaStorage.listService(MetaStorage.K8S);
        Map<String, List<ServiceMetaItem>> groupedK8sMap = k8sItems.stream().collect(Collectors.groupingBy(ServiceMetaItem::getFramework));
        groupedK8sMap.forEach((frameCode, items)-> {
            FrameInfoEntity frameInfo = frameworkCache.computeIfAbsent(frameCode, c-> frameInfoService.saveFrameIfAbsent(frameCode));
            for (ServiceMetaItem item : items) {
                try {
                    String serviceDdl = metaStorage.getServiceDdL(item);
                    ddlMetaService.loadServiceK8sDdl(frameInfo, item.getServiceName(), serviceDdl);
                } catch (Exception e) {
                    logger.error("invalid service ddl file: {} {}", frameCode, item.getServiceName(), e);
                }
            }
        });
    }


    public void loadGlobalVariables(List<ClusterInfoEntity> clusters) {
        if (CollUtil.isNotEmpty(clusters)) {
            for (ClusterInfoEntity cluster : clusters) {
                ConcurrentHashMap<String, String> globalVariables = GlobalVariables.genDefaultGlobalVariables();
                List<ClusterVariable> variables = variableService.list(Wrappers.<ClusterVariable>lambdaQuery()
                        .eq(ClusterVariable::getClusterId, cluster.getId()));
                for (ClusterVariable variable : variables) {
                    globalVariables.put(GlobalVariables.surroundKey(variable.getServiceName() + "." + variable.getVariableName()), variable.getVariableValue());
                }
                globalVariables.put(GlobalVariables.surroundKey(GlobalVariables.CLUSTER_CODE), cluster.getClusterFrame());
                GlobalVariables.put(cluster.getId(), globalVariables);
            }
        }
    }


}
