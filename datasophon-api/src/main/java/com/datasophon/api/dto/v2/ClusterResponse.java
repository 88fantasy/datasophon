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

import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.enums.ClusterArchType;
import com.datasophon.dao.enums.ClusterState;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import lombok.Data;

/**
 * 集群响应体。持有轻量枚举（序列化形状与原实体一致），
 * 用 {@link UserResponse} 替代 {@link com.datasophon.dao.entity.UserInfoEntity}，
 * 避免 password 等敏感字段泄漏。
 */
@Data
public class ClusterResponse {
    
    private Integer id;
    private String clusterName;
    private String clusterCode;
    /** 框架代码，如 {@code "BIGDATA"}。 */
    private String clusterFrame;
    private String frameVersion;
    private Integer frameId;
    /** 序列化为中文 label（{@code @JsonValue} 在枚举上），与原实体 wire 形状一致。 */
    private ClusterState clusterState;
    private Integer clusterStateCode;
    private ClusterArchType archType;
    /** 只含 id + username，不含 password 等敏感字段。 */
    private List<UserResponse> clusterManagerList;
    
    public static ClusterResponse from(ClusterInfoEntity entity) {
        ClusterResponse r = new ClusterResponse();
        r.setId(entity.getId());
        r.setClusterName(entity.getClusterName());
        r.setClusterCode(entity.getClusterCode());
        r.setClusterFrame(entity.getClusterFrame());
        r.setFrameVersion(entity.getFrameVersion());
        r.setFrameId(entity.getFrameId());
        r.setClusterState(entity.getClusterState());
        r.setClusterStateCode(entity.getClusterStateCode());
        r.setArchType(entity.getArchType());
        r.setClusterManagerList(
                entity.getClusterManagerList() == null
                        ? Collections.emptyList()
                        : entity.getClusterManagerList().stream()
                                .map(UserResponse::from)
                                .collect(Collectors.toList()));
        return r;
    }
    
    public static List<ClusterResponse> fromList(List<ClusterInfoEntity> entities) {
        return entities.stream().map(ClusterResponse::from).collect(Collectors.toList());
    }
}
