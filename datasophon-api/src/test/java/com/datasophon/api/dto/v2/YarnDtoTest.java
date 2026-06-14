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

import com.datasophon.dao.entity.ClusterYarnQueue;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 纯 Java 单测（无 Spring），验证 yarn 域 DTO 的字段映射。
 */
class YarnDtoTest {
    
    // ─── SaveYarnQueueRequest.toEntity() ─────────────────────────────────────
    
    @Test
    void saveRequest_toEntity_clusterId_and_createTime_set() {
        SaveYarnQueueRequest req = new SaveYarnQueueRequest();
        req.setQueueName("test-queue");
        req.setMinCore(1);
        req.setMinMem(2);
        req.setMaxCore(4);
        req.setMaxMem(8);
        req.setAppNum(10);
        req.setSchedulePolicy("fair");
        req.setWeight(1);
        req.setAllowPreemption(1);
        req.setAmShare("0.5");
        
        ClusterYarnQueue entity = req.toEntity(42);
        
        assertThat(entity.getQueueName()).isEqualTo("test-queue");
        assertThat(entity.getMinCore()).isEqualTo(1);
        assertThat(entity.getMinMem()).isEqualTo(2);
        assertThat(entity.getMaxCore()).isEqualTo(4);
        assertThat(entity.getMaxMem()).isEqualTo(8);
        assertThat(entity.getAppNum()).isEqualTo(10);
        assertThat(entity.getSchedulePolicy()).isEqualTo("fair");
        assertThat(entity.getWeight()).isEqualTo(1);
        assertThat(entity.getAllowPreemption()).isEqualTo(1);
        assertThat(entity.getAmShare()).isEqualTo("0.5");
        assertThat(entity.getClusterId()).isEqualTo(42);
        assertThat(entity.getCreateTime()).isNotNull();
        assertThat(entity.getId()).isNull();
    }
    
    // ─── UpdateYarnQueueRequest.toEntity() ───────────────────────────────────
    
    @Test
    void updateRequest_toEntity_id_set_clusterId_not_set() {
        UpdateYarnQueueRequest req = new UpdateYarnQueueRequest();
        req.setId(99);
        req.setQueueName("updated-queue");
        req.setMinCore(2);
        req.setMinMem(4);
        req.setMaxCore(8);
        req.setMaxMem(16);
        req.setAppNum(5);
        req.setSchedulePolicy("fifo");
        req.setWeight(2);
        req.setAllowPreemption(2);
        req.setAmShare("0.3");
        
        ClusterYarnQueue entity = req.toEntity();
        
        assertThat(entity.getId()).isEqualTo(99);
        assertThat(entity.getQueueName()).isEqualTo("updated-queue");
        assertThat(entity.getSchedulePolicy()).isEqualTo("fifo");
        assertThat(entity.getClusterId()).isNull();
        assertThat(entity.getCreateTime()).isNull();
    }
    
    // ─── YarnQueueResponse.from() ─────────────────────────────────────────────
    
    @Test
    void response_from_allFieldsMapped() {
        Date now = new Date();
        ClusterYarnQueue entity = new ClusterYarnQueue();
        entity.setId(7);
        entity.setQueueName("prod-queue");
        entity.setMinCore(1);
        entity.setMinMem(2);
        entity.setMaxCore(8);
        entity.setMaxMem(16);
        entity.setAppNum(20);
        entity.setSchedulePolicy("drf");
        entity.setWeight(3);
        entity.setAllowPreemption(1);
        entity.setAmShare("0.8");
        entity.setClusterId(5);
        entity.setCreateTime(now);
        
        YarnQueueResponse resp = YarnQueueResponse.from(entity);
        
        assertThat(resp.getId()).isEqualTo(7);
        assertThat(resp.getQueueName()).isEqualTo("prod-queue");
        assertThat(resp.getMinCore()).isEqualTo(1);
        assertThat(resp.getMinMem()).isEqualTo(2);
        assertThat(resp.getMaxCore()).isEqualTo(8);
        assertThat(resp.getMaxMem()).isEqualTo(16);
        assertThat(resp.getAppNum()).isEqualTo(20);
        assertThat(resp.getSchedulePolicy()).isEqualTo("drf");
        assertThat(resp.getWeight()).isEqualTo(3);
        assertThat(resp.getAllowPreemption()).isEqualTo(1);
        assertThat(resp.getAmShare()).isEqualTo("0.8");
        assertThat(resp.getClusterId()).isEqualTo(5);
        assertThat(resp.getCreateTime()).isEqualTo(now);
    }
    
    @Test
    void response_fromList_emptyList_safe() {
        List<YarnQueueResponse> result = YarnQueueResponse.fromList(Collections.emptyList());
        assertThat(result).isNotNull().isEmpty();
    }
    
    @Test
    void response_fromList_null_safe() {
        List<YarnQueueResponse> result = YarnQueueResponse.fromList(null);
        assertThat(result).isNotNull().isEmpty();
    }
    
    // ─── YarnQueuePageResponse.of() ──────────────────────────────────────────
    
    @Test
    void pageResponse_of_setsDataAndTotal() {
        YarnQueueResponse item = new YarnQueueResponse();
        item.setId(1);
        item.setQueueName("q1");
        
        YarnQueuePageResponse page = YarnQueuePageResponse.of(List.of(item), 100L);
        
        assertThat(page.getData()).hasSize(1);
        assertThat(page.getData().get(0).getQueueName()).isEqualTo("q1");
        assertThat(page.getTotal()).isEqualTo(100L);
    }
}
