/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.datasophon.worker.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.client.naming.remote.http.NamingHttpClientManager;
import com.alibaba.nacos.client.naming.utils.NamingHttpUtil;
import com.alibaba.nacos.common.http.HttpRestResult;
import com.alibaba.nacos.common.http.client.NacosRestTemplate;
import com.alibaba.nacos.common.http.param.Header;
import com.alibaba.nacos.common.http.param.Query;
import com.datasophon.common.Constants;
import com.datasophon.common.model.AlertItem;
import com.datasophon.common.model.Generators;
import com.datasophon.common.model.ServiceConfig;
import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.FileTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

import static com.datasophon.worker.handler.ConfigureServiceHandler.SH;

public class FreemakerUtils {

    private static final Logger logger = LoggerFactory.getLogger(FreemakerUtils.class);

    private static final NacosRestTemplate nacosRestTemplate = NamingHttpClientManager.getInstance().getNacosRestTemplate();

    public static void generateConfigFile(Generators generators,
                                          List<ServiceConfig> configs,
                                          String decompressPackageName) throws IOException, TemplateException {
        generateConfigFile(generators, configs, decompressPackageName, null);
    }

    public static void generateConfigFile(Generators generators,
                                          List<ServiceConfig> configs,
                                          String decompressPackageName,
                                          String extPath) throws IOException, TemplateException {
        // 1.加载模板
        // 创建核心配置对象
        Configuration config = new Configuration(Configuration.getVersion());
        // 设置加载的目录
        List<TemplateLoader> loaderList = new ArrayList<>();
        loaderList.add(new ClassTemplateLoader(FreemakerUtils.class, "/templates"));
        if (StringUtils.isNotBlank(extPath) && new File(extPath).exists()) {
            // 如果 三方的 package 中存在 templates 模版，则直接加载
            loaderList.add(new FileTemplateLoader(new File(extPath)));
        }
        config.setTemplateLoader(new MultiTemplateLoader(loaderList.toArray(new TemplateLoader[0])));


        List<ServiceConfig> scs = configs.stream().map(serviceConfig -> {
            ServiceConfig sc = new ServiceConfig();
            BeanUtil.copyProperties(serviceConfig, sc);
            if (StringUtils.isNotEmpty(sc.getKey())) {
                sc.setName(sc.getKey());
            }
            return sc;
        }).collect(Collectors.toCollection(ArrayList::new));

        Map<String, Object> data = new HashMap<>();
        // 得到模板对象
        String configFormat = generators.getConfigFormat();
        Template template = null;
        if (Constants.XML.equals(configFormat)) {
            template = config.getTemplate("xml.ftl");
        }
        if (Constants.PROPERTIES.equals(configFormat)) {
            template = config.getTemplate("properties.ftl");
        }
        if (Constants.PROPERTIES2.equals(configFormat)) {
            template = config.getTemplate("properties2.ftl");
        }
        if (Constants.PROPERTIES3.equals(configFormat)) {
            template = config.getTemplate("properties3.ftl");
        }
        if (Constants.PROMETHEUS.equals(configFormat)) {
            template = config.getTemplate("alert.yml");
        }
        if (Constants.YAML.equals(configFormat)) {
            generateYaml(generators, scs, decompressPackageName);
            return;
        }
        if (Constants.CUSTOM.equals(configFormat)) {
            // 添加内置变量
            String hostName = InetAddress.getLocalHost().getHostName();
            String ip = InetAddress.getLocalHost().getHostAddress();
            ServiceConfig hostConfig = new ServiceConfig();
            hostConfig.setName("host");
            hostConfig.setConfigType("map");
            hostConfig.setValue(hostName);
            ServiceConfig ipConfig = new ServiceConfig();
            ipConfig.setName("ip");
            ipConfig.setConfigType("map");
            ipConfig.setValue(ip);
            scs.add(hostConfig);
            scs.add(ipConfig);

            template = config.getTemplate(generators.getTemplateName());
            data = scs.stream().filter(e -> "map".equals(e.getConfigType()))
                    .collect(Collectors.toMap(ServiceConfig::getName,
                            ServiceConfig::getValue));
            if (Constants.NACOS.equals(generators.getType())) {
                logger.info("生成nacos配置");
                String outputDirectory = generators.getOutputDirectory();
                if (StrUtil.isNotEmpty(outputDirectory)) {
                    String[] split = outputDirectory.split(":");
                    String username = checkValue(data, split[0]);
                    String password = checkValue(data, split[1]);
                    String host = checkValue(data, split[2]);
                    String port = checkValue(data, split[3]);
                    String profile = checkValue(data, split[4]);
                    String group = checkValue(data, split[5]);
                    logger.info("解析nacos配置参数outputDirectory,host:{},port:{}", host, port);
                    Properties properties = new Properties();
                    properties.put(PropertyKeyConst.SERVER_ADDR, host);
                    properties.put(PropertyKeyConst.ENDPOINT_PORT, port);
                    properties.put(PropertyKeyConst.USERNAME, username);
                    properties.put(PropertyKeyConst.PASSWORD, password);
                    properties.put(PropertyKeyConst.NAMESPACE, profile);
                    // 检查命名空间
                    checkNamespace(properties);
                    StringWriter content = new StringWriter();
                    template.process(data, content);
                    String filename = generators.getFilename();
                    String dataId = filename.substring(filename.lastIndexOf(".") + 1);
                    publishConfig(properties, content.toString(), group, filename, dataId);
                    return;
                }
            }
            scs = scs.stream().filter(e -> !"map".equals(e.getConfigType())).collect(Collectors.toList());
        }
        logger.info("load template: {} success.", template.getSourceName());
        data.put("itemList", scs);
        // 3.产生输出
        processOut(generators, template, data, decompressPackageName);
    }

    public static String checkValue(Map<String, Object> data, String str) {
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

    private static void checkNamespace(Properties properties) {
        logger.info("检查命名空间");
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
                    logger.info("创建命名空间");
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
                        logger.error("创建命名空间失败");
                    }
                }
            } else {
                logger.error("检查命名空间失败");
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

    public static void generatePromAlertFile(Generators generators, List<AlertItem> configs,
                                             String serviceName) throws IOException, TemplateException {
        // 创建核心配置对象
        Configuration config = new Configuration(Configuration.getVersion());
        // 设置加载的目录
        // ""代表当前包
        config.setClassForTemplateLoading(FreemakerUtils.class, "/templates");
        // 得到模板对象
        String configFormat = generators.getConfigFormat();
        Template template = null;

        if (Constants.PROMETHEUS.equals(configFormat)) {
            template = config.getTemplate("alert.yml");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("itemList", configs);
        data.put("serviceName", serviceName);
        // 3.产生输出
        processOut(generators, template, data, "prometheus");
    }

    public static void generatePromScrapeConfig(Generators generators, List<ServiceConfig> configs,
                                                String serviceName) throws IOException, TemplateException {
        // 创建核心配置对象
        Configuration config = new Configuration(Configuration.getVersion());
        // 设置加载的目录
        // ""代表当前包
        config.setClassForTemplateLoading(FreemakerUtils.class, "/templates");
        // 得到模板对象
        Template template = config.getTemplate("scrape.ftl");

        Map<String, Object> data = new HashMap<>();
        data.put("itemList", configs);
        // 3.产生输出
        processOut(generators, template, data, serviceName);
    }

    public static void generateYaml(Generators generators, List<ServiceConfig> configs,
                                    String servicename) {
        Map<String, Object> configMap = new LinkedHashMap<>();
        configs.parallelStream().forEach(serviceConfig -> {
            String key = StringUtils.isEmpty(serviceConfig.getKey()) ? serviceConfig.getName() : serviceConfig.getKey();
            configMap.put(key, serviceConfig.getValue());
        });
        processYaml(generators, configMap, servicename);
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

    private static void writeToYaml(Map<String, Object> data,
                                    String outputFile) {
        String yaml = YamlParser.flattenedMapToYaml(data);
        File file = new File(outputFile);
        if (!file.exists()) {
            FileUtil.mkParentDirs(file);
        }
        FileUtil.writeUtf8String(yaml, file);
    }

    private static void processYaml(Generators generators, Map<String, Object> data,
                                    String decompressPackageName) {
        String packagePath = Constants.INSTALL_PATH + Constants.SLASH + decompressPackageName + Constants.SLASH;
        String outputDirectory = generators.getOutputDirectory();
        for (String outPutDir : generators.getOutputDirectory().split(StrUtil.COMMA)) {
            String outputFile = (outputDirectory.startsWith(Constants.SLASH) ? "" : packagePath) + outPutDir + Constants.SLASH + generators.getFilename();
            writeToYaml(data, outputFile);
        }
    }

//    public static void main(String[] args) throws IOException, TemplateException {
//        Map<String, Object> data = new HashMap<>();
//        Configuration config = new Configuration(Configuration.getVersion());
//        List<TemplateLoader> loaderList = new ArrayList<>();
//        loaderList.add(new ClassTemplateLoader(FreemakerUtils.class, "/templates"));
//        config.setTemplateLoader(new MultiTemplateLoader(loaderList.toArray(new TemplateLoader[0])));
//        Template template = config.getTemplate("juicefs-env.ftl");
//
//        data.put("juicefsMeta", "mysql://juicefs:juicefs@(${apiHost}:3306)/juicefs");
//        ServiceConfig sc = new ServiceConfig();
//        com.alibaba.fastjson.JSONArray array = new com.alibaba.fastjson.JSONArray();
//        JSONObject obj = new JSONObject();
//        obj.putOpt("path", "/opt/datasophon/juicefs/appweb");
//        obj.putOpt("log", "appweb");
//        array.add(obj);
//        sc.setValue(array);
//        data.put("juicefsMounts", sc);
//        StringWriter sw = new StringWriter();
//        template.process(data, sw);
//        sw.close();
//        System.out.println(sw);
//    }
}
