/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
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

import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.utils.ProcessUtils;
import com.datasophon.common.command.HdfsEcCommand;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * HDFS 扩缩容白名单管理 Spring Service，业务逻辑来自 {@link HdfsECActor}。
 */
@Slf4j
@Service
public class HdfsECService {

    private final ClusterServiceRoleInstanceService roleInstanceService;

    public HdfsECService(ClusterServiceRoleInstanceService roleInstanceService) {
        this.roleInstanceService = roleInstanceService;
    }

    /**
     * 异步更新 HDFS DataNode 白名单（替代 HdfsECActor.tell(command)）。
     */
    @Async("masterExecutor")
    public void manageHdfsEC(HdfsEcCommand command) {
        List<ClusterServiceRoleInstanceEntity> datanodes = roleInstanceService.lambdaQuery()
                .eq(ClusterServiceRoleInstanceEntity::getServiceId, command.getServiceInstanceId())
                .eq(ClusterServiceRoleInstanceEntity::getServiceRoleName, "DataNode")
                .list();
        TreeSet<String> hostnameSet = datanodes.stream()
                .map(ClusterServiceRoleInstanceEntity::getHostname)
                .collect(Collectors.toCollection(TreeSet::new));
        try {
            ProcessUtils.hdfsEcMethond(command.getServiceInstanceId(), roleInstanceService, hostnameSet,
                    "whitelist", "NameNode");
        } catch (Exception e) {
            log.error("HDFS EC manage failed for serviceInstanceId={}: {}",
                    command.getServiceInstanceId(), e.getMessage(), e);
        }
    }
}
