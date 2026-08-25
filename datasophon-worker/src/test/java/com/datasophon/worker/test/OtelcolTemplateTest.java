package com.datasophon.worker.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import freemarker.cache.FileTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

public class OtelcolTemplateTest {

    private Configuration buildCfg() throws Exception {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_30);
        // 模板已随迁移搬到 package/raw/meta/datacluster-physical/OTELCOLLECTOR/templates/
        cfg.setTemplateLoader(new FileTemplateLoader(
                Path.of("..", "package", "raw", "meta", "datacluster-physical", "OTELCOLLECTOR", "templates").toFile()));
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
        data.put("carbonReceiverPort", "2003");
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
        assertTrue(yaml.contains("receivers: [otlp, carbon, prometheus/self, prometheus/local]"));
    }

    @Test
    public void skips_local_prometheus_receiver_when_local_scrape_jobs_are_empty() throws Exception {
        String yaml = render("doris", "");

        assertTrue(!yaml.contains("prometheus/local:"));
        assertTrue(yaml.contains("receivers: [otlp, carbon, prometheus/self]"));
    }

    @Test
    public void local_scrape_jobs_are_independent_from_exporter_mode() throws Exception {
        String yaml = render("s3", "        - job_name: 'DataNode'\n"
                + "          static_configs:\n"
                + "            - targets: ['127.0.0.1:9101']\n");

        assertTrue(yaml.contains("prometheus/local:"));
        assertTrue(yaml.contains("exporters: [awss3]"));
        assertTrue(yaml.contains("receivers: [otlp, carbon, prometheus/self, prometheus/local]"));
    }

    @Test
    public void renders_carbon_receiver_for_spark_metrics() throws Exception {
        String yaml = render("doris");

        assertTrue(yaml.contains("carbon:\n    endpoint: 127.0.0.1:2003\n    transport: tcp"));
        assertTrue(yaml.contains("name_separator: \"_\""));

        int threadpool = yaml.indexOf("name_prefix: \"spark_threadpool\"");
        int filesystem = yaml.indexOf("name_prefix: \"spark_fs\"");
        int executor = yaml.indexOf("name_prefix: \"spark_executor\"");
        int dagscheduler = yaml.indexOf("name_prefix: \"spark_dagscheduler\"");
        int fallback = yaml.indexOf("name_prefix: \"spark_unmatched\"");
        assertTrue(threadpool >= 0 && threadpool < filesystem);
        assertTrue(filesystem < executor);
        assertTrue(executor < dagscheduler);
        assertTrue(dagscheduler < fallback, "spark_unmatched fallback must be the last carbon regex rule");

        assertTrue(yaml.contains("name_prefix: \"spark_executor\"\n            type: cumulative"));
        assertTrue(yaml.contains("name_prefix: \"spark_dagscheduler\"\n            type: gauge"));
        assertTrue(yaml.contains("name_prefix: \"spark_unmatched\"\n            type: gauge"));
        assertTrue(yaml.contains("receivers: [otlp, carbon, prometheus/self]"));
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
    public void renders_filter_processor_dropping_empty_summary_datapoints() throws Exception {
        String yaml = render("doris");

        // 空 Summary(count=0,quantile 恒为 NaN)会让 dorisexporter 序列化 JSON 时报
        // "unsupported value: NaN" 导致整批导出失败并拖累同一 pipeline 里其它指标(实测确认，
        // 见 docs/monitoring/zookeeper-otel-verification.md)
        assertTrue(yaml.contains("filter/drop_empty_summary:"));
        assertTrue(yaml.contains("metric.type == METRIC_DATA_TYPE_SUMMARY and count == 0"));
        // 该过滤器现在挂在隔离出来的 metrics/summary pipeline 上（见 isolates_summary_metrics_*）
        assertTrue(yaml.contains(
                "processors: [memory_limiter, filter/keep_summary_only, filter/drop_empty_summary, "
                        + "filter/drop_zk_decaying_summary, batch]"));
    }

    /**
     * 前两条 filter 都是「按已知来源逐个排除」，只能事后补：任何新服务引入会衰减出 NaN 的 Summary，
     * 都会让 dorisexporter 整批序列化失败，把同一 pipeline 里的 Sum/Gauge 一起打挂
     * （2026-08-25 沙箱实测：NaN 报错 1531 次、Dropping data 124 次，Flink numRecordsIn 同步断流）。
     * 因此把 Summary 拆到独立 pipeline，并给它独立的 exporter 实例（独立 sending_queue 与 consumer），
     * 使 NaN 的爆炸半径收敛到 Summary 自身。本测试锁住这个隔离结构不被改回去。
     */
    @Test
    public void isolates_summary_metrics_into_dedicated_pipeline_and_exporter() throws Exception {
        String yaml = render("doris");

        // 主 pipeline 必须把 Summary 摘出去，且不再挂 Summary 专用过滤器
        assertTrue(yaml.contains("processors: [memory_limiter, filter/drop_summary, batch]"));
        // 两个过滤器互补，保证数据不重不漏
        assertTrue(yaml.contains("filter/drop_summary:"));
        assertTrue(yaml.contains("filter/keep_summary_only:"));
        assertTrue(yaml.contains("metric.type == METRIC_DATA_TYPE_SUMMARY'"));
        assertTrue(yaml.contains("metric.type != METRIC_DATA_TYPE_SUMMARY'"));
        // 独立 pipeline 与独立 exporter 实例
        assertTrue(yaml.contains("metrics/summary:"));
        assertTrue(yaml.contains("doris/summary:"));
        assertTrue(yaml.contains("exporters: [doris/summary]"));
    }

    @Test
    public void renders_filter_processor_dropping_zk_decaying_summary_metrics() throws Exception {
        String yaml = render("doris");

        // election_time/fsynctime/snapshottime/jvm_pause_time_ms 即使 count>0 也会因滑动时间窗衰减
        // 回 NaN，filter/drop_empty_summary 覆盖不到；OTTL 当前版本又无法清空 quantile_values，
        // 因此直接整体丢弃这 4 个指标，不导入 Doris（详见 docs/monitoring/zookeeper-otel-verification.md）
        assertTrue(yaml.contains("filter/drop_zk_decaying_summary:"));
        assertTrue(yaml.contains(
                "name == \"election_time\" or name == \"fsynctime\" or name == \"snapshottime\" or name == \"jvm_pause_time_ms\""));
    }

    /**
     * P2：OTELCOLLECTOR 的 carbonReceiverPort 与 SPARK3 的 spark.metrics.conf.*.sink.graphite.port
     * 分属两份独立渲染的 service_ddl.json，Datasophon 的 DDL 参数机制不支持跨服务引用同一个值。
     * 运维只改前者、忘了同步改后者，会导致 Spark 推送的端口与 collector 监听的端口不一致——而且
     * 因为 carbon receiver 的兜底规则也是在"端口对了"的前提下才生效，连 spark_unmatched_*
     * 都不会出现，完全没有告警痕迹（E8/T8 的兜底只兜"报文匹配不上规则"，兜不了"报文根本没收到"）。
     * 这条测试是这个约束下能给出的最强保障：默认值不一致时立刻测试报红。
     */
    @Test
    public void carbonReceiverPortDefaultMatchesSparkGraphiteSinkPortDefault() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        File otelcolDdl = Path.of("..", "package", "raw", "meta", "datacluster-physical",
                "OTELCOLLECTOR", "service_ddl.json").toFile();
        File sparkDdl = Path.of("..", "package", "raw", "meta", "datacluster-physical",
                "SPARK3", "service_ddl.json").toFile();

        String carbonReceiverPort = findParameterDefault(mapper.readTree(otelcolDdl), "carbonReceiverPort");
        String graphiteSinkPort = findSparkDefaultsConfValue(mapper.readTree(sparkDdl),
                "spark.metrics.conf.*.sink.graphite.port");

        assertNotNull(carbonReceiverPort, "OTELCOLLECTOR.carbonReceiverPort not found in service_ddl.json");
        assertNotNull(graphiteSinkPort, "SPARK3 sink.graphite.port not found in custom.spark.defaults.conf");
        assertEquals(carbonReceiverPort, graphiteSinkPort,
                "carbonReceiverPort 与 spark.metrics.conf.*.sink.graphite.port 的默认值必须一致，"
                        + "否则 Spark 推送指标会静默丢失（不产生任何错误或 spark_unmatched_* 兜底数据）");
    }

    private static String findParameterDefault(JsonNode ddl, String parameterName) {
        for (JsonNode parameter : ddl.path("parameters")) {
            if (parameterName.equals(parameter.path("name").asText())) {
                return parameter.path("defaultValue").asText(null);
            }
        }
        return null;
    }

    /** custom.spark.defaults.conf 的 defaultValue 是 [{key: value}, ...] 形式，按 key 找值。 */
    private static String findSparkDefaultsConfValue(JsonNode ddl, String key) {
        for (JsonNode parameter : ddl.path("parameters")) {
            if (!"custom.spark.defaults.conf".equals(parameter.path("name").asText())) {
                continue;
            }
            for (JsonNode entry : parameter.path("defaultValue")) {
                if (entry.has(key)) {
                    return entry.path(key).asText(null);
                }
            }
        }
        return null;
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
