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

package com.datasophon.api.controller.v2;

import com.datasophon.api.controller.ApiController;
import com.datasophon.api.dto.ApiResponse;
import com.datasophon.api.dto.v2.CurrentUserVO;
import com.datasophon.api.dto.v2.LoginRequest;
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
import jakarta.validation.Valid;

import org.apache.commons.lang3.StringUtils;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * v2 鉴权接口（JSON 入参 + 标准信封出参）。
 *
 * <p>所有接口经 {@code AppConfiguration.configurePathMatch} 自动加前缀 {@code /api}，
 * 加上 {@code context-path=/ddh} 后完整路径为：
 * <ul>
 *   <li>POST {@code /ddh/api/v2/login/account}</li>
 *   <li>GET  {@code /ddh/api/v2/currentUser}</li>
 *   <li>POST {@code /ddh/api/v2/logout}</li>
 * </ul>
 *
 * <p>实现复用现有 {@link Authenticator}、{@link SessionService}、
 * {@link CsrfTokenInterceptor}，不重写鉴权逻辑。
 */
@RestController
@RequestMapping("/v2")
public class LoginV2Controller extends ApiController {
    
    private static final Logger logger = LoggerFactory.getLogger(LoginV2Controller.class);
    
    @Autowired
    private Authenticator authenticator;
    
    @Autowired
    private SessionService sessionService;
    
    /**
     * 登录接口——接收 JSON，种 cookie，返回标准信封含用户信息。
     *
     * <p>复刻 {@code LoginController.login} 的 cookie 逻辑，改为 JSON 入参。
     */
    @PostMapping("/login/account")
    public ApiResponse<CurrentUserVO> login(@Valid @RequestBody LoginRequest req,
                                            HttpServletRequest request,
                                            HttpServletResponse response) {
        logger.info("v2 login: {}", req.getUsername());
        
        String ip = HttpUtils.getClientIpAddress(request);
        if (StringUtils.isEmpty(ip)) {
            return ApiResponse.fail(400, "无法获取客户端 IP");
        }
        
        Result result = authenticator.authenticate(req.getUsername(), req.getPassword(), ip);
        if (!Integer.valueOf(200).equals(result.getCode())) {
            return ApiResponse.fail(result.getCode(), (String) result.get(Constants.MSG));
        }
        
        // 种 HttpOnly session cookie
        response.setStatus(HttpStatus.OK.value());
        @SuppressWarnings("unchecked")
        Map<String, String> cookieMap = (Map<String, String>) result.getData();
        for (Map.Entry<String, String> entry : cookieMap.entrySet()) {
            Cookie cookie = new Cookie(entry.getKey(), entry.getValue());
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            response.addCookie(cookie);
        }
        
        // 种非 HttpOnly CSRF cookie（前端 JS 读取并附加到请求头）
        String sessionId = cookieMap.get(Constants.SESSION_ID);
        if (StringUtils.isNotBlank(sessionId)) {
            String csrfToken = CsrfTokenInterceptor.generateToken(sessionId);
            Cookie csrfCookie = new Cookie(Constants.CSRF_TOKEN, csrfToken);
            csrfCookie.setHttpOnly(false);
            csrfCookie.setPath("/");
            response.addCookie(csrfCookie);
        }
        
        UserInfoEntity user = (UserInfoEntity) result.get(Constants.USER_INFO);
        return ApiResponse.ok(CurrentUserVO.from(user));
    }
    
    /**
     * 获取当前登录用户——从 session 读取，无需查库。
     *
     * <p>{@link com.datasophon.api.interceptor.LoginHandlerInterceptor} 已将
     * {@code SESSION_USER} 注入 request attribute，此处直接取用。
     */
    @GetMapping("/currentUser")
    public ApiResponse<CurrentUserVO> currentUser(
                                                  @RequestAttribute(Constants.SESSION_USER) UserInfoEntity loginUser) {
        return ApiResponse.ok(CurrentUserVO.from(loginUser));
    }
    
    /**
     * 登出——复用 {@link SessionService#signOut}，清理 CSRF cookie。
     */
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
                                    @RequestAttribute(Constants.SESSION_USER) UserInfoEntity loginUser,
                                    HttpServletRequest request,
                                    HttpServletResponse response) {
        logger.info("v2 logout: {}", loginUser.getUsername());
        String ip = HttpUtils.getClientIpAddress(request);
        sessionService.signOut(ip, loginUser);
        request.removeAttribute(Constants.SESSION_USER);
        
        // 清除客户端 CSRF cookie
        Cookie csrfCookie = new Cookie(Constants.CSRF_TOKEN, "");
        csrfCookie.setMaxAge(0);
        csrfCookie.setPath("/");
        response.addCookie(csrfCookie);
        
        return ApiResponse.ok();
    }
}
