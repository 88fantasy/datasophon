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
import com.datasophon.api.dto.v2.HostPageResponse;
import com.datasophon.api.dto.v2.HostResponse;
import com.datasophon.api.dto.v2.HostRoleResponse;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.cluster.K8sClusterConfigService;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.api.service.host.dto.QueryHostListPageDTO;
import com.datasophon.api.service.k8s.K8sService;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.entity.cluster.K8sClusterConfig;
import com.datasophon.dao.enums.ClusterArchType;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * v2 主机管理接口。
 *
 * <p>复用现有 ClusterHostService，返回 ApiResponse 标准信封。
 * 安全说明：所有响应均使用 DTO，不直接暴露实体，避免 sshPassword 等敏感字段泄漏。
 */
@Slf4j
@RestController
@RequestMapping("/v2/cluster/{clusterId}/host")
public class ClusterHostV2Controller extends ApiController {

    private final ClusterHostService clusterHostService;

    private final ClusterInfoService clusterInfoService;

    private final K8sClusterConfigService k8sClusterConfigService;

    private final K8sService k8sService;

    @Autowired
    public ClusterHostV2Controller(ClusterHostService clusterHostService,
                                   ClusterInfoService clusterInfoService,
                                   K8sClusterConfigService k8sClusterConfigService,
                                   K8sService k8sService) {
        this.clusterHostService = clusterHostService;
        this.clusterInfoService = clusterInfoService;
        this.k8sClusterConfigService = k8sClusterConfigService;
        this.k8sService = k8sService;
    }

    // ─── 分页主机列表 ─────────────────────────────────────────────

    @GetMapping("/list")
    public ApiResponse<HostPageResponse> list(
                                              @PathVariable Integer clusterId,
                                              @RequestParam(defaultValue = "1") Integer page,
                                              @RequestParam(defaultValue = "20") Integer pageSize,
                                              @RequestParam(required = false) String hostname,
                                              @RequestParam(required = false) String ip,
                                              @RequestParam(required = false) String cpuArchitecture,
                                              @RequestParam(required = false) Integer hostState,
                                              @RequestParam(required = false) String sortField,
                                              @RequestParam(required = false) String sortOrder) {
        ClusterInfoEntity cluster = clusterInfoService.getById(clusterId);
        if (cluster != null && ClusterArchType.k8s.equals(cluster.getArchType())) {
            return ApiResponse.ok(listK8sNodes(clusterId, page, pageSize, hostname, ip, cpuArchitecture, hostState));
        }

        Result result = clusterHostService.listByPage(clusterId, hostname, ip, cpuArchitecture,
                hostState, sortField, sortOrder, page, pageSize);
        @SuppressWarnings("unchecked")
        List<QueryHostListPageDTO> pageList = (List<QueryHostListPageDTO>) result.getData();
        long total = ((Number) result.get(Constants.TOTAL)).longValue();
        List<HostResponse> records = HostResponse.fromPageDtoList(pageList);
        return ApiResponse.ok(HostPageResponse.of(records, total));
    }

    private HostPageResponse listK8sNodes(Integer clusterId,
                                          Integer page,
                                          Integer pageSize,
                                          String hostname,
                                          String ip,
                                          String cpuArchitecture,
                                          Integer hostState) {
        K8sClusterConfig config = k8sClusterConfigService.getInitConfig(clusterId);
        List<HostResponse> all = k8sService.listNodes(config).stream()
                .map(node -> HostResponse.fromK8sNode(clusterId, node))
                .filter(host -> contains(host.getHostname(), hostname))
                .filter(host -> contains(host.getIp(), ip))
                .filter(host -> cpuArchitecture == null || cpuArchitecture.equals(host.getCpuArchitecture()))
                .filter(host -> hostState == null || hostState.equals(host.getHostState()))
                .collect(Collectors.toCollection(ArrayList::new));
        int total = all.size();
        int safePage = page == null || page < 1 ? 1 : page;
        int safePageSize = pageSize == null || pageSize < 1 ? 20 : pageSize;
        int from = Math.min((safePage - 1) * safePageSize, total);
        int to = Math.min(from + safePageSize, total);
        return HostPageResponse.of(all.subList(from, to), total);
    }

    private static boolean contains(String value, String keyword) {
        return keyword == null || keyword.isBlank() || (value != null && value.contains(keyword));
    }

    // ─── 主机详情 ────────────────────────────────────────────────

    @GetMapping("/{hostId}")
    public ApiResponse<HostResponse> info(@PathVariable Integer hostId) {
        ClusterHostDO host = clusterHostService.getById(hostId);
        return ApiResponse.ok(HostResponse.from(host));
    }

    // ─── 批量删除主机 ────────────────────────────────────────────

    @DeleteMapping
    public ApiResponse<Void> delete(
                                    @PathVariable Integer clusterId,
                                    @RequestBody List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return ApiResponse.fail(400, "请选择要移除的主机");
        }
        String hostIds = ids.stream().map(Object::toString).collect(Collectors.joining(","));
        Result result = clusterHostService.deleteHosts(hostIds);
        if (result.isSuccess()) {
            return ApiResponse.ok();
        }
        return ApiResponse.fail(500, String.valueOf(result.getMsg()));
    }

    // ─── 按主机名查角色列表 ───────────────────────────────────────

    @GetMapping("/roles")
    public ApiResponse<List<HostRoleResponse>> getRoleListByHostname(
                                                                     @PathVariable Integer clusterId,
                                                                     @RequestParam String hostname) {
        Result result = clusterHostService.getRoleListByHostname(clusterId, hostname);
        if (!result.isSuccess()) {
            return ApiResponse.fail(500, String.valueOf(result.getMsg()));
        }
        @SuppressWarnings("unchecked")
        List<ClusterServiceRoleInstanceEntity> roleList =
                (List<ClusterServiceRoleInstanceEntity>) result.getData();
        return ApiResponse.ok(HostRoleResponse.fromList(roleList));
    }

    // ─── 分配机架 ────────────────────────────────────────────────

    @Data
    public static class AssignRackRequest {
        private String rack;
        private List<Integer> hostIds;
    }

    @PostMapping("/assign-rack")
    public ApiResponse<Void> assignRack(
                                        @PathVariable Integer clusterId,
                                        @RequestBody AssignRackRequest request) {
        if (request.getHostIds() == null || request.getHostIds().isEmpty()) {
            return ApiResponse.fail(400, "请选择要分配机架的主机");
        }
        String hostIds = request.getHostIds().stream()
                .map(Object::toString)
                .collect(Collectors.joining(","));
        Result result = clusterHostService.assignRack(clusterId, request.getRack(), hostIds);
        return result.isSuccess()
                ? ApiResponse.ok()
                : ApiResponse.fail(500, String.valueOf(result.getMsg()));
    }

    // ─── 机架列表 ────────────────────────────────────────────────

    @GetMapping("/rack")
    public ApiResponse<List<String>> getRack(@PathVariable Integer clusterId) {
        Result result = clusterHostService.getRack(clusterId);
        if (!result.isSuccess()) {
            return ApiResponse.fail(500, String.valueOf(result.getMsg()));
        }
        @SuppressWarnings("unchecked")
        List<String> racks = (List<String>) result.getData();
        return ApiResponse.ok(racks);
    }
}
