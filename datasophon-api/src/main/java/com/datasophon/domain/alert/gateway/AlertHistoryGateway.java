package com.datasophon.domain.alert.gateway;

import com.datasophon.domain.alert.model.AlertHistory;

import java.util.Optional;

public interface AlertHistoryGateway {
    
    boolean hasEnabledAlertHistory(String alertname, int clusterId, String hostname);
    
    Optional<AlertHistory> getEnabledAlertHistory(String alertname, int clusterId, String hostname);
    
    void updateAlertHistoryToDisabled(Integer id);
    
    boolean nodeHasWarnAlertList(String hostname, String serviceRoleName, Integer id);
}
