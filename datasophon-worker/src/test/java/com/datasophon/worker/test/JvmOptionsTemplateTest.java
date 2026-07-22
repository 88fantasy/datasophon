package com.datasophon.worker.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

public class JvmOptionsTemplateTest {

    private Configuration buildCfg() throws Exception {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_30);
        cfg.setClassForTemplateLoading(JvmOptionsTemplateTest.class, "/templates");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        return cfg;
    }

    @Test
    public void rendersHeapSizeAndOtelJavaagent() throws Exception {
        Configuration cfg = buildCfg();
        Template tpl = cfg.getTemplate("jvm.options.ftl");
        Map<String, Object> data = new HashMap<>();
        data.put("heapSize", "2g");
        data.put("otelJavaagentPath", "/data/install_datasophon/elasticsearch/otel/opentelemetry-javaagent.jar");
        StringWriter out = new StringWriter();
        tpl.process(data, out);
        String options = out.toString();

        assertTrue(options.contains("-Xms2g"));
        assertTrue(options.contains("-Xmx2g"));
        // ES 没有内建 OTel SDK，走 javaagent 自动插桩；jar 复用 datasophon-worker 自带的 otel/
        // 拷贝（Phase F link hook），路径由 ${ROOT.ELASTICSEARCH.INSTALL_PATH} 在 DDL 层解析为绝对路径，
        // 避免依赖 ES 启动脚本的 cwd 假设
        assertTrue(options.contains(
                "-javaagent:/data/install_datasophon/elasticsearch/otel/opentelemetry-javaagent.jar"));
        assertTrue(options.contains("-Dotel.service.name=elasticsearch"));
        assertTrue(options.contains("-Dotel.traces.exporter=otlp"));
        assertTrue(options.contains("-Dotel.metrics.exporter=none"));
        assertTrue(options.contains("-Dotel.logs.exporter=none"));
    }
}
