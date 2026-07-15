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
import com.datasophon.api.dto.v2.ServiceInstanceResponse;
import com.datasophon.api.service.ClusterServiceInstanceService;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterServiceInstanceEntity;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

/**
 * v2 服务实例管理接口。
 *
 * <p>复用现有 ClusterServiceInstanceService，返回 ApiResponse 标准信封。
 */
@Slf4j
@RestController
@RequestMapping("/v2/cluster/{clusterId}/service/instance")
public class ClusterServiceInstanceV2Controller extends ApiController {

    @Autowired
    private ClusterServiceInstanceService clusterServiceInstanceService;

    /**
     * 获取集群服务实例列表（含 catalog / 告警数 / dashboardUrl 等运行时信息）。
     */
    @GetMapping("/list")
    public ApiResponse<List<ServiceInstanceResponse>> list(
                                                           @PathVariable Integer clusterId) {
        List<ClusterServiceInstanceEntity> list = clusterServiceInstanceService.listAll(clusterId);
        return ApiResponse.ok(ServiceInstanceResponse.fromList(list));
    }

    /**
     * 获取单个服务实例详情。
     */
    @GetMapping("/{instanceId}")
    public ApiResponse<ServiceInstanceResponse> info(
                                                     @PathVariable Integer clusterId,
                                                     @PathVariable Integer instanceId) {
        ClusterServiceInstanceEntity entity = clusterServiceInstanceService.getById(instanceId);
        return ApiResponse.ok(ServiceInstanceResponse.from(entity));
    }

    /**
     * 删除服务实例（级联清理角色实例、角色组、WebUI 及关联的 ClusterVariable）。
     *
     * <p>仍有 RUNNING 状态的角色实例时会被拒绝，需先停止全部角色实例。
     */
    @DeleteMapping("/{instanceId}")
    public ApiResponse<Void> delete(@PathVariable Integer clusterId,
                                    @PathVariable Integer instanceId) {
        Result result = clusterServiceInstanceService.delServiceInstance(instanceId);
        if (result.isSuccess()) {
            return ApiResponse.ok();
        }
        return ApiResponse.fail(500, String.valueOf(result.get(Constants.MSG)));
    }
}
