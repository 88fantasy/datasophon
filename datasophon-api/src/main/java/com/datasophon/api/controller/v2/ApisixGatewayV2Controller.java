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

import com.datasophon.api.controller.ApiController;
import com.datasophon.api.dto.ApiResponse;
import com.datasophon.api.dto.v2.ApisixGatewayPushResult;
import com.datasophon.api.dto.v2.ApisixGatewayResponse;
import com.datasophon.api.dto.v2.SaveApisixGatewayRequest;
import com.datasophon.api.service.ApisixGatewayConfigService;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * APISIX(standalone) 网关配置 Tab 的读写接口。
 *
 * <p>真相源是隐藏参数 {@code apisixGatewayYaml}，图形化视图只是其 {@code js-yaml.load()}
 * 后的结构化投影；全部业务逻辑委托给 {@link ApisixGatewayConfigService}。
 */
@RestController
@RequestMapping("/v2/cluster/{clusterId}/service/instance/{instanceId}/apisix/gateway")
public class ApisixGatewayV2Controller extends ApiController {

    private final ApisixGatewayConfigService gatewayConfigService;

    public ApisixGatewayV2Controller(ApisixGatewayConfigService gatewayConfigService) {
        this.gatewayConfigService = gatewayConfigService;
    }

    @GetMapping
    public ApiResponse<ApisixGatewayResponse> get(@PathVariable Integer clusterId,
                                                  @PathVariable Integer instanceId) {
        return ApiResponse.ok(gatewayConfigService.getGatewayConfig(clusterId, instanceId));
    }

    /**
     * 保存网关配置：校验 YAML → 复用 saveServiceConfig 落库 → 只对 apisix.yaml 下发（不重启）。
     */
    @PostMapping
    public ApiResponse<List<ApisixGatewayPushResult>> save(@PathVariable Integer clusterId,
                                                           @PathVariable Integer instanceId,
                                                           @Valid @RequestBody SaveApisixGatewayRequest req) {
        return ApiResponse.ok(gatewayConfigService.saveGatewayConfig(clusterId, instanceId, req.getGatewayYaml()));
    }
}
