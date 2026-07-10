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

import com.datasophon.dao.entity.ChatMessageEntity;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import lombok.Data;

/**
 * 消息列表响应体（v2）。屏蔽 conversationId 等内部字段。
 */
@Data
public class ChatMessageResponse {

    private Long id;
    private String role;
    private String content;
    private Date createTime;

    public static ChatMessageResponse from(ChatMessageEntity entity) {
        ChatMessageResponse r = new ChatMessageResponse();
        r.setId(entity.getId());
        r.setRole(entity.getRole());
        r.setContent(entity.getContent());
        r.setCreateTime(entity.getCreateTime());
        return r;
    }

    public static List<ChatMessageResponse> fromList(List<ChatMessageEntity> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        return list.stream().map(ChatMessageResponse::from).toList();
    }
}
