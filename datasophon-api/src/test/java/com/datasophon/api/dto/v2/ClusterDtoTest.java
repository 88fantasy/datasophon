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

import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.FrameInfoEntity;
import com.datasophon.dao.entity.UserInfoEntity;
import com.datasophon.dao.enums.ClusterArchType;
import com.datasophon.dao.enums.ClusterState;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 纯 Java 单测（无 Spring），验证 v2 cluster DTO 的字段映射与 Jackson 序列化形状。
 */
class ClusterDtoTest {
    
    private final ObjectMapper mapper = new ObjectMapper();
    
    // ─── CreateClusterRequest.toEntity() ──────────────────────────────────────
    
    @Test
    void createRequest_toEntity_mapsOnly4Fields() {
        CreateClusterRequest req = new CreateClusterRequest();
        req.setClusterName("MyCluster");
        req.setClusterCode("my_cluster");
        req.setFrameId(3);
        req.setArchType(ClusterArchType.physical);
        
        ClusterInfoEntity entity = req.toEntity();
        
        assertThat(entity.getClusterName()).isEqualTo("MyCluster");
        assertThat(entity.getClusterCode()).isEqualTo("my_cluster");
        assertThat(entity.getFrameId()).isEqualTo(3);
        assertThat(entity.getArchType()).isEqualTo(ClusterArchType.physical);
        // clusterFrame / frameVersion 由 controller 回填，toEntity 不设置
        assertThat(entity.getClusterFrame()).isNull();
        assertThat(entity.getFrameVersion()).isNull();
        // 业务字段由 service 设置，不由请求体携带
        assertThat(entity.getClusterState()).isNull();
        assertThat(entity.getId()).isNull();
    }
    
    // ─── UpdateClusterRequest.toEntity() ──────────────────────────────────────
    
    @Test
    void updateRequest_toEntity_bindsIdNameCode() {
        UpdateClusterRequest req = new UpdateClusterRequest();
        req.setClusterName("Updated");
        req.setClusterCode("updated_code");
        
        ClusterInfoEntity entity = req.toEntity(42);
        
        assertThat(entity.getId()).isEqualTo(42);
        assertThat(entity.getClusterName()).isEqualTo("Updated");
        assertThat(entity.getClusterCode()).isEqualTo("updated_code");
    }
    
    // ─── ClusterResponse.from() ───────────────────────────────────────────────
    
    @Test
    void clusterResponse_from_mapsAllFields() {
        UserInfoEntity user = new UserInfoEntity();
        user.setId(10);
        user.setUsername("alice");
        user.setPassword("secret-hash");
        
        ClusterInfoEntity entity = new ClusterInfoEntity();
        entity.setId(1);
        entity.setClusterName("Prod");
        entity.setClusterCode("prod");
        entity.setClusterFrame("BIGDATA");
        entity.setFrameVersion("3.3.4");
        entity.setFrameId(2);
        entity.setClusterState(ClusterState.RUNNING);
        entity.setClusterStateCode(2);
        entity.setArchType(ClusterArchType.physical);
        entity.setClusterManagerList(List.of(user));
        
        ClusterResponse resp = ClusterResponse.from(entity);
        
        assertThat(resp.getId()).isEqualTo(1);
        assertThat(resp.getClusterName()).isEqualTo("Prod");
        assertThat(resp.getClusterFrame()).isEqualTo("BIGDATA");
        assertThat(resp.getClusterState()).isEqualTo(ClusterState.RUNNING);
        assertThat(resp.getArchType()).isEqualTo(ClusterArchType.physical);
        // 管理员只含 id + username，不含 password
        assertThat(resp.getClusterManagerList()).hasSize(1);
        UserResponse userResp = resp.getClusterManagerList().get(0);
        assertThat(userResp.getId()).isEqualTo(10);
        assertThat(userResp.getUsername()).isEqualTo("alice");
    }
    
    @Test
    void clusterResponse_jackson_clusterStateSerializesAsChineseLabel() throws Exception {
        ClusterInfoEntity entity = new ClusterInfoEntity();
        entity.setId(1);
        entity.setClusterName("Test");
        entity.setClusterCode("test");
        entity.setClusterState(ClusterState.RUNNING);
        entity.setArchType(ClusterArchType.physical);
        
        ClusterResponse resp = ClusterResponse.from(entity);
        String json = mapper.writeValueAsString(resp);
        
        // ClusterState @JsonValue 序列化为中文 label，与原实体 wire 形状一致
        assertThat(json).contains("\"clusterState\":\"正在运行\"");
        // ClusterArchType 按 name 序列化
        assertThat(json).contains("\"archType\":\"physical\"");
    }
    
    @Test
    void clusterResponse_nullManagerList_returnsEmptyList() {
        ClusterInfoEntity entity = new ClusterInfoEntity();
        entity.setId(2);
        entity.setClusterName("Empty");
        entity.setClusterCode("empty");
        entity.setClusterManagerList(null);
        
        ClusterResponse resp = ClusterResponse.from(entity);
        
        assertThat(resp.getClusterManagerList()).isNotNull().isEmpty();
    }
    
    // ─── UserResponse.from() ──────────────────────────────────────────────────
    
    @Test
    void userResponse_from_onlyIdAndUsername() {
        UserInfoEntity entity = new UserInfoEntity();
        entity.setId(5);
        entity.setUsername("bob");
        entity.setPassword("hash");
        entity.setEmail("bob@example.com");
        
        UserResponse resp = UserResponse.from(entity);
        
        assertThat(resp.getId()).isEqualTo(5);
        assertThat(resp.getUsername()).isEqualTo("bob");
    }
    
    // ─── FrameResponse.from() ────────────────────────────────────────────────
    
    @Test
    void frameResponse_from_allFields() {
        FrameInfoEntity entity = new FrameInfoEntity();
        entity.setId(1);
        entity.setFrameName("大数据框架");
        entity.setFrameCode("BIGDATA");
        entity.setFrameVersion("3.3.4");
        
        FrameResponse resp = FrameResponse.from(entity);
        
        assertThat(resp.getId()).isEqualTo(1);
        assertThat(resp.getFrameName()).isEqualTo("大数据框架");
        assertThat(resp.getFrameCode()).isEqualTo("BIGDATA");
        assertThat(resp.getFrameVersion()).isEqualTo("3.3.4");
    }
}
