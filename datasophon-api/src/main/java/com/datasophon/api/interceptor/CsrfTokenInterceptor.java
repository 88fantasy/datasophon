package com.datasophon.api.interceptor;

import com.datasophon.common.Constants;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.WebUtils;

@Component
public class CsrfTokenInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(CsrfTokenInterceptor.class);

    // Stateless CSRF: token = HMAC-SHA256(sessionId, secret). No server-side store needed —
    // survives process restarts as long as the secret is stable.
    private static final String SECRET =
            System.getenv().getOrDefault("DDH_CSRF_SECRET", "datasophon-csrf-default-secret");

    private static final Set<String> SAFE_METHODS = new HashSet<>(Arrays.asList("GET", "HEAD", "OPTIONS"));

    public static String generateToken(String sessionId) {
        return hmac(sessionId);
    }

    // No-op: stateless tokens do not need explicit removal.
    public static void removeToken(String sessionId) {
    }

    private static String hmac(String sessionId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] bytes = mac.doFinal(sessionId.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            logger.error("CSRF HMAC computation failed", e);
            throw new IllegalStateException("CSRF token generation failed", e);
        }
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

        String expectedToken = hmac(sessionId);
        if (!expectedToken.equals(clientToken)) {
            logger.warn("CSRF check failed: token mismatch for session {}", sessionId);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }

        return true;
    }
}
