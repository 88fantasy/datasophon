package com.datasophon.worker.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;

public class YarnEnvTemplateTest {
    
    private Configuration buildCfg() throws Exception {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_30);
        cfg.setClassForTemplateLoading(YarnEnvTemplateTest.class, "/templates");
        cfg.setDefaultEncoding("UTF-8");
        cfg.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        return cfg;
    }
    
    @Test
    public void yarnEnvRendersConfiguredJmxPorts() throws Exception {
        Configuration cfg = buildCfg();
        Template tpl = cfg.getTemplate("yarn-env.ftl");
        Map<String, Object> data = new HashMap<>();
        data.put("hadoopHome", "${HADOOP_HOME}");
        data.put("rmJmxPort", "19323");
        data.put("nmJmxPort", "19324");
        data.put("historyServerJmxPort", "19325");
        StringWriter out = new StringWriter();
        tpl.process(data, out);
        String script = out.toString();
        
        assertTrue(script
                .contains("YARN_RESOURCEMANAGER_OPTS=\"$YARN_RESOURCEMANAGER_OPTS -javaagent:${HADOOP_HOME}/jmx/jmx_prometheus_javaagent-0.16.1.jar=19323:${HADOOP_HOME}/jmx/prometheus_config.yml\""));
        assertTrue(
                script.contains("YARN_NODEMANAGER_OPTS=\"$YARN_NODEMANAGER_OPTS -javaagent:${HADOOP_HOME}/jmx/jmx_prometheus_javaagent-0.16.1.jar=19324:${HADOOP_HOME}/jmx/prometheus_config.yml\""));
        // 修正原 hadoop-env.ftl 里的 typo（YAiRN_HISTORYSERVER_OPTS），迁移后变量名必须正确
        assertTrue(script
                .contains("YARN_HISTORYSERVER_OPTS=\"$YARN_HISTORYSERVER_OPTS -javaagent:${HADOOP_HOME}/jmx/jmx_prometheus_javaagent-0.16.1.jar=19325:${HADOOP_HOME}/jmx/prometheus_config.yml\""));
        assertTrue(!script.contains("YAiRN_HISTORYSERVER_OPTS"), "typo must not survive the move");
    }
    
    @Test
    public void hadoopEnvNoLongerContainsYarnOpts() throws Exception {
        Configuration cfg = buildCfg();
        Template tpl = cfg.getTemplate("hadoop-env.ftl");
        Map<String, Object> data = new HashMap<>();
        data.put("hadoopHome", "${HADOOP_HOME}");
        data.put("hadoopLogDir", "${HADOOP_HOME}/logs");
        data.put("namenodeHeapSize", "8");
        data.put("datanodeHeapSize", "8");
        data.put("namenodeJmxPort", "27001");
        data.put("datanodeJmxPort", "27002");
        data.put("journalnodeJmxPort", "27003");
        data.put("zkfcJmxPort", "27004");
        data.put("itemList", new java.util.ArrayList<>());
        StringWriter out = new StringWriter();
        tpl.process(data, out);
        String script = out.toString();
        
        assertTrue(script.contains("HDFS_ZKFC_OPTS"), "HDFS 自身的 opts 段必须保留");
        assertTrue(!script.contains("YARN_RESOURCEMANAGER_OPTS"), "YARN 段已迁移到 yarn-env.ftl，不应再残留");
        assertTrue(!script.contains("YARN_NODEMANAGER_OPTS"));
        assertTrue(!script.contains("YARN_HISTORYSERVER_OPTS"));
    }
}
