package com.datasophon.api.configuration;


import com.datasophon.api.interceptor.GrafanaProxyServlet;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.servlet.Servlet;

@Configuration
public class GrafanaProxyServletConfiguration {

    @Bean
    public ServletRegistrationBean<Servlet> servletRegistrationBean() {
        return new ServletRegistrationBean<>(new GrafanaProxyServlet(), "/grafana/*");
    }

}
