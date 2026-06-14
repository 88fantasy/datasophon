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

import com.datasophon.dao.entity.ChatConversationEntity;
import com.datasophon.dao.entity.ChatMessageEntity;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 纯 Java 单测（无 Spring），验证 chatbot 域 DTO 的字段映射。
 */
class ChatbotDtoTest {
    
    // ─── ChatConversationResponse.from() ────────────────────────────────────
    
    @Test
    void conversationResponse_from_mapsThreeFields() {
        Date now = new Date();
        ChatConversationEntity entity = new ChatConversationEntity();
        entity.setId(1L);
        entity.setTitle("测试会话");
        entity.setUpdateTime(now);
        entity.setUserId(99);
        entity.setClusterId(10);
        entity.setCreateTime(new Date(0));
        
        ChatConversationResponse resp = ChatConversationResponse.from(entity);
        
        assertThat(resp.getId()).isEqualTo(1L);
        assertThat(resp.getTitle()).isEqualTo("测试会话");
        assertThat(resp.getUpdateTime()).isEqualTo(now);
    }
    
    @Test
    void conversationResponse_from_doesNotExposeUserId() throws Exception {
        ChatConversationEntity entity = new ChatConversationEntity();
        entity.setId(2L);
        entity.setTitle("t");
        entity.setUserId(42);
        
        ChatConversationResponse resp = ChatConversationResponse.from(entity);
        
        // ChatConversationResponse 不含 userId 字段
        assertThat(resp.getClass().getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("userId", "clusterId", "createTime");
    }
    
    @Test
    void conversationResponse_fromList_emptyList_returnsEmpty() {
        List<ChatConversationResponse> result = ChatConversationResponse.fromList(Collections.emptyList());
        assertThat(result).isNotNull().isEmpty();
    }
    
    @Test
    void conversationResponse_fromList_nullInput_returnsEmpty() {
        List<ChatConversationResponse> result = ChatConversationResponse.fromList(null);
        assertThat(result).isNotNull().isEmpty();
    }
    
    @Test
    void conversationResponse_fromList_multipleItems() {
        ChatConversationEntity e1 = new ChatConversationEntity();
        e1.setId(1L);
        e1.setTitle("会话 A");
        ChatConversationEntity e2 = new ChatConversationEntity();
        e2.setId(2L);
        e2.setTitle("会话 B");
        
        List<ChatConversationResponse> result = ChatConversationResponse.fromList(List.of(e1, e2));
        
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(1).getId()).isEqualTo(2L);
    }
    
    // ─── ChatMessageResponse.from() ──────────────────────────────────────────
    
    @Test
    void messageResponse_from_mapsFourFields() {
        Date now = new Date();
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setId(10L);
        entity.setRole("user");
        entity.setContent("你好");
        entity.setCreateTime(now);
        entity.setConversationId(99L);
        
        ChatMessageResponse resp = ChatMessageResponse.from(entity);
        
        assertThat(resp.getId()).isEqualTo(10L);
        assertThat(resp.getRole()).isEqualTo("user");
        assertThat(resp.getContent()).isEqualTo("你好");
        assertThat(resp.getCreateTime()).isEqualTo(now);
    }
    
    @Test
    void messageResponse_from_doesNotExposeConversationId() {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setId(5L);
        entity.setRole("assistant");
        entity.setContent("回复");
        entity.setConversationId(77L);
        
        ChatMessageResponse resp = ChatMessageResponse.from(entity);
        
        // ChatMessageResponse 不含 conversationId 字段
        assertThat(resp.getClass().getDeclaredFields())
                .extracting(java.lang.reflect.Field::getName)
                .doesNotContain("conversationId");
    }
    
    @Test
    void messageResponse_fromList_nullInput_returnsEmpty() {
        List<ChatMessageResponse> result = ChatMessageResponse.fromList(null);
        assertThat(result).isNotNull().isEmpty();
    }
    
    @Test
    void messageResponse_fromList_emptyList_returnsEmpty() {
        List<ChatMessageResponse> result = ChatMessageResponse.fromList(Collections.emptyList());
        assertThat(result).isNotNull().isEmpty();
    }
    
    @Test
    void messageResponse_fromList_multipleItems() {
        ChatMessageEntity e1 = new ChatMessageEntity();
        e1.setId(1L);
        e1.setRole("user");
        e1.setContent("问题");
        ChatMessageEntity e2 = new ChatMessageEntity();
        e2.setId(2L);
        e2.setRole("assistant");
        e2.setContent("答案");
        
        List<ChatMessageResponse> result = ChatMessageResponse.fromList(List.of(e1, e2));
        
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getRole()).isEqualTo("user");
        assertThat(result.get(1).getRole()).isEqualTo("assistant");
    }
}
