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

import com.datasophon.dao.entity.dag.DagDefinitionEntity;
import com.datasophon.dao.enums.dag.DagStatus;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;

/**
 * 命令历史条目响应体，对应前端 {@code DagCommand} 接口。
 * 从 {@link DagDefinitionEntity} 映射，避免泄漏 MyBatis-Plus IPage 与 DAO 实体。
 */
@Data
public class DagCommandResponse {

    private String id;
    private Integer clusterId;
    private String dagName;
    private String description;
    /** 序列化为枚举名称字符串（如 {@code "PENDING"}），与 TS {@code DagStatus} 匹配。 */
    private DagStatus status;
    private LocalDateTime createdTime;
    private LocalDateTime startedTime;
    private LocalDateTime completedTime;

    public static DagCommandResponse from(DagDefinitionEntity entity) {
        DagCommandResponse r = new DagCommandResponse();
        r.setId(entity.getId());
        r.setClusterId(entity.getClusterId());
        r.setDagName(entity.getDagName());
        r.setDescription(entity.getDescription());
        r.setStatus(entity.getStatus());
        r.setCreatedTime(entity.getCreatedTime());
        r.setStartedTime(entity.getStartedTime());
        r.setCompletedTime(entity.getCompletedTime());
        return r;
    }

    public static List<DagCommandResponse> fromList(List<DagDefinitionEntity> entities) {
        return entities.stream().map(DagCommandResponse::from).toList();
    }
}
