package com.datasophon.worker.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

public class OtelcolTemplateTest {
    
    private Configuration buildCfg() throws Exception {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_30);
        cfg.setClassForTemplateLoading(OtelcolTemplateTest.class, "/templates");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        return cfg;
    }
    
    private String render() throws Exception {
        return render("s3");
    }
    
    private String render(String exporterMode) throws Exception {
        return render(exporterMode, "");
    }
    
    private String render(String exporterMode, String localScrapeJobsYaml) throws Exception {
        Configuration cfg = buildCfg();
        Template tpl = cfg.getTemplate("otelcol.ftl");
        Map<String, Object> data = new HashMap<>();
        data.put("ip", "10.0.0.11");
        data.put("nodeHostname", "worker-1");
        data.put("s3Endpoint", "http://mw1:9040");
        data.put("s3Bucket", "otel-bootstrap");
        data.put("s3Prefix", "node");
        data.put("s3Region", "us-east-1");
        data.put("memLimitMiB", "512");
        data.put("batchSize", "8192");
        data.put("queueStorageDir", "/data/otelcol/storage");
        data.put("exporterMode", exporterMode);
        data.put("dorisEndpoint", "http://doris-fe:8030");
        data.put("dorisDatabase", "otel");
        data.put("dorisUser", "otel_collector");
        data.put("otelSelfMetricsPort", "8888");
        data.put("localScrapeJobsYaml", localScrapeJobsYaml);
        StringWriter out = new StringWriter();
        tpl.process(data, out);
        return out.toString();
    }
    
    @Test
    public void renders_env_file_with_aws_credentials() throws Exception {
        Configuration cfg = buildCfg();
        Template tpl = cfg.getTemplate("otelcol-env.ftl");
        Map<String, Object> data = new HashMap<>();
        data.put("s3AccessKey", "minio_access_key");
        data.put("s3SecretKey", "minio_secret_key");
        data.put("dorisUser", "otel_collector");
        data.put("dorisPassword", "generated-secret");
        StringWriter out = new StringWriter();
        tpl.process(data, out);
        String env = out.toString();
        assertTrue(env.contains("AWS_ACCESS_KEY_ID=minio_access_key"), "env must contain AWS_ACCESS_KEY_ID");
        assertTrue(env.contains("AWS_SECRET_ACCESS_KEY=minio_secret_key"), "env must contain AWS_SECRET_ACCESS_KEY");
        assertTrue(env.contains("OTEL_DORIS_USER=otel_collector"));
        assertTrue(env.contains("OTEL_DORIS_PASSWORD=generated-secret"));
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
        assertTrue(yaml.contains("exporters: [awss3]"));
    }
    
    @Test
    public void renders_doris_mode_without_plaintext_password() throws Exception {
        String yaml = render("doris");
        
        assertTrue(yaml.contains("doris:"));
        assertTrue(yaml.contains("endpoint: http://doris-fe:8030"));
        assertTrue(yaml.contains("database: otel"));
        assertTrue(yaml.contains("username: otel_collector"));
        assertTrue(yaml.contains("password: ${env:OTEL_DORIS_PASSWORD}"));
        assertTrue(yaml.contains("create_schema: false"));
        assertTrue(yaml.contains("exporters: [doris]"));
        assertTrue(!yaml.contains("generated-secret"));
    }
    
    @Test
    public void renders_local_prometheus_receiver_when_local_scrape_jobs_exist() throws Exception {
        String yaml = render("doris", "        - job_name: 'DataNode'\n"
                + "          static_configs:\n"
                + "            - targets: ['127.0.0.1:9101']\n");
        
        assertTrue(yaml.contains("prometheus/local:"));
        assertTrue(yaml.contains("job_name: 'DataNode'"));
        assertTrue(yaml.contains("receivers: [otlp, prometheus/self, prometheus/local]"));
    }
    
    @Test
    public void skips_local_prometheus_receiver_when_local_scrape_jobs_are_empty() throws Exception {
        String yaml = render("doris", "");
        
        assertTrue(!yaml.contains("prometheus/local:"));
        assertTrue(yaml.contains("receivers: [otlp, prometheus/self]"));
    }
    
    @Test
    public void local_scrape_jobs_are_independent_from_exporter_mode() throws Exception {
        String yaml = render("s3", "        - job_name: 'DataNode'\n"
                + "          static_configs:\n"
                + "            - targets: ['127.0.0.1:9101']\n");
        
        assertTrue(yaml.contains("prometheus/local:"));
        assertTrue(yaml.contains("exporters: [awss3]"));
        assertTrue(yaml.contains("receivers: [otlp, prometheus/self, prometheus/local]"));
    }
    
    @Test
    public void renders_hostmetrics_receiver_with_dedicated_pipeline() throws Exception {
        String yaml = render();
        
        // host_metrics receiver 替代 node_exporter 采集主机 CPU/内存/磁盘/网络
        // （receiver 名用 host_metrics，非 hostmetrics：后者是 v0.154.0 已废弃的 legacy alias）
        assertTrue(yaml.contains("host_metrics:"));
        assertTrue(!yaml.contains("  hostmetrics:"));
        assertTrue(yaml.contains("system.linux.memory.available"));
        // resource processor 把身份改写成 prometheus receiver 同形状，供查询层复用
        assertTrue(yaml.contains("resource/host_metrics:"));
        assertTrue(yaml.contains("value: node"));
        assertTrue(yaml.contains("value: worker-1"));
        // 独立 pipeline，不与现有 metrics pipeline 共用 processor
        assertTrue(yaml.contains("metrics/host:"));
        assertTrue(yaml.contains("receivers: [host_metrics]"));
        assertTrue(yaml.contains("processors: [memory_limiter, resource/host_metrics, batch]"));
        // node_exporter 相关端口已彻底退役
        assertTrue(!yaml.contains(":9100"));
    }
    
    @Test
    public void renders_raw_yaml_override_verbatim() throws Exception {
        Configuration cfg = buildCfg();
        Template tpl = cfg.getTemplate("otelcol.ftl");
        Map<String, Object> data = new HashMap<>();
        String rawYaml = "receivers:\n  otlp:\nexporters:\n  debug:\n";
        data.put("rawYaml", rawYaml);
        StringWriter out = new StringWriter();
        
        tpl.process(data, out);
        
        assertEquals(rawYaml, out.toString());
    }
}
