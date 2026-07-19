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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.datasophon.api.observability.OtelCollectorConfigService;
import com.datasophon.api.service.ClusterAlertQuotaService;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterServiceRoleInstanceWebuisService;
import com.datasophon.api.service.cmd.ClusterServiceCommandHostCommandService;
import com.datasophon.api.service.cmd.ClusterServiceCommandHostService;
import com.datasophon.api.service.cmd.ClusterServiceCommandService;
import com.datasophon.common.enums.CommandType;
import com.datasophon.dao.entity.cmd.ClusterServiceCommandEntity;
import com.datasophon.dao.entity.cmd.ClusterServiceCommandHostCommandEntity;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ServiceCommandServiceTest {

    private final ClusterServiceCommandHostCommandService hostCommandService =
            mock(ClusterServiceCommandHostCommandService.class);
    private final OtelCollectorConfigService collectorConfigService = mock(OtelCollectorConfigService.class);
    private final ServiceCommandService service = new ServiceCommandService(
            hostCommandService,
            mock(ClusterServiceCommandHostService.class),
            mock(ClusterServiceCommandService.class),
            mock(ClusterInfoService.class),
            mock(ClusterAlertQuotaService.class),
            mock(ClusterServiceRoleInstanceWebuisService.class),
            mock(HdfsECService.class),
            collectorConfigService);

    @Test
    void refreshesCollectorsAfterInstallAndUpgrade() {
        assertThat(shouldRefresh(CommandType.INSTALL_SERVICE)).isTrue();
        assertThat(shouldRefresh(CommandType.UPGRADE_SERVICE)).isTrue();
        assertThat(shouldRefresh(CommandType.CHECK_STATUS)).isFalse();
    }

    @Test
    void refreshesEachAffectedHostOnce() {
        ClusterServiceCommandEntity command = new ClusterServiceCommandEntity();
        command.setCommandId("101");
        command.setClusterId(7);
        when(hostCommandService.getHostCommandListByCommandId("101"))
                .thenReturn(List.of(hostCommand("worker-1"), hostCommand("worker-1"), hostCommand("worker-2")));

        ReflectionTestUtils.invokeMethod(service, "refreshAffectedOtelCollectors", command);

        verify(collectorConfigService, times(1)).pushNodeConfig(7, "worker-1", Map.of());
        verify(collectorConfigService, times(1)).pushNodeConfig(7, "worker-2", Map.of());
    }

    private boolean shouldRefresh(CommandType commandType) {
        return Boolean.TRUE.equals(ReflectionTestUtils.invokeMethod(
                service, "shouldRefreshOtelCollectors", commandType.getValue()));
    }

    private static ClusterServiceCommandHostCommandEntity hostCommand(String hostname) {
        ClusterServiceCommandHostCommandEntity command = new ClusterServiceCommandHostCommandEntity();
        command.setHostname(hostname);
        return command;
    }
}
