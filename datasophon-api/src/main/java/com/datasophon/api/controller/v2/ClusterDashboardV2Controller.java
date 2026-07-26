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
import com.datasophon.api.dto.v2.ClusterDashboardResponse;
import com.datasophon.api.service.ClusterDashboardService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * v2 集群总览看板接口：主机/服务/告警统计、近 7 天告警趋势、服务健康度 TOP5、集群概要。
 *
 * <p>本接口只承载 DB 侧聚合数字；CPU/内存/磁盘/网络等时序图表由前端直接调用
 * {@code /v2/observability/otel/metrics/query_range} 取数，本 Controller 不涉及。
 */
@RestController
@RequestMapping("/v2/cluster/{clusterId}/dashboard")
public class ClusterDashboardV2Controller extends ApiController {

    private static final Logger log = LoggerFactory.getLogger(ClusterDashboardV2Controller.class);

    private final ClusterDashboardService clusterDashboardService;

    public ClusterDashboardV2Controller(ClusterDashboardService clusterDashboardService) {
        this.clusterDashboardService = clusterDashboardService;
    }

    @GetMapping("/summary")
    public ApiResponse<ClusterDashboardResponse> summary(@PathVariable Integer clusterId) {
        try {
            return ApiResponse.ok(clusterDashboardService.getDashboard(clusterId));
        } catch (Exception e) {
            log.error("Cluster dashboard summary query failed: cluster={} reason={}",
                    clusterId, e.getMessage(), e);
            return ApiResponse.fail(500, "集群看板数据查询失败");
        }
    }
}
