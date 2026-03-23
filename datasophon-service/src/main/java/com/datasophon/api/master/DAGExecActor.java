package com.datasophon.api.master;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.fastjson.JSONObject;
import com.datasophon.api.dag.DAGListener;
import com.datasophon.api.dag.NodeTask;
import com.datasophon.api.dag.RepoDAG;
import com.datasophon.api.dag.model.NodeDefinition;
import com.datasophon.api.dag.repo.DAGRepository;
import com.datasophon.api.exceptions.BusinessException;
import com.datasophon.api.load.GlobalVariables;
import com.datasophon.api.master.handler.service.ServiceStatusHandler;
import com.datasophon.api.service.ClusterServiceCommandHostCommandService;
import com.datasophon.api.service.ClusterServiceCommandHostService;
import com.datasophon.api.service.ClusterServiceCommandService;
import com.datasophon.api.service.ClusterServiceRoleGroupConfigService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.dag.DAGService;
import com.datasophon.api.utils.ProcessUtils;
import com.datasophon.api.utils.SpringTool;
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
import com.datasophon.dao.entity.ClusterServiceCommandEntity;
import com.datasophon.dao.entity.ClusterServiceCommandHostCommandEntity;
import com.datasophon.dao.entity.ClusterServiceCommandHostEntity;
import com.datasophon.dao.entity.ClusterServiceRoleGroupConfig;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.enums.CommandState;
import com.datasophon.dao.enums.NeedRestart;
import com.datasophon.dao.enums.ServiceRoleState;
import javafx.util.Pair;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import static com.datasophon.common.enums.CommandType.INSTALL_SERVICE;
import static com.datasophon.common.enums.CommandType.RESTART_SERVICE;
import static com.datasophon.common.enums.CommandType.START_SERVICE;
import static com.datasophon.common.enums.CommandType.STOP_SERVICE;
import static com.datasophon.common.enums.CommandType.UPGRADE_SERVICE;

/**
 * @author zhanghuangbin
 */
@Slf4j
public class DAGExecActor extends TypedActor<DAGExecCommand> {


    private static final List<CommandType> DELAY_ACTION_COMMAND_TYPES = Arrays.asList(INSTALL_SERVICE, UPGRADE_SERVICE, START_SERVICE, RESTART_SERVICE);

    @Override
    protected void doOnReceive(DAGExecCommand message) {
        RepoDAG dag = createMultiServiceDAG(message);

        NodeTask task = (nodeDef) -> {
//            单个服务的安装
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
                    log.info("执行命令, ID:{}，{}不存在的{}角色， 无需操作", serviceNode.getCommandId(), serviceNode.getServiceName(), pair.getKey());
                } else {
                    log.info("执行命令, ID:{}， 准备{}{}的{}角色",
                            serviceNode.getCommandId(),
                            serviceNode.getCommandType().getCommandName(Constants.CN),
                            serviceNode.getServiceName(),
                            pair.getKey()
                    );
                    doExecServiceRoles(serviceNode, roles);
                }
            }
            return String.format("%s %s成功", serviceNode.getCommandType().getCommandName(Constants.CN), serviceNode.getServiceName());
        };

        dag.exec(task, message.isRestart());
    }


    private RepoDAG createMultiServiceDAG(DAGExecCommand cmd) {
        String dagId = cmd.getDagId();
        log.info("DAGExecActor开始执行任务， id:{}", dagId);
        DAGRepository repository = SpringUtil.getBean(DAGService.class);
        RepoDAG dag = new RepoDAG(repository);
        dag.init(dagId, false);

        dag.registerListener(new DAGListener() {
            @Override
            public void onNodeFail(NodeDefinition node, Throwable throwable) {
                boolean ignore = throwable instanceof ServiceRoleExecException && throwable.getCause() == null;
                if (!ignore) {
                    ServiceNode serviceNode = JSONObject.parseObject((String) node.getNodeConfig(), ServiceNode.class);
                    log.info("更新{}{}的状态为{}", serviceNode.getCommandType().getCommandName(Constants.CN), serviceNode.getServiceName(), CommandState.FAILED);
                    updateCmdState(serviceNode, CommandState.FAILED);
                }
            }

            @Override
            public void onNodeCancel(NodeDefinition node, Throwable throwable) {
                ServiceNode serviceNode = JSONObject.parseObject((String) node.getNodeConfig(), ServiceNode.class);
                log.info("更新{}{}的状态为{}", serviceNode.getCommandType().getCommandName(Constants.CN), serviceNode.getServiceName(), CommandState.CANCEL);
                updateCmdState(serviceNode, CommandState.CANCEL);
            }
        });
        return dag;
    }

    /**
     * 更新命令行运行状态
     *
     * @param serviceNode
     * @param commandState
     */
    private void updateCmdState(ServiceNode serviceNode, CommandState commandState) {
        ClusterServiceCommandHostCommandService hostCmdService = SpringTool.getApplicationContext().getBean(ClusterServiceCommandHostCommandService.class);
        ClusterServiceCommandHostService commandHostService = SpringTool.getApplicationContext().getBean(ClusterServiceCommandHostService.class);
        ClusterServiceCommandService commandService = SpringTool.getApplicationContext().getBean(ClusterServiceCommandService.class);

//        更新每一台服务器的命令的状态
        List<String> hostCmdIds = commandHostService.lambdaQuery().eq(ClusterServiceCommandHostEntity::getCommandId, serviceNode.getCommandId()).select(ClusterServiceCommandHostEntity::getCommandHostId).list().stream().map(ClusterServiceCommandHostEntity::getCommandHostId).collect(Collectors.toList());
        hostCmdService.lambdaUpdate().in(ClusterServiceCommandHostCommandEntity::getCommandHostId, hostCmdIds).in(ClusterServiceCommandHostCommandEntity::getCommandState, Arrays.asList(CommandState.WAIT, CommandState.RUNNING)).set(ClusterServiceCommandHostCommandEntity::getCommandState, commandState).update();

//      更新每一台服务器的名字执行状态
        commandHostService.lambdaUpdate().eq(ClusterServiceCommandHostEntity::getCommandId, serviceNode.getCommandId()).in(ClusterServiceCommandHostEntity::getCommandState, Arrays.asList(CommandState.WAIT, CommandState.RUNNING)).set(ClusterServiceCommandHostEntity::getCommandState, commandState).update();

//        更新命令动作的状态
        commandService.lambdaUpdate().eq(ClusterServiceCommandEntity::getCommandId, serviceNode.getCommandId()).in(ClusterServiceCommandEntity::getCommandState, Arrays.asList(CommandState.WAIT, CommandState.RUNNING)).set(ClusterServiceCommandEntity::getCommandState, commandState).update();
    }


    /**
     * 安装单个服务的一个roleType的所有进程
     *
     * @param serviceNode
     * @param roles
     */
    private void doExecServiceRoles(ServiceNode serviceNode, List<ServiceRoleInfo> roles) {
        ServiceRoleInfo currentRole = null;
        try {
            List<String> sortedRoles = getSortedRoleNames(serviceNode.getCommandType(), roles);
            Map<String, List<ServiceRoleInfo>> map = new HashMap<>();
            for (ServiceRoleInfo role : roles) {
//                保留列表的原有顺序
                map.computeIfAbsent(role.getServiceRoleName(), k -> new ArrayList<>()).add(role);
            }

            ExecResult result = null;
            for (String roleName : sortedRoles) {
                List<ServiceRoleInfo> roleList = map.get(roleName);
                log.info("开始处理服务{}的命令", roleName);
//              已经执行成功的进程数
                int successCount = 0;
//                逐个执行进程的操作命令
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
//                如果只有部分节点安装成功，则快速检查状态(即检查次数可以减少)
                handler.setQuickCheck(successCount != roleList.size());
//                对延迟执行后置逻辑的进程，执行后置逻辑
                for (int i = 0; i < successCount; i++) {
                    ServiceRoleInfo role = roleList.get(i);
                    if (DELAY_ACTION_COMMAND_TYPES.contains(role.getCommandType())) {
                        log.info("主机{} {} {} {}成功，开始检查状态", role.getHostname(), role.getCommandType().getCommandName(Constants.CN), role.getParentName(), role.getName());
                        currentRole = role;
                        lastResult = handler.handlerRequest(role);
                        if (!lastResult.isSuccess()) {
                            log.info("主机{}, {} {}状态检查返回失败，执行返回信息为：{}", role.getHostname(), role.getParentName(), role.getName(), result.getExecOut());
                            if (firstError == null) {
                                firstError = lastResult;
                            }
                        } else {
                            getCommandPostAction(role).run();
                        }

                        ProcessUtils.handleCommandResult(role.getHostCommandId(), lastResult.getExecResult(), lastResult.getExecOut());
                    }
                }
//                如果已经成功安装的服务状态检查存在问题，则取第一个错误的信息作为输出
                if (firstError != null) {
                    result = firstError;
                } else {
//                    如果全部节点都安装成功，取最后一个执行结果作为判断条件，否则取第一个安装失败的结果用于判断条件(即默认情况）
                    if (successCount == roleList.size()) {
                        if (lastResult != null) {
                            result = lastResult;
                        }
                    }
                }
                if (successCount < roleList.size() || firstError != null) {
                    break;
                }
            }

//
            if (result == null || result.isSuccess()) {
                ServiceRoleType type = roles.get(0).getRoleType();
                log.info("执行{}{}成功, 共{}个角色, 类型为{}", serviceNode.getCommandType().getCommandName(Constants.CN), serviceNode.getServiceName(), roles.size(), type.getName());

            } else {
                String message = String.format("在%s %s %s %s失败, 原因：%s", currentRole.getHostname(), currentRole.getCommandType().getCommandName(Constants.CN),
                        currentRole.getParentName(), currentRole.getName(), result.getExecOut());
                throw new ServiceRoleExecException(message);
            }
        } catch (ServiceRoleExecException ex) {
            throw ex;
        } catch (Throwable throwable) {
            if (currentRole != null) {
                log.error("在{}{}{}失败，原因：{}, ", currentRole.getHostname(), currentRole.getCommandType().getCommandName(Constants.CN),
                        currentRole.getParentName(), throwable.getMessage(), throwable);
            } else {
                log.error("执行{}{}失败, 共{}个角色, 错误原因：{}", serviceNode.getCommandType().getCommandName(Constants.CN), serviceNode.getServiceName(),
                        roles.size(), throwable.getMessage(), throwable);
            }

            String message = getString(serviceNode, throwable, currentRole);
            throw new ServiceRoleExecException(message, throwable);
        }
    }

    private static String getString(ServiceNode serviceNode, Throwable throwable, ServiceRoleInfo currentRole) {
        String tmpMsg = throwable instanceof NullPointerException ? "对象空指针" : throwable.getMessage();
        if (currentRole != null) {
            return String.format("在%s%s%s失败，原因：%s", currentRole.getHostname(), currentRole.getCommandType().getCommandName(Constants.CN), currentRole.getParentName(), tmpMsg);
        } else {
            return String.format("%s%s失败，原因：%s", serviceNode.getCommandType().getCommandName(Constants.CN), serviceNode.getServiceName(), tmpMsg);
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
        ClusterServiceRoleInstanceService roleInstanceService = SpringTool.getApplicationContext().getBean(ClusterServiceRoleInstanceService.class);

        Map<Generators, List<ServiceConfig>> configFileMap = createConfigFileMap(serviceRoleInfo);
        serviceRoleInfo.setConfigFileMap(configFileMap);

        boolean enableRangerPlugin = isEnableRangerPlugin(serviceRoleInfo);
        serviceRoleInfo.setEnableRangerPlugin(enableRangerPlugin);

        ExecResult execResult = null;
        ClusterServiceRoleInstanceEntity serviceRoleInstance = roleInstanceService.getOneServiceRole(serviceRoleInfo.getName(), serviceRoleInfo.getHostname(), serviceRoleInfo.getClusterId());
        boolean needReConfig = serviceRoleInstance == null || serviceRoleInstance.getNeedRestart() == NeedRestart.YES;

        CommandType type = serviceRoleInfo.getCommandType();
        log.info("开始{}服务{}的{}角色", type.getCommandName(Constants.CN), serviceRoleInfo.getParentName(), serviceRoleInfo.getName());
        switch (type) {
            case INSTALL_SERVICE:
                execResult = doServiceAction(serviceRoleInfo, () -> ProcessUtils.startInstallService(serviceRoleInfo));
                break;
            case START_SERVICE:
                execResult = doServiceAction(serviceRoleInfo, () -> ProcessUtils.startService(serviceRoleInfo, needReConfig));
                break;
            case STOP_SERVICE:
                execResult = doServiceAction(serviceRoleInfo, () -> ProcessUtils.stopService(serviceRoleInfo));
                break;
            case RESTART_SERVICE:
                execResult = doServiceAction(serviceRoleInfo, () -> ProcessUtils.restartService(serviceRoleInfo, needReConfig));
                break;
            case UPGRADE_SERVICE:
                execResult = doServiceAction(serviceRoleInfo, () -> ProcessUtils.upgradeService(serviceRoleInfo));
                break;
            default:
                throw new BusinessException(String.format("unknown cmd type: %s of srv %s in host %s{}", serviceRoleInfo.getCommandType().getCommandName(Constants.CN), serviceRoleInfo.getName(), serviceRoleInfo.getHostname()));
        }

        log.info("完成{}服务{}的{}角色, 执行结果：{}, 信息：{}", type.getCommandName(Constants.CN), serviceRoleInfo.getParentName(), serviceRoleInfo.getName(),
                execResult.isSuccess() ? "成功" : "失败", execResult.getExecOut());

//        包含”启动"服务的操作，应该等待多个节点启动后，才能检查状态
        boolean needTellResult = !execResult.isSuccess() || !DELAY_ACTION_COMMAND_TYPES.contains(type);
        if (needTellResult) {
            ProcessUtils.handleCommandResult(serviceRoleInfo.getHostCommandId(), execResult.getExecResult(), execResult.getExecOut());
        } else {
            ClusterServiceCommandHostCommandService service = getBean(ClusterServiceCommandHostCommandService.class);
            ClusterServiceCommandHostCommandEntity hostCommand = service.getByHostCommandId(serviceRoleInfo.getHostCommandId());
            hostCommand.setCommandProgress(90);
            service.updateByHostCommandId(hostCommand);
        }
        return execResult;
    }


    private Map<Generators, List<ServiceConfig>> createConfigFileMap(ServiceRoleInfo serviceRoleInfo) {
        log.info("服务{}{}创建ConfigFileMap", serviceRoleInfo.getParentName(), serviceRoleInfo.getServiceRoleName());
        ClusterServiceRoleGroupConfigService roleGroupConfigService = getBean(ClusterServiceRoleGroupConfigService.class);
        ClusterServiceRoleInstanceService roleInstanceService = getBean(ClusterServiceRoleInstanceService.class);

        ClusterServiceRoleInstanceEntity serviceRoleInstance = roleInstanceService.getOneServiceRole(serviceRoleInfo.getName(), serviceRoleInfo.getHostname(), serviceRoleInfo.getClusterId());


        ClusterServiceRoleGroupConfig config = null;
        if (Arrays.asList(INSTALL_SERVICE, UPGRADE_SERVICE).contains(serviceRoleInfo.getCommandType())) {
            Integer roleGroupId = (Integer) CacheUtils.get("UseRoleGroup_" + serviceRoleInfo.getServiceInstanceId());
            if (roleGroupId == null) {
                throw new BusinessException("缓存已经失效，请重新安装");
            }
            config = roleGroupConfigService.getConfigByRoleGroupId(roleGroupId);
        } else if (serviceRoleInstance.getNeedRestart() == NeedRestart.YES) {
            config = roleGroupConfigService.getConfigByRoleGroupId(serviceRoleInstance.getRoleGroupId());
        }

        Map<Generators, List<ServiceConfig>> configFileMap = new HashMap<>();
        if (config != null) {
            ProcessUtils.generateConfigFileMap(configFileMap, config, serviceRoleInfo.getClusterId());
            Map<String, String> globalVariables = GlobalVariables.getVariables(serviceRoleInfo.getClusterId());
            for (Generators generators : configFileMap.keySet()) {
                String outputDirectory = generators.getOutputDirectory();
                generators.setOutputDirectory(PlaceholderUtils.replacePlaceholders(outputDirectory, globalVariables, Constants.REGEX_VARIABLE));
            }
        }


        log.info("服务{}{}创建ConfigFileMap成功，总共需要{}个Generators", serviceRoleInfo.getParentName(), serviceRoleInfo.getServiceRoleName(), configFileMap.size());
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
                log.info("在host {} {} {} 成功, 开始执行后置逻辑", srvInfo.getHostname(), srvInfo.getCommandType().getCommandName(Constants.CN), srvInfo.getName());
                getCommandPostAction(srvInfo).run();
            }
            return execResult;
        } catch (Exception e) {
            log.error(e.getMessage(), e);

            String error = String.format("%s %s失败, 堆栈信息：%s", srvInfo.getParentName(), srvInfo.getCommandType().getCommandName(Constants.CN), ProcessUtils.getExceptionMessage(e));
            return ExecResult.error(error);
        }
    }

    /**
     * 获取服务执行后的后置执行逻辑块
     *
     * @param serviceRoleInfo
     * @return
     */
    private Runnable getCommandPostAction(ServiceRoleInfo serviceRoleInfo) {
        CommandType type = serviceRoleInfo.getCommandType();
        switch (type) {
            case INSTALL_SERVICE:
            case UPGRADE_SERVICE:
                return () -> ProcessUtils.saveServiceInstallInfo(serviceRoleInfo);
            case START_SERVICE:
            case RESTART_SERVICE:
                return () -> updateServiceRoleState(serviceRoleInfo, ServiceRoleState.RUNNING);
            case STOP_SERVICE:
                return () -> updateServiceRoleState(serviceRoleInfo, ServiceRoleState.STOP);
            default:
                throw new BusinessException(String.format("unknown cmd type: %s of srv %s in host %s{}", serviceRoleInfo.getCommandType().getCommandName(Constants.CN), serviceRoleInfo.getName(), serviceRoleInfo.getHostname()));
        }
    }


    private void updateServiceRoleState(ServiceRoleInfo role, ServiceRoleState state) {
        ProcessUtils.updateServiceRoleState(role.getCommandType(), role.getName(), role.getHostname(), role.getClusterId(), state);
    }


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
