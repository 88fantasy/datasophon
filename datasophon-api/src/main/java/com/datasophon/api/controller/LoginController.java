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

package com.datasophon.api.controller;

import static com.datasophon.api.enums.Status.IP_IS_EMPTY;

import com.datasophon.api.enums.Status;
import com.datasophon.api.interceptor.CsrfTokenInterceptor;
import com.datasophon.api.security.Authenticator;
import com.datasophon.api.service.SessionService;
import com.datasophon.api.utils.HttpUtils;
import com.datasophon.common.Constants;
import com.datasophon.common.utils.Result;
import com.datasophon.dao.entity.UserInfoEntity;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginController extends ApiController {
    
    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);
    
    @Autowired
    private SessionService sessionService;
    
    @Autowired
    private Authenticator authenticator;
    
    /**
     * login
     *
     * @param userName     user name
     * @param userPassword user password
     * @param request      request
     * @param response     response
     * @return login result
     */
    
    @RequestMapping("/login")
    public Result login(@RequestParam(value = "username") String userName,
                        @RequestParam(value = "password") String userPassword,
                        HttpServletRequest request,
                        HttpServletResponse response) {
        logger.info("login user name: {} ", userName);
        
        // user name check
        if (StringUtils.isEmpty(userName)) {
            return Result.error(Status.USER_NAME_NULL.getCode(),
                    Status.USER_NAME_NULL.getMsg());
        }
        
        // user ip check
        String ip = HttpUtils.getClientIpAddress(request);
        if (StringUtils.isEmpty(ip)) {
            return Result.error(IP_IS_EMPTY.getCode(), IP_IS_EMPTY.getMsg());
        }
        
        // verify username and password
        Result result = authenticator.authenticate(userName, userPassword, ip);
        if (result.getCode() != Status.SUCCESS.getCode()) {
            return result;
        }
        
        response.setStatus(HttpStatus.OK.value());
        Map<String, String> cookieMap = (Map<String, String>) result.getData();
        for (Map.Entry<String, String> cookieEntry : cookieMap.entrySet()) {
            Cookie cookie = new Cookie(cookieEntry.getKey(), cookieEntry.getValue());
            cookie.setHttpOnly(true);
            response.addCookie(cookie);
        }
        
        // Generate CSRF token and set it as a non-HttpOnly cookie for JS access.
        // path must be "/" so JS on any page (e.g. /ddh/Colony/) can read it via document.cookie.
        String sessionId = cookieMap.get(Constants.SESSION_ID);
        if (StringUtils.isNotBlank(sessionId)) {
            String csrfToken = CsrfTokenInterceptor.generateToken(sessionId);
            Cookie csrfCookie = new Cookie(Constants.CSRF_TOKEN, csrfToken);
            csrfCookie.setHttpOnly(false);
            csrfCookie.setPath("/");
            response.addCookie(csrfCookie);
        }
        
        return result;
    }
    
    /**
     * sign out
     *
     * @param loginUser login user
     * @param request   request
     * @return sign out result
     */
    @PostMapping(value = "/signOut")
    public Result signOut(@RequestAttribute(value = Constants.SESSION_USER) UserInfoEntity loginUser,
                          HttpServletRequest request,
                          HttpServletResponse response) {
        logger.info("login user:{} sign out", loginUser.getUsername());
        String ip = HttpUtils.getClientIpAddress(request);
        
        // signOut cleans up both session and CSRF token internally
        sessionService.signOut(ip, loginUser);
        // clear session
        request.removeAttribute(Constants.SESSION_USER);
        
        // Clear CSRF cookie on client side
        Cookie csrfCookie = new Cookie(Constants.CSRF_TOKEN, "");
        csrfCookie.setMaxAge(0);
        csrfCookie.setPath("/");
        response.addCookie(csrfCookie);
        
        return Result.success();
    }
}
