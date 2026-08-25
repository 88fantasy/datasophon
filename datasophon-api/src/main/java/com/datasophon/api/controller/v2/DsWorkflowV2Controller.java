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
import com.datasophon.api.dto.v2.DsDagVO;
import com.datasophon.api.dto.v2.DsPageVO;
import com.datasophon.api.dto.v2.DsProjectVO;
import com.datasophon.api.dto.v2.DsWorkflowDefinitionVO;
import com.datasophon.api.dto.v2.DsWorkflowInstanceVO;
import com.datasophon.api.service.ds.DsWorkflowService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v2/ds")
public class DsWorkflowV2Controller extends ApiController {

    private final DsWorkflowService workflowService;

    public DsWorkflowV2Controller(DsWorkflowService workflowService) {
        this.workflowService = workflowService;
    }

    @GetMapping("/projects")
    public ApiResponse<DsPageVO<DsProjectVO>> projects(@RequestParam Integer clusterId) {
        return ApiResponse.ok(workflowService.projects(clusterId));
    }

    @GetMapping("/workflows")
    public ApiResponse<DsPageVO<DsWorkflowDefinitionVO>> workflows(
                                                                   @RequestParam Integer clusterId,
                                                                   @RequestParam long projectCode,
                                                                   @RequestParam(defaultValue = "1") int pageNo,
                                                                   @RequestParam(defaultValue = "20") int pageSize,
                                                                   @RequestParam(required = false) String searchVal) {
        return ApiResponse.ok(workflowService.workflows(clusterId, projectCode, pageNo, pageSize, searchVal));
    }

    @GetMapping("/workflows/{workflowCode}/instances")
    public ApiResponse<DsPageVO<DsWorkflowInstanceVO>> instances(
                                                                 @RequestParam Integer clusterId,
                                                                 @RequestParam long projectCode,
                                                                 @PathVariable long workflowCode,
                                                                 @RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(workflowService.instances(clusterId, projectCode, workflowCode, limit));
    }

    @GetMapping("/instances/{instanceId}/dag")
    public ApiResponse<DsDagVO> dag(@RequestParam Integer clusterId,
                                    @RequestParam long projectCode,
                                    @PathVariable int instanceId) {
        return ApiResponse.ok(workflowService.dag(clusterId, projectCode, instanceId));
    }
}
