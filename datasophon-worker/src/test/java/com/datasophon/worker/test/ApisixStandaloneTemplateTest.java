package com.datasophon.worker.test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

public class ApisixStandaloneTemplateTest {

    private Configuration buildCfg() {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_30);
        cfg.setClassForTemplateLoading(ApisixStandaloneTemplateTest.class, "/templates");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        return cfg;
    }

    private Map<String, Object> configData() {
        Map<String, Object> data = new HashMap<>();
        data.put("apisixPort", 9080);
        data.put("apisixPrometheusAddr", "192.168.10.131");
        data.put("apisixPrometheusPort", 9091);
        return data;
    }

    private Map<String, Object> routeData() {
        Map<String, Object> data = new HashMap<>();
        data.put("apisixRouteUri", "/get");
        data.put("apisixUpstreamHost", "192.168.10.135");
        data.put("apisixUpstreamPort", 8080);
        return data;
    }

    private String render(Configuration cfg, String templateName, Map<String, Object> data) throws Exception {
        Template template = cfg.getTemplate(templateName);
        StringWriter out = new StringWriter();
        template.process(data, out);
        return out.toString();
    }

    @Test
    public void rendersStandaloneConfigWithoutEtcd() throws Exception {
        String config = render(buildCfg(), "apisix-config.ftl", configData());
        Map<String, Object> yaml = new Yaml(new SafeConstructor(new LoaderOptions())).load(config);

        assertTrue(config.contains("role: data_plane"));
        assertTrue(config.contains("config_provider: yaml"));
        assertTrue(config.contains("enable_admin: false"));
        assertTrue(config.contains("node_listen: 9080"));
        assertTrue(config.contains("port: 9091"));
        assertFalse(config.contains("etcd"));
        assertTrue(yaml.containsKey("deployment"));
        assertTrue(yaml.containsKey("apisix"));
        // 中间件链路追踪接入（Phase F）：opentelemetry 未进 APISIX 官方默认插件清单，config.yaml 显式声明
        // plugins 会整体覆盖默认清单而非追加，必须列出全部默认项 + opentelemetry
        assertTrue(yaml.containsKey("plugins"));
        assertTrue(config.contains("- opentelemetry"));
        assertTrue(config.contains("- prometheus"));
    }

    @Test
    public void rendersStaticRouteWithEndMarker() throws Exception {
        String routes = render(buildCfg(), "apisix-routes.ftl", routeData());
        String yaml = routes.replace("#END\n", "");
        Map<String, Object> document = new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);

        assertTrue(routes.contains("'192.168.10.135:8080': 1"));
        assertTrue(routes.contains("uri: '/get'"));
        assertTrue(routes.contains("prometheus:"));
        assertTrue(routes.contains("opentelemetry:"));
        // sampler 必须显式 always_on：插件 schema 默认 sampler=always_off，写空对象 {} 不会真正采样任何 span
        assertTrue(routes.contains("name: always_on"));
        // opentelemetry 插件运行时读取 plugin_metadata（不是 plugin_attr），缺失时插件静默跳过、不产生任何 span
        assertTrue(routes.contains("service.name: apisix"));
        assertTrue(routes.contains("address: 127.0.0.1:4318"));
        assertTrue(routes.endsWith("#END\n"));
        assertTrue(document.containsKey("upstreams"));
        assertTrue(document.containsKey("routes"));
        assertTrue(document.containsKey("global_rules"));
        assertTrue(document.containsKey("plugin_metadata"));
    }

    @Test
    public void rendersStringPortValuesWithoutNumericFormattingErrors() throws Exception {
        Map<String, Object> configData = configData();
        configData.put("apisixPort", "9080");
        configData.put("apisixPrometheusPort", "9091");
        Map<String, Object> routeData = routeData();
        routeData.put("apisixUpstreamPort", "8080");

        String config = render(buildCfg(), "apisix-config.ftl", configData);
        String routes = render(buildCfg(), "apisix-routes.ftl", routeData);

        assertTrue(config.contains("node_listen: 9080"));
        assertTrue(config.contains("port: 9091"));
        assertTrue(routes.contains("'192.168.10.135:8080': 1"));
    }

    @Test
    public void standaloneDdlUsesCustomTemplatesAndMapParameters() throws Exception {
        String ddl = Files.readString(Path.of("..", "package", "raw", "meta", "datacluster-physical", "APISIX", "service_ddl.json"));

        assertTrue(ddl.contains("\"filename\": \"config.yaml\""));
        assertTrue(ddl.contains("\"filename\": \"apisix.yaml\""));
        assertTrue(ddl.contains("\"templateName\": \"apisix-config.ftl\""));
        assertTrue(ddl.contains("\"templateName\": \"apisix-routes.ftl\""));
        assertFalse(ddl.contains("apisixEtcdNodeList"));
        assertFalse(ddl.contains("apisixAdminKey"));
        assertFalse(ddl.contains("apisixAllowAdmin"));
        assertTrue(ddl.contains("\"name\": \"apisixPort\""));
        assertTrue(ddl.contains("\"portParams\": [\"apisixPort\", \"apisixPrometheusPort\"]"));
        assertTrue(ddl.contains("\"name\": \"apisixRouteUri\",\n      \"key\": \"apisixRouteUri\""));
        assertTrue(ddl.contains("\"name\": \"apisixUpstreamHost\",\n      \"key\": \"apisixUpstreamHost\""));
        assertTrue(ddl.contains("\"name\": \"apisixUpstreamPort\",\n      \"key\": \"apisixUpstreamPort\""));
        assertTrue(ddl.contains("\"name\": \"apisixPrometheusAddr\",\n      \"key\": \"apisixPrometheusAddr\""));
        assertTrue(ddl.contains("\"name\": \"apisixPrometheusPort\""));
    }
}
