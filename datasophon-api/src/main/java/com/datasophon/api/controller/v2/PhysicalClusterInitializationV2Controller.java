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
import com.datasophon.api.dto.v2.PhysicalClusterInitializationResponse;
import com.datasophon.api.service.PhysicalClusterInitializationService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v2/cluster/{clusterId}/initialization")
public class PhysicalClusterInitializationV2Controller extends ApiController {

    private final PhysicalClusterInitializationService initializationService;

    public PhysicalClusterInitializationV2Controller(
                                                     PhysicalClusterInitializationService initializationService) {
        this.initializationService = initializationService;
    }

    @PostMapping("/start")
    public ApiResponse<PhysicalClusterInitializationResponse> start(@PathVariable Integer clusterId) {
        return ApiResponse.ok(initializationService.start(clusterId));
    }

    @GetMapping("/status")
    public ApiResponse<PhysicalClusterInitializationResponse> status(@PathVariable Integer clusterId) {
        return ApiResponse.ok(initializationService.getStatus(clusterId));
    }
}
