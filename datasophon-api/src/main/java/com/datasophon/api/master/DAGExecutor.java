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

package com.datasophon.api.master;

import static com.datasophon.common.enums.CommandType.INSTALL_SERVICE;
import static com.datasophon.common.enums.CommandType.RESTART_SERVICE;
import static com.datasophon.common.enums.CommandType.START_SERVICE;
import static com.datasophon.common.enums.CommandType.STOP_SERVICE;
import static com.datasophon.common.enums.CommandType.UPGRADE_SERVICE;

import com.datasophon.api.dag.DAGListener;
import com.datasophon.api.dag.NodeTask;
import com.datasophon.api.dag.RepoDAG;
import com.datasophon.api.dag.model.NodeDefinition;
import com.datasophon.api.dag.repo.DAGRepository;
import com.datasophon.api.exceptions.BusinessException;
import com.datasophon.api.load.GlobalVariables;
import com.datasophon.api.master.handler.service.ServiceStatusHandler;
import com.datasophon.api.service.ClusterServiceRoleGroupConfigService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.cmd.ClusterServiceCommandHostCommandService;
import com.datasophon.api.service.cmd.ClusterServiceCommandHostService;
import com.datasophon.api.service.cmd.ClusterServiceCommandService;
import com.datasophon.api.service.dag.DAGService;
import com.datasophon.api.utils.CommonUtils;
import com.datasophon.api.utils.ServiceCommandUtils;
import com.datasophon.api.utils.ServiceConfigUtils;
import com.datasophon.api.utils.ServiceLifecycleUtils;
import com.datasophon.common.Constants;
import com.datasophon.common.cache.CacheUtils;
import com.datasophon.common.command.dag.DAGExecCommand;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.enums.ServiceRoleType;
import com.datasophon.common.model.Generators;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceNode;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.common.utils.ExecResult;
import com.datasophon.common.utils.PlaceholderUtils;
import com.datasophon.dao.entity.ClusterServiceRoleGroupConfig;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.entity.cmd.ClusterServiceCommandEntity;
import com.datasophon.dao.entity.cmd.ClusterServiceCommandHostCommandEntity;
import com.datasophon.dao.entity.cmd.ClusterServiceCommandHostEntity;
import com.datasophon.dao.enums.CommandState;
import com.datasophon.dao.enums.NeedRestart;
import com.datasophon.dao.enums.ServiceRoleState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.alibaba.fastjson2.JSONObject;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Pair;

/**
 * 物理集群 DAG 执行器（Spring Service）。
 *
 * <p>合并原 {@code DAGExecActor}（业务逻辑）与 {@code DAGExecService}（@Async 包装），
 * 使用构造器注入代替 {@code SpringUtil.getBean()} 静态查找，便于测试和依赖追踪。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DAGExecutor {
    
    private static final List<CommandType> DELAY_ACTION_COMMAND_TYPES =
            Arrays.asList(INSTALL_SERVICE, UPGRADE_SERVICE, START_SERVICE, RESTART_SERVICE);
    
    private final DAGService dagService;
    private final ClusterServiceCommandHostCommandService hostCommandService;
    private final ClusterServiceCommandHostService commandHostService;
    private final ClusterServiceCommandService commandService;
    private final ClusterServiceRoleInstanceService roleInstanceService;
    private final ClusterServiceRoleGroupConfigService roleGroupConfigService;
    
    /**
     * 异步执行物理集群 DAG 任务（替代原 {@code DAGExecActor.tell(cmd)}）。
     */
    @Async("masterExecutor")
    public void execDAG(DAGExecCommand cmd) {
        try {
            RepoDAG dag = createMultiServiceDAG(cmd);
            
            NodeTask task = (nodeDef) -> {
                ServiceNode serviceNode = JSONObject.parseObject((String) nodeDef.getNodeConfig(), ServiceNode.class);
                
                List<Pair<String, List<ServiceRoleInfo>>> tasks = new ArrayList<>();
                if (STOP_SERVICE.equals(serviceNode.getCommandType())) {
                    tasks.add(new Pair<>("worker", serviceNode.getWorkerRoles()));
                    tasks.add(new Pair<>("client", serviceNode.getClientRoles()));
                    tasks.add(new Pair<>("master", serviceNode.getMasterRoles()));
                } else {
                    tasks.add(new Pair<>("master", serviceNode.getMasterRoles()));
                    tasks.add(new Pair<>("client", serviceNode.getClientRoles()));
                    tasks.add(new Pair<>("worker", serviceNode.getWorkerRoles()));
                }
                
                for (Pair<String, List<ServiceRoleInfo>> pair : tasks) {
                    List<ServiceRoleInfo> roles = pair.getValue();
                    if (CollectionUtil.isEmpty(roles)) {
                        log.info("执行命令, ID:{}，{}不存在的{}角色， 无需操作",
                                serviceNode.getCommandId(), serviceNode.getServiceName(), pair.getKey());
                    } else {
                        log.info("执行命令, ID:{}， 准备{}{}的{}角色",
                                serviceNode.getCommandId(),
                                serviceNode.getCommandType().getCommandName(Constants.CN),
                                serviceNode.getServiceName(),
                                pair.getKey());
                        doExecServiceRoles(serviceNode, roles);
                    }
                }
                return String.format("%s %s成功",
                        serviceNode.getCommandType().getCommandName(Constants.CN), serviceNode.getServiceName());
            };
            
            dag.exec(task, cmd.isRestart());
        } catch (Throwable e) {
            log.error("DAG execution failed for dagId={}: {}", cmd.getDagId(), e.getMessage(), e);
        }
    }
    
    // ─── private methods ─────────────────────────────────────────────────────
    
    private RepoDAG createMultiServiceDAG(DAGExecCommand cmd) {
        String dagId = cmd.getDagId();
        log.info("DAGExecutor 开始执行任务，id:{}", dagId);
        DAGRepository repository = dagService;
        RepoDAG dag = new RepoDAG(repository);
        dag.init(dagId, false);
        
        dag.registerListener(new DAGListener() {
            @Override
            public void onNodeFail(NodeDefinition node, Throwable throwable) {
                boolean ignore = throwable instanceof ServiceRoleExecException && throwable.getCause() == null;
                if (!ignore) {
                    ServiceNode serviceNode = JSONObject.parseObject((String) node.getNodeConfig(), ServiceNode.class);
                    log.info("更新{}{}的状态为{}",
                            serviceNode.getCommandType().getCommandName(Constants.CN),
                            serviceNode.getServiceName(), CommandState.FAILED);
                    updateCmdState(serviceNode, CommandState.FAILED);
                }
            }
            
            @Override
            public void onNodeCancel(NodeDefinition node, Throwable throwable) {
                ServiceNode serviceNode = JSONObject.parseObject((String) node.getNodeConfig(), ServiceNode.class);
                log.info("更新{}{}的状态为{}",
                        serviceNode.getCommandType().getCommandName(Constants.CN),
                        serviceNode.getServiceName(), CommandState.CANCEL);
                updateCmdState(serviceNode, CommandState.CANCEL);
            }
        });
        return dag;
    }
    
    private void updateCmdState(ServiceNode serviceNode, CommandState commandState) {
        List<String> hostCmdIds = commandHostService.lambdaQuery()
                .eq(ClusterServiceCommandHostEntity::getCommandId, serviceNode.getCommandId())
                .select(ClusterServiceCommandHostEntity::getCommandHostId).list().stream()
                .map(ClusterServiceCommandHostEntity::getCommandHostId).collect(Collectors.toList());
        hostCommandService.lambdaUpdate()
                .in(ClusterServiceCommandHostCommandEntity::getCommandHostId, hostCmdIds)
                .in(ClusterServiceCommandHostCommandEntity::getCommandState, Arrays.asList(CommandState.WAIT, CommandState.RUNNING))
                .set(ClusterServiceCommandHostCommandEntity::getCommandState, commandState).update();
        commandHostService.lambdaUpdate()
                .eq(ClusterServiceCommandHostEntity::getCommandId, serviceNode.getCommandId())
                .in(ClusterServiceCommandHostEntity::getCommandState, Arrays.asList(CommandState.WAIT, CommandState.RUNNING))
                .set(ClusterServiceCommandHostEntity::getCommandState, commandState).update();
        commandService.lambdaUpdate()
                .eq(ClusterServiceCommandEntity::getCommandId, serviceNode.getCommandId())
                .in(ClusterServiceCommandEntity::getCommandState, Arrays.asList(CommandState.WAIT, CommandState.RUNNING))
                .set(ClusterServiceCommandEntity::getCommandState, commandState).update();
    }
    
    private void doExecServiceRoles(ServiceNode serviceNode, List<ServiceRoleInfo> roles) {
        ServiceRoleInfo currentRole = null;
        try {
            List<String> sortedRoles = getSortedRoleNames(serviceNode.getCommandType(), roles);
            Map<String, List<ServiceRoleInfo>> map = new HashMap<>();
            for (ServiceRoleInfo role : roles) {
                map.computeIfAbsent(role.getServiceRoleName(), k -> new ArrayList<>()).add(role);
            }
            
            ExecResult result = null;
            for (String roleName : sortedRoles) {
                List<ServiceRoleInfo> roleList = map.get(roleName);
                log.info("开始处理服务{}的命令", roleName);
                int successCount = 0;
                for (ServiceRoleInfo role : roleList) {
                    currentRole = role;
                    result = execServiceRole(role);
                    if (!result.isSuccess()) {
                        break;
                    }
                    successCount++;
                }
                
                ExecResult firstError = null;
                ExecResult lastResult = null;
                ServiceStatusHandler handler = new ServiceStatusHandler();
                handler.setQuickCheck(successCount != roleList.size());
                for (int i = 0; i < successCount; i++) {
                    ServiceRoleInfo role = roleList.get(i);
                    if (DELAY_ACTION_COMMAND_TYPES.contains(role.getCommandType())) {
                        log.info("主机{} {} {} {}成功，开始检查状态",
                                role.getHostname(), role.getCommandType().getCommandName(Constants.CN),
                                role.getParentName(), role.getName());
                        currentRole = role;
                        lastResult = handler.handlerRequest(role);
                        if (!lastResult.isSuccess()) {
                            log.info("主机{}, {} {}状态检查返回失败，执行返回信息为：{}",
                                    role.getHostname(), role.getParentName(), role.getName(), lastResult.getExecOut());
                            if (firstError == null) {
                                firstError = lastResult;
                            }
                        } else {
                            getCommandPostAction(role).run();
                        }
                        ServiceCommandUtils.handleCommandResult(role.getHostCommandId(), lastResult.getExecResult(), lastResult.getExecOut());
                    }
                }
                if (firstError != null) {
                    result = firstError;
                } else {
                    if (successCount == roleList.size() && lastResult != null) {
                        result = lastResult;
                    }
                }
                if (successCount < roleList.size() || firstError != null) {
                    break;
                }
            }
            
            if (result == null || result.isSuccess()) {
                ServiceRoleType type = roles.get(0).getRoleType();
                log.info("执行{}{}成功, 共{}个角色, 类型为{}",
                        serviceNode.getCommandType().getCommandName(Constants.CN),
                        serviceNode.getServiceName(), roles.size(), type.getName());
            } else {
                String message = String.format("在%s %s %s %s失败, 原因：%s",
                        currentRole.getHostname(),
                        currentRole.getCommandType().getCommandName(Constants.CN),
                        currentRole.getParentName(), currentRole.getName(), result.getExecOut());
                throw new ServiceRoleExecException(message);
            }
        } catch (ServiceRoleExecException ex) {
            throw ex;
        } catch (Throwable throwable) {
            if (currentRole != null) {
                log.error("在{}{}{}失败，原因：{}, ",
                        currentRole.getHostname(),
                        currentRole.getCommandType().getCommandName(Constants.CN),
                        currentRole.getParentName(), throwable.getMessage(), throwable);
            } else {
                log.error("执行{}{}失败, 共{}个角色, 错误原因：{}",
                        serviceNode.getCommandType().getCommandName(Constants.CN),
                        serviceNode.getServiceName(), roles.size(), throwable.getMessage(), throwable);
            }
            String message = buildErrorMessage(serviceNode, throwable, currentRole);
            throw new ServiceRoleExecException(message, throwable);
        }
    }
    
    private static String buildErrorMessage(ServiceNode serviceNode, Throwable throwable, ServiceRoleInfo currentRole) {
        String tmpMsg = throwable instanceof NullPointerException ? "对象空指针" : throwable.getMessage();
        if (currentRole != null) {
            return String.format("在%s%s%s失败，原因：%s",
                    currentRole.getHostname(),
                    currentRole.getCommandType().getCommandName(Constants.CN),
                    currentRole.getParentName(), tmpMsg);
        } else {
            return String.format("%s%s失败，原因：%s",
                    serviceNode.getCommandType().getCommandName(Constants.CN),
                    serviceNode.getServiceName(), tmpMsg);
        }
    }
    
    private List<String> getSortedRoleNames(CommandType type, List<ServiceRoleInfo> roles) {
        List<SortedRole> tempRoles = roles.stream().map(SortedRole::new).collect(Collectors.toList());
        if (STOP_SERVICE.equals(type)) {
            tempRoles.sort(Comparator.comparing(SortedRole::getOrder).reversed());
        } else {
            tempRoles.sort(Comparator.comparing(SortedRole::getOrder));
        }
        List<String> sortedRoles = new ArrayList<>();
        for (SortedRole role : tempRoles) {
            if (!sortedRoles.contains(role.getServiceRoleName())) {
                sortedRoles.add(role.getServiceRoleName());
            }
        }
        return sortedRoles;
    }
    
    private ExecResult execServiceRole(ServiceRoleInfo serviceRoleInfo) {
        Map<Generators, List<ServiceConfig>> configFileMap = createConfigFileMap(serviceRoleInfo);
        serviceRoleInfo.setConfigFileMap(configFileMap);
        
        boolean enableRangerPlugin = isEnableRangerPlugin(serviceRoleInfo);
        serviceRoleInfo.setEnableRangerPlugin(enableRangerPlugin);
        
        ClusterServiceRoleInstanceEntity serviceRoleInstance = roleInstanceService.getOneServiceRole(
                serviceRoleInfo.getName(), serviceRoleInfo.getHostname(), serviceRoleInfo.getClusterId());
        boolean needReConfig = serviceRoleInstance == null || serviceRoleInstance.getNeedRestart() == NeedRestart.YES;
        
        CommandType type = serviceRoleInfo.getCommandType();
        log.info("开始{}服务{}的{}角色", type.getCommandName(Constants.CN), serviceRoleInfo.getParentName(), serviceRoleInfo.getName());
        
        ExecResult execResult;
        switch (type) {
            case INSTALL_SERVICE:
                execResult = doServiceAction(serviceRoleInfo, () -> ServiceLifecycleUtils.startInstallService(serviceRoleInfo));
                break;
            case START_SERVICE:
                execResult = doServiceAction(serviceRoleInfo, () -> ServiceLifecycleUtils.startService(serviceRoleInfo, needReConfig));
                break;
            case STOP_SERVICE:
                execResult = doServiceAction(serviceRoleInfo, () -> ServiceLifecycleUtils.stopService(serviceRoleInfo));
                break;
            case RESTART_SERVICE:
                execResult = doServiceAction(serviceRoleInfo, () -> ServiceLifecycleUtils.restartService(serviceRoleInfo, needReConfig));
                break;
            case UPGRADE_SERVICE:
                execResult = doServiceAction(serviceRoleInfo, () -> ServiceLifecycleUtils.upgradeService(serviceRoleInfo));
                break;
            default:
                throw new BusinessException(String.format("unknown cmd type: %s of srv %s in host %s{}",
                        serviceRoleInfo.getCommandType().getCommandName(Constants.CN),
                        serviceRoleInfo.getName(), serviceRoleInfo.getHostname()));
        }
        
        log.info("完成{}服务{}的{}角色, 执行结果：{}, 信息：{}",
                type.getCommandName(Constants.CN), serviceRoleInfo.getParentName(), serviceRoleInfo.getName(),
                execResult.isSuccess() ? "成功" : "失败", execResult.getExecOut());
        
        boolean needTellResult = !execResult.isSuccess() || !DELAY_ACTION_COMMAND_TYPES.contains(type);
        if (needTellResult) {
            ServiceCommandUtils.handleCommandResult(serviceRoleInfo.getHostCommandId(), execResult.getExecResult(), execResult.getExecOut());
        } else {
            ClusterServiceCommandHostCommandEntity hostCommand = hostCommandService.getByHostCommandId(serviceRoleInfo.getHostCommandId());
            hostCommand.setCommandProgress(90);
            hostCommandService.updateByHostCommandId(hostCommand);
        }
        return execResult;
    }
    
    private Map<Generators, List<ServiceConfig>> createConfigFileMap(ServiceRoleInfo serviceRoleInfo) {
        log.info("服务{}{}创建ConfigFileMap", serviceRoleInfo.getParentName(), serviceRoleInfo.getServiceRoleName());
        
        ClusterServiceRoleInstanceEntity serviceRoleInstance = roleInstanceService.getOneServiceRole(
                serviceRoleInfo.getName(), serviceRoleInfo.getHostname(), serviceRoleInfo.getClusterId());
        
        ClusterServiceRoleGroupConfig config = null;
        if (Arrays.asList(INSTALL_SERVICE, UPGRADE_SERVICE).contains(serviceRoleInfo.getCommandType())) {
            Integer roleGroupId = (Integer) CacheUtils.get("UseRoleGroup_" + serviceRoleInfo.getServiceInstanceId());
            if (roleGroupId == null) {
                throw new BusinessException("缓存已经失效，请重新安装");
            }
            config = roleGroupConfigService.getConfigByRoleGroupId(roleGroupId);
        } else if (serviceRoleInstance != null && serviceRoleInstance.getNeedRestart() == NeedRestart.YES) {
            config = roleGroupConfigService.getConfigByRoleGroupId(serviceRoleInstance.getRoleGroupId());
        }
        
        Map<Generators, List<ServiceConfig>> configFileMap = new HashMap<>();
        if (config != null) {
            ServiceConfigUtils.generateConfigFileMap(configFileMap, config, serviceRoleInfo.getClusterId());
            Map<String, String> globalVariables = GlobalVariables.getVariables(serviceRoleInfo.getClusterId());
            for (Generators generators : configFileMap.keySet()) {
                String outputDirectory = generators.getOutputDirectory();
                generators.setOutputDirectory(PlaceholderUtils.replacePlaceholders(outputDirectory, globalVariables, Constants.REGEX_VARIABLE));
            }
        }
        
        log.info("服务{}{}创建ConfigFileMap成功，总共需要{}个Generators",
                serviceRoleInfo.getParentName(), serviceRoleInfo.getServiceRoleName(), configFileMap.size());
        return configFileMap;
    }
    
    private boolean isEnableRangerPlugin(ServiceRoleInfo roleInfo) {
        if (!ServiceRoleType.MASTER.equals(roleInfo.getRoleType())) {
            return false;
        }
        Integer clusterId = roleInfo.getClusterId();
        String serviceName = roleInfo.getParentName();
        return "true".equals(GlobalVariables.getValueByService(clusterId, serviceName, "enable" + serviceName + "Plugin"));
    }
    
    private ExecResult doServiceAction(ServiceRoleInfo srvInfo, Callable<ExecResult> callable) {
        try {
            log.info("{} {} in host {}", srvInfo.getCommandType().getCommandName(Constants.CN), srvInfo.getName(), srvInfo.getHostname());
            ExecResult execResult = callable.call();
            if (execResult.isSuccess() && !DELAY_ACTION_COMMAND_TYPES.contains(srvInfo.getCommandType())) {
                log.info("在host {} {} {} 成功, 开始执行后置逻辑",
                        srvInfo.getHostname(), srvInfo.getCommandType().getCommandName(Constants.CN), srvInfo.getName());
                getCommandPostAction(srvInfo).run();
            }
            return execResult;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            String error = String.format("%s %s失败, 堆栈信息：%s",
                    srvInfo.getParentName(), srvInfo.getCommandType().getCommandName(Constants.CN),
                    CommonUtils.getExceptionMessage(e));
            return ExecResult.error(error);
        }
    }
    
    private Runnable getCommandPostAction(ServiceRoleInfo serviceRoleInfo) {
        CommandType type = serviceRoleInfo.getCommandType();
        switch (type) {
            case INSTALL_SERVICE:
            case UPGRADE_SERVICE:
                return () -> ServiceCommandUtils.saveServiceInstallInfo(serviceRoleInfo);
            case START_SERVICE:
            case RESTART_SERVICE:
                return () -> updateServiceRoleState(serviceRoleInfo, ServiceRoleState.RUNNING);
            case STOP_SERVICE:
                return () -> updateServiceRoleState(serviceRoleInfo, ServiceRoleState.STOP);
            default:
                throw new BusinessException(String.format("unknown cmd type: %s of srv %s in host %s{}",
                        serviceRoleInfo.getCommandType().getCommandName(Constants.CN),
                        serviceRoleInfo.getName(), serviceRoleInfo.getHostname()));
        }
    }
    
    private void updateServiceRoleState(ServiceRoleInfo role, ServiceRoleState state) {
        ServiceCommandUtils.updateServiceRoleState(role.getCommandType(), role.getName(), role.getHostname(), role.getClusterId(), state);
    }
    
    // ─── inner types ─────────────────────────────────────────────────────────
    
    /** DAG 节点中单个服务角色执行失败时抛出，由 RepoDAG 的监听器识别并决定是否触发级联取消。 */
    @EqualsAndHashCode(callSuper = false)
    @Data
    public static class ServiceRoleExecException extends RuntimeException {
        public ServiceRoleExecException(String message) {
            super(message);
        }
        
        public ServiceRoleExecException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    @Data
    private static class SortedRole {
        private String serviceRoleName;
        private int order;
        
        public SortedRole(ServiceRoleInfo role) {
            serviceRoleName = role.getServiceRoleName();
            order = role.getSortNum() == null ? Integer.MAX_VALUE : role.getSortNum();
        }
    }
}
