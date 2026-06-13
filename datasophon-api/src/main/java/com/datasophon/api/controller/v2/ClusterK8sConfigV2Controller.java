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
import com.datasophon.api.dto.instance.K8sServiceInstanceValuesUpdateDTO;
import com.datasophon.api.service.instance.K8sServiceInstanceValuesService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * v2 K8s Helm 配置接口：values 版本列表 / 读取单条 / 保存 deltaValues。
 *
 * <p>K8s 配置保存语义：仅更新 deltaValues，不递增版本号，不打 needRestart 标记。
 * 与物理集群配置（{@link ClusterServiceConfigV2Controller}）的版本管理不同。
 */
@Slf4j
@RestController
@RequestMapping("/v2/cluster/{clusterId}/k8s/instance/{instanceId}/config")
@Tag(name = "v2 K8s Helm 配置")
public class ClusterK8sConfigV2Controller extends ApiController {
    
    @Autowired
    private K8sServiceInstanceValuesService k8sServiceInstanceValuesService;
    
    /**
     * Helm values 历史版本列表（仅含 id / version，降序）。
     *
     * @param instanceId 实例 ID
     */
    @GetMapping("/versions")
    @Operation(summary = "获取 Helm values 版本列表")
    public ApiResponse<Object> versions(@PathVariable Integer instanceId) {
        return ApiResponse.ok(k8sServiceInstanceValuesService.listSimpleByInstanceId(instanceId));
    }
    
    /**
     * 按 valueId 读取完整 Helm values（含 values / deltaValues / metaFileType）。
     *
     * @param valueId values 记录 ID
     */
    @GetMapping("/{valueId}")
    @Operation(summary = "读取指定版本的 Helm values")
    public ApiResponse<Object> info(@PathVariable Integer valueId) {
        return ApiResponse.ok(k8sServiceInstanceValuesService.getById(valueId));
    }
    
    /**
     * 保存用户编辑的 deltaValues（仅更新当前版本，不升版、不打 needRestart）。
     *
     * @param req 包含 id + deltaValues
     */
    @PostMapping
    @Operation(summary = "保存 Helm deltaValues")
    public ApiResponse<Void> save(@RequestBody K8sServiceInstanceValuesUpdateDTO req) {
        k8sServiceInstanceValuesService.update(req);
        return ApiResponse.ok();
    }
}
