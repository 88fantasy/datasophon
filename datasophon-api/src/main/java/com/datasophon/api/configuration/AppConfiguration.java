/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.datasophon.api.configuration;

import com.datasophon.api.controller.ApiController;
import com.datasophon.api.interceptor.BasicValidRequestInterceptor;
import com.datasophon.api.interceptor.LocaleChangeInterceptor;
import com.datasophon.api.interceptor.LoginHandlerInterceptor;
import com.datasophon.api.interceptor.UserPermissionHandler;
import org.apache.commons.lang3.StringUtils;
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
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

import java.util.Locale;

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
                          BasicValidRequestInterceptor basicValidRequestInterceptor
  ) {
    this.loginHandlerInterceptor = loginHandlerInterceptor;
    this.userPermissionHandler = userPermissionHandler;
    this.localeChangeInterceptor = localeChangeInterceptor;
    this.basicValidRequestInterceptor = basicValidRequestInterceptor;
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
            getPathPrefix() + "/login", "/error",
            "/grafana/**",
            getPathPrefix() + "/cluster/alert/history/save",
            getPathPrefix() + "/cluster/kerberos/downloadKeytab",
            "/index.html",
            "/",
            "/static/**"
        );
    if (enableOpenApi) {
      loginRegistration.excludePathPatterns(
          "/swagger-resources/**", "/webjars/**", "/swagger-ui.html/**", "/doc.html",
          "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**", "/favicon.ico"
      );
    }

      InterceptorRegistration basicValidRegistration = registry.addInterceptor(basicValidRequestInterceptor)
              .addPathPatterns("/**");
      if (enableOpenApi) {
          basicValidRegistration.excludePathPatterns(
                  "/swagger-resources/**", "/webjars/**", "/swagger-ui.html/**", "/doc.html",
                  "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**", "/favicon.ico"
          );
      }
  }

  //Add request url prefix
  @Override
  public void configurePathMatch(PathMatchConfigurer configurer) {
    configurer.addPathPrefix(getPathPrefix(), aClass -> aClass.getSuperclass().equals(ApiController.class));
  }

  private String getPathPrefix() {
    return StringUtils.removeEnd(pathPrefix, "/");
  }

//  @Override
//  public void addViewControllers(ViewControllerRegistry registry) {
//    // 排除的文件扩展名
//    String excludedPatterns = ".*\\.(js|css|map|ico|png|jpg|jpeg|gif|svg|woff|woff2|ttf|eot)$";
//
//    // 捕获所有请求，但排除API、静态资源和某些已知路径
//    registry.addViewController("/{path:^(?!api$|static$|swagger$|webjars$|v2$|v3$).*$}/**")
//        .setViewName("forward:/index.html");
//  }

  /**
   * Turn off suffix-based content negotiation
   *
   * @param configurer configurer
   */
//  @Override
//  public void configureContentNegotiation(final ContentNegotiationConfigurer configurer) {
//    configurer.favorPathExtension(false);
//  }

}
