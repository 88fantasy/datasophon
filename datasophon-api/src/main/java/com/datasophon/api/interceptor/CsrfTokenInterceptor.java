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
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
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
            logger.warn("CSRF check failed: missing sessionId");
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        
        String clientToken = request.getHeader(Constants.CSRF_HEADER);
        if (StringUtils.isBlank(clientToken)) {
            logger.warn("CSRF check failed: missing {} header", Constants.CSRF_HEADER);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        
        String serverToken = CSRF_TOKEN_STORE.get(sessionId);
        if (serverToken == null || !serverToken.equals(clientToken)) {
            logger.warn("CSRF check failed: token mismatch for session {}", sessionId);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        
        return true;
    }
}
