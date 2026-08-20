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

package com.datasophon.api.service.extrepo.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.datasophon.api.dto.extrepo.DeploymentDTO;
import com.datasophon.api.dto.extrepo.RunDagDto;
import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.security.ImportedClusterWriteGuard;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.dag.DAGService;
import com.datasophon.api.service.extrepo.ExtRepoInstallService;
import com.datasophon.common.enums.CommandType;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.dag.DagDefinitionEntity;
import com.datasophon.dao.enums.ManageMode;
import com.datasophon.dao.enums.dag.DagStatus;
import com.datasophon.dao.mapper.dag.DagDefinitionEntityMapper;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ExtRepoInstallDelegateServiceImplTest {

    private static final int CLUSTER_ID = 7;

    private final ClusterInfoService clusterInfoService = mock(ClusterInfoService.class);
    private final ExtRepoInstallService physicalInstallService = mock(ExtRepoInstallService.class);
    private final ExtRepoInstallService k8sInstallService = mock(ExtRepoInstallService.class);
    private final DAGService dagService = mock(DAGService.class);
    private final DagDefinitionEntityMapper dagMapper = mock(DagDefinitionEntityMapper.class);
    private final ExtRepoInstallDelegateServiceImpl service = new ExtRepoInstallDelegateServiceImpl();

    @BeforeEach
    void setUp() {
        ClusterInfoEntity cluster = new ClusterInfoEntity();
        cluster.setId(CLUSTER_ID);
        cluster.setClusterName("imported-k8s");
        cluster.setManageMode(ManageMode.IMPORTED);
        when(clusterInfoService.getById(CLUSTER_ID)).thenReturn(cluster);

        ReflectionTestUtils.setField(service, "clusterInfoService", clusterInfoService);
        ReflectionTestUtils.setField(service, "physicalExtRepoInstallService", physicalInstallService);
        ReflectionTestUtils.setField(service, "k8SExtRepoInstallService", k8sInstallService);
        ReflectionTestUtils.setField(service, "dagService", dagService);
        ReflectionTestUtils.setField(service, "dagDefinitionEntityMapper", dagMapper);
        ReflectionTestUtils.setField(service, "importedClusterWriteGuard",
                new ImportedClusterWriteGuard(clusterInfoService));
    }

    @Test
    void deployRejectsImportedClusterBeforeDispatchingToLegacyHandler() {
        DeploymentDTO dto = new DeploymentDTO();
        dto.setClusterId(CLUSTER_ID);

        assertThatThrownBy(() -> service.deploy(dto))
                .isInstanceOf(BusinessHintException.class)
                .hasMessageContaining("接管模式")
                .hasMessageContaining("部署服务");

        verifyNoInteractions(physicalInstallService, k8sInstallService);
    }

    @Test
    void redeployRejectsImportedClusterBeforeLoadingOrDispatchingDagNodes() {
        RunDagDto dto = new RunDagDto();
        dto.setDagId("dag-1");
        DagDefinitionEntity definition = new DagDefinitionEntity();
        definition.setId("dag-1");
        definition.setClusterId(CLUSTER_ID);
        definition.setStatus(DagStatus.FAILED);
        definition.setCreatedTime(LocalDateTime.now());
        when(dagMapper.selectById("dag-1")).thenReturn(definition);

        assertThatThrownBy(() -> service.redeploy(dto))
                .isInstanceOf(BusinessHintException.class)
                .hasMessageContaining("接管模式")
                .hasMessageContaining("重新运行部署任务");

        verifyNoInteractions(dagService, physicalInstallService, k8sInstallService);
    }

    @Test
    void serviceCommandRejectsImportedClusterBeforeDispatching() {
        assertThatThrownBy(() -> service.generateAndExecSrvInstCmd(
                CLUSTER_ID, CommandType.STOP_SERVICE, List.of(11)))
                .isInstanceOf(BusinessHintException.class)
                .hasMessageContaining("接管模式")
                .hasMessageContaining("启停服务");

        verifyNoInteractions(physicalInstallService, k8sInstallService);
    }
}
