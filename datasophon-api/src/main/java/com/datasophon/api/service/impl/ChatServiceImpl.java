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

package com.datasophon.api.service.impl;

import com.datasophon.api.dto.v2.ChatRequest;
import com.datasophon.api.service.ChatService;
import com.datasophon.dao.entity.ChatConversationEntity;
import com.datasophon.dao.entity.ChatMessageEntity;
import com.datasophon.dao.entity.UserInfoEntity;
import com.datasophon.dao.mapper.ChatConversationMapper;
import com.datasophon.dao.mapper.ChatMessageMapper;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;

@Service
public class ChatServiceImpl implements ChatService {
    
    @Autowired
    private ChatConversationMapper conversationMapper;
    
    @Autowired
    private ChatMessageMapper messageMapper;
    
    @Autowired
    private ChatStreamService chatStreamService;
    
    @Override
    public SseEmitter chat(ChatRequest req, UserInfoEntity user) {
        SseEmitter emitter = new SseEmitter(600_000L);
        ChatConversationEntity conv = resolveConversation(req, user.getId());
        String userContent = req.getMessages().stream()
                .filter(m -> "user".equals(m.get("role")))
                .reduce((a, b) -> b)
                .map(m -> m.get("content"))
                .orElse("");
        saveMessage(conv.getId(), "user", userContent);
        chatStreamService.stream(req, conv, emitter);
        return emitter;
    }
    
    @Override
    public List<ChatConversationEntity> listConversations(Integer userId) {
        return conversationMapper.selectList(
                new QueryWrapper<ChatConversationEntity>()
                        .eq("user_id", userId)
                        .orderByDesc("update_time"));
    }
    
    @Override
    public List<ChatMessageEntity> listMessages(Long conversationId, Integer userId) {
        ChatConversationEntity conv = conversationMapper.selectById(conversationId);
        if (conv == null || !conv.getUserId().equals(userId)) {
            return List.of();
        }
        return messageMapper.selectList(
                new QueryWrapper<ChatMessageEntity>()
                        .eq("conversation_id", conversationId)
                        .orderByAsc("create_time"));
    }
    
    @Override
    public void deleteConversation(Long conversationId, Integer userId) {
        ChatConversationEntity conv = conversationMapper.selectById(conversationId);
        if (conv != null && conv.getUserId().equals(userId)) {
            messageMapper.delete(new QueryWrapper<ChatMessageEntity>()
                    .eq("conversation_id", conversationId));
            conversationMapper.deleteById(conversationId);
        }
    }
    
    private ChatConversationEntity resolveConversation(ChatRequest req, Integer userId) {
        if (req.getConversationId() != null) {
            ChatConversationEntity existing =
                    conversationMapper.selectById(req.getConversationId());
            if (existing != null && existing.getUserId().equals(userId)) {
                return existing;
            }
        }
        ChatConversationEntity conv = new ChatConversationEntity();
        conv.setUserId(userId);
        conv.setClusterId(req.getClusterId());
        String title = req.getMessages().stream()
                .filter(m -> "user".equals(m.get("role")))
                .map(m -> m.get("content"))
                .findFirst()
                .map(s -> s.length() > 20 ? s.substring(0, 20) : s)
                .orElse("新对话");
        conv.setTitle(title);
        conversationMapper.insert(conv);
        return conv;
    }
    
    private void saveMessage(Long conversationId, String role, String content) {
        ChatMessageEntity msg = new ChatMessageEntity();
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setContent(content);
        messageMapper.insert(msg);
    }
}
