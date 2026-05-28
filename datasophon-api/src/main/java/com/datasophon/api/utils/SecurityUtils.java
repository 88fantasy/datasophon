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


package com.datasophon.api.utils;

import com.datasophon.common.Constants;
import com.datasophon.dao.entity.UserInfoEntity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import cn.hutool.core.convert.Convert;

public class SecurityUtils {
    
    public static HttpServletRequest getRequest() {
        HttpServletRequest request =
                ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        return request;
    }
    
    public static HttpSession getSession() {
        HttpSession session = getRequest().getSession();
        return session;
    }
    
    /**
     * 获取用户
     */
    public static String getUsername() {
        String username = getAuthUser().getUsername();
        return null == username ? null : ServletUtils.urlDecode(username);
    }
    
    /**
     * 获取用户ID
     */
    public static Long getUserId() {
        return Convert.toLong(ServletUtils.getRequest().getHeader(Constants.DETAILS_USER_ID));
    }
    
    /**
     * 是否为管理员
     *
     * @param userInfoEntity 用户
     * @return 结果
     */
    public static boolean isAdmin(UserInfoEntity userInfoEntity) {
        Integer userId = userInfoEntity.getId();
        return userId != null && 1 == userId;
    }
    
    public static UserInfoEntity getAuthUser() {
        return (UserInfoEntity) getRequest().getAttribute(Constants.SESSION_USER);
    }
}
