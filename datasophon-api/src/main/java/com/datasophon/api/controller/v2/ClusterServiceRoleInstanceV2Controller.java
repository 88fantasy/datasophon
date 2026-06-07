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
import com.datasophon.api.service.ClusterServiceInstanceRoleGroupService;
import com.datasophon.api.service.ClusterServiceInstanceService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.ClusterServiceRoleInstanceWebuisService;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterServiceInstanceRoleGroup;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * v2 服务角色实例管理接口。
 */
@Slf4j
@RestController
@RequestMapping("/v2/cluster/{clusterId}/service/instance/{instanceId}/role")
public class ClusterServiceRoleInstanceV2Controller extends ApiController {
    
    @Autowired
    private ClusterServiceRoleInstanceService clusterServiceRoleInstanceService;
    
    @Autowired
    private ClusterServiceInstanceService clusterServiceInstanceService;
    
    @Autowired
    private ClusterServiceInstanceRoleGroupService roleGroupService;
    
    @Autowired
    private ClusterServiceRoleInstanceWebuisService webuisService;
    
    /**
     * 服务角色实例列表（分页）。
     */
    @GetMapping("/list")
    public ApiResponse<Result> list(@PathVariable Integer instanceId,
                                    @RequestParam(defaultValue = "1") Integer page,
                                    @RequestParam(defaultValue = "20") Integer pageSize,
                                    @RequestParam(required = false) String hostname,
                                    @RequestParam(required = false) Integer serviceRoleState,
                                    @RequestParam(required = false) String serviceRoleName,
                                    @RequestParam(required = false) Integer roleGroupId) {
        Result result = clusterServiceRoleInstanceService.listAll(
                instanceId, hostname, serviceRoleState, serviceRoleName, roleGroupId, page, pageSize);
        return ApiResponse.ok(result);
    }
    
    /**
     * 服务角色类型列表（供筛选）。
     */
    @GetMapping("/type-list")
    public ApiResponse<Result> getServiceRoleType(@PathVariable Integer instanceId) {
        Result result = clusterServiceInstanceService.getServiceRoleType(instanceId);
        return ApiResponse.ok(result);
    }
    
    /**
     * 服务角色组列表（供筛选）。
     */
    @GetMapping("/group-list")
    public ApiResponse<List<ClusterServiceInstanceRoleGroup>> getRoleGroupList(
                                                                               @PathVariable Integer instanceId) {
        List<ClusterServiceInstanceRoleGroup> list =
                roleGroupService.listRoleGroupByServiceInstanceId(instanceId);
        return ApiResponse.ok(list);
    }
    
    /**
     * 服务 WebUI 列表。
     */
    @GetMapping("/webuis")
    public ApiResponse<Result> getWebUis(@PathVariable Integer instanceId) {
        Result result = webuisService.getWebUis(instanceId);
        return ApiResponse.ok(result);
    }
}
