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

import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.service.ServiceInstallService;
import com.datasophon.api.utils.ServiceConfigUtils;
import com.datasophon.common.model.ServiceConfig;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

/** Loads DS configs with DDL fallback for service instances installed before a new parameter existed. */
@Service
public class DsConfigService {

    public static final String SERVICE_NAME = "DS";
    public static final String TOKEN_PARAM = "apiToken";

    private final ServiceInstallService serviceInstallService;

    public DsConfigService(ServiceInstallService serviceInstallService) {
        this.serviceInstallService = serviceInstallService;
    }

    public List<ServiceConfig> mergeDdlFallback(Integer clusterId, List<ServiceConfig> persisted) {
        List<ServiceConfig> effective = persisted == null ? new ArrayList<>() : new ArrayList<>(persisted);
        List<ServiceConfig> ddl = serviceInstallService.getServiceConfigFromDdl(clusterId, SERVICE_NAME);
        List<ServiceConfig> merged = ServiceConfigUtils.addAll(effective, ddl);
        if (merged.stream().noneMatch(config -> TOKEN_PARAM.equals(config.getName()))) {
            merged.add(apiTokenConfig());
        }
        return merged;
    }

    private static ServiceConfig apiTokenConfig() {
        ServiceConfig config = new ServiceConfig();
        config.setName(TOKEN_PARAM);
        config.setLabel("DS开放接口令牌");
        config.setDescription(
                "供 Datasophon 工作流 Tab 只读访问 DS Open API；建议使用专用只读账号签发的 token，不会下发到 DS 节点");
        config.setRequired(false);
        config.setConfigType("map");
        config.setType("input");
        config.setConfigurableInWizard(true);
        config.setHidden(false);
        config.setDefaultValue("");
        config.setValue("");
        config.setRegister(false);
        return config;
    }

    public String apiToken(Integer clusterId) {
        List<ServiceConfig> persisted = serviceInstallService.getServiceConfigOption(clusterId, SERVICE_NAME);
        return mergeDdlFallback(clusterId, persisted).stream()
                .filter(config -> TOKEN_PARAM.equals(config.getName()))
                .map(ServiceConfig::getValue)
                .filter(value -> value != null && StringUtils.isNotBlank(String.valueOf(value)))
                .map(String::valueOf)
                .findFirst()
                .orElseThrow(() -> new BusinessHintException("请在 DS 服务配置中填写 apiToken"));
    }
}
