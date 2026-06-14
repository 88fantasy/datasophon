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

package com.datasophon.api.dto.v2;

import com.datasophon.dao.entity.ClusterNodeLabelEntity;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import lombok.Data;

/**
 * 节点标签响应 DTO（v2 API 专用）。
 */
@Data
public class NodeLabelResponse {
    
    private Integer id;
    private Integer clusterId;
    private String nodeLabel;
    
    public static NodeLabelResponse from(ClusterNodeLabelEntity entity) {
        NodeLabelResponse resp = new NodeLabelResponse();
        resp.setId(entity.getId());
        resp.setClusterId(entity.getClusterId());
        resp.setNodeLabel(entity.getNodeLabel());
        return resp;
    }
    
    public static List<NodeLabelResponse> fromList(List<ClusterNodeLabelEntity> entities) {
        if (entities == null) {
            return Collections.emptyList();
        }
        return entities.stream().map(NodeLabelResponse::from).collect(Collectors.toList());
    }
}
