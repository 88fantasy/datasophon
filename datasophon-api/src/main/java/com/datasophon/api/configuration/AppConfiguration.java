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

package com.datasophon.api.configuration;

import com.datasophon.api.controller.ApiController;
import com.datasophon.api.interceptor.BasicValidRequestInterceptor;
import com.datasophon.api.interceptor.CsrfTokenInterceptor;
import com.datasophon.api.interceptor.LocaleChangeInterceptor;
import com.datasophon.api.interceptor.LoginHandlerInterceptor;
import com.datasophon.api.interceptor.UserPermissionHandler;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

/**
 * application configuration
 */
@Configuration
public class AppConfiguration implements WebMvcConfigurer {
    
    public static final String LOCALE_LANGUAGE_COOKIE = "language";
    
    @Value("${datasophon.server.path-prefix}")
    private String pathPrefix;
    
    @Value("${springdoc.api-docs.enabled:false}")
    private boolean enableOpenApi;
    
    private final LoginHandlerInterceptor loginHandlerInterceptor;
    
    private final UserPermissionHandler userPermissionHandler;
    
    private final LocaleChangeInterceptor localeChangeInterceptor;
    
    private final BasicValidRequestInterceptor basicValidRequestInterceptor;
    
    private final CsrfTokenInterceptor csrfTokenInterceptor;
    
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOrigin("*");
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");
        UrlBasedCorsConfigurationSource configSource = new UrlBasedCorsConfigurationSource();
        configSource.registerCorsConfiguration(getPathPrefix() + "/**", config);
        return new CorsFilter(configSource);
    }
    
    public AppConfiguration(LoginHandlerInterceptor loginHandlerInterceptor,
                            UserPermissionHandler userPermissionHandler,
                            LocaleChangeInterceptor localeChangeInterceptor,
                            BasicValidRequestInterceptor basicValidRequestInterceptor,
                            CsrfTokenInterceptor csrfTokenInterceptor) {
        this.loginHandlerInterceptor = loginHandlerInterceptor;
        this.userPermissionHandler = userPermissionHandler;
        this.localeChangeInterceptor = localeChangeInterceptor;
        this.basicValidRequestInterceptor = basicValidRequestInterceptor;
        this.csrfTokenInterceptor = csrfTokenInterceptor;
    }
    
    /**
     * Cookie
     *
     * @return local resolver
     */
    @Bean(name = "localeResolver")
    public LocaleResolver localeResolver() {
        CookieLocaleResolver localeResolver = new CookieLocaleResolver();
        localeResolver.setCookieName(LOCALE_LANGUAGE_COOKIE);
        /** set default locale **/
        localeResolver.setDefaultLocale(Locale.SIMPLIFIED_CHINESE);
        /** set language tag compliant **/
        localeResolver.setLanguageTagCompliant(false);
        return localeResolver;
    }
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // i18n
        registry.addInterceptor(localeChangeInterceptor);
        registry.addInterceptor(userPermissionHandler);
        // login
        InterceptorRegistration loginRegistration = registry.addInterceptor(loginHandlerInterceptor)
                .addPathPatterns(getPathPrefix() + "/**")
                .excludePathPatterns(
                        "/error",
                        "/grafana/**",
                        "/index.html",
                        "/",
                        "/static/**")
                .excludePathPatterns(getRealExcludeUrl(
                        "/login",
                        "/v2/login/account",
                        "/cluster/alert/history/save",
                        "/cluster/kerberos/downloadKeytab",
                        "/service/install/download*"));
        if (enableOpenApi) {
            loginRegistration.excludePathPatterns(
                    "/swagger-resources/**", "/webjars/**", "/swagger-ui.html/**", "/doc.html",
                    "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**", "/favicon.ico");
        }
        
        // CSRF token verification — placed after login interceptor
        InterceptorRegistration csrfRegistration = registry
                .addInterceptor(csrfTokenInterceptor)
                .addPathPatterns(getPathPrefix() + "/**")
                .excludePathPatterns(
                        "/error",
                        "/grafana/**",
                        "/index.html",
                        "/",
                        "/static/**")
                .excludePathPatterns(getRealExcludeUrl(
                        "/login",
                        "/v2/login/account",
                        "/cluster/alert/history/save",
                        "/cluster/kerberos/downloadKeytab",
                        "/service/install/download*"));
        if (enableOpenApi) {
            csrfRegistration.excludePathPatterns(
                    "/swagger-resources/**", "/webjars/**", "/swagger-ui.html/**", "/doc.html",
                    "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**", "/favicon.ico");
        }
        
        InterceptorRegistration basicValidRegistration = registry
                .addInterceptor(basicValidRequestInterceptor)
                .addPathPatterns("/**")
                // Internal sidecar endpoints are not browser-navigable routes; exclude them
                // so the SPA-forward logic doesn't serve index.html for /internal/** paths.
                .excludePathPatterns("/internal/**");
        if (enableOpenApi) {
            basicValidRegistration.excludePathPatterns(
                    "/swagger-resources/**", "/webjars/**", "/swagger-ui.html/**", "/doc.html",
                    "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**", "/favicon.ico");
        }
    }
    
    private String[] getRealExcludeUrl(String... urls) {
        List<String> result = new ArrayList<>(urls.length);
        for (String url : urls) {
            if (!url.startsWith("/")) {
                url = "/" + url;
            }
            result.add(getPathPrefix() + url);
        }
        return result.toArray(new String[0]);
    }
    
    // Add request url prefix
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(getPathPrefix(), aClass -> aClass.getSuperclass().equals(ApiController.class));
    }
    
    private String getPathPrefix() {
        return StringUtils.removeEnd(pathPrefix, "/");
    }
    
}
