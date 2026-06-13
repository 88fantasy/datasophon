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
import com.datasophon.api.dto.v2.ManagersRequest;
import com.datasophon.api.security.UserPermission;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterRoleUserService;
import com.datasophon.api.service.FrameInfoService;
import com.datasophon.api.service.UserInfoService;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.FrameInfoEntity;
import com.datasophon.dao.entity.UserInfoEntity;

import jakarta.validation.Valid;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * v2 集群管理接口。
 *
 * <p>完整路径示例（context-path=/ddh，/api 前缀由 configurePathMatch 注入）：
 * <ul>
 *   <li>GET  {@code /ddh/api/v2/cluster/list}</li>
 *   <li>POST {@code /ddh/api/v2/cluster}</li>
 *   <li>PUT  {@code /ddh/api/v2/cluster/{id}}</li>
 *   <li>DELETE {@code /ddh/api/v2/cluster/{id}}</li>
 *   <li>PUT  {@code /ddh/api/v2/cluster/{id}/managers}</li>
 *   <li>GET  {@code /ddh/api/v2/frame/list}</li>
 *   <li>GET  {@code /ddh/api/v2/user/list}</li>
 * </ul>
 */
@RestController
@RequestMapping("/v2")
public class ClusterV2Controller extends ApiController {
    
    @Autowired
    private ClusterInfoService clusterInfoService;
    
    @Autowired
    private FrameInfoService frameInfoService;
    
    @Autowired
    private UserInfoService userInfoService;
    
    @Autowired
    private ClusterRoleUserService clusterRoleUserService;
    
    // ─── 集群 CRUD ────────────────────────────────────────────────────
    
    @GetMapping("/cluster/list")
    public ApiResponse<List<ClusterInfoEntity>> clusterList() {
        return ApiResponse.ok(clusterInfoService.getClusterList());
    }
    
    @PostMapping("/cluster")
    @UserPermission
    public ApiResponse<ClusterInfoEntity> createCluster(@Valid @RequestBody ClusterInfoEntity entity) {
        return ApiResponse.ok(clusterInfoService.saveCluster(entity));
    }
    
    @PutMapping("/cluster/{id}")
    @UserPermission
    public ApiResponse<Void> updateCluster(@PathVariable Integer id,
                                           @RequestBody ClusterInfoEntity entity) {
        entity.setId(id);
        clusterInfoService.updateCluster(entity);
        return ApiResponse.ok();
    }
    
    @DeleteMapping("/cluster/{id}")
    @UserPermission
    public ApiResponse<Void> deleteCluster(@PathVariable Integer id) {
        clusterInfoService.deleteCluster(id);
        return ApiResponse.ok();
    }
    
    // ─── 集群管理员授权 ────────────────────────────────────────────────
    
    @PutMapping("/cluster/{id}/managers")
    @UserPermission
    public ApiResponse<Void> saveManagers(@PathVariable Integer id,
                                          @RequestBody ManagersRequest req) {
        final String userIds = (req.getUserIds() == null || req.getUserIds().isEmpty())
                ? ""
                : req.getUserIds().stream()
                        .map(Object::toString)
                        .collect(Collectors.joining(","));
        clusterRoleUserService.saveClusterManager(id, userIds);
        return ApiResponse.ok();
    }
    
    // ─── 框架版本 + 用户列表（下拉数据源）────────────────────────────────
    
    @GetMapping("/frame/list")
    public ApiResponse<List<FrameInfoEntity>> frameList() {
        return ApiResponse.ok(frameInfoService.list());
    }
    
    /** 查询所有普通用户（排除超级管理员 id=1），用于集群授权下拉。 */
    @GetMapping("/user/list")
    public ApiResponse<List<UserInfoEntity>> userList() {
        return ApiResponse.ok(
                userInfoService.lambdaQuery().ne(UserInfoEntity::getId, 1).list());
    }
}
