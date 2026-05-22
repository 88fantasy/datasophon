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

import com.datasophon.api.service.ClusterAlertHistoryService;
import com.datasophon.api.service.ClusterServiceInstanceService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.dao.entity.ClusterAlertHistory;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterServiceInstanceEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.AlertLevel;
import com.datasophon.dao.enums.ServiceRoleState;
import com.datasophon.dao.enums.ServiceState;
import com.datasophon.domain.alert.gateway.AlertHistoryGateway;
import com.datasophon.domain.alert.model.AlertHistory;
import com.datasophon.domain.alert.model.AlertLabels;
import com.datasophon.domain.alert.model.AlertMessage;
import com.datasophon.domain.alert.model.Alerts;
import com.datasophon.domain.host.enums.HostState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;

/**
 * 告警处理 Spring Service，业务逻辑完全来自 {@link AlertActor}。
 * {@code handleAlertMessage()} 标注 @Async，保持原有的 fire-and-forget 语义。
 */
@Service
public class AlertService {

    private static final Logger logger = LoggerFactory.getLogger(AlertService.class);

    private static final String FIRING = "firing";
    private static final String RESOLVED = "resolved";
    private static final String NODE = "node";
    private static final String WARNING = "warning";
    private static final String EXCEPTION = "exception";

    private final AlertHistoryGateway alertHistoryGateway;
    private final ClusterHostService hostService;
    private final ClusterAlertHistoryService alertHistoryService;
    private final ClusterServiceInstanceService serviceInstanceService;
    private final ClusterServiceRoleInstanceService roleInstanceService;

    public AlertService(AlertHistoryGateway alertHistoryGateway,
                        ClusterHostService hostService,
                        ClusterAlertHistoryService alertHistoryService,
                        ClusterServiceInstanceService serviceInstanceService,
                        ClusterServiceRoleInstanceService roleInstanceService) {
        this.alertHistoryGateway = alertHistoryGateway;
        this.hostService = hostService;
        this.alertHistoryService = alertHistoryService;
        this.serviceInstanceService = serviceInstanceService;
        this.roleInstanceService = roleInstanceService;
    }

    /**
     * 异步处理 Prometheus 告警消息（替代 AlertActor.tell(message)）。
     */
    @Async("masterExecutor")
    public void handleAlertMessage(AlertMessage message) {
        List<Alerts> alerts = message.getAlerts();
        for (Alerts alert : alerts) {
            handleAlert(alert);
        }
    }

    private void handleAlert(Alerts alertInfo) {
        String status = alertInfo.getStatus();
        if (FIRING.equals(status)) {
            handleFiringAlert(alertInfo);
        }
        if (RESOLVED.equals(status)) {
            handleResolvedAlert(alertInfo);
        }
    }

    private void handleFiringAlert(Alerts alertInfo) {
        AlertLabels labels = alertInfo.getLabels();
        String alertName = labels.getAlertname();
        int clusterId = labels.getClusterId();
        String hostname = labels.getInstance().split(":")[0];
        String serviceRoleName = labels.getServiceRoleName();

        boolean hasHistory = alertHistoryGateway.hasEnabledAlertHistory(alertName, clusterId, hostname);

        if (NODE.equals(serviceRoleName)) {
            ClusterHostDO clusterHost = hostService.getClusterHostByHostname(hostname);
            clusterHost.setHostState(EXCEPTION.equals(labels.getSeverity())
                    ? HostState.OFFLINE : HostState.EXISTS_ALARM);
            hostService.updateById(clusterHost);
            if (!hasHistory) {
                addAlertHistory(alertInfo);
            }
        } else {
            ClusterServiceRoleInstanceEntity roleInstance =
                    roleInstanceService.getOneServiceRole(serviceRoleName, hostname, clusterId);
            if (roleInstance == null) {
                return;
            }
            roleInstance.setServiceRoleState(EXCEPTION.equals(labels.getSeverity())
                    ? ServiceRoleState.STOP : ServiceRoleState.EXISTS_ALARM);
            roleInstanceService.updateById(roleInstance);

            ClusterServiceInstanceEntity serviceInstance = serviceInstanceService.getById(roleInstance.getServiceId());
            serviceInstance.setServiceState(EXCEPTION.equals(labels.getSeverity())
                    ? ServiceState.EXISTS_EXCEPTION : ServiceState.EXISTS_ALARM);
            serviceInstanceService.updateById(serviceInstance);

            if (!hasHistory) {
                addAlertHistory(alertInfo);
            }
        }
    }

    private void handleResolvedAlert(Alerts alertInfo) {
        AlertLabels labels = alertInfo.getLabels();
        String hostname = labels.getInstance().split(":")[0];
        AlertHistory alertHistory = alertHistoryGateway.getEnabledAlertHistory(
                labels.getAlertname(), labels.getClusterId(), hostname);
        if (alertHistory == null) {
            return;
        }
        boolean nodeHasWarn = alertHistoryGateway.nodeHasWarnAlertList(
                hostname, labels.getServiceRoleName(), alertHistory.getId());

        if (NODE.equals(labels.getServiceRoleName())) {
            ClusterHostDO clusterHost = hostService.getClusterHostByHostname(hostname);
            clusterHost.setHostState(nodeHasWarn ? HostState.EXISTS_ALARM : HostState.RUNNING);
            hostService.updateById(clusterHost);
        } else {
            ClusterServiceRoleInstanceEntity roleInstance = roleInstanceService.getOneServiceRole(
                    labels.getServiceRoleName(), hostname, labels.getClusterId());
            if (roleInstance != null && roleInstance.getServiceRoleState() != ServiceRoleState.RUNNING) {
                roleInstance.setServiceRoleState(nodeHasWarn
                        ? ServiceRoleState.EXISTS_ALARM : ServiceRoleState.RUNNING);
                roleInstanceService.updateById(roleInstance);
            }
        }
        alertHistoryGateway.updateAlertHistoryToDisabled(alertHistory.getId());
    }

    private void addAlertHistory(Alerts alertInfo) {
        AlertLabels labels = alertInfo.getLabels();
        String hostname = labels.getInstance().split(":")[0];
        ClusterAlertHistory history = ClusterAlertHistory.builder()
                .clusterId(labels.getClusterId())
                .alertGroupName(labels.getJob())
                .alertTargetName(labels.getAlertname())
                .createTime(new Date())
                .updateTime(new Date())
                .alertLevel(WARNING.equals(labels.getSeverity()) ? AlertLevel.WARN : AlertLevel.EXCEPTION)
                .alertInfo(alertInfo.getAnnotations().getDescription())
                .alertAdvice(alertInfo.getAnnotations().getSummary())
                .hostname(hostname)
                .isEnabled(1)
                .build();
        alertHistoryService.save(history);
    }
}
