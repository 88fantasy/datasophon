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

package com.datasophon.api.ds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.datasophon.api.service.ServiceInstallService;
import com.datasophon.common.model.ServiceConfig;

import java.util.List;

import org.junit.jupiter.api.Test;

class DsConfigServiceTest {

    @Test
    void backfillsApiTokenForInstanceInstalledBeforeParameterExisted() {
        ServiceInstallService installService = mock(ServiceInstallService.class);
        DsConfigService configService = new DsConfigService(installService);
        when(installService.getServiceConfigFromDdl(7, "DS"))
                .thenReturn(List.of(config("apiToken", ""), config("apiServerPort", "12345")));

        List<ServiceConfig> effective = configService.mergeDdlFallback(
                7, List.of(config("apiServerPort", "12346")));

        assertThat(effective).extracting(ServiceConfig::getName, ServiceConfig::getValue)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("apiServerPort", "12346"),
                        org.assertj.core.groups.Tuple.tuple("apiToken", ""));
    }

    @Test
    void backfillsApiTokenWhenStoredDdlPredatesParameter() {
        ServiceInstallService installService = mock(ServiceInstallService.class);
        DsConfigService configService = new DsConfigService(installService);
        when(installService.getServiceConfigFromDdl(7, "DS"))
                .thenReturn(List.of(config("apiServerPort", "12345")));

        List<ServiceConfig> effective = configService.mergeDdlFallback(
                7, List.of(config("apiServerPort", "12346")));

        assertThat(effective).extracting(ServiceConfig::getName, ServiceConfig::getValue)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("apiServerPort", "12346"),
                        org.assertj.core.groups.Tuple.tuple("apiToken", ""));
        ServiceConfig apiToken = effective.get(1);
        assertThat(apiToken.getDefaultValue()).isEqualTo("");
        assertThat(apiToken.isRequired()).isFalse();
        assertThat(apiToken.getRegister()).isFalse();
        assertThat(apiToken.isConfigurableInWizard()).isTrue();
    }

    private static ServiceConfig config(String name, String value) {
        ServiceConfig config = new ServiceConfig();
        config.setName(name);
        config.setValue(value);
        return config;
    }
}
