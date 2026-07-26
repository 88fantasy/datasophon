/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.datasophon.api.master.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.api.service.ClusterAlertHistoryService;
import com.datasophon.api.service.ClusterServiceInstanceService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.dao.entity.ClusterAlertHistory;
import com.datasophon.dao.entity.ClusterServiceInstanceEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.domain.alert.gateway.AlertHistoryGateway;
import com.datasophon.domain.alert.model.AlertLabels;
import com.datasophon.domain.alert.model.AlertMessage;
import com.datasophon.domain.alert.model.Alerts;
import com.datasophon.domain.alert.model.Annotations;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private AlertHistoryGateway alertHistoryGateway;

    @Mock
    private ClusterHostService hostService;

    @Mock
    private ClusterAlertHistoryService alertHistoryService;

    @Mock
    private ClusterServiceInstanceService serviceInstanceService;

    @Mock
    private ClusterServiceRoleInstanceService roleInstanceService;

    @InjectMocks
    private AlertService alertService;

    @Test
    void firingServiceAlert_persistsRoleAndServiceInstanceIds() {
        AlertLabels labels = new AlertLabels();
        labels.setAlertname("DataNode进程存活");
        labels.setClusterId(1);
        labels.setServiceRoleName("DataNode");
        labels.setInstance("node-1:9100");
        labels.setJob("hdfs");
        labels.setSeverity("warning");

        Annotations annotations = new Annotations();
        annotations.setDescription("DataNode unavailable");
        annotations.setSummary("Restart DataNode");

        Alerts alert = new Alerts();
        alert.setStatus("firing");
        alert.setLabels(labels);
        alert.setAnnotations(annotations);

        AlertMessage message = new AlertMessage();
        message.setAlerts(List.of(alert));

        ClusterServiceRoleInstanceEntity roleInstance = new ClusterServiceRoleInstanceEntity();
        roleInstance.setId(11);
        roleInstance.setServiceId(22);
        ClusterServiceInstanceEntity serviceInstance = new ClusterServiceInstanceEntity();
        serviceInstance.setId(22);

        when(alertHistoryGateway.hasEnabledAlertHistory("DataNode进程存活", 1, "node-1"))
                .thenReturn(false);
        when(roleInstanceService.getOneServiceRole("DataNode", "node-1", 1))
                .thenReturn(roleInstance);
        when(serviceInstanceService.getById(22)).thenReturn(serviceInstance);

        alertService.handleAlertMessage(message);

        ArgumentCaptor<ClusterAlertHistory> historyCaptor = ArgumentCaptor.forClass(ClusterAlertHistory.class);
        verify(alertHistoryService).save(historyCaptor.capture());
        assertThat(historyCaptor.getValue().getServiceRoleInstanceId()).isEqualTo(11);
        assertThat(historyCaptor.getValue().getServiceInstanceId()).isEqualTo(22);
    }
}
