package com.datasophon.cli.util;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RuntimeUtil;
import com.datasophon.cli.base.CliConstants;
import com.datasophon.common.model.ClusterConfig;
import com.datasophon.cli.base.Executor;
import com.datasophon.common.Constants;
import com.datasophon.common.enums.OsType;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.MetaUtils;
import com.datasophon.common.utils.NexusFileUtils;
import com.datasophon.common.utils.ShellUtils;
import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
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

    /**
     * 优先使用 cluster-sample.yml.decode > cluster-sample.yml
     * @param configFilePath
     * @return
     */
    public static ClusterConfig getConfig(String configFilePath) {
        File configFile = new File(configFilePath);
        if (!configFile.exists()) {
            throw new RuntimeException("config file not found, please check " + configFilePath);
        }
        Yaml yaml = new Yaml();
        String decodeClusterSamplePath = String.format("%s.decode", configFilePath);
        if(!FileUtil.exist(decodeClusterSamplePath)) {
            byte[] bytes = MetaUtils.decodeContext(configFile, Constants.SECRET_KEY);
            return yaml.loadAs(StringUtils.toEncodedString(bytes, StandardCharsets.UTF_8), ClusterConfig.class);
        } else {
            String content = FileUtil.readString(configFile, Charset.defaultCharset());
            return yaml.loadAs(content, ClusterConfig.class);
        }
    }

    public static void downRegistryFile(Executor executor, boolean enableRegistry, String registryIp, String registryPort, String registryUsername, String registryPassword,
                                  String sourceName, String distPath){
        if(enableRegistry) {
            String url = String.format("http://%s:%s/repository/raw/packages/%s", registryIp, registryPort, sourceName);
            log.info("制品{}下载开始, url:{}", sourceName, url);
            InputStream inputStream = null;
            try {
                inputStream = NexusFileUtils.downStream(url, registryUsername, registryPassword);
                executor.writeFromStream(inputStream, distPath);
                inputStream.close();
            } catch (Exception e) {
                throw new RuntimeException("url:"+ url, e);
            }
            log.info("制品{}下载完成", distPath);
        } else {
            log.info("enableRegistry is {}. skip", enableRegistry);
        }
    }

}
