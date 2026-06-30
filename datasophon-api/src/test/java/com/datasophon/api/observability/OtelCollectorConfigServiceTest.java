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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.api.master.transport.WorkerCallAdapter;
import com.datasophon.api.service.ServiceInstallService;
import com.datasophon.common.command.GenerateServiceConfigCommand;
import com.datasophon.common.command.ServiceRoleOperateCommand;
import com.datasophon.common.model.Generators;
import com.datasophon.common.utils.ExecResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class OtelCollectorConfigServiceTest {

    @Test
    void builds_command_with_two_generators() {
        Map<String, String> params = new HashMap<>();
        params.put("s3Endpoint", "http://mw1:9040");
        GenerateServiceConfigCommand cmd =
                service(null).buildConfigCommand(1, "app1", params);

        assertEquals("OTELCOLLECTOR", cmd.getServiceName());
        assertEquals("OtelCollector", cmd.getServiceRoleName());
        assertEquals(Integer.valueOf(1), cmd.getClusterId());

        // 双 generator 不得塌缩:Generators.equals 以 filename 为键，碰撞会让两条塌成一条
        assertEquals(2, cmd.getCofigFileMap().size(), "应有 2 个独立 generator(otelcol.yaml + otelcol.env)");
        Set<String> files = cmd.getCofigFileMap().keySet().stream()
                .map(Generators::getFilename)
                .collect(Collectors.toSet());
        assertTrue(files.contains("otelcol.yaml"), "缺 otelcol.yaml generator");
        assertTrue(files.contains("otelcol.env"), "缺 otelcol.env generator(凭据)");
    }

    @Test
    void push_configures_then_restarts_in_order() {
        WorkerCallAdapter adapter = mock(WorkerCallAdapter.class);
        when(adapter.configureServiceRole(eq("app1"), any())).thenReturn(ok());
        when(adapter.restartServiceRole(eq("app1"), any())).thenReturn(ok());

        OtelCollectorConfigService svc = service(adapter);
        ExecResult r = svc.pushNodeConfig(1, "app1", new HashMap<>());

        assertTrue(r.getExecResult());
        InOrder o = inOrder(adapter);
        o.verify(adapter).configureServiceRole(eq("app1"), any(GenerateServiceConfigCommand.class));
        o.verify(adapter).restartServiceRole(eq("app1"), any(ServiceRoleOperateCommand.class));
    }

    @Test
    void push_does_not_restart_when_configure_fails() {
        WorkerCallAdapter adapter = mock(WorkerCallAdapter.class);
        when(adapter.configureServiceRole(eq("app1"), any())).thenReturn(fail());

        OtelCollectorConfigService svc = service(adapter);
        ExecResult r = svc.pushNodeConfig(1, "app1", new HashMap<>());

        assertFalse(r.getExecResult());
        verify(adapter, never()).restartServiceRole(any(), any());
    }

    private static ExecResult ok() {
        ExecResult e = new ExecResult();
        e.setExecResult(true);
        return e;
    }

    private static ExecResult fail() {
        ExecResult e = new ExecResult();
        e.setExecResult(false);
        return e;
    }

    private static OtelCollectorConfigService service(WorkerCallAdapter adapter) {
        ServiceInstallService installService = mock(ServiceInstallService.class);
        when(installService.getServiceConfigOption(any(), any())).thenReturn(List.of());
        OtelScrapeConfigBuilder builder = mock(OtelScrapeConfigBuilder.class);
        when(builder.build(any(), any())).thenReturn("");
        return new OtelCollectorConfigService(adapter, installService, builder);
    }
}
