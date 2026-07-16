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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.api.hook.ServiceHookContext;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.ClusterVariableService;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.entity.ClusterVariable;
import com.datasophon.dao.enums.ServiceRoleState;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class DorisRootPasswordInitHookTest {

    private final DorisReadinessService readinessService = mock(DorisReadinessService.class);
    private final DorisSqlOperations dorisSqlOperations = mock(DorisSqlOperations.class);
    private final ClusterServiceRoleInstanceService roleService = mock(ClusterServiceRoleInstanceService.class);
    private final ClusterVariableService variableService = mock(ClusterVariableService.class);
    private final DorisRootPasswordInitHook hook = new DorisRootPasswordInitHook(
            readinessService, dorisSqlOperations, roleService, variableService);

    @Test
    void delegatesReadinessWithDdlParameters() {
        ServiceHookContext context = context(7);
        context.setParams(Map.of("maxAttempts", 3, "intervalMs", 10));
        when(readinessService.waitUntilClusterReady(7, 3, 10)).thenReturn(true);

        assertTrue(hook.isReady(context));
        verify(readinessService).waitUntilClusterReady(7, 3, 10);
    }

    @Test
    void nullClusterIsNotReady() {
        assertFalse(hook.isReady(context(null)));
    }

    @Test
    void skipsResetWhenTargetPasswordAlreadyWorks() throws Exception {
        givenRunningFe();
        givenVariable("root_password", "target-password");
        when(dorisSqlOperations.canConnect("fe-1", 9030, "target-password")).thenReturn(true);

        hook.invoke(context(7));

        verify(dorisSqlOperations, never()).resetRootAndAdminPassword("fe-1", 9030, "", "target-password");
    }

    @Test
    void resetsPasswordWhenOnlyEmptyPasswordWorks() throws Exception {
        givenRunningFe();
        givenVariable("root_password", "target-password");
        when(dorisSqlOperations.canConnect("fe-1", 9030, "target-password")).thenReturn(false);
        when(dorisSqlOperations.canConnect("fe-1", 9030, "")).thenReturn(true);

        hook.invoke(context(7));

        verify(dorisSqlOperations).resetRootAndAdminPassword("fe-1", 9030, "", "target-password");
    }

    @Test
    void failsWhenNeitherPasswordCanConnect() throws Exception {
        givenRunningFe();
        givenVariable("root_password", "target-password");
        when(dorisSqlOperations.canConnect("fe-1", 9030, "target-password")).thenReturn(false);
        when(dorisSqlOperations.canConnect("fe-1", 9030, "")).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> hook.invoke(context(7)));
        verify(dorisSqlOperations, never()).resetRootAndAdminPassword("fe-1", 9030, "", "target-password");
    }

    @Test
    void skipsJdbcWhenNoRunningFeExists() throws Exception {
        when(roleService.getServiceRoleInstanceListByClusterIdAndRoleName(7, "DorisFE")).thenReturn(List.of());
        givenVariable("root_password", "target-password");

        hook.invoke(context(7));

        verify(dorisSqlOperations, never()).canConnect("fe-1", 9030, "target-password");
    }

    @Test
    void rejectsBlankRootPasswordBeforeJdbcAccess() {
        givenVariable("root_password", " ");

        assertThrows(IllegalStateException.class, () -> hook.invoke(context(7)));
        verify(dorisSqlOperations, never()).canConnect("fe-1", 9030, "");
    }

    @Test
    void nullClusterSkipsPasswordInitialization() throws Exception {
        hook.invoke(context(null));

        verify(dorisSqlOperations, never()).canConnect("fe-1", 9030, "");
    }

    private void givenRunningFe() {
        ClusterServiceRoleInstanceEntity fe = new ClusterServiceRoleInstanceEntity();
        fe.setHostname("fe-1");
        fe.setServiceRoleState(ServiceRoleState.RUNNING);
        when(roleService.getServiceRoleInstanceListByClusterIdAndRoleName(7, "DorisFE")).thenReturn(List.of(fe));
    }

    private void givenVariable(String name, String value) {
        ClusterVariable variable = new ClusterVariable();
        variable.setVariableValue(value);
        when(variableService.getVariableByVariableName(7, "DORIS", name)).thenReturn(variable);
    }

    private static ServiceHookContext context(Integer clusterId) {
        ServiceHookContext context = new ServiceHookContext();
        context.setClusterId(clusterId);
        return context;
    }
}
