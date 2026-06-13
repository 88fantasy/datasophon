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
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterHostDO;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * v2 主机管理接口。
 *
 * <p>复用现有 ClusterHostService，返回 ApiResponse 标准信封。
 */
@Slf4j
@RestController
@RequestMapping("/v2/cluster/{clusterId}/host")
public class ClusterHostV2Controller extends ApiController {
    
    @Autowired
    private ClusterHostService clusterHostService;
    
    // ─── 分页主机列表 ─────────────────────────────────────────────
    
    @GetMapping("/list")
    public ApiResponse<Map<String, Object>> list(
                                                 @PathVariable Integer clusterId,
                                                 @RequestParam(defaultValue = "1") Integer page,
                                                 @RequestParam(defaultValue = "20") Integer pageSize,
                                                 @RequestParam(required = false) String hostname,
                                                 @RequestParam(required = false) String ip,
                                                 @RequestParam(required = false) String cpuArchitecture,
                                                 @RequestParam(required = false) Integer hostState,
                                                 @RequestParam(required = false) String sortField,
                                                 @RequestParam(required = false) String sortOrder) {
        Result result = clusterHostService.listByPage(clusterId, hostname, ip, cpuArchitecture,
                hostState, sortField, sortOrder, page, pageSize);
        Map<String, Object> pageData = new HashMap<>();
        pageData.put("records", result.getData());
        pageData.put("total", result.get(Constants.TOTAL));
        return ApiResponse.ok(pageData);
    }
    
    // ─── 主机详情 ────────────────────────────────────────────────
    
    @GetMapping("/{hostId}")
    public ApiResponse<ClusterHostDO> info(@PathVariable Integer hostId) {
        ClusterHostDO host = clusterHostService.getById(hostId);
        return ApiResponse.ok(host);
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
    public ApiResponse<Object> getRoleListByHostname(
                                                     @PathVariable Integer clusterId,
                                                     @RequestParam String hostname) {
        Result result = clusterHostService.getRoleListByHostname(clusterId, hostname);
        return result.isSuccess()
                ? ApiResponse.ok(result.getData())
                : ApiResponse.fail(500, String.valueOf(result.getMsg()));
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
    public ApiResponse<Object> getRack(@PathVariable Integer clusterId) {
        Result result = clusterHostService.getRack(clusterId);
        return result.isSuccess()
                ? ApiResponse.ok(result.getData())
                : ApiResponse.fail(500, String.valueOf(result.getMsg()));
    }
}
