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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.api.observability.OtelDorisReaderFactory;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterServiceInstanceService;
import com.datasophon.api.service.ClusterServiceRoleGroupConfigService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.ClusterVariableService;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.api.service.cluster.K8sClusterNamespaceService;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.api.service.instance.K8sServiceInstanceService;
import com.datasophon.api.service.instance.K8sServiceInstanceValuesService;
import com.datasophon.api.service.k8s.K8sService;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.enums.ClusterArchType;
import com.datasophon.dao.enums.ManageMode;

import org.junit.jupiter.api.Test;

class ClusterDeleteServiceTest {

    @Test
    void deletingImportedClusterRemovesCredentialsAndClosesReaderPool() {
        ClusterInfoService clusterInfoService = mock(ClusterInfoService.class);
        K8sClusterConfigService configService = mock(K8sClusterConfigService.class);
        K8sServiceInstanceService instanceService = mock(K8sServiceInstanceService.class);
        K8sServiceInstanceValuesService valuesService = mock(K8sServiceInstanceValuesService.class);
        K8sClusterNamespaceService namespaceService = mock(K8sClusterNamespaceService.class);
        ClusterVariableService variableService = mock(ClusterVariableService.class);
        OtelDorisReaderFactory readerFactory = mock(OtelDorisReaderFactory.class);
        ClusterInfoEntity cluster = new ClusterInfoEntity();
        cluster.setId(7);
        cluster.setArchType(ClusterArchType.k8s);
        cluster.setManageMode(ManageMode.IMPORTED);
        when(clusterInfoService.getById(7)).thenReturn(cluster);
        ClusterDeleteService service = new ClusterDeleteService(
                clusterInfoService,
                mock(ClusterServiceRoleInstanceService.class),
                mock(ClusterServiceRoleGroupConfigService.class),
                mock(ClusterServiceInstanceService.class),
                mock(ClusterHostService.class),
                configService,
                instanceService,
                valuesService,
                namespaceService,
                mock(K8sService.class),
                variableService,
                readerFactory);

        service.deleteCluster(7);

        verify(instanceService).removeByClusterId(7);
        verify(valuesService).removeByClusterId(7);
        verify(namespaceService).removeByClusterId(7);
        verify(configService).removeByClusterId(7);
        verify(variableService).removeByClusterId(7);
        verify(readerFactory).invalidate(7);
        verify(clusterInfoService).removeById(7);
    }
}
