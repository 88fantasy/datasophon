package com.datasophon.api.master.alert;

import com.datasophon.api.master.TypedActor;
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

import java.util.Date;
import java.util.List;

public class AlertActor extends TypedActor<AlertMessage> {

    private static final String FIRING = "firing";

    private static final String NODE = "node";

    private static final String WARNING = "warning";

    private static final String EXCEPTION = "exception";

    private static final String RESOLVED = "resolved";


    @Override
    protected void doOnReceive(AlertMessage message) throws Throwable {
        List<Alerts> alerts = message.getAlerts();
        for (Alerts alertInfo : alerts) {
            handleAlert(alertInfo);
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
        AlertHistoryGateway alertHistoryGateway = getBean(AlertHistoryGateway.class);
        ClusterHostService hostService = getBean(ClusterHostService.class);
        ClusterAlertHistoryService alertHistoryService = getBean(ClusterAlertHistoryService.class);
        ClusterServiceInstanceService serviceInstanceService = getBean(ClusterServiceInstanceService.class);
        ClusterServiceRoleInstanceService roleInstanceService = getBean(ClusterServiceRoleInstanceService.class);

        AlertLabels labels = alertInfo.getLabels();
        String alertName = labels.getAlertname();
        int clusterId = labels.getClusterId();
        String instance = labels.getInstance();
        String hostname = instance.split(":")[0];
        String serviceRoleName = labels.getServiceRoleName();

        boolean hasEnabledAlertHistory = alertHistoryGateway.hasEnabledAlertHistory(alertName, clusterId, hostname);
        // 查询服务实例，服务角色实例
        if (NODE.equals(serviceRoleName)) {
            ClusterHostDO clusterHost = hostService.getClusterHostByHostname(hostname);
            clusterHost.setHostState(EXCEPTION.equals(labels.getSeverity()) ? HostState.OFFLINE : HostState.EXISTS_ALARM);
            hostService.updateById(clusterHost);

            if (!hasEnabledAlertHistory) {
                addAlertHistory(alertInfo);
            }
        } else {
            ClusterServiceRoleInstanceEntity roleInstance = roleInstanceService.getOneServiceRole(serviceRoleName, hostname, clusterId);
            if (roleInstance == null) {
                return;
            }
            roleInstance.setServiceRoleState(EXCEPTION.equals(labels.getSeverity()) ? ServiceRoleState.STOP: ServiceRoleState.EXISTS_ALARM);
            roleInstanceService.updateById(roleInstance);

            ClusterServiceInstanceEntity serviceInstance = serviceInstanceService.getById(roleInstance.getServiceId());
            serviceInstance.setServiceState(EXCEPTION.equals(labels.getSeverity()) ? ServiceState.EXISTS_EXCEPTION : ServiceState.EXISTS_ALARM);
            serviceInstanceService.updateById(serviceInstance);

            if (!hasEnabledAlertHistory) {
                addAlertHistory(alertInfo);
            }
        }
    }

    private void addAlertHistory(Alerts alertInfo) {
        ClusterAlertHistoryService alertHistoryService = getBean(ClusterAlertHistoryService.class);
        AlertLabels labels = alertInfo.getLabels();
        String alertName = labels.getAlertname();
        int clusterId = labels.getClusterId();
        String instance = labels.getInstance();
        String hostname = instance.split(":")[0];
        ClusterAlertHistory clusterAlertHistory = ClusterAlertHistory.builder()
                .clusterId(clusterId)
                .alertGroupName(labels.getJob())
                .alertTargetName(alertName)
                .createTime(new Date())
                .updateTime(new Date())
                .alertLevel(WARNING.equals(labels.getSeverity()) ? AlertLevel.WARN : AlertLevel.EXCEPTION)
                .alertInfo(alertInfo.getAnnotations().getDescription())
                .alertAdvice(alertInfo.getAnnotations().getSummary())
                .hostname(hostname)
                .isEnabled(1)
                .build();
        alertHistoryService.save(clusterAlertHistory);
    }

    private void handleResolvedAlert(Alerts alertInfo) {
        AlertHistoryGateway alertHistoryGateway = getBean(AlertHistoryGateway.class);
        ClusterHostService hostService = getBean(ClusterHostService.class);
        ClusterServiceRoleInstanceService roleInstanceService = getBean(ClusterServiceRoleInstanceService.class);

        AlertLabels labels = alertInfo.getLabels();
        String hostname = labels.getInstance().split(":")[0];
        AlertHistory alertHistory = alertHistoryGateway.getEnabledAlertHistory(labels.getAlertname(), labels.getClusterId(), hostname);
        if (alertHistory == null) {
            return;
        }

        boolean nodeHasWarnAlertList = alertHistoryGateway.nodeHasWarnAlertList(hostname, labels.getServiceRoleName(), alertHistory.getId());
        if (NODE.equals(labels.getServiceRoleName())) {
            ClusterHostDO clusterHost = hostService.getClusterHostByHostname(hostname);
            clusterHost.setHostState(nodeHasWarnAlertList ? HostState.EXISTS_ALARM : HostState.RUNNING);
            hostService.updateById(clusterHost);
        } else {
            ClusterServiceRoleInstanceEntity roleInstance = roleInstanceService.getOneServiceRole(labels.getServiceRoleName(), hostname, labels.getClusterId());
            if (roleInstance.getServiceRoleState() != ServiceRoleState.RUNNING) {
                roleInstance.setServiceRoleState(nodeHasWarnAlertList ? ServiceRoleState.EXISTS_ALARM : ServiceRoleState.RUNNING);
                roleInstanceService.updateById(roleInstance);
            }
        }
        alertHistoryGateway.updateAlertHistoryToDisabled(alertHistory.getId());
    }


}
