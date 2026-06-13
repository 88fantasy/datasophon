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
import com.datasophon.api.dto.v2.SaveYarnQueueRequest;
import com.datasophon.api.dto.v2.UpdateYarnQueueRequest;
import com.datasophon.api.dto.v2.YarnQueuePageResponse;
import com.datasophon.api.dto.v2.YarnQueueResponse;
import com.datasophon.api.enums.Status;
import com.datasophon.api.service.ClusterYarnQueueService;
import com.datasophon.api.service.ClusterYarnSchedulerService;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterYarnQueue;
import com.datasophon.dao.entity.ClusterYarnScheduler;

import jakarta.validation.Valid;

import java.util.List;
import java.util.Objects;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

/**
 * v2 YARN 队列管理接口。
 */
@RestController
@RequestMapping("/v2/cluster/{clusterId}/yarn/queue")
public class ClusterYarnQueueV2Controller extends ApiController {
    
    private final ClusterYarnQueueService clusterYarnQueueService;
    
    private final ClusterYarnSchedulerService clusterYarnSchedulerService;
    
    public ClusterYarnQueueV2Controller(ClusterYarnQueueService clusterYarnQueueService,
                                        ClusterYarnSchedulerService clusterYarnSchedulerService) {
        this.clusterYarnQueueService = clusterYarnQueueService;
        this.clusterYarnSchedulerService = clusterYarnSchedulerService;
    }
    
    /**
     * 获取调度器类型（fair / capacity / …）。
     */
    @GetMapping("/scheduler")
    public ApiResponse<String> scheduler(@PathVariable Integer clusterId) {
        ClusterYarnScheduler scheduler = clusterYarnSchedulerService.getScheduler(clusterId);
        return ApiResponse.ok(scheduler.getScheduler());
    }
    
    /**
     * 队列列表（分页）。
     */
    @GetMapping("/list")
    public ApiResponse<YarnQueuePageResponse> list(@PathVariable Integer clusterId,
                                                   @RequestParam(defaultValue = "1") Integer page,
                                                   @RequestParam(defaultValue = "20") Integer pageSize) {
        Result result = clusterYarnQueueService.listByPage(clusterId, page, pageSize);
        @SuppressWarnings("unchecked")
        List<ClusterYarnQueue> entities = (List<ClusterYarnQueue>) result.getData();
        long total = (long) result.get(Constants.TOTAL);
        List<YarnQueueResponse> items = YarnQueueResponse.fromList(entities);
        return ApiResponse.ok(YarnQueuePageResponse.of(items, total));
    }
    
    /**
     * 新建队列。
     */
    @PostMapping("/save")
    public ApiResponse<Void> save(@PathVariable Integer clusterId,
                                  @Valid @RequestBody SaveYarnQueueRequest request) {
        List<ClusterYarnQueue> existing = clusterYarnQueueService
                .list(new QueryWrapper<ClusterYarnQueue>().eq(Constants.QUEUE_NAME, request.getQueueName()));
        if (Objects.nonNull(existing) && existing.size() == 1) {
            return ApiResponse.fail(400, Status.QUEUE_NAME_ALREADY_EXISTS.getMsg());
        }
        clusterYarnQueueService.save(request.toEntity(clusterId));
        return ApiResponse.ok();
    }
    
    /**
     * 修改队列。
     */
    @PostMapping("/update")
    public ApiResponse<Void> update(@Valid @RequestBody UpdateYarnQueueRequest request) {
        clusterYarnQueueService.updateById(request.toEntity());
        return ApiResponse.ok();
    }
    
    /**
     * 删除队列（批量）。
     */
    @PostMapping("/delete")
    public ApiResponse<Void> delete(@RequestBody List<Integer> ids) {
        clusterYarnQueueService.removeByIds(ids);
        return ApiResponse.ok();
    }
    
    /**
     * 刷新队列到 YARN。
     */
    @PostMapping("/refresh")
    public ApiResponse<Void> refresh(@PathVariable Integer clusterId) throws Exception {
        Result result = clusterYarnQueueService.refreshQueues(clusterId);
        if (!result.isSuccess()) {
            String msg = (String) result.get(Constants.MSG);
            return ApiResponse.fail(500, msg != null ? msg : Status.FAILED_REFRESH_THE_QUEUE_TO_YARN.getMsg());
        }
        return ApiResponse.ok();
    }
}
