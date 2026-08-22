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
import com.datasophon.api.security.ImportedReadOnly;
import com.datasophon.api.service.instance.K8sServiceInstanceValuesService;
import com.datasophon.dao.entity.instance.K8sServiceInstanceValues;

import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

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
     * <p>同 {@link #save} 的越权问题：路径里的 {@code clusterId} 与 {@code instanceId} 互相独立，
     * 换个 {@code instanceId} 就能列出任意实例（含别的集群）的 values 版本号 → valueId 映射，
     * 是拿到 {@code valueId} 后调用 {@link #info} 读全文的前置步骤。这里用 {@link #belongsToCluster}
     * 校验返回的记录确实属于路径声明的集群。
     *
     * @param clusterId  路径中的集群 ID
     * @param instanceId 实例 ID
     */
    @GetMapping("/versions")
    @Operation(summary = "获取 Helm values 版本列表")
    public ApiResponse<List<K8sServiceInstanceValues>> versions(@PathVariable Integer clusterId,
                                                                @PathVariable Integer instanceId) {
        List<K8sServiceInstanceValues> list = k8sServiceInstanceValuesService.listSimpleByInstanceId(instanceId);
        if (!list.isEmpty() && !belongsToCluster(list.get(0), clusterId)) {
            return ApiResponse.fail(404, "配置记录不存在");
        }
        return ApiResponse.ok(list);
    }

    /**
     * 按 valueId 读取完整 Helm values（含 values / deltaValues / metaFileType）。
     *
     * <p>同 {@link #save} 的越权问题：{@code valueId} 未做归属校验，任意登录用户换个 URL 里的
     * clusterId/instanceId/valueId 组合即可读到别的集群的 Helm values 全文（routinely 含密码等
     * 敏感值）。这里用 {@link #belongsToCluster} 校验记录确实属于路径声明的集群与实例。
     *
     * @param clusterId  路径中的集群 ID
     * @param instanceId 路径中的实例 ID
     * @param valueId    values 记录 ID
     */
    @GetMapping("/{valueId}")
    @Operation(summary = "读取指定版本的 Helm values")
    public ApiResponse<K8sServiceInstanceValues> info(@PathVariable Integer clusterId,
                                                      @PathVariable Integer instanceId,
                                                      @PathVariable Integer valueId) {
        K8sServiceInstanceValues db = k8sServiceInstanceValuesService.getById(valueId);
        if (db == null || !belongsToCluster(db, clusterId) || !Objects.equals(db.getInstanceId(), instanceId)) {
            return ApiResponse.fail(404, "配置记录不存在");
        }
        return ApiResponse.ok(db);
    }

    /**
     * 保存用户编辑的 deltaValues（仅更新当前版本，不升版、不打 needRestart）。
     *
     * <p>{@code @ImportedReadOnly} 门禁只按路径里的 {@code clusterId} 判定，而 {@code req} 里的
     * values id 可以指向任意集群的记录——只换 URL 里的 clusterId 就能绕过门禁去改别的集群数据。
     * 这里先确认 {@code req.getId()} 对应的记录确实属于路径声明的集群，不属于直接拒绝；
     * Service 层的接管只读门禁（按记录自身 clusterId 判定）仍然保留，两层互不替代。
     *
     * @param clusterId 路径中的集群 ID
     * @param req       包含 id + deltaValues
     */
    @ImportedReadOnly("修改服务配置")
    @PostMapping
    @Operation(summary = "保存 Helm deltaValues")
    public ApiResponse<Void> save(@PathVariable Integer clusterId,
                                  @RequestBody K8sServiceInstanceValuesUpdateDTO req) {
        K8sServiceInstanceValues db = k8sServiceInstanceValuesService.getById(req.getId());
        if (!belongsToCluster(db, clusterId)) {
            return ApiResponse.fail(404, "配置记录不存在");
        }
        k8sServiceInstanceValuesService.update(req);
        return ApiResponse.ok();
    }

    /**
     * 归属校验：记录存在且 {@code clusterId} 与路径声明的一致。
     * {@code versions} / {@code info} / {@code save} 三个端点共用，避免下次再漏第四个端点。
     */
    private boolean belongsToCluster(K8sServiceInstanceValues db, Integer clusterId) {
        return db != null && Objects.equals(db.getClusterId(), clusterId);
    }
}
