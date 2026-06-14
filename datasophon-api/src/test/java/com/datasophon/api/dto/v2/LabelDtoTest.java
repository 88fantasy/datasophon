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

import com.datasophon.dao.entity.ClusterNodeLabelEntity;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 纯 Java 单测（无 Spring），验证 NodeLabelResponse 的字段映射。
 */
class LabelDtoTest {
    
    // ─── NodeLabelResponse.from() ─────────────────────────────────────────────
    
    @Test
    void from_mapsAllThreeFields() {
        ClusterNodeLabelEntity entity = new ClusterNodeLabelEntity();
        entity.setId(1);
        entity.setClusterId(10);
        entity.setNodeLabel("gpu");
        
        NodeLabelResponse resp = NodeLabelResponse.from(entity);
        
        assertThat(resp.getId()).isEqualTo(1);
        assertThat(resp.getClusterId()).isEqualTo(10);
        assertThat(resp.getNodeLabel()).isEqualTo("gpu");
    }
    
    // ─── NodeLabelResponse.fromList() ────────────────────────────────────────
    
    @Test
    void fromList_emptyList_returnsEmptyList() {
        List<NodeLabelResponse> result = NodeLabelResponse.fromList(Collections.emptyList());
        
        assertThat(result).isNotNull().isEmpty();
    }
    
    @Test
    void fromList_nullList_returnsEmptyList() {
        List<NodeLabelResponse> result = NodeLabelResponse.fromList(null);
        
        assertThat(result).isNotNull().isEmpty();
    }
    
    @Test
    void fromList_mapsAllEntities() {
        ClusterNodeLabelEntity e1 = new ClusterNodeLabelEntity();
        e1.setId(1);
        e1.setClusterId(10);
        e1.setNodeLabel("gpu");
        
        ClusterNodeLabelEntity e2 = new ClusterNodeLabelEntity();
        e2.setId(2);
        e2.setClusterId(10);
        e2.setNodeLabel("ssd");
        
        List<NodeLabelResponse> result = NodeLabelResponse.fromList(List.of(e1, e2));
        
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getNodeLabel()).isEqualTo("gpu");
        assertThat(result.get(1).getId()).isEqualTo(2);
        assertThat(result.get(1).getNodeLabel()).isEqualTo("ssd");
    }
}
