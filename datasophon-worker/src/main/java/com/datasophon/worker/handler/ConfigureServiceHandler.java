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

package com.datasophon.worker.handler;

import com.datasophon.common.Constants;
import com.datasophon.common.command.GenerateServiceConfigCommand;
import com.datasophon.common.command.ServiceRoleResource;
import com.datasophon.common.model.Generators;
import com.datasophon.common.model.RunAs;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.common.utils.PlaceholderUtils;
import com.datasophon.common.utils.ShellUtils;
import com.datasophon.worker.utils.FreemakerUtils;
import com.datasophon.worker.utils.TaskConstants;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import freemarker.template.TemplateException;
import freemarker.template.TemplateNotFoundException;
import lombok.Data;

@Data
public class ConfigureServiceHandler {

    private static final String RANGER_ADMIN = "RangerAdmin";

    public static final String SH = "sh";

    private String serviceName;

    private String serviceRoleName;

    private Logger logger;

    public ConfigureServiceHandler(String serviceName, String serviceRoleName) {
        this.serviceName = serviceName;
        this.serviceRoleName = serviceRoleName;
        String loggerName = TaskConstants.createLoggerName(serviceName, serviceRoleName, ConfigureServiceHandler.class);
        logger = LoggerFactory.getLogger(loggerName);
    }

    public ExecResult configure(ServiceRoleResource srvRoleResource, GenerateServiceConfigCommand command) {
        ExecResult execResult = new ExecResult();
        try {
            String pkgInstallHome = PkgInstallPathUtils.getInstallHomeName(srvRoleResource);
            Map<String, String> paramMap = getExtraParams(srvRoleResource, command);
            // 软件安装路径的相关变量

            Map<Generators, List<ServiceConfig>> cofigFileMap = command.getCofigFileMap();
            logger.info("开始生成服务{} {}的配置文件", srvRoleResource.getServiceName(), srvRoleResource.getServiceRoleName());
            for (Generators generators : cofigFileMap.keySet()) {
                logger.info("开始生成配置文件: {}, 当前模板为{}", generators.getFilename(), generators.getTemplateName());
                List<ServiceConfig> configs = cofigFileMap.get(generators);
                ArrayList<ServiceConfig> customConfList = CollUtil.newArrayList();
                Iterator<ServiceConfig> iterator = configs.iterator();
                while (iterator.hasNext()) {
                    ServiceConfig config = iterator.next();
                    if (!config.isEnabled()) {
                        logger.warn("配置项{}未启用, 忽略该值。", config.getName());
                        iterator.remove();
                        continue;
                    }

                    if (StringUtils.isNotBlank(config.getType())) {
                        replacePlaceholder(config, paramMap);
                    }
                    if (Constants.PATH.equals(config.getConfigType())) {
                        createPath(config, command.getRunAs());
                    }
                    if (Constants.MV_PATH.equals(config.getConfigType())) {
                        movePath(config, command.getRunAs());
                    }
                    if (Constants.CUSTOM.equals(config.getConfigType())) {
                        addToCustomList(iterator, customConfList, config);
                    }
                    if (Constants.STRING_ARRAY.equals(config.getConfigType())) {
                        conventArray(config);
                    } else if (Constants.NUMBER.equals(config.getConfigType())) {
                        conventNumber(config);
                    } else if (config.getValue() instanceof Boolean || config.getValue() instanceof Integer) {
                        logger.info("Convert boolean and integer to string");
                        config.setValue(config.getValue().toString());
                    }

                    if ("dataDir".equals(config.getName())) {
                        String dataDir = (String) config.getValue();
                        if (Objects.nonNull(command.getMyid()) && StringUtils.isNotBlank(dataDir)) {
                            logger.info("write myid: {} to dataDir : {}", command.getMyid(), dataDir);
                            FileUtil.writeUtf8String(command.getMyid() + "", dataDir + Constants.SLASH + "myid");
                        }
                    }
                    if ("TrinoCoordinator".equals(serviceRoleName) && "coordinator".equals(config.getName())) {
                        logger.info("Start config trino coordinator");
                        config.setValue("true");
                        ServiceConfig serviceConfig = new ServiceConfig();
                        serviceConfig.setName("node-scheduler.include-coordinator");
                        serviceConfig.setValue("false");
                        customConfList.add(serviceConfig);
                    }
                    if ("fe_priority_networks".equals(config.getName()) || "be_priority_networks".equals(config.getName())) {
                        config.setName("priority_networks");
                    }

                    if ("KyuubiServer".equals(serviceRoleName) && "sparkHome".equals(config.getName())) {
                        // add hive-site.xml link in kerberos module
                        final String targetPath = Constants.INSTALL_PATH + File.separator + pkgInstallHome + "/conf/hive-site.xml";
                        if (!FileUtil.exist(targetPath)) {
                            logger.info("Add hive-site.xml link");
                            ExecResult result = ShellUtils.execShell("ln -s " + config.getValue() + "/conf/hive-site.xml " + targetPath);
                            if (!result.getExecResult()) {
                                logger.warn("Add hive-site.xml link failed,msg: {}", result.getExecErrOut());
                            }
                        }
                    }
                }

                if ("node.properties".equals(generators.getFilename())) {
                    ServiceConfig serviceConfig = new ServiceConfig();
                    serviceConfig.setName("node.id");
                    serviceConfig.setValue(IdUtil.simpleUUID());
                    customConfList.add(serviceConfig);
                }

                configs.addAll(customConfList);
                if (!configs.isEmpty()) {
                    // extra app, package: META, templates
                    File extTemplateDir = new File(Constants.INSTALL_PATH + File.separator + pkgInstallHome, "templates");
                    if (extTemplateDir.exists() && extTemplateDir.isDirectory()) {
                        // 3rd app, load ext templates
                        logger.info("Add ext app template path: {} to loader path.", extTemplateDir.getAbsolutePath());
                        FreemakerUtils.generateConfigFile(generators, configs, pkgInstallHome, extTemplateDir.getAbsolutePath());
                    } else {
                        FreemakerUtils.generateConfigFile(generators, configs, pkgInstallHome);
                    }
                } else if (!generators.getFilename().endsWith(SH)) {
                    String packagePath = Constants.INSTALL_PATH + Constants.SLASH + pkgInstallHome + Constants.SLASH;
                    String outputFile = packagePath + generators.getOutputDirectory() + Constants.SLASH + generators.getFilename();
                    FileUtil.writeUtf8String("", outputFile);
                }
                execResult.setExecOut("configure success");
                logger.info("生成配置文件{}成功!", generators.getFilename());
            }
            if (RANGER_ADMIN.equals(serviceRoleName) && !setupRangerAdmin(pkgInstallHome)) {
                return execResult;
            }
            execResult.setExecResult(true);
        } catch (Exception e) {
            execResult.setExecErrOut(e.getMessage());
            if (e instanceof TemplateNotFoundException ex) {
                logger.error("生成服务{} {}的配置失败, 模板{}不存在, 信息：{}", srvRoleResource.getServiceName(), srvRoleResource.getServiceRoleName(),
                        ex.getTemplateName(), e.getMessage(), e);
            } else if (e instanceof TemplateException ex) {
                logger.error("生成服务{} {}的配置失败, \n\t模板名称{},\n\t出错行数{}， \n\t出错列数{}, \n\t原因:{}, \n\t出错细节：\n",
                        srvRoleResource.getServiceName(), srvRoleResource.getServiceRoleName(),
                        ex.getTemplateSourceName(), ex.getLineNumber(), ex.getColumnNumber(), ex.getMessage(), e);
            } else {
                logger.error("生成服务{} {}的配置失败, {}", srvRoleResource.getServiceName(), srvRoleResource.getServiceRoleName(), e.getMessage(), e);
            }
        }
        return execResult;
    }

    private Map<String, String> getExtraParams(ServiceRoleResource srvRoleResource, GenerateServiceConfigCommand command) throws UnknownHostException {
        String hostName = InetAddress.getLocalHost().getHostName();
        String ip = InetAddress.getLocalHost().getHostAddress();
        Map<String, String> extraParams = new HashMap<>();
        extraParams.put("${clusterId}", String.valueOf(command.getClusterId()));
        extraParams.put("${host}", hostName);
        extraParams.put("${ip}", ip);
        extraParams.put("${user}", "root");
        extraParams.put("${myid}", String.valueOf(command.getMyid()));
        extraParams.put(PkgInstallPathUtils.getRoleInstallHomeKey(srvRoleResource), PkgInstallPathUtils.getInstallHome(srvRoleResource));
        extraParams.put(PkgInstallPathUtils.getInstallHomeKey(srvRoleResource), PkgInstallPathUtils.getInstallHome(srvRoleResource));

        return extraParams;
    }

    private boolean setupRangerAdmin(String decompressPackageName) {
        logger.info("start to execute ranger admin setup.sh");
        ArrayList<String> commands = new ArrayList<>();
        commands.add(Constants.INSTALL_PATH + Constants.SLASH + decompressPackageName + Constants.SLASH + "setup.sh");
        ExecResult execResult = ShellUtils.exec(Constants.INSTALL_PATH + Constants.SLASH + decompressPackageName, commands, 300L);

        ArrayList<String> globalCommand = new ArrayList<>();
        globalCommand.add(Constants.INSTALL_PATH + Constants.SLASH + decompressPackageName + Constants.SLASH + "set_globals.sh");
        ShellUtils.execWithStatus(Constants.INSTALL_PATH + Constants.SLASH + decompressPackageName, globalCommand, 300L, logger);
        if (execResult.getExecResult()) {
            logger.info("ranger admin setup success");
            return true;
        }
        logger.info("ranger admin setup failed");
        return false;
    }

    private void replacePlaceholder(ServiceConfig config, Map<String, String> paramMap) {
        logger.info("handle config value, key: {}", config.getName());
        switch (config.getType()) {
            case Constants.INPUT:
                Object tempVal = config.getValue();
                if (String.class.isAssignableFrom(tempVal.getClass())) {
                    String value = PlaceholderUtils.replacePlaceholders((String) tempVal, paramMap, Constants.REGEX_VARIABLE);
                    config.setValue(value);
                }
                break;
            case Constants.MULTIPLE:
                if (config.getSeparator() == null) {
                    throw new IllegalStateException(String.format("配置项%s配置有误, 类型为multiple要求分割符(separator)不能为空。", config.getName()));
                }
                JSONArray value2 = (JSONArray) config.getValue();
                List<String> valueList = value2.toJavaList(String.class);
                valueList = valueList.stream()
                        .map(val -> PlaceholderUtils.replacePlaceholdersRecursive(val, paramMap, Constants.REGEX_VARIABLE))
                        .toList();
                String joinValue = String.join(config.getSeparator(), valueList);
                config.setValue(joinValue);
                break;

            case Constants.MULTIPLE_WITH_MAP:
                // 忽略异常值
                if (config.getValue() == null || config.getValue() instanceof String) {
                    break;
                }
                List<JSONObject> list = (List<JSONObject>) config.getValue();
                for (JSONObject item : list) {
                    // create a copy set to prevent ConcurrentModificationException
                    Set<String> keys = new HashSet<>(item.keySet());
                    for (String oldKey : keys) {
                        String newKey = PlaceholderUtils.replacePlaceholders(oldKey, paramMap, Constants.REGEX_VARIABLE);
                        Object targetValue = item.get(oldKey);
                        if (targetValue instanceof String) {
                            targetValue = PlaceholderUtils.replacePlaceholders((String) targetValue, paramMap, Constants.REGEX_VARIABLE);
                        } else if (targetValue instanceof JSONObject) {
                            String json = ((JSONObject) targetValue).toJSONString();
                            json = PlaceholderUtils.replacePlaceholders(json, paramMap, Constants.REGEX_VARIABLE);
                            targetValue = JSONObject.parse(json);
                        }
                        item.remove(oldKey);
                        item.put(newKey, targetValue);
                    }
                }
        }
        logger.info("config {} set value to {}", config.getName(), config.getValue());
        if (!"map".equals(config.getConfigType())) {
            String refName = StrUtil.isBlank(config.getKey()) ? config.getName() : config.getKey();
            logger.warn("配置项{}的configType不是‘map’，在模板中，需要通过key值为: itemList[$index].{} 使用", config.getName(), refName);
        }
    }

    private void createPath(ServiceConfig config, RunAs runAs) {
        String path = (String) config.getValue();
        if (StringUtils.isNotBlank(config.getSeparator()) && path.contains(config.getSeparator())) {
            for (String dir : path.split(config.getSeparator())) {
                mkdir(dir, runAs);
            }
        } else {
            mkdir(path, runAs);
        }
    }

    private void movePath(ServiceConfig config, RunAs runAs) {
        String oldPath = (String) config.getDefaultValue();
        String newPath = (String) config.getValue();
        if (FileUtil.exist(oldPath) && !FileUtil.exist(newPath)) {
            if (StringUtils.isNotBlank(config.getSeparator()) && newPath.contains(config.getSeparator())) {
                for (String dir : newPath.split(config.getSeparator())) {
                    mkdir(dir, runAs);
                }
            } else {
                mkdir(newPath, runAs);
            }
            FileUtil.move(new File(oldPath), new File(newPath), false);
            logger.info("move path {} to {}", oldPath, newPath);
        }
    }

    private void addToCustomList(Iterator<ServiceConfig> iterator, ArrayList<ServiceConfig> customConfList, ServiceConfig config) {
        iterator.remove();

        // 部分ddl的value值乱写，导致转换失败，这段代码是为了去除value: "", value: null两个值
        if (config.getValue() == null) {
            return;
        }
        if (config.getValue() instanceof String) {
            if (StrUtil.isBlank(config.getValue().toString())) {
                return;
            }
        }

        List<JSONObject> list = (List<JSONObject>) config.getValue();
        for (JSONObject json : list) {
            if (Objects.nonNull(json)) {
                Set<String> set = json.keySet();
                for (String key : set) {
                    if (StringUtils.isNotBlank(key)) {
                        ServiceConfig serviceConfig = new ServiceConfig();
                        serviceConfig.setName(key);
                        serviceConfig.setValue(json.get(key));
                        customConfList.add(serviceConfig);
                    }
                }
            }
        }
    }

    private void conventArray(ServiceConfig config) {
        Object value = config.getValue();
        if (value instanceof String) {
            String separator = StringUtils.isNotEmpty(config.getSeparator()) ? config.getSeparator() : ",";
            config.setValue(((String) value).split(separator));
        }
    }

    private void conventNumber(ServiceConfig config) {
        Object value = config.getValue();
        if (value instanceof String) {
            config.setValue(Long.valueOf((String) value));
        }
    }

    private void mkdir(String path, RunAs runAs) {
        if (!FileUtil.exist(path)) {
            logger.info("create file path {}", path);
            FileUtil.mkdir(path);
            ShellUtils.addChmod(path, "775");
            if (Objects.nonNull(runAs)) {
                ShellUtils.addChown(path, runAs.getUser(), runAs.getGroup());
            }
        }
    }
}
