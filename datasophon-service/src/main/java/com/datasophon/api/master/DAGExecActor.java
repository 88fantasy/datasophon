package com.datasophon.api.master;

import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.fastjson.JSONObject;
import com.datasophon.api.dag.AsyncNodeTask;
import com.datasophon.api.dag.DAGListener;
import com.datasophon.api.dag.NodeExecutionCallback;
import com.datasophon.api.dag.RepoDAG;
import com.datasophon.api.dag.model.NodeDefinition;
import com.datasophon.api.dag.repo.DAGRepository;
import com.datasophon.api.dag.repo.SimpleDAGRepository;
import com.datasophon.api.exceptions.BusinessException;
import com.datasophon.api.load.GlobalVariables;
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
import com.datasophon.dao.enums.dag.DagStatus;
import com.datasophon.dao.enums.dag.NodeStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * @author zhanghuangbin
 */
@Slf4j
public class DAGExecActor extends TargetTypeActor<DAGExecCommand> {


    @Override
    protected void doOnReceive(DAGExecCommand message) {
        RepoDAG dag = createMultiServiceDAG(message);

        AsyncNodeTask task = (nodeDef, callback) -> {
//            单个服务的安装
            ServiceNode serviceNode = JSONObject.parseObject((String) nodeDef.getNodeConfig(), ServiceNode.class);
            log.info("准备执行{}{}, 命令行ID:{}", serviceNode.getCommandType().getCommandName(Constants.CN), serviceNode.getServiceName(), serviceNode.getCommandId());
            RepoDAG singleSrvDAG = createSingleServiceExecDAG(serviceNode, callback);
            log.info("开始执行{}{}, 命令行ID:{}", serviceNode.getCommandType().getCommandName(Constants.CN), serviceNode.getServiceName(), serviceNode.getCommandId());
            singleSrvDAG.exec((node, cb) -> {
                List<ServiceRoleInfo> roles = (List<ServiceRoleInfo>) node.getNodeConfig();
                doExecServiceRoles(serviceNode, roles, cb);
            });
        };

        if (message.isRestart()) {
            dag.exec(task);
        } else {
            dag.resume(task);
        }
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
                boolean ignore = throwable != null && throwable.getCause() instanceof ServiceRoleExecException;
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
     * 创建单个软件的安装dag图，要求：
     * 1. master进程先执行
     * 2. 其他类型worker,client后执行
     *
     * @param serviceNode
     * @param callback
     * @return
     */
    private RepoDAG createSingleServiceExecDAG(ServiceNode serviceNode, NodeExecutionCallback callback) {
        List<Object> tasks = new ArrayList<>();
        if (!serviceNode.getMasterRoles().isEmpty()) {
            tasks.add(serviceNode.getMasterRoles());
        }
        if (!serviceNode.getElseRoles().isEmpty()) {
            tasks.add(serviceNode.getElseRoles());
        }

        List<int[]> edges = new ArrayList<>(0);
        if (tasks.size() == 2) {
//                master任务先执行
            edges.add(new int[]{0, 1});
        }
        DAGRepository simpleRepo = new SimpleDAGRepository(tasks, edges);
        RepoDAG singleSrvDAG = new RepoDAG(simpleRepo);
//        注册监听器，当单个软件安装完成后，通知其他软件执行
        singleSrvDAG.registerListener(new DAGListener() {

            @Override
            public void onCompleted(RepoDAG dag, DagStatus status, Throwable throwable) {
                if (DagStatus.SUCCESS.equals(status)) {
                    callback.onSuccess(String.format("%s%s成功", serviceNode.getCommandType().getCommandName(Constants.CN), serviceNode.getServiceName()));
                } else {
                    String message = String.format("%s%s失败，原因：%s", serviceNode.getCommandType().getCommandName(Constants.CN), serviceNode.getServiceName(), throwable == null ? "执行结果：" + status.name() : throwable.getMessage());
                    callback.onFailure(new RuntimeException(message, throwable));
                }
            }
        });
        log.info("创建服务安装dag成功");
        return singleSrvDAG;
    }

    private void doExecServiceRoles(ServiceNode serviceNode, List<ServiceRoleInfo> roles, NodeExecutionCallback cb) {
        ServiceRoleInfo currentRole = null;
        try {
            roles.sort(Comparator.comparing(ServiceRoleInfo::getSortNum, Comparator.nullsFirst(Comparator.naturalOrder())));
            ExecResult result = null;
            for (ServiceRoleInfo role : roles) {
                currentRole = role;
                result = execServiceRole(role);
                if (!result.isSuccess()) {
                    break;
                }
            }
            if (result == null || result.isSuccess()) {
                ServiceRoleType type = roles.get(0).getRoleType();
                log.info("执行{}{}成功, 共{}个角色, 类型为{}", serviceNode.getCommandType().getCommandName(Constants.CN), serviceNode.getServiceName(), roles.size(), type.getName());
                cb.onSuccess(NodeStatus.SUCCESS.name());
            } else {
                String message = String.format("在%s %s %s失败", currentRole.getHostname(), currentRole.getCommandType().getCommandName(Constants.CN), currentRole.getParentName());
                throw new ServiceRoleExecException(message);
            }
        } catch (ServiceRoleExecException ex) {
            cb.onFailure(ex);
        } catch (Throwable throwable) {
            if (currentRole != null) {
                log.error("在{}{}{}失败，原因：{}", currentRole.getHostname(), currentRole.getCommandType().getCommandName(Constants.CN), currentRole.getParentName(), throwable.getMessage());
                String message = String.format("在%s%s%s失败，原因：%s", currentRole.getHostname(), currentRole.getCommandType().getCommandName(Constants.CN), currentRole.getParentName(), throwable.getMessage());
                cb.onFailure(new RuntimeException(message, throwable));
            } else {
                log.error("执行{}{}失败, 共{}个角色, 错误原因：{}", serviceNode.getCommandType().getCommandName(Constants.CN), serviceNode.getServiceName(), roles.size(), throwable.getMessage());
                cb.onFailure(throwable);
            }
        }
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
        switch (serviceRoleInfo.getCommandType()) {
            case INSTALL_SERVICE:
                execResult = doServiceAction(serviceRoleInfo, () -> ProcessUtils.startInstallService(serviceRoleInfo), () -> ProcessUtils.saveServiceInstallInfo(serviceRoleInfo));
                break;
            case START_SERVICE:
                execResult = doServiceAction(serviceRoleInfo, () -> ProcessUtils.startService(serviceRoleInfo, needReConfig), () -> updateServiceRoleState(serviceRoleInfo, ServiceRoleState.RUNNING));
                break;
            case STOP_SERVICE:
                execResult = doServiceAction(serviceRoleInfo, () -> ProcessUtils.stopService(serviceRoleInfo), () -> updateServiceRoleState(serviceRoleInfo, ServiceRoleState.STOP));
                break;
            case RESTART_SERVICE:
                execResult = doServiceAction(serviceRoleInfo, () -> ProcessUtils.restartService(serviceRoleInfo, needReConfig), () -> updateServiceRoleState(serviceRoleInfo, ServiceRoleState.RUNNING));
                break;
            case UPGRADE_SERVICE:
                execResult = doServiceAction(serviceRoleInfo, () -> ProcessUtils.upgradeService(serviceRoleInfo), () -> ProcessUtils.saveServiceInstallInfo(serviceRoleInfo));
                break;
            default:
                throw new BusinessException(String.format("unknown cmd type: %s of srv %s in host %s{}", serviceRoleInfo.getCommandType().getCommandName(Constants.CN), serviceRoleInfo.getName(), serviceRoleInfo.getHostname()));
        }

        ProcessUtils.handleCommandResult(serviceRoleInfo.getHostCommandId(), execResult.getExecResult(), execResult.getExecOut());
        return execResult;
    }


    private Map<Generators, List<ServiceConfig>> createConfigFileMap(ServiceRoleInfo serviceRoleInfo) {
        log.info("为service:{}, serviceRole: {}创建ConfigFileMap", serviceRoleInfo.getParentName(), serviceRoleInfo.getServiceRoleName());
        ClusterServiceRoleGroupConfigService roleGroupConfigService = SpringTool.getApplicationContext().getBean(ClusterServiceRoleGroupConfigService.class);
        ClusterServiceRoleInstanceService roleInstanceService = SpringTool.getApplicationContext().getBean(ClusterServiceRoleInstanceService.class);

        ClusterServiceRoleInstanceEntity serviceRoleInstance = roleInstanceService.getOneServiceRole(serviceRoleInfo.getName(), serviceRoleInfo.getHostname(), serviceRoleInfo.getClusterId());

        HashMap<Generators, List<ServiceConfig>> configFileMap = new HashMap<>();
        if (Arrays.asList(CommandType.INSTALL_SERVICE, CommandType.UPGRADE_SERVICE).contains(serviceRoleInfo.getCommandType())) {
            Integer roleGroupId = (Integer) CacheUtils.get("UseRoleGroup_" + serviceRoleInfo.getServiceInstanceId());
            ClusterServiceRoleGroupConfig config = roleGroupConfigService.getConfigByRoleGroupId(roleGroupId);
            ProcessUtils.generateConfigFileMap(configFileMap, config, serviceRoleInfo.getClusterId());
        } else if (serviceRoleInstance.getNeedRestart() == NeedRestart.YES) {
            ClusterServiceRoleGroupConfig config = roleGroupConfigService.getConfigByRoleGroupId(serviceRoleInstance.getRoleGroupId());
            ProcessUtils.generateConfigFileMap(configFileMap, config, serviceRoleInfo.getClusterId());
        }
        Map<String, String> globalVariables = GlobalVariables.getVariables(serviceRoleInfo.getClusterId());
        for (Generators generators : configFileMap.keySet()) {
            String outputDirectory = generators.getOutputDirectory();
            generators.setOutputDirectory(PlaceholderUtils.replacePlaceholders(outputDirectory, globalVariables, Constants.REGEX_VARIABLE));
        }

        log.info("创建ConfigFileMap成功，总共需要{}个Generators", configFileMap.size());
        return configFileMap;
    }


    private boolean isEnableRangerPlugin(ServiceRoleInfo roleInfo) {
        if (!ServiceRoleType.MASTER.equals(roleInfo.getRoleType())) {
            return false;
        }
        Integer clusterId = roleInfo.getClusterId();
        String serviceName = roleInfo.getParentName();
        return "true".equals(GlobalVariables.getValue(clusterId, "enable" + serviceName + "Plugin"));
    }


    private ExecResult doServiceAction(ServiceRoleInfo srvInfo, Callable<ExecResult> callable, Runnable postAction) {
        try {
            log.info("{} {} in host {}", srvInfo.getCommandType().getCommandName(Constants.CN), srvInfo.getName(), srvInfo.getHostname());
            ExecResult execResult = callable.call();
            if (execResult.isSuccess()) {
                log.info("在host {} {} {} 成功, 开始执行后置逻辑", srvInfo.getHostname(), srvInfo.getCommandType().getCommandName(Constants.CN), srvInfo.getName());
                postAction.run();
            }
            return execResult;
        } catch (Exception e) {
            log.error(e.getMessage(), e);

            String error = String.format("%s %s失败, 堆栈信息：%s", srvInfo.getParentName(), srvInfo.getCommandType().getCommandName(Constants.CN), ProcessUtils.getExceptionMessage(e));
            return ExecResult.error(error);
        }
    }

    private void updateServiceRoleState(ServiceRoleInfo role, ServiceRoleState state) {
        ProcessUtils.updateServiceRoleState(role.getCommandType(), role.getName(), role.getHostname(), role.getClusterId(), state);
    }


    public static class ServiceRoleExecException extends RuntimeException {

        public ServiceRoleExecException(String message) {
            super(message);
        }
    }
}
