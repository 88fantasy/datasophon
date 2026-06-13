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

import com.datasophon.dao.entity.UserInfoEntity;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 纯 Java 单测（无 Spring），验证 user 域 DTO 的字段映射。
 */
class UserDtoTest {
    
    // ─── CreateUserRequest.toEntity() ────────────────────────────────────────
    
    @Test
    void createRequest_toEntity_mapsCorrectFields() {
        CreateUserRequest req = new CreateUserRequest();
        req.setUsername("alice");
        req.setPassword("secret123");
        req.setEmail("alice@example.com");
        req.setPhone("13800000000");
        req.setUserType(2);
        
        UserInfoEntity entity = req.toEntity();
        
        assertThat(entity.getUsername()).isEqualTo("alice");
        assertThat(entity.getPassword()).isEqualTo("secret123");
        assertThat(entity.getEmail()).isEqualTo("alice@example.com");
        assertThat(entity.getPhone()).isEqualTo("13800000000");
        assertThat(entity.getUserType()).isEqualTo(2);
        // id 和 createTime 不应由请求体设置
        assertThat(entity.getId()).isNull();
        assertThat(entity.getCreateTime()).isNull();
    }
    
    // ─── UpdateUserRequest.toEntity(id) ──────────────────────────────────────
    
    @Test
    void updateRequest_toEntity_setsIdButNotPassword() {
        UpdateUserRequest req = new UpdateUserRequest();
        req.setUsername("bob");
        req.setEmail("bob@example.com");
        req.setPhone("13900000000");
        req.setUserType(2);
        
        UserInfoEntity entity = req.toEntity(99);
        
        assertThat(entity.getId()).isEqualTo(99);
        assertThat(entity.getUsername()).isEqualTo("bob");
        assertThat(entity.getEmail()).isEqualTo("bob@example.com");
        assertThat(entity.getPhone()).isEqualTo("13900000000");
        assertThat(entity.getUserType()).isEqualTo(2);
        // password 明确不设，防止覆盖
        assertThat(entity.getPassword()).isNull();
    }
    
    // ─── UserInfoResponse.from() ──────────────────────────────────────────────
    
    @Test
    void userInfoResponse_from_allFieldsPresentExceptPassword() {
        Date now = new Date();
        UserInfoEntity entity = new UserInfoEntity();
        entity.setId(7);
        entity.setUsername("carol");
        entity.setPassword("should-not-appear");
        entity.setEmail("carol@example.com");
        entity.setPhone("13700000000");
        entity.setCreateTime(now);
        entity.setUserType(1);
        
        UserInfoResponse resp = UserInfoResponse.from(entity);
        
        assertThat(resp.getId()).isEqualTo(7);
        assertThat(resp.getUsername()).isEqualTo("carol");
        assertThat(resp.getEmail()).isEqualTo("carol@example.com");
        assertThat(resp.getPhone()).isEqualTo("13700000000");
        assertThat(resp.getCreateTime()).isEqualTo(now);
        assertThat(resp.getUserType()).isEqualTo(1);
        // UserInfoResponse 没有 password 字段，编译期保证不泄漏
    }
    
    @Test
    void userInfoResponse_fromList_mapsAllElements() {
        UserInfoEntity e1 = new UserInfoEntity();
        e1.setId(1);
        e1.setUsername("u1");
        
        UserInfoEntity e2 = new UserInfoEntity();
        e2.setId(2);
        e2.setUsername("u2");
        
        List<UserInfoResponse> list = UserInfoResponse.fromList(List.of(e1, e2));
        
        assertThat(list).hasSize(2);
        assertThat(list.get(0).getId()).isEqualTo(1);
        assertThat(list.get(1).getUsername()).isEqualTo("u2");
    }
    
    @Test
    void userInfoResponse_fromList_nullInput_returnsEmptyList() {
        List<UserInfoResponse> list = UserInfoResponse.fromList(null);
        assertThat(list).isNotNull().isEmpty();
    }
    
    // ─── UserPageResponse.of() ────────────────────────────────────────────────
    
    @Test
    void userPageResponse_of_setsRecordsAndTotal() {
        UserInfoResponse r = new UserInfoResponse();
        r.setId(1);
        r.setUsername("dave");
        
        UserPageResponse page = UserPageResponse.of(List.of(r), 42L);
        
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getTotal()).isEqualTo(42L);
    }
}
