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

package com.datasophon.api.service.k8s;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.api.service.ClusterVariableService;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.dao.entity.ClusterVariable;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.annotation.Transactional;

class DorisDatasourcePersistenceServiceTest {

    @Test
    void savesConfigAndReaderPasswordInTransactionalMethod() throws Exception {
        K8sClusterConfigService configService = mock(K8sClusterConfigService.class);
        ClusterVariableService variableService = mock(ClusterVariableService.class);
        DorisDatasourcePersistenceService service = new DorisDatasourcePersistenceService(
                configService, variableService);
        K8sClusterConfig config = new K8sClusterConfig();
        config.setClusterId(7);

        service.save(config, "reader-secret");

        verify(configService).updateById(config);
        ArgumentCaptor<ClusterVariable> captor = ArgumentCaptor.forClass(ClusterVariable.class);
        verify(variableService).save(captor.capture());
        assertThat(captor.getValue().getClusterId()).isEqualTo(7);
        assertThat(captor.getValue().getVariableName()).isEqualTo("otel_reader_password");
        assertThat(DorisDatasourcePersistenceService.class
                .getMethod("save", K8sClusterConfig.class, String.class)
                .getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void updatesExistingReaderPassword() {
        K8sClusterConfigService configService = mock(K8sClusterConfigService.class);
        ClusterVariableService variableService = mock(ClusterVariableService.class);
        ClusterVariable existing = new ClusterVariable();
        when(variableService.getVariableByVariableName(7, "DORIS", "otel_reader_password"))
                .thenReturn(existing);
        DorisDatasourcePersistenceService service = new DorisDatasourcePersistenceService(
                configService, variableService);
        K8sClusterConfig config = new K8sClusterConfig();
        config.setClusterId(7);

        service.save(config, "rotated-secret");

        assertThat(existing.getVariableValue()).isEqualTo("rotated-secret");
        verify(variableService).updateById(existing);
    }
}
