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
import com.datasophon.api.dto.v2.RoleGroupResponse;
import com.datasophon.api.dto.v2.ServiceRoleInstancePageResponse;
import com.datasophon.api.dto.v2.ServiceRoleInstanceResponse;
import com.datasophon.api.service.ClusterServiceInstanceRoleGroupService;
import com.datasophon.api.service.ClusterServiceInstanceService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.ClusterServiceRoleInstanceWebuisService;
import com.datasophon.api.service.extrepo.PhysicalProductInstallService;
import com.datasophon.common.Constants;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.utils.ConverterUtils;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterServiceInstanceRoleGroup;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.entity.FrameServiceRoleEntity;
import com.datasophon.dao.model.WebuisVO;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import cn.hutool.core.util.EnumUtil;

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
    
    @Autowired
    private PhysicalProductInstallService physicalProductInstallService;
    
    /**
     * 服务角色实例列表（分页）。
     *
     * <p>返回 {@code { data: [...], total: N }}，供前端 ProTable request 回调按
     * {@code res.data.data} 与 {@code res.data.total} 读取。
     */
    @GetMapping("/list")
    public ApiResponse<ServiceRoleInstancePageResponse> list(
                                                             @PathVariable Integer clusterId,
                                                             @PathVariable Integer instanceId,
                                                             @RequestParam(defaultValue = "1") Integer page,
                                                             @RequestParam(defaultValue = "20") Integer pageSize,
                                                             @RequestParam(required = false) String hostname,
                                                             @RequestParam(required = false) Integer serviceRoleState,
                                                             @RequestParam(required = false) String serviceRoleName,
                                                             @RequestParam(required = false) Integer roleGroupId) {
        Result result = clusterServiceRoleInstanceService.listAll(
                instanceId, hostname, serviceRoleState, serviceRoleName, roleGroupId, page, pageSize);
        @SuppressWarnings("unchecked")
        List<ClusterServiceRoleInstanceEntity> entities =
                (List<ClusterServiceRoleInstanceEntity>) result.getData();
        Long total = result.get(Constants.TOTAL) != null
                ? ((Number) result.get(Constants.TOTAL)).longValue()
                : 0L;
        List<ServiceRoleInstanceResponse> records = ServiceRoleInstanceResponse.fromList(
                entities != null ? entities : List.of());
        return ApiResponse.ok(ServiceRoleInstancePageResponse.of(records, total));
    }
    
    /**
     * 服务角色类型列表（供筛选）。只投影 id / serviceRoleName 两个字段，不泄漏实体全量信息。
     */
    @GetMapping("/type-list")
    public ApiResponse<List<Map<String, Object>>> getServiceRoleType(
                                                                     @PathVariable Integer clusterId,
                                                                     @PathVariable Integer instanceId) {
        Result result = clusterServiceInstanceService.getServiceRoleType(instanceId);
        @SuppressWarnings("unchecked")
        List<FrameServiceRoleEntity> entities = (List<FrameServiceRoleEntity>) result.getData();
        List<Map<String, Object>> projected = entities == null
                ? List.of()
                : entities.stream().map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", e.getId());
                    m.put("serviceRoleName", e.getServiceRoleName());
                    return m;
                }).toList();
        return ApiResponse.ok(projected);
    }
    
    /**
     * 服务角色组列表（供筛选）。
     */
    @GetMapping("/group-list")
    public ApiResponse<List<RoleGroupResponse>> getRoleGroupList(
                                                                 @PathVariable Integer clusterId,
                                                                 @PathVariable Integer instanceId) {
        List<ClusterServiceInstanceRoleGroup> list =
                roleGroupService.listRoleGroupByServiceInstanceId(instanceId);
        return ApiResponse.ok(RoleGroupResponse.fromList(list));
    }
    
    /**
     * 服务 WebUI 列表。
     */
    @GetMapping("/webuis")
    public ApiResponse<List<WebuisVO>> getWebUis(@PathVariable Integer clusterId,
                                                 @PathVariable Integer instanceId) {
        Result result = webuisService.getWebUis(instanceId);
        @SuppressWarnings("unchecked")
        List<WebuisVO> list = (List<WebuisVO>) result.getData();
        return ApiResponse.ok(list != null ? list : List.of());
    }
    
    /**
     * 批量操作角色实例（启动/停止/重启）。
     */
    @PostMapping("/command")
    public ApiResponse<String> command(@PathVariable Integer clusterId,
                                       @PathVariable Integer instanceId,
                                       @RequestParam String commandType,
                                       @RequestParam String serviceRoleInstancesIds) {
        List<Integer> ids = ConverterUtils.convertIds(serviceRoleInstancesIds, Integer::parseInt);
        CommandType command = EnumUtil.fromString(CommandType.class, commandType);
        String result = physicalProductInstallService.generateAndExecSrvRoleCmd(clusterId, command, instanceId, ids);
        return ApiResponse.ok(result);
    }
    
    /**
     * 删除角色实例。
     */
    @PostMapping("/delete")
    public ApiResponse<Void> delete(@PathVariable Integer clusterId,
                                    @PathVariable Integer instanceId,
                                    @RequestParam String serviceRoleInstancesIds) {
        List<String> idList = Arrays.asList(serviceRoleInstancesIds.split(","));
        clusterServiceRoleInstanceService.deleteServiceRole(idList);
        return ApiResponse.ok();
    }
    
    /**
     * 查看角色实例日志。
     */
    @GetMapping("/{roleInstanceId}/log")
    public ApiResponse<String> getLog(@PathVariable Integer clusterId,
                                      @PathVariable Integer instanceId,
                                      @PathVariable Integer roleInstanceId) throws Exception {
        Result result = clusterServiceRoleInstanceService.getLog(roleInstanceId);
        Object data = result.getData();
        return ApiResponse.ok(data != null ? data.toString() : "");
    }
    
    /**
     * 添加角色组。
     */
    @PostMapping("/group/save")
    public ApiResponse<Void> saveRoleGroup(@PathVariable Integer clusterId,
                                           @PathVariable Integer instanceId,
                                           @RequestParam(required = false) Integer roleGroupId,
                                           @RequestParam String roleGroupName) {
        roleGroupService.saveRoleGroup(instanceId, roleGroupId, roleGroupName);
        return ApiResponse.ok();
    }
    
    /**
     * 批量分配角色组。
     */
    @PostMapping("/group/bind")
    public ApiResponse<Void> bindRoleGroup(@PathVariable Integer clusterId,
                                           @PathVariable Integer instanceId,
                                           @RequestParam String roleInstanceIds,
                                           @RequestParam Integer roleGroupId) {
        roleGroupService.bind(roleInstanceIds, roleGroupId);
        return ApiResponse.ok();
    }
}
