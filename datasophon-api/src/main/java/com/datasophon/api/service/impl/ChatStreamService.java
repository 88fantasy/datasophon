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

import com.datasophon.api.configuration.AiProperties;
import com.datasophon.api.dto.v2.ChatRequest;
import com.datasophon.dao.entity.ChatConversationEntity;
import com.datasophon.dao.entity.ChatMessageEntity;
import com.datasophon.dao.mapper.ChatConversationMapper;
import com.datasophon.dao.mapper.ChatMessageMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ChatStreamService {
    
    private static final Logger log = LoggerFactory.getLogger(ChatStreamService.class);
    
    @Autowired
    private ChatConversationMapper conversationMapper;
    
    @Autowired
    private ChatMessageMapper messageMapper;
    
    @Autowired
    private AiProperties aiProperties;
    
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Async
    public void stream(ChatRequest req,
                       ChatConversationEntity conv,
                       SseEmitter emitter) {
        StringBuilder assistantBuf = new StringBuilder();
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("messages", req.getMessages());
            body.put("conversationId", conv.getId());
            body.put("clusterId", req.getClusterId());
            body.put("userId", conv.getUserId());
            String json = objectMapper.writeValueAsString(body);
            
            HttpRequest httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(aiProperties.getSidecarUrl() + "/agent/chat"))
                    .header("Content-Type", "application/json")
                    .header("X-Agent-Token", aiProperties.getInternalToken())
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            
            HttpResponse<java.io.InputStream> resp = httpClient.send(
                    httpReq, HttpResponse.BodyHandlers.ofInputStream());
            
            if (resp.statusCode() != 200) {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("AI sidecar returned " + resp.statusCode()));
                emitter.send(SseEmitter.event().data("[DONE]"));
                emitter.complete();
                return;
            }
            
            try (
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(resp.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("data: ")) {
                        String payload = line.substring(6).trim();
                        if ("[DONE]".equals(payload)) {
                            emitter.send(SseEmitter.event().data("[DONE]"));
                            break;
                        }
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> chunk = objectMapper.readValue(payload, Map.class);
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> choices =
                                    (List<Map<String, Object>>) chunk.get("choices");
                            if (choices != null && !choices.isEmpty()) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> delta =
                                        (Map<String, Object>) choices.get(0).get("delta");
                                if (delta != null && delta.get("content")instanceof String s) {
                                    assistantBuf.append(s);
                                }
                            }
                        } catch (Exception ignored) {
                        }
                        emitter.send(SseEmitter.event().data(payload));
                    }
                }
            }
            
            if (!assistantBuf.isEmpty()) {
                saveMessage(conv.getId(), "assistant", assistantBuf.toString());
            }
            emitter.complete();
            
        } catch (Exception e) {
            log.error("chat stream error", e);
            emitter.completeWithError(e);
        }
    }
    
    private void saveMessage(Long conversationId, String role, String content) {
        ChatMessageEntity msg = new ChatMessageEntity();
        msg.setConversationId(conversationId);
        msg.setRole(role);
        msg.setContent(content);
        messageMapper.insert(msg);
    }
}
