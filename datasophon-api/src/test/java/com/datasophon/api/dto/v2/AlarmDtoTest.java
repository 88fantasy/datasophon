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

import com.datasophon.dao.entity.AlertGroupEntity;
import com.datasophon.dao.entity.ClusterAlertQuota;
import com.datasophon.dao.enums.AlertLevel;
import com.datasophon.dao.enums.QuotaState;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 纯 Java 单测（无 Spring），验证 alarm 域 DTO 的字段映射。
 */
class AlarmDtoTest {
    
    // ─── SaveAlertGroupRequest.toEntity() ────────────────────────────────────
    
    @Test
    void saveAlertGroupRequest_toEntity_mapsCorrectFields() {
        SaveAlertGroupRequest req = new SaveAlertGroupRequest();
        req.setAlertGroupName("测试告警组");
        req.setAlertGroupCategory("HDFS");
        
        AlertGroupEntity entity = req.toEntity(42);
        
        assertThat(entity.getAlertGroupName()).isEqualTo("测试告警组");
        assertThat(entity.getAlertGroupCategory()).isEqualTo("HDFS");
        assertThat(entity.getClusterId()).isEqualTo(42);
        assertThat(entity.getCreateTime()).isNotNull();
        assertThat(entity.getId()).isNull();
    }
    
    // ─── AlertGroupResponse.from() ────────────────────────────────────────────
    
    @Test
    void alertGroupResponse_from_allFieldsMapped() {
        Date now = new Date();
        AlertGroupEntity entity = new AlertGroupEntity();
        entity.setId(1);
        entity.setAlertGroupName("HDFS 告警组");
        entity.setAlertGroupCategory("HDFS");
        entity.setAlertQuotaNum(3);
        entity.setClusterId(10);
        entity.setCreateTime(now);
        
        AlertGroupResponse resp = AlertGroupResponse.from(entity);
        
        assertThat(resp.getId()).isEqualTo(1);
        assertThat(resp.getAlertGroupName()).isEqualTo("HDFS 告警组");
        assertThat(resp.getAlertGroupCategory()).isEqualTo("HDFS");
        assertThat(resp.getAlertQuotaNum()).isEqualTo(3);
        assertThat(resp.getClusterId()).isEqualTo(10);
        assertThat(resp.getCreateTime()).isEqualTo(now);
    }
    
    @Test
    void alertGroupResponse_fromList_nullInput_returnsEmptyList() {
        List<AlertGroupResponse> list = AlertGroupResponse.fromList(null);
        assertThat(list).isNotNull().isEmpty();
    }
    
    // ─── AlertGroupPageResponse.of() ─────────────────────────────────────────
    
    @Test
    void alertGroupPageResponse_of_setsTotalListAndCount() {
        AlertGroupResponse item = new AlertGroupResponse();
        item.setId(1);
        
        AlertGroupPageResponse page = AlertGroupPageResponse.of(List.of(item), 5L);
        
        assertThat(page.getTotalList()).hasSize(1);
        assertThat(page.getTotalCount()).isEqualTo(5L);
    }
    
    // ─── SaveAlertQuotaRequest.toEntity() ────────────────────────────────────
    
    @Test
    void saveAlertQuotaRequest_toEntity_mapsCorrectFields() {
        SaveAlertQuotaRequest req = new SaveAlertQuotaRequest();
        req.setAlertQuotaName("HDFS 指标");
        req.setAlertExpr("node_cpu_usage");
        req.setCompareMethod(">");
        req.setAlertThreshold(80L);
        req.setAlertLevel("warning");
        req.setAlertGroupId(5);
        req.setServiceRoleName("NameNode");
        req.setNoticeGroupId(1);
        req.setAlertTactic(1);
        req.setIntervalDuration(10);
        req.setTriggerDuration(30);
        req.setAlertAdvice("请检查节点");
        
        ClusterAlertQuota entity = req.toEntity();
        
        assertThat(entity.getAlertQuotaName()).isEqualTo("HDFS 指标");
        assertThat(entity.getAlertExpr()).isEqualTo("node_cpu_usage");
        assertThat(entity.getCompareMethod()).isEqualTo(">");
        assertThat(entity.getAlertThreshold()).isEqualTo(80L);
        assertThat(entity.getAlertLevel()).isEqualTo(AlertLevel.WARN);
        assertThat(entity.getAlertGroupId()).isEqualTo(5);
        assertThat(entity.getServiceRoleName()).isEqualTo("NameNode");
        assertThat(entity.getNoticeGroupId()).isEqualTo(1);
        assertThat(entity.getAlertTactic()).isEqualTo(1);
        assertThat(entity.getIntervalDuration()).isEqualTo(10);
        assertThat(entity.getTriggerDuration()).isEqualTo(30);
        assertThat(entity.getAlertAdvice()).isEqualTo("请检查节点");
        // quotaState 和 createTime 由 service 层设置，不在 request 中
        assertThat(entity.getQuotaState()).isNull();
        assertThat(entity.getId()).isNull();
    }
    
    @Test
    void saveAlertQuotaRequest_toEntity_exceptionLevel() {
        SaveAlertQuotaRequest req = new SaveAlertQuotaRequest();
        req.setAlertQuotaName("test");
        req.setAlertLevel("exception");
        
        ClusterAlertQuota entity = req.toEntity();
        
        assertThat(entity.getAlertLevel()).isEqualTo(AlertLevel.EXCEPTION);
    }
    
    @Test
    void saveAlertQuotaRequest_toEntity_nullAlertLevel() {
        SaveAlertQuotaRequest req = new SaveAlertQuotaRequest();
        req.setAlertQuotaName("test");
        req.setAlertLevel(null);
        
        ClusterAlertQuota entity = req.toEntity();
        
        assertThat(entity.getAlertLevel()).isNull();
    }
    
    // ─── UpdateAlertQuotaRequest.toEntity() ──────────────────────────────────
    
    @Test
    void updateAlertQuotaRequest_toEntity_setsIdAndWaitToUpdateState() {
        UpdateAlertQuotaRequest req = new UpdateAlertQuotaRequest();
        req.setId(99);
        req.setAlertQuotaName("updated name");
        req.setAlertLevel("warning");
        
        ClusterAlertQuota entity = req.toEntity();
        
        assertThat(entity.getId()).isEqualTo(99);
        assertThat(entity.getQuotaState()).isEqualTo(QuotaState.WAIT_TO_UPDATE);
        assertThat(entity.getAlertQuotaName()).isEqualTo("updated name");
    }
    
    // ─── AlertQuotaResponse.from() ────────────────────────────────────────────
    
    @Test
    void alertQuotaResponse_from_allFieldsMapped() {
        ClusterAlertQuota entity = new ClusterAlertQuota();
        entity.setId(7);
        entity.setAlertQuotaName("CPU 告警");
        entity.setAlertExpr("cpu_usage");
        entity.setCompareMethod(">");
        entity.setAlertThreshold(90L);
        entity.setAlertLevel(AlertLevel.EXCEPTION);
        entity.setAlertGroupId(2);
        entity.setAlertGroupName("YARN 组");
        entity.setServiceRoleName("ResourceManager");
        entity.setNoticeGroupId(1);
        entity.setAlertTactic(2);
        entity.setIntervalDuration(5);
        entity.setTriggerDuration(60);
        entity.setAlertAdvice("重启服务");
        entity.setQuotaState(QuotaState.RUNNING);
        entity.setQuotaStateCode(1);
        
        AlertQuotaResponse resp = AlertQuotaResponse.from(entity);
        
        assertThat(resp.getId()).isEqualTo(7);
        assertThat(resp.getAlertQuotaName()).isEqualTo("CPU 告警");
        assertThat(resp.getAlertExpr()).isEqualTo("cpu_usage");
        assertThat(resp.getCompareMethod()).isEqualTo(">");
        assertThat(resp.getAlertThreshold()).isEqualTo(90L);
        assertThat(resp.getAlertLevel()).isEqualTo("exception");
        assertThat(resp.getAlertGroupId()).isEqualTo(2);
        assertThat(resp.getAlertGroupName()).isEqualTo("YARN 组");
        assertThat(resp.getServiceRoleName()).isEqualTo("ResourceManager");
        assertThat(resp.getNoticeGroupId()).isEqualTo(1);
        assertThat(resp.getAlertTactic()).isEqualTo(2);
        assertThat(resp.getIntervalDuration()).isEqualTo(5);
        assertThat(resp.getTriggerDuration()).isEqualTo(60);
        assertThat(resp.getAlertAdvice()).isEqualTo("重启服务");
        assertThat(resp.getQuotaState()).isEqualTo("启用");
        assertThat(resp.getQuotaStateCode()).isEqualTo(1);
    }
    
    @Test
    void alertQuotaResponse_fromList_nullInput_returnsEmptyList() {
        List<AlertQuotaResponse> list = AlertQuotaResponse.fromList(null);
        assertThat(list).isNotNull().isEmpty();
    }
    
    // ─── AlertQuotaPageResponse.of() ─────────────────────────────────────────
    
    @Test
    void alertQuotaPageResponse_of_setsTotalListAndCount() {
        AlertQuotaResponse item = new AlertQuotaResponse();
        item.setId(1);
        
        AlertQuotaPageResponse page = AlertQuotaPageResponse.of(List.of(item), 100L);
        
        assertThat(page.getTotalList()).hasSize(1);
        assertThat(page.getTotalCount()).isEqualTo(100L);
    }
}
