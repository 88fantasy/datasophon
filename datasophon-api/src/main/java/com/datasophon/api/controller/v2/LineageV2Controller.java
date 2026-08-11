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
import com.datasophon.api.lineage.metrics.LineageJobMetricsService;
import com.datasophon.api.lineage.metrics.LineageJobMetricsService.JobMetrics;
import com.datasophon.api.lineage.metrics.LineageJobMetricsService.RatePoint;
import com.datasophon.api.lineage.proxy.GravitinoLineageClient;
import com.datasophon.api.lineage.proxy.GravitinoLineageClient.NodeInjection;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;

/** Authenticated compatibility facade for the Gravitino native lineage API. */
@RestController
@RequestMapping("/v2/lineage")
public class LineageV2Controller extends ApiController {

    private static final Logger log = LoggerFactory.getLogger(LineageV2Controller.class);

    private final GravitinoLineageClient client;
    private final LineageJobMetricsService jobMetricsService;

    public LineageV2Controller(GravitinoLineageClient client,
                               LineageJobMetricsService jobMetricsService) {
        this.client = client;
        this.jobMetricsService = jobMetricsService;
    }

    @GetMapping("/tables")
    public JsonNode tables(@RequestParam long clusterId,
                           @RequestParam(defaultValue = "1") int page,
                           @RequestParam(defaultValue = "20") int size,
                           @RequestParam(required = false) String keyword,
                           @RequestParam(required = false) String layer,
                           @RequestParam(required = false) String connector,
                           @RequestParam(required = false) String database) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("page", page);
        query.put("size", size);
        query.put("keyword", keyword);
        query.put("layer", layer);
        query.put("connector", connector);
        query.put("database", database);
        return client.get(clusterId, "lineage/tables", query, NodeInjection.TABLE_LIST);
    }

    @GetMapping("/graph")
    public JsonNode graph(@RequestParam long clusterId,
                          @RequestParam long rootNodeId,
                          @RequestParam(defaultValue = "2") int depth,
                          @RequestParam(defaultValue = "both") String direction,
                          @RequestParam(required = false) String expand) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("rootNodeId", rootNodeId);
        query.put("depth", depth);
        query.put("direction", direction);
        query.put("expand", expand);
        return client.get(clusterId, "lineage/graph", query, NodeInjection.GRAPH_NODES);
    }

    @GetMapping("/overview")
    public JsonNode overview(@RequestParam long clusterId) {
        return client.get(clusterId, "lineage/overview", Map.of(), NodeInjection.NONE);
    }

    @GetMapping("/table/{id}")
    public JsonNode table(@RequestParam long clusterId, @PathVariable long id) {
        return client.get(clusterId, "lineage/table/" + id, Map.of(), NodeInjection.SINGLE_TABLE);
    }

    @GetMapping("/job/{id}")
    public JsonNode job(@RequestParam long clusterId, @PathVariable long id) {
        return client.getJob(clusterId, id);
    }

    @GetMapping("/job-metrics")
    public Map<String, JobMetrics> jobMetrics(
                                              @RequestParam Integer clusterId,
                                              @RequestParam(required = false, defaultValue = "") String appIds) {
        List<String> requestedAppIds = appIds.isBlank() ? List.of() : Arrays.asList(appIds.split(","));
        try {
            return jobMetricsService.getJobMetrics(clusterId, requestedAppIds);
        } catch (Exception e) {
            log.error("Lineage job metrics query failed: clusterId={} reason={}", clusterId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "任务指标查询失败");
        }
    }

    @GetMapping("/job-rate-history")
    public List<RatePoint> jobRateHistory(
                                          @RequestParam Integer clusterId,
                                          @RequestParam String appId,
                                          @RequestParam long start,
                                          @RequestParam long end,
                                          @RequestParam(defaultValue = "60") long step) {
        try {
            return jobMetricsService.getJobRateHistory(clusterId, appId, start, end, step);
        } catch (IllegalArgumentException e) {
            // 请求参数越界（如窗口切出的桶数超限）是调用方的问题，不是服务端故障
            log.warn("Lineage job rate history rejected: clusterId={} appId={} reason={}",
                    clusterId, appId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("Lineage job rate history query failed: clusterId={} appId={} reason={}",
                    clusterId, appId, e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "任务速率查询失败");
        }
    }

    @GetMapping("/impact")
    public JsonNode impact(@RequestParam long clusterId,
                           @RequestParam long rootNodeId,
                           @RequestParam(defaultValue = "2") int depth) {
        return client.get(clusterId, "lineage/impact",
                Map.of("rootNodeId", rootNodeId, "depth", depth), NodeInjection.GRAPH_NODES);
    }

    @PostMapping("/rebuild")
    public ResponseEntity<JsonNode> rebuild(@RequestParam long clusterId) {
        return ResponseEntity.accepted().body(client.post(clusterId, "lineage/rebuild"));
    }
}
