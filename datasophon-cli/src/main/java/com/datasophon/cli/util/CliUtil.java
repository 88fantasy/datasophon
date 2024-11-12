package com.datasophon.cli.util;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjectUtil;
import com.datasophon.cli.base.CliConstants;
import com.datasophon.cli.base.ClusterConfig;
import com.datasophon.cli.base.GlobalConfig;
import com.datasophon.common.Constants;
import com.datasophon.common.enums.ArchType;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.ShellUtils;

import freemarker.template.Configuration;
import freemarker.template.Template;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import cn.hutool.core.util.RuntimeUtil;
import org.yaml.snakeyaml.Yaml;
import picocli.CommandLine;

public final class CliUtil {
    
    public static boolean checkAndInstall(String component, OsType os, String dir, Map<String, Map<OsType, String>> packages) {
        System.out.printf("checking %s...%n", component);
        String checkStr = CliConstants.CHECK_PREFIX + component;
        List<String> checkResult = RuntimeUtil.execForLines(checkStr);
        if (checkResult.isEmpty()) {
            if (!packages.containsKey(component) && !packages.get(component).containsKey(os)) {
                System.out.printf("%s is undefined...%n", component);
                return false;
            }
            String packageFilePath = dir + Constants.SLASH + packages.get(component).get(os);
            System.out.printf("%s is not installed... going to install: %s %n", component, packageFilePath);
            ExecResult result = ShellUtils.execWithStatus(dir, Collections.singletonList(String.format("rpm -ivh %s", packageFilePath)), 180);
            if (result.getExecResult()) {
                checkResult = RuntimeUtil.execForLines(checkStr);
                if (!checkResult.isEmpty()) {
                    System.out.printf("%s install successfully. %n", component);
                } else {
                    System.out.printf("%s install failed, stop. %n", component);
                    return false;
                }
            }
        }
        System.out.printf("%s done...%n", component);
        return true;
    }
    
    /**
     * 根据配置模板与变量值，生成配置文件
     */
    public static void generateConfigFile(String templateDir, String templateFile, Map<String, Object> data, String outFile) throws Exception {
        Configuration cfg = new Configuration(Configuration.VERSION_2_3_30);
        cfg.setDirectoryForTemplateLoading(new File(templateDir));
        Template template = cfg.getTemplate(templateFile);
        
        File outputFile = new File(outFile);
        try (Writer out = new FileWriter(outputFile)) {
            template.process(data, out);
        }
    }

    public static ClusterConfig getConfig(String configFilePath) {
        File configFile = new File(configFilePath);
        if (!configFile.exists()) {
            throw new RuntimeException("config file not found, please check " + configFilePath);
        }
        Yaml yaml = new Yaml();
        String content = FileUtil.readString(configFile, Charset.defaultCharset());
        return yaml.loadAs(content, ClusterConfig.class);
    }
    
}
