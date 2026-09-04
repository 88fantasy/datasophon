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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.datasophon.api.security;

import static org.springframework.http.HttpStatus.FORBIDDEN;

import com.datasophon.api.enums.Status;
import com.datasophon.api.utils.SecurityUtils;
import com.datasophon.common.Constants;
import com.datasophon.dao.entity.UserInfoEntity;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;

/** 仅允许系统管理员访问 Doris 活动任务查询。 */
@Component
public class SystemAdminGuard {

    private final HttpServletRequest request;

    public SystemAdminGuard(HttpServletRequest request) {
        this.request = request;
    }

    public void requireAdmin() {
        UserInfoEntity user = (UserInfoEntity) request.getAttribute(Constants.SESSION_USER);
        if (user == null || !SecurityUtils.isAdmin(user)) {
            throw new ResponseStatusException(FORBIDDEN, Status.USER_NO_OPERATION_PERM.getMsg());
        }
    }
}
