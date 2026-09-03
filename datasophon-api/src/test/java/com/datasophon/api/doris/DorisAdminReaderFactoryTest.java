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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.api.observability.ExternalOtelDatasourceProvider;
import com.datasophon.api.observability.OtelCredentialService;
import com.datasophon.api.observability.OtelCredentials;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.ClusterVariableService;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.entity.ClusterVariable;
import com.datasophon.dao.enums.ServiceRoleState;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.zaxxer.hikari.HikariDataSource;

class DorisAdminReaderFactoryTest {

    @Test
    void physicalClusterUsesRootAndExposesActualEndpoint() {
        ClusterServiceRoleInstanceService roles = mock(ClusterServiceRoleInstanceService.class);
        ClusterVariableService variables = mock(ClusterVariableService.class);
        when(roles.getServiceRoleInstanceListByClusterIdAndRoleName(7, "DorisFE"))
                .thenReturn(List.of(role("ddh-01", ServiceRoleState.RUNNING)));
        when(variables.getVariableByVariableName(7, "DORIS", "query_port"))
                .thenReturn(variable("query_port", "19030"));
        when(variables.getVariableByVariableName(7, "DORIS", "root_password"))
                .thenReturn(variable("root_password", "root-secret"));

        DorisAdminReaderFactory factory = factory(roles, variables, noExternal(), dataSource -> true);
        DorisAdminReaderFactory.DorisAdminConnection connection = factory.create(7);

        assertThat(connection.username()).isEqualTo("root");
        assertThat(connection.hostPort()).isEqualTo("ddh-01:19030");
        assertThat(connection.degraded()).isFalse();
        assertThat(factory.dataSourceForTest(7).getConnectionTimeout()).isEqualTo(5_000);
        assertThat(factory.dataSourceForTest(7).getJdbcUrl())
                .contains("ddh-01:19030")
                .contains("connectTimeout=5000")
                .contains("socketTimeout=10000");
    }

    @Test
    void importedClusterUsesReaderAccountAndExternalEndpoint() {
        ClusterServiceRoleInstanceService roles = mock(ClusterServiceRoleInstanceService.class);
        ClusterVariableService variables = mock(ClusterVariableService.class);
        ExternalOtelDatasourceProvider provider = external("10.0.0.9", "29030");
        OtelCredentialService credentials = mock(OtelCredentialService.class);
        when(credentials.getOrCreate(7)).thenReturn(new OtelCredentials("collector", "reader-secret"));
        DorisAdminReaderFactory factory = new DorisAdminReaderFactory(
                roles, variables, credentials, provider, dataSource -> true);

        DorisAdminReaderFactory.DorisAdminConnection connection = factory.create(7);

        assertThat(connection.username()).isEqualTo("otel_reader");
        assertThat(connection.hostPort()).isEqualTo("10.0.0.9:29030");
        assertThat(connection.degraded()).isFalse();
        verify(roles, never()).getServiceRoleInstanceListByClusterIdAndRoleName(anyInt(), anyString());
    }

    @Test
    void missingRootPasswordFallsBackToReaderWithDegradedFlag() {
        ClusterServiceRoleInstanceService roles = mock(ClusterServiceRoleInstanceService.class);
        ClusterVariableService variables = mock(ClusterVariableService.class);
        when(roles.getServiceRoleInstanceListByClusterIdAndRoleName(7, "DorisFE"))
                .thenReturn(List.of(role("ddh-01", ServiceRoleState.RUNNING)));
        when(variables.getVariableByVariableName(7, "DORIS", "query_port"))
                .thenReturn(variable("query_port", "9030"));
        OtelCredentialService credentials = mock(OtelCredentialService.class);
        when(credentials.getOrCreate(7)).thenReturn(new OtelCredentials("collector", "reader-secret"));
        DorisAdminReaderFactory factory = new DorisAdminReaderFactory(
                roles, variables, credentials, noExternal(), dataSource -> true);

        DorisAdminReaderFactory.DorisAdminConnection connection = factory.create(7);

        assertThat(connection.username()).isEqualTo("otel_reader");
        assertThat(connection.degraded()).isTrue();
        assertThat(connection.degradedReason()).isEqualTo(DorisAdminReaderFactory.ROOT_FALLBACK_REASON);
    }

    @Test
    void rejectedRootFallsBackAndChangedSettingsReplaceAndCloseTheOldPool() {
        ClusterServiceRoleInstanceService roles = mock(ClusterServiceRoleInstanceService.class);
        ClusterVariableService variables = mock(ClusterVariableService.class);
        when(roles.getServiceRoleInstanceListByClusterIdAndRoleName(7, "DorisFE"))
                .thenReturn(List.of(role("ddh-01", ServiceRoleState.RUNNING)));
        when(variables.getVariableByVariableName(7, "DORIS", "query_port"))
                .thenReturn(variable("query_port", "9030"));
        when(variables.getVariableByVariableName(7, "DORIS", "root_password"))
                .thenReturn(variable("root_password", "bad-root"), variable("root_password", "good-root"));
        OtelCredentialService credentials = mock(OtelCredentialService.class);
        when(credentials.getOrCreate(7)).thenReturn(new OtelCredentials("collector", "reader-secret"));
        DorisAdminReaderFactory.ConnectionVerifier verifier = dataSource -> {
            if ("root".equals(dataSource.getUsername()) && "bad-root".equals(dataSource.getPassword())) {
                return false;
            }
            return true;
        };
        DorisAdminReaderFactory factory = new DorisAdminReaderFactory(
                roles, variables, credentials, noExternal(), verifier);

        DorisAdminReaderFactory.DorisAdminConnection degraded = factory.create(7);
        HikariDataSource oldReaderPool = factory.dataSourceForTest(7);
        DorisAdminReaderFactory.DorisAdminConnection root = factory.create(7);

        assertThat(degraded.username()).isEqualTo("otel_reader");
        assertThat(degraded.degraded()).isTrue();
        assertThat(root.username()).isEqualTo("root");
        assertThat(root.degraded()).isFalse();
        assertThat(oldReaderPool.isClosed()).isTrue();
        assertThat(factory.poolSizeForTest()).isEqualTo(1);
    }

    @Test
    void readerFailureIsReportedAsSafeConnectionFailure() {
        ClusterServiceRoleInstanceService roles = mock(ClusterServiceRoleInstanceService.class);
        ClusterVariableService variables = mock(ClusterVariableService.class);
        when(roles.getServiceRoleInstanceListByClusterIdAndRoleName(7, "DorisFE"))
                .thenReturn(List.of(role("ddh-01", ServiceRoleState.RUNNING)));
        OtelCredentialService credentials = mock(OtelCredentialService.class);
        when(credentials.getOrCreate(7)).thenReturn(new OtelCredentials("collector", "reader-secret"));
        DorisAdminReaderFactory factory = factory(roles, variables, noExternal(), dataSource -> false);

        assertThatThrownBy(() -> factory.create(7))
                .isInstanceOf(DorisAdminReaderFactory.DorisConnectionException.class)
                .hasMessage("Doris connection unavailable");
    }

    private static DorisAdminReaderFactory factory(ClusterServiceRoleInstanceService roles,
                                                   ClusterVariableService variables,
                                                   ExternalOtelDatasourceProvider provider,
                                                   DorisAdminReaderFactory.ConnectionVerifier verifier) {
        OtelCredentialService credentials = mock(OtelCredentialService.class);
        when(credentials.getOrCreate(7)).thenReturn(new OtelCredentials("collector", "reader-secret"));
        return new DorisAdminReaderFactory(roles, variables, credentials, provider, verifier);
    }

    private static ExternalOtelDatasourceProvider noExternal() {
        ExternalOtelDatasourceProvider provider = mock(ExternalOtelDatasourceProvider.class);
        when(provider.find(anyInt())).thenReturn(Optional.empty());
        return provider;
    }

    private static ExternalOtelDatasourceProvider external(String host, String port) {
        ExternalOtelDatasourceProvider provider = mock(ExternalOtelDatasourceProvider.class);
        when(provider.find(anyInt())).thenReturn(
                Optional.of(new ExternalOtelDatasourceProvider.ExternalDatasource(host, port)));
        return provider;
    }

    private static ClusterServiceRoleInstanceEntity role(String hostname, ServiceRoleState state) {
        ClusterServiceRoleInstanceEntity role = new ClusterServiceRoleInstanceEntity();
        role.setHostname(hostname);
        role.setServiceRoleState(state);
        return role;
    }

    private static ClusterVariable variable(String name, String value) {
        ClusterVariable variable = new ClusterVariable();
        variable.setVariableName(name);
        variable.setVariableValue(value);
        return variable;
    }
}
