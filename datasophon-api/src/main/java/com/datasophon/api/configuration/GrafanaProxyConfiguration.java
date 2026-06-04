package com.datasophon.api.configuration;

import static com.datasophon.common.Constants.GRAFANA_PATH;

import com.datasophon.api.service.ClusterServiceDashboardService;

import jakarta.servlet.Servlet;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

import lombok.SneakyThrows;

import org.eclipse.jetty.ee10.proxy.ProxyServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.util.UriComponentsBuilder;

import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;

@Configuration
@ConditionalOnProperty(name = "datasophon.proxy-grafana.enable", havingValue = "true")
public class GrafanaProxyConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(GrafanaProxyConfiguration.class);
    
    @Value("${datasophon.proxy-grafana.max-threads:80}")
    String maxThreads;
    
    @Autowired
    private ClusterServiceDashboardService clusterServiceDashboardService;
    
    @Bean
    public ServletRegistrationBean<Servlet> grafanaHttpProxy() {
        ServletRegistrationBean<Servlet> servlet = new ServletRegistrationBean<>(new GrafanaProxyServlet(),
                GRAFANA_PATH + "/*");
        servlet.setInitParameters(MapUtil.builder("maxThreads", maxThreads)
                .put("preserveHost", "true").build());
        return servlet;
    }
    
    public class GrafanaProxyServlet extends ProxyServlet {
        
        @SneakyThrows
        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
            super.service(request, response);
        }
        
        @Override
        protected String rewriteTarget(HttpServletRequest request) {
            Integer clusterId = getCluster(request.getRequestURI());
            if (clusterId != null) {
                String host = "http://" + clusterServiceDashboardService.getGrafanaHost(clusterId);
                return UriComponentsBuilder.fromUriString(host)
                        .path(request.getRequestURI())
                        .query(request.getQueryString())
                        .build(true).toUriString();
            } else {
                return super.rewriteTarget(request);
            }
        }
    }
    
    private Integer getCluster(String requestUrl) {
        try {
            List<String> paths = StrUtil.splitTrim(requestUrl, "/");
            if (paths.size() > 3) {
                return Integer.parseInt(paths.get(2));
            } else {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }
    
}
