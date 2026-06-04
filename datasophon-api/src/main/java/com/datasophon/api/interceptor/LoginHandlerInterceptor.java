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

package com.datasophon.api.interceptor;

import com.datasophon.api.security.Authenticator;
import com.datasophon.common.Constants;
import com.datasophon.dao.entity.UserInfoEntity;
import com.datasophon.dao.mapper.UserInfoMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * login interceptor, must login first
 */
@Component
public class LoginHandlerInterceptor implements HandlerInterceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(LoginHandlerInterceptor.class);
    
    @Autowired
    private UserInfoMapper userMapper;
    
    @Autowired
    private Authenticator authenticator;
    
    /**
     * Intercept the execution of a handler. Called after HandlerMapping determined
     *
     * @param request  current HTTP request
     * @param response current HTTP response
     * @param handler  chosen handler to execute, for type and/or instance evaluation
     * @return boolean true or false
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // get token
        String token = request.getHeader("token");
        UserInfoEntity user = null;
        if (StringUtils.isEmpty(token)) {
            user = authenticator.getAuthUser(request);
            String url = request.getRequestURI();
            
            // 跳过拦截的接口
            if (url.startsWith("/ddh/api/cluster/engineInfo")) {
                logger.info("url:{}", url);
                return true;
            }
            // if user is null
            if (user == null) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                logger.info("user does not exist");
                return false;
            }
        } else {
            user = userMapper.queryUserByToken(token);
            if (user == null) {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                logger.info("user token has expired");
                return false;
            }
        }
        request.getSession().setAttribute(Constants.SESSION_USER, user);
        request.setAttribute(Constants.SESSION_USER, user);
        return true;
    }
    
}
