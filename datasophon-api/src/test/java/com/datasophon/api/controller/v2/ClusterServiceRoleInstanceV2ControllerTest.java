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
import static org.mockito.Mockito.when;

import com.datasophon.api.dto.ApiResponse;
import com.datasophon.api.dto.v2.ServiceRoleInstancePageResponse;
import com.datasophon.api.dto.v2.ServiceRoleInstanceResponse;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.ServiceInstancePortResolver;
import com.datasophon.api.service.ServiceInstancePortResolver.RolePort;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.ServiceRoleState;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 端口不是 {@link ClusterServiceRoleInstanceEntity} 的字段，需在 controller 里按
 * {@link ServiceInstancePortResolver} 批量回填（见 {@code list} 方法）；这里验证回填按下标正确对齐。
 */
class ClusterServiceRoleInstanceV2ControllerTest {

    private final ClusterServiceRoleInstanceService roleService = mock(ClusterServiceRoleInstanceService.class);
    private final ServiceInstancePortResolver portResolver = mock(ServiceInstancePortResolver.class);
    private final ClusterServiceRoleInstanceV2Controller controller = new ClusterServiceRoleInstanceV2Controller();

    ClusterServiceRoleInstanceV2ControllerTest() {
        ReflectionTestUtils.setField(controller, "clusterServiceRoleInstanceService", roleService);
        ReflectionTestUtils.setField(controller, "portResolver", portResolver);
    }

    @Test
    void listBackfillsPortsPerEntityByIndex() {
        ClusterServiceRoleInstanceEntity fe = role(1, "DorisFE");
        ClusterServiceRoleInstanceEntity be = role(2, "DorisBE");
        when(roleService.listAll(7, null, null, null, null, 1, 20))
                .thenReturn(Result.success(List.of(fe, be)).put(Constants.TOTAL, 2L));
        // controller 改走批量重载(见 ServiceInstancePortResolver#portsOf(List))，按下标与 entities 对齐。
        when(portResolver.portsOf(List.of(fe, be))).thenReturn(List.of(
                List.of(new RolePort("query_port", "FE节点上MySQL服务器的端口", 9030)),
                List.of(new RolePort("webserver_port", "BE WebServer端口", 8040),
                        new RolePort("brpc_port", "BE Rpc端口", 8060))));

        ApiResponse<ServiceRoleInstancePageResponse> response =
                controller.list(1, 7, 1, 20, null, null, null, null);

        List<ServiceRoleInstanceResponse> records = response.getData().getData();
        assertThat(records).hasSize(2);
        assertThat(records.get(0).getPorts()).containsExactly(
                new RolePort("query_port", "FE节点上MySQL服务器的端口", 9030));
        assertThat(records.get(1).getPorts()).containsExactly(
                new RolePort("webserver_port", "BE WebServer端口", 8040),
                new RolePort("brpc_port", "BE Rpc端口", 8060));
    }

    @Test
    void listReturnsEmptyRecordsWithoutTouchingResolverWhenNoRoles() {
        when(roleService.listAll(7, null, null, null, null, 1, 20)).thenReturn(Result.successEmptyCount());

        ApiResponse<ServiceRoleInstancePageResponse> response =
                controller.list(1, 7, 1, 20, null, null, null, null);

        assertThat(response.getData().getData()).isEmpty();
    }

    private static ClusterServiceRoleInstanceEntity role(Integer id, String roleName) {
        ClusterServiceRoleInstanceEntity role = new ClusterServiceRoleInstanceEntity();
        role.setId(id);
        role.setServiceRoleName(roleName);
        role.setServiceRoleState(ServiceRoleState.RUNNING);
        return role;
    }
}
