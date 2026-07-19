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

package com.datasophon.api.utils;

import com.datasophon.api.load.GlobalVariables;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterVariableService;
import com.datasophon.common.Constants;
import com.datasophon.common.model.Generators;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.utils.PlaceholderUtils;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceRoleGroupConfig;
import com.datasophon.dao.entity.ClusterVariable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

/** 服务配置与集群变量工具:配置合并、占位符替换、变量落库(原 ProcessUtils 拆出)。 */
public class ServiceConfigUtils {

    private static final Logger logger = LoggerFactory.getLogger(ServiceConfigUtils.class);

    private ServiceConfigUtils() {
    }

    public static void generateClusterVariable(Integer clusterId, String serviceName, String variableName, String value) {
        ClusterVariableService variableService = SpringTool.getApplicationContext().getBean(ClusterVariableService.class);
        ClusterVariable clusterVariable = variableService.getVariableByVariableName(clusterId, serviceName, variableName);
        if (Objects.nonNull(clusterVariable)) {
            logger.info("update variable {} value {} to {}", variableName, clusterVariable.getVariableValue(), value);
            clusterVariable.setServiceName(serviceName);
            clusterVariable.setVariableValue(value);
            variableService.updateById(clusterVariable);
        } else {
            ClusterVariable newClusterVariable = new ClusterVariable();
            newClusterVariable.setClusterId(clusterId);
            newClusterVariable.setServiceName(serviceName);
            newClusterVariable.setVariableName(variableName);
            newClusterVariable.setVariableValue(value);
            variableService.save(newClusterVariable);
        }

        // 内存立即写（同事务内的后续读取依赖新值）；若处于事务中，注册回滚补偿，
        // 事务回滚时把内存恢复到写前状态，保证 DB 与 GlobalVariables 一致。
        String key = serviceName + "." + variableName;
        String previousValue = GlobalVariables.getValue(clusterId, key);
        GlobalVariables.putValue(clusterId, key, value);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_ROLLED_BACK) {
                        if (previousValue != null) {
                            GlobalVariables.putValue(clusterId, key, previousValue);
                        } else {
                            GlobalVariables.removeValue(clusterId, key);
                        }
                    }
                }
            });
        }
    }

    /**
     * @Description: 生成configFileMap
     */
    public static void generateConfigFileMap(Map<Generators, List<ServiceConfig>> configFileMap,
                                             ClusterServiceRoleGroupConfig config, Integer clusterId) {
        String configFileJson = config.getConfigFileJson();
        if (configFileJson.stripLeading().startsWith("[")) {
            JSONArray entries = JSONArray.parseArray(configFileJson);
            for (Object value : entries) {
                JSONObject entry = (JSONObject) value;
                Generators generators = entry.getObject("generator", Generators.class);
                List<ServiceConfig> serviceConfigs = entry.getJSONArray("configs").toJavaList(ServiceConfig.class);
                addConfigFile(configFileMap, config, clusterId, generators, serviceConfigs);
            }
            return;
        }

        Map<JSONObject, JSONArray> map = JSONObject.parseObject(config.getConfigFileJson(), Map.class);
        for (JSONObject fileJson : map.keySet()) {
            Generators generators = fileJson.toJavaObject(Generators.class);
            List<ServiceConfig> serviceConfigs = map.get(fileJson).toJavaList(ServiceConfig.class);
            addConfigFile(configFileMap, config, clusterId, generators, serviceConfigs);
        }
    }

    public static String serializeConfigFileMap(Map<Generators, List<ServiceConfig>> configFileMap) {
        JSONArray entries = new JSONArray();
        configFileMap.forEach((generator, configs) -> {
            JSONObject entry = new JSONObject();
            entry.put("generator", generator);
            entry.put("configs", configs);
            entries.add(entry);
        });
        return entries.toJSONString();
    }

    private static void addConfigFile(
                                      Map<Generators, List<ServiceConfig>> configFileMap,
                                      ClusterServiceRoleGroupConfig config,
                                      Integer clusterId,
                                      Generators generators,
                                      List<ServiceConfig> serviceConfigs) {
        Map<String, String> variables = createMergeVariables(clusterId, config.getServiceName(), serviceConfigs);
        replaceVariable(serviceConfigs, variables);
        configFileMap.put(generators, serviceConfigs);
    }

    public static Map<String, String> createMergeVariables(Integer clusterId, String serviceName, List<ServiceConfig> serviceConfigs) {
        Map<String, String> variables = new HashMap<>(GlobalVariables.getVariables(clusterId));
        serviceConfigs.forEach(config -> {
            String name = config.getName();
            // 如果存在占位符，则忽略(即不支持递归占位符)。如果全局变量，也忽略(有可能已经被系统特殊逻辑处理）
            if (name.contains("${") || Boolean.TRUE.equals(config.getRegister())) {
                return;
            }
            if (config.getValue() instanceof String) {
                variables.putIfAbsent(String.format("${%s.%s}", serviceName, name), config.getValue().toString());
                variables.putIfAbsent(String.format("${%s}", name), config.getValue().toString());
            }
        });
        return variables;
    }

    private static void replaceVariable(List<ServiceConfig> serviceConfigs, Map<String, String> variables) {
        for (ServiceConfig serviceConfig : serviceConfigs) {
            serviceConfig.setOriginalName(serviceConfig.getName());
            String name = PlaceholderUtils.replacePlaceholders(serviceConfig.getName(), variables, Constants.REGEX_VARIABLE);
            serviceConfig.setName(name);
            if (Constants.INPUT.equals(serviceConfig.getType())) {
                Object value = serviceConfig.getValue();
                if (value != null && String.class.isAssignableFrom(value.getClass())) {
                    String value1 = PlaceholderUtils.replacePlaceholders((String) value, variables, Constants.REGEX_VARIABLE);
                    serviceConfig.setValue(value1);
                }
            }
            if (Constants.MULTIPLE.equals(serviceConfig.getType())) {
                JSONArray value2 = (JSONArray) serviceConfig.getValue();
                if (value2 != null) {
                    List<String> valueList = value2.toJavaList(String.class);
                    List<Object> tmpList = valueList.stream()
                            .map(val -> PlaceholderUtils.replacePlaceholdersRecursive(val, variables, Constants.REGEX_VARIABLE))
                            .collect(Collectors.toList());
                    serviceConfig.setValue(new JSONArray(tmpList));
                }
            }
            if (Constants.MULTIPLE_WITH_MAP.equals(serviceConfig.getType())) {
                // 忽略异常值
                if (serviceConfig.getValue() == null || serviceConfig.getValue() instanceof String) {
                    break;
                }
                List<JSONObject> list = (List<JSONObject>) serviceConfig.getValue();
                for (JSONObject item : list) {
                    Set<String> keys = new HashSet<>(item.keySet());
                    for (String oldKey : keys) {
                        String newKey = PlaceholderUtils.replacePlaceholders(oldKey, variables, Constants.REGEX_VARIABLE);
                        Object targetValue = item.get(oldKey);
                        if (targetValue instanceof String) {
                            targetValue = PlaceholderUtils.replacePlaceholders((String) targetValue, variables, Constants.REGEX_VARIABLE);
                        } else if (targetValue instanceof JSONObject) {
                            String json = ((JSONObject) targetValue).toJSONString();
                            json = PlaceholderUtils.replacePlaceholders(json, variables, Constants.REGEX_VARIABLE);
                            targetValue = JSONObject.parse(json);
                        }
                        item.remove(oldKey);
                        item.put(newKey, targetValue);
                    }
                }
            }
        }
    }

    public static ServiceConfig createServiceConfig(String configName, Object configValue, String type) {
        ServiceConfig serviceConfig = new ServiceConfig();
        serviceConfig.setName(configName);
        serviceConfig.setLabel(configName);
        serviceConfig.setValue(configValue);
        serviceConfig.setRequired(true);
        serviceConfig.setEnabled(true);
        serviceConfig.setHidden(false);
        serviceConfig.setType(type);
        return serviceConfig;
    }

    public static ClusterInfoEntity getClusterInfo(Integer clusterId) {
        ClusterInfoService clusterInfoService = SpringTool.getApplicationContext().getBean(ClusterInfoService.class);
        return clusterInfoService.getById(clusterId);
    }

    /**
     * 并集：左边集合与右边集合合并
     *
     */
    public static List<ServiceConfig> addAll(List<ServiceConfig> left, List<ServiceConfig> right) {
        if (left == null) {
            return null;
        }
        if (right == null) {
            return left;
        }
        // 使用LinkedList方便插入和删除
        List<ServiceConfig> res = new LinkedList<>(right);
        Set<String> set = new HashSet<>();
        //
        for (ServiceConfig item : left) {
            set.add(item.getName());
        }
        // 迭代器遍历listA
        for (ServiceConfig item : res) {
            // 如果set中包含id则remove
            if (!set.contains(item.getName())) {
                left.add(item);
            }
        }
        return left;
    }

    public static Map<String, ServiceConfig> translateToMap(List<ServiceConfig> list) {
        return list.stream()
                .collect(Collectors.toMap(ServiceConfig::getName, serviceConfig -> serviceConfig, (v1, v2) -> v1));
    }
}
