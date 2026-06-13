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

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新建用户请求体。只包含创建所需字段，不含 id / createTime。
 */
@Data
public class CreateUserRequest {
    
    @NotBlank
    private String username;
    
    @NotBlank
    private String password;
    
    private String email;
    private String phone;
    private Integer userType;
    
    /** 映射为 UserInfoEntity，不设 id / createTime。 */
    public UserInfoEntity toEntity() {
        UserInfoEntity entity = new UserInfoEntity();
        entity.setUsername(this.username);
        entity.setPassword(this.password);
        entity.setEmail(this.email);
        entity.setPhone(this.phone);
        entity.setUserType(this.userType);
        return entity;
    }
}
