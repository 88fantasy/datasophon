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

package com.datasophon.api.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.datasophon.api.grpc.WorkerCommandClient;
import com.datasophon.api.master.transport.WorkerCallAdapter;
import com.datasophon.api.load.GlobalVariables;
import com.datasophon.api.load.ServiceConfigMap;
import com.datasophon.api.master.service.ServiceCommandService;
import com.datasophon.api.master.handler.service.ServiceConfigureHandler;
import com.datasophon.common.command.remote.CreateUnixGroupCommand;
import com.datasophon.common.command.remote.DelUnixGroupCommand;
import com.datasophon.api.master.handler.service.ServiceHandler;
import com.datasophon.api.master.handler.service.ServiceInstallHandler;
import com.datasophon.api.master.handler.service.ServiceStartHandler;
import com.datasophon.api.master.handler.service.ServiceStopHandler;
import com.datasophon.api.master.handler.service.ServiceUpgradeHandler;
import com.datasophon.api.service.ClusterAlertHistoryService;
import com.datasophon.api.service.ClusterInfoService;
import com.datasophon.api.service.ClusterServiceInstanceConfigService;
import com.datasophon.api.service.ClusterServiceInstanceRoleGroupService;
import com.datasophon.api.service.ClusterServiceInstanceService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.ClusterServiceRoleInstanceWebuisService;
import com.datasophon.api.service.ClusterVariableService;
import com.datasophon.api.service.ClusterZkService;
import com.datasophon.api.service.cmd.ClusterServiceCommandHostCommandService;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.common.Constants;
import com.datasophon.common.cache.CacheUtils;
import com.datasophon.common.command.ExecuteCmdCommand;
import com.datasophon.common.command.ExecuteServiceRoleCommand;
import com.datasophon.common.command.FileOperateCommand;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.enums.ServiceExecuteState;
import com.datasophon.common.enums.ServiceRoleType;
import com.datasophon.common.model.DAGGraph;
import com.datasophon.common.model.ExternalLink;
import com.datasophon.common.model.Generators;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceNode;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.common.model.StartWorkerMessage;
import com.datasophon.common.model.UpdateCommandHostMessage;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.HostUtils;
import com.datasophon.common.utils.PkgInstallPathUtils;
import com.datasophon.common.utils.PlaceholderUtils;
import com.datasophon.common.utils.PropertyUtils;
import com.datasophon.dao.entity.ClusterAlertHistory;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceInstanceConfigEntity;
import com.datasophon.dao.entity.ClusterServiceInstanceEntity;
import com.datasophon.dao.entity.ClusterServiceInstanceRoleGroup;
import com.datasophon.dao.entity.ClusterServiceRoleGroupConfig;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceWebuis;
import com.datasophon.dao.entity.ClusterVariable;
import com.datasophon.dao.entity.ClusterZk;
import com.datasophon.dao.entity.cmd.ClusterServiceCommandEntity;
import com.datasophon.dao.entity.cmd.ClusterServiceCommandHostCommandEntity;
import com.datasophon.dao.entity.cmd.ClusterServiceCommandHostEntity;
import com.datasophon.dao.enums.AlertLevel;
import com.datasophon.dao.enums.CommandState;
import com.datasophon.dao.enums.NeedRestart;
import com.datasophon.dao.enums.RoleType;
import com.datasophon.dao.enums.ServiceRoleState;
import com.datasophon.dao.enums.ServiceState;
import com.datasophon.domain.host.enums.HostState;
import com.datasophon.domain.host.enums.MANAGED;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class ProcessUtils {

    private static final Logger logger = LoggerFactory.getLogger(ProcessUtils.class);

    public static void saveServiceInstallInfo(ServiceRoleInfo serviceRoleInfo) {
        ClusterServiceInstanceService serviceInstanceService =
                SpringTool.getApplicationContext().getBean(ClusterServiceInstanceService.class);
        ClusterServiceInstanceConfigService serviceInstanceConfigService =
                SpringTool.getApplicationContext().getBean(ClusterServiceInstanceConfigService.class);
        ClusterServiceRoleInstanceService serviceRoleInstanceService =
                SpringTool.getApplicationContext().getBean(ClusterServiceRoleInstanceService.class);
        ClusterInfoService clusterInfoService = SpringTool.getApplicationContext().getBean(ClusterInfoService.class);
        ClusterServiceRoleInstanceWebuisService webuisService =
                SpringTool.getApplicationContext().getBean(ClusterServiceRoleInstanceWebuisService.class);
        ClusterServiceInstanceRoleGroupService roleGroupService =
                SpringTool.getApplicationContext().getBean(ClusterServiceInstanceRoleGroupService.class);

        ClusterInfoEntity clusterInfo = clusterInfoService.getById(serviceRoleInfo.getClusterId());

        ClusterServiceInstanceEntity clusterServiceInstance =
                serviceInstanceService.getServiceInstanceByClusterIdAndServiceName(serviceRoleInfo.getClusterId(),
                        serviceRoleInfo.getParentName());
        if (Objects.isNull(clusterServiceInstance)) {
            clusterServiceInstance = new ClusterServiceInstanceEntity();
            clusterServiceInstance.setClusterId(serviceRoleInfo.getClusterId());
            clusterServiceInstance.setServiceName(serviceRoleInfo.getParentName());
            clusterServiceInstance.setServiceState(ServiceState.RUNNING);
            clusterServiceInstance.setCreateTime(new Date());
            clusterServiceInstance.setUpdateTime(new Date());
            serviceInstanceService.save(clusterServiceInstance);
            // save config
            List<ServiceConfig> list = ServiceConfigMap.get(clusterInfo.getClusterCode() + Constants.UNDERLINE
                                                            + serviceRoleInfo.getParentName() + Constants.CONFIG);
            String config = JSON.toJSONString(list);
            ClusterServiceInstanceConfigEntity clusterServiceInstanceConfig = new ClusterServiceInstanceConfigEntity();
            clusterServiceInstanceConfig.setClusterId(serviceRoleInfo.getClusterId());
            clusterServiceInstanceConfig.setServiceId(clusterServiceInstance.getId());
            clusterServiceInstanceConfig.setConfigJson(config);
            clusterServiceInstanceConfig.setConfigJsonMd5(SecureUtil.md5(config));
            clusterServiceInstanceConfig.setConfigVersion(1);
            clusterServiceInstanceConfig.setCreateTime(new Date());
            clusterServiceInstanceConfig.setUpdateTime(new Date());
            serviceInstanceConfigService.save(clusterServiceInstanceConfig);


        } else {
            clusterServiceInstance.setNeedRestart(NeedRestart.NO);
            clusterServiceInstance.setServiceState(ServiceState.RUNNING);
            clusterServiceInstance.setServiceStateCode(ServiceState.RUNNING.getValue());
            serviceInstanceService.updateById(clusterServiceInstance);
        }


        Integer roleGroupId = (Integer) CacheUtils.get("UseRoleGroup_" + clusterServiceInstance.getId());
        ClusterServiceInstanceRoleGroup roleGroup = roleGroupService.getById(roleGroupId);
        // save role instance
        ClusterServiceRoleInstanceEntity roleInstanceEntity = serviceRoleInstanceService
                .getOneServiceRole(serviceRoleInfo.getName(), serviceRoleInfo.getHostname(), clusterInfo.getId());
        if (Objects.isNull(roleInstanceEntity)) {
            ClusterServiceRoleInstanceEntity roleInstance = new ClusterServiceRoleInstanceEntity();
            roleInstance.setServiceId(clusterServiceInstance.getId());
            roleInstance.setRoleType(CommonUtils.convertRoleType(serviceRoleInfo.getRoleType().getName()));
            roleInstance.setCreateTime(new Date());
            roleInstance.setHostname(serviceRoleInfo.getHostname());
            roleInstance.setClusterId(serviceRoleInfo.getClusterId());
            roleInstance.setServiceRoleName(serviceRoleInfo.getName());
            roleInstance.setServiceRoleState(ServiceRoleState.RUNNING);
            roleInstance.setUpdateTime(new Date());
            roleInstance.setServiceName(serviceRoleInfo.getParentName());
            roleInstance.setRoleGroupId(roleGroup.getId());
            roleInstance.setNeedRestart(NeedRestart.NO);
            serviceRoleInstanceService.save(roleInstance);
            if (Constants.ZKSERVER.equalsIgnoreCase(roleInstance.getServiceRoleName())) {
                ClusterZkService clusterZkService = SpringTool.getApplicationContext().getBean(ClusterZkService.class);
                ClusterZk clusterZk = new ClusterZk();
                clusterZk.setMyid((Integer) CacheUtils.get("zkserver_" + serviceRoleInfo.getHostname()));
                clusterZk.setClusterId(serviceRoleInfo.getClusterId());
                clusterZk.setZkServer(roleInstance.getHostname());
                clusterZkService.save(clusterZk);
            }
            roleInstanceEntity = roleInstance;
        }

        ExternalLink externalLink = serviceRoleInfo.getExternalLink();
        boolean externalLinkConfigDefined = externalLink != null
                                            && StrUtil.isNotBlank(externalLink.getUrl())
                                            && StrUtil.isNotBlank(externalLink.getName());
        ClusterServiceRoleInstanceWebuis webui = webuisService.getRoleInstanceWebUi(roleInstanceEntity.getId());
        if (externalLinkConfigDefined) {
            if (webui == null) {
                webui = new ClusterServiceRoleInstanceWebuis();
            }
            webui.setWebUrl(externalLink.getUrl());
            webui.setServiceInstanceId(clusterServiceInstance.getId());
            webui.setServiceRoleInstanceId(roleInstanceEntity.getId());
            webui.setName(externalLink.getName() + "(" + serviceRoleInfo.getHostname() + ")");
            webuisService.saveOrUpdate(webui);
        } else if (webui != null) {
            webuisService.removeById(webui.getId());
        }


        ProcessUtils.generateClusterVariable(
                serviceRoleInfo.getClusterId(), GlobalVariables.ROOT,
                String.format("%s.%s", serviceRoleInfo.getParentName(), GlobalVariables.INSTALL_PATH),
                PkgInstallPathUtils.getInstallHome(serviceRoleInfo)
        );
        ProcessUtils.generateClusterVariable(
                serviceRoleInfo.getClusterId(), serviceRoleInfo.getParentName(),
                String.format("%s.%s", serviceRoleInfo.getServiceRoleName(), GlobalVariables.INSTALL_PATH),
                PkgInstallPathUtils.getInstallHome(serviceRoleInfo)
        );
    }

    public static void saveHostInstallInfo(StartWorkerMessage message, String clusterCode,
                                           ClusterHostService clusterHostService) {
        ClusterInfoService clusterInfoService = SpringTool.getApplicationContext().getBean(ClusterInfoService.class);
        ClusterHostDO clusterHostDO = new ClusterHostDO();
        BeanUtil.copyProperties(message, clusterHostDO);

        ClusterInfoEntity cluster = clusterInfoService.getClusterByClusterCode(clusterCode);

        clusterHostDO.setClusterId(cluster.getId());
        clusterHostDO.setCheckTime(new Date());
        clusterHostDO.setRack("/default-rack");
        clusterHostDO.setNodeLabel("default");
        clusterHostDO.setCreateTime(new Date());
        clusterHostDO.setIp(HostUtils.getIp(message.getHostname()));
        clusterHostDO.setHostState(HostState.RUNNING);
        clusterHostDO.setManaged(MANAGED.YES);
        clusterHostService.save(clusterHostDO);
    }



    public static ClusterServiceCommandHostCommandEntity handleCommandResult(String hostCommandId, Boolean execResult,
                                                                             String execOut) {
        ClusterServiceCommandHostCommandService service =
                SpringTool.getApplicationContext().getBean(ClusterServiceCommandHostCommandService.class);

        ClusterServiceCommandHostCommandEntity hostCommand = service.getByHostCommandId(hostCommandId);
        hostCommand.setCommandProgress(100);
        if (execResult) {
            hostCommand.setCommandState(CommandState.SUCCESS);
            hostCommand.setResultMsg("success");
            logger.info("{} in {} success", hostCommand.getCommandName(), hostCommand.getHostname());
        } else {
            hostCommand.setCommandState(CommandState.FAILED);
            hostCommand.setResultMsg(execOut);
            logger.info("{} in {} failed", hostCommand.getCommandName(), hostCommand.getHostname());
        }
        service.updateByHostCommandId(hostCommand);
        // 更新command host进度
        // 更新command进度
        UpdateCommandHostMessage message = new UpdateCommandHostMessage();
        message.setExecResult(execResult);
        message.setCommandId(hostCommand.getCommandId());
        message.setCommandHostId(hostCommand.getCommandHostId());
        message.setHostname(hostCommand.getHostname());
        if (hostCommand.getServiceRoleType() == RoleType.MASTER) {
            message.setServiceRoleType(ServiceRoleType.MASTER);
        } else {
            message.setServiceRoleType(ServiceRoleType.WORKER);
        }

        ServiceCommandService commandService =
                SpringTool.getApplicationContext().getBean(ServiceCommandService.class);
        commandService.updateCommandHost(message);

        return hostCommand;
    }

    public static ClusterServiceCommandEntity generateCommandEntity(Integer clusterId, CommandType commandType,
                                                                    String serviceName) {
        ClusterServiceCommandEntity commandEntity = new ClusterServiceCommandEntity();
        String commandId = IdUtil.simpleUUID();
        commandEntity.setCommandId(commandId);
        commandEntity.setClusterId(clusterId);
        commandEntity.setCommandName(commandType.getCommandName(PropertyUtils.getString(Constants.LOCALE_LANGUAGE)) + Constants.SPACE + serviceName);
        commandEntity.setCommandProgress(0);
        commandEntity.setCommandState(CommandState.RUNNING);
        commandEntity.setCommandType(commandType.getValue());
        commandEntity.setCreateTime(new Date());
        commandEntity.setCreateBy("admin");
        commandEntity.setServiceName(serviceName);
        return commandEntity;
    }

    public static ClusterServiceCommandHostEntity generateCommandHostEntity(String commandId, String hostname) {
        ClusterServiceCommandHostEntity commandHost = new ClusterServiceCommandHostEntity();
        String commandHostId = IdUtil.simpleUUID();
        commandHost.setCommandHostId(commandHostId);
        commandHost.setCommandId(commandId);
        commandHost.setHostname(hostname);
        commandHost.setCommandState(CommandState.RUNNING);
        commandHost.setCommandProgress(0);
        commandHost.setCreateTime(new Date());

        return commandHost;
    }

    public static ClusterServiceCommandHostCommandEntity generateCommandHostCommandEntity(CommandType commandType,
                                                                                          String commandId,
                                                                                          String serviceRoleName,
                                                                                          RoleType serviceRoleType,
                                                                                          ClusterServiceCommandHostEntity commandHost) {
        ClusterServiceCommandHostCommandEntity hostCommand = new ClusterServiceCommandHostCommandEntity();
        String hostCommandId = IdUtil.simpleUUID();
        hostCommand.setHostCommandId(hostCommandId);
        hostCommand.setServiceRoleName(serviceRoleName);
        hostCommand.setCommandHostId(commandHost.getCommandHostId());
        hostCommand.setCommandState(CommandState.RUNNING);
        hostCommand.setCommandProgress(0);
        hostCommand.setHostname(commandHost.getHostname());
        hostCommand.setCommandName(commandType.getCommandName(PropertyUtils.getString(Constants.LOCALE_LANGUAGE))
                                   + Constants.SPACE + serviceRoleName);
        hostCommand.setCommandId(commandId);
        hostCommand.setCommandType(commandType.getValue());
        hostCommand.setServiceRoleType(serviceRoleType);
        hostCommand.setCreateTime(new Date());
        return hostCommand;
    }

    public static void updateServiceRoleState(CommandType commandType, String serviceRoleName, String hostname,
                                              Integer clusterId, ServiceRoleState serviceRoleState) {
        ClusterServiceRoleInstanceService serviceRoleInstanceService =
                SpringTool.getApplicationContext().getBean(ClusterServiceRoleInstanceService.class);
        ClusterServiceRoleInstanceEntity serviceRole =
                serviceRoleInstanceService.getOneServiceRole(serviceRoleName, hostname, clusterId);
        serviceRole.setServiceRoleState(serviceRoleState);
        serviceRole.setServiceRoleStateCode(serviceRoleState.getValue());
        if (commandType != CommandType.STOP_SERVICE) {
            serviceRole.setNeedRestart(NeedRestart.NO);
        }
        serviceRoleInstanceService.updateById(serviceRole);
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
        
        GlobalVariables.putValue(clusterId, serviceName + "." + variableName, value);
    }

    public static void hdfsEcMethond(Integer serviceInstanceId, ClusterServiceRoleInstanceService roleInstanceService,
                                     TreeSet<String> list, String type, String roleName) throws Exception {

        List<ClusterServiceRoleInstanceEntity> namenodes = roleInstanceService.lambdaQuery()
                .eq(ClusterServiceRoleInstanceEntity::getServiceId, serviceInstanceId)
                .eq(ClusterServiceRoleInstanceEntity::getServiceRoleName, roleName)
                .list();

        // 更新namenode节点的whitelist白名单
        WorkerCallAdapter adapter = SpringTool.getApplicationContext().getBean(WorkerCallAdapter.class);
        for (ClusterServiceRoleInstanceEntity namenode : namenodes) {
            FileOperateCommand fileOperateCommand = new FileOperateCommand();
            fileOperateCommand.setLines(list);
            fileOperateCommand.setPath(Constants.INSTALL_PATH + "/hadoop/etc/hadoop/" + type);
            ExecResult fileOperateResult = adapter.operateFile(namenode.getHostname(), fileOperateCommand);
            if (Objects.nonNull(fileOperateResult) && fileOperateResult.getExecResult()) {
                logger.info("write {} success in namenode {}", type, namenode.getHostname());
                // 刷新白名单
                ArrayList<String> refreshCmds = new ArrayList<>();
                refreshCmds.add(Constants.INSTALL_PATH + "/hadoop/bin/hdfs");
                refreshCmds.add("dfsadmin");
                refreshCmds.add("-refreshNodes");
                WorkerCommandClient workerCommandClient =
                        SpringTool.getApplicationContext().getBean(WorkerCommandClient.class);
                ExecResult execResult = workerCommandClient.executeCmd(namenode.getHostname(), refreshCmds);
                if (execResult.getExecResult()) {
                    logger.info("hdfs dfsadmin -refreshNodes success at {}", namenode.getHostname());
                }
            }
        }
    }


    public static String getExceptionMessage(Exception ex) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream pout = new PrintStream(out);
        ex.printStackTrace(pout);
        String ret = out.toString();
        pout.close();
        try {
            out.close();
        } catch (Exception ignored) {
        }
        return ret;
    }

    public static ExecResult restartService(ServiceRoleInfo serviceRoleInfo, boolean needReConfig) throws Exception {
        ServiceHandler serviceStartHandler = new ServiceStartHandler();
        ServiceHandler serviceStopHandler = new ServiceStopHandler();
        if (needReConfig) {
            ServiceConfigureHandler serviceConfigureHandler = new ServiceConfigureHandler();
            serviceStopHandler.setNext(serviceConfigureHandler);
            serviceConfigureHandler.setNext(serviceStartHandler);
        } else {
            serviceStopHandler.setNext(serviceStartHandler);
        }
        return serviceStopHandler.handlerRequest(serviceRoleInfo);
    }

    public static ExecResult startService(ServiceRoleInfo serviceRoleInfo, boolean needReConfig) throws Exception {
        ExecResult execResult;
        if (needReConfig) {
            ServiceConfigureHandler serviceHandler = new ServiceConfigureHandler();
            ServiceHandler serviceStartHandler = new ServiceStartHandler();
            serviceHandler.setNext(serviceStartHandler);
            execResult = serviceHandler.handlerRequest(serviceRoleInfo);
        } else {
            ServiceHandler serviceStartHandler = new ServiceStartHandler();
            execResult = serviceStartHandler.handlerRequest(serviceRoleInfo);
        }
        return execResult;
    }

    public static ExecResult stopService(ServiceRoleInfo serviceRoleInfo) throws Exception {
        ServiceHandler serviceStopHandler = new ServiceStopHandler();
        return serviceStopHandler.handlerRequest(serviceRoleInfo);
    }

    public static ExecResult startInstallService(ServiceRoleInfo serviceRoleInfo) throws Exception {
        ServiceHandler serviceInstallHandler = new ServiceInstallHandler();
        ServiceHandler serviceConfigureHandler = new ServiceConfigureHandler();


//        安装时，不见是否启动成功(部分软件，需要全部节点启动成功后，状态才能成功)
        ServiceStartHandler serviceStartHandler = new ServiceStartHandler();
        serviceStartHandler.setCheckStatus(false);

        serviceInstallHandler.setNext(serviceConfigureHandler);
        serviceConfigureHandler.setNext(serviceStartHandler);
        ExecResult execResult = serviceInstallHandler.handlerRequest(serviceRoleInfo);
        return execResult;
    }


    /**
     * 升级角色服务，操作链
     * 1. 停止服务
     * 2. 安装软件
     * 3. 生成配置
     * 4. 启动应用
     */
    public static ExecResult upgradeService(ServiceRoleInfo serviceRoleInfo) throws Exception {
        ServiceHandler handler = new ServiceStopHandler();

        handler
                .thenNext(new ServiceUpgradeHandler())
                .thenNext(new ServiceConfigureHandler())
                .thenNext(new ServiceStartHandler())
        ;

        ExecResult execResult = handler.handlerRequest(serviceRoleInfo);
        return execResult;
    }

    public static ExecResult configServiceRoleInstance(ClusterInfoEntity clusterInfo,
                                                       Map<Generators, List<ServiceConfig>> configFileMap,
                                                       ClusterServiceRoleInstanceEntity roleInstanceEntity) throws Exception {
        ServiceRoleInfo serviceRoleInfo = new ServiceRoleInfo();
        serviceRoleInfo.setName(roleInstanceEntity.getServiceRoleName());
        serviceRoleInfo.setParentName(roleInstanceEntity.getServiceName());
        serviceRoleInfo.setConfigFileMap(configFileMap);
        serviceRoleInfo
                .setDecompressPackageName(PackageUtils.getServiceDcPackageName(clusterInfo.getClusterFrame(), "YARN"));
        serviceRoleInfo.setHostname(roleInstanceEntity.getHostname());
        ServiceConfigureHandler configureHandler = new ServiceConfigureHandler();
        return configureHandler.handlerRequest(serviceRoleInfo);
    }


    /**
     * @param configFileMap
     * @param config
     * @Description: 生成configFileMap
     */
    public static void generateConfigFileMap(Map<Generators, List<ServiceConfig>> configFileMap,
                                             ClusterServiceRoleGroupConfig config, Integer clusterId) {
        Map<JSONObject, JSONArray> map = JSONObject.parseObject(config.getConfigFileJson(), Map.class);
        for (JSONObject fileJson : map.keySet()) {
            Generators generators = fileJson.toJavaObject(Generators.class);
            List<ServiceConfig> serviceConfigs = map.get(fileJson).toJavaList(ServiceConfig.class);
            Map<String, String> variables = createMergeVariables(clusterId, config.getServiceName(), serviceConfigs);
            replaceVariable(serviceConfigs, variables);
            configFileMap.put(generators, serviceConfigs);
        }
    }


    public static Map<String, String> createMergeVariables(Integer clusterId, String serviceName, List<ServiceConfig> serviceConfigs) {
        Map<String, String> variables = new HashMap<>(GlobalVariables.getVariables(clusterId));
        serviceConfigs.forEach(config-> {
            String name = config.getName();
//                如果存在占位符，则忽略(即不支持递归占位符)。如果全局变量，也忽略(有可能已经被系统特殊逻辑处理）
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

    private static void replaceVariable(List<ServiceConfig> serviceConfigs,Map<String, String>  variables) {
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
                            .map(val-> PlaceholderUtils.replacePlaceholdersRecursive(val, variables, Constants.REGEX_VARIABLE))
                            .collect(Collectors.toList());
                    serviceConfig.setValue(new JSONArray(tmpList));
                }
            }
            if (Constants.MULTIPLE_WITH_MAP.equals(serviceConfig.getType())) {
//                忽略异常值
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
                            String json = ((JSONObject)targetValue).toJSONString();
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
     * @param left
     * @param right
     * @return
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

    public static void syncUserGroupToHosts(List<ClusterHostDO> hostList, String groupName, String operate) {
        WorkerCallAdapter workerCallAdapter =
                SpringTool.getApplicationContext().getBean(WorkerCallAdapter.class);
        for (ClusterHostDO hostEntity : hostList) {
            try {
                if ("groupadd".equalsIgnoreCase(operate)) {
                    CreateUnixGroupCommand cmd = new CreateUnixGroupCommand();
                    cmd.setGroupName(groupName);
                    workerCallAdapter.createUnixGroup(hostEntity.getHostname(), cmd);
                } else {
                    DelUnixGroupCommand cmd = new DelUnixGroupCommand();
                    cmd.setGroupName(groupName);
                    workerCallAdapter.deleteUnixGroup(hostEntity.getHostname(), cmd);
                }
            } catch (Exception e) {
                logger.warn("syncUserGroupToHosts failed for host {}: {}", hostEntity.getHostname(), e.getMessage());
            }
        }
    }

    public static Map<String, ServiceConfig> translateToMap(List<ServiceConfig> list) {
        return list.stream()
                .collect(Collectors.toMap(ServiceConfig::getName, serviceConfig -> serviceConfig, (v1, v2) -> v1));
    }



    public static void recoverAlert(ClusterServiceRoleInstanceEntity roleInstanceEntity) {
        ClusterServiceRoleInstanceService roleInstanceService =
                SpringTool.getApplicationContext().getBean(ClusterServiceRoleInstanceService.class);
        ClusterAlertHistoryService alertHistoryService =
                SpringTool.getApplicationContext().getBean(ClusterAlertHistoryService.class);
        ClusterAlertHistory clusterAlertHistory = alertHistoryService.getOne(new QueryWrapper<ClusterAlertHistory>()
                .eq(Constants.ALERT_TARGET_NAME, roleInstanceEntity.getServiceRoleName() + " Survive")
                .eq(Constants.CLUSTER_ID, roleInstanceEntity.getClusterId())
                .eq(Constants.HOSTNAME, roleInstanceEntity.getHostname())
                .eq(Constants.IS_ENABLED, 1));
        if (Objects.nonNull(clusterAlertHistory)) {
            clusterAlertHistory.setIsEnabled(2);
            alertHistoryService.updateById(clusterAlertHistory);
        }
        // update service role instance state
        if (roleInstanceEntity.getServiceRoleState() != ServiceRoleState.RUNNING) {
            roleInstanceEntity.setServiceRoleState(ServiceRoleState.RUNNING);
            roleInstanceService.updateById(roleInstanceEntity);
        }
    }

    public static void saveAlert(ClusterServiceRoleInstanceEntity roleInstanceEntity, String alertTargetName,
                                 AlertLevel alertLevel, String alertAdvice) {
        ClusterServiceRoleInstanceService roleInstanceService =
                SpringTool.getApplicationContext().getBean(ClusterServiceRoleInstanceService.class);
        ClusterAlertHistoryService alertHistoryService =
                SpringTool.getApplicationContext().getBean(ClusterAlertHistoryService.class);
        ClusterServiceInstanceService serviceInstanceService =
                SpringTool.getApplicationContext().getBean(ClusterServiceInstanceService.class);

        logger.info("alertTargetName:{},clusterId:{},hostname:{}", alertTargetName, roleInstanceEntity.getClusterId(), roleInstanceEntity.getHostname());
        ClusterAlertHistory clusterAlertHistory = alertHistoryService.getOne(new QueryWrapper<ClusterAlertHistory>()
                .eq(Objects.nonNull(alertTargetName), Constants.ALERT_TARGET_NAME, alertTargetName)
                .eq(Objects.nonNull(roleInstanceEntity.getClusterId()), Constants.CLUSTER_ID, roleInstanceEntity.getClusterId())
                .eq(Objects.nonNull(roleInstanceEntity.getHostname()), Constants.HOSTNAME, roleInstanceEntity.getHostname())
                .eq(Objects.nonNull(alertTargetName), Constants.IS_ENABLED, 1));

        ClusterServiceInstanceEntity serviceInstanceEntity =
                serviceInstanceService.getById(roleInstanceEntity.getServiceId());
        if (Objects.isNull(clusterAlertHistory)) {
            clusterAlertHistory = ClusterAlertHistory.builder()
                    .clusterId(roleInstanceEntity.getClusterId())
                    .alertGroupName(roleInstanceEntity.getServiceName().toLowerCase())
                    .alertTargetName(alertTargetName)
                    .createTime(new Date())
                    .updateTime(new Date())
                    .alertLevel(alertLevel)
                    .alertInfo("")
                    .alertAdvice(alertAdvice)
                    .hostname(roleInstanceEntity.getHostname())
                    .serviceRoleInstanceId(roleInstanceEntity.getId())
                    .serviceInstanceId(roleInstanceEntity.getServiceId())
                    .isEnabled(1)
                    .serviceInstanceId(roleInstanceEntity.getServiceId())
                    .build();

            alertHistoryService.save(clusterAlertHistory);
        }
        // update service role instance state
        serviceInstanceEntity.setServiceState(ServiceState.EXISTS_EXCEPTION);
        roleInstanceEntity.setServiceRoleState(ServiceRoleState.STOP);
        if (alertLevel == AlertLevel.WARN) {
            serviceInstanceEntity.setServiceState(ServiceState.EXISTS_ALARM);
            roleInstanceEntity.setServiceRoleState(ServiceRoleState.EXISTS_ALARM);
        }
        serviceInstanceService.updateById(serviceInstanceEntity);
        roleInstanceService.updateById(roleInstanceEntity);

    }


}
