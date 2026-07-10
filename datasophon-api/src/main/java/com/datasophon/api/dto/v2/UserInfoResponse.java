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

import com.datasophon.dao.entity.UserInfoEntity;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import lombok.Data;

/**
 * 用户管理页响应体。包含完整展示字段，明确不含 password。
 * 与 {@link UserResponse}（仅 id+username，供集群管理员下拉用）是不同的类，勿混用。
 */
@Data
public class UserInfoResponse {

    private Integer id;
    private String username;
    private String email;
    private String phone;
    private Date createTime;
    private Integer userType;

    public static UserInfoResponse from(UserInfoEntity entity) {
        UserInfoResponse r = new UserInfoResponse();
        r.setId(entity.getId());
        r.setUsername(entity.getUsername());
        r.setEmail(entity.getEmail());
        r.setPhone(entity.getPhone());
        r.setCreateTime(entity.getCreateTime());
        r.setUserType(entity.getUserType());
        return r;
    }

    public static List<UserInfoResponse> fromList(List<UserInfoEntity> entities) {
        if (entities == null) {
            return Collections.emptyList();
        }
        return entities.stream().map(UserInfoResponse::from).toList();
    }
}
