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

import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.ServiceRoleState;
import com.datasophon.domain.host.enums.HostState;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 纯 Java 单测（无 Spring），验证 v2 host DTO 的字段映射与 Jackson 序列化形状。
 */
class HostDtoTest {
    
    private final ObjectMapper mapper = new ObjectMapper();
    
    // ─── HostResponse.from() ──────────────────────────────────────────────────
    
    @Test
    void hostResponse_from_doesNotContainSshPasswordOrSshUser() {
        ClusterHostDO entity = new ClusterHostDO();
        entity.setId(1);
        entity.setHostname("node1");
        entity.setIp("192.168.1.1");
        entity.setSshPort(22);
        entity.setSshUser("root");
        entity.setSshPassword("super-secret");
        entity.setHostState(HostState.RUNNING);
        
        HostResponse resp = HostResponse.from(entity);
        
        // 验证响应体本身没有 sshPassword / sshUser 字段
        List<String> fieldNames = Arrays.stream(HostResponse.class.getDeclaredFields())
                .map(Field::getName)
                .toList();
        assertThat(fieldNames).doesNotContain("sshPassword", "sshUser", "managed");
        
        // 核心字段正常映射
        assertThat(resp.getId()).isEqualTo(1);
        assertThat(resp.getHostname()).isEqualTo("node1");
        assertThat(resp.getIp()).isEqualTo("192.168.1.1");
        assertThat(resp.getSshPort()).isEqualTo(22);
    }
    
    @Test
    void hostResponse_from_hostStateIsInteger() {
        ClusterHostDO entity = new ClusterHostDO();
        entity.setId(2);
        entity.setHostname("node2");
        entity.setIp("10.0.0.1");
        entity.setHostState(HostState.RUNNING);
        
        HostResponse resp = HostResponse.from(entity);
        
        // hostState 必须是整数 1，而非枚举或中文描述
        assertThat(resp.getHostState()).isEqualTo(1);
    }
    
    @Test
    void hostResponse_from_hostStateOffline() {
        ClusterHostDO entity = new ClusterHostDO();
        entity.setId(3);
        entity.setHostname("node3");
        entity.setIp("10.0.0.2");
        entity.setHostState(HostState.OFFLINE);
        
        HostResponse resp = HostResponse.from(entity);
        
        assertThat(resp.getHostState()).isEqualTo(2);
    }
    
    @Test
    void hostResponse_from_hostStateExistsAlarm() {
        ClusterHostDO entity = new ClusterHostDO();
        entity.setId(4);
        entity.setHostname("node4");
        entity.setIp("10.0.0.3");
        entity.setHostState(HostState.EXISTS_ALARM);
        
        HostResponse resp = HostResponse.from(entity);
        
        assertThat(resp.getHostState()).isEqualTo(3);
    }
    
    @Test
    void hostResponse_from_nullHostState_returnsNullInteger() {
        ClusterHostDO entity = new ClusterHostDO();
        entity.setId(5);
        entity.setHostname("node5");
        entity.setIp("10.0.0.4");
        entity.setHostState(null);
        
        HostResponse resp = HostResponse.from(entity);
        
        assertThat(resp.getHostState()).isNull();
    }
    
    @Test
    void hostResponse_jackson_hostStateSerializesAsInteger() throws Exception {
        ClusterHostDO entity = new ClusterHostDO();
        entity.setId(6);
        entity.setHostname("node6");
        entity.setIp("10.0.0.5");
        entity.setHostState(HostState.RUNNING);
        
        HostResponse resp = HostResponse.from(entity);
        String json = mapper.writeValueAsString(resp);
        
        // 必须序列化为整数 1，不能是中文字符串
        assertThat(json).contains("\"hostState\":1");
        assertThat(json).doesNotContain("正在运行");
        // 敏感字段不能出现在 JSON 中
        assertThat(json).doesNotContain("sshPassword");
        assertThat(json).doesNotContain("sshUser");
    }
    
    @Test
    void hostResponse_fromList_mapsAllEntities() {
        ClusterHostDO e1 = new ClusterHostDO();
        e1.setId(1);
        e1.setHostname("node1");
        e1.setIp("10.0.0.1");
        e1.setHostState(HostState.RUNNING);
        
        ClusterHostDO e2 = new ClusterHostDO();
        e2.setId(2);
        e2.setHostname("node2");
        e2.setIp("10.0.0.2");
        e2.setHostState(HostState.OFFLINE);
        
        List<HostResponse> list = HostResponse.fromList(List.of(e1, e2));
        
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getHostname()).isEqualTo("node1");
        assertThat(list.get(1).getHostState()).isEqualTo(2);
    }
    
    // ─── HostPageResponse.of() ────────────────────────────────────────────────
    
    @Test
    void hostPageResponse_of_setsRecordsAndTotal() {
        ClusterHostDO entity = new ClusterHostDO();
        entity.setId(1);
        entity.setHostname("node1");
        entity.setIp("10.0.0.1");
        entity.setHostState(HostState.RUNNING);
        
        List<HostResponse> records = HostResponse.fromList(List.of(entity));
        HostPageResponse page = HostPageResponse.of(records, 100L);
        
        assertThat(page.getTotal()).isEqualTo(100L);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getHostname()).isEqualTo("node1");
    }
    
    // ─── HostRoleResponse.from() ──────────────────────────────────────────────
    
    @Test
    void hostRoleResponse_from_mapsCorrectly() {
        ClusterServiceRoleInstanceEntity entity = new ClusterServiceRoleInstanceEntity();
        entity.setId(10);
        entity.setServiceRoleName("NameNode");
        entity.setHostname("node1");
        entity.setServiceRoleState(ServiceRoleState.RUNNING);
        entity.setServiceRoleStateCode(ServiceRoleState.RUNNING.getValue());
        entity.setServiceId(5);
        entity.setClusterId(1);
        
        HostRoleResponse resp = HostRoleResponse.from(entity);
        
        assertThat(resp.getId()).isEqualTo(10);
        assertThat(resp.getServiceRoleName()).isEqualTo("NameNode");
        assertThat(resp.getHostname()).isEqualTo("node1");
        assertThat(resp.getServiceRoleStateCode()).isEqualTo(1);
        assertThat(resp.getServiceId()).isEqualTo(5);
        assertThat(resp.getClusterId()).isEqualTo(1);
    }
    
    @Test
    void hostRoleResponse_fromList_mapsAllEntities() {
        ClusterServiceRoleInstanceEntity e1 = new ClusterServiceRoleInstanceEntity();
        e1.setId(1);
        e1.setServiceRoleName("NameNode");
        e1.setServiceRoleStateCode(ServiceRoleState.RUNNING.getValue());
        
        ClusterServiceRoleInstanceEntity e2 = new ClusterServiceRoleInstanceEntity();
        e2.setId(2);
        e2.setServiceRoleName("DataNode");
        e2.setServiceRoleStateCode(ServiceRoleState.STOP.getValue());
        
        List<HostRoleResponse> list = HostRoleResponse.fromList(List.of(e1, e2));
        
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getServiceRoleName()).isEqualTo("NameNode");
        assertThat(list.get(1).getServiceRoleStateCode()).isEqualTo(2);
    }
}
