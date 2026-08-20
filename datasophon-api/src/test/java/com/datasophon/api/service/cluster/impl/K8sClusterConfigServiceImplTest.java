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

package com.datasophon.api.service.cluster.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.api.master.service.DispatcherK8sAgentService;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.k8s.K8sDashboardCollectorService;
import com.datasophon.api.service.k8s.K8sService;
import com.datasophon.api.vo.k8s.K8sConnectionResult;
import com.datasophon.common.command.DispatcherK8sAgentCommand;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.enums.ClusterArchType;
import com.datasophon.dao.enums.ManageMode;
import com.datasophon.dao.enums.k8s.K8sAuthType;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class K8sClusterConfigServiceImplTest {

    private ClusterInfoService clusterInfoService;
    private K8sService k8sService;
    private DispatcherK8sAgentService dispatcherK8sAgentService;
    private K8sDashboardCollectorService dashboardCollectorService;
    private K8sClusterConfigServiceImpl service;

    @BeforeEach
    void setUp() {
        clusterInfoService = mock(ClusterInfoService.class);
        k8sService = mock(K8sService.class);
        dispatcherK8sAgentService = mock(DispatcherK8sAgentService.class);
        dashboardCollectorService = mock(K8sDashboardCollectorService.class);
        service = org.mockito.Mockito.spy(new K8sClusterConfigServiceImpl());
        ReflectionTestUtils.setField(service, "clusterInfoService", clusterInfoService);
        ReflectionTestUtils.setField(service, "k8sService", k8sService);
        ReflectionTestUtils.setField(service, "dispatcherK8sAgentService", dispatcherK8sAgentService);
        ReflectionTestUtils.setField(service, "k8sDashboardCollectorService", dashboardCollectorService);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void importedClusterDoesNotInstallAgentOrCollector() {
        givenCluster(ManageMode.IMPORTED);
        K8sClusterConfig config = tokenConfig("new-token");
        doReturn(null).when(service).getByClusterId(7);
        doReturn(true).when(service).save(any(K8sClusterConfig.class));
        when(k8sService.testConnection(any())).thenReturn(success());

        service.saveOrUpdateConfig(config);

        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verify(dispatcherK8sAgentService, never()).dispatchK8sAgent(any());
        verify(dashboardCollectorService, never()).install(any());
    }

    @Test
    void managedClusterInstallsAgentAndCollectorAfterCommit() {
        givenCluster(ManageMode.MANAGED);
        K8sClusterConfig config = tokenConfig("new-token");
        doReturn(null).when(service).getByClusterId(7);
        doReturn(true).when(service).save(any(K8sClusterConfig.class));
        when(k8sService.testConnection(any())).thenReturn(success());

        service.saveOrUpdateConfig(config);
        for (TransactionSynchronization synchronization : TransactionSynchronizationManager.getSynchronizations()) {
            synchronization.afterCommit();
        }

        verify(dispatcherK8sAgentService).dispatchK8sAgent(any(DispatcherK8sAgentCommand.class));
        verify(dashboardCollectorService).install(7);
    }

    @Test
    void blankCredentialUsesStoredValueForConnectionTestAndUpdate() {
        givenCluster(ManageMode.IMPORTED);
        K8sClusterConfig stored = tokenConfig("stored-token");
        stored.setId(11);
        K8sClusterConfig incoming = tokenConfig(" ");
        doReturn(stored).when(service).getByClusterId(7);
        doReturn(true).when(service).updateById(any(K8sClusterConfig.class));
        when(k8sService.testConnection(any())).thenReturn(success());

        service.saveOrUpdateConfig(incoming);

        assertThat(stored.getToken()).isEqualTo("stored-token");
        verify(k8sService).testConnection(org.mockito.ArgumentMatchers.argThat(
                config -> "stored-token".equals(config.getToken())));
    }

    @Test
    void connectionTestUsesStoredCredentialWhenRequestLeavesItBlank() {
        K8sClusterConfig stored = tokenConfig("stored-token");
        K8sClusterConfig incoming = tokenConfig(null);
        doReturn(stored).when(service).getByClusterId(7);
        when(k8sService.testConnection(any())).thenReturn(success());

        service.testConnection(incoming);

        verify(k8sService).testConnection(org.mockito.ArgumentMatchers.argThat(
                config -> "stored-token".equals(config.getToken())));
    }

    @Test
    void changingAuthenticationTypeClearsCredentialsFromPreviousType() {
        givenCluster(ManageMode.IMPORTED);
        K8sClusterConfig stored = tokenConfig(null);
        stored.setId(11);
        stored.setType(K8sAuthType.password);
        stored.setUsername("old-user");
        stored.setPassword("old-password");
        K8sClusterConfig incoming = tokenConfig("new-token");
        doReturn(stored).when(service).getByClusterId(7);
        doReturn(true).when(service).updateById(any(K8sClusterConfig.class));
        when(k8sService.testConnection(any())).thenReturn(success());

        service.saveOrUpdateConfig(incoming);

        assertThat(stored.getToken()).isEqualTo("new-token");
        assertThat(stored.getUsername()).isNull();
        assertThat(stored.getPassword()).isNull();
    }

    private void givenCluster(ManageMode manageMode) {
        ClusterInfoEntity cluster = new ClusterInfoEntity();
        cluster.setId(7);
        cluster.setArchType(ClusterArchType.k8s);
        cluster.setManageMode(manageMode);
        when(clusterInfoService.getById(7)).thenReturn(cluster);
    }

    private static K8sClusterConfig tokenConfig(String token) {
        K8sClusterConfig config = new K8sClusterConfig();
        config.setClusterId(7);
        config.setType(K8sAuthType.token);
        config.setServerHost("https://k8s.example:6443");
        config.setServerCert("certificate");
        config.setToken(token);
        return config;
    }

    private static K8sConnectionResult success() {
        K8sConnectionResult result = new K8sConnectionResult();
        result.setSuccess(true);
        return result;
    }
}
