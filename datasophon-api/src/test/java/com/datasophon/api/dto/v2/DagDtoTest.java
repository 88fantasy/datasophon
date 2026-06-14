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

import static org.assertj.core.api.Assertions.assertThat;

import com.datasophon.dao.entity.dag.DagDefinitionEntity;
import com.datasophon.dao.enums.dag.DagStatus;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * 纯 Java 单测（无 Spring），验证 dag 域 DTO 的字段映射与 Jackson 序列化形状。
 */
class DagDtoTest {
    
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    
    // ─── DagCommandResponse.from() ───────────────────────────────────────────
    
    @Test
    void dagCommandResponse_from_mapsAllFields() {
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
        DagDefinitionEntity entity = new DagDefinitionEntity();
        entity.setId("dag-001");
        entity.setClusterId(10);
        entity.setDagName("安装 HDFS");
        entity.setDescription("初始化 HDFS 集群");
        entity.setStatus(DagStatus.RUNNING);
        entity.setCreatedTime(now);
        entity.setStartedTime(now.plusMinutes(1));
        entity.setCompletedTime(null);
        
        DagCommandResponse resp = DagCommandResponse.from(entity);
        
        assertThat(resp.getId()).isEqualTo("dag-001");
        assertThat(resp.getClusterId()).isEqualTo(10);
        assertThat(resp.getDagName()).isEqualTo("安装 HDFS");
        assertThat(resp.getDescription()).isEqualTo("初始化 HDFS 集群");
        assertThat(resp.getStatus()).isEqualTo(DagStatus.RUNNING);
        assertThat(resp.getCreatedTime()).isEqualTo(now);
        assertThat(resp.getStartedTime()).isEqualTo(now.plusMinutes(1));
        assertThat(resp.getCompletedTime()).isNull();
    }
    
    @Test
    void dagCommandResponse_status_serializesAsEnumName() throws Exception {
        DagDefinitionEntity entity = new DagDefinitionEntity();
        entity.setId("dag-002");
        entity.setClusterId(1);
        entity.setStatus(DagStatus.SUCCESS);
        
        DagCommandResponse resp = DagCommandResponse.from(entity);
        String json = mapper.writeValueAsString(resp);
        
        // DagStatus 无 @JsonValue，序列化为枚举名称字符串，与 TS DagStatus 匹配
        assertThat(json).contains("\"status\":\"SUCCESS\"");
    }
    
    @Test
    void dagCommandResponse_fromList_preservesOrder() {
        DagDefinitionEntity e1 = new DagDefinitionEntity();
        e1.setId("a");
        e1.setStatus(DagStatus.PENDING);
        
        DagDefinitionEntity e2 = new DagDefinitionEntity();
        e2.setId("b");
        e2.setStatus(DagStatus.FAILED);
        
        List<DagCommandResponse> list = DagCommandResponse.fromList(List.of(e1, e2));
        
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getId()).isEqualTo("a");
        assertThat(list.get(1).getId()).isEqualTo("b");
    }
    
    // ─── DagCommandPageResponse.of() ─────────────────────────────────────────
    
    @Test
    void dagCommandPageResponse_of_recordsAndTotal() {
        DagDefinitionEntity entity = new DagDefinitionEntity();
        entity.setId("dag-003");
        entity.setClusterId(5);
        entity.setDagName("重启 YARN");
        entity.setStatus(DagStatus.CANCEL);
        
        IPage<DagDefinitionEntity> page = new Page<>(1, 20, 42L);
        page.setRecords(List.of(entity));
        
        DagCommandPageResponse resp = DagCommandPageResponse.of(page);
        
        assertThat(resp.getTotal()).isEqualTo(42L);
        assertThat(resp.getRecords()).hasSize(1);
        assertThat(resp.getRecords().get(0).getId()).isEqualTo("dag-003");
        assertThat(resp.getRecords().get(0).getDagName()).isEqualTo("重启 YARN");
        assertThat(resp.getRecords().get(0).getStatus()).isEqualTo(DagStatus.CANCEL);
    }
    
    @Test
    void dagCommandPageResponse_emptyPage_returnsEmptyRecords() {
        IPage<DagDefinitionEntity> page = new Page<>(1, 20, 0L);
        page.setRecords(List.of());
        
        DagCommandPageResponse resp = DagCommandPageResponse.of(page);
        
        assertThat(resp.getTotal()).isEqualTo(0L);
        assertThat(resp.getRecords()).isEmpty();
    }
}
