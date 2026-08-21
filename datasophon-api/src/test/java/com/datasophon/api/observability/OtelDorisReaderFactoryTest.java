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

package com.datasophon.api.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.ClusterVariableService;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.ServiceRoleState;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.zaxxer.hikari.HikariDataSource;

class OtelDorisReaderFactoryTest {

    @Test
    void treatsDorisFeWithActiveAlarmAsUsable() {
        ClusterServiceRoleInstanceService roleService = mock(ClusterServiceRoleInstanceService.class);
        ClusterVariableService variableService = mock(ClusterVariableService.class);
        when(roleService.getServiceRoleInstanceListByClusterIdAndRoleName(7, "DorisFE"))
                .thenReturn(List.of(role("ddh-01", ServiceRoleState.EXISTS_ALARM)));
        OtelDorisReaderFactory factory = new OtelDorisReaderFactory(
                roleService, variableService, new OtelCredentialService(variableService),
                noExternalDatasource());

        factory.create(7);

        assertThat(factory.poolSizeForTest()).isEqualTo(1);
    }

    @Test
    void rejectsClusterWithNoUsableDorisFe() {
        ClusterServiceRoleInstanceService roleService = mock(ClusterServiceRoleInstanceService.class);
        ClusterVariableService variableService = mock(ClusterVariableService.class);
        when(roleService.getServiceRoleInstanceListByClusterIdAndRoleName(7, "DorisFE"))
                .thenReturn(List.of(role("ddh-01", ServiceRoleState.STOP)));
        OtelDorisReaderFactory factory = new OtelDorisReaderFactory(
                roleService, variableService, new OtelCredentialService(variableService),
                noExternalDatasource());

        assertThatThrownBy(() -> factory.create(7))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No running DorisFE for cluster 7");
    }

    private static ClusterServiceRoleInstanceEntity role(String hostname, ServiceRoleState state) {
        ClusterServiceRoleInstanceEntity role = new ClusterServiceRoleInstanceEntity();
        role.setHostname(hostname);
        role.setServiceRoleState(state);
        return role;
    }

    @Test
    void reusesPoolForSameConnectionSettings() {
        OtelCredentialService credentialService = new OtelCredentialService(null);
        OtelDorisReaderFactory factory = new OtelDorisReaderFactory(
                proxy(ClusterServiceRoleInstanceService.class),
                proxy(ClusterVariableService.class),
                credentialService,
                noExternalDatasource());
        ReflectionTestUtils.setField(factory, "fallbackHost", "127.0.0.1");
        ReflectionTestUtils.setField(factory, "fallbackPort", "9030");
        ReflectionTestUtils.setField(factory, "fallbackPassword", "secret");

        factory.create(7);
        factory.create(7);

        assertThat(factory.poolSizeForTest()).isEqualTo(1);
    }

    @Test
    void usesConfiguredFallbackUser() {
        OtelCredentialService credentialService = new OtelCredentialService(null);
        OtelDorisReaderFactory factory = new OtelDorisReaderFactory(
                proxy(ClusterServiceRoleInstanceService.class),
                proxy(ClusterVariableService.class),
                credentialService,
                noExternalDatasource());
        ReflectionTestUtils.setField(factory, "fallbackHost", "127.0.0.1");
        ReflectionTestUtils.setField(factory, "fallbackPort", "9030");
        ReflectionTestUtils.setField(factory, "fallbackUser", "custom_reader");
        ReflectionTestUtils.setField(factory, "fallbackPassword", "secret");

        factory.create(7);

        assertThat(factory.poolSizeForTest()).isEqualTo(1);
        assertThat(factory.dataSourceForTest(7).getUsername()).isEqualTo("custom_reader");
    }

    @Test
    void usesExternalDatasourceWhenProviderResolvesOne() {
        ClusterServiceRoleInstanceService roleService = mock(ClusterServiceRoleInstanceService.class);
        ClusterVariableService variableService = mock(ClusterVariableService.class);
        OtelDorisReaderFactory factory = new OtelDorisReaderFactory(
                roleService, variableService, new OtelCredentialService(variableService),
                externalDatasource("10.0.0.9", "9030"));

        factory.create(7);

        assertThat(factory.poolSizeForTest()).isEqualTo(1);
        assertThat(factory.dataSourceForTest(7).getJdbcUrl()).contains("10.0.0.9:9030");
        // 走了外部数据源就不该再查角色实例表
        verify(roleService, never()).getServiceRoleInstanceListByClusterIdAndRoleName(anyInt(), anyString());
    }

    @Test
    void reusesResolvedDatasourceWithoutRepeatingPlatformQueries() {
        ClusterServiceRoleInstanceService roleService = mock(ClusterServiceRoleInstanceService.class);
        ClusterVariableService variableService = mock(ClusterVariableService.class);
        ExternalOtelDatasourceProvider provider = externalDatasource("10.0.0.9", "9030");
        OtelDorisReaderFactory factory = new OtelDorisReaderFactory(
                roleService, variableService, new OtelCredentialService(variableService), provider);

        factory.create(7);
        factory.create(7);

        verify(provider, times(1)).find(7);
    }

    @Test
    void fallsBackToRoleInstanceWhenNoExternalDatasource() {
        ClusterServiceRoleInstanceService roleService = mock(ClusterServiceRoleInstanceService.class);
        ClusterVariableService variableService = mock(ClusterVariableService.class);
        when(roleService.getServiceRoleInstanceListByClusterIdAndRoleName(7, "DorisFE"))
                .thenReturn(List.of(role("ddh-01", ServiceRoleState.RUNNING)));
        OtelDorisReaderFactory factory = new OtelDorisReaderFactory(
                roleService, variableService, new OtelCredentialService(variableService),
                noExternalDatasource());

        factory.create(7);

        assertThat(factory.dataSourceForTest(7).getJdbcUrl()).contains("ddh-01:9030");
    }

    @Test
    void devFallbackTakesPrecedenceOverExternalDatasource() {
        ClusterServiceRoleInstanceService roleService = mock(ClusterServiceRoleInstanceService.class);
        ClusterVariableService variableService = mock(ClusterVariableService.class);
        OtelDorisReaderFactory factory = new OtelDorisReaderFactory(
                roleService, variableService, new OtelCredentialService(variableService),
                externalDatasource("10.0.0.9", "9030"));
        ReflectionTestUtils.setField(factory, "fallbackHost", "127.0.0.1");
        ReflectionTestUtils.setField(factory, "fallbackPort", "9030");
        ReflectionTestUtils.setField(factory, "fallbackPassword", "secret");

        factory.create(7);

        assertThat(factory.dataSourceForTest(7).getJdbcUrl()).contains("127.0.0.1:9030");
    }

    @Test
    void replacesAndClosesPoolWhenConnectionSettingsChange() {
        OtelDorisReaderFactory factory = new OtelDorisReaderFactory(
                proxy(ClusterServiceRoleInstanceService.class),
                proxy(ClusterVariableService.class),
                new OtelCredentialService(null),
                noExternalDatasource());
        ReflectionTestUtils.setField(factory, "fallbackHost", "127.0.0.1");
        ReflectionTestUtils.setField(factory, "fallbackPort", "9030");
        ReflectionTestUtils.setField(factory, "fallbackPassword", "secret-1");
        factory.create(7);
        HikariDataSource old = factory.dataSourceForTest(7);

        ReflectionTestUtils.setField(factory, "fallbackPassword", "secret-2");
        factory.create(7);

        assertThat(factory.poolSizeForTest()).isEqualTo(1);
        assertThat(old.isClosed()).isTrue();
        assertThat(factory.dataSourceForTest(7)).isNotSameAs(old);
    }

    @Test
    void invalidationClosesAndRemovesClusterPool() {
        OtelDorisReaderFactory factory = new OtelDorisReaderFactory(
                proxy(ClusterServiceRoleInstanceService.class),
                proxy(ClusterVariableService.class),
                new OtelCredentialService(null),
                noExternalDatasource());
        ReflectionTestUtils.setField(factory, "fallbackHost", "127.0.0.1");
        ReflectionTestUtils.setField(factory, "fallbackPassword", "secret");
        factory.create(7);
        HikariDataSource old = factory.dataSourceForTest(7);

        factory.invalidate(7);

        assertThat(factory.poolSizeForTest()).isZero();
        assertThat(old.isClosed()).isTrue();
    }

    private static ExternalOtelDatasourceProvider noExternalDatasource() {
        ExternalOtelDatasourceProvider provider = mock(ExternalOtelDatasourceProvider.class);
        when(provider.find(anyInt())).thenReturn(Optional.empty());
        return provider;
    }

    private static ExternalOtelDatasourceProvider externalDatasource(String host, String port) {
        ExternalOtelDatasourceProvider provider = mock(ExternalOtelDatasourceProvider.class);
        when(provider.find(anyInt())).thenReturn(
                Optional.of(new ExternalOtelDatasourceProvider.ExternalDatasource(host, port)));
        return provider;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> null);
    }
}
