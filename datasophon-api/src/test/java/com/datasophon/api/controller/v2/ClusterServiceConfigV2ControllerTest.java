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

package com.datasophon.api.controller.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.api.ds.DsConfigService;
import com.datasophon.api.dto.ApiResponse;
import com.datasophon.api.dto.v2.SaveConfigRequest;
import com.datasophon.api.service.ClusterServiceInstanceConfigService;
import com.datasophon.api.service.ClusterServiceInstanceRoleGroupService;
import com.datasophon.api.service.ClusterServiceInstanceService;
import com.datasophon.api.service.ServiceInstallService;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterServiceInstanceEntity;
import com.datasophon.dao.entity.ClusterServiceInstanceRoleGroup;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ClusterServiceConfigV2ControllerTest {

    private final ClusterServiceInstanceConfigService configService = mock(ClusterServiceInstanceConfigService.class);
    private final ServiceInstallService serviceInstallService = mock(ServiceInstallService.class);
    private final ClusterServiceInstanceService instanceService = mock(ClusterServiceInstanceService.class);
    private final ClusterServiceInstanceRoleGroupService roleGroupService = mock(ClusterServiceInstanceRoleGroupService.class);
    private final DsConfigService dsConfigService = mock(DsConfigService.class);
    private final ClusterServiceConfigV2Controller controller = new ClusterServiceConfigV2Controller();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(controller, "configService", configService);
        ReflectionTestUtils.setField(controller, "serviceInstallService", serviceInstallService);
        ReflectionTestUtils.setField(controller, "instanceService", instanceService);
        ReflectionTestUtils.setField(controller, "roleGroupService", roleGroupService);
        ReflectionTestUtils.setField(controller, "dsConfigService", dsConfigService);
    }

    @Test
    void infoRejectsAnInstanceFromAnotherClusterBeforeReadingItsConfig() {
        when(instanceService.getById(8)).thenReturn(instance(8, 2, "DS"));

        ApiResponse<List<ServiceConfig>> response = controller.info(1, 8, 3, null);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo(404);
        verify(configService, never()).getServiceInstanceConfig(8, null, 3, 1, 10000);
        verify(dsConfigService, never()).mergeDdlFallback(1, List.of());
    }

    @Test
    void versionsRejectsRoleGroupThatBelongsToAnotherServiceInstance() {
        when(instanceService.getById(8)).thenReturn(instance(8, 1, "DS"));
        when(roleGroupService.getById(3)).thenReturn(roleGroup(3, 1, 9));

        ApiResponse<List<Integer>> response = controller.versions(1, 8, 3);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo(404);
        verify(configService, never()).getConfigVersion(8, 3);
    }

    @Test
    void saveRejectsRoleGroupFromAnotherClusterBeforePersisting() {
        when(instanceService.getById(8)).thenReturn(instance(8, 1, "DS"));
        when(roleGroupService.getById(3)).thenReturn(roleGroup(3, 2, 8));
        SaveConfigRequest request = new SaveConfigRequest();
        request.setRoleGroupId(3);
        request.setServiceConfig(List.of());

        ApiResponse<Void> response = controller.save(1, 8, request);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorCode()).isEqualTo(404);
        verify(serviceInstallService, never()).saveServiceConfig(1, "DS", List.of(), 3);
    }

    @Test
    void infoReadsDsConfigOnlyWhenThePathOwnsBothRecords() {
        List<ServiceConfig> configs = List.of();
        when(instanceService.getById(8)).thenReturn(instance(8, 1, "DS"));
        when(roleGroupService.getById(3)).thenReturn(roleGroup(3, 1, 8));
        when(configService.getServiceInstanceConfig(8, null, 3, 1, 10000)).thenReturn(Result.success(configs));
        when(dsConfigService.mergeDdlFallback(1, configs)).thenReturn(configs);

        ApiResponse<List<ServiceConfig>> response = controller.info(1, 8, 3, null);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isSameAs(configs);
    }

    private static ClusterServiceInstanceEntity instance(int id, int clusterId, String serviceName) {
        ClusterServiceInstanceEntity instance = new ClusterServiceInstanceEntity();
        instance.setId(id);
        instance.setClusterId(clusterId);
        instance.setServiceName(serviceName);
        return instance;
    }

    private static ClusterServiceInstanceRoleGroup roleGroup(int id, int clusterId, int serviceInstanceId) {
        ClusterServiceInstanceRoleGroup roleGroup = new ClusterServiceInstanceRoleGroup();
        roleGroup.setId(id);
        roleGroup.setClusterId(clusterId);
        roleGroup.setServiceInstanceId(serviceInstanceId);
        return roleGroup;
    }
}
