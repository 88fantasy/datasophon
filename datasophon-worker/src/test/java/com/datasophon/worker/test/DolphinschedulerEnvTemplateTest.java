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

        // $command 由 dolphinscheduler-daemon.sh 在 source 本文件前置好，case 分支穷举 4 个角色
        assertTrue(env.contains("case \"$command\" in"));
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
    }
}
