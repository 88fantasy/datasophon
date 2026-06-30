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

package com.datasophon.api.controller.observability;

import com.datasophon.api.controller.ApiController;
import com.datasophon.api.observability.OtelLogsQueryService;
import com.datasophon.api.observability.OtelMonitorService;
import com.datasophon.api.observability.OtelTracesQueryService;
import com.datasophon.api.observability.OtelTracesQueryService.PageResult;
import com.datasophon.common.utils.Result;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("observability/otelcol")
public class OtelMonitorController extends ApiController {
    
    private final OtelMonitorService monitorService;
    private final OtelTracesQueryService tracesQueryService;
    private final OtelLogsQueryService logsQueryService;
    
    public OtelMonitorController(OtelMonitorService monitorService,
                                 OtelTracesQueryService tracesQueryService,
                                 OtelLogsQueryService logsQueryService) {
        this.monitorService = monitorService;
        this.tracesQueryService = tracesQueryService;
        this.logsQueryService = logsQueryService;
    }
    
    @GetMapping("monitor")
    public Result monitor(@RequestParam Integer clusterId) {
        return Result.success(monitorService.collectAll(clusterId));
    }
    
    @GetMapping("traces")
    public Result traces(@RequestParam Integer clusterId,
                         @RequestParam long start,
                         @RequestParam long end,
                         @RequestParam(required = false) String serviceName,
                         @RequestParam(required = false) String status,
                         @RequestParam(required = false) String spanName,
                         @RequestParam(required = false) String traceId,
                         @RequestParam(defaultValue = "1") int page,
                         @RequestParam(defaultValue = "20") int pageSize) {
        PageResult<?> result = tracesQueryService.listTraces(
                clusterId, start, end, serviceName, status, spanName, traceId, page, pageSize);
        return Result.success(result.total(), result.data());
    }
    
    @GetMapping("traces/detail")
    public Result traceDetail(@RequestParam Integer clusterId,
                              @RequestParam String traceId) {
        return Result.success(tracesQueryService.getTrace(clusterId, traceId));
    }
    
    @GetMapping("traces/services")
    public Result traceServices(@RequestParam Integer clusterId,
                                @RequestParam long start,
                                @RequestParam long end) {
        return Result.success(tracesQueryService.listServices(clusterId, start, end));
    }
    
    @GetMapping("logs")
    public Result logs(@RequestParam Integer clusterId,
                       @RequestParam long start,
                       @RequestParam long end,
                       @RequestParam(required = false) String serviceName,
                       @RequestParam(required = false) String severities,
                       @RequestParam(required = false) String bodyKeyword,
                       @RequestParam(required = false) String traceId,
                       @RequestParam(defaultValue = "1") int page,
                       @RequestParam(defaultValue = "50") int pageSize) {
        PageResult<?> result = logsQueryService.listLogs(
                clusterId, start, end, serviceName, severities, bodyKeyword, traceId, page, pageSize);
        return Result.success(result.total(), result.data());
    }
}
