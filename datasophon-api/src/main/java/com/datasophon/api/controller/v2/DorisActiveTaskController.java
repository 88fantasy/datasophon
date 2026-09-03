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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
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
import com.datasophon.api.dto.v2.DorisActiveTaskQueryDTO;
import com.datasophon.api.dto.v2.DorisActiveTaskResponseVO;
import com.datasophon.api.dto.v2.DorisActiveTaskVO;
import com.datasophon.api.service.doris.DorisActiveTaskFacade;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** v2 Doris active-task endpoint. */
@RestController
@RequestMapping("/v2/cluster/{clusterId}/service/{instanceId}/doris")
public class DorisActiveTaskController extends ApiController {

    private final DorisActiveTaskFacade facade;

    public DorisActiveTaskController(DorisActiveTaskFacade facade) {
        this.facade = facade;
    }

    @PostMapping("/active-tasks")
    public ApiResponse<DorisActiveTaskResponseVO> activeTasks(
                                                              @PathVariable Integer clusterId,
                                                              @PathVariable Integer instanceId,
                                                              @RequestBody(required = false) DorisActiveTaskQueryDTO query) {
        return ApiResponse.ok(facade.query(clusterId, instanceId, query));
    }

    @GetMapping("/active-tasks/{taskId}")
    public ApiResponse<DorisActiveTaskVO> activeTaskDetail(@PathVariable Integer clusterId,
                                                           @PathVariable Integer instanceId,
                                                           @PathVariable String taskId) {
        return ApiResponse.ok(facade.queryDetail(clusterId, instanceId, taskId));
    }
}
