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
import com.datasophon.api.security.SystemAdminGuard;
import com.datasophon.api.service.seatunnel.SeaTunnelJobService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only SeaTunnel job endpoints. */
@RestController
@RequestMapping("/v2/cluster/{clusterId}/service/{instanceId}/seatunnel")
public class SeaTunnelJobController extends ApiController {

    private final SeaTunnelJobService jobService;
    private final SystemAdminGuard adminGuard;

    public SeaTunnelJobController(SeaTunnelJobService jobService, SystemAdminGuard adminGuard) {
        this.jobService = jobService;
        this.adminGuard = adminGuard;
    }

    @GetMapping("/overview")
    public ApiResponse<Object> overview(@PathVariable Integer clusterId, @PathVariable Integer instanceId) {
        adminGuard.requireAdmin();
        return ApiResponse.ok(jobService.overview(clusterId, instanceId));
    }

    @GetMapping("/workers")
    public ApiResponse<Object> workers(@PathVariable Integer clusterId, @PathVariable Integer instanceId) {
        adminGuard.requireAdmin();
        return ApiResponse.ok(jobService.workers(clusterId, instanceId));
    }

    @GetMapping("/pending")
    public ApiResponse<Object> pending(@PathVariable Integer clusterId, @PathVariable Integer instanceId) {
        adminGuard.requireAdmin();
        return ApiResponse.ok(jobService.pending(clusterId, instanceId));
    }

    @GetMapping("/jobs")
    public ApiResponse<Object> jobs(@PathVariable Integer clusterId,
                                    @PathVariable Integer instanceId,
                                    @RequestParam String state) {
        adminGuard.requireAdmin();
        return ApiResponse.ok(jobService.jobs(clusterId, instanceId, state));
    }

    @GetMapping("/jobs/{jobId}")
    public ApiResponse<Object> jobInfo(@PathVariable Integer clusterId,
                                       @PathVariable Integer instanceId,
                                       @PathVariable String jobId) {
        adminGuard.requireAdmin();
        return ApiResponse.ok(jobService.jobInfo(clusterId, instanceId, jobId));
    }
}
