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
import com.datasophon.api.dto.v2.NodeLabelResponse;
import com.datasophon.api.service.ClusterNodeLabelService;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.ClusterNodeLabelEntity;

import java.util.List;
import java.util.stream.Collectors;

import lombok.Data;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * v2 节点标签管理接口（标签 CRUD + 分配）。
 */
@RestController
@RequestMapping("/v2/cluster/{clusterId}/host/label")
public class ClusterNodeLabelV2Controller extends ApiController {
    
    private final ClusterNodeLabelService nodeLabelService;
    
    public ClusterNodeLabelV2Controller(ClusterNodeLabelService nodeLabelService) {
        this.nodeLabelService = nodeLabelService;
    }
    
    /** 标签列表 */
    @GetMapping("/list")
    public ApiResponse<List<NodeLabelResponse>> list(@PathVariable Integer clusterId) {
        List<ClusterNodeLabelEntity> list = nodeLabelService.queryClusterNodeLabel(clusterId);
        return ApiResponse.ok(NodeLabelResponse.fromList(list));
    }
    
    /** 新建标签 */
    @PostMapping
    public ApiResponse<Void> save(@PathVariable Integer clusterId, @RequestParam String nodeLabel) {
        Result result = nodeLabelService.saveNodeLabel(clusterId, nodeLabel);
        if (result.isSuccess()) {
            return ApiResponse.ok();
        }
        return ApiResponse.fail(500, result.getMsg());
    }
    
    /** 删除标签 */
    @DeleteMapping("/{nodeLabelId}")
    public ApiResponse<Void> delete(@PathVariable Integer clusterId, @PathVariable Integer nodeLabelId) {
        Result result = nodeLabelService.deleteNodeLabel(nodeLabelId);
        if (result.isSuccess()) {
            return ApiResponse.ok();
        }
        return ApiResponse.fail(500, result.getMsg());
    }
    
    /** 分配标签给主机 */
    @PostMapping("/assign")
    public ApiResponse<Void> assign(@PathVariable Integer clusterId, @RequestBody AssignLabelRequest body) {
        // v1 service 接受逗号拼接字符串，在 v2 入口做适配
        String hostIdsStr = body.getHostIds().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        Result result = nodeLabelService.assignNodeLabel(body.getNodeLabelId(), hostIdsStr);
        if (result.isSuccess()) {
            return ApiResponse.ok();
        }
        return ApiResponse.fail(500, result.getMsg());
    }
    
    @Data
    public static class AssignLabelRequest {
        private Integer nodeLabelId;
        private List<Integer> hostIds;
    }
}
