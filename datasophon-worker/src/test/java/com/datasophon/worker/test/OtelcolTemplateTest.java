package com.datasophon.worker.test;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class OtelcolTemplateTest {

    private String render() throws Exception {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_30);
        cfg.setClassForTemplateLoading(OtelcolTemplateTest.class, "/templates");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        Template tpl = cfg.getTemplate("otelcol.ftl");
        Map<String, Object> data = new HashMap<>();
        data.put("ip", "10.0.0.11");
        data.put("s3Endpoint", "http://mw1:9040");
        data.put("s3Bucket", "otel-bootstrap");
        data.put("s3Prefix", "node");
        data.put("s3Region", "us-east-1");
        data.put("memLimitMiB", "512");
        data.put("batchSize", "8192");
        data.put("queueStorageDir", "/data/otelcol/storage");
        StringWriter out = new StringWriter();
        tpl.process(data, out);
        return out.toString();
    }

    @Test
    public void renders_s3_mode_with_persistent_queue() throws Exception {
        String yaml = render();
        // 持久化队列(F3)
        assertTrue(yaml.contains("file_storage/queue"));
        assertTrue(yaml.contains("directory: /data/otelcol/storage"));
        assertTrue(yaml.contains("storage: file_storage/queue"));
        // S3 bootstrap sink → Rustfs
        assertTrue(yaml.contains("endpoint: http://mw1:9040"));
        assertTrue(yaml.contains("s3_bucket: otel-bootstrap"));
        assertTrue(yaml.contains("s3_force_path_style: true"));
        // 限流/批量
        assertTrue(yaml.contains("memory_limiter"));
        assertTrue(yaml.contains("limit_mib: 512"));
        assertTrue(yaml.contains("send_batch_size: 8192"));
        // self-metrics(A3 监控 tab 依赖) — v0.154.0 readers 新结构
        assertTrue(yaml.contains("prometheus:"));
        assertTrue(yaml.contains("port: 8888"));
        // file_storage 目录自动创建
        assertTrue(yaml.contains("create_directory: true"));
        // 三信号 pipeline
        assertTrue(yaml.contains("metrics:"));
        assertTrue(yaml.contains("logs:"));
        assertTrue(yaml.contains("traces:"));
    }
}
