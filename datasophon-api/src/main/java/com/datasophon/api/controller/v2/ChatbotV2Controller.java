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

package com.datasophon.api.controller.v2;

import com.datasophon.api.configuration.AiProperties;
import com.datasophon.api.controller.ApiController;
import com.datasophon.api.dto.ApiResponse;
import com.datasophon.api.dto.v2.ChatRequest;
import com.datasophon.api.service.ChatService;
import com.datasophon.common.Constants;
import com.datasophon.dao.entity.ChatConversationEntity;
import com.datasophon.dao.entity.ChatMessageEntity;
import com.datasophon.dao.entity.UserInfoEntity;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/v2/chat")
public class ChatbotV2Controller extends ApiController {
    
    @Autowired
    private AiProperties aiProperties;
    
    @Autowired
    private ChatService chatService;
    
    @GetMapping("/config")
    public ApiResponse<Map<String, String>> config() {
        return ApiResponse.ok(Map.of("model", aiProperties.getModel()));
    }
    
    @PostMapping(value = "/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter completions(
                                  @Valid @RequestBody ChatRequest req,
                                  @RequestAttribute(Constants.SESSION_USER) UserInfoEntity loginUser,
                                  HttpServletResponse response) {
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache, no-transform");
        response.setHeader("Connection", "keep-alive");
        return chatService.chat(req, loginUser);
    }
    
    @GetMapping("/conversations")
    public ApiResponse<List<ChatConversationEntity>> conversations(
                                                                   @RequestAttribute(Constants.SESSION_USER) UserInfoEntity loginUser) {
        return ApiResponse.ok(chatService.listConversations(loginUser.getId()));
    }
    
    @GetMapping("/conversations/{id}/messages")
    public ApiResponse<List<ChatMessageEntity>> messages(
                                                         @PathVariable Long id,
                                                         @RequestAttribute(Constants.SESSION_USER) UserInfoEntity loginUser) {
        return ApiResponse.ok(chatService.listMessages(id, loginUser.getId()));
    }
    
    @DeleteMapping("/conversations/{id}")
    public ApiResponse<Void> deleteConversation(
                                                @PathVariable Long id,
                                                @RequestAttribute(Constants.SESSION_USER) UserInfoEntity loginUser) {
        chatService.deleteConversation(id, loginUser.getId());
        return ApiResponse.ok();
    }
}
