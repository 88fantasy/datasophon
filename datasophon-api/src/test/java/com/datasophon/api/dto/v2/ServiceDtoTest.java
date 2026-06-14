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

import com.datasophon.dao.entity.ClusterServiceInstanceEntity;
import com.datasophon.dao.entity.ClusterServiceInstanceRoleGroup;
import com.datasophon.dao.enums.NeedRestart;
import com.datasophon.dao.enums.ServiceState;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 纯 Java 单测（无 Spring），验证 service 域 v2 DTO 的字段映射。
 */
class ServiceDtoTest {
    
    // ─── ServiceInstanceResponse.from() ──────────────────────────────────────
    
    @Test
    void serviceInstanceResponse_from_serviceState_isEnumName() {
        ClusterServiceInstanceEntity entity = new ClusterServiceInstanceEntity();
        entity.setId(1);
        entity.setClusterId(10);
        entity.setServiceName("HDFS");
        entity.setLabel("HDFS");
        entity.setServiceState(ServiceState.RUNNING);
        entity.setNeedRestart(NeedRestart.NO);
        
        ServiceInstanceResponse resp = ServiceInstanceResponse.from(entity);
        
        // serviceState 必须是枚举英文名，不能是中文描述
        assertThat(resp.getServiceState()).isEqualTo("RUNNING");
    }
    
    @Test
    void serviceInstanceResponse_from_serviceStateCode_isIntegerValue() {
        ClusterServiceInstanceEntity entity = new ClusterServiceInstanceEntity();
        entity.setId(2);
        entity.setServiceState(ServiceState.EXISTS_EXCEPTION);
        entity.setNeedRestart(NeedRestart.NO);
        
        ServiceInstanceResponse resp = ServiceInstanceResponse.from(entity);
        
        assertThat(resp.getServiceStateCode()).isEqualTo(4);
    }
    
    @Test
    void serviceInstanceResponse_from_needRestart_yesIsTrue() {
        ClusterServiceInstanceEntity entity = new ClusterServiceInstanceEntity();
        entity.setId(3);
        entity.setServiceState(ServiceState.RUNNING);
        entity.setNeedRestart(NeedRestart.YES);
        
        ServiceInstanceResponse resp = ServiceInstanceResponse.from(entity);
        
        assertThat(resp.getNeedRestart()).isTrue();
    }
    
    @Test
    void serviceInstanceResponse_from_needRestart_noIsFalse() {
        ClusterServiceInstanceEntity entity = new ClusterServiceInstanceEntity();
        entity.setId(4);
        entity.setServiceState(ServiceState.RUNNING);
        entity.setNeedRestart(NeedRestart.NO);
        
        ServiceInstanceResponse resp = ServiceInstanceResponse.from(entity);
        
        assertThat(resp.getNeedRestart()).isFalse();
    }
    
    @Test
    void serviceInstanceResponse_from_extraFields_mappedFromEntity() {
        ClusterServiceInstanceEntity entity = new ClusterServiceInstanceEntity();
        entity.setId(5);
        entity.setServiceState(ServiceState.WAIT_INSTALL);
        entity.setNeedRestart(NeedRestart.NO);
        entity.setDashboardUrl("http://grafana/d/hdfs");
        entity.setAlertNum(3);
        entity.setCatalog("MIDDLEWARE");
        entity.setFrameServiceId(42);
        entity.setSortNum(1);
        
        ServiceInstanceResponse resp = ServiceInstanceResponse.from(entity);
        
        assertThat(resp.getDashboardUrl()).isEqualTo("http://grafana/d/hdfs");
        assertThat(resp.getAlertNum()).isEqualTo(3);
        assertThat(resp.getCatalog()).isEqualTo("MIDDLEWARE");
        assertThat(resp.getFrameServiceId()).isEqualTo(42);
        assertThat(resp.getSortNum()).isEqualTo(1);
    }
    
    @Test
    void serviceInstanceResponse_from_null_returnsNull() {
        assertThat(ServiceInstanceResponse.from(null)).isNull();
    }
    
    @Test
    void serviceInstanceResponse_fromList_emptyList_doesNotNPE() {
        List<ServiceInstanceResponse> result = ServiceInstanceResponse.fromList(List.of());
        assertThat(result).isEmpty();
    }
    
    @Test
    void serviceInstanceResponse_fromList_null_returnsEmpty() {
        List<ServiceInstanceResponse> result = ServiceInstanceResponse.fromList(null);
        assertThat(result).isEmpty();
    }
    
    @Test
    void serviceInstanceResponse_fromList_mapsAll() {
        ClusterServiceInstanceEntity e1 = new ClusterServiceInstanceEntity();
        e1.setId(1);
        e1.setServiceState(ServiceState.RUNNING);
        e1.setNeedRestart(NeedRestart.NO);
        
        ClusterServiceInstanceEntity e2 = new ClusterServiceInstanceEntity();
        e2.setId(2);
        e2.setServiceState(ServiceState.EXISTS_ALARM);
        e2.setNeedRestart(NeedRestart.YES);
        
        List<ServiceInstanceResponse> list = ServiceInstanceResponse.fromList(List.of(e1, e2));
        
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getServiceState()).isEqualTo("RUNNING");
        assertThat(list.get(1).getServiceState()).isEqualTo("EXISTS_ALARM");
        assertThat(list.get(1).getNeedRestart()).isTrue();
    }
    
    // ─── RoleGroupResponse.from() ─────────────────────────────────────────────
    
    @Test
    void roleGroupResponse_from_mapsCorrectly() {
        ClusterServiceInstanceRoleGroup entity = new ClusterServiceInstanceRoleGroup();
        entity.setId(10);
        entity.setRoleGroupName("default");
        entity.setServiceInstanceId(5);
        
        RoleGroupResponse resp = RoleGroupResponse.from(entity);
        
        assertThat(resp.getId()).isEqualTo(10);
        assertThat(resp.getRoleGroupName()).isEqualTo("default");
        assertThat(resp.getServiceInstanceId()).isEqualTo(5);
    }
    
    @Test
    void roleGroupResponse_from_null_returnsNull() {
        assertThat(RoleGroupResponse.from(null)).isNull();
    }
    
    @Test
    void roleGroupResponse_fromList_emptyList_doesNotNPE() {
        List<RoleGroupResponse> result = RoleGroupResponse.fromList(List.of());
        assertThat(result).isEmpty();
    }
    
    @Test
    void roleGroupResponse_fromList_null_returnsEmpty() {
        List<RoleGroupResponse> result = RoleGroupResponse.fromList(null);
        assertThat(result).isEmpty();
    }
    
    @Test
    void roleGroupResponse_fromList_mapsAll() {
        ClusterServiceInstanceRoleGroup e1 = new ClusterServiceInstanceRoleGroup();
        e1.setId(1);
        e1.setRoleGroupName("group-a");
        e1.setServiceInstanceId(100);
        
        ClusterServiceInstanceRoleGroup e2 = new ClusterServiceInstanceRoleGroup();
        e2.setId(2);
        e2.setRoleGroupName("group-b");
        e2.setServiceInstanceId(100);
        
        List<RoleGroupResponse> list = RoleGroupResponse.fromList(List.of(e1, e2));
        
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getRoleGroupName()).isEqualTo("group-a");
        assertThat(list.get(1).getRoleGroupName()).isEqualTo("group-b");
    }
}
