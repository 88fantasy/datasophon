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

import lombok.Data;

/**
 * v2 当前用户视图对象，对齐前端 API.CurrentUser。
 *
 * <p>字段含义：
 * <ul>
 *   <li>{@code name} — 用户名，对应前端菜单/头像显示</li>
 *   <li>{@code access} — 权限标识，"admin" 对应 access.ts 的 canAdmin</li>
 *   <li>{@code userid} — 用户 ID</li>
 *   <li>{@code email} — 邮箱</li>
 * </ul>
 */
@Data
public class CurrentUserVO {
    
    private Integer userid;
    private String name;
    private String email;
    /**
     * 权限级别：userType=1 → "admin"，其他 → "user"。
     * 对应前端 {@code access.ts} 中的 {@code canAdmin} 判断。
     */
    private String access;
    
    public static CurrentUserVO from(UserInfoEntity user) {
        CurrentUserVO vo = new CurrentUserVO();
        vo.userid = user.getId();
        vo.name = user.getUsername();
        vo.email = user.getEmail();
        // userType=1 为超级管理员
        vo.access = Integer.valueOf(1).equals(user.getUserType()) ? "admin" : "user";
        return vo;
    }
}
