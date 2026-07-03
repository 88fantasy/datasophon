package com.datasophon.worker.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

public class ExporterScriptTemplateTest {
    
    private Configuration buildCfg() throws Exception {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_30);
        cfg.setClassForTemplateLoading(ExporterScriptTemplateTest.class, "/templates");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        return cfg;
    }
    
    @Test
    public void controlEsExporterRendersConfiguredPortAndKeepsHostnameLiteral() throws Exception {
        Configuration cfg = buildCfg();
        Template tpl = cfg.getTemplate("control_es_exporter.ftl");
        Map<String, Object> data = new HashMap<>();
        data.put("esExporterPort", "9200");
        StringWriter out = new StringWriter();
        tpl.process(data, out);
        String script = out.toString();
        
        assertTrue(script.contains("--web.listen-address=:9200"), "port must be substituted");
        assertTrue(!script.contains("--web.listen-address=:9114"), "static default must not leak through");
        // ${HOSTNAME} 必须保持字面量交给 bash 在运行时求值，不能被 FreeMarker 当成模板变量吞掉
        assertTrue(script.contains("@${HOSTNAME}:9200"), "shell ${HOSTNAME} expansion must survive rendering literally");
    }
    
    @Test
    public void controlValkeyRendersConfiguredExporterPort() throws Exception {
        Configuration cfg = buildCfg();
        Template tpl = cfg.getTemplate("control_valkey.ftl");
        Map<String, Object> data = new HashMap<>();
        data.put("valkeyExporterPort", "19121");
        StringWriter out = new StringWriter();
        tpl.process(data, out);
        String script = out.toString();
        
        assertTrue(script.contains("-web.listen-address 0.0.0.0:19121"), "port must be substituted");
        assertTrue(!script.contains("-web.listen-address 0.0.0.0:9121"), "static default must not leak through");
        // $port / $pwd 是 shell 变量（单 $ 无花括号），不会被 FreeMarker 解释，必须原样保留
        assertTrue(script.contains("redis.addr 127.0.0.1:$port"), "shell $port expansion must survive rendering literally");
        assertTrue(script.contains("redis.password $pwd"), "shell $pwd expansion must survive rendering literally");
    }
}
