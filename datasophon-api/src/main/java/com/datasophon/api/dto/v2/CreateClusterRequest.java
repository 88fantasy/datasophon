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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 新建集群请求体。只包含前端表单实际提交的 4 个字段。 */
@Data
public class CreateClusterRequest {
    
    @NotBlank
    private String clusterName;
    
    @NotBlank
    private String clusterCode;
    
    /** 框架版本 ID；由 controller 负责解析为 clusterFrame / frameVersion 后回填。 */
    @NotNull
    private Integer frameId;
    
    @NotNull
    private ClusterArchType archType;
    
    /** 转为 {@link ClusterInfoEntity}；clusterFrame / frameVersion 由 controller 解析 frameId 后回填。 */
    public ClusterInfoEntity toEntity() {
        ClusterInfoEntity e = new ClusterInfoEntity();
        e.setClusterName(clusterName);
        e.setClusterCode(clusterCode);
        e.setFrameId(frameId);
        e.setArchType(archType);
        return e;
    }
}
