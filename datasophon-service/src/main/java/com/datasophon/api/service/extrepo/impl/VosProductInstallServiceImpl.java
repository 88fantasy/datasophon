package com.datasophon.api.service.extrepo.impl;

import akka.actor.ActorRef;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import com.datasophon.api.dag.model.DagDefinition;
import com.datasophon.api.dag.model.NodeDefinition;
import com.datasophon.api.dto.extrepo.DeploymentDTO;
import com.datasophon.api.dto.extrepo.RunDagDto;
import com.datasophon.api.dto.extrepo.ServiceRoleQueryDTO;
import com.datasophon.api.exceptions.BusinessHintException;
import com.datasophon.api.master.ActorUtils;
import com.datasophon.api.master.DAGExecActor;
import com.datasophon.api.service.ClusterServiceInstanceService;
import com.datasophon.api.service.ClusterServiceRoleInstanceService;
import com.datasophon.api.service.FrameServiceRoleService;
import com.datasophon.api.service.FrameServiceService;
import com.datasophon.api.service.ServiceInstallService;
import com.datasophon.api.service.cmd.ClusterServiceCommandHostCommandService;
import com.datasophon.api.service.cmd.ClusterServiceCommandHostService;
import com.datasophon.api.service.cmd.ClusterServiceCommandService;
import com.datasophon.api.service.extrepo.VosProductInstallService;
import com.datasophon.api.service.extrepo.ctx.SimpleServiceResource;
import com.datasophon.api.service.extrepo.ctx.VosProductCmdSrvMappingContext;
import com.datasophon.api.service.extrepo.ctx.VosProductDeployDAGBuildContext;
import com.datasophon.api.service.host.ClusterHostService;
import com.datasophon.api.strategy.ServiceRoleStrategyContext;
import com.datasophon.api.utils.ProcessUtils;
import com.datasophon.api.utils.ServicePkgNameUtils;
import com.datasophon.api.vo.extrepo.DAGNode;
import com.datasophon.api.vo.extrepo.InstallResult;
import com.datasophon.api.vo.extrepo.ValidateResultVO;
import com.datasophon.common.Constants;
import com.datasophon.common.cache.CacheUtils;
import com.datasophon.common.cache.Namespace;
import com.datasophon.common.command.dag.DAGExecCommand;
import com.datasophon.common.enums.CommandType;
import com.datasophon.common.enums.ServiceRoleType;
import com.datasophon.common.model.DAG;
import com.datasophon.common.model.ServiceConfig;
import com.datasophon.common.model.ServiceInfo;
import com.datasophon.common.model.ServiceNode;
import com.datasophon.common.model.ServiceRoleHostMapping;
import com.datasophon.common.model.ServiceRoleInfo;
import com.datasophon.common.utils.IdUtils;
import com.datasophon.dao.entity.ClusterHostDO;
import com.datasophon.dao.entity.ClusterInfoEntity;
import com.datasophon.dao.entity.ClusterServiceInstanceEntity;
import com.datasophon.dao.entity.ClusterServiceRoleInstanceEntity;
import com.datasophon.dao.entity.FrameServiceEntity;
import com.datasophon.dao.entity.FrameServiceRoleEntity;
import com.datasophon.dao.entity.cmd.ClusterServiceCommandEntity;
import com.datasophon.dao.entity.cmd.ClusterServiceCommandHostCommandEntity;
import com.datasophon.dao.entity.cmd.ClusterServiceCommandHostEntity;
import com.datasophon.dao.entity.dag.NodeDefinitionEntity;
import com.datasophon.dao.enums.CommandState;
import com.datasophon.dao.enums.RoleType;
import com.datasophon.dao.enums.ServiceState;
import com.datasophon.dao.enums.dag.NodeStatus;
import com.datasophon.dao.model.extrepo.DeploySrvModel;
import com.datasophon.dao.model.extrepo.DeploySrvRoleModel;
import com.datasophon.dao.model.extrepo.DeploymentModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * @author zhanghuangbin
 */
@Component("vosProductInstallService")
@Slf4j
public class VosProductInstallServiceImpl extends ProductDeployHandlerSupport implements VosProductInstallService {


    @Autowired
    private ServiceInstallService serviceInstallService;


    @Autowired
    private ClusterServiceInstanceService clusterServiceInstanceService;

    @Autowired
    private FrameServiceRoleService frameServiceRoleService;

    @Autowired
    private ClusterServiceCommandHostService commandHostService;

    @Autowired
    private ClusterServiceCommandHostCommandService hostCommandService;


    @Autowired
    private ClusterServiceCommandService commandService;

    @Autowired
    private ClusterServiceInstanceService serviceInstanceService;

    @Autowired
    private ClusterServiceRoleInstanceService roleInstanceService;

    @Autowired
    private ClusterHostService clusterHostService;

    @Autowired
    private FrameServiceService frameService;

    @Override
    public ValidateResultVO validateDeploymentModel(DeploymentModel model, DeploymentDTO dto) {
        List<String> errors = new ArrayList<>();
        List<DeploySrvModel> apps = getTargetApps(model);

        Set<String> deployHosts = apps.stream()
                .flatMap(app -> app.getRoles().stream())
                .flatMap(role -> role.getDeployHosts().stream())
                .collect(Collectors.toSet());
        List<ClusterHostDO> hostList = clusterHostService.getHostListByClusterId(dto.getClusterId());
        Map<String, ClusterHostDO> hostMap = hostList.stream().collect(Collectors.toMap(ClusterHostDO::getHostname, a -> a, (a, b) -> a));
        deployHosts = deployHosts.stream().filter(host -> !hostMap.containsKey(host)).collect(Collectors.toSet());
        if (!deployHosts.isEmpty()) {
            errors.add(String.format("以下主机%s不存在或者无法通讯", StrUtil.join(",", deployHosts)));
        }

        ClusterInfoEntity clusterInfo = clusterInfoService.getById(dto.getClusterId());
        List<FrameServiceEntity> serviceList = frameService.getFrameServiceList(clusterInfo.getId());
        VosProductDeployDAGBuildContext ctx = new VosProductDeployDAGBuildContext(serviceList);
        apps.forEach(app -> {
            FrameServiceEntity entity = ctx.getSrvEntity(app);
            if (entity == null) {
                errors.add(String.format("服务%s %s不存在", app.getName(), app.getVersion()));
                return;
            }
            ClusterServiceInstanceEntity serviceInstance = clusterServiceInstanceService.getServiceInstanceByClusterIdAndServiceName(clusterInfo.getId(), entity.getServiceName());
            boolean exist = serviceInstance != null && commandService.lambdaQuery()
                    .eq(ClusterServiceCommandEntity::getServiceInstanceId, serviceInstance.getId())
                    .in(ClusterServiceCommandEntity::getCommandState, Arrays.asList(CommandState.RUNNING, CommandState.WAIT))
                    .exists();
            if (exist) {
                errors.add(String.format("服务%s %s正在执行命令，请等待命令执行完成或者取消命令", app.getName(), app.getVersion()));
            }
        });

        if (errors.isEmpty()) {
            ValidateResultVO vo = new ValidateResultVO();
            List<ValidateResultVO.DeploySrvRoleModel> roles = new ArrayList<>();
            apps.forEach(app -> {
                app.getRoles().forEach(role -> {
                    ValidateResultVO.DeploySrvRoleModel tmp = new ValidateResultVO.DeploySrvRoleModel();
                    tmp.setServiceName(app.getName());
                    tmp.setVersion(app.getVersion());
                    tmp.setRoleName(role.getName());
                    tmp.setDeployHosts(role.getDeployHosts());
                    roles.add(tmp);
                });
            });
            vo.setRoles(roles);
            return vo;
        } else {
            return new ValidateResultVO(errors);
        }
    }

    private List<DeploySrvModel> getTargetApps(DeploymentModel model) {
        return model.getApp()
                .stream()
                .filter(app -> app.getDeployType().equalsIgnoreCase("physical"))
                .collect(Collectors.toList());
    }


    @Override
    public InstallResult deploy(DeploymentDTO dto) {
        DeploymentModel model = doParseDeploymentFile(dto);
        List<DeploySrvModel> apps = getTargetApps(model);
        log.debug("解析到配置\n：{}", JSONObject.toJSONString(model, true));
        log.info("完成解析部署VOS格式的制品文件, 需要部署VOS格式的制品{}个应用", apps.size());

//        保存serviceRole和host的映射
        List<ServiceRoleHostMapping> hostMappings = new ArrayList<>();
        apps.stream()
                .flatMap(app -> app.getRoles().stream())
                .forEach(role -> {
                    ServiceRoleHostMapping hostMapping = new ServiceRoleHostMapping();
                    hostMapping.setHosts(role.getDeployHosts());
                    hostMapping.setServiceRole(role.getName());
                    hostMappings.add(hostMapping);
                });
        serviceInstallService.saveServiceRoleHostMapping(dto.getClusterId(), hostMappings);
        log.info("缓存存角色和host映射成功, 总共{}个映射", hostMappings.size());


//        保存应用的启动配置
        apps.forEach(app -> {
            List<ServiceConfig> configs = serviceInstallService.getServiceConfigFromDdl(dto.getClusterId(), app.getName());
            serviceInstallService.saveServiceConfig(dto.getClusterId(), app.getName(), configs, -1);
        });
        log.info("保存部署VOS格式的制品配置项成功");


        ClusterInfoEntity clusterInfo = clusterInfoService.getById(dto.getClusterId());
        List<FrameServiceEntity> serviceList = frameService.getFrameServiceList(clusterInfo.getId());
        Map<String, FrameServiceEntity> srvDefMap = CollectionUtil.toMap(serviceList, new HashMap<>(), srv -> srv.getServiceName() + ":" + srv.getServiceVersion());
        List<String> commandIds = new ArrayList<>(apps.size());
        apps.forEach(srv -> {
            String cmdId = doGenerateInstallCmd(clusterInfo, srvDefMap.get(srv.getName() + ":" + srv.getVersion()));
            commandIds.add(cmdId);
        });
        log.info("保存安装命令成功, 共需要安装{}个应用", commandIds.size());


        VosProductDeployDAGBuildContext ctx = new VosProductDeployDAGBuildContext(serviceList);
        DAG<String, DAGNode, Integer> dag = ctx.buildDeployDAG(apps, t -> {
            DeploySrvModel srvModel = t.unwrap();
            DAGNode node = new DAGNode();
            node.setName(srvModel.getName());
            node.setVersion(srvModel.getVersion());
            return node;
        });
        String dagId = saveDAG(clusterInfo.getId(), "部署VOS格式的制品", commandIds, dag);
//        必须等待事务提交，否则，有概率查询不到保存的命令
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                invokeCommands(dagId, false, commandIds);
            }
        });
        return new InstallResult(dagId);
    }


    private String doGenerateInstallCmd(ClusterInfoEntity cluster, FrameServiceEntity frameService) {
        ClusterServiceInstanceEntity serviceInstance = clusterServiceInstanceService.getServiceInstanceByClusterIdAndServiceName(cluster.getId(), frameService.getServiceName());
        CommandType commandType = ServiceState.WAIT_INSTALL.equals(serviceInstance.getServiceState()) ? CommandType.INSTALL_SERVICE : CommandType.UPGRADE_SERVICE;
        ClusterServiceCommandEntity cmd = ProcessUtils.generateCommandEntity(cluster.getId(), commandType, frameService.getServiceName());
        cmd.setServiceInstanceId(serviceInstance.getId());
        commandService.save(cmd);
        log.info("保存{}{}命令成功, 命令ID:{}", commandType.getCommandName(Constants.CN), frameService.getServiceName(), cmd.getCommandId());


        Map<String, List<String>> serviceRoleHostMap = (Map<String, List<String>>) CacheUtils.get(Namespace.of(cluster.getClusterCode(), Constants.SERVICE_ROLE_HOST_MAPPING));
//        保存commandHost的相关数据
        List<ClusterServiceCommandHostEntity> hostEntityList = new ArrayList<>();
        List<FrameServiceRoleEntity> serviceRoleList = frameServiceRoleService.getServiceRoleList(cluster.getId(), Collections.singletonList(frameService.getId()), null);
        serviceRoleList.sort(Comparator.comparing(FrameServiceRoleEntity::getSortNum));
        Set<String> hostnames = new HashSet<>();
        for (FrameServiceRoleEntity serviceRole : serviceRoleList) {
            hostnames.addAll(serviceRoleHostMap.getOrDefault(serviceRole.getServiceRoleName(), new ArrayList<>(0)));
        }
        hostnames.forEach(host -> hostEntityList.add(ProcessUtils.generateCommandHostEntity(cmd.getCommandId(), host)));
        commandHostService.saveBatch(hostEntityList);
        log.info("命令:{}{}保存各主机总命令信息成功,共涉及{}台主机",
                CommandType.ofCode(cmd.getCommandType()).getCommandName(Constants.CN),
                cmd.getServiceName(), hostEntityList.size());


//        保存每一台主机每一个角色需要执行的命令
        Map<String, ClusterServiceCommandHostEntity> cache = CollectionUtil.toMap(hostEntityList, new HashMap<>(), ClusterServiceCommandHostEntity::getHostname);
        List<ClusterServiceCommandHostCommandEntity> hostCommandList = new ArrayList<>();

        for (FrameServiceRoleEntity serviceRole : serviceRoleList) {
            List<String> hosts = serviceRoleHostMap.get(serviceRole.getServiceRoleName());
            if (hosts == null) {
                continue;
            }

            for (int i = 0; i < hosts.size(); i++) {
                String hostname = hosts.get(i);
                ClusterServiceRoleInstanceEntity db = roleInstanceService.getOneServiceRole(serviceRole.getServiceRoleName(), hostname, cluster.getId());

                CommandType roleCmdType = db == null ? CommandType.INSTALL_SERVICE : CommandType.UPGRADE_SERVICE;
                ClusterServiceCommandHostCommandEntity hostCommand = ProcessUtils.generateCommandHostCommandEntity(
                        roleCmdType, cmd.getCommandId(),
                        serviceRole.getServiceRoleName(), serviceRole.getServiceRoleType(),
                        cache.get(hostname)
                );
                hostCommand.setSort(i);
                hostCommandList.add(hostCommand);
            }
        }
        hostCommandService.saveBatch(hostCommandList);
        log.info("命令:{}{}保存各主机需要执行命令成功,共需要执行{}个命令", CommandType.ofCode(cmd.getCommandType()).getCommandName(Constants.CN),
                cmd.getServiceName(), hostCommandList.size());

        return cmd.getCommandId();
    }


    private String saveDAG(Integer clusterId, String serviceActionName, List<String> commandIds, DAG<String, DAGNode, Integer> dag) {
        ClusterInfoEntity clusterInfo = clusterInfoService.getById(clusterId);


        VosProductCmdSrvMappingContext context = new VosProductCmdSrvMappingContext();
        context.setSrvCmd(commandService.lambdaQuery().in(ClusterServiceCommandEntity::getCommandId, commandIds).list());
        context.setCmdHost(hostCommandService.lambdaQuery().in(ClusterServiceCommandHostCommandEntity::getCommandId, commandIds).list());
//        将服务依赖的dag重构成节点安装服务的dag
        Map<String, NodeDefinitionEntity> nodeMap = new HashMap<>();
        dag.getNodes().forEach((serviceName, info) -> {
            ServiceNode serviceNode = new ServiceNode();
            ClusterServiceCommandEntity cmd = context.getCmd(info.getName());
            serviceNode.setCommandId(cmd.getCommandId());
            serviceNode.setCommandType(CommandType.ofCode(cmd.getCommandType()));
            serviceNode.setServiceName(info.getName());


            FrameServiceEntity serviceEntity = frameService.lambdaQuery()
                    .eq(FrameServiceEntity::getFrameCode, clusterInfo.getClusterFrame())
                    .eq(FrameServiceEntity::getServiceName, info.getName())
                    .eq(FrameServiceEntity::getServiceVersion, info.getVersion())
                    .one();
            List<FrameServiceRoleEntity> srvRoles = frameServiceRoleService.getAllServiceRoleList(serviceEntity.getId());
            Map<String, FrameServiceRoleEntity> srvRoleMap = CollectionUtil.toMap(srvRoles, new HashMap<>(), FrameServiceRoleEntity::getServiceRoleName);

            List<ServiceRoleInfo> masterRoles = new ArrayList<>();
            List<ServiceRoleInfo> workerRoles = new ArrayList<>();
            List<ServiceRoleInfo> clientRoles = new ArrayList<>();

            List<ClusterServiceCommandHostCommandEntity> hostCommands = context.getCmdHostList(cmd.getCommandId());
            hostCommands.sort(Comparator.comparing(ClusterServiceCommandHostCommandEntity::getSort, Comparator.nullsLast(Comparator.naturalOrder())));
            for (ClusterServiceCommandHostCommandEntity hostCommand : context.getCmdHostList(cmd.getCommandId())) {
                FrameServiceRoleEntity frameServiceRoleEntity = srvRoleMap.get(hostCommand.getServiceRoleName());

                ServiceRoleInfo serviceRoleInfo = JSONObject.parseObject(frameServiceRoleEntity.getServiceRoleJson(), ServiceRoleInfo.class);
                serviceRoleInfo.setClusterId(clusterInfo.getId());

                serviceRoleInfo.setHostname(hostCommand.getHostname());
                serviceRoleInfo.setHostCommandId(hostCommand.getHostCommandId());

                serviceRoleInfo.setParentName(cmd.getServiceName());
                serviceRoleInfo.setCommandType(CommandType.ofCode(hostCommand.getCommandType()));
                serviceRoleInfo.setServiceInstanceId(cmd.getServiceInstanceId());

                serviceRoleInfo.setPackageName(serviceEntity.getPackageName());
                serviceRoleInfo.setArchInfoMap(ServicePkgNameUtils.getArchInfo(serviceEntity));
                serviceRoleInfo.setDecompressPackageName(serviceEntity.getDecompressPackageName());
                serviceRoleInfo.setFrameCode(serviceEntity.getFrameCode());


                ServiceInfo serviceInfo = JSONObject.parseObject(serviceEntity.getServiceJson(), ServiceInfo.class);
                serviceRoleInfo.setCreateDecompressDir(serviceInfo.getCreateDecompressDir());

                Optional.ofNullable(ServiceRoleStrategyContext.getServiceRoleHandler(serviceRoleInfo.getName()))
                        .ifPresent(ha -> ha.handlerServiceRoleInfo(serviceRoleInfo, hostCommand.getHostname()));


                if (ServiceRoleType.MASTER.equals(serviceRoleInfo.getRoleType())) {
                    masterRoles.add(serviceRoleInfo);
                } else if (ServiceRoleType.WORKER.equals(serviceRoleInfo.getRoleType())) {
                    workerRoles.add(serviceRoleInfo);
                } else if (ServiceRoleType.CLIENT.equals(serviceRoleInfo.getRoleType())) {
                    clientRoles.add(serviceRoleInfo);
                }
            }

            serviceNode.setMasterRoles(masterRoles);
            serviceNode.setWorkerRoles(workerRoles);
            serviceNode.setClientRoles(clientRoles);

            NodeDefinitionEntity node = new NodeDefinitionEntity();
            node.setNodeName(info.getName());
            node.setNodeConfig(JSONObject.toJSONString(serviceNode));
            nodeMap.put(info.getName(), node);
        });


        DagDefinition definition = new DagDefinition();
        definition.setDagName(String.format("%s服务-%s", serviceActionName, DateUtil.format(new Date(), DatePattern.NORM_DATETIME_PATTERN)));

        StringBuilder sb = new StringBuilder();
        sb.append(serviceActionName).append("服务:");
        dag.getNodes().forEach((srv, n) -> sb.append(srv).append(";"));
        String desc = sb.toString();
        definition.setDescription(desc.length() > 300 ? desc.substring(0, 300) + "..." : desc);
        String dagId = dagService.saveDAG(clusterId, definition);
        dagService.saveNodes(dagId, new ArrayList<>(nodeMap.values()));

        dag.getEdges().forEach(edge -> {
            NodeDefinitionEntity start = nodeMap.get(edge.getStart());
            NodeDefinitionEntity end = nodeMap.get(edge.getEnd());
            dagService.saveEdge(dagId, start, end);
        });

        return dagId;
    }


    private void invokeCommands(String dagId, boolean restart, List<String> commandIds) {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("构建DAG完成，开始执行命令");
                DAGExecCommand cmd = new DAGExecCommand();
                cmd.setDagId(dagId);
                cmd.setRestart(restart);
                ActorRef actor = ActorUtils.getLocalActor(DAGExecActor.class, ActorUtils.getActorRefName(DAGExecActor.class));
                actor.tell(cmd, ActorRef.noSender());
            } catch (Exception e) {
                log.error("execute dagId: {} fail, {}", dagId, e.getMessage(), e);
                transactionalUtils.doInNewTx(() -> commandIds.forEach(cmdId -> updateCommandState(cmdId, CommandState.FAILED, false)));
            }
        });
    }

    private void updateCommandState(String cmdId, CommandState state, boolean restart) {
        ClusterServiceCommandEntity cmd = commandService.getCommandById(cmdId);
        if (state == CommandState.FAILED) {
            if (Arrays.asList(CommandState.RUNNING, CommandState.WAIT).contains(cmd.getCommandState())) {
                cmd.setCommandState(state);
                cmd.setCommandProgress(100);
                commandService.updateById(cmd);
                List<String> cmdHostList = commandHostService.lambdaQuery()
                        .in(ClusterServiceCommandHostEntity::getCommandState, Arrays.asList(CommandState.WAIT, CommandState.RUNNING))
                        .eq(ClusterServiceCommandHostEntity::getCommandId, cmdId)
                        .select(ClusterServiceCommandHostEntity::getCommandHostId)
                        .list()
                        .stream()
                        .map(ClusterServiceCommandHostEntity::getCommandHostId)
                        .collect(Collectors.toList());
                if (!cmdHostList.isEmpty()) {
                    hostCommandService.lambdaUpdate()
                            .in(ClusterServiceCommandHostCommandEntity::getCommandHostId, cmdHostList)
                            .set(ClusterServiceCommandHostCommandEntity::getCommandState, state)
                            .set(ClusterServiceCommandHostCommandEntity::getCommandProgress, 100)
                            .update();
                    commandHostService.lambdaUpdate()
                            .in(ClusterServiceCommandHostEntity::getCommandHostId, cmdHostList)
                            .set(ClusterServiceCommandHostEntity::getCommandState, state)
                            .set(ClusterServiceCommandHostEntity::getCommandProgress, 100)
                            .update();
                }
            }
        }

        if (state == CommandState.RUNNING) {
            if (restart && cmd.getCommandState().equals(CommandState.SUCCESS)) {
                return;
            }
            cmd.setCommandState(state);
            cmd.setCommandProgress(0);
            commandService.updateById(cmd);
            List<String> cmdHostList = commandHostService.lambdaQuery()
                    .eq(ClusterServiceCommandHostEntity::getCommandId, cmdId)
                    .select(ClusterServiceCommandHostEntity::getCommandHostId)
                    .list()
                    .stream()
                    .map(ClusterServiceCommandHostEntity::getCommandHostId)
                    .collect(Collectors.toList());
            if (!cmdHostList.isEmpty()) {
                hostCommandService.lambdaUpdate()
                        .in(ClusterServiceCommandHostCommandEntity::getCommandHostId, cmdHostList)
                        .set(ClusterServiceCommandHostCommandEntity::getCommandState, state)
                        .set(ClusterServiceCommandHostCommandEntity::getCommandProgress, 0)
                        .update();
                commandHostService.lambdaUpdate()
                        .in(ClusterServiceCommandHostEntity::getCommandHostId, cmdHostList)
                        .set(ClusterServiceCommandHostEntity::getCommandState, state)
                        .set(ClusterServiceCommandHostEntity::getCommandProgress, 0)
                        .update();
            }
        }
    }


    @Override
    public void redeploy(RunDagDto dto) {
        List<NodeDefinition> nodes = dagService.getNodesByDagId(dto.getDagId(), true);
        List<String> commandIds = new ArrayList<>();
        for (NodeDefinition node : nodes) {
            ServiceNode serviceNode = JSONObject.parseObject((String) node.getNodeConfig(), ServiceNode.class);

            if (dto.isRestart() && !node.getStatus().equals(NodeStatus.SUCCESS)) {
                serviceNode.getMasterRoles().forEach(role -> {
                    Integer roleGroupId = (Integer) CacheUtils.get("UseRoleGroup_" + role.getServiceInstanceId());
                    if (roleGroupId == null) {
                        throw new BusinessHintException("系统已经重启，内存缓存数据已经丢失，当前任务无法恢复，请重新上传部署VOS格式的制品清单安装");
                    }
                });
            }
            commandIds.add(serviceNode.getCommandId());
        }

        for (NodeDefinition node : nodes) {
            if (dto.isRestart() && !NodeStatus.SUCCESS.equals(node.getStatus())) {
                updateNode(node);
            }
        }

        commandIds.forEach(cmd -> updateCommandState(cmd, CommandState.RUNNING, dto.isRestart()));
        invokeCommands(dto.getDagId(), dto.isRestart(), commandIds);
    }

    /**
     * 更新节点的状态以及配置
     *
     * @param node
     */
    private void updateNode(NodeDefinition node) {
        ServiceNode serviceNode = JSONObject.parseObject((String) node.getNodeConfig(), ServiceNode.class);
        List<ServiceRoleInfo> roleInfoList = new ArrayList<>();
        if (serviceNode.getMasterRoles() != null) {
            roleInfoList.addAll(serviceNode.getMasterRoles());
        }
        if (serviceNode.getWorkerRoles() != null) {
            roleInfoList.addAll(serviceNode.getWorkerRoles());
        }
        if (serviceNode.getClientRoles() != null) {
            roleInfoList.addAll(serviceNode.getClientRoles());
        }
        if (!roleInfoList.isEmpty()) {
            String frameCode = roleInfoList.get(0).getFrameCode();
            Integer clusterId = roleInfoList.get(0).getClusterId();

            FrameServiceEntity serviceEntity = frameService.getNewestDefByName(frameCode, serviceNode.getServiceName());
            List<FrameServiceRoleEntity> srvRoles = frameServiceRoleService.getAllServiceRoleList(serviceEntity.getId());
            Map<String, FrameServiceRoleEntity> srvRoleMap = CollectionUtil.toMap(srvRoles, new HashMap<>(), FrameServiceRoleEntity::getServiceRoleName);
            Map<String, ClusterServiceRoleInstanceEntity> roleInstanceMap = new HashMap<>();
            ClusterServiceInstanceEntity srvInstance = serviceInstanceService.getServiceInstanceByClusterIdAndServiceName(clusterId, serviceNode.getServiceName());
            if (srvInstance != null) {
                List<ClusterServiceRoleInstanceEntity> instances = roleInstanceService.getServiceRoleInstanceListByServiceId(srvInstance.getId());
                instances.forEach(instance -> {
                    roleInstanceMap.put(String.format("%s-%s", instance.getHostname(), instance.getServiceRoleName()), instance);
                });
            }


            ServiceInfo serviceDef = JSONObject.parseObject(serviceEntity.getServiceJson(), ServiceInfo.class);
            CopyOptions cpOpt = CopyOptions.create().setIgnoreProperties(
                    ServiceRoleInfo::getClusterId, ServiceRoleInfo::getHostname, ServiceRoleInfo::getHostCommandId,
                    ServiceRoleInfo::getParentName, ServiceRoleInfo::getCommandType, ServiceRoleInfo::getServiceInstanceId,
                    ServiceRoleInfo::getFrameCode, ServiceRoleInfo::getRoleType
            );
//            根据最新的ddl，更新配置信息
            for (ServiceRoleInfo oldOne : roleInfoList) {
                FrameServiceRoleEntity frameServiceRoleEntity = srvRoleMap.get(oldOne.getName());
                ServiceRoleInfo newOne = JSONObject.parseObject(frameServiceRoleEntity.getServiceRoleJson(), ServiceRoleInfo.class);
                if (!newOne.getRoleType().equals(oldOne.getRoleType())) {
                    throw new BusinessHintException(String.format("服务%s %s的角色类型发生变更，请重新执行%s操作", serviceNode.getServiceName(),
                            oldOne.getName(), serviceNode.getCommandType().getCommandName(Constants.CN)));
                }
                if (CommandType.INSTALL_SERVICE.equals(oldOne.getCommandType())) {
                    if (roleInstanceMap.containsKey(String.format("%s-%s", oldOne.getHostname(), oldOne.getName()))) {
                        oldOne.setCommandType(CommandType.UPGRADE_SERVICE);
                    }
                }

                BeanUtil.copyProperties(newOne, oldOne, cpOpt);

                oldOne.setCreateDecompressDir(serviceDef.getCreateDecompressDir());
                oldOne.setDecompressPackageName(serviceEntity.getDecompressPackageName());
                oldOne.setPackageName(serviceEntity.getPackageName());
                oldOne.setArchInfoMap(ServicePkgNameUtils.getArchInfo(serviceEntity));

                Optional.ofNullable(ServiceRoleStrategyContext.getServiceRoleHandler(newOne.getName()))
                        .ifPresent(ha -> ha.handlerServiceRoleInfo(oldOne, oldOne.getHostname()));
            }
        }

        node.setStatus(NodeStatus.PENDING);
        node.setNodeConfig(JSONObject.toJSONString(serviceNode));
        dagService.updateNode(node);
    }


    @Override
    public String generateGenericInstallCommand(Integer clusterId, List<String> serviceNames) {
        ClusterInfoEntity clusterInfo = clusterInfoService.getById(clusterId);
        List<FrameServiceEntity> serviceList = frameService.getFrameServiceList(clusterInfo.getId());
        VosProductDeployDAGBuildContext ctx = new VosProductDeployDAGBuildContext(serviceList);
        List<String> commandIds = new ArrayList<>(serviceNames.size());

        List<SimpleServiceResource> resources = new ArrayList<>();
        serviceNames.forEach(srv -> {
            FrameServiceEntity entity = ctx.getHighestVersionSrv(srv);
            SimpleServiceResource resource = new SimpleServiceResource(entity.getServiceName(), entity.getServiceVersion());
            resources.add(resource);
            String cmdId = doGenerateInstallCmd(clusterInfo, ctx.getHighestVersionSrv(srv));
            commandIds.add(cmdId);
        });

        DAG<String, DAGNode, Integer> dag = ctx.buildDeployDAG(resources, rs -> {
            SimpleServiceResource resource = rs.unwrap();
            return BeanUtil.toBean(resource, DAGNode.class);
        });
        String dagId = saveDAG(clusterId, "部署VOS格式的制品", commandIds, dag);
        return dagId;
    }

    @Override
    public String generateAndExecSrvInstCmd(Integer clusterId, CommandType commandType, List<Integer> serviceInstanceIds) {
        List<Integer> serviceFrameworkIds = new ArrayList<>();
        List<String> commandIds = new ArrayList<>();
        serviceInstanceIds.forEach(instId -> {
            List<ClusterServiceRoleInstanceEntity> roleInstances = roleInstanceService.getServiceRoleInstanceListByServiceId(instId);
            if (CollectionUtil.isEmpty(roleInstances)) {
                return;
            }
            ClusterServiceInstanceEntity serviceInstance = clusterServiceInstanceService.getById(instId);
            serviceFrameworkIds.add(serviceInstance.getFrameServiceId());

            String cmdId = doGenerateSrvInstOpCommand(serviceInstance, roleInstances, commandType);
            if (cmdId != null) {
                commandIds.add(cmdId);
            }
        });
        if (commandIds.isEmpty()) {
            return null;
        }

        ClusterInfoEntity clusterInfo = clusterInfoService.getById(clusterId);
        List<FrameServiceEntity> serviceList = frameService.getFrameServiceList(clusterInfo.getId());
        VosProductDeployDAGBuildContext ctx = new VosProductDeployDAGBuildContext(serviceList);
        List<SimpleServiceResource> resources = frameService.listServices(serviceFrameworkIds)
                .stream()
                .map(s -> new SimpleServiceResource(s.getServiceName(), s.getServiceVersion()))
                .collect(Collectors.toList());
        DAG<String, DAGNode, Integer> dag = ctx.buildDeployDAG(resources, t -> {
            SimpleServiceResource srvModel = t.unwrap();
            DAGNode node = new DAGNode();
            node.setName(srvModel.getName());
            node.setVersion(srvModel.getVersion());
            return node;
        });
        if (CommandType.STOP_SERVICE.equals(commandType)) {
            dag = dag.getReverseDag();
        }
        String dagId = saveDAG(clusterInfo.getId(), commandType.getCommandName(Constants.CN), commandIds, dag);
//        必须等待事务提交，否则，有概率查询不到保存的命令
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                invokeCommands(dagId, false, commandIds);
            }
        });
        return dagId;
    }


    private String doGenerateSrvInstOpCommand(ClusterServiceInstanceEntity serviceInstance, List<ClusterServiceRoleInstanceEntity> roleInstanceList, CommandType commandType) {
        ClusterServiceCommandEntity command = ProcessUtils.generateCommandEntity(serviceInstance.getClusterId(), commandType, serviceInstance.getServiceName());
        command.setServiceInstanceId(serviceInstance.getId());
        commandService.save(command);

        List<ClusterServiceCommandHostCommandEntity> hostCommands = new ArrayList<>();
        Map<String, ClusterServiceCommandHostEntity> map = new HashMap<>();
        for (ClusterServiceRoleInstanceEntity roleInstance : roleInstanceList) {
            ClusterServiceCommandHostEntity commandHost = map.computeIfAbsent(
                    roleInstance.getHostname(),
                    i -> ProcessUtils.generateCommandHostEntity(command.getCommandId(), roleInstance.getHostname())
            );
            ClusterServiceCommandHostCommandEntity hostCommand = ProcessUtils.generateCommandHostCommandEntity(commandType,
                    command.getCommandId(), roleInstance.getServiceRoleName(), roleInstance.getRoleType(), commandHost);
            hostCommand.setSort(0);
            hostCommands.add(hostCommand);
        }
        commandHostService.saveBatch(map.values());
        hostCommandService.saveBatch(hostCommands);

        return command.getCommandId();
    }


    @Override
    public String generateAndExecSrvRoleCmd(Integer clusterId, CommandType commandType, Integer instId, List<Integer> roleInstIds) {
        if (Arrays.asList(CommandType.UPGRADE_SERVICE, CommandType.INSTALL_SERVICE).contains(commandType)) {
            throw new UnsupportedOperationException(String.format("command %s is not support", commandType));
        }

        List<Integer> serviceFrameworkIds = new ArrayList<>();

        List<ClusterServiceRoleInstanceEntity> roleInstances = roleInstanceService.lambdaQuery()
                .in(ClusterServiceRoleInstanceEntity::getId, roleInstIds)
                .list();
        if (CollectionUtil.isEmpty(roleInstances)) {
            return null;
        }

        ClusterServiceInstanceEntity serviceInstance = clusterServiceInstanceService.getById(instId);
        serviceFrameworkIds.add(serviceInstance.getFrameServiceId());

        String cmdId = doGenerateSrvInstOpCommand(serviceInstance, roleInstances, commandType);

        ClusterInfoEntity clusterInfo = clusterInfoService.getById(clusterId);
        List<FrameServiceEntity> serviceList = frameService.getFrameServiceList(clusterInfo.getId());
        VosProductDeployDAGBuildContext ctx = new VosProductDeployDAGBuildContext(serviceList);
        List<SimpleServiceResource> resources = frameService.listServices(serviceFrameworkIds)
                .stream()
                .map(s -> new SimpleServiceResource(s.getServiceName(), s.getServiceVersion()))
                .collect(Collectors.toList());
        DAG<String, DAGNode, Integer> dag = ctx.buildDeployDAG(resources, t -> {
            SimpleServiceResource srvModel = t.unwrap();
            DAGNode node = new DAGNode();
            node.setName(srvModel.getName());
            node.setVersion(srvModel.getVersion());
            return node;
        });
        if (CommandType.STOP_SERVICE.equals(commandType)) {
            dag = dag.getReverseDag();
        }

        List<String> commandIds = Collections.singletonList(cmdId);
        String dagId = saveDAG(clusterInfo.getId(), commandType.getCommandName(Constants.CN), commandIds, dag);
//        必须等待事务提交，否则，有概率查询不到保存的命令
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                invokeCommands(dagId, false, commandIds);
            }
        });
        return dagId;
    }


    @Override
    public void generateAndExecSrvRoleCommands(Integer clusterId, CommandType commandType, Map<Integer, List<Integer>> instanceIdMap) {
        instanceIdMap.forEach((instId, roleInstIds) -> generateAndExecSrvRoleCmd(clusterId, commandType, instId, roleInstIds));
    }

    @Override
    public List<FrameServiceEntity> listNewestByDeployment(DeploymentDTO dto) {
        DeploymentModel model = doParseDeploymentFile(dto);
        List<String> serviceList = getTargetApps(model).stream().map(DeploySrvModel::getName).collect(Collectors.toList());
        List<FrameServiceEntity> list = frameService.listNewest(dto.getClusterId(), true);
        list.forEach(et -> et.setSelected(serviceList.contains(et.getServiceName())));
        return list;
    }


    @Override
    public List<FrameServiceRoleEntity> getServiceRoleListByDeployment(ServiceRoleQueryDTO dto) {
        ClusterInfoEntity clusterInfo = clusterInfoService.getById(dto.getClusterId());
        List<Integer> ids = IdUtils.toIdList(dto.getServiceIds());
        List<FrameServiceRoleEntity> list = frameServiceRoleService.lambdaQuery()
                .eq(FrameServiceRoleEntity::getFrameCode, clusterInfo.getClusterFrame())
                .eq(Objects.nonNull(dto.getServiceRoleType()), FrameServiceRoleEntity::getServiceRoleType, dto.getServiceRoleType())
                .in(!ids.isEmpty(), FrameServiceRoleEntity::getServiceId, ids)
                .list();
        setHosts(list, BeanUtil.toBean(dto, DeploymentDTO.class));
        return list;
    }

    private void setHosts(List<FrameServiceRoleEntity> list, DeploymentDTO dto) {
        DeploymentModel model = doParseDeploymentFile(dto);

        Map<String, DeploySrvRoleModel> map = new HashMap<>();
        getTargetApps(model).forEach(app -> {
            app.getRoles().forEach(role -> {
                map.put(app.getName() + "_" + role.getName(), role);
            });
        });

        for (FrameServiceRoleEntity role : list) {
            FrameServiceEntity frameServiceEntity = frameService.getById(role.getServiceId());
            DeploySrvRoleModel roleModel = map.get(frameServiceEntity.getServiceName() + "_" + role.getServiceRoleName());
            if (roleModel != null) {
                role.setHosts(roleModel.getDeployHosts());
            }
        }
    }

    @Override
    public List<FrameServiceRoleEntity> getNonMasterRoleListByDeployment(DeploymentDTO dto) {
        ClusterInfoEntity clusterInfo = clusterInfoService.getById(dto.getClusterId());
        List<Integer> ids = IdUtils.toIdList(dto.getServiceIds());
        List<FrameServiceRoleEntity> list = frameServiceRoleService.lambdaQuery()
                .eq(FrameServiceRoleEntity::getFrameCode, clusterInfo.getClusterFrame())
                .ne(FrameServiceRoleEntity::getServiceRoleType, RoleType.MASTER)
                .in(!ids.isEmpty(), FrameServiceRoleEntity::getServiceId, ids)
                .list();
        setHosts(list, dto);
        return list;
    }
}
