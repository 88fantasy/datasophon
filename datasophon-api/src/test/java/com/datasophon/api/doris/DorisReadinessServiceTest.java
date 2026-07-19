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

package com.datasophon.api.doris;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.ClusterVariableService;
import com.datasophon.common.model.ProcInfo;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.entity.ClusterVariable;
import com.datasophon.dao.enums.ServiceRoleState;

import java.util.List;

import org.junit.jupiter.api.Test;

class DorisReadinessServiceTest {

    private final ClusterServiceRoleInstanceService roleService = mock(ClusterServiceRoleInstanceService.class);
    private final ClusterVariableService variableService = mock(ClusterVariableService.class);
    private final TestDorisReadinessService service = new TestDorisReadinessService(roleService, variableService);

    @Test
    void returnsTrueWhenAllRunningBackendsAreAlive() {
        givenRoles(List.of(role("fe-1", ServiceRoleState.RUNNING)),
                List.of(role("be-1", ServiceRoleState.RUNNING), role("be-2", ServiceRoleState.RUNNING)));
        service.backends = List.of(backend(true), backend(true));
        when(variableService.getVariableByVariableName(7, "DORIS", "root_password"))
                .thenReturn(variable("configured-password"));

        assertTrue(service.waitUntilClusterReady(7, 1, 0));
    }

    @Test
    void returnsFalseWhenBackendIsNotAlive() {
        givenRoles(List.of(role("fe-1", ServiceRoleState.RUNNING)), List.of(role("be-1", ServiceRoleState.RUNNING)));
        service.backends = List.of(backend(false));
        when(variableService.getVariableByVariableName(7, "DORIS", "root_password"))
                .thenReturn(variable("configured-password"));

        assertFalse(service.waitUntilClusterReady(7, 2, 0));
        assertEquals(2, service.showBackendsCalls);
    }

    @Test
    void skipsBackendQueryWhenNoRunningBackendsExist() {
        givenRoles(List.of(role("fe-1", ServiceRoleState.RUNNING)), List.of());

        assertFalse(service.waitUntilClusterReady(7, 1, 0));
        assertFalse(service.showBackendsCalled);
        verifyNoInteractions(variableService);
    }

    @Test
    void retriesWhenBackendQueryFails() {
        givenRoles(List.of(role("fe-1", ServiceRoleState.RUNNING)), List.of(role("be-1", ServiceRoleState.RUNNING)));
        service.failure = new IllegalStateException("not ready");
        when(variableService.getVariableByVariableName(7, "DORIS", "root_password"))
                .thenReturn(variable("configured-password"));

        assertFalse(service.waitUntilClusterReady(7, 2, 0));
        assertTrue(service.showBackendsCalled);
        assertEquals(2, service.showBackendsCalls);
    }

    @Test
    void stopsRetryingWhenThreadIsInterrupted() {
        givenRoles(List.of(role("fe-1", ServiceRoleState.RUNNING)), List.of(role("be-1", ServiceRoleState.RUNNING)));
        service.backends = List.of(backend(false));
        when(variableService.getVariableByVariableName(7, "DORIS", "root_password"))
                .thenReturn(variable("configured-password"));

        Thread.currentThread().interrupt();
        try {
            assertFalse(service.waitUntilClusterReady(7, 3, 1));
            assertEquals(1, service.showBackendsCalls);
        } finally {
            Thread.interrupted();
        }
    }

    private void givenRoles(List<ClusterServiceRoleInstanceEntity> frontends,
                            List<ClusterServiceRoleInstanceEntity> backends) {
        when(roleService.getServiceRoleInstanceListByClusterIdAndRoleName(7, "DorisFE")).thenReturn(frontends);
        when(roleService.getServiceRoleInstanceListByClusterIdAndRoleName(7, "DorisBE")).thenReturn(backends);
    }

    private static ClusterServiceRoleInstanceEntity role(String hostname, ServiceRoleState state) {
        ClusterServiceRoleInstanceEntity role = new ClusterServiceRoleInstanceEntity();
        role.setHostname(hostname);
        role.setServiceRoleState(state);
        return role;
    }

    private static ProcInfo backend(boolean alive) {
        ProcInfo backend = new ProcInfo();
        backend.setAlive(alive);
        return backend;
    }

    private static ClusterVariable variable(String value) {
        ClusterVariable variable = new ClusterVariable();
        variable.setVariableValue(value);
        return variable;
    }

    private static class TestDorisReadinessService extends DorisReadinessService {

        private List<ProcInfo> backends = List.of();
        private Exception failure;
        private boolean showBackendsCalled;
        private int showBackendsCalls;

        private TestDorisReadinessService(ClusterServiceRoleInstanceService roleService,
                                          ClusterVariableService variableService) {
            super(roleService, variableService);
        }

        @Override
        protected List<ProcInfo> showBackends(String feHost, String rootPassword) throws Exception {
            showBackendsCalled = true;
            showBackendsCalls++;
            if (failure != null) {
                throw failure;
            }
            return backends;
        }
    }
}
