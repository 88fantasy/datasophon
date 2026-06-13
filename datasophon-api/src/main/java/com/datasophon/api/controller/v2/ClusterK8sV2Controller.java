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
import com.datasophon.api.dto.instance.K8sNamespaceIdentityDTO;
import com.datasophon.api.dto.instance.K8sServiceInstanceQueryDTO;
import com.datasophon.api.service.cluster.K8sClusterNamespaceService;
import com.datasophon.api.service.instance.K8sServiceInstanceService;
import com.datasophon.dao.entity.cluster.K8sClusterNamespace;
import com.datasophon.dao.vo.instance.K8sServiceInstanceVO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * v2 K8s 集群链路接口：namespace 列表 / 实例列表 / 资源类型 / 资源列表。
 *
 * <p>全部业务逻辑委托给现有 Service，本 Controller 只做薄封装，返回 {@link ApiResponse} 信封。
 */
@Slf4j
@RestController
@RequestMapping("/v2/cluster/{clusterId}/k8s")
@Tag(name = "v2 K8s 集群服务链路")
public class ClusterK8sV2Controller extends ApiController {
    
    @Autowired
    private K8sClusterNamespaceService k8sClusterNamespaceService;
    
    @Autowired
    private K8sServiceInstanceService k8sServiceInstanceService;
    
    /**
     * 获取 K8s 集群下的 namespace 列表（同时触发与 K8s 集群的对账更新）。
     *
     * @param clusterId 集群 ID
     */
    @GetMapping("/namespace/list")
    @Operation(summary = "获取 K8s 集群 namespace 列表")
    public ApiResponse<List<K8sClusterNamespace>> listNamespaces(@PathVariable Integer clusterId) {
        return ApiResponse.ok(k8sClusterNamespaceService.listAndUpdateNamespaceByClusterId(clusterId));
    }
    
    /**
     * 获取指定 namespace 下的服务实例列表。
     *
     * @param clusterId 集群 ID
     * @param namespace namespace 名称
     */
    @GetMapping("/namespace/{namespace}/instance/list")
    @Operation(summary = "获取 namespace 下的服务实例列表")
    public ApiResponse<List<K8sServiceInstanceVO>> listInstances(@PathVariable Integer clusterId,
                                                                 @PathVariable String namespace) {
        return ApiResponse.ok(
                k8sServiceInstanceService.queryInstanceList(
                        new K8sNamespaceIdentityDTO(clusterId, namespace)));
    }
    
    /**
     * 获取服务实例支持的资源类型列表（Pod / Service / Deployment / Ingress / ConfigMap 等）。
     *
     * @param instanceId 实例 ID
     */
    @GetMapping("/instance/{instanceId}/resource-types")
    @Operation(summary = "获取实例的资源类型列表")
    public ApiResponse<List<String>> listResourceTypes(@PathVariable Integer instanceId) {
        K8sServiceInstanceQueryDTO query = new K8sServiceInstanceQueryDTO();
        query.setInstanceId(instanceId);
        return ApiResponse.ok(k8sServiceInstanceService.listResourceType(query));
    }
    
    /**
     * 获取服务实例指定资源类型的资源列表。
     *
     * @param instanceId   实例 ID
     * @param resourceType 资源类型（Pod / Service / Deployment / Ingress / ConfigMap）
     */
    @GetMapping("/instance/{instanceId}/resource")
    @Operation(summary = "获取实例指定类型的资源列表")
    public ApiResponse<List<?>> listResources(@PathVariable Integer instanceId,
                                              @RequestParam String resourceType) {
        K8sServiceInstanceQueryDTO query = new K8sServiceInstanceQueryDTO();
        query.setInstanceId(instanceId);
        query.setResourceType(resourceType);
        List<?> result = (List<?>) k8sServiceInstanceService.listResource(query);
        return ApiResponse.ok(result);
    }
}
