package com.datasophon.api.interceptor;

import com.datasophon.common.Constants;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.WebUtils;

@Component
public class CsrfTokenInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(CsrfTokenInterceptor.class);

    private static final ConcurrentHashMap<String, String> CSRF_TOKEN_STORE = new ConcurrentHashMap<>();

    private static final Set<String> SAFE_METHODS = new HashSet<>(Arrays.asList("GET", "HEAD", "OPTIONS"));

    public static String generateToken(String sessionId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        CSRF_TOKEN_STORE.put(sessionId, token);
        return token;
    }

    public static void removeToken(String sessionId) {
        CSRF_TOKEN_STORE.remove(sessionId);
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return true;
        }

        // API token auth is inherently CSRF-safe (browser won't auto-attach custom headers)
        String apiToken = request.getHeader("token");
        if (StringUtils.isNotBlank(apiToken)) {
            return true;
        }

        String sessionId = request.getHeader(Constants.SESSION_ID);
        if (StringUtils.isBlank(sessionId)) {
            Cookie cookie = WebUtils.getCookie(request, Constants.SESSION_ID);
            if (cookie != null) {
                sessionId = cookie.getValue();
            }
        }
        if (StringUtils.isBlank(sessionId)) {
            // 未登录用户，无需 CSRF 防护
            return true;
        }

        String serverToken = CSRF_TOKEN_STORE.get(sessionId);
        if (serverToken == null) {
            // 内存中无 CSRF token（服务重启后旧 session），放行由 LoginInterceptor 判断 session 有效性
            return true;
        }

        String clientToken = request.getHeader(Constants.CSRF_HEADER);
        if (StringUtils.isBlank(clientToken) || !serverToken.equals(clientToken)) {
            logger.warn("CSRF check failed: token mismatch for session {}", sessionId);
            response.setStatus(javax.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
            return false;
        }

        return true;
    }
}
