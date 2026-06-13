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
 * 编辑用户请求体。不含 password（密码由独立接口修改）。
 */
@Data
public class UpdateUserRequest {
    
    @NotBlank
    private String username;
    
    private String email;
    private String phone;
    private Integer userType;
    
    /**
     * 映射为 UserInfoEntity，设置 id，不设 password（防止密码被覆盖）。
     *
     * @param id 路径参数传入的用户 ID
     */
    public UserInfoEntity toEntity(Integer id) {
        UserInfoEntity entity = new UserInfoEntity();
        entity.setId(id);
        entity.setUsername(this.username);
        entity.setEmail(this.email);
        entity.setPhone(this.phone);
        entity.setUserType(this.userType);
        // password 明确不设，防止覆盖
        return entity;
    }
}
