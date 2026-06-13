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
import com.datasophon.api.dto.v2.SaveConfigRequest;
import com.datasophon.api.service.ClusterServiceInstanceConfigService;
import com.datasophon.api.service.ClusterServiceInstanceService;
import com.datasophon.api.service.ServiceInstallService;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterServiceInstanceEntity;

import jakarta.validation.Valid;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * v2 服务配置读写接口（物理集群）。
 *
 * <p>全部业务逻辑委托给现有 Service，本 Controller 只做薄封装：
 * <ul>
 *   <li>{@link ClusterServiceInstanceConfigService} — 版本列表 / 按版本读配置</li>
 *   <li>{@link ServiceInstallService#saveServiceConfig} — 保存配置（自动版本递增 + needRestart 打标）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/v2/cluster/{clusterId}/service/instance/{instanceId}/config")
public class ClusterServiceConfigV2Controller extends ApiController {
    
    @Autowired
    private ClusterServiceInstanceConfigService configService;
    
    @Autowired
    private ServiceInstallService serviceInstallService;
    
    @Autowired
    private ClusterServiceInstanceService instanceService;
    
    /**
     * 配置历史版本列表（降序）。
     *
     * @param instanceId  服务实例 ID
     * @param roleGroupId 角色组 ID
     */
    @GetMapping("/versions")
    public ApiResponse<Object> versions(@PathVariable Integer instanceId,
                                        @RequestParam Integer roleGroupId) {
        Result result = configService.getConfigVersion(instanceId, roleGroupId);
        return ApiResponse.ok(result.getData());
    }
    
    /**
     * 按版本获取配置参数列表。
     *
     * <p>前端版本切换时调用；version=null 时 Service 内部取最新版。
     *
     * @param instanceId  服务实例 ID
     * @param roleGroupId 角色组 ID
     * @param version     版本号（可选，不传则返回最新版）
     */
    @GetMapping
    public ApiResponse<Object> info(@PathVariable Integer instanceId,
                                    @RequestParam Integer roleGroupId,
                                    @RequestParam(required = false) Integer version) {
        Result result = configService.getServiceInstanceConfig(instanceId, version, roleGroupId, 1, 10000);
        return ApiResponse.ok(result.getData());
    }
    
    /**
     * 保存配置（自动生成新版本号并打 needRestart 标记）。
     *
     * <p>首次保存生成 version=1，不触发 needRestart；后续每次保存 version+1 并三处打标。
     * 该逻辑完全由 {@link ServiceInstallService#saveServiceConfig} 内部处理。
     *
     * @param clusterId   集群 ID
     * @param instanceId  服务实例 ID
     * @param req         角色组 ID + 配置项列表
     */
    @PostMapping
    public ApiResponse<Void> save(@PathVariable Integer clusterId,
                                  @PathVariable Integer instanceId,
                                  @Valid @RequestBody SaveConfigRequest req) {
        ClusterServiceInstanceEntity entity = instanceService.getById(instanceId);
        if (entity == null) {
            return ApiResponse.fail(404, "服务实例不存在");
        }
        List<ServiceConfig> configs = req.getServiceConfig();
        serviceInstallService.saveServiceConfig(clusterId, entity.getServiceName(), configs, req.getRoleGroupId());
        return ApiResponse.ok();
    }
}
