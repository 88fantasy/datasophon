package com.datasophon.worker.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

public class DolphinschedulerEnvTemplateTest {

    private Configuration buildCfg() throws Exception {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_30);
        cfg.setClassForTemplateLoading(DolphinschedulerEnvTemplateTest.class, "/templates");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        return cfg;
    }

    private Map<String, Object> baseData() {
        Map<String, Object> data = new HashMap<>();
        data.put("databaseUrl", "jdbc:mysql://mysql:3306/dolphinscheduler");
        data.put("username", "dolphinscheduler");
        data.put("password", "secret");
        data.put("apiServerPort", "12345");
        data.put("masterServerPort", "5679");
        data.put("workerServerPort", "1235");
        data.put("alertServerPort", "50053");
        return data;
    }

    @Test
    public void rendersServerPortForKnownCommand() throws Exception {
        Configuration cfg = buildCfg();
        Template tpl = cfg.getTemplate("dolphinscheduler_env.ftl");
        Map<String, Object> data = baseData();
        StringWriter out = new StringWriter();
        tpl.process(data, out);
        String env = out.toString();

        // $command 是 dolphinscheduler-daemon.sh 的本地 shell 变量，daemon.sh 用 nohup bash 派生
        // 新进程执行各角色 start.sh，source 本文件时 $command 恒为空——改用各角色 start.sh 各自
        // 设置的 *_HOME 变量判断角色，这是运行时真正可靠的信号
        assertTrue(env.contains("if [ -n \"$API_HOME\" ]"));
        assertTrue(env.contains("elif [ -n \"$MASTER_HOME\" ]"));
        assertTrue(env.contains("elif [ -n \"$WORKER_HOME\" ]"));
        assertTrue(env.contains("elif [ -n \"$ALERT_HOME\" ]"));
        assertTrue(env.contains("export SERVER_PORT=12345"));
        assertTrue(env.contains("export SERVER_PORT=5679"));
        assertTrue(env.contains("export SERVER_PORT=1235"));
        assertTrue(env.contains("export SERVER_PORT=50053"));
        // 既有的数据库注入机制必须保持不变
        assertTrue(env.contains("SPRING_DATASOURCE_URL=\"jdbc:mysql://mysql:3306/dolphinscheduler\""));
        // 注册中心改为 jdbc（复用 DS 自身元数据库），不再依赖 ZooKeeper
        assertTrue(env.contains("export REGISTRY_TYPE=${REGISTRY_TYPE:-jdbc}"));
        assertTrue(env.contains(
                "export REGISTRY_HIKARI_CONFIG_JDBC_URL=${REGISTRY_HIKARI_CONFIG_JDBC_URL:-jdbc:mysql://mysql:3306/dolphinscheduler}"));
        assertTrue(env.contains(
                "export REGISTRY_HIKARI_CONFIG_DRIVER_CLASS_NAME=${REGISTRY_HIKARI_CONFIG_DRIVER_CLASS_NAME:-com.mysql.cj.jdbc.Driver}"));
        assertTrue(!env.contains("REGISTRY_ZOOKEEPER_CONNECT_STRING"));
        // OTel Java Agent 默认开启，只发 traces，agent jar 复用 datasophon-worker 自带的 otel/
        assertTrue(env.contains("export OTEL_JAVAAGENT_ENABLED=\"${OTEL_JAVAAGENT_ENABLED:-true}\""));
        assertTrue(env.contains("-javaagent:$DOLPHINSCHEDULER_HOME/otel/opentelemetry-javaagent.jar"));
        assertTrue(env.contains("-Dotel.service.name=dolphinscheduler-$DS_ROLE"));
        assertTrue(env.contains("-Dotel.traces.exporter=otlp"));
        assertTrue(env.contains("-Dotel.metrics.exporter=none"));
        assertTrue(env.contains("-Dotel.logs.exporter=none"));
    }
}
