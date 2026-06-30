/*
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.datasophon.worker.utils;

import static com.datasophon.worker.handler.ConfigureServiceHandler.SH;

import com.datasophon.common.Constants;
import com.datasophon.common.model.Generators;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.utils.PropertyUtils;

import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.FileTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.ConfigType;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.naming.remote.http.NamingHttpClientManager;
import com.alibaba.nacos.client.naming.utils.NamingHttpUtil;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;

public class FreemakerUtils {
    
    private static final Logger logger = LoggerFactory.getLogger(FreemakerUtils.class);
    
    // 匹配含 PASSWORD / SECRET 的 key=value 行，日志脱敏用
    private static final Pattern CREDENTIAL_LINE =
            Pattern.compile("(?im)^([^=\n]*(?:PASSWORD|SECRET)[^=\n]*=)(.+)$");
    
    static String redactSecrets(String content) {
        return CREDENTIAL_LINE.matcher(content).replaceAll("$1<redacted>");
    }
    
    private static final NacosRestTemplate nacosRestTemplate = NamingHttpClientManager.getInstance().getNacosRestTemplate();
    
    public static void generateConfigFile(Generators generators, List<ServiceConfig> configs,
                                          String serviceInstallHome) throws IOException, TemplateException {
        generateConfigFile(generators, configs, serviceInstallHome, null);
    }
    
    public static void generateConfigFile(Generators generators, List<ServiceConfig> configs,
                                          String serviceInstallHome, String extPath) throws IOException, TemplateException {
        Configuration config = initConfiguration(extPath);
        
        // 获取模板的名称
        String tplName = determinateTplName(generators);
        logger.info("begin to generate config file, tplName: {}, additional tpl path is: {}", tplName, extPath);
        
        Template template = null;
        // 加载模板，有些configFormat不需要模板
        if (tplName != null) {
            template = config.getTemplate(tplName);
        }
        
        String content = renderTemplate(generators, template, configs);
        if (logger.isDebugEnabled()) {
            logger.debug("generate config file from tpl {}, content is: {}", tplName, redactSecrets(content));
        }
        
        writeContent(generators, configs, serviceInstallHome, content);
        
    }
    
    private static Configuration initConfiguration(String extPath) throws IOException {
        // 创建核心配置对象
        Configuration config = new Configuration(Configuration.getVersion());
        List<TemplateLoader> loaderList = new ArrayList<>();
        
        String masterHost = PropertyUtils.getString(Constants.MASTER_HOST);
        String masterPort = PropertyUtils.getString(Constants.MASTER_WEB_PORT);
        // 安装包的模板优先
        if (StringUtils.isNotBlank(extPath) && new File(extPath).exists()) {
            // 如果 三方的 package 中存在 templates 模版，则直接加载
            loaderList.add(new FileTemplateLoader(new File(extPath)));
        }
        // master的下发的模板优先
        loaderList.add(new RemoteTemplateLoader(String.format("http://%s:%s", masterHost, masterPort)));
        loaderList.add(new ClassTemplateLoader(FreemakerUtils.class, "/templates"));
        
        MultiTemplateLoader loader = new MultiTemplateLoader(loaderList.toArray(new TemplateLoader[0]));
        loader.setSticky(false);
        config.setTemplateLoader(loader);
        return config;
    }
    
    /**
     * 获取模板名
     *
     */
    private static String determinateTplName(Generators generators) {
        String configFormat = generators.getConfigFormat();
        // 旧的代码，为了兼容，直接硬编码文件 //
        // ---------------开始------------------------- //
        if (Constants.XML.equals(configFormat)) {
            return "xml.ftl";
        }
        if (Constants.PROPERTIES.equals(configFormat)) {
            return "properties.ftl";
        }
        if (Constants.PROPERTIES2.equals(configFormat)) {
            return "properties2.ftl";
        }
        if (Constants.PROPERTIES3.equals(configFormat)) {
            return "properties3.ftl";
        }
        if (Constants.YAML.equals(configFormat)) {
            // 直接根据字段生成，无需模板
            return null;
        }
        // 旧的代码，为了兼容，直接硬编码文件 //
        // ---------------结束-----------------------
        
        return generators.getTemplateName();
    }
    
    /**
     * TODO 改为SPI实现
     * 渲染模板。
     * 注意保留public，方便写单元测试代码
     *
     */
    public static String renderTemplate(Generators generators, Template template, List<ServiceConfig> configs) throws TemplateException, IOException {
        String configFormat = generators.getConfigFormat();
        if (Constants.YAML.equals(configFormat)) {
            return renderYaml(generators, configs);
        }
        if (Constants.CUSTOM.equals(configFormat)) {
            return renderCustomConfigFormat(template, configs);
        }
        
        return renderDefaultConfigFormat(template, configs);
        
    }
    
    public static String renderCustomConfigFormat(Template template, List<ServiceConfig> configs) throws TemplateException, IOException {
        Map<String, Object> data = new HashMap<>();
        
        // 添加内置变量, see commitId: 4420c26b96fc88d8a74db5b3053beae67f6197c9
        data.put("ip", InetAddress.getLocalHost().getHostAddress());
        data.put("host", InetAddress.getLocalHost().getHostName());
        
        // “map”为自定义属性
        configs.stream().filter(e -> "map".equals(e.getConfigType())).forEach(config -> {
            // 阮伟儿自定义的属性，优先级高于name
            if (StrUtil.isNotBlank(config.getKey())) {
                data.put(config.getKey(), config.getValue());
            } else {
                data.put(config.getName(), config.getValue());
            }
        });
        
        data.put("itemList", configs.stream().filter(e -> !"map".equals(e.getConfigType())).toList());
        StringWriter out = new StringWriter();
        template.process(data, out);
        return out.toString();
        
    }
    
    public static String renderYaml(Generators generators, List<ServiceConfig> configs) {
        // 只保留generator要求的变量，其他变量均过滤掉。用于解决以下业务场景：
        // ConfigureServiceHandler除了includeParams外，还会添加其他的额外的变量。
        // 这样子生成的yaml会包含这些额外的变量，部分软件(如apisix)在启动时，会检查配置文件，多余的配置项会报错
        List<String> includeParams = generators.getIncludeParams();
        List<ServiceConfig> finalConfigs = configs.stream()
                .filter(config -> includeParams.contains(config.getName()))
                .toList();
        
        Map<String, Object> configMap = new LinkedHashMap<>();
        finalConfigs.stream().forEach(serviceConfig -> {
            String key = StringUtils.isEmpty(serviceConfig.getKey()) ? serviceConfig.getName() : serviceConfig.getKey();
            configMap.put(key, serviceConfig.getValue());
        });
        return YamlParser.flattenedMapToYaml(configMap);
    }
    
    public static String renderDefaultConfigFormat(Template template, List<ServiceConfig> configs) throws TemplateException, IOException {
        // default render
        Map<String, Object> data = new HashMap<>();
        data.put("itemList", configs);
        StringWriter out = new StringWriter();
        template.process(data, out);
        return out.toString();
    }
    
    /**
     * TODO 改为SPI实现
     *
     * @throws UnknownHostException
     */
    private static void writeContent(Generators generators, List<ServiceConfig> configs, String serviceInstallHome, String content) throws UnknownHostException {
        String protocol = generators.getType();
        // 默认写文件
        if (protocol == null) {
            writeContentToFile(generators, serviceInstallHome, content);
            return;
        }
        
        if (Constants.NACOS.equals(protocol)) {
            writeContentToNacos(generators, configs, content);
            return;
        }
        
        throw new IllegalArgumentException(String.format("unknown type:%s, 请检查service-ddl.json文件的filename属性为%s的generators的配置属性", protocol, generators.getFilename()));
    }
    
    private static void writeContentToFile(Generators generators, String decompressPackageName, String content) {
        String packagePath = Constants.INSTALL_PATH + Constants.SLASH + decompressPackageName + Constants.SLASH;
        String outputDirectory = generators.getOutputDirectory();
        for (String outPutDir : generators.getOutputDirectory().split(StrUtil.COMMA)) {
            String outputFile = (outputDirectory.startsWith(Constants.SLASH) ? "" : packagePath) + outPutDir + Constants.SLASH + generators.getFilename();
            File file = new File(outputFile);
            if (!file.exists()) {
                FileUtil.mkParentDirs(file);
            }
            FileUtil.writeString(content, file, StandardCharsets.UTF_8);
            logger.info("成功生成配置文件{}，写入位置为{}", generators.getFilename(), file.getAbsolutePath());
            
            if (generators.getFilename().endsWith(SH) && !file.canExecute()) {
                file.setExecutable(true);
            }
        }
    }
    
    private static void writeContentToNacos(Generators generators, List<ServiceConfig> configs, String content) throws UnknownHostException {
        String[] split = generators.getOutputDirectory().split(":");
        
        Map<String, Object> data = new HashMap<>();
        
        // 添加内置变量, see commitId: 4420c26b96fc88d8a74db5b3053beae67f6197c9
        data.put("ip", InetAddress.getLocalHost().getHostAddress());
        data.put("host", InetAddress.getLocalHost().getHostName());
        
        configs.stream().filter(e -> "map".equals(e.getConfigType())).forEach(config -> {
            // 阮伟儿自定义的属性，优先级高于name
            if (StrUtil.isNotBlank(config.getKey())) {
                data.put(config.getKey(), config.getValue());
            } else {
                data.put(config.getName(), config.getValue());
            }
        });
        String username = parseValue(data, split[0]);
        String password = parseValue(data, split[1]);
        String host = parseValue(data, split[2]);
        String port = parseValue(data, split[3]);
        String profile = parseValue(data, split[4]);
        String group = parseValue(data, split[5]);
        logger.info("解析nacos配置参数outputDirectory, host:{},port:{}, namespace: {}, group: {}", host, port, profile, group);
        Properties properties = new Properties();
        properties.put(PropertyKeyConst.SERVER_ADDR, host);
        properties.put(PropertyKeyConst.ENDPOINT_PORT, port);
        properties.put(PropertyKeyConst.USERNAME, username);
        properties.put(PropertyKeyConst.PASSWORD, password);
        properties.put(PropertyKeyConst.NAMESPACE, profile);
        // 检查命名空间
        createNacosNamespaceIfAbsent(properties);
        
        String filename = generators.getFilename();
        String dataType = FileUtil.getSuffix(filename);
        dataType = dataType == null ? null : dataType.toLowerCase();
        if ("yml".equalsIgnoreCase(dataType)) {
            dataType = "yaml";
        }
        if (!ConfigType.isValidType(dataType)) {
            dataType = ConfigType.getDefaultType().getType();
        }
        
        publishConfig(properties, content, group, filename, dataType);
        logger.info("成功生成配置文件{}，写入nacos: url:{}:{}, namespace {}, group: {} 成功", generators.getFilename(), host, port, profile, group);
    }
    
    public static String parseValue(Map<String, Object> data, String str) {
        String value = str;
        if (str.startsWith("$")) {
            String key = str.substring(2, str.length() - 1);
            if (data.containsKey(key)) {
                value = data.get(key).toString();
            } else {
                throw new RuntimeException(key + "获取值失败");
            }
        }
        return value;
    }
    
    private static void createNacosNamespaceIfAbsent(Properties properties) {
        try {
            String profile = properties.get(PropertyKeyConst.NAMESPACE).toString();
            String namespacesUrl = "/nacos/v1/console/namespaces";
            String url = "http://" + properties.get(PropertyKeyConst.SERVER_ADDR).toString() + ":" + properties.get(PropertyKeyConst.ENDPOINT_PORT).toString();
            Header header = NamingHttpUtil.builderHeader();
            HttpRestResult<String> result = nacosRestTemplate.get(url + namespacesUrl, header, Query.newInstance().initParams(new HashMap<>()), String.class);
            if (result.getCode() == 200) {
                logger.info("检查命名空间");
                String data = result.getData();
                JSONArray jsonArray = JSONUtil.parseObj(data).getJSONArray("data");
                boolean anyMatch = jsonArray.stream().anyMatch(str -> Objects.equals(JSONUtil.parseObj(str).getStr("namespace"), profile));
                if (!anyMatch) {
                    logger.info("创建命名空间:{}", profile);
                    Map<String, String> params = new HashMap<>();
                    params.put("customNamespaceId", profile);
                    params.put("namespaceName", profile);
                    params.put("namespaceDesc", profile);
                    params.put(PropertyKeyConst.SERVER_ADDR, properties.get(PropertyKeyConst.SERVER_ADDR).toString());
                    params.put(PropertyKeyConst.ENDPOINT_PORT, properties.get(PropertyKeyConst.ENDPOINT_PORT).toString());
                    params.put(PropertyKeyConst.USERNAME, properties.get(PropertyKeyConst.USERNAME).toString());
                    params.put(PropertyKeyConst.PASSWORD, properties.get(PropertyKeyConst.PASSWORD).toString());
                    HttpRestResult<Object> postForm = nacosRestTemplate.postForm(url + namespacesUrl, header, params, String.class);
                    if (postForm.getCode() != 200) {
                        logger.error("创建命名空间{}失败", profile);
                    }
                }
            } else {
                logger.error("检查命名空间{}失败", profile);
            }
        } catch (Exception e) {
            logger.error("检查命名空间失败:", e);
            throw new RuntimeException(e);
        }
    }
    
    private static void publishConfig(Properties properties, String content, String group, String dataId, String type) {
        logger.info("写入nacos配置");
        try {
            ConfigService configService = NacosFactory.createConfigService(properties);
            configService.publishConfig(dataId, group, content, type);
        } catch (NacosException e) {
            logger.error("写入nacos配置失败:", e);
            throw new RuntimeException(e);
        }
    }
    
    private static void processOut(Generators generators, Template template, Map<String, Object> data,
                                   String decompressPackageName) throws IOException, TemplateException {
        String packagePath = Constants.INSTALL_PATH + Constants.SLASH + decompressPackageName + Constants.SLASH;
        String outputDirectory = generators.getOutputDirectory();
        for (String outPutDir : generators.getOutputDirectory().split(StrUtil.COMMA)) {
            String outputFile = (outputDirectory.startsWith(Constants.SLASH) ? "" : packagePath) + outPutDir + Constants.SLASH + generators.getFilename();
            File file = writeToTemplate(template, data, outputFile);
            if (generators.getFilename().endsWith(SH) && !file.canExecute()) {
                file.setExecutable(true);
            }
        }
    }
    
    private static File writeToTemplate(Template template, Map<String, Object> data,
                                        String outputFile) throws IOException, TemplateException {
        File file = new File(outputFile);
        if (!file.exists()) {
            FileUtil.mkParentDirs(file);
        }
        FileWriter out = new FileWriter(file);
        template.process(data, out);
        out.close();
        return file;
    }
    
}
