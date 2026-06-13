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
import com.datasophon.api.dto.extrepo.DeploymentDTO;
import com.datasophon.api.dto.extrepo.RunDagDto;
import com.datasophon.api.dto.extrepo.ServiceRoleQueryDTO;
import com.datasophon.api.service.ServiceInstallService;
import com.datasophon.api.service.extrepo.ExtRepoInstallDelegateService;
import com.datasophon.api.service.extrepo.PhysicalProductInstallService;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.common.Constants;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceRoleHostMapping;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterHostDO;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Collections;
import java.util.List;

import lombok.Data;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

/**
 * v2 添加服务向导接口 — 给已建集群追加服务（物理集群路径）。
 * 方法体全量委托现有 service，零业务改动。
 *
 * <p>向导 6 步对应：① 导入清单（复用 /deploy/upload + /deploy/validate-deployment-file）
 * ② 选服务（list-newest + check-dependency）③ Master 角色（service-roles + hosts + role-host-mapping）
 * ④ Worker 角色（non-master-roles + role-host-mapping）⑤ 服务配置（config-from-ddl + save-config）
 * ⑥ 安装启动（install → dagId → 前端跳 DAG 全屏图）。
 */
@RestController
@RequestMapping("/v2/cluster/{clusterId}/add-service")
public class ClusterAddServiceV2Controller extends ApiController {
    
    private final PhysicalProductInstallService physicalProductInstallService;
    private final ServiceInstallService serviceInstallService;
    private final ClusterHostService clusterHostService;
    private final ExtRepoInstallDelegateService extRepoInstallDelegateService;
    
    public ClusterAddServiceV2Controller(
                                         PhysicalProductInstallService physicalProductInstallService,
                                         ServiceInstallService serviceInstallService,
                                         ClusterHostService clusterHostService,
                                         ExtRepoInstallDelegateService extRepoInstallDelegateService) {
        this.physicalProductInstallService = physicalProductInstallService;
        this.serviceInstallService = serviceInstallService;
        this.clusterHostService = clusterHostService;
        this.extRepoInstallDelegateService = extRepoInstallDelegateService;
    }
    
    // ─── 步骤 2：选择服务 ───────────────────────────────────────────────────
    
    /** 按部署清单获取最新服务列表（清单中出现的服务带 selected=true）。 */
    @PostMapping("/list-newest")
    public Result listNewest(
                             @PathVariable Integer clusterId,
                             @RequestBody @Valid ManifestRequest req) {
        DeploymentDTO dto = new DeploymentDTO();
        dto.setClusterId(clusterId);
        dto.setDeployFileId(req.getDeployFileId());
        dto.setContentDecodePasswd(passwd(req.getContentDecodePasswd()));
        return Result.success(physicalProductInstallService.listNewestByDeployment(dto));
    }
    
    /** 校验所选服务的依赖完整性（缺依赖时返回错误信息）。 */
    @PostMapping("/check-dependency")
    public Result checkDependency(
                                  @PathVariable Integer clusterId,
                                  @RequestBody @Valid ServiceIdsRequest req) {
        return serviceInstallService.checkServiceDependency(clusterId, req.getServiceIds());
    }
    
    // ─── 步骤 3/4：分配角色 ────────────────────────────────────────────────
    
    /** 获取 Master 角色列表（按部署清单回填 hosts）。 */
    @PostMapping("/service-roles")
    public Result serviceRoles(
                               @PathVariable Integer clusterId,
                               @RequestBody @Valid RoleQueryRequest req) {
        ServiceRoleQueryDTO dto = new ServiceRoleQueryDTO();
        dto.setClusterId(clusterId);
        dto.setDeployFileId(req.getDeployFileId());
        dto.setContentDecodePasswd(passwd(req.getContentDecodePasswd()));
        dto.setServiceIds(joinIds(req.getServiceIds()));
        dto.setServiceRoleType(1);
        return Result.success(physicalProductInstallService.getServiceRoleListByDeployment(dto));
    }
    
    /** 获取非 Master（Worker/Client）角色列表（按部署清单回填 hosts）。 */
    @PostMapping("/non-master-roles")
    public Result nonMasterRoles(
                                 @PathVariable Integer clusterId,
                                 @RequestBody @Valid RoleQueryRequest req) {
        DeploymentDTO dto = new DeploymentDTO();
        dto.setClusterId(clusterId);
        dto.setDeployFileId(req.getDeployFileId());
        dto.setContentDecodePasswd(passwd(req.getContentDecodePasswd()));
        dto.setServiceIds(joinIds(req.getServiceIds()));
        return Result.success(physicalProductInstallService.getNonMasterRoleListByDeployment(dto));
    }
    
    /** 查询集群全部已纳管主机（角色分配候选）。 */
    @GetMapping("/hosts")
    public Result hosts(@PathVariable Integer clusterId) {
        List<ClusterHostDO> list =
                clusterHostService.list(new QueryWrapper<ClusterHostDO>().eq(Constants.CLUSTER_ID, clusterId)
                        .eq(Constants.MANAGED, 1)
                        .orderByAsc(Constants.HOSTNAME));
        return Result.success(list);
    }
    
    /** 保存服务角色与主机映射关系（Master/Worker 步共用）。 */
    @PostMapping("/role-host-mapping")
    public Result saveRoleHostMapping(
                                      @PathVariable Integer clusterId,
                                      @RequestBody @NotEmpty List<ServiceRoleHostMapping> list) {
        serviceInstallService.saveServiceRoleHostMapping(clusterId, list);
        return Result.success();
    }
    
    // ─── 步骤 5：服务配置 ───────────────────────────────────────────────────
    
    /** 从服务 DDL 定义读取配置项（未安装服务的初始配置）。 */
    @GetMapping("/config-from-ddl")
    public Result configFromDdl(
                                @PathVariable Integer clusterId,
                                @RequestParam String serviceName) {
        return Result.success(serviceInstallService.getServiceConfigFromDdl(clusterId, serviceName));
    }
    
    /** 保存单个服务的配置（请求体形态与 ClusterServiceConfigV2Controller 一致）。 */
    @PostMapping("/save-config")
    public Result saveConfig(
                             @PathVariable Integer clusterId,
                             @RequestBody @Valid SaveConfigRequest req) {
        Integer roleGroupId = req.getRoleGroupId() != null ? req.getRoleGroupId() : -1;
        serviceInstallService.saveServiceConfig(clusterId, req.getServiceName(), req.getServiceConfig(), roleGroupId);
        return Result.success();
    }
    
    // ─── 步骤 6：安装并启动 ────────────────────────────────────────────────
    
    /** 生成通用安装命令并立即执行 DAG，返回 dagId 供前端跳转 DAG 全屏图。 */
    @PostMapping("/install")
    public Result install(
                          @PathVariable Integer clusterId,
                          @RequestBody @Valid InstallRequest req) {
        String dagId = extRepoInstallDelegateService.generateGenericInstallCommand(clusterId, req.getServiceNames());
        RunDagDto runDag = new RunDagDto();
        runDag.setDagId(dagId);
        extRepoInstallDelegateService.redeploy(runDag);
        return Result.success(Collections.singletonMap("dagId", dagId));
    }
    
    // ─── 内部 DTO / 工具 ────────────────────────────────────────────────────
    
    private static String passwd(String value) {
        return value != null ? value : "";
    }
    
    private static String joinIds(List<Integer> ids) {
        if (ids == null || ids.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Integer id : ids) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(id);
        }
        return sb.toString();
    }
    
    /** 部署清单请求体（clusterId 由路径变量提供）。 */
    @Data
    public static class ManifestRequest {
        @NotNull(message = "部署文件ID不能为空")
        private Integer deployFileId;
        /** 配置文件密码，可为空。 */
        private String contentDecodePasswd;
    }
    
    /** 服务依赖校验请求体。 */
    @Data
    public static class ServiceIdsRequest {
        @NotEmpty(message = "服务ID列表不能为空")
        private List<Integer> serviceIds;
    }
    
    /** 角色列表查询请求体。 */
    @Data
    public static class RoleQueryRequest {
        @NotNull(message = "部署文件ID不能为空")
        private Integer deployFileId;
        private String contentDecodePasswd;
        @NotEmpty(message = "服务ID列表不能为空")
        private List<Integer> serviceIds;
    }
    
    /** 保存服务配置请求体。 */
    @Data
    public static class SaveConfigRequest {
        @NotNull(message = "服务名不能为空")
        private String serviceName;
        @NotEmpty(message = "服务配置不能为空")
        private List<ServiceConfig> serviceConfig;
        /** 角色组ID，新装服务传 -1（默认）。 */
        private Integer roleGroupId;
    }
    
    /** 安装请求体。 */
    @Data
    public static class InstallRequest {
        @NotEmpty(message = "服务名列表不能为空")
        private List<String> serviceNames;
    }
}
