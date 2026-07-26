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
import com.datasophon.api.dto.v2.AlertCategoryResponse;
import com.datasophon.api.dto.v2.AlertGroupPageResponse;
import com.datasophon.api.dto.v2.AlertGroupResponse;
import com.datasophon.api.dto.v2.AlertHistoryPageResponse;
import com.datasophon.api.dto.v2.AlertQuotaPageResponse;
import com.datasophon.api.dto.v2.AlertQuotaResponse;
import com.datasophon.api.dto.v2.SaveAlertGroupRequest;
import com.datasophon.api.dto.v2.SaveAlertQuotaRequest;
import com.datasophon.api.dto.v2.UpdateAlertQuotaRequest;
import com.datasophon.api.enums.Status;
import com.datasophon.api.service.AlertGroupService;
import com.datasophon.api.service.ClusterAlertHistoryService;
import com.datasophon.api.service.ClusterAlertQuotaService;
import com.datasophon.api.service.FrameServiceRoleService;
import com.datasophon.api.service.FrameServiceService;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.AlertGroupEntity;
import com.datasophon.dao.entity.ClusterAlertHistory;
import com.datasophon.dao.entity.ClusterAlertQuota;
import com.datasophon.dao.enums.AlertLevel;

import java.util.Date;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.metadata.IPage;

import jakarta.validation.Valid;

/**
 * v2 告警管理接口（告警组 + 告警指标 + 告警历史）。
 */
@RestController
@RequestMapping("/v2/cluster/{clusterId}/alert")
public class ClusterAlertV2Controller extends ApiController {

    private final AlertGroupService alertGroupService;

    private final ClusterAlertQuotaService clusterAlertQuotaService;

    private final ClusterAlertHistoryService clusterAlertHistoryService;

    private final FrameServiceService frameServiceService;

    private final FrameServiceRoleService frameServiceRoleService;

    public ClusterAlertV2Controller(AlertGroupService alertGroupService,
                                    ClusterAlertQuotaService clusterAlertQuotaService,
                                    ClusterAlertHistoryService clusterAlertHistoryService,
                                    FrameServiceService frameServiceService,
                                    FrameServiceRoleService frameServiceRoleService) {
        this.alertGroupService = alertGroupService;
        this.clusterAlertQuotaService = clusterAlertQuotaService;
        this.clusterAlertHistoryService = clusterAlertHistoryService;
        this.frameServiceService = frameServiceService;
        this.frameServiceRoleService = frameServiceRoleService;
    }

    // ── 告警组 ──────────────────────────────────────────────────

    /**
     * 告警组列表（分页）。
     */
    @GetMapping("/group/list")
    public ApiResponse<AlertGroupPageResponse> listGroups(@PathVariable Integer clusterId,
                                                          @RequestParam(required = false) String alertGroupName,
                                                          @RequestParam(defaultValue = "1") Integer page,
                                                          @RequestParam(defaultValue = "20") Integer pageSize) {
        Result result = alertGroupService.getAlertGroupList(clusterId, alertGroupName, page, pageSize);
        @SuppressWarnings("unchecked")
        List<AlertGroupEntity> entities = (List<AlertGroupEntity>) result.get("data");
        Object totalObj = result.get("total");
        long total = totalObj instanceof Number ? ((Number) totalObj).longValue() : 0L;
        List<AlertGroupResponse> list = AlertGroupResponse.fromList(entities);
        return ApiResponse.ok(AlertGroupPageResponse.of(list, total));
    }

    /**
     * 新建告警组。
     */
    @PostMapping("/group")
    public ApiResponse<Void> saveGroup(@PathVariable Integer clusterId,
                                       @Valid @RequestBody SaveAlertGroupRequest request) {
        alertGroupService.saveAlertGroup(request.toEntity(clusterId));
        return ApiResponse.ok();
    }

    /**
     * 删除告警组（批量）。若已绑定告警指标则拒绝删除。
     */
    @DeleteMapping("/group")
    public ApiResponse<Void> deleteGroups(@PathVariable Integer clusterId,
                                          @RequestBody List<Integer> ids) {
        List<ClusterAlertQuota> bound = clusterAlertQuotaService.lambdaQuery()
                .in(ClusterAlertQuota::getAlertGroupId, ids).list();
        if (!bound.isEmpty()) {
            return ApiResponse.fail(400, Status.ALERT_GROUP_TIPS_ONE.getMsg());
        }
        alertGroupService.removeByIds(ids);
        return ApiResponse.ok();
    }

    /**
     * 告警组类别下拉（服务列表，用于新建告警组时选择关联服务）。
     */
    @GetMapping("/group/categories")
    public ApiResponse<List<AlertCategoryResponse>> listCategories(@PathVariable Integer clusterId) {
        return ApiResponse.ok(AlertCategoryResponse.fromList(frameServiceService.getFrameServiceList(clusterId)));
    }

    // ── 告警指标 ──────────────────────────────────────────────────

    /**
     * 告警指标列表（分页，可按 alertGroupId / quotaName 过滤）。
     */
    @GetMapping("/quota/list")
    public ApiResponse<AlertQuotaPageResponse> listQuotas(@PathVariable Integer clusterId,
                                                          @RequestParam(required = false) Integer alertGroupId,
                                                          @RequestParam(required = false) String quotaName,
                                                          @RequestParam(defaultValue = "1") Integer page,
                                                          @RequestParam(defaultValue = "20") Integer pageSize) {
        Result result =
                clusterAlertQuotaService.getAlertQuotaList(clusterId, alertGroupId, quotaName, page, pageSize);
        @SuppressWarnings("unchecked")
        List<ClusterAlertQuota> entities = (List<ClusterAlertQuota>) result.get("data");
        Object totalObj = result.get("total");
        long total = totalObj instanceof Number ? ((Number) totalObj).longValue() : 0L;
        List<AlertQuotaResponse> list = AlertQuotaResponse.fromList(entities);
        return ApiResponse.ok(AlertQuotaPageResponse.of(list, total));
    }

    /**
     * 新建告警指标。
     */
    @PostMapping("/quota")
    public ApiResponse<Void> saveQuota(@PathVariable Integer clusterId,
                                       @Valid @RequestBody SaveAlertQuotaRequest request) {
        clusterAlertQuotaService.saveAlertQuota(request.toEntity());
        return ApiResponse.ok();
    }

    /**
     * 修改告警指标。
     */
    @PutMapping("/quota")
    public ApiResponse<Void> updateQuota(@Valid @RequestBody UpdateAlertQuotaRequest request) {
        clusterAlertQuotaService.updateById(request.toEntity());
        return ApiResponse.ok();
    }

    /**
     * 删除告警指标（批量）。
     */
    @DeleteMapping("/quota")
    public ApiResponse<Void> deleteQuotas(@RequestBody List<Integer> ids) {
        clusterAlertQuotaService.removeByIds(ids);
        return ApiResponse.ok();
    }

    /**
     * 启用告警指标（批量，alertQuotaIds 逗号分隔）。
     */
    @PostMapping("/quota/start")
    public ApiResponse<Void> startQuotas(@PathVariable Integer clusterId,
                                         @RequestParam String alertQuotaIds) {
        clusterAlertQuotaService.start(clusterId, alertQuotaIds);
        return ApiResponse.ok();
    }

    /**
     * 停用告警指标（批量，alertQuotaIds 逗号分隔）。
     */
    @PostMapping("/quota/stop")
    public ApiResponse<Void> stopQuotas(@PathVariable Integer clusterId,
                                        @RequestParam String alertQuotaIds) {
        clusterAlertQuotaService.stop(clusterId, alertQuotaIds);
        return ApiResponse.ok();
    }

    /**
     * 按告警组查询可绑定的服务角色列表（用于新建告警指标时选择绑定角色）。
     */
    @GetMapping("/quota/roles")
    public ApiResponse<Object> listQuotaRoles(@PathVariable Integer clusterId,
                                              @RequestParam Integer alertGroupId) {
        AlertGroupEntity alertGroup = alertGroupService.getById(alertGroupId);
        Result result = frameServiceRoleService.getServiceRoleByServiceName(clusterId, alertGroup.getAlertGroupCategory());
        return ApiResponse.ok(result.get("data"));
    }

    // ── 告警历史 ──────────────────────────────────────────────────

    /**
     * 告警历史列表（分页，包含告警中和已恢复记录）。
     */
    @GetMapping("/history/list")
    public ApiResponse<AlertHistoryPageResponse> listHistory(@PathVariable Integer clusterId,
                                                             @RequestParam(required = false) String alertTargetName,
                                                             @RequestParam(required = false) String hostname,
                                                             @RequestParam(required = false) Integer alertLevel,
                                                             @RequestParam(required = false) Integer status,
                                                             @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
                                                             @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime,
                                                             @RequestParam(defaultValue = "1") Integer page,
                                                             @RequestParam(defaultValue = "20") Integer pageSize) {
        AlertLevel level = toAlertLevel(alertLevel);
        IPage<ClusterAlertHistory> historyPage = clusterAlertHistoryService.getHistoryPage(
                clusterId, alertTargetName, hostname, level, status, startTime, endTime, page, pageSize);
        return ApiResponse.ok(AlertHistoryPageResponse.from(historyPage));
    }

    private static AlertLevel toAlertLevel(Integer value) {
        if (value == null) {
            return null;
        }
        for (AlertLevel level : AlertLevel.values()) {
            if (level.getValue() == value) {
                return level;
            }
        }
        return null;
    }
}
