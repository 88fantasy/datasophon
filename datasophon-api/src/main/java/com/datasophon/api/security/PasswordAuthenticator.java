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

package com.datasophon.api.security;

import com.datasophon.api.enums.Status;
import com.datasophon.api.service.SessionService;
import com.datasophon.api.service.UserInfoService;
import com.datasophon.api.utils.SecurityUtils;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.SessionEntity;
import com.datasophon.dao.entity.UserInfoEntity;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class PasswordAuthenticator implements Authenticator {
    
    private static final Logger logger = LoggerFactory.getLogger(PasswordAuthenticator.class);
    
    @Autowired
    private UserInfoService userService;
    @Autowired
    private SessionService sessionService;
    
    @Override
    public Result authenticate(String username, String password, String extra) {
        Result result = new Result();
        // verify username and password
        UserInfoEntity user = userService.queryUser(username, password);
        if (user == null) {
            result.put(Constants.CODE, Status.USER_NAME_PASSWD_ERROR.getCode());
            result.put(Constants.MSG, Status.USER_NAME_PASSWD_ERROR.getMsg());
            return result;
        }
        
        // create session
        String sessionId = sessionService.createSession(user, extra);
        if (sessionId == null) {
            result.put(Constants.CODE, Status.LOGIN_SESSION_FAILED.getCode());
            result.put(Constants.MSG, Status.LOGIN_SESSION_FAILED.getMsg());
            return result;
        }
        logger.info("sessionId : {}", sessionId);
        result.put(Constants.DATA, Collections.singletonMap(Constants.SESSION_ID, sessionId));
        result.put(Constants.CODE, Status.SUCCESS.getCode());
        result.put(Constants.MSG, Status.LOGIN_SUCCESS.getMsg());
        result.put(Constants.USER_INFO, user);
        SecurityUtils.getSession().setAttribute(Constants.SESSION_USER, user);
        return result;
    }
    
    @Override
    public UserInfoEntity getAuthUser(HttpServletRequest request) {
        SessionEntity session = sessionService.getSession(request);
        if (session == null) {
            logger.info("session info is null ");
            return null;
        }
        // get user object from session
        return userService.getById(session.getUserId());
    }
}
