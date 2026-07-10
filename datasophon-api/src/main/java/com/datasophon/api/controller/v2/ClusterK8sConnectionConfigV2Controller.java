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
import com.datasophon.api.security.UserPermission;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.api.service.k8s.K8sService;
import com.datasophon.api.vo.k8s.K8sConnectionResult;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/**
 * v2 K8s 集群连接配置接口。
 *
 * <p>完整路径示例（context-path=/ddh，/api 前缀由 configurePathMatch 注入）：
 * <ul>
 *   <li>GET  {@code /ddh/api/v2/cluster/k8sConfig/getConfigByClusterId/{clusterId}}</li>
 *   <li>POST {@code /ddh/api/v2/cluster/k8sConfig/testConnection}</li>
 *   <li>POST {@code /ddh/api/v2/cluster/k8sConfig/saveOrUpdateConfig}</li>
 * </ul>
 */
@RestController
@RequestMapping("/v2/cluster/k8sConfig")
@Tag(name = "v2 K8s 集群连接配置")
public class ClusterK8sConnectionConfigV2Controller extends ApiController {

    private final K8sService k8sService;

    private final K8sClusterConfigService k8sClusterConfigService;

    public ClusterK8sConnectionConfigV2Controller(K8sService k8sService,
                                                  K8sClusterConfigService k8sClusterConfigService) {
        this.k8sService = k8sService;
        this.k8sClusterConfigService = k8sClusterConfigService;
    }

    @GetMapping("/getConfigByClusterId/{clusterId}")
    @Operation(summary = "根据集群 id 获取 K8s 连接配置")
    @UserPermission
    public ApiResponse<K8sClusterConfig> getConfigByClusterId(@PathVariable Integer clusterId) {
        return ApiResponse.ok(k8sClusterConfigService.getByClusterId(clusterId));
    }

    @PostMapping("/testConnection")
    @Operation(summary = "测试 K8s 集群连通性")
    public ApiResponse<K8sConnectionResult> testConnection(@RequestBody @Valid K8sClusterConfig config) {
        return ApiResponse.ok(k8sService.testConnection(config));
    }

    @PostMapping("/saveOrUpdateConfig")
    @Operation(summary = "新增或修改 K8s 集群连接配置")
    @UserPermission
    public ApiResponse<K8sClusterConfig> saveOrUpdateConfig(@RequestBody @Valid K8sClusterConfig config) {
        return ApiResponse.ok(k8sClusterConfigService.saveOrUpdateConfig(config));
    }
}
